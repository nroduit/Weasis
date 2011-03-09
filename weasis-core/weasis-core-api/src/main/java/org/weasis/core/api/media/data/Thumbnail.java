/*******************************************************************************
 * Copyright (c) 2010 Nicolas Roduit.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     Nicolas Roduit - initial API and implementation
 ******************************************************************************/
package org.weasis.core.api.media.data;

import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DragGestureEvent;
import java.awt.dnd.DragGestureListener;
import java.awt.dnd.DragSource;
import java.awt.dnd.DragSourceDragEvent;
import java.awt.dnd.DragSourceDropEvent;
import java.awt.dnd.DragSourceEvent;
import java.awt.dnd.DragSourceListener;
import java.awt.dnd.DragSourceMotionListener;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.event.MouseWheelListener;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.awt.image.RenderedImage;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.lang.ref.SoftReference;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import javax.imageio.ImageIO;
import javax.imageio.stream.FileImageInputStream;
import javax.imageio.stream.ImageInputStream;
import javax.media.jai.JAI;
import javax.media.jai.ParameterBlockJAI;
import javax.media.jai.PlanarImage;
import javax.media.jai.operator.SubsampleAverageDescriptor;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JViewport;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.ToolTipManager;
import javax.swing.border.Border;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;

import org.weasis.core.api.Messages;
import org.weasis.core.api.gui.task.CircularProgressBar;
import org.weasis.core.api.gui.util.AbstractProperties;
import org.weasis.core.api.gui.util.GhostGlassPane;
import org.weasis.core.api.image.util.ImageFiler;
import org.weasis.core.api.image.util.ImageToolkit;
import org.weasis.core.api.media.MimeInspector;
import org.weasis.core.api.util.FileUtil;
import org.weasis.core.api.util.FontTools;

