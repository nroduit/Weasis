/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.api.image.cv;

import java.awt.image.BandedSampleModel;
import java.awt.image.BufferedImage;
import java.awt.image.IndexColorModel;
import java.awt.image.PackedColorModel;
import java.awt.image.RenderedImage;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.stream.FileImageInputStream;
import javax.imageio.stream.ImageInputStream;
import org.opencv.core.Core;
import org.opencv.core.Core.MinMaxLocResult;
import org.opencv.core.CvException;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfInt;
import org.opencv.imgcodecs.Imgcodecs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.explorer.ObservableEvent;
import org.weasis.core.api.explorer.model.AbstractFileModel;
import org.weasis.core.api.explorer.model.DataExplorerModel;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.gui.util.AppProperties;
import org.weasis.core.api.image.color.IccProfile;
import org.weasis.core.api.media.MimeInspector;
import org.weasis.core.api.media.data.Codec;
import org.weasis.core.api.media.data.FileCache;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.api.media.data.MediaElement;
import org.weasis.core.api.media.data.MediaReader;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.media.data.Series;
import org.weasis.core.api.media.data.SeriesEvent;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.api.media.data.Thumbnail;
import org.weasis.core.internal.cv.NativeOpenCVCodec;
import org.weasis.core.util.FileUtil;
import org.weasis.core.util.MathUtil;
import org.weasis.core.util.StringUtil;
import org.weasis.opencv.data.FileRawImage;
import org.weasis.opencv.data.ImageCV;
import org.weasis.opencv.data.MetadataParser;
import org.weasis.opencv.data.PlanarImage;
import org.weasis.opencv.op.ImageAnalyzer;
import org.weasis.opencv.op.ImageConversion;
import org.weasis.opencv.op.ImageIOHandler;

/** Media reader for non-DICOM image files supporting reading, metadata extraction, and caching. */
public class ImageCVIO implements MediaReader<ImageElement> {
  private static final Logger LOGGER = LoggerFactory.getLogger(ImageCVIO.class);

  public static final int TILE_SIZE = 512;
  public static final Path CACHE_UNCOMPRESSED_DIR =
      AppProperties.buildAccessibleTempDirectory(AppProperties.CACHE_NAME, "uncompressed");

  private static final String WCV_EXTENSION = ".wcv";
  private static final String JPEG_EXTENSION = ".jpg";
  private static final String DICOM_MIME_TYPE = "dicom";
  private static final double MIN_MAX_EQUAL_OFFSET = 1.0;

  private final URI uri;
  private final String mimeType;

  private final FileCache fileCache;
  private final Codec codec;
  private ImageElement image;

  public ImageCVIO(URI media, String mimeType, Codec codec) {
    this.uri = Objects.requireNonNull(media);
    this.fileCache = new FileCache(this);
    this.mimeType = Objects.requireNonNullElse(mimeType, MimeInspector.UNKNOWN_MIME_TYPE);
    this.codec = codec;
  }

  @Override
  public PlanarImage getImageFragment(MediaElement media) throws Exception {
    Objects.requireNonNull(media);

    Path imagePath = resolveImagePath(media);
    if (imagePath == null) {
      return null;
    }

    Path cachePath = getCachePathIfNeeded(media);
    PlanarImage img = readImage(imagePath);

    if (cachePath != null && img != null) {
      return processCachedImage(media, img, cachePath);
    }

    return img;
  }

  @Override
  public URI getUri() {
    return uri;
  }

  @Override
  public MediaElement getPreview() {
    return getSingleImage();
  }

  @Override
  public boolean delegate(DataExplorerModel explorerModel) {
    return false;
  }

  @Override
  public ImageElement[] getMediaElement() {
    return new ImageElement[] {getSingleImage()};
  }

  @Override
  public MediaSeries<ImageElement> getMediaSeries() {
    String seriesUID = getSeriesUID();
    MediaSeries<ImageElement> series = createMediaSeries(seriesUID);

    ImageElement img = getSingleImage();
    series.add(img);
    series.setTag(TagW.FileName, img.getName());
    return series;
  }

