/*******************************************************************************
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.weasis.core.api.image.cv;

import java.awt.image.RenderedImage;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Iterator;
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
import org.opencv.core.CvType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.explorer.ObservableEvent;
import org.weasis.core.api.explorer.model.AbstractFileModel;
import org.weasis.core.api.explorer.model.DataExplorerModel;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.gui.util.AppProperties;
import org.weasis.core.api.gui.util.MathUtil;
import org.weasis.core.api.image.util.ImageFiler;
import org.weasis.core.api.internal.cv.NativeOpenCVCodec;
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
import org.weasis.core.util.FileUtil;
import org.weasis.core.util.StringUtil;
import org.weasis.opencv.data.FileRawImage;
import org.weasis.opencv.data.PlanarImage;
import org.weasis.opencv.op.ImageConversion;
import org.weasis.opencv.op.ImageProcessor;

public class ImageCVIO implements MediaReader {
    private static final Logger LOGGER = LoggerFactory.getLogger(ImageCVIO.class);

    public static final File CACHE_UNCOMPRESSED_DIR =
        AppProperties.buildAccessibleTempDirectory(AppProperties.FILE_CACHE_DIR.getName(), "uncompressed"); //$NON-NLS-1$

    private final URI uri;
    private final String mimeType;

    private final FileCache fileCache;
    private final Codec codec;
    private ImageElement image = null;

    public ImageCVIO(URI media, String mimeType, Codec codec) {
        this.uri = Objects.requireNonNull(media);
        this.fileCache = new FileCache(this);
        if (mimeType == null) {
            this.mimeType = MimeInspector.UNKNOWN_MIME_TYPE;
        } else {
            this.mimeType = mimeType;
        }
        this.codec = codec;
    }

    @Override
    public PlanarImage getImageFragment(MediaElement media) throws Exception {
        Objects.requireNonNull(media);
        FileCache cache = media.getFileCache();

        Path imgCachePath = null;
        File file;
        if (cache.isRequireTransformation()) {
            file = cache.getTransformedFile();
            if (file == null) {
                String filename = StringUtil.bytesToMD5(media.getMediaURI().toString().getBytes());
                imgCachePath = CACHE_UNCOMPRESSED_DIR.toPath().resolve(filename + ".wcv"); //$NON-NLS-1$
                if (Files.isReadable(imgCachePath)) {
                    file = imgCachePath.toFile();
                    cache.setTransformedFile(file);
                    imgCachePath = null;
                } else {
                    file = cache.getOriginalFile().orElse(null);
                }
            }
        } else {
            file = cache.getOriginalFile().orElse(null);
        }

        if (file != null) {
            PlanarImage img = readImage(file, imgCachePath == null);

            if (imgCachePath != null) {

                File rawFile = uncompress(imgCachePath, img, media);
                if (rawFile != null) {
                    file = rawFile;
                }
                cache.setTransformedFile(file);
                img = readImage(file, true);
            }
            return img;
        }
        return null;
    }

    private PlanarImage readImage(File file, boolean createTiledLayout) throws Exception {
        PlanarImage img = null;
        if (file.getPath().endsWith(".wcv")) { //$NON-NLS-1$
            img = new FileRawImage(file).read();
        } else if (codec instanceof NativeOpenCVCodec) {
            img = ImageProcessor.readImageWithCvException(file);
            if (img == null) {
                // Try ImageIO
                img = readImageIOImage(file);
            }
        } else {
            img = readImageIOImage(file);
        }

        if (img != null && image != null) {
            image.setTag(TagW.ImageWidth, img.width());
            image.setTag(TagW.ImageHeight, img.height());
        }
        return img;
    }

    private PlanarImage readImageIOImage(File file) throws IOException {
        ImageReader reader = getDefaultReader(mimeType);
        if (reader == null) {
            LOGGER.info("Cannot find a reader for the mime type: {}", mimeType); //$NON-NLS-1$
            return null;
        }

        ImageInputStream stream = new FileImageInputStream(new RandomAccessFile(file, "r")); //$NON-NLS-1$
        ImageReadParam param = reader.getDefaultReadParam();
        reader.setInput(stream, true, true);
        RenderedImage bi;
        try {
            bi = reader.read(0, param);
        } finally {
            reader.dispose();
            stream.close();
        }

        // to avoid problem with alpha channel and png encoded in 24 and 32 bits
        bi = ImageFiler.getReadableImage(bi);
        return ImageConversion.toMat(bi);
    }

    @Override
    public URI getUri() {
        return uri;
    }

    @Override
    public void reset() {
        // Do nothing
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
    public MediaElement[] getMediaElement() {
        MediaElement element = getSingleImage();
        if (element != null) {
            return new MediaElement[] { element };
        }
        return null;
    }

    @Override
    public MediaSeries<MediaElement> getMediaSeries() {
        String sUID = null;
        MediaElement element = getSingleImage();
        if (element != null) {
            sUID = (String) element.getTagValue(TagW.get("SeriesInstanceUID")); //$NON-NLS-1$
        }
        if (sUID == null) {
            sUID = uri == null ? "unknown" : uri.toString(); //$NON-NLS-1$
        }
        MediaSeries<MediaElement> series =
            new Series<MediaElement>(TagW.SubseriesInstanceUID, sUID, AbstractFileModel.series.getTagView()) { // $NON-NLS-1$

                @Override
                public String getMimeType() {
                    if (!medias.isEmpty()) {
                        synchronized (this) {
                            MediaElement img = medias.get(0);
                            if (img != null) {
                                return img.getMimeType();
                            }
                        }
                    }
                    return null;
                }

                @Override
                public void addMedia(MediaElement media) {
                    if (media instanceof ImageElement) {
                        this.add(media);
                        DataExplorerModel model = (DataExplorerModel) getTagValue(TagW.ExplorerModel);
                        if (model != null) {
                            model.firePropertyChange(new ObservableEvent(ObservableEvent.BasicAction.ADD, model, null,
                                new SeriesEvent(SeriesEvent.Action.ADD_IMAGE, this, media)));
                        }
                    }
                }
            };

        ImageElement img = getSingleImage();
        if (img != null) {
            series.add(getSingleImage());
            series.setTag(TagW.FileName, img.getName());
        }
        return series;
    }

    @Override
    public int getMediaElementNumber() {
        return 1;
    }

    private ImageElement getSingleImage() {
        if (image == null) {
            image = new ImageElement(this, 0);
        }
        return image;
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
        // Do nothing
    }

    @Override
    public Codec getCodec() {
        return codec;
    }

    @Override
    public String[] getReaderDescription() {
        return new String[] { "Image Codec: " + codec.getCodecName(), "Version: " + Core.VERSION }; //$NON-NLS-1$ //$NON-NLS-2$
    }

    public ImageReader getDefaultReader(String mimeType) {
        if (mimeType != null) {
            Iterator readers = ImageIO.getImageReadersByMIMEType(mimeType);
            if (readers.hasNext()) {
                return (ImageReader) readers.next();
            }
        }
        return null;
    }

    @Override
    public Object getTagValue(TagW tag) {
        MediaElement element = getSingleImage();
        if (tag != null && element != null) {
            return element.getTagValue(tag);
        }
        return null;
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

    private File uncompress(Path imgCachePath, PlanarImage img, MediaElement media) {
        /*
         * Make an image cache with its thumbnail when the image size is larger than a tile size and if not DICOM file
         */
        if (img != null && (img.width() > ImageFiler.TILESIZE || img.height() > ImageFiler.TILESIZE)
            && !mimeType.contains("dicom")) { //$NON-NLS-1$
            File outFile = imgCachePath.toFile();
            try {
                new FileRawImage(outFile).write(img);
                PlanarImage img8 = img;
                if (CvType.depth(img.type()) > CvType.CV_8S && media instanceof ImageElement) {
                    ImageElement imgElement = ((ImageElement) media);
                    Map<String, Object> params = null;
                    if (!imgElement.isImageAvailable()) {
                        // Ensure to load image before calling the default preset that requires pixel min and max
                        params = new HashMap<>(2);
                        double min = 0;
                        double max = 65536;
                        MinMaxLocResult val = ImageProcessor.findMinMaxValues(img.toMat());
                        if (val != null) {
                            min = val.minVal;
                            max = val.maxVal;
                        }

                        // Handle special case when min and max are equal, ex. black image
                        // + 1 to max enables to display the correct value
                        if (MathUtil.isEqual(min, max)) {
                            max += 1.0;
                        }

                        params.put(ActionW.WINDOW.cmd(), max - min);
                        params.put(ActionW.LEVEL.cmd(), min + (max - min) / 2.0);
                    }
                    img8 = imgElement.getRenderedImage(img, params);

                }
                ImageProcessor.writeThumbnail(img8.toMat(),
                    new File(ImageFiler.changeExtension(outFile.getPath(), ".jpg")), Thumbnail.MAX_SIZE); //$NON-NLS-1$
                return outFile;
            } catch (Exception e) {
                FileUtil.delete(outFile);
                LOGGER.error("Uncompress temporary image", e); //$NON-NLS-1$
            }
        }
        return null;
    }

    @Override
    public boolean buildFile(File ouptut) {
        return false;
    }
}
