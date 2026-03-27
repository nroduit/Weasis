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

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import javax.swing.JProgressBar;
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
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.joml.Vector3i;
import org.opencv.core.Core;
import org.opencv.core.Core.MinMaxLocResult;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Rect;
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
import org.weasis.core.ui.model.GraphicModel;
import org.weasis.core.util.SoftHashMap;
import org.weasis.dicom.codec.DcmMediaReader;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.codec.DicomSeries;
import org.weasis.dicom.codec.TagD;
import org.weasis.dicom.codec.geometry.GeometryOfSlice;
import org.weasis.dicom.codec.utils.DicomMediaUtils;
import org.weasis.dicom.viewer2d.mip.MipView.Type;
import org.weasis.opencv.data.ImageCV;
import org.weasis.opencv.data.PlanarImage;

public class VolImageIO implements DcmMediaReader {
  private static final Logger LOGGER = LoggerFactory.getLogger(VolImageIO.class);

  private static final int BORDER_CROP_TOLERANCE = 5;
  private static final int BORDER_CROP_MARGIN = 2;
  private static final String MIME_TYPE = "image/vol"; // NON-NLS
  private static final SoftHashMap<VolImageIO, DicomMetaData> HEADER_CACHE = new SoftHashMap<>();
  private final FileCache fileCache;

  private final HashMap<TagW, Object> tags;
  private final URI uri;
  private final MprAxis mprAxis;
  private final Volume<?, ?> volume;
  private Attributes attributes;
  private Map<GeometryOfSlice, GraphicModel> graphicModelMap;

  public VolImageIO(MprAxis mprAxis, Volume<?, ?> volume) {
    this.mprAxis = Objects.requireNonNull(mprAxis);
    this.volume = Objects.requireNonNull(volume);
    this.fileCache = new FileCache(this);
    this.tags = new HashMap<>();
    try {
      this.uri = new URI("data:" + MIME_TYPE); // NON-NLS
    } catch (URISyntaxException e) {
      throw new IllegalArgumentException(e);
    }
    this.graphicModelMap = new HashMap<>();
  }

  public void setGraphicModel(GeometryOfSlice geometry, GraphicModel model) {
    if (model != null) {
      graphicModelMap.put(geometry, model);
    }
  }

  public GraphicModel getGraphicModel(GeometryOfSlice geometry) {
    return graphicModelMap.get(geometry);
  }

  public void setBaseAttributes(Attributes attributes) {
    this.attributes = attributes;
  }

  @Override
  public void writeMetaData(MediaSeriesGroup group) {
    DcmMediaReader.super.writeMetaData(group);
  }

