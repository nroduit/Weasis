/*******************************************************************************
 * Copyright (c) 2009-2018 Weasis Team and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 *     Nicolas Roduit - initial API and implementation
 *******************************************************************************/
package org.weasis.core.api.media.data;

import java.awt.Component;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.KeyListener;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.event.MouseWheelListener;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.awt.image.RenderedImage;
import java.io.File;
import java.io.IOException;
import java.lang.ref.SoftReference;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.imageio.ImageIO;
import javax.media.jai.JAI;
import javax.media.jai.PlanarImage;
import javax.media.jai.operator.SubsampleAverageDescriptor;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.SwingConstants;
import javax.swing.SwingWorker;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.Messages;
import org.weasis.core.api.gui.util.AppProperties;
import org.weasis.core.api.image.OpManager;
import org.weasis.core.api.image.util.ImageFiler;
import org.weasis.core.api.media.MimeInspector;
import org.weasis.core.api.util.FileUtil;
import org.weasis.core.api.util.FontTools;
import org.weasis.core.api.util.ThreadUtil;

@SuppressWarnings("serial")
public class Thumbnail extends JLabel implements Thumbnailable {
    private static final Logger LOGGER = LoggerFactory.getLogger(Thumbnail.class);

    public static final File THUMBNAIL_CACHE_DIR =
        AppProperties.buildAccessibleTempDirectory(AppProperties.FILE_CACHE_DIR.getName(), "thumb"); //$NON-NLS-1$
    public static final ExecutorService THUMB_LOADER = ThreadUtil.buildNewSingleThreadExecutor("Thumbnail Loader"); //$NON-NLS-1$