  @Override
  public int getMediaElementNumber() {
    return 1;
  }

  @Override
  public String getMediaFragmentMimeType() {
    return mimeType;
  }

  @Override
  public Map<TagW, Object> getMediaFragmentTags(Object key) {
    return new HashMap<>();
  }

  @Override
  public void close() {
    // No resources to close
  }

  @Override
  public Codec getCodec() {
    return codec;
  }

  @Override
  public String[] getReaderDescription() {
    return new String[] {
      "Image Codec: " + codec.getCodecName(), "Version: " + Core.VERSION // NON-NLS
    };
  }

  @Override
  public Object getTagValue(TagW tag) {
    return tag != null ? getSingleImage().getTagValue(tag) : null;
  }

  @Override
  public void replaceURI(URI uri) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void setTag(TagW tag, Object value) {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean containTagKey(TagW tag) {
    return false;
  }

  @Override
  public void setTagNoNull(TagW tag, Object value) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Iterator<Entry<TagW, Object>> getTagEntrySetIterator() {
    throw new UnsupportedOperationException();
  }

  @Override
  public FileCache getFileCache() {
    return fileCache;
  }

  @Override
  public boolean buildFile(File output) {
    return false;
  }

  private Path resolveImagePath(MediaElement media) throws NoSuchAlgorithmException {
    FileCache cache = media.getFileCache();

    if (!cache.isRequireTransformation()) {
      return cache.getOriginalFile().orElse(null);
    }

    Path transformed = cache.getTransformedFile();
    if (transformed != null) {
      return transformed;
    }

    Path cachePath = buildCachePath(media);

    if (Files.isReadable(cachePath)) {
      cache.setTransformedFile(cachePath);
      return cachePath;
    }

    return cache.getOriginalFile().orElse(null);
  }

  private Path getCachePathIfNeeded(MediaElement media) throws NoSuchAlgorithmException {
    FileCache cache = media.getFileCache();
    if (!cache.isRequireTransformation() || cache.getTransformedFile() != null) {
      return null;
    }

    Path cachePath = buildCachePath(media);
    return Files.isReadable(cachePath) ? null : cachePath;
  }

  private Path buildCachePath(MediaElement media) throws NoSuchAlgorithmException {
    String filename =
        StringUtil.bytesToMD5(media.getMediaURI().toString().getBytes(StandardCharsets.UTF_8));
    return CACHE_UNCOMPRESSED_DIR.resolve(filename + WCV_EXTENSION);
  }

  private PlanarImage processCachedImage(MediaElement media, PlanarImage img, Path cachePath)
      throws Exception {
    Path rawFile = uncompressIfNeeded(img, cachePath, media);

    if (rawFile != null) {
      media.getFileCache().setTransformedFile(rawFile);
      return readImage(rawFile);
    }

    return img;
  }

  private PlanarImage readImage(Path path) throws Exception {
    if (path.getFileName().toString().endsWith(WCV_EXTENSION)) {
      return new FileRawImage(path).read();
    }

    if (codec instanceof NativeOpenCVCodec) {
      return readWithOpenCV(path);
    }

    return readWithImageIO(path);
  }

  private PlanarImage readWithOpenCV(Path path) {
    ImageIOHandler.validateReadablePath(path);
    MatOfInt metadataTypes = new MatOfInt();
    List<Mat> metadata = new ArrayList<>();
    try {
      Mat mat =
          Imgcodecs.imreadWithMetadata(path.toAbsolutePath().toString(), metadataTypes, metadata);
      ImageCV img;
      try {
        if (mat.empty()) {
          throw new CvException("Failed to read image or unsupported format: " + path);
        }
        img = ImageCV.fromMat(mat);
      } finally {
        mat.release();
      }
      ImageElement element = getSingleImage();
      applyExifTags(element, MetadataParser.parseExifParseMetadata(metadata, metadataTypes));
      if (element != null) {
        element.setTagNoNull(TagW.IccProfile, iccProfile(metadata, metadataTypes, path));
      }
      updateImageDimensions(img);
      return img;
    } finally {
      metadataTypes.release();
      metadata.forEach(Mat::release);
    }
  }

  /** The embedded profile (PNG iCCP, JPEG APP2, WebP, AVIF), shown converted by the display. */
  private static IccProfile iccProfile(List<Mat> metadata, MatOfInt types, Path path) {
    int[] values = types.empty() ? new int[0] : types.toArray();
    for (int i = 0; i < values.length && i < metadata.size(); i++) {
      Mat blob = metadata.get(i);
      if (values[i] == Imgcodecs.IMAGE_METADATA_ICCP && !blob.empty()) {
        byte[] bytes = new byte[(int) (blob.total() * blob.elemSize())];
        blob.get(0, 0, bytes);
        IccProfile profile = IccProfile.of(bytes).orElse(null);
        if (profile == null) {
          LOGGER.warn("Invalid embedded ICC profile in {}, ignored", path);
        }
        return profile;
      }
    }
    return null;
  }

  private PlanarImage readWithImageIO(Path path) throws IOException {
    ImageReader reader = ImageIO.getImageReadersByMIMEType(mimeType).next();
    if (reader == null) {
      LOGGER.info("Cannot find a reader for the mime type: {}", mimeType);
      return null;
    }

    try (ImageInputStream stream =
        new FileImageInputStream(new RandomAccessFile(path.toFile(), "r"))) {
      reader.setInput(stream, true, true);
      ImageReadParam param = reader.getDefaultReadParam();

      RenderedImage bi;
      try {
        bi = reader.read(0, param);
      } finally {
        reader.dispose();
      }

      bi = convertToReadableImage(bi);
      PlanarImage result = ImageConversion.toMat(bi);
      updateImageDimensions(result);

      return result;
    }
  }

  private void updateImageDimensions(PlanarImage img) {
    if (img != null && image != null) {
      image.setTag(TagW.ImageWidth, img.width());
      image.setTag(TagW.ImageHeight, img.height());
    }
  }

  private ImageElement getSingleImage() {
    if (image == null) {
      image = new ImageElement(this, 0);
    }
    return image;
  }

  private String getSeriesUID() {
    MediaElement element = getSingleImage();
    String sUID = (String) element.getTagValue(TagW.get("SeriesInstanceUID"));
    return sUID != null ? sUID : uri.toString();
  }

  private MediaSeries<ImageElement> createMediaSeries(String seriesUID) {
    return new Series<>(TagW.SubseriesInstanceUID, seriesUID, AbstractFileModel.series.tagView()) {
      @Override
      public String getMimeType() {
        synchronized (this) {
          return medias.isEmpty() ? null : medias.getFirst().getMimeType();
        }
      }

      @Override
      public void addMedia(ImageElement media) {
        if (media != null) {
          this.add(media);
          notifyDataExplorerModel(media);
        }
      }

      @Override
      public MediaElement getFirstSpecialElement() {
        return null;
      }

      private void notifyDataExplorerModel(ImageElement media) {
        DataExplorerModel model = (DataExplorerModel) getTagValue(TagW.ExplorerModel);
        if (model != null) {
          model.firePropertyChange(
              new ObservableEvent(
                  ObservableEvent.BasicAction.ADD,
                  model,
                  null,
                  new SeriesEvent(SeriesEvent.Action.ADD_IMAGE, this, media)));
        }
      }
    };
  }

  private Path uncompressIfNeeded(PlanarImage img, Path cachePath, MediaElement media) {
    if (!shouldUncompress(img)) {
      return null;
    }
    try {
      new FileRawImage(cachePath).write(img);
      writeThumbnail(cachePath, img, media);
      return cachePath;
    } catch (Exception e) {
      FileUtil.delete(cachePath);
      LOGGER.error("Failed to uncompress temporary image", e);
      return null;
    }
  }

  private boolean shouldUncompress(PlanarImage img) {
    return img != null
        && (img.width() > TILE_SIZE || img.height() > TILE_SIZE)
        && !mimeType.contains(DICOM_MIME_TYPE);
  }

  private void writeThumbnail(Path outFile, PlanarImage img, MediaElement media) {
    PlanarImage thumbnailImg = prepareThumbnailImage(img, media);
    Path thumbnailPath = Path.of(changeExtension(outFile.toString(), JPEG_EXTENSION));
    ImageIOHandler.writeThumbnail(thumbnailImg.toMat(), thumbnailPath, Thumbnail.MAX_SIZE);
  }

  private PlanarImage prepareThumbnailImage(PlanarImage img, MediaElement media) {
    if (CvType.depth(img.type()) <= CvType.CV_8S || !(media instanceof ImageElement imgElement)) {
      return img;
    }

    Map<String, Object> params = imgElement.isImageAvailable() ? null : createRenderingParams(img);
    return imgElement.getRenderedImage(img, params);
  }

  private Map<String, Object> createRenderingParams(PlanarImage img) {
    MinMaxLocResult val = ImageAnalyzer.findMinMaxValues(img.toMat());
    double min = val.minVal;
    double max = MathUtil.isEqual(min, val.maxVal) ? val.maxVal + MIN_MAX_EQUAL_OFFSET : val.maxVal;

    return Map.of(ActionW.WINDOW.cmd(), max - min, ActionW.LEVEL.cmd(), min + (max - min) / 2.0);
  }

  private static void applyExifTags(ImageElement img, List<String> exifTags) {
    if (exifTags.size() < Imgcodecs.POS_COPYRIGHT) {
      return;
    }

    setTagIfPresent(img, TagW.ExifImageDescription, exifTags.get(Imgcodecs.POS_IMAGE_DESCRIPTION));
    setTagIfPresent(img, TagW.ExifMake, exifTags.get(Imgcodecs.POS_MAKE));
    setTagIfPresent(img, TagW.ExifModel, exifTags.get(Imgcodecs.POS_MODEL));
    setTagIfPresent(img, TagW.ExifOrientation, exifTags.get(Imgcodecs.POS_ORIENTATION));
    setTagIfPresent(img, TagW.ExifXResolution, exifTags.get(Imgcodecs.POS_XRESOLUTION));
    setTagIfPresent(img, TagW.ExifYResolution, exifTags.get(Imgcodecs.POS_YRESOLUTION));
    setTagIfPresent(img, TagW.ExifResolutionUnit, exifTags.get(Imgcodecs.POS_RESOLUTION_UNIT));
    setTagIfPresent(img, TagW.ExifSoftware, exifTags.get(Imgcodecs.POS_SOFTWARE));
    setTagIfPresent(img, TagW.ExifDateTime, exifTags.get(Imgcodecs.POS_DATE_TIME));
    setTagIfPresent(img, TagW.ExifCopyright, exifTags.get(Imgcodecs.POS_COPYRIGHT));
  }

  private static void setTagIfPresent(ImageElement img, TagW tag, String value) {
    if (StringUtil.hasText(value)) {
      img.setTag(tag, value);
    }
  }

  public static RenderedImage convertToReadableImage(RenderedImage source) {
    if (source == null || source.getSampleModel() == null) {
      return source;
    }
    if (ImageConversion.isBinary(source.getSampleModel())) {
      return ImageConversion.convertTo(source, BufferedImage.TYPE_BYTE_GRAY);
    }

    int numBands = source.getSampleModel().getNumBands();
    if (requiresConversion(source, numBands)) {
      int imageType = numBands >= 3 ? BufferedImage.TYPE_3BYTE_BGR : BufferedImage.TYPE_BYTE_GRAY;
      return ImageConversion.convertTo(source, imageType);
    }

    return source;
  }

  private static boolean requiresConversion(RenderedImage source, int numBands) {
    return source.getColorModel() instanceof PackedColorModel
        || source.getColorModel() instanceof IndexColorModel
        || numBands == 2
        || numBands > 3
        || (source.getSampleModel() instanceof BandedSampleModel && numBands > 1);
  }

  public static String changeExtension(String filename, String ext) {
    if (filename == null) {
      return "";
    }
    int pointPos = filename.lastIndexOf('.');
    return filename.substring(0, pointPos == -1 ? filename.length() : pointPos) + ext;
  }
}