public class Thumbnail<E> extends JLabel implements MouseListener, DragGestureListener, DragSourceListener,
    DragSourceMotionListener, FocusListener {

    public static final RenderingHints DownScaleQualityHints = new RenderingHints(RenderingHints.KEY_RENDERING,
        RenderingHints.VALUE_RENDER_QUALITY);
    static {
        DownScaleQualityHints.add(new RenderingHints(JAI.KEY_TILE_CACHE, null));
    }
    public final static int MIN_SIZE = 48;
    public final static int DEFAULT_SIZE = 112;
    public final static int MAX_SIZE = 256;

    protected SoftReference<BufferedImage> imageSoftRef;
    protected boolean readable = true;
    private File thumbnailPath = null;
    private int thumbnailSize;
    // Get the closest cursor size regarding to the platform
    private final Border onMouseOverBorder = new CompoundBorder(new EmptyBorder(2, 2, 0, 2), new LineBorder(
        Color.orange, 2));
    private final Border outMouseOverBorder = new EmptyBorder(4, 4, 2, 4);
    private JProgressBar progressBar;
    private final MediaSeries<E> series;
    private Point dragPressed = null;
    private DragSource dragSource = null;

    public Thumbnail(final MediaSeries<E> sequence, int thumbnailSize) {
        this(sequence, null, thumbnailSize);
    }

    public Thumbnail(final MediaSeries<E> sequence, File thumbnailPath, int thumbnailSize) {
        super(null, null, SwingConstants.CENTER);
        if (sequence == null) {
            throw new IllegalArgumentException("Sequence cannot be null"); //$NON-NLS-1$
        }
        this.thumbnailSize = thumbnailSize;
        this.series = sequence;
        this.thumbnailPath = thumbnailPath;
        init();
    }

    private void init() {
        this.setFont(FontTools.getFont10());
        // Activate tooltip
        ToolTipManager.sharedInstance().registerComponent(this);
        buildThumbnail();
        setBorder(outMouseOverBorder);
    }

    public JProgressBar getProgressBar() {
        return progressBar;
    }

    public void setProgressBar(JProgressBar progressBar) {
        removeMouseListener(this);
        this.progressBar = progressBar;
        if (progressBar != null) {
            addMouseListener(this);
        }
    }

    public void registerListeners() {
        if (dragSource != null) {
            dragSource.removeDragSourceListener(this);
            dragSource.removeDragSourceMotionListener(this);
            removeFocusListener(this);
        }
        addFocusListener(this);
        this.setFocusable(true);
        dragSource = new DragSource();
        dragSource.createDefaultDragGestureRecognizer(this, DnDConstants.ACTION_COPY, this);
        dragSource.addDragSourceMotionListener(this);
    }

    public synchronized void reBuildThumbnail() {
        File old = thumbnailPath;
        thumbnailPath = null;
        buildThumbnail();
        if (old != null) {
            old.delete();
        }
    }

    private synchronized void buildThumbnail() {
        imageSoftRef = null;
        Icon icon = MimeInspector.unknownIcon;
        String type = Messages.getString("Thumbnail.unknown"); //$NON-NLS-1$
        Object media = series.getMedia(MediaSeries.MEDIA_POSITION.MIDDLE);
        if (media instanceof MediaElement) {
            MediaElement m = (MediaElement) media;
            String mime = m.getMimeType();
            if (mime.startsWith("image")) { //$NON-NLS-1$
                type = Messages.getString("Thumbnail.img"); //$NON-NLS-1$
                icon = MimeInspector.imageIcon;
            } else if (mime.startsWith("video")) { //$NON-NLS-1$
                type = Messages.getString("Thumbnail.video"); //$NON-NLS-1$
                icon = MimeInspector.videoIcon;
            } else if (mime.startsWith("audio")) { //$NON-NLS-1$
                type = Messages.getString("Thumbnail.audio"); //$NON-NLS-1$
                icon = MimeInspector.audioIcon;
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
        setIcon(icon, type);
    }

    public synchronized int getThumbnailSize() {
        return thumbnailSize;
    }

    public synchronized void setThumbnailSize(int thumbnailSize) {
        boolean update = this.thumbnailSize != thumbnailSize;
        if (update) {
            this.thumbnailSize = thumbnailSize;
            buildThumbnail();
        }
    }

    private void setIcon(final Icon mime, final String type) {
        this.setSize(thumbnailSize, thumbnailSize);

        ImageIcon icon = new ImageIcon() {

            @Override
            public void paintIcon(Component c, Graphics g, int x, int y) {
                Graphics2D g2d = (Graphics2D) g;
                int width = thumbnailSize;
                int height = thumbnailSize;
                final BufferedImage thumbnail = Thumbnail.this.getImage();
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
                super.paintIcon(c, g2d, x, y);
                paintSeriesState(g2d, x, y, width, height);
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

    private PlanarImage loadImage(File path) throws Exception {
        // Imageio issue with native library in multi-thread environment
        // (https://jai-imageio-core.dev.java.net/issues/show_bug.cgi?id=126)
        // For this reason, the thumbnails are loaded sequentially.
        ImageInputStream in = new FileImageInputStream(new RandomAccessFile(path, "r")); //$NON-NLS-1$
        ParameterBlockJAI pb = new ParameterBlockJAI("ImageRead"); //$NON-NLS-1$
        pb.setParameter("Input", in); //$NON-NLS-1$
        return JAI.create("ImageRead", pb, null); //$NON-NLS-1$
        // // stream to unlock the file when it is not used any more
        // FileSeekableStream fileStream = new FileSeekableStream(path);
        // PlanarImage img = JAI.create("stream", fileStream);
        // // to avoid problem with alpha channel and png encoded in 24 and 32 bits
        // return ImageFiler.getReadableImage(img);
    }

    public synchronized BufferedImage getImage() {
        if ((imageSoftRef == null && readable) || (imageSoftRef != null && imageSoftRef.get() == null)) {
            readable = false;
            BufferedImage thumb = null;
            if (thumbnailPath == null || !thumbnailPath.canRead()) {
                Object media = series.getMedia(MediaSeries.MEDIA_POSITION.MIDDLE);
                if (media instanceof ImageElement) {
                    ImageElement image = (ImageElement) media;
                    PlanarImage imgPl = image.getImage();
                    if (imgPl != null) {
                        RenderedImage img = ImageToolkit.getDefaultRenderedImage(image, imgPl);
                        final double scale =
                            Math.min(MAX_SIZE / (double) img.getHeight(), MAX_SIZE / (double) img.getWidth());
                        final PlanarImage t =
                            scale < 1.0 ? SubsampleAverageDescriptor.create(img, scale, scale, DownScaleQualityHints)
                                .getRendering() : PlanarImage.wrapRenderedImage(img);
                        try {
                            thumbnailPath = File.createTempFile("tumb_", ".jpg", AbstractProperties.APP_TEMP_DIR); //$NON-NLS-1$ //$NON-NLS-2$
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                        if (thumbnailPath != null) {
                            ImageElement.IMAGE_LOADER.submit(new Runnable() {

                                @Override
                                public void run() {
                                    if (ImageFiler.writeJPG(thumbnailPath, t, 0.75f)) {
                                        // will read the thumbnail in temp folder, better than getting the thumbnail
                                        // from
                                        // t.getAsBufferedImage() (it
                                        // is true if the image is big and cannot handle all the tiles in memory)
                                        readable = true;
                                        repaint(100L);
                                    } else {
                                        // out of memory
                                    }
                                }
                            });
                            return null;

                        }
                        thumb = t.getAsBufferedImage();
                    }
                }
            } else {
                Load ref = new Load(thumbnailPath);
                // loading images sequentially, only one thread pool
                Future<BufferedImage> future = ImageElement.IMAGE_LOADER.submit(ref);
                BufferedImage img = null;
                try {
                    img = future.get();
                    int width = img.getWidth();
                    int height = img.getHeight();
                    if (width > thumbnailSize || height > thumbnailSize) {
                        final double scale = Math.min(thumbnailSize / (double) height, thumbnailSize / (double) width);
                        PlanarImage t =
                            scale < 1.0 ? SubsampleAverageDescriptor.create(img, scale, scale, DownScaleQualityHints)
                                : PlanarImage.wrapRenderedImage(img);
                        thumb = t.getAsBufferedImage();
                        t.dispose();
                    } else {
                        thumb = img;
                    }

                } catch (InterruptedException e) {
                    // Re-assert the thread's interrupted status
                    Thread.currentThread().interrupt();
                    // We don't need the result, so cancel the task too
                    future.cancel(true);
                } catch (ExecutionException e) {
                    System.err.println("Error: Cannot read pixel data!:" + thumbnailPath); //$NON-NLS-1$
                    e.printStackTrace();
                }
            }
            if (thumb == null && (thumbnailPath != null || series.getMedia(MediaSeries.MEDIA_POSITION.MIDDLE) != null)) {
                readable = false;
            } else {
                readable = true;
                imageSoftRef = new SoftReference<BufferedImage>(thumb);
            }
        }
        if (imageSoftRef == null) {
            return null;
        }
        return imageSoftRef.get();
    }

    public void dispose() {
        // Unload image from memory
        if (imageSoftRef != null) {
            BufferedImage temp = imageSoftRef.get();
            if (temp != null) {
                temp.flush();
            }
            // image = null;
        }
    }

    // --- DragGestureListener methods -----------------------------------

    public void dragGestureRecognized(DragGestureEvent dge) {
        Component comp = dge.getComponent();
        try {
            GhostGlassPane glassPane = AbstractProperties.glassPane;
            glassPane.setIcon(getIcon());
            Point p = (Point) dge.getDragOrigin().clone();
            dragPressed = new Point(p.x - 4, p.y - 4);
            SwingUtilities.convertPointToScreen(p, comp);
            drawGlassPane(p);
            glassPane.setVisible(true);
            dge.startDrag(null, series, this);
            return;
        } catch (RuntimeException re) {
        }

    }

    public void dragMouseMoved(DragSourceDragEvent dsde) {
        drawGlassPane(dsde.getLocation());
    }

    // --- DragSourceListener methods -----------------------------------

    public void dragEnter(DragSourceDragEvent dsde) {
    }

    public void dragOver(DragSourceDragEvent dsde) {
    }

    public void dragExit(DragSourceEvent dsde) {

    }

    public void dragDropEnd(DragSourceDropEvent dsde) {
        GhostGlassPane glassPane = AbstractProperties.glassPane;
        dragPressed = null;
        glassPane.setImagePosition(null);
        glassPane.setIcon(null);
        glassPane.setVisible(false);
    }

    public void dropActionChanged(DragSourceDragEvent dsde) {
    }

    public void drawGlassPane(Point p) {
        if (dragPressed != null) {
            GhostGlassPane glassPane = AbstractProperties.glassPane;
            SwingUtilities.convertPointFromScreen(p, glassPane);
            p.translate(-dragPressed.x, -dragPressed.y);
            glassPane.setImagePosition(p);
        }
    }

    public MediaSeries<E> getSeries() {
        return series;
    }

    public void focusGained(FocusEvent e) {
        if (!e.isTemporary()) {
            setBorder(onMouseOverBorder);
            JPanel container = getScrollPane();
            if (container != null) {
                Rectangle bound = this.getBounds();
                Point p1 = SwingUtilities.convertPoint(this, this.getX(), this.getY(), container);
                bound.x = p1.x;
                bound.y = p1.y;
                container.scrollRectToVisible(bound);
            }
            SeriesImporter loader = series.getSeriesLoader();
            if (loader != null) {
                loader.setPriority();
            }
        }
    }

    public void focusLost(FocusEvent e) {
        if (!e.isTemporary()) {
            setBorder(outMouseOverBorder);
        }
    }

    private JPanel getScrollPane() {
        Container container = getParent();
        while (container != null) {
            if (container.getParent() instanceof JViewport) {
                return (JPanel) container;
            }
            container = container.getParent();
        }
        return null;
    }

    @Override
    public String getToolTipText() {
        return series.getToolTips();
    }

    public void paintSeriesState(Graphics2D g2d, int x, int y, int width, int height) {
        if (series.isOpen()) {
            g2d.setPaint(Color.green);
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2d.fillArc(x + 2, y + 2, 7, 7, 0, 360);
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_DEFAULT);
        }
        g2d.setPaint(Color.ORANGE);
        if (series.isSelected()) {
            g2d.drawRect(x + 12, y + 3, 5, 5);
        }
        Integer splitNb = (Integer) series.getTagValue(TagW.SplitSeriesNumber);
        g2d.setFont(FontTools.getFont10());
        int hbleft = y + height - 2;
        if (splitNb != null) {
            g2d.drawString("#" + splitNb + " [" + series.getMedias().size() + "]", x + 2, hbleft); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ $NON-NLS-2$ $NON-NLS-3$
        } else {
            g2d.drawString("[" + series.getMedias().size() + "]", x + 2, hbleft); //$NON-NLS-1$ //$NON-NLS-2$ $NON-NLS-2$
        }

        // To avoid concurrency issue
        final JProgressBar bar = progressBar;
        if (bar != null) {
            if (series.getFileSize() > 0.0) {
                g2d.drawString(FileUtil.formatSize(series.getFileSize()), x + 2, hbleft - 12);
            }
            g2d.translate(width - bar.getWidth() + x - 2, height - bar.getHeight() + y - 2);
            bar.paint(g2d);
        }
    }

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

        public BufferedImage call() throws Exception {
            return ImageIO.read(path);
            // return loadImage(path);
        }

    }

    @Override
    public void mouseClicked(MouseEvent e) {
    }

    @Override
    public void mousePressed(MouseEvent e) {
    }

    @Override
    public void mouseReleased(MouseEvent e) {
        if (progressBar instanceof JProgressBar) {
            CircularProgressBar cricleBar = (CircularProgressBar) progressBar;
            Rectangle rect = cricleBar.getBounds();
            rect.x = thumbnailSize - rect.width - 2;
            rect.y = thumbnailSize - rect.height - 2;
            if (rect.contains(e.getPoint())) {
                SeriesImporter loader = series.getSeriesLoader();
                if (loader != null) {
                    if (loader.isStopped()) {
                        loader.resume();
                    } else {
                        loader.stop();
                    }
                }
            }
        }
    }

    @Override
    public void mouseEntered(MouseEvent e) {
    }

    @Override
    public void mouseExited(MouseEvent e) {
    }
}
