/*
 * Copyright (c) 2024 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.viewer2d.mpr.cmpr;

import java.io.File;
import java.io.FileOutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.dcm4che3.data.VR;
import org.dcm4che3.img.DicomJpegWriteParam;
import org.dcm4che3.img.DicomMetaData;
import org.dcm4che3.img.DicomOutputData;
import org.dcm4che3.img.DicomTranscodeParam;
import org.dcm4che3.img.stream.ImageDescriptor;
import org.dcm4che3.io.DicomOutputStream;
import org.dcm4che3.util.UIDUtils;
import org.joml.Vector3d;
import org.opencv.core.Core.MinMaxLocResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.explorer.model.DataExplorerModel;
import org.weasis.core.api.media.data.Codec;
import org.weasis.core.api.media.data.FileCache;
import org.weasis.core.api.media.data.MediaElement;
import org.weasis.core.api.media.data.MediaSeriesGroup;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.util.SoftHashMap;
import org.weasis.dicom.codec.DcmMediaReader;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.codec.DicomSeries;
import org.weasis.dicom.codec.TagD;
import org.weasis.dicom.codec.utils.DicomMediaUtils;
import org.weasis.dicom.viewer2d.mpr.Volume;
import org.weasis.opencv.data.ImageCV;
import org.weasis.opencv.data.PlanarImage;

/**
 * Image I/O handler for a single curved-MPR cross-section: a slab perpendicular to the curve at a
 * sample point, spanning {@code perp} (slab horizontal axis) and the source plane normal (vertical
 * axis). Together a series of these instances forms the dental "cross-cuts" companion to the
 * panoramic image.
 */
public class CrossSectionImageIO implements DcmMediaReader {

  private static final Logger LOGGER = LoggerFactory.getLogger(CrossSectionImageIO.class);

  private static final String MIME_TYPE = "image/cmpr-xs";

  private static final SoftHashMap<CrossSectionImageIO, DicomMetaData> HEADER_CACHE =
      new SoftHashMap<>();

  private final FileCache fileCache;
  private final HashMap<TagW, Object> tags;
  private final URI uri;
  private final Volume<?, ?> volume;
  private final Vector3d center;
  private final Vector3d perp;
  private final Vector3d normal;
  private final double widthMm;
  private final double heightMm;
  private final double pixelMm;
  private Attributes attributes;

  /**
   * @param volume source volume
   * @param center curve sample point in voxel-ratio-scaled coordinates
   * @param perp in-plane perpendicular direction (unit length in scaled space)
   * @param normal source plane normal (unit length in scaled space); the slab's vertical axis
   * @param widthMm slab extent along {@code perp} (mm)
   * @param heightMm slab extent along {@code normal} (mm)
   * @param instanceNumber 1-based DICOM InstanceNumber for the slice
   */
  public CrossSectionImageIO(
      Volume<?, ?> volume,
      Vector3d center,
      Vector3d perp,
      Vector3d normal,
      double widthMm,
      double heightMm,
      int instanceNumber) {
    this.volume = Objects.requireNonNull(volume);
    this.center = new Vector3d(center);
    this.perp = new Vector3d(perp);
    this.normal = new Vector3d(normal);
    this.widthMm = widthMm;
    this.heightMm = heightMm;
    this.pixelMm = volume.getMinPixelRatio();
    this.fileCache = new FileCache(this);
    this.tags = new HashMap<>();
    try {
      this.uri = new URI("data:" + MIME_TYPE + ";seq=" + instanceNumber);
    } catch (URISyntaxException e) {
      throw new IllegalArgumentException(e);
    }
    tags.put(TagD.get(Tag.SOPInstanceUID), UIDUtils.createUID());
    tags.put(TagD.get(Tag.InstanceNumber), instanceNumber);
  }

  public void setBaseAttributes(Attributes attributes) {
    this.attributes = attributes;
  }

  @Override
  public PlanarImage getImageFragment(MediaElement media) {
    return generateSlice();
  }

  /**
   * Sample the volume on the plane spanned by {@code perp} (output X) and {@code normal} (output
   * Y), centered at {@link #center}. Geometry is in voxel-ratio-scaled space (physically isotropic,
   * one unit == pixelMm), so each output pixel is a one-unit step; samples are converted to a raw
   * voxel index just before interpolation.
   */
  private PlanarImage generateSlice() {
    int widthPx = Math.max(1, (int) Math.round(widthMm / pixelMm));
    int heightPx = Math.max(1, (int) Math.round(heightMm / pixelMm));
    int cvType = volume.getCvType();
    ImageCV dst = new ImageCV(heightPx, widthPx, cvType);

    Vector3d voxelRatio = volume.getVoxelRatio();

    for (int j = 0; j < heightPx; j++) {
      // Y axis = plane normal (orthogonal to the drawing plane).
      double vOffset = j - heightPx / 2.0;
      for (int i = 0; i < widthPx; i++) {
        // X axis = perp (in-plane, perpendicular to the curve tangent).
        double hOffset = i - widthPx / 2.0;
        double wx = center.x + perp.x * hOffset + normal.x * vOffset;
        double wy = center.y + perp.y * hOffset + normal.y * vOffset;
        double wz = center.z + perp.z * hOffset + normal.z * vOffset;
        Number value =
            volume.getInterpolatedValueFromSource(
                wx / voxelRatio.x, wy / voxelRatio.y, wz / voxelRatio.z, 0);
        if (value != null) {
          CurvedMprImageIO.setPixelValue(dst, j, i, value, cvType);
        }
      }
    }

    HEADER_CACHE.remove(this);
    tags.put(TagD.get(Tag.Columns), widthPx);
    tags.put(TagD.get(Tag.Rows), heightPx);
    tags.put(TagD.get(Tag.PixelSpacing), new double[] {pixelMm, pixelMm});
    return dst;
  }

