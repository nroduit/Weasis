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
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.SpecificCharacterSet;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.dcm4che3.img.DicomMetaData;
import org.dcm4che3.img.stream.ImageDescriptor;
import org.dcm4che3.util.UIDUtils;
import org.joml.Vector3d;
import org.opencv.core.Core.MinMaxLocResult;
import org.opencv.core.CvType;
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
 * Image I/O handler for curved MPR panoramic images.
 *
 * <p>This class generates the "straightened" panoramic view by sampling the volume along the curved
 * path defined in CurvedMprAxis.
 */
public class CurvedMprImageIO implements DcmMediaReader {
  private static final Logger LOGGER = LoggerFactory.getLogger(CurvedMprImageIO.class);

  private static final String MIME_TYPE = "image/cmpr";

  private static final SoftHashMap<CurvedMprImageIO, DicomMetaData> HEADER_CACHE =
      new SoftHashMap<>();

  private final FileCache fileCache;
  private final HashMap<TagW, Object> tags;
  private final URI uri;
  private final CurvedMprAxis axis;
  private final Volume<?, ?> volume;
  private Attributes attributes;

  public CurvedMprImageIO(CurvedMprAxis axis) {
    this.axis = Objects.requireNonNull(axis);
    this.volume = axis.getVolume();
    this.fileCache = new FileCache(this);
    this.tags = new HashMap<>();
    try {
      this.uri = new URI("data:" + MIME_TYPE);
    } catch (URISyntaxException e) {
      throw new IllegalArgumentException(e);
    }
    updateIdentityTags();
  }

  public void setBaseAttributes(Attributes attributes) {
    this.attributes = attributes;
  }

  @Override
  public PlanarImage getImageFragment(MediaElement media) throws Exception {
    return generatePanoramicImage();
  }

  /**
   * Generate the panoramic image using Curved Planar Reformation (CPR).
   *
   * <p>For a dental arch curve drawn on the axial plane (XY plane), this uses a straightforward
   * approach:
   *
   * <ul>
   *   <li>The curve lies in the XY plane at a fixed Z (the axial slice level)
   *   <li>At each curve point a single voxel on the curve is sampled (a thin curved slice, like the
   *       cross-section slices and the MPR views) so the pixel values match the source exactly
   *   <li>The vertical (Z) direction is used for height sampling
   *   <li>Output X = arc-length position along the curve
   *   <li>Output Y = vertical (Z) position
   * </ul>
   */
  private PlanarImage generatePanoramicImage() {
    List<Vector3d> curvePoints = axis.getCurvePoints3D();
    if (curvePoints.size() < 2) {
      return null;
    }

    double stepMm = axis.getStepMm();
    double sliceSizeMm = axis.getWidthMm();
    double pixelMm = volume.getMinPixelRatio();
    Vector3d voxelRatio = volume.getVoxelRatio();

    LOGGER.info("=== generatePanoramicImage CPR ===");
    LOGGER.info(
        "curvePoints: {}, stepMm: {}, widthMm: {}, pixelMm: {}",
        curvePoints.size(),
        stepMm,
        sliceSizeMm,
        pixelMm);
    LOGGER.info(
        "volume size: {}x{}x{}, voxelRatio: ({},{},{})",
        volume.getSize().x,
        volume.getSize().y,
        volume.getSize().z,
        voxelRatio.x,
        voxelRatio.y,
        voxelRatio.z);

    CurveSampler.Sampling sampling =
        CurveSampler.sample(curvePoints, axis.getPlaneNormal(), stepMm, pixelMm);
    List<Vector3d> smoothedPoints = sampling.smoothedPoints();
    List<Vector3d> sampledPoints = sampling.sampledPoints();
    List<Vector3d> perpDirs = sampling.perpDirections();
    if (sampledPoints.isEmpty()) {
      return null;
    }

    // Vertical extent: height in Z direction (in mm)
    int heightPx = (int) Math.round(sliceSizeMm / pixelMm);
    if (heightPx < 1) heightPx = 1;

    int widthPx = sampledPoints.size();

    if (CurvedMprAxis.DEBUG_DRAW) {
      axis.setDebugData(
          new CurvedMprAxis.DebugCurveData(
              curvePoints, smoothedPoints, sampledPoints, perpDirs, pixelMm));
    }

    LOGGER.info("Output: {}x{} px, height={}mm", widthPx, heightPx, sliceSizeMm);

    int cvType = volume.getCvType();
    ImageCV dst = new ImageCV(heightPx, widthPx, cvType);

    // The Z coordinate of the curve (axial slice level)
    double curveZ = sampledPoints.getFirst().z;

    // Diagnostic logging
    int midI = widthPx / 2;
    Vector3d midPt = sampledPoints.get(midI);
    Vector3d midPerp = perpDirs.get(midI);
    LOGGER.info(
        "Middle curve point[{}]: ({},{},{})",
        midI,
        String.format("%.1f", midPt.x),
        String.format("%.1f", midPt.y),
        String.format("%.1f", midPt.z));
    LOGGER.info(
        "Middle perp: ({},{},{})",
        String.format("%.3f", midPerp.x),
        String.format("%.3f", midPerp.y),
        String.format("%.3f", midPerp.z));

    // For each point along the curve (horizontal axis of panoramic)
    for (int i = 0; i < widthPx; i++) {
      Vector3d curvePoint = sampledPoints.get(i);

      // For each pixel in the vertical direction (Z axis)
      for (int j = 0; j < heightPx; j++) {
        // Vertical offset in voxels along Z, centered on the curve's Z level
        // Account for voxel ratio: convert pixel offset to voxel offset
        double zOffsetVoxels = (j - heightPx / 2.0) / voxelRatio.z;
        double sampleZ = curveZ + zOffsetVoxels;

        Number value =
            volume.getInterpolatedValueFromSource(curvePoint.x, curvePoint.y, sampleZ, 0);
        if (value != null) {
          setPixelValue(dst, j, i, value, cvType);
        }
      }
    }

    LOGGER.info("Generated CPR panoramic image");

    setDicomTags(widthPx, heightPx);
    return dst;
  }

