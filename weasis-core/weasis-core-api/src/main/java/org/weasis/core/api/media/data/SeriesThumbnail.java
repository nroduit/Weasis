/*******************************************************************************
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.weasis.core.api.media.data;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Component;
import java.awt.Composite;
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Polygon;
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
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.io.File;
import java.util.Arrays;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JViewport;
import javax.swing.SwingUtilities;
import javax.swing.ToolTipManager;
import javax.swing.border.Border;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.gui.util.AppProperties;
import org.weasis.core.api.gui.util.GhostGlassPane;
import org.weasis.core.api.image.OpManager;
import org.weasis.core.api.media.data.MediaSeries.MEDIA_POSITION;
import org.weasis.core.util.FileUtil;
import org.weasis.core.api.util.FontTools;

public class SeriesThumbnail extends Thumbnail
    implements MouseListener, DragGestureListener, DragSourceListener, DragSourceMotionListener, FocusListener {
    private static final long serialVersionUID = 2359304176364341395L;

    private static final Logger LOGGER = LoggerFactory.getLogger(SeriesThumbnail.class);

    private static final int BUTTON_SIZE_HALF = 7;
    private static final Polygon startButton = new Polygon(new int[] { 0, 2 * BUTTON_SIZE_HALF, 0 },
        new int[] { 0, BUTTON_SIZE_HALF, 2 * BUTTON_SIZE_HALF }, 3);
    private static final Rectangle stopButton = new Rectangle(0, 0, 2 * BUTTON_SIZE_HALF, 2 * BUTTON_SIZE_HALF);

    private static final Composite SOLID_COMPOSITE = AlphaComposite.SrcOver;
    private static final Composite TRANSPARENT_COMPOSITE = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.2f);

    private MediaSeries.MEDIA_POSITION mediaPosition = MediaSeries.MEDIA_POSITION.MIDDLE;
    // Get the closest cursor size regarding to the platform
    private final Border onMouseOverBorderFocused =
        new CompoundBorder(new EmptyBorder(2, 2, 0, 2), new LineBorder(Color.orange, 2));
    private final Border onMouseOverBorder =
        new CompoundBorder(new EmptyBorder(2, 2, 0, 2), new LineBorder(new Color(255, 224, 178), 2));
    private final Border outMouseOverBorder =
        new CompoundBorder(new EmptyBorder(2, 2, 0, 2), BorderFactory.createEtchedBorder());
    private JProgressBar progressBar;
    private final MediaSeries<? extends MediaElement> series;
    private Point dragPressed = null;
    private DragSource dragSource = null;

    public SeriesThumbnail(final MediaSeries<? extends MediaElement> sequence, int thumbnailSize) {
        super(thumbnailSize);
        if (sequence == null) {
            throw new IllegalArgumentException("Sequence cannot be null"); //$NON-NLS-1$
        }
        this.series = sequence;

        // media can be null for seriesThumbnail
        MediaElement media = sequence.getMedia(MEDIA_POSITION.MIDDLE, null, null);
        // Handle special case for DICOM SR
        if (media == null) {
            List<MediaElement> specialElements = (List<MediaElement>) series.getTagValue(TagW.DicomSpecialElementList);
            if (specialElements != null && !specialElements.isEmpty()) {
                media = specialElements.get(0);
            }
        }
        /*
         * Do not remove the image from the cache after building the thumbnail when the series is associated to a
         * explorerModel (stream should be closed at least when closing the application or when free the cache).
         */
        init(media, series.getTagValue(TagW.ExplorerModel) != null, null);
    }

    @Override
    protected void init(MediaElement media, boolean keepMediaCache, OpManager opManager) {
        super.init(media, keepMediaCache, opManager);
        setBorder(outMouseOverBorder);
    }

    public JProgressBar getProgressBar() {
        return progressBar;
    }

    public void setProgressBar(JProgressBar progressBar) {
        if (progressBar == null) {
            removeMouseListener(this);
        } else {
            if (!Arrays.asList(this.getMouseListeners()).contains(this)) {
                addMouseListener(this);
            }
        }
        this.progressBar = progressBar;
    }

    @Override
    public void registerListeners() {
        super.registerListeners();

        // Reactivate tooltip listener
        ToolTipManager.sharedInstance().registerComponent(this);

        if (dragSource != null) {
            dragSource.removeDragSourceListener(this);
            dragSource.removeDragSourceMotionListener(this);
            removeFocusListener(this);
        }
        addFocusListener(this);
        this.setFocusable(true);
        this.addMouseListener(this);
        dragSource = new DragSource();
        dragSource.createDefaultDragGestureRecognizer(this, DnDConstants.ACTION_COPY, this);
        dragSource.addDragSourceMotionListener(this);
    }

    public void reBuildThumbnail(MediaSeries.MEDIA_POSITION position) {
        reBuildThumbnail(null, position);
    }

    public void reBuildThumbnail() {
        reBuildThumbnail(null, mediaPosition);
    }

    public synchronized void reBuildThumbnail(File file, MediaSeries.MEDIA_POSITION position) {
        MediaElement media = series.getMedia(position, null, null);
        // Handle special case for DICOM SR
        if (media == null) {
            List<MediaElement> specialElements = (List<MediaElement>) series.getTagValue(TagW.DicomSpecialElementList);
            if (specialElements != null && !specialElements.isEmpty()) {
                media = specialElements.get(0);
            }
        }
        if (file != null || media != null) {
            mediaPosition = position;
            if (thumbnailPath != null && thumbnailPath.getPath().startsWith(AppProperties.FILE_CACHE_DIR.getPath())) {
                FileUtil.delete(thumbnailPath); // delete old temp file
            }
            thumbnailPath = file;
            readable = true;
            /*
             * Do not remove the image from the cache after building the thumbnail when the series is associated to a
             * explorerModel (stream should be closed at least when closing the application or when free the cache).
             */
            buildThumbnail(media, series.getTagValue(TagW.ExplorerModel) != null, null);
            revalidate();
            repaint();
        }
    }

    public synchronized int getThumbnailSize() {
        return thumbnailSize;
    }

    public synchronized void setThumbnailSize(int thumbnailSize) {
        boolean update = this.thumbnailSize != thumbnailSize;
        if (update) {
            Object media = series.getMedia(mediaPosition, null, null);
            this.thumbnailSize = thumbnailSize;
            removeImageFromCache();
            buildThumbnail((MediaElement) media, series.getTagValue(TagW.ExplorerModel) != null, null);
        }
    }

    // --- DragGestureListener methods -----------------------------------

    @Override
    public void dragGestureRecognized(DragGestureEvent dge) {
        Component comp = dge.getComponent();
        try {
            GhostGlassPane glassPane = AppProperties.glassPane;
            glassPane.setIcon(getIcon());
            Point p = (Point) dge.getDragOrigin().clone();
            dragPressed = new Point(p.x - 4, p.y - 4);
            SwingUtilities.convertPointToScreen(p, comp);
            drawGlassPane(p);
            glassPane.setVisible(true);
            dge.startDrag(null, series, this);
        } catch (Exception e) {
            LOGGER.error("Prepare to drag", e); //$NON-NLS-1$
        }

    }

    @Override
    public void dragMouseMoved(DragSourceDragEvent dsde) {
        drawGlassPane(dsde.getLocation());
    }

    // --- DragSourceListener methods -----------------------------------

    @Override
    public void dragEnter(DragSourceDragEvent dsde) {
        // Do nothing
    }

    @Override
    public void dragOver(DragSourceDragEvent dsde) {
        // Do nothing
    }

    @Override
    public void dragExit(DragSourceEvent dsde) {
        // Do nothing
    }

    @Override
    public void dragDropEnd(DragSourceDropEvent dsde) {
        GhostGlassPane glassPane = AppProperties.glassPane;
        dragPressed = null;
        glassPane.setImagePosition(null);
        glassPane.setIcon(null);
        glassPane.setVisible(false);
    }

    @Override
    public void dropActionChanged(DragSourceDragEvent dsde) {
        // Do nothing
    }

    public void drawGlassPane(Point p) {
        if (dragPressed != null) {
            GhostGlassPane glassPane = AppProperties.glassPane;
            SwingUtilities.convertPointFromScreen(p, glassPane);
            p.translate(-dragPressed.x, -dragPressed.y);
            glassPane.setImagePosition(p);
        }
    }

    public MediaSeries<MediaElement> getSeries() {
        return (MediaSeries<MediaElement>) series;
    }

    @Override
    public void focusGained(FocusEvent e) {
        if (!e.isTemporary()) {
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

    @Override
    public void focusLost(FocusEvent e) {
        // Do nothing
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

    @Override
    protected void drawOverIcon(Graphics2D g2d, int x, int y, int width, int height) {
        if (dragPressed == null) {
            setBorder(series.isSelected() ? series.isFocused() ? onMouseOverBorderFocused : onMouseOverBorder
                : outMouseOverBorder);
            if (series.isOpen()) {
                g2d.setPaint(Color.BLACK);
                g2d.fillRect(x, y, 11, 11);
                g2d.setPaint(Color.GREEN);
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2d.fillArc(x + 2, y + 2, 7, 7, 0, 360);
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_DEFAULT);
            }

            g2d.setFont(FontTools.getFont10());
            int fontHeight = (int) (FontTools.getAccurateFontHeight(g2d) + 1.5f);
            Integer splitNb = (Integer) series.getTagValue(TagW.SplitSeriesNumber);
            if (splitNb != null) {
                String nb = "#" + splitNb; //$NON-NLS-1$
                int w = g2d.getFontMetrics().stringWidth(nb);
                g2d.setPaint(Color.BLACK);
                int sx = x + width - 2 - w;
                g2d.fillRect(sx - 2, y, w + 4, fontHeight);
                g2d.setPaint(Color.ORANGE);
                g2d.drawString(nb, sx, y + fontHeight - 3);
            }

            String nbImg = "[" + series.size(null) + "]"; //$NON-NLS-1$ //$NON-NLS-2$
            int hbleft = y + height - 3;
            int w = g2d.getFontMetrics().stringWidth(nbImg);
            g2d.setPaint(Color.BLACK);
            g2d.fillRect(x, y + height - fontHeight, w + 4, fontHeight);
            g2d.setPaint(Color.ORANGE);
            g2d.drawString(nbImg, x + 2, hbleft);

            // To avoid concurrency issue
            final JProgressBar bar = progressBar;
            if (bar != null) {
                if (series.getFileSize() > 0.0) {
                    g2d.drawString(FileUtil.humanReadableByte(series.getFileSize(), false), x + 2, hbleft - 12);
                }
                if (bar.isVisible()) {
                    // Draw in the bottom right corner of thumbnail
                    int shiftx = thumbnailSize - bar.getWidth();
                    int shifty = thumbnailSize - bar.getHeight();
                    g2d.translate(shiftx, shifty);
                    bar.paint(g2d);

                    // Draw in the top right corner
                    SeriesImporter seriesLoader = series.getSeriesLoader();
                    if (seriesLoader != null) {
                        boolean stopped = seriesLoader.isStopped();

                        g2d.translate(-shiftx, -shifty);
                        shiftx = thumbnailSize - stopButton.width;
                        shifty = 5;
                        g2d.translate(shiftx, shifty);
                        g2d.setColor(Color.RED);
                        g2d.setComposite(stopped ? TRANSPARENT_COMPOSITE : SOLID_COMPOSITE);
                        g2d.fill(stopButton);

                        g2d.translate(-shiftx, -shifty);
                        shiftx = shiftx - 3 * BUTTON_SIZE_HALF;
                        shifty = 5;
                        g2d.translate(shiftx, shifty);
                        g2d.setColor(Color.GREEN);
                        g2d.setComposite(stopped ? SOLID_COMPOSITE : TRANSPARENT_COMPOSITE);
                        g2d.fill(startButton);

                        g2d.translate(-shiftx, -shifty);
                    }
                }
            }
        }
    }

    @Override
    public void mouseClicked(MouseEvent e) {
        // Do nothing
    }

    @Override
    public void mousePressed(MouseEvent e) {
        // Do nothing
    }

    @Override
    public void mouseReleased(MouseEvent e) {
        if (progressBar != null) {
            // To avoid concurrency issue
            JProgressBar bar = progressBar;
            if (bar.isVisible()) {
                Point p = e.getPoint();
                Insets bd = this.getInsets();
                p.translate(-(thumbnailSize + bd.left - stopButton.width), -6 -bd.top);
                Rectangle rect = stopButton.getBounds();
                rect.grow(2, 2);
                if (rect.contains(p)) {
                    SeriesImporter loader = series.getSeriesLoader();
                    if (loader != null) {
                        loader.stop();
                    }
                    repaint();
                    return;
                }

                p.translate(3 * BUTTON_SIZE_HALF, 0);
                if (rect.contains(p)) {
                    SeriesImporter loader = series.getSeriesLoader();
                    if (loader != null) {
                        loader.resume();
                    }
                    repaint();
                }
            }
        }
    }

    @Override
    public void mouseEntered(MouseEvent e) {
        // Do nothing
    }

    @Override
    public void mouseExited(MouseEvent e) {
        // Do nothing
    }

}