  @Override
  public PlanarImage getImageFragment(MediaElement media) throws Exception {
    MprController controller = mprAxis.getMprView().mprController;
    Vector3d volumeCenter = controller.getCrossHairPosition();
    int extend = mprAxis.getThicknessExtension();
    if (extend > 0 && !mprAxis.isAdjusting()) {
      double position = controller.getCrossHairPosition(mprAxis).z;
      List<PlanarImage> sources = new ArrayList<>();
      for (int i = -extend; i <= extend; i++) {
        PlanarImage slice;
        if (i == 0) {
          slice = getSlice(volumeCenter);
        } else {
          double positionIndex = position + i;
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

  public Volume<?, ?> getVolume() {
    return volume;
  }

  public PlanarImage getSlice(Vector3d volumeCenter) {
    if (volume.stack == null) {
      return null;
    }
    HEADER_CACHE.remove(this);

    PlanarImage curImage = volume.getVolumeSlice(mprAxis, volumeCenter);
    if (curImage == null) {
      return null;
    }

    double minRatio = volume.getMinPixelRatio();
    double[] pixSpacing = new double[] {minRatio, minRatio};

    // Tags with same values for all the Series
    this.setTag(TagD.get(Tag.Columns), curImage.width());
    this.setTag(TagD.get(Tag.Rows), curImage.height());
    int extend = mprAxis.getThicknessExtension();
    double thickness =
        extend > 0 && !mprAxis.isAdjusting() ? (extend * 2 + 1) * minRatio : minRatio;
    this.setTag(TagD.get(Tag.SliceThickness), thickness);
    this.setTag(TagD.get(Tag.PixelSpacing), pixSpacing);

    // Image specific tags
    this.setTag(TagD.get(Tag.SOPInstanceUID), UIDUtils.createUID());
    int instanceNumber = mprAxis.getSliceIndex();
    this.setTag(TagD.get(Tag.InstanceNumber), instanceNumber + 1);

    if (volume.isBasic()) {
      // Basic (non-rectified) volume: voxels were placed using plane-specific rotations, so
      // there is no consistent patient-space geometry to recover. Skip IOP and IPP.
      this.setTag(TagD.get(Tag.ImageOrientationPatient), null);
      this.setTag(TagD.get(Tag.ImagePositionPatient), null);
    } else {
      // Derive the MPR rotation quaternion for this plane
      Quaterniond mprRotation = mprAxis.getMprView().mprController.getRotation(mprAxis.getPlane());

      // Determine the base row/col directions in voxelRatio space for each plane.
      // These are the standard basis vectors corresponding to the pixel row and column
      // directions, derived from the plane-specific sub-transforms applied in
      // getRealVolumeTransformation:
      //   AXIAL:    identity              → row = e1, col = e2
      //   CORONAL:  Rx(-90°)·S(1,-1,1)    → row = e1, col = e3
      //   SAGITTAL: Ry(90°)·Rz(90°)       → row = e2, col = e3
      Vector3d baseRow;
      Vector3d baseCol;
      switch (mprAxis.getPlane()) {
        case CORONAL -> {
          baseRow = new Vector3d(1, 0, 0);
          baseCol = new Vector3d(0, 0, 1);
        }
        case SAGITTAL -> {
          baseRow = new Vector3d(0, 1, 0);
          baseCol = new Vector3d(0, 0, 1);
        }
        default -> { // AXIAL
          baseRow = new Vector3d(1, 0, 0);
          baseCol = new Vector3d(0, 1, 0);
        }
      }
      // Apply MPR rotation in voxelRatio space
      mprRotation.transform(baseRow);
      mprRotation.transform(baseCol);

      // Map from voxelRatio space to patient (LPS) space using volume axes.
      // A direction d in voxelRatio space maps to: d.x * axisX + d.y * axisY + d.z * axisZ.
      // This is necessary because the volume axes may include inversions (e.g. axisZ = (0,0,-1)
      // for rectified volumes), so rotating the patient-space axes directly by the
      // voxelRatio-space quaternion would produce incorrect results for oblique rotations.
      Vector3d axX = volume.getVolumeAxisX();
      Vector3d axY = volume.getVolumeAxisY();
      Vector3d axZ = volume.getVolumeAxisZ();

      Vector3d row =
          new Vector3d(axX)
              .mul(baseRow.x)
              .add(new Vector3d(axY).mul(baseRow.y))
              .add(new Vector3d(axZ).mul(baseRow.z));
      row.normalize();

      Vector3d col =
          new Vector3d(axX)
              .mul(baseCol.x)
              .add(new Vector3d(axY).mul(baseCol.y))
              .add(new Vector3d(axZ).mul(baseCol.z));
      col.normalize();

      double[] orientation = new double[] {row.x, row.y, row.z, col.x, col.y, col.z};

      // TLHC = patient position of crosshair  -  half-sliceImage * minRatio along row and col.
      // This is exact regardless of plane or rotation: the slice center is always the crosshair
      // and the slice image is always sliceSize × sliceSize pixels of minRatio mm each.
      Vector3d tlhc = computeSliceTLHC(row, col);

      this.setTag(TagD.get(Tag.ImageOrientationPatient), orientation);
      this.setTag(TagD.get(Tag.ImagePositionPatient), new double[] {tlhc.x, tlhc.y, tlhc.z});

      Double slicePos = DicomMediaUtils.computeSlicePosition(this);
      this.setTagNoNull(TagD.get(Tag.SliceLocation), slicePos);
    }

    mprAxis.getImageElement().initPixelConfiguration();
    return curImage;
  }

  /**
   * Computes the Image Position Patient (top-left-hand corner) of the current MPR slice in patient
   * (LPS) millimetre coordinates.
   *
   * <p>The slice image is always {@link Volume#getSliceSize()} × {@link Volume#getSliceSize()}
   * pixels of {@link Volume#getMinPixelRatio()} mm each. Its center is at the crosshair, which is
   * the voxelRatio-scaled position obtained by transforming the slice center pixel {@code (S/2,
   * S/2, 0)} through the current {@code transformation} matrix.
   *
   * @param iopRow unit row direction cosine (IOP[0..2], already MPR-rotated)
   * @param iopCol unit column direction cosine (IOP[3..5], already MPR-rotated)
   */
  private Vector3d computeSliceTLHC(Vector3d iopRow, Vector3d iopCol) {
    double minRatio = volume.getMinPixelRatio();
    double halfSlice = volume.getSliceSize() / 2.0;

    // Transform the slice center pixel (S/2, S/2, 0) → voxelRatio-scaled volume coordinates
    Vector3d sliceCenter = new Vector3d(halfSlice, halfSlice, 0);
    mprAxis.getTransformation().transformPosition(sliceCenter);

    // Convert voxelRatio-scaled → patient (LPS) mm
    // voxelRatio = pixelRatio / minRatio, so scaled / voxelRatio * pixelRatio = scaled * minRatio
    Vector3d centerPatient = volume.getVolumeOrigin();
    centerPatient.add(new Vector3d(volume.getVolumeAxisX()).mul(sliceCenter.x * minRatio));
    centerPatient.add(new Vector3d(volume.getVolumeAxisY()).mul(sliceCenter.y * minRatio));
    centerPatient.add(new Vector3d(volume.getVolumeAxisZ()).mul(sliceCenter.z * minRatio));

    // TLHC = center - half-slice along each in-plane direction
    centerPatient.sub(new Vector3d(iopRow).mul(halfSlice * minRatio));
    centerPatient.sub(new Vector3d(iopCol).mul(halfSlice * minRatio));
    return centerPatient;
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
      return writeImageToFile(output, image);
    } catch (Exception e) {
      LOGGER.error("Cannot write dicom file", e);
    }
    return false;
  }

  /**
   * Renders the current MPR slice and returns the bounding box of non-background pixels.
   *
   * <p>Used during the first (analysis) pass of a series export to determine the tightest crop
   * region that can be applied uniformly to every slice. A {@code null} return value means the
   * entire slice is background (outside the volume) and should be skipped.
   *
   * @return {@code [x, y, width, height]} of the content bounding box, or {@code null} if the slice
   *     is entirely background
   */
  int[] computeCurrentSliceBoundingBox() {
    try {
      PlanarImage image = getImageFragment(null);
      if (image == null) return null;
      return detectNonBlackBoundingBox(image, volume.getMinimumAsDouble(), BORDER_CROP_TOLERANCE);
    } catch (Exception e) {
      LOGGER.warn("Cannot compute slice bounding box", e);
      return null;
    } finally {
      HEADER_CACHE.remove(this);
    }
  }

  /**
   * Builds a DICOM file from the current MPR slice using a <em>pre-computed</em> crop region, and
   * saves it to {@code directory/<SOPInstanceUID>}.
   *
   * <p>This overload is used during the second (export) pass of a series rebuild. Providing the
   * same {@code precomputedCrop} for every slice guarantees that all images in the series have
   * identical dimensions, which is required for smooth scrolling in DICOM viewers.
   *
   * @param directory the output directory
   * @param precomputedCrop {@code [x, y, width, height]} uniform crop to apply, or {@code null} to
   *     write the full (uncropped) image
   * @return the written {@link File}, or {@code null} if writing failed
   */
  public File buildCroppedFile(File directory, int[] precomputedCrop) {
    try {
      // Render the slice – also refreshes SOPInstanceUID, Rows, Columns, IPP, IOP, …
      PlanarImage image = getImageFragment(null);
      if (image == null) return null;

      // Name the output file after the SOPInstanceUID that was just assigned
      String sopUID = (String) tags.get(TagD.get(Tag.SOPInstanceUID));
      if (sopUID == null) {
        sopUID = UIDUtils.createUID();
      }

      PlanarImage finalImage = image;
      if (precomputedCrop != null) {
        Mat cropped =
            new Mat(
                image.toMat(),
                new Rect(
                    precomputedCrop[0], precomputedCrop[1],
                    precomputedCrop[2], precomputedCrop[3]));
        ImageCV croppedImg = new ImageCV();
        cropped.copyTo(croppedImg);
        finalImage = croppedImg;

        // Update dimension tags to match the cropped image
        this.setTag(TagD.get(Tag.Columns), precomputedCrop[2]);
        this.setTag(TagD.get(Tag.Rows), precomputedCrop[3]);

        // Shift ImagePositionPatient to the new top-left corner
        adjustImagePositionPatient(precomputedCrop[0], precomputedCrop[1]);
      }

      File output = new File(directory, sopUID);
      writeImageToFile(output, finalImage);
      return output;
    } catch (Exception e) {
      LOGGER.error("Cannot write DICOM file with precomputed crop", e);
    } finally {
      // Clear the metadata cache so the next slice gets a clean start
      HEADER_CACHE.remove(this);
    }
    return null;
  }

  public File buildCroppedFile(File directory) {
    try {
      // Render the slice – also refreshes SOPInstanceUID, Rows, Columns, IPP, IOP, …
      PlanarImage image = getImageFragment(null);
      if (image == null) return null;

      // Name the output file after the SOPInstanceUID that was just assigned
      String sopUID = (String) tags.get(TagD.get(Tag.SOPInstanceUID));
      if (sopUID == null) {
        sopUID = UIDUtils.createUID();
      }

      double bgValue = volume.getMinimumAsDouble();
      int[] crop = detectNonBlackBoundingBox(image, bgValue, BORDER_CROP_TOLERANCE);

      PlanarImage finalImage = image;
      if (crop != null) {
        // Crop via OpenCV submatrix
        Mat cropped = new Mat(image.toMat(), new Rect(crop[0], crop[1], crop[2], crop[3]));
        ImageCV croppedImg = new ImageCV();
        cropped.copyTo(croppedImg);
        finalImage = croppedImg;

        // Update dimension tags to match the cropped image
        this.setTag(TagD.get(Tag.Columns), crop[2]);
        this.setTag(TagD.get(Tag.Rows), crop[3]);

        // Shift ImagePositionPatient to the new top-left corner
        adjustImagePositionPatient(crop[0], crop[1]);
      }

      File output = new File(directory, sopUID);
      writeImageToFile(output, finalImage);
      return output;
    } catch (Exception e) {
      LOGGER.error("Cannot write cropped DICOM file", e);
    } finally {
      // Clear the metadata cache so the next slice gets a clean start
      HEADER_CACHE.remove(this);
    }
    return null;
  }

  private static int[] detectNonBlackBoundingBox(PlanarImage image, double bgValue, int tolerance) {
    int width = image.width();
    int height = image.height();
    if (width <= 0 || height <= 0) return null;

    Mat src = image.toMat();

    // Binary mask: 255 where pixel != bgValue, 0 otherwise
    Mat mask = buildBackgroundMask(src, bgValue);

    // Per-column sums (1 × width) and per-row sums (height × 1)
    Mat colSums = new Mat();
    Mat rowSums = new Mat();
    Core.reduce(mask, colSums, 0, Core.REDUCE_SUM, CvType.CV_32S);
    Core.reduce(mask, rowSums, 1, Core.REDUCE_SUM, CvType.CV_32S);
    mask.release();

    // Mask values are 0 or 255; threshold in sum space: count > tolerance → sum > tolerance*255
    int sumThreshold = tolerance * 255;

    int[] colData = new int[(int) colSums.total()];
    colSums.get(0, 0, colData);
    colSums.release();

    int[] rowData = new int[(int) rowSums.total()];
    rowSums.get(0, 0, rowData);
    rowSums.release();

    // Scan for first/last content column
    int minX = -1, maxX = -1;
    for (int x = 0; x < width; x++) {
      if (colData[x] > sumThreshold) {
        if (minX < 0) minX = x;
        maxX = x;
      }
    }

    // Scan for first/last content row
    int minY = -1, maxY = -1;
    for (int y = 0; y < height; y++) {
      if (rowData[y] > sumThreshold) {
        if (minY < 0) minY = y;
        maxY = y;
      }
    }

    // Entire image is background
    if (minX < 0 || minY < 0) return null;

    // Add safety margin and clamp to image bounds
    minX = Math.max(0, minX - BORDER_CROP_MARGIN);
    minY = Math.max(0, minY - BORDER_CROP_MARGIN);
    maxX = Math.min(width - 1, maxX + BORDER_CROP_MARGIN);
    maxY = Math.min(height - 1, maxY + BORDER_CROP_MARGIN);

    int cropW = maxX - minX + 1;
    int cropH = maxY - minY + 1;

    // Skip if the bounding box is essentially the whole image
    if (minX == 0 && minY == 0 && cropW == width && cropH == height) return null;

    return new int[] {minX, minY, cropW, cropH};
  }

  /**
   * Builds a single-channel binary mask: 255 where the pixel differs from {@code bgValue}, 0
   * elsewhere. For multi-channel images a pixel is treated as content if ANY channel differs.
   */
  private static Mat buildBackgroundMask(Mat src, double bgValue) {
    Scalar bg = new Scalar(bgValue);
    if (src.channels() == 1) {
      Mat mask = new Mat();
      Core.compare(src, bg, mask, Core.CMP_NE);
      return mask;
    }
    // Multi-channel: OR of per-channel masks
    Mat mask = new Mat();
    Mat channelImg = new Mat();
    Mat chMask = new Mat();
    Core.extractChannel(src, channelImg, 0);
    Core.compare(channelImg, bg, mask, Core.CMP_NE);
    for (int c = 1; c < src.channels(); c++) {
      Core.extractChannel(src, channelImg, c);
      Core.compare(channelImg, bg, chMask, Core.CMP_NE);
      Core.bitwise_or(mask, chMask, mask);
    }
    channelImg.release();
    chMask.release();
    return mask;
  }

  /** Shifts {@code ImagePositionPatient} to the new top-left corner after a pixel-level crop. */
  private void adjustImagePositionPatient(int cropX, int cropY) {
    double[] ipp = (double[]) tags.get(TagD.get(Tag.ImagePositionPatient));
    double[] iop = (double[]) tags.get(TagD.get(Tag.ImageOrientationPatient));
    if (ipp == null || iop == null) return;

    double ps = volume.getMinPixelRatio(); // pixel spacing in mm
    // IOP[0..2] = row direction cosine; IOP[3..5] = column direction cosine
    double newX = ipp[0] + cropX * ps * iop[0] + cropY * ps * iop[3];
    double newY = ipp[1] + cropX * ps * iop[1] + cropY * ps * iop[4];
    double newZ = ipp[2] + cropX * ps * iop[2] + cropY * ps * iop[5];

    this.setTag(TagD.get(Tag.ImagePositionPatient), new double[] {newX, newY, newZ});
  }

  private boolean writeImageToFile(File output, PlanarImage image) throws Exception {
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

  private synchronized DicomMetaData readMetaData() {
    DicomMetaData header = HEADER_CACHE.get(this);
    if (header != null) {
      return header;
    }
    Attributes dcm = getDicomObject();
    header = new DicomMetaData(dcm, UID.ImplicitVRLittleEndian);
    MinMaxLocResult minMax = new MinMaxLocResult();
    minMax.minVal = volume.getMinimumAsDouble();
    minMax.maxVal = volume.getMaximumAsDouble();
    header.getImageDescriptor().setMinMaxPixelValue(0, minMax);
    HEADER_CACHE.put(this, header);
    return header;
  }

  public static void saveVolumeInFile(Volume<?, ?> volume, Path file) {
    try (DataOutputStream dos =
        new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(file)))) {

      Vector3i size = volume.getSize();
      int cvType = volume.getCvType();
      Vector3d pixelRatio = volume.getPixelRatio();

      dos.writeInt(size.x);
      dos.writeInt(size.y);
      dos.writeInt(size.z);
      dos.writeInt(cvType);
      switch (CvType.depth(cvType)) {
        case CvType.CV_8S, CvType.CV_8U -> {
          dos.writeByte(volume.minValue.byteValue());
          dos.writeByte(volume.maxValue.byteValue());
        }
        case CvType.CV_16S, CvType.CV_16U -> {
          dos.writeShort(volume.minValue.shortValue());
          dos.writeShort(volume.maxValue.shortValue());
        }
        case CvType.CV_32S -> {
          dos.writeInt(volume.minValue.intValue());
          dos.writeInt(volume.maxValue.intValue());
        }
        case CvType.CV_32F -> {
          dos.writeFloat(volume.minValue.floatValue());
          dos.writeFloat(volume.maxValue.floatValue());
        }
        case CvType.CV_64F -> {
          dos.writeDouble(volume.minValue.doubleValue());
          dos.writeDouble(volume.maxValue.doubleValue());
        }
        default -> throw new IOException("Unsupported volume data type: " + CvType.depth(cvType));
      }
      dos.writeDouble(pixelRatio.x);
      dos.writeDouble(pixelRatio.y);
      dos.writeDouble(pixelRatio.z);

      for (int z = 0; z < size.z; z++) {
        for (int y = 0; y < size.y; y++) {
          for (int x = 0; x < size.x; x++) {
            volume.writeVolume(dos, x, y, z);
          }
        }
      }
    } catch (IOException e) {
      LOGGER.error("Cannot save volume in file", e);
    }
  }

  public static Volume<?, ?> readVolumeFromFile(Path file, JProgressBar progressBar) {
    try (DataInputStream dis =
        new DataInputStream(new BufferedInputStream(Files.newInputStream(file)))) {
      int sizeX = dis.readInt();
      int sizeY = dis.readInt();
      int sizeZ = dis.readInt();
      int cvType = dis.readInt();
      int depth = CvType.depth(cvType);
      int channels = CvType.channels(cvType);
      boolean signed = depth == CvType.CV_16S || depth == CvType.CV_8S;

      Volume<?, ?> volume =
          switch (depth) {
            case CvType.CV_8S, CvType.CV_8U -> {
              VolumeByte v = new VolumeByte(sizeX, sizeY, sizeZ, signed, channels, progressBar);
              v.minValue = dis.readByte();
              v.maxValue = dis.readByte();
              yield v;
            }
            case CvType.CV_16S, CvType.CV_16U -> {
              VolumeShort v = new VolumeShort(sizeX, sizeY, sizeZ, signed, channels, progressBar);
              v.minValue = dis.readShort();
              v.maxValue = dis.readShort();
              yield v;
            }
            case CvType.CV_32S -> {
              VolumeInt v = new VolumeInt(sizeX, sizeY, sizeZ, channels, progressBar);
              v.minValue = dis.readInt();
              v.maxValue = dis.readInt();
              yield v;
            }
            case CvType.CV_32F -> {
              VolumeFloat v = new VolumeFloat(sizeX, sizeY, sizeZ, channels, progressBar);
              v.minValue = dis.readFloat();
              v.maxValue = dis.readFloat();
              yield v;
            }
            case CvType.CV_64F -> {
              VolumeDouble v = new VolumeDouble(sizeX, sizeY, sizeZ, channels, progressBar);
              v.minValue = dis.readDouble();
              v.maxValue = dis.readDouble();
              yield v;
            }
            default -> throw new IOException("Unsupported volume data type: " + depth);
          };

      volume.pixelRatio.x = dis.readDouble();
      volume.pixelRatio.y = dis.readDouble();
      volume.pixelRatio.z = dis.readDouble();

      for (int z = 0; z < sizeZ; z++) {
        for (int y = 0; y < sizeY; y++) {
          for (int x = 0; x < sizeX; x++) {
            volume.readVolume(dis, x, y, z);
          }
        }
      }
      return volume;
    } catch (IOException e) {
      LOGGER.error("Cannot read volume from file", e);
    }
    return null;
  }
}
