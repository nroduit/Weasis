package org.weasis.base.explorer;

import java.awt.image.BufferedImage;
import java.awt.image.RenderedImage;
import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.media.jai.PlanarImage;
import javax.media.jai.operator.SubsampleAverageDescriptor;
import javax.swing.Icon;

import org.weasis.core.api.gui.util.GuiExecutor;
import org.weasis.core.api.image.util.ImageFiler;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.api.media.data.Thumbnail;

import com.sun.media.jai.codec.FileSeekableStream;
import com.sun.media.jai.codec.ImageCodec;
import com.sun.media.jai.codec.ImageDecoder;

public final class JIThumbnailCache {

    private static JIThumbnailCache instance;
    // Set only one concurrent thread, because of the imageio issue with native library
    // (https://jai-imageio-core.dev.java.net/issues/show_bug.cgi?id=126)
    private static final ExecutorService qExecutor = Executors.newFixedThreadPool(1);

    private final Map<String, ThumbnailIcon> cachedThumbnails;

    private JIThumbnailCache() {

        this.cachedThumbnails = Collections.synchronizedMap(new LinkedHashMap<String, ThumbnailIcon>(80) {

            private static final int MAX_ENTRIES = 50;

            @Override
            protected boolean removeEldestEntry(final Map.Entry eldest) {
                return size() > MAX_ENTRIES;
            }
        });
    }

    public static JIThumbnailCache getInstance() {
        if (instance == null) {
            instance = new JIThumbnailCache();
        }
        return instance;
    }

    public synchronized void invalidate() {
        this.cachedThumbnails.clear();
    }

    public Icon getThumbnailFor(final ImageElement diskObject, final JIThumbnailList list, final int index) {
        try {

            final ThumbnailIcon jiIcon = this.cachedThumbnails.get(diskObject.getFile().getPath());
            if (jiIcon != null) {
                return jiIcon;
            }

        } catch (final Exception e) {
            // log.debug(e);
        }
        if (!diskObject.isLoading()) {
            loadThumbnail(diskObject, list, index);
        }
        return null;
    }

    private void loadThumbnail(final ImageElement diskObject, final JIThumbnailList thumbnailList, final int index) {
        if ((index > thumbnailList.getLastVisibleIndex()) || (index < thumbnailList.getFirstVisibleIndex())) {
            return;
        }
        try {
            // ///////////////////////////////////////////////////////////////////////
            JIThumbnailCache.qExecutor.execute(new Runnable() {

                @Override
                public void run() {
                    RenderedImage img = null;
                    String mime = diskObject.getMimeType();
                    if (mime == null) {
                        mime = "";
                    }
                    String cfile = diskObject.getFile().getAbsolutePath();
                    String tiled_File = thumbnailList.getThumbnailListModel().getFileInCache(cfile);
                    if (tiled_File != null) {
                        try {
                            ImageDecoder dec =
                                ImageCodec.createImageDecoder("tiff", new FileSeekableStream(tiled_File == null
                                    ? diskObject.getFile() : new File(JIListModel.EXPLORER_CACHE_DIR, tiled_File)),
                                    null);
                            int count = dec.getNumPages();
                            if (count == 2) {
                                RenderedImage src2 = dec.decodeAsRenderedImage(1);
                                if (src2.getWidth() <= Thumbnail.MAX_SIZE) {
                                    img = src2;
                                }
                            }
                        } catch (IOException ex) {
                            ex.printStackTrace();
                        }
                    }

                    if (img == null) {
                        img = diskObject.getRenderedImage(diskObject.getImage(null));
                    }
                    if (img == null) {
                        return;
                    }

                    if (tiled_File == null) {

                        /*
                         * Make an image cache with its thumbnail when the image size is larger than a tile size and if
                         * not DICOM file
                         */

                        if ((img.getWidth() > ImageFiler.TILESIZE || img.getHeight() > ImageFiler.TILESIZE)
                            && !mime.contains("dicom")) {
                            File imgCacheFile = null;
                            try {
                                imgCacheFile = File.createTempFile("tiled_", ".tif", JIListModel.EXPLORER_CACHE_DIR); //$NON-NLS-1$ //$NON-NLS-2$
                            } catch (IOException e) {
                                e.printStackTrace();
                            }

                            if (ImageFiler.writeTIFF(imgCacheFile, img, true, true, false)) {
                                thumbnailList.getThumbnailListModel().putFileInCache(cfile, imgCacheFile.getName());
                            } else {
                                // TODO make it invalid
                            }
                            return;
                        }
                    }

                    final double scale =
                        Math.min(ThumbnailRenderer.ICON_DIM.height / (double) img.getHeight(),
                            ThumbnailRenderer.ICON_DIM.width / (double) img.getWidth());

                    final BufferedImage tIcon =
                        scale <= 1.0 ? scale > 0.005 ? SubsampleAverageDescriptor.create(img, scale, scale,
                            Thumbnail.DownScaleQualityHints).getAsBufferedImage() : null : PlanarImage
                            .wrapRenderedImage(img).getAsBufferedImage();

                    // DO not close the stream
                    // diskObject.dispose();

                    GuiExecutor.instance().execute(new Runnable() {

                        @Override
                        public void run() {
                            if (tIcon != null) {
                                JIThumbnailCache.this.cachedThumbnails.put(diskObject.getFile().getPath(),
                                    new ThumbnailIcon(tIcon));
                            }
                            thumbnailList.getThumbnailListModel().notifyAsUpdated(index);
                        }
                    });

                }
            });
            // ///////////////////////////////////////////////////////////////////////

        } catch (final Exception exp) {
            // log.debug(exp);
        }
    }
}
