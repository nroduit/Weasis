/*******************************************************************************
 * Copyright (c) 2016 Weasis Team and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Nicolas Roduit - initial API and implementation
 *******************************************************************************/
package org.weasis.base.explorer;

import java.awt.image.BufferedImage;
import java.io.File;
import java.net.URI;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.opencv.core.Mat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.base.explorer.list.IThumbnailList;
import org.weasis.base.explorer.list.IThumbnailModel;
import org.weasis.core.api.gui.util.GuiExecutor;
import org.weasis.core.api.image.cv.ImageProcessor;
import org.weasis.core.api.image.util.ImageFiler;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.api.util.ThreadUtil;

public final class JIThumbnailCache {
    private static final Logger LOGGER = LoggerFactory.getLogger(JIThumbnailCache.class);

    private static final LinkedBlockingQueue<Runnable> queue = new LinkedBlockingQueue<>();
    // Set only one concurrent thread. The time consuming part is in loading image thread (see ImageElement)
    private static final ExecutorService qExecutor =
        new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS, queue, ThreadUtil.getThreadFactory("Thumbnail Cache")); //$NON-NLS-1$

    private static final JIThumbnailCache instance = new JIThumbnailCache();

    private final Map<URI, ThumbnailIcon> cachedThumbnails;

    private JIThumbnailCache() {

        this.cachedThumbnails = Collections.synchronizedMap(new LinkedHashMap<URI, ThumbnailIcon>(80) {

            private static final long serialVersionUID = 5981678679620794224L;
            private static final int MAX_ENTRIES = 100;

            @Override
            @SuppressWarnings("rawtypes")
            protected boolean removeEldestEntry(final Map.Entry eldest) {
                return size() > MAX_ENTRIES;
            }
        });
    }

    public static JIThumbnailCache getInstance() {
        return instance;
    }

    public synchronized void invalidate() {
        this.cachedThumbnails.clear();
    }

    public static void removeInQueue(ImageElement imgElement) {
        Runnable r = null;
        for (Runnable runnable : queue) {
            if (Objects.equals(imgElement, ((ThumbnailRunnable) runnable).getDiskObject())) {
                r = runnable;
            }
        }
        if (r != null) {
            queue.remove(r);
        }
    }

    public ThumbnailIcon getThumbnailFor(final ImageElement diskObject, final IThumbnailList aThumbnailList,
        final int index) {
        try {

            final ThumbnailIcon jiIcon = this.cachedThumbnails.get(diskObject.getMediaURI());
            if (jiIcon != null) {
                return jiIcon;
            }

        } catch (final Exception e) {
            LOGGER.error("", e); //$NON-NLS-1$
        }
        if (!diskObject.isLoading()) {
            loadThumbnail(diskObject, aThumbnailList, index);
        }
        return null;
    }

    private static void loadThumbnail(final ImageElement diskObject, final IThumbnailList thumbnailList,
        final int index) {
        if ((index > thumbnailList.getLastVisibleIndex()) || (index < thumbnailList.getFirstVisibleIndex())) {
            return;
        }
        for (Runnable runnable : queue) {
            if (diskObject.equals(((ThumbnailRunnable) runnable).getDiskObject())) {
                return;
            }
        }
        cleanPending();
        ThumbnailRunnable runnable = new ThumbnailRunnable(diskObject, thumbnailList, index);
        JIThumbnailCache.qExecutor.execute(runnable);
    }

    private static void cleanPending() {
        for (Runnable runnable : queue) {
            ThumbnailRunnable r = (ThumbnailRunnable) runnable;
            int index = r.getIndex();
            if ((index > r.getThumbnailList().getLastVisibleIndex())
                || (index < r.getThumbnailList().getFirstVisibleIndex())) {
                JIThumbnailCache.removeInQueue(r.getDiskObject());
            }
        }
    }

    static class ThumbnailRunnable implements Runnable {
        final ImageElement diskObject;
        final IThumbnailList thumbnailList;
        final int index;
        final IThumbnailModel modelList;

        public ThumbnailRunnable(ImageElement diskObject, IThumbnailList thumbnailList, int index) {
            this.diskObject = diskObject;
            this.thumbnailList = thumbnailList;
            this.index = index;
            this.modelList = thumbnailList.getThumbnailListModel();
        }

        public ImageElement getDiskObject() {
            return diskObject;
        }

        public IThumbnailList getThumbnailList() {
            return thumbnailList;
        }

        public int getIndex() {
            return index;
        }

        public IThumbnailModel getModelList() {
            return modelList;
        }

        @Override
        public void run() {
            Mat img = null;

            // Get the final that contain the thumbnail when the uncompress mode is activated
            File file = diskObject.getFile();
            if (file != null && file.getName().endsWith(".pnm")) {
                File thumbFile = new File(ImageFiler.changeExtension(file.getPath(), ".jpg"));
                if (thumbFile.canRead()) {
                    img = ImageProcessor.readImage(thumbFile);
                }
            }

            if (img == null) {
                img = diskObject.getRenderedImage(diskObject.getImage(null));
            }

            if (img == null) {
                return;
            }

            final BufferedImage tIcon =
                ImageProcessor.toBufferedImage(ImageProcessor.buildThumbnail(img, ThumbnailRenderer.ICON_DIM, true));

            // Prevent to many files open on Linux (Ubuntu => 1024) and close image stream
            diskObject.removeImageFromCache();

            GuiExecutor.instance().execute(() -> {
                if (tIcon != null) {
                    getInstance().cachedThumbnails.put(diskObject.getMediaURI(), new ThumbnailIcon(tIcon));
                }
                thumbnailList.getThumbnailListModel().notifyAsUpdated(index);
            });
        }

    }

}
