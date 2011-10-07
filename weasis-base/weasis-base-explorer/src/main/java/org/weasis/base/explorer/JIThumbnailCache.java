package org.weasis.base.explorer;

import java.awt.image.BufferedImage;
import java.awt.image.RenderedImage;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.media.jai.PlanarImage;
import javax.media.jai.operator.SubsampleAverageDescriptor;
import javax.swing.Icon;

import org.weasis.core.api.gui.util.GuiExecutor;
import org.weasis.core.api.image.util.ImageToolkit;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.api.media.data.Thumbnail;

public final class JIThumbnailCache {

    private static JIThumbnailCache instance;
    // Set only one concurrent thread, because of the imageio issue with native library
    // (https://jai-imageio-core.dev.java.net/issues/show_bug.cgi?id=126)
    private static final ExecutorService qExecutor = Executors.newFixedThreadPool(1);

    private final Map<String, ThumbnailIcon> cachedThumbnails;

    private JIThumbnailCache() {

        this.cachedThumbnails = Collections.synchronizedMap(new LinkedHashMap<String, ThumbnailIcon>(80) {

            /**
			 *
			 */
            private static final long serialVersionUID = 6299868061343240330L;
            private static final int MAX_ENTRIES = 500;

            @Override
            protected boolean removeEldestEntry(final Map.Entry eldest) {
                return size() > MAX_ENTRIES;
            }
        });

        // ImageIO.setUseCache(false);
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
                    // final BufferedImage tIcon = JIUtility.createThumbnailRetry(diskObject);
                    RenderedImage img = ImageToolkit.getDefaultRenderedImage(diskObject, diskObject.getImage(null));
                    if (img == null) {
                        return;
                    }
                    final double scale =
                        Math.min((double) ThumbnailRenderer.ICON_DIM.height / (double) img.getHeight(),
                            (double) ThumbnailRenderer.ICON_DIM.width / (double) img.getWidth());

                    final BufferedImage tIcon =
                        scale < 1.0 ? scale > 0.005 ? SubsampleAverageDescriptor.create(img, scale, scale,
                            Thumbnail.DownScaleQualityHints).getAsBufferedImage() : null : PlanarImage
                            .wrapRenderedImage(img).getAsBufferedImage();
                    diskObject.dispose();

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
