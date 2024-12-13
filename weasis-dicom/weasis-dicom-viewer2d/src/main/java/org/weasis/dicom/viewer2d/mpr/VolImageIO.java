/*
 * Copyright (c) 2024 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.viewer2d.mpr;

import java.io.File;
import java.io.FileOutputStream;
import java.lang.ref.Reference;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
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
import org.dcm4che3.img.DicomJpegWriteParam;
import org.dcm4che3.img.DicomMetaData;
import org.dcm4che3.img.DicomOutputData;
import org.dcm4che3.img.DicomTranscodeParam;
import org.dcm4che3.img.stream.ImageDescriptor;
import org.dcm4che3.io.DicomOutputStream;
import org.dcm4che3.util.UIDUtils;
import org.joml.Matrix4d;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Scalar;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.explorer.model.DataExplorerModel;
import org.weasis.core.api.image.cv.CvUtil;
import org.weasis.core.api.media.data.Codec;
import org.weasis.core.api.media.data.FileCache;
import org.weasis.core.api.media.data.MediaElement;
import org.weasis.core.api.media.data.MediaSeriesGroup;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.api.media.data.Taggable;
import org.weasis.core.util.SoftHashMap;
import org.weasis.dicom.codec.DcmMediaReader;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.codec.DicomSeries;
import org.weasis.dicom.codec.TagD;
import org.weasis.dicom.codec.geometry.GeometryOfSlice;
import org.weasis.dicom.codec.utils.DicomMediaUtils;
import org.weasis.dicom.viewer2d.mip.MipView.Type;
import org.weasis.dicom.viewer2d.mpr.MprView.SliceOrientation;
import org.weasis.opencv.data.ImageCV;
import org.weasis.opencv.data.PlanarImage;

public class VolImageIO implements DcmMediaReader {
  private static final Logger LOGGER = LoggerFactory.getLogger(VolImageIO.class);

  private static final String MIME_TYPE = "image/vol";
  private static final SoftHashMap<VolImageIO, DicomMetaData> HEADER_CACHE =
      new SoftHashMap<>() {

        @Override
        public void removeElement(Reference<? extends DicomMetaData> soft) {
          VolImageIO key = reverseLookup.remove(soft);
          if (key != null) {
            hash.remove(key);
          }
        }
      };
  private final FileCache fileCache;

  private final HashMap<TagW, Object> tags;
  private final URI uri;
  private final MprAxis mprAxis;
  private final Volume<?> volume;
  private Attributes attributes;

  public VolImageIO(MprAxis mprAxis, Volume<?> volume) {
    this.mprAxis = Objects.requireNonNull(mprAxis);
    this.volume = Objects.requireNonNull(volume);
    this.fileCache = new FileCache(this);
    this.tags = new HashMap<>();
    try {
      this.uri = new URI("data:" + MIME_TYPE);
    } catch (URISyntaxException e) {
      throw new IllegalArgumentException(e);
    }
  }

  public void setBaseAttributes(Attributes attributes) {
    this.attributes = attributes;
  }

  @Override
  public void writeMetaData(MediaSeriesGroup group) {
    if (group == null) {
      return;
    }
    // Get the dicom header
    Attributes header = getDicomObject();
    DicomMediaUtils.writeMetaData(group, header);

    // Series Group
    if (TagW.SubseriesInstanceUID.equals(group.getTagID())) {
      // Information for series ToolTips
      group.setTagNoNull(TagD.get(Tag.PatientName), getTagValue(TagD.get(Tag.PatientName)));
      group.setTagNoNull(TagD.get(Tag.StudyDescription), header.getString(Tag.StudyDescription));
    }
  }

  @Override
  public PlanarImage getImageFragment(MediaElement media) throws Exception {
    double position = mprAxis.getPositionAlongAxis();
    Vector3d volumeCenter = mprAxis.getMprView().mprController.getVolumeCrossHair();
    int extend = mprAxis.getThicknessExtension();
    if (extend > 0 && !mprAxis.isAdjusting()) {
      List<PlanarImage> sources = new ArrayList<>();
      for (int i = -extend; i <= extend; i++) {
        PlanarImage slice;
        if (i == 0) {
          slice = getSlice(volumeCenter);
        } else {
          double positionIndex = position + i + volume.getSliceSize() / 2.0;
          Vector3d centerIndex = new Vector3d(volumeCenter);
          mprAxis.changePositionAlongAxis(centerIndex, positionIndex);
          slice = volume.getVolumeSlice(mprAxis, centerIndex);
        }
        if (slice != null) {
          sources.add(slice);
        }
      }

      PlanarImage image = mipStack(sources, volumeCenter);
      image.setReleasedAfterProcessing(false);
      return image;
    }
    PlanarImage image = getSlice(volumeCenter);
    image.setReleasedAfterProcessing(false);
    return image;
  }

  private PlanarImage mipStack(List<PlanarImage> sources, Vector3d volumeCenter) {
    if (sources.size() > 1) {
      Type mipType = (Type) mprAxis.getMprView().mprController.getMipTypeOption().getSelectedItem();
      if (mipType == Type.MEAN) {
        return meanStack(sources);
      } else {
        return minMaxStack(sources, mipType);
      }
    }
    return getSlice(volumeCenter);
  }

  private static PlanarImage minMaxStack(List<PlanarImage> sources, Type mipType) {
    PlanarImage firstImg = sources.getFirst();
    ImageCV dstImg = new ImageCV();
    firstImg.toMat().copyTo(dstImg);

    for (int i = 1; i < sources.size(); i++) {
      PlanarImage image = sources.get(i);
      if (image instanceof Mat mat && image.width() == dstImg.width()) {
        if (mipType == Type.MIN) {
          Core.min(dstImg, mat, dstImg);
        } else if (mipType == Type.MAX) {
          Core.max(dstImg, mat, dstImg);
        }
      }
    }
    return dstImg;
  }

  public static ImageCV meanStack(List<PlanarImage> sources) {
    PlanarImage firstImg = sources.getFirst();
    int type = firstImg.type();
    Mat mean = new Mat(firstImg.height(), firstImg.width(), CvType.CV_32F);
    firstImg.toMat().convertTo(mean, CvType.CV_32F);
    int size = sources.size();
    for (int i = 1; i < size; i++) {
      PlanarImage image = sources.get(i);
      CvUtil.accumulateFloatStack(image, firstImg, mean);
    }
    ImageCV dstImg = new ImageCV();
    Core.divide(mean, new Scalar(size), mean);
    mean.convertTo(dstImg, type);
    return dstImg;
  }

  public Volume<?> getVolume() {
    return volume;
  }

  public PlanarImage getSlice(Vector3d volumeCenter) {
    Taggable rawIO = mprAxis.getRawIO();
    if (rawIO == null || volume.stack == null) {
      return null;
    }
    HEADER_CACHE.remove(this);

    PlanarImage curImage = volume.getVolumeSlice(mprAxis, volumeCenter);
    if (curImage == null) {
      return null;
    }

    GeometryOfSlice geometry = volume.stack.getFistSliceGeometry();
    double minRatio = volume.getMinPixelRatio();
    double[] pixSpacing = new double[] {minRatio, minRatio};

    Vector3d row = new Vector3d(geometry.getRow());
    Vector3d col = new Vector3d(geometry.getColumn());
    if (volume.negativeDirection) {
      row.negate();
      col.negate();
    }

    // Transform the original 3D position using the transformation matrix
    mprAxis.getTransformation().transformDirection(row);
    mprAxis.getTransformation().transformDirection(col);
    double[] orientation = new double[] {row.x, row.y, row.z, col.x, col.y, col.z};
    if (mprAxis.getViewOrientation() == SliceOrientation.CORONAL) {
      orientation = new double[] {row.x, row.y, row.z, col.x, col.y, -col.z};

    } else if (mprAxis.getViewOrientation() == SliceOrientation.SAGITTAL) {
      orientation = new double[] {row.x, row.y, row.z, col.x, -col.y, -col.z};
    }

    // Tags with same values for all the Series
    rawIO.setTag(TagD.get(Tag.Columns), curImage.width());
    rawIO.setTag(TagD.get(Tag.Rows), curImage.height());
    int extend = mprAxis.getThicknessExtension();
    double thickness = extend > 0 && !mprAxis.isAdjusting() ? extend * minRatio : minRatio;
    rawIO.setTag(TagD.get(Tag.SliceThickness), thickness);
    rawIO.setTag(TagD.get(Tag.PixelSpacing), pixSpacing);
    rawIO.setTag(TagD.get(Tag.ImageOrientationPatient), orientation);

    // Image specific tags
    rawIO.setTag(TagD.get(Tag.SOPInstanceUID), UIDUtils.createUID());
    int instanceNumber = mprAxis.getSliceIndex();
    rawIO.setTag(TagD.get(Tag.InstanceNumber), instanceNumber + 1);

    Vector3d thlc = transformPosition(geometry, volumeCenter);
    rawIO.setTag(TagD.get(Tag.ImagePositionPatient), new double[] {thlc.x, thlc.y, thlc.z});

    DicomMediaUtils.computeSlicePositionVector(rawIO);

    double[] loc = (double[]) rawIO.getTagValue(TagW.SlicePosition);
    if (loc != null) {
      rawIO.setTag(TagD.get(Tag.SliceLocation), loc[0] + loc[1] + loc[2]);
    }
    mprAxis.getImageElement().initPixelConfiguration();
    return curImage;
  }

  private Vector3d transformPosition(GeometryOfSlice geometry, Vector3d volumeCenter) {
    // Calculate new Image Position (Patient)
    Vector3d topLeft = geometry.getTLHC();

    Matrix4d transform = mprAxis.getCombinedTransformation(new Quaterniond(), volumeCenter);
    Vector3d origin = new Vector3d(0.5, 0.5, 0.5);
    Vector3d t1 = new Vector3d(); // Top left in the image space without rotation
    Vector3d t2 = new Vector3d(); // Top left in the image space
    Vector3d t3 = new Vector3d(); // Difference between the two

    mprAxis.getTransformation().transformPosition(origin, t2);
    transform.transformPosition(origin, t1);
    t3.set(t2);
    t3.sub(t1);
    t1.add(t3);
    Vector3d imgOffset = new Vector3d(-t1.x, -t1.y, t1.z).mul(volume.getMinPixelRatio());
    return new Vector3d(topLeft).sub(imgOffset);
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
    return new String[] {"File Raw Image Decoder from OpenCV"}; // NON-NLS
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
    Attributes dcm = new Attributes(tags.size() + attributes.size());
    SpecificCharacterSet cs = attributes.getSpecificCharacterSet();
    dcm.setSpecificCharacterSet(cs.toCodes());
    DicomMediaUtils.fillAttributes(tags, dcm);
    dcm.addAll(attributes);
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
      DicomMetaData metadata = getDicomMetaData();
      Attributes dataSet = metadata.getDicomObject();
      if (dataSet == null) {
        return false;
      }
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
          imgData.writRawImageData(dos, dataSet);
        } else {
          int[] jpegWriteParams =
              imgData.adaptTagsToCompressedImage(
                  dataSet, imgData.getFirstImage().get(), desc, writeParams);
          imgData.writeCompressedImageData(dos, dataSet, jpegWriteParams);
        }
      }
      return true;
    } catch (Exception e) {
      LOGGER.error("Cannot write dicom file", e);
    }
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

  private synchronized DicomMetaData readMetaData() {
    DicomMetaData header = HEADER_CACHE.get(this);
    if (header != null) {
      return header;
    }
    Attributes dcm = new Attributes(tags.size() + attributes.size());
    SpecificCharacterSet cs = attributes.getSpecificCharacterSet();
    dcm.setSpecificCharacterSet(cs.toCodes());
    DicomMediaUtils.fillAttributes(tags, dcm);
    dcm.addAll(attributes);
    header = new DicomMetaData(dcm, UID.ImplicitVRLittleEndian);
    HEADER_CACHE.put(this, header);
    return header;
  }
}