    public static final RenderingHints DownScaleQualityHints =
        new RenderingHints(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

    static {
        DownScaleQualityHints.add(new RenderingHints(JAI.KEY_TILE_CACHE, null));
    }

    public static final int MIN_SIZE = 48;
    public static final int DEFAULT_SIZE = 112;
    public static final int MAX_SIZE = 256;

    private SoftReference<BufferedImage> imageSoftRef;
    protected volatile boolean readable = true;
    protected volatile AtomicBoolean loading = new AtomicBoolean(false);
    protected File thumbnailPath = null;
    protected int thumbnailSize;

    public Thumbnail(int thumbnailSize) {
        super(null, null, SwingConstants.CENTER);
        this.thumbnailSize = thumbnailSize;
    }

    public Thumbnail(final MediaElement media, int thumbnailSize, boolean keepMediaCache, OpManager opManager) {
        super(null, null, SwingConstants.CENTER);
        if (media == null) {
            throw new IllegalArgumentException("image cannot be null"); //$NON-NLS-1$
        }
        this.thumbnailSize = thumbnailSize;
        init(media, keepMediaCache, opManager);
    }

    /**
     * @param media
     * @param keepMediaCache
     *            if true will remove the media from cache after building the thumbnail. Only when media is an image.
     */
    protected void init(MediaElement media, boolean keepMediaCache, OpManager opManager) {
        this.setFont(FontTools.getFont10());
        buildThumbnail(media, keepMediaCache, opManager);
    }

    @Override
    public void registerListeners() {
        removeMouseAndKeyListener();
    }

    public static RenderedImage createThumbnail(RenderedImage source) {
        if (source == null) {
            return null;
        }
        final double scale =
            Math.min(Thumbnail.MAX_SIZE / (double) source.getHeight(), Thumbnail.MAX_SIZE / (double) source.getWidth());
        return scale < 1.0
            ? SubsampleAverageDescriptor.create(source, scale, scale, Thumbnail.DownScaleQualityHints).getRendering()
            : source;
    }

    protected synchronized void buildThumbnail(MediaElement media, boolean keepMediaCache, OpManager opManager) {
        imageSoftRef = null;
        Icon icon = MimeInspector.unknownIcon;
        String type = Messages.getString("Thumbnail.unknown"); //$NON-NLS-1$
        if (media != null) {
            String mime = media.getMimeType();
            if (mime != null) {
                if (mime.startsWith("image")) { //$NON-NLS-1$
                    type = Messages.getString("Thumbnail.img"); //$NON-NLS-1$
                    icon = MimeInspector.imageIcon;
                } else if (mime.startsWith("video")) { //$NON-NLS-1$
                    type = Messages.getString("Thumbnail.video"); //$NON-NLS-1$
                    icon = MimeInspector.videoIcon;
                } else if (mime.startsWith("audio")) { //$NON-NLS-1$
                    type = Messages.getString("Thumbnail.audio"); //$NON-NLS-1$
                    icon = MimeInspector.audioIcon;
                } else if (mime.equals("sr/dicom")) { //$NON-NLS-1$
                    type = Messages.getString("Thumbnail.dicom_sr"); //$NON-NLS-1$
                    icon = MimeInspector.textIcon;
                } else if (mime.startsWith("txt")) { //$NON-NLS-1$
                    type = Messages.getString("Thumbnail.text"); //$NON-NLS-1$
                    icon = MimeInspector.textIcon;
                } else if (mime.endsWith("html")) { //$NON-NLS-1$
                    type = Messages.getString("Thumbnail.html"); //$NON-NLS-1$
                    icon = MimeInspector.htmlIcon;
                } else if (mime.equals("application/pdf")) { //$NON-NLS-1$
                    type = Messages.getString("Thumbnail.pdf"); //$NON-NLS-1$
                    icon = MimeInspector.pdfIcon;
                } else {
                    type = mime;
                }
            }
        }
        setIcon(media, icon, type, keepMediaCache, opManager);
    }

    private void setIcon(final MediaElement media, final Icon mime, final String type, final boolean keepMediaCache,
        OpManager opManager) {
        this.setSize(thumbnailSize, thumbnailSize);

        ImageIcon icon = new ImageIcon() {

            @Override
            public synchronized void paintIcon(Component c, Graphics g, int x, int y) {
                Graphics2D g2d = (Graphics2D) g;
                int width = thumbnailSize;
                int height = thumbnailSize;
                final BufferedImage thumbnail = Thumbnail.this.getImage(media, keepMediaCache, opManager);
                if (thumbnail == null) {
                    FontMetrics fontMetrics = g2d.getFontMetrics();
                    int fheight = y + (thumbnailSize - fontMetrics.getAscent() + 5 - mime.getIconHeight()) / 2;
                    mime.paintIcon(c, g2d, x + (thumbnailSize - mime.getIconWidth()) / 2, fheight);

                    int startx = x + (thumbnailSize - fontMetrics.stringWidth(type)) / 2;
                    g2d.drawString(type, startx, fheight + mime.getIconHeight() + fontMetrics.getAscent() + 5);
                } else {
                    width = thumbnail.getWidth();
                    height = thumbnail.getHeight();
                    x += (thumbnailSize - width) / 2;
                    y += (thumbnailSize - height) / 2;
                    g2d.drawImage(thumbnail, AffineTransform.getTranslateInstance(x, y), null);
                }
                drawOverIcon(g2d, x, y, width, height);
            }

            @Override
            public int getIconWidth() {
                return thumbnailSize;
            }

            @Override
            public int getIconHeight() {
                return thumbnailSize;
            }
        };
        setIcon(icon);
    }

    protected void drawOverIcon(Graphics2D g2d, int x, int y, int width, int height) {

    }

    @Override
    public File getThumbnailPath() {
        return thumbnailPath;
    }

    protected synchronized BufferedImage getImage(final MediaElement media, final boolean keepMediaCache,
        final OpManager opManager) {
        if ((imageSoftRef == null && readable) || (imageSoftRef != null && imageSoftRef.get() == null)) {
            if (loading.compareAndSet(false, true)) {
                try {
                    SwingWorker<Boolean, String> thumbnailReader = new SwingWorker<Boolean, String>() {
                        @Override
                        protected void done() {
                            repaint();
                        }

                        @Override
                        protected Boolean doInBackground() throws Exception {
                            loadThumbnail(media, keepMediaCache, opManager);
                            return Boolean.TRUE;
                        }

                    };
                    THUMB_LOADER.execute(thumbnailReader);
                } catch (Exception e) {
                    LOGGER.error("Cannot build thumbnail!", e);//$NON-NLS-1$
                    loading.set(false);
                }
            }
        }
        if (imageSoftRef == null) {
            return null;
        }
        return imageSoftRef.get();
    }

    private void loadThumbnail(final MediaElement media, final boolean keepMediaCache, final OpManager opManager)
        throws Exception {
        try {
            File file = thumbnailPath;
            boolean noPath = file == null || !file.canRead();
            if (noPath && media != null) {
                String path = (String) media.getTagValue(TagW.ThumbnailPath);
                if (path != null) {
                    file = new File(path);
                    if (file.canRead()) {
                        noPath = false;
                        thumbnailPath = file;
                    }
                }
            }
            if (noPath) {
                if (media instanceof ImageElement) {
                    final ImageElement image = (ImageElement) media;
                    PlanarImage imgPl = image.getImage(opManager);
                    if (imgPl != null) {
                        RenderedImage img = image.getRenderedImage(imgPl);
                        final RenderedImage thumb = createThumbnail(img);
                        try {
                            file = File.createTempFile("tumb_", ".jpg", Thumbnail.THUMBNAIL_CACHE_DIR); //$NON-NLS-1$ //$NON-NLS-2$
                        } catch (IOException e) {
                            LOGGER.error("Cannot create file for thumbnail!", e);//$NON-NLS-1$
                        }
                        try {
                            BufferedImage thumbnail = null;
                            if (file != null) {
                                if (ImageFiler.writeJPG(file, thumb, 0.75f)) {
                                    /*
                                     * Write the thumbnail in temp folder, better than getting the thumbnail directly
                                     * from t.getAsBufferedImage() (it is true if the image is big and cannot handle all
                                     * the tiles in memory)
                                     */
                                    image.setTag(TagW.ThumbnailPath, file.getPath());
                                    thumbnailPath = file;
                                    return;
                                } else {
                                    // out of memory
                                }

                            } else if (thumb instanceof PlanarImage) {
                                thumbnail = ((PlanarImage) thumb).getAsBufferedImage();
                            }

                            if (thumbnail == null) {
                                readable = false;
                            } else {
                                imageSoftRef = new SoftReference<>(thumbnail);
                            }
                        } finally {
                            if (!keepMediaCache) {
                                // Prevent to many files open on Linux (Ubuntu => 1024) and close image stream
                                image.removeImageFromCache();
                            }
                        }
                    } else {
                        readable = false;
                    }
                }
            } else {
                Load ref = new Load(file);
                // loading images sequentially, only one thread pool
                Future<BufferedImage> future = ImageElement.IMAGE_LOADER.submit(ref);
                BufferedImage img = null;
                BufferedImage thumb = null;
                try {
                    img = future.get();
                    if (img == null) {
                        thumb = null;
                    } else {
                        int width = img.getWidth();
                        int height = img.getHeight();
                        if (width > thumbnailSize || height > thumbnailSize) {
                            final double scale =
                                Math.min(thumbnailSize / (double) height, thumbnailSize / (double) width);
                            PlanarImage t = scale < 1.0
                                ? SubsampleAverageDescriptor.create(img, scale, scale, DownScaleQualityHints)
                                : PlanarImage.wrapRenderedImage(img);
                            thumb = t.getAsBufferedImage();
                            t.dispose();
                        } else {
                            thumb = img;
                        }
                    }

                } catch (InterruptedException e) {
                    // Re-assert the thread's interrupted status
                    Thread.currentThread().interrupt();
                    // We don't need the result, so cancel the task too
                    future.cancel(true);
                } catch (ExecutionException e) {
                    LOGGER.error("Cannot read thumbnail pixel data!: {}", file, e);//$NON-NLS-1$
                }
                if (thumb == null && media != null) {
                    readable = false;
                } else {
                    imageSoftRef = new SoftReference<>(thumb);
                }
            }
        } finally {
            loading.set(false);
        }
    }

    @Override
    public void dispose() {
        // Unload image from memory
        if (imageSoftRef != null) {
            BufferedImage temp = imageSoftRef.get();
            if (temp != null) {
                temp.flush();
            }
            if (thumbnailPath != null && thumbnailPath.getPath().startsWith(AppProperties.FILE_CACHE_DIR.getPath())) {
                FileUtil.delete(thumbnailPath);
            }
        }
        removeMouseAndKeyListener();
    }

    @Override
    public void removeMouseAndKeyListener() {
        MouseListener[] listener = this.getMouseListeners();
        MouseMotionListener[] motionListeners = this.getMouseMotionListeners();
        KeyListener[] keyListeners = this.getKeyListeners();
        MouseWheelListener[] wheelListeners = this.getMouseWheelListeners();
        for (int i = 0; i < listener.length; i++) {
            this.removeMouseListener(listener[i]);
        }
        for (int i = 0; i < motionListeners.length; i++) {
            this.removeMouseMotionListener(motionListeners[i]);
        }
        for (int i = 0; i < keyListeners.length; i++) {
            this.removeKeyListener(keyListeners[i]);
        }
        for (int i = 0; i < wheelListeners.length; i++) {
            this.removeMouseWheelListener(wheelListeners[i]);
        }
    }

    class Load implements Callable<BufferedImage> {

        private final File path;

        public Load(File path) {
            this.path = path;
        }

        @Override
        public BufferedImage call() throws Exception {
            return ImageIO.read(path);
        }
    }

}