  static void setPixelValue(ImageCV dst, int row, int col, Number value, int cvType) {
    int depth = CvType.depth(cvType);
    switch (depth) {
      case CvType.CV_8U, CvType.CV_8S -> dst.put(row, col, value.byteValue());
      case CvType.CV_16U, CvType.CV_16S -> dst.put(row, col, value.shortValue());
      case CvType.CV_32S -> dst.put(row, col, value.intValue());
      case CvType.CV_32F -> dst.put(row, col, value.floatValue());
      case CvType.CV_64F -> dst.put(row, col, value.doubleValue());
    }
  }

  /**
   * Refresh the per-image identity tags. PixelSpacing is intentionally <em>not</em> written: the X
   * axis of the panoramic represents arc-length along the curve (uniform in mm-along-arc, but
   * <em>not</em> Euclidean distance between distant points), so calibrated mm measurements would be
   * misleading. Leaving PixelSpacing absent forces measurements to report in pixels, which is an
   * honest unit for this image.
   */
  public void updateIdentityTags() {
    tags.putIfAbsent(TagD.get(Tag.SOPInstanceUID), UIDUtils.createUID());
    tags.putIfAbsent(TagD.get(Tag.InstanceNumber), 1);
  }

  private void setDicomTags(int widthPx, int heightPx) {
    HEADER_CACHE.remove(this);
    tags.put(TagD.get(Tag.Columns), widthPx);
    tags.put(TagD.get(Tag.Rows), heightPx);
    updateIdentityTags();
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
    return new String[] {"Curved MPR Image Decoder"};
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
    return buildAttributes(tags, attributes);
  }

  static Attributes buildAttributes(HashMap<TagW, Object> tags, Attributes attributes) {
    Attributes dcm = new Attributes(tags.size() + (attributes != null ? attributes.size() : 0));
    if (attributes != null) {
      SpecificCharacterSet cs = attributes.getSpecificCharacterSet();
      dcm.setSpecificCharacterSet(cs.toCodes());
      dcm.addAll(attributes);
    }
    DicomMediaUtils.fillAttributes(tags, dcm);
    return dcm;
  }

  @Override
  public FileCache getFileCache() {
    return fileCache;
  }

  @Override
  public boolean buildFile(File output) {
    return false;
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
