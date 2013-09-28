package org.weasis.core.api.media.data;

import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
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
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.io.File;
import java.util.List;

import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JViewport;
import javax.swing.SwingUtilities;
import javax.swing.ToolTipManager;
import javax.swing.border.Border;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;

import org.weasis.core.api.gui.util.AbstractProperties;
import org.weasis.core.api.gui.util.GhostGlassPane;
import org.weasis.core.api.media.data.MediaSeries.MEDIA_POSITION;
import org.weasis.core.api.util.FileUtil;
import org.weasis.core.api.util.FontTools;

public class SeriesThumbnail extends Thumbnail implements MouseListener, DragGestureListener, DragSourceListener,
    DragSourceMotionListener, FocusListener {

    private MediaSeries.MEDIA_POSITION mediaPosition = MediaSeries.MEDIA_POSITION.MIDDLE;
    // Get the closest cursor size regarding to the platform
    private final Border onMouseOverBorderFocused = new CompoundBorder(new EmptyBorder(2, 2, 0, 2), new LineBorder(
        Color.orange, 2));
    private final Border onMouseOverBorder = new CompoundBorder(new EmptyBorder(2, 2, 0, 2), new LineBorder(new Color(
        255, 224, 178), 2));
    private final Border outMouseOverBorder = new EmptyBorder(4, 4, 2, 4);
    private JProgressBar progressBar;
    private final MediaSeries<?> series;
    private Point dragPressed = null;
    private DragSource dragSource = null;

    public SeriesThumbnail(final MediaSeries<?> sequence, int thumbnailSize) {
        super((File) null, thumbnailSize);
        if (sequence == null) {
            throw new IllegalArgumentException("Sequence cannot be null"); //$NON-NLS-1$
        }
        this.series = sequence;
        // media can be null for seriesThumbnail
        MediaElement<?> media = (MediaElement<?>) sequence.getMedia(MEDIA_POSITION.MIDDLE, null, null);
        // Handle special case for DICOM SR
        if (media == null) {
            List<MediaElement<?>> specialElements =
                (List<MediaElement<?>>) series.getTagValue(TagW.DicomSpecialElementList);
            if (specialElements != null && specialElements.size() > 0) {
                media = specialElements.get(0);
            }
        }
        init(media);
    }

    @Override
    protected void init(MediaElement<?> media) {
        super.init(media);
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
        Object media = series.getMedia(mediaPosition, null, null);
        if (file != null || media instanceof MediaElement<?>) {
            mediaPosition = position;
            thumbnailPath = file;
            readable = true;
            buildThumbnail((MediaElement<?>) media);
        }
    }

    public synchronized int getThumbnailSize() {
        return thumbnailSize;
    }

    public synchronized void setThumbnailSize(int thumbnailSize) {
        boolean update = this.thumbnailSize != thumbnailSize;
        if (update) {
            Object media = series.getMedia(mediaPosition, null, null);
            if (thumbnailPath != null || media instanceof MediaElement<?>) {
                this.thumbnailSize = thumbnailSize;
                buildThumbnail((MediaElement<?>) media);
            }
        }
    }

    // --- DragGestureListener methods -----------------------------------

    @Override
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

    @Override
    public void dragMouseMoved(DragSourceDragEvent dsde) {
        drawGlassPane(dsde.getLocation());
    }

    // --- DragSourceListener methods -----------------------------------

    @Override
    public void dragEnter(DragSourceDragEvent dsde) {
    }

    @Override
    public void dragOver(DragSourceDragEvent dsde) {
    }

    @Override
    public void dragExit(DragSourceEvent dsde) {

    }

    @Override
    public void dragDropEnd(DragSourceDropEvent dsde) {
        GhostGlassPane glassPane = AbstractProperties.glassPane;
        dragPressed = null;
        glassPane.setImagePosition(null);
        glassPane.setIcon(null);
        glassPane.setVisible(false);
    }

    @Override
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

    public MediaSeries<?> getSeries() {
        return series;
    }

    @Override
    public void focusGained(FocusEvent e) {
        if (!e.isTemporary()) {
            // setBorder(onMouseOverBorder);
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
        // if (!e.isTemporary()) {
        // setBorder(outMouseOverBorder);
        // }
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
        setBorder(series.isSelected() ? series.isFocused() ? onMouseOverBorderFocused : onMouseOverBorder
            : outMouseOverBorder);
        if (series.isOpen()) {
            g2d.setPaint(Color.green);
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2d.fillArc(x + 2, y + 2, 7, 7, 0, 360);
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_DEFAULT);
        }
        g2d.setPaint(Color.ORANGE);
        // if (series.isSelected()) {
        // g2d.drawRect(x + 12, y + 3, 5, 5);
        // }
        Integer splitNb = (Integer) series.getTagValue(TagW.SplitSeriesNumber);
        g2d.setFont(FontTools.getFont10());
        int hbleft = y + height - 2;
        if (splitNb != null) {
            g2d.drawString("#" + splitNb + " [" + series.size(null) + "]", x + 2, hbleft); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ $NON-NLS-2$ $NON-NLS-3$
        } else {
            g2d.drawString("[" + series.size(null) + "]", x + 2, hbleft); //$NON-NLS-1$ //$NON-NLS-2$ $NON-NLS-2$
        }

        // To avoid concurrency issue
        final JProgressBar bar = progressBar;
        if (bar != null) {
            if (series.getFileSize() > 0.0) {
                g2d.drawString(FileUtil.formatSize(series.getFileSize()), x + 2, hbleft - 12);
            }
            if (bar.isVisible()) {
                // Draw in the bottom right corner of thumbnail space;
                int shiftx = thumbnailSize - bar.getWidth();
                int shifty = thumbnailSize - bar.getHeight();
                g2d.translate(shiftx, shifty);
                bar.paint(g2d);
                g2d.translate(-shiftx, -shifty);
            }
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
        if (progressBar != null) {
            // To avoid concurrency issue
            JProgressBar bar = progressBar;
            if (bar.isVisible()) {
                Rectangle rect = bar.getBounds();
                rect.x = thumbnailSize - rect.width;
                rect.y = thumbnailSize - rect.height;
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
    }

    @Override
    public void mouseEntered(MouseEvent e) {
    }

    @Override
    public void mouseExited(MouseEvent e) {
    }

}
