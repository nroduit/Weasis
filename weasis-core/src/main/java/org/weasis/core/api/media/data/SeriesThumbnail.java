/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.api.media.data;

import com.formdev.flatlaf.extras.FlatSVGIcon;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DragGestureEvent;
import java.awt.dnd.DragGestureListener;
import java.awt.dnd.DragSource;
import java.awt.dnd.DragSourceDragEvent;
import java.awt.dnd.DragSourceDropEvent;
import java.awt.dnd.DragSourceEvent;
import java.awt.dnd.DragSourceListener;
import java.awt.dnd.DragSourceMotionListener;
import java.awt.event.*;
import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.gui.util.AppProperties;
import org.weasis.core.api.gui.util.GhostGlassPane;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.gui.util.GuiUtils.IconColor;
import org.weasis.core.api.image.OpManager;
import org.weasis.core.api.media.data.MediaSeries.MEDIA_POSITION;
import org.weasis.core.api.util.FontItem;
import org.weasis.core.api.util.FontTools;
import org.weasis.core.api.util.ResourceUtil;
import org.weasis.core.api.util.ResourceUtil.ResourceIconPath;
import org.weasis.core.ui.editor.image.PlayViewButton;
import org.weasis.core.ui.editor.image.ViewButton;
import org.weasis.core.util.FileUtil;