  @Override
  public URI getUri() {
    return uri;
  }

  @Override
  public MediaElement getPreview() {
    return null;
  }

  @Override
  public boolean delegate(DataExplorerModel explorerModel) {
    return false;
  }

  @Override
  public DicomImageElement[] getMediaElement() {
    return null;
  }

  @Override
  public DicomSeries getMediaSeries() {
    return null;
  }

  @Override
  public int getMediaElementNumber() {
    return 1;
  }

  @Override
  public String getMediaFragmentMimeType() {
    return MIME_TYPE;
  }

  @Override
  public Map<TagW, Object> getMediaFragmentTags(Object key) {
    return tags;
  }

  @Override
  public void close() {
    HEADER_CACHE.remove(this);
  }

  @Override
  public Codec getCodec() {
    return null;
  }

  @Override
  public String[] getReaderDescription() {
    return new String[] {"Curved MPR Cross-Section Image Decoder"};
  }

  @Override
  public Object getTagValue(TagW tag) {
    return tag == null ? null : tags.get(tag);
  }

  @Override
  public void setTag(TagW tag, Object value) {
    DicomMediaUtils.setTag(tags, tag, value);
  }

  @Override
  public void setTagNoNull(TagW tag, Object value) {
    if (value != null) {
      setTag(tag, value);
    }
  }

  @Override
  public boolean containTagKey(TagW tag) {
    return tags.containsKey(tag);
  }

  @Override
  public Iterator<Entry<TagW, Object>> getTagEntrySetIterator() {
    return tags.entrySet().iterator();
  }

  public void copyTags(TagW[] tagList, MediaElement media, boolean allowNullValue) {
    if (tagList != null && media != null) {
      for (TagW tag : tagList) {
        Object value = media.getTagValue(tag);
        if (allowNullValue || value != null) {
          tags.put(tag, value);
        }
      }
    }
  }

  @Override
  public void replaceURI(URI uri) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Attributes getDicomObject() {
    Attributes dcm = CurvedMprImageIO.buildAttributes(tags, attributes);
    // Ensure the per-instance SeriesInstanceUID stays the one assigned to the series, not the
    // ref-image one copied by the base attributes.
    Object seriesUid = tags.get(TagD.get(Tag.SeriesInstanceUID));
    if (seriesUid != null) {
      dcm.setString(Tag.SeriesInstanceUID, VR.UI, seriesUid.toString());
    }
    return dcm;
  }

  @Override
  public FileCache getFileCache() {
    return fileCache;
  }

  @Override
  public boolean buildFile(File output) {
    try {
      PlanarImage image = getImageFragment(null);
      return image != null && writeImageToFile(output, image);
    } catch (Exception e) {
      LOGGER.error("Cannot write cross-section DICOM file", e);
      return false;
    }
  }

  private boolean writeImageToFile(File output, PlanarImage image) throws Exception {
    DicomMetaData metadata = getDicomMetaData();
    Attributes dataSet = metadata.getDicomObject();
    if (dataSet == null) {
      return false;
    }
    // The base attributes are cloned from the source header, which may carry the source image's
    // PixelData. Drop it so the writer emits only the freshly generated slab pixels.
    dataSet.remove(Tag.PixelData);
    String dstTsuid = metadata.getTransferSyntaxUID();
    ImageDescriptor desc = metadata.getImageDescriptor();
    DicomTranscodeParam params = new DicomTranscodeParam(dstTsuid);
    DicomJpegWriteParam writeParams = params.getWriteJpegParam();

    DicomOutputData imgData = new DicomOutputData(image, desc, dstTsuid);
    if (!dstTsuid.equals(imgData.getTsuid())) {
      dstTsuid = imgData.getTsuid();
      if (!DicomOutputData.isNativeSyntax(dstTsuid)) {
        writeParams = DicomJpegWriteParam.buildDicomImageWriteParam(dstTsuid);
      }
    }

    try (DicomOutputStream dos = new DicomOutputStream(new FileOutputStream(output), dstTsuid)) {
      dos.writeFileMetaInformation(dataSet.createFileMetaInformation(dstTsuid));
      if (DicomOutputData.isNativeSyntax(dstTsuid)) {
        imgData.writeRawImageData(dos, dataSet);
      } else {
        int[] jpegWriteParams =
            imgData.adaptTagsToCompressedImage(
                dataSet, imgData.getFirstImage().get(), desc, writeParams);
        imgData.writeCompressedImageData(dos, dataSet, jpegWriteParams);
      }
    }
    return true;
  }

  @Override
  public DicomMetaData getDicomMetaData() {
    return readMetaData();
  }

  @Override
  public boolean isEditableDicom() {
    return false;
  }

  @Override
  public void writeMetaData(MediaSeriesGroup group) {
    DcmMediaReader.super.writeMetaData(group);
  }

  private synchronized DicomMetaData readMetaData() {
    DicomMetaData header = HEADER_CACHE.get(this);
    if (header != null) {
      return header;
    }
    Attributes dcm = getDicomObject();
    header = new DicomMetaData(dcm, UID.ImplicitVRLittleEndian);
    ImageDescriptor desc = header.getImageDescriptor();
    if (desc != null) {
      MinMaxLocResult minMax = new MinMaxLocResult();
      minMax.minVal = volume.getMinimum().doubleValue();
      minMax.maxVal = volume.getMaximum().doubleValue();
      desc.setMinMaxPixelValue(0, minMax);
    }
    HEADER_CACHE.put(this, header);
    return header;
  }
}