public class SeriesThumbnail extends Thumbnail
    implements MouseListener,
        MouseMotionListener,
        DragGestureListener,
        DragSourceListener,
        DragSourceMotionListener,
        FocusListener {

  private static final Logger LOGGER = LoggerFactory.getLogger(SeriesThumbnail.class);

  private MediaSeries.MEDIA_POSITION mediaPosition = MediaSeries.MEDIA_POSITION.MIDDLE;

  private final Border thumbnailBorder = new EmptyBorder(5, 5, 0, 5);

  private JProgressBar progressBar;
  private final MediaSeries<? extends MediaElement> series;
  private final Function<String, Set<ResourceIconPath>> drawIcons;
  private final PlayViewButton playBtn;
  private Point dragPressed = null;
  private DragSource dragSource = null;

  public SeriesThumbnail(
      final MediaSeries<? extends MediaElement> sequence,
      int thumbnailSize,
      Function<String, Set<ResourceIconPath>> drawIcons) {
    super(thumbnailSize);
    if (sequence == null) {
      throw new IllegalArgumentException("Sequence cannot be null");
    }
    this.series = sequence;
    this.drawIcons = drawIcons;

    this.playBtn =
        new PlayViewButton(
            (invoker, x, y) -> {
              SeriesImporter loader = series.getSeriesLoader();
              if (loader != null) {
                PlayViewButton b = getPlayButton();
                if (b != null) {
                  if (b.getState() == PlayViewButton.eState.PAUSE) {
                    b.setState(PlayViewButton.eState.PLAY);
                    loader.stop();
                  } else {
                    b.setState(PlayViewButton.eState.PAUSE);
                    loader.resume();
                  }
                }
              }
            });

    // media can be null for seriesThumbnail
    MediaElement media = sequence.getMedia(MEDIA_POSITION.MIDDLE, null, null);
    // Handle special case for DICOM SR
    if (media == null) {
      List<MediaElement> specialElements =
          (List<MediaElement>) series.getTagValue(TagW.DicomSpecialElementList);
      if (specialElements != null && !specialElements.isEmpty()) {
        media = specialElements.getFirst();
      }
    }
    /*
     * Do not remove the image from the cache after building the thumbnail when the series is
     * associated to a explorerModel (stream should be closed at least when closing the application
     * or when free the cache).
     */
    init(media, series.getTagValue(TagW.ExplorerModel) != null, null);
  }

  @Override
  protected void init(MediaElement media, boolean keepMediaCache, OpManager opManager) {
    super.init(media, keepMediaCache, opManager);
    setBorder(thumbnailBorder);
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

  public PlayViewButton getPlayButton() {
    return playBtn;
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
    this.addMouseMotionListener(this);
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
      media = series.getFirstSpecialElement();
    }
    if (file != null || media != null) {
      mediaPosition = position;
      if (thumbnailPath != null
          && thumbnailPath.getPath().startsWith(AppProperties.FILE_CACHE_DIR.getPath())) {
        FileUtil.delete(thumbnailPath); // delete old temp file
      }
      removeImageFromCache();
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
    int size = GuiUtils.getScaleLength(thumbnailSize);
    if (this.thumbnailSize != size) {
      this.thumbnailSize = GuiUtils.getScaleLength(size);
      MediaElement media = series.getMedia(mediaPosition, null, null);
      if (media == null) {
        media = series.getFirstSpecialElement();
      }
      removeImageFromCache();
      buildThumbnail(media, series.getTagValue(TagW.ExplorerModel) != null, null);
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
      LOGGER.error("Prepare to drag", e);
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
      Object[] oldRenderingHints = GuiUtils.setRenderingHints(g2d, true, false, true);
      int inset = GuiUtils.getScaleLength(2);
      if (series.isOpen()) {
        if (series.isSelected() && series.isFocused()) {
          g2d.setPaint(IconColor.ACTIONS_YELLOW.getColor());
        } else {
          g2d.setPaint(IconColor.ACTIONS_GREEN.getColor());
        }
        int size = inset * 5;
        g2d.fillArc(x + inset, y + inset, size, size, 0, 360);
        g2d.setPaint(Color.BLACK);
        g2d.drawArc(x + inset, y + inset, size, size, 0, 360);
      }

      g2d.setFont(
          width > DEFAULT_SIZE
              ? FontItem.MINI_SEMIBOLD.getFont()
              : FontItem.MICRO_SEMIBOLD.getFont());
      FontMetrics fontMetrics = g2d.getFontMetrics();
      final int fontHeight = fontMetrics.getHeight();
      int descent = g2d.getFontMetrics().getDescent();
      Integer splitNb = (Integer) series.getTagValue(TagW.SplitSeriesNumber);
      if (splitNb != null) {
        String nb = "#" + splitNb;
        int w = g2d.getFontMetrics().stringWidth(nb);
        int sx = x + width - inset - w;
        FontTools.paintColorFontOutline(
            g2d,
            nb,
            sx,
            y + inset + fontHeight - (float) descent,
            IconColor.ACTIONS_BLUE.getColor());
      }

      int number = series.size(null);
      if (number > 0) {
        FontTools.paintColorFontOutline(
            g2d,
            String.valueOf(number),
            x + (float) inset,
            y + height - (float) (inset + descent),
            IconColor.ACTIONS_BLUE.getColor());
      }

      // To avoid concurrency issue
      final JProgressBar bar = progressBar;
      if (bar != null) {
        drawProgression(g2d, x, y, height, bar, inset, fontHeight);
      } else {
        String seriesUID = (String) series.getTagValue(TagW.get("SeriesInstanceUID"));
        Set<ResourceIconPath> paths = drawIcons.apply(seriesUID);
        if (paths != null && !paths.isEmpty()) {
          double yPos = height;
          for (ResourceIconPath path : paths) {
            FlatSVGIcon icon = ResourceUtil.getIcon(path);
            yPos -= icon.getIconHeight() + inset;
            double shiftX = (double) width - icon.getIconWidth();
            g2d.translate(shiftX, yPos);
            icon.paintIcon(this, g2d, x, y);
            g2d.translate(-shiftX, -yPos);
          }
        }
      }
      GuiUtils.resetRenderingHints(g2d, oldRenderingHints);
    }
  }

  private void drawProgression(
      Graphics2D g2d, int x, int y, int height, JProgressBar bar, int inset, int fontHeight) {
    if (series.getFileSize() > 0.0) {
      g2d.drawString(
          FileUtil.humanReadableByte(series.getFileSize(), false),
          x + inset,
          y + height - fontHeight - inset * 2);
    }
    if (bar.isVisible()) {
      // Draw in the bottom right corner of thumbnail
      double shiftX = (double) thumbnailSize - bar.getWidth();
      double shiftY = (double) thumbnailSize - bar.getHeight();
      g2d.translate(shiftX, shiftY);
      bar.paint(g2d);
      g2d.translate(-shiftX, -shiftY);

      // Draw in the top right corner
      SeriesImporter seriesLoader = series.getSeriesLoader();
      playBtn.setVisible(seriesLoader != null);
      if (seriesLoader != null) {
        if (seriesLoader.isStopped()) {
          playBtn.setState(PlayViewButton.eState.PLAY);
        } else {
          playBtn.setState(PlayViewButton.eState.PAUSE);
        }
        Icon icon = playBtn.getIcon();
        playBtn.x = (double) thumbnailSize - icon.getIconWidth() - inset * 3.0;
        playBtn.y = thumbnailSize - bar.getHeight() - icon.getIconHeight() - inset * 5.0;
        ViewButton.drawButtonBackground(g2d, this, playBtn, icon);
      }
    }
  }

  @Override
  public void mouseClicked(MouseEvent e) {
    // Do nothing
  }

  @Override
  public void mousePressed(MouseEvent e) {
    if (playBtn.isVisible() && playBtn.contains(e.getPoint())) {
      e.consume();
      playBtn.showPopup(e.getComponent(), e.getX(), e.getY());
    }
  }

  @Override
  public void mouseReleased(MouseEvent e) {
    // Do nothing
  }

  @Override
  public void mouseEntered(MouseEvent e) {
    // Do nothing
  }

  @Override
  public void mouseExited(MouseEvent e) {
    // Do nothing
  }

  @Override
  public void mouseDragged(MouseEvent e) {
    // Do nothing
  }

  @Override
  public void mouseMoved(MouseEvent e) {
    if (playBtn.isVisible()) {
      boolean hover = playBtn.contains(e.getPoint());
      if (hover != playBtn.isHover()) {
        playBtn.setHover(hover);
        repaint();
      }
    }
  }
}
