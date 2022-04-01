/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.base.explorer.list;

import com.formdev.flatlaf.ui.FlatUIUtils;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DragGestureEvent;
import java.awt.dnd.DragSource;
import java.awt.dnd.DragSourceDragEvent;
import java.awt.dnd.DragSourceDropEvent;
import java.awt.dnd.DragSourceEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.net.URI;
import java.text.NumberFormat;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;
import javax.swing.Action;
import javax.swing.DefaultListSelectionModel;
import javax.swing.Icon;
import javax.swing.JList;
import javax.swing.JPopupMenu;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.event.ListSelectionEvent;
import org.weasis.base.explorer.JIThumbnailCache;
import org.weasis.base.explorer.JIUtility;
import org.weasis.base.explorer.Messages;
import org.weasis.base.explorer.ThumbnailRenderer;
import org.weasis.core.api.gui.util.AppProperties;
import org.weasis.core.api.gui.util.GhostGlassPane;
import org.weasis.core.api.media.data.Codec;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.api.media.data.MediaElement;
import org.weasis.core.api.media.data.MediaReader;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.media.data.TagUtil;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.api.util.LocalUtil;
import org.weasis.core.ui.docking.UIManager;
import org.weasis.core.ui.editor.SeriesViewerFactory;
import org.weasis.core.ui.editor.ViewerPluginBuilder;
import org.weasis.core.ui.util.DefaultAction;
import org.weasis.core.util.FileUtil;

public abstract class AbstractThumbnailList<E extends MediaElement> extends JList<E>
    implements ThumbnailList<E> {

  public static final String SECTION_CHANGED = "SECTION_CHANGED"; // NON-NLS
  public static final String DIRECTORY_SIZE = "DIRECTORY_SIZE"; // NON-NLS

  private static final NumberFormat intGroupFormat = LocalUtil.getIntegerInstance();

  static {
    intGroupFormat.setGroupingUsed(true);
  }

  protected final JIThumbnailCache thumbCache;

  private boolean changed;
  private Point dragPressed = null;
  private DragSource dragSource = null;

  protected AbstractThumbnailList(JIThumbnailCache thumbCache) {
    this(thumbCache, HORIZONTAL_WRAP);
  }

  protected AbstractThumbnailList(JIThumbnailCache thumbCache, int scrollMode) {
    super();
    this.thumbCache = thumbCache == null ? new JIThumbnailCache() : thumbCache;
    this.setModel(newModel());
    this.changed = false;

    DefaultListSelectionModel selectionModel = new DefaultListSelectionModel();
    this.setBackground(FlatUIUtils.getUIColor("List.background", Color.DARK_GRAY));
    setSelectionModel(selectionModel);
    // setTransferHandler(new ListTransferHandler());
    ThumbnailRenderer<E> panel = new ThumbnailRenderer<>();
    Dimension dim = panel.getPreferredSize();
    setCellRenderer(panel);
    setFixedCellHeight(dim.height);
    setFixedCellWidth(dim.width);
    setVisibleRowCount(-1);

    setLayoutOrientation(scrollMode);

    setVerifyInputWhenFocusTarget(false);
  }

  public JIThumbnailCache getThumbCache() {
    return thumbCache;
  }

  @Override
  public Component asComponent() {
    return this;
  }

  /**
   * Marks this <tt>Observable</tt> object as having been changed; the <tt>hasChanged</tt> method
   * will now return <tt>true</tt>.
   */
  @Override
  public synchronized void setChanged() {
    this.changed = true;
  }

  /**
   * Indicates that this object has no longer changed, or that it has already notified all of its
   * observers of its most recent change, so that the <tt>hasChanged</tt> method will now return
   * <tt>false</tt>. This method is called automatically by the <code>notifyObservers</code>
   * methods.
   */
  @Override
  public synchronized void clearChanged() {
    this.changed = false;
  }

  /**
   * Tests if this object has changed.
   *
   * @return <code>true</code> if and only if the <code>setChanged</code> method has been called
   *     more recently than the <code>clearChanged</code> method on this object; <code>false</code>
   *     otherwise.
   */
  @Override
  public synchronized boolean hasChanged() {
    return this.changed;
  }

  @Override
  public void registerListeners() {
    registerDragListeners();
    addMouseListener(new PopupTrigger());

    // TODO prefer the use of Key Bindings rather than keyListener
    // @see http://docs.oracle.com/javase/tutorial/uiswing/misc/keybinding.html

    addKeyListener(
        new KeyAdapter() {
          @Override
          public void keyPressed(KeyEvent e) {
            AbstractThumbnailList.this.jiThumbnailKeyPressed(e);
          }
        });
  }

  public void registerDragListeners() {
    if (dragSource != null) {
      dragSource.removeDragSourceListener(this);
      dragSource.removeDragSourceMotionListener(this);
    }
    dragSource = DragSource.getDefaultDragSource();
    dragSource.createDefaultDragGestureRecognizer(this, DnDConstants.ACTION_COPY, this);
    dragSource.addDragSourceMotionListener(this);
  }

  @Override
  public IThumbnailModel<E> getThumbnailListModel() {
    return (IThumbnailModel) getModel();
  }

  // Subclass JList to workaround bug 4832765, which can cause the
  // scroll pane to not let the user easily scroll up to the beginning
  // of the list. An alternative would be to set the unitIncrement
  // of the JScrollBar to a fixed value. You wouldn't get the nice
  // aligned scrolling, but it should work.
  @Override
  public int getScrollableUnitIncrement(
      final Rectangle visibleRect, final int orientation, final int direction) {
    int row;
    if ((orientation == SwingConstants.VERTICAL)
        && (direction < 0)
        && ((row = getFirstVisibleIndex()) != -1)) {
      final Rectangle r = getCellBounds(row, row);
      if ((r.y == visibleRect.y) && (row != 0)) {
        final Point loc = r.getLocation();
        loc.y--;
        final int prevIndex = locationToIndex(loc);
        final Rectangle prevR = getCellBounds(prevIndex, prevIndex);

        if ((prevR == null) || (prevR.y >= r.y)) {
          return 0;
        }
        return prevR.height;
      }
    }
    return super.getScrollableUnitIncrement(visibleRect, orientation, direction);
  }

  @Override
  public String getToolTipText(final MouseEvent evt) {
    Point pt = evt.getPoint();
    final int index = locationToIndex(pt);
    final Rectangle thumBounds = getCellBounds(index, index);

    if (thumBounds == null || !thumBounds.contains(pt)) {
      return null;
    }

    final E item = getModel().getElementAt(index);
    if (item == null || item.getName() == null) {
      return null;
    }

    return """
      <html>
        %s<br>
        %s: %s<br>
        %s: %s<br>
      </html>
      """
        .formatted(
            item.getName(),
            Messages.getString("JIThumbnailList.size"),
            FileUtil.humanReadableByte(item.getLength(), false),
            Messages.getString("JIThumbnailList.date"),
            TagUtil.formatDateTime(Instant.ofEpochMilli(item.getLastModified())));
  }

  public void reset() {
    setLayoutOrientation(HORIZONTAL_WRAP);

    getThumbnailListModel().reload();
    setVisibleRowCount(-1);
    clearSelection();
    ensureIndexIsVisible(0);
  }

  private MediaSeries buildSeriesFromMediaElement(E mediaElement) {
    if (mediaElement != null) {
      MediaReader reader = mediaElement.getMediaReader();

      TagW tname;
      String tvalue;

      Codec codec = reader.getCodec();
      String sUID;
      String gUID;
      if (isDicomMedia(mediaElement)
          && codec != null
          && codec.isMimeTypeSupported("application/dicom")) { // NON-NLS
        if (reader.getMediaElement() == null) {
          // DICOM is not readable
          return null;
        }
        sUID = (String) reader.getTagValue(TagW.get("SeriesInstanceUID"));
        gUID = (String) reader.getTagValue(TagW.get("PatientID"));
        tname = TagW.get("PatientName");
        tvalue = (String) reader.getTagValue(tname);
      } else {
        sUID = mediaElement.getMediaURI().toString();
        gUID = sUID;
        tname = TagW.FileName;
        tvalue = mediaElement.getName();
      }

      return ViewerPluginBuilder.buildMediaSeriesWithDefaultModel(
          reader, gUID, tname, tvalue, sUID);
    }
    return null;
  }

  public void openSelection() {
    E object = getSelectedValue();
    openSelection(Collections.singletonList(object), true, true, false);
  }

  public void openSelection(
      List<E> selMedias,
      boolean compareEntryToBuildNewViewer,
      boolean bestDefaultLayout,
      boolean inSelView) {
    if (selMedias != null) {
      boolean oneFile = selMedias.size() == 1;
      String sUID = null;
      String gUID;
      ArrayList<MediaSeries<? extends MediaElement>> list = new ArrayList<>();
      for (E mediaElement : selMedias) {

        MediaReader reader = mediaElement.getMediaReader();

        TagW tname;
        String tvalue;

        Codec codec = reader.getCodec();
        if (isDicomMedia(mediaElement)
            && codec != null
            && codec.isMimeTypeSupported("application/dicom")) { // NON-NLS
          if (reader.getMediaElement() == null) {
            // DICOM is not readable
            return;
          }
          sUID = (String) reader.getTagValue(TagW.get("SeriesInstanceUID"));
          gUID = (String) reader.getTagValue(TagW.get("PatientID"));
          tname = TagW.get("PatientName");
          tvalue = (String) reader.getTagValue(tname);
        } else {
          sUID =
              oneFile
                  ? mediaElement.getMediaURI().toString()
                  : sUID == null ? UUID.randomUUID().toString() : sUID;
          gUID = sUID;
          tname = TagW.FileName;
          tvalue = oneFile ? mediaElement.getName() : sUID;
        }

        MediaSeries s =
            ViewerPluginBuilder.buildMediaSeriesWithDefaultModel(reader, gUID, tname, tvalue, sUID);
        if (s != null && !list.contains(s)) {
          list.add(s);
        }
      }
      if (!list.isEmpty()) {
        Map<String, Object> props = Collections.synchronizedMap(new HashMap<>());
        props.put(ViewerPluginBuilder.CMP_ENTRY_BUILD_NEW_VIEWER, compareEntryToBuildNewViewer);
        props.put(ViewerPluginBuilder.BEST_DEF_LAYOUT, bestDefaultLayout);
        props.put(ViewerPluginBuilder.SCREEN_BOUND, null);
        if (inSelView) {
          props.put(ViewerPluginBuilder.ADD_IN_SELECTED_VIEW, true);
        }

        ArrayList<String> mimes = new ArrayList<>();
        for (MediaSeries s : list) {
          String mime = s.getMimeType();
          if (mime != null && !mimes.contains(mime)) {
            mimes.add(mime);
          }
        }
        for (String mime : mimes) {
          SeriesViewerFactory plugin = UIManager.getViewerFactory(mime);
          if (plugin != null) {
            ArrayList<MediaSeries<MediaElement>> seriesList = new ArrayList<>();
            for (MediaSeries s : list) {
              if (mime.equals(s.getMimeType())) {
                seriesList.add(s);
              }
            }
            ViewerPluginBuilder builder =
                new ViewerPluginBuilder(
                    plugin, seriesList, ViewerPluginBuilder.DefaultDataModel, props);
            ViewerPluginBuilder.openSequenceInPlugin(builder);
          }
        }
      }
    }
  }

  public void openGroup(
      List<E> selMedias,
      boolean compareEntryToBuildNewViewer,
      boolean bestDefaultLayout,
      boolean modeLayout,
      boolean inSelView) {
    if (selMedias != null) {
      String groupUID = null;

      if (modeLayout) {
        groupUID = UUID.randomUUID().toString();
      }
      Map<SeriesViewerFactory, List<MediaSeries<MediaElement>>> plugins = new HashMap<>();
      for (E m : selMedias) {
        String mime = m.getMimeType();
        if (mime != null) {
          SeriesViewerFactory plugin = UIManager.getViewerFactory(mime);
          if (plugin != null) {
            List<MediaSeries<MediaElement>> list =
                plugins.computeIfAbsent(plugin, k -> new ArrayList<>(modeLayout ? 10 : 1));

            // Get only application readers from files
            MediaReader mreader = m.getMediaReader();
            if (modeLayout) {
              MediaSeries<MediaElement> series =
                  ViewerPluginBuilder.buildMediaSeriesWithDefaultModel(
                      mreader, groupUID, null, null, null);
              if (series != null) {
                list.add(series);
              }
            } else {
              MediaSeries<MediaElement> series;
              if (list.isEmpty()) {
                series = ViewerPluginBuilder.buildMediaSeriesWithDefaultModel(mreader);
                if (series != null) {
                  list.add(series);
                }
              } else {
                series = list.get(0);
                if (series != null) {
                  MediaElement[] ms = mreader.getMediaElement();
                  if (ms != null) {
                    for (MediaElement media : ms) {
                      media.setTag(
                          TagW.get("SeriesInstanceUID"), series.getTagValue(series.getTagID()));
                      URI uri = media.getMediaURI();
                      media.setTag(
                          TagW.get("SOPInstanceUID"),
                          uri == null ? UUID.randomUUID().toString() : uri.toString());
                      series.addMedia(media);
                    }
                  }
                }
              }
            }
          }
        }
      }

      Map<String, Object> props = Collections.synchronizedMap(new HashMap<>());
      props.put(ViewerPluginBuilder.CMP_ENTRY_BUILD_NEW_VIEWER, compareEntryToBuildNewViewer);
      props.put(ViewerPluginBuilder.BEST_DEF_LAYOUT, bestDefaultLayout);
      props.put(ViewerPluginBuilder.SCREEN_BOUND, null);
      if (inSelView) {
        props.put(ViewerPluginBuilder.ADD_IN_SELECTED_VIEW, true);
      }

      for (Entry<SeriesViewerFactory, List<MediaSeries<MediaElement>>> item : plugins.entrySet()) {
        ViewerPluginBuilder builder =
            new ViewerPluginBuilder(
                item.getKey(), item.getValue(), ViewerPluginBuilder.DefaultDataModel, props);
        ViewerPluginBuilder.openSequenceInPlugin(builder);
      }
    }
  }

  private static boolean isDicomMedia(MediaElement mediaElement) {
    if (mediaElement != null) {
      String mime = mediaElement.getMimeType();
      if (mime != null) {
        return mime.contains("dicom"); // NON-NLS
      }
    }
    return false;
  }

  public void nextPage(final KeyEvent e) {
    final int lastIndex = getLastVisibleIndex();

    if (getLayoutOrientation() != JList.HORIZONTAL_WRAP) {
      e.consume();
      final int firstIndex = getFirstVisibleIndex();
      final int visibleRows = getVisibleRowCount();
      final int visibleColumns =
          (int) (((float) (lastIndex - firstIndex) / (float) visibleRows) + .5);
      final int visibleItems = visibleRows * visibleColumns;

      final int val =
          (lastIndex + visibleItems >= getModel().getSize())
              ? getModel().getSize() - 1
              : lastIndex + visibleItems;
      clearSelection();
      setSelectedIndex(val);
      fireSelectionValueChanged(val, val, false);
    } else {
      clearSelection();
      setSelectedIndex(lastIndex);
      fireSelectionValueChanged(lastIndex, lastIndex, false);
    }
  }

  public void lastPage(final KeyEvent e) {
    final int lastIndex = getLastVisibleIndex();

    if (getLayoutOrientation() != JList.HORIZONTAL_WRAP) {
      e.consume();
      final int firstIndex = getFirstVisibleIndex();
      final int visibleRows = getVisibleRowCount();
      final int visibleColumns =
          (int) (((float) (lastIndex - firstIndex) / (float) visibleRows) + .5);
      final int visibleItems = visibleRows * visibleColumns;

      final int val = Math.max((firstIndex - 1) - visibleItems, 0);
      clearSelection();
      setSelectedIndex(val);
      fireSelectionValueChanged(val, val, false);
    } else {
      clearSelection();
      setSelectedIndex(lastIndex);
      fireSelectionValueChanged(lastIndex, lastIndex, false);
    }
  }

  public void jiThumbnailKeyPressed(final KeyEvent e) {
    switch (e.getKeyCode()) {
      case KeyEvent.VK_PAGE_DOWN -> nextPage(e);
      case KeyEvent.VK_PAGE_UP -> lastPage(e);
      case KeyEvent.VK_ENTER -> {
        openSelection();
        e.consume();
      }
    }
  }

  public Action buildRefreshAction() {
    // TODO set this action in toolbar
    return new DefaultAction(
        Messages.getString("JIThumbnailList.refresh_list"),
        event -> {
          final Thread runner =
              new Thread(AbstractThumbnailList.this.getThumbnailListModel()::reload);
          runner.start();
        });
  }

  public int getLastSelectedIndex() {
    return super.getSelectedValuesList().indexOf(super.getSelectedValue());
  }

  @Override
  public void listValueChanged(final ListSelectionEvent e) {
    setChanged();
    notifyObservers(SECTION_CHANGED);
    clearChanged();
  }

  final class PopupTrigger extends MouseAdapter {

    @Override
    public void mouseClicked(MouseEvent e) {
      mouseClickedEvent(e);
    }

    @Override
    public void mousePressed(final MouseEvent evt) {
      showPopup(evt);
    }

    @Override
    public void mouseReleased(final MouseEvent evt) {
      showPopup(evt);
    }

    private void showPopup(final MouseEvent evt) {
      // Context menu
      if (SwingUtilities.isRightMouseButton(evt)) {
        JPopupMenu popupMenu = AbstractThumbnailList.this.buildContextMenu(evt);
        if (popupMenu != null) {
          popupMenu.show(evt.getComponent(), evt.getX(), evt.getY());
        }
      }
    }
  }

  /**
   * If this object has changed, as indicated by the <code>hasChanged</code> method, then notify all
   * of its observers and then call the <code>clearChanged</code> method to indicate that this
   * object has no longer changed.
   *
   * <p>Each observer has its <code>update</code> method called with two arguments: this observable
   * object and <code>null</code>. In other words, this method is equivalent to:
   *
   * <blockquote>
   *
   * <tt> notifyObservers(null)</tt>
   *
   * </blockquote>
   */
  public void notifyObservers() {
    notifyObservers(null);
  }

  /**
   * If this object has changed, as indicated by the <code>hasChanged</code> method, then notify all
   * of its observers and then call the <code>clearChanged</code> method to indicate that this
   * object has no longer changed.
   *
   * <p>Each observer has its <code>update</code> method called with two arguments: this observable
   * object and the <code>arg</code> argument.
   *
   * @param arg any object.
   */
  @Override
  public void notifyObservers(final Object arg) {

    synchronized (this) {
      if (!this.changed) {
        return;
      }
      clearChanged();
    }
  }

  public void notifyStatusBar(final Object arg) {
    synchronized (this) {
      clearChanged();
    }
  }

  // --- DragGestureListener methods -----------------------------------

  @Override
  public void dragGestureRecognized(DragGestureEvent dge) {
    Component comp = dge.getComponent();
    if (comp instanceof AbstractThumbnailList) {
      int index = getSelectedIndex();
      E media = getSelectedValue();
      MediaSeries<?> series = buildSeriesFromMediaElement(media);
      if (series != null) {
        GhostGlassPane glassPane = AppProperties.glassPane;
        Icon icon = null;
        if (media instanceof ImageElement imageElement) {
          icon = thumbCache.getThumbnailFor(imageElement, this, index);
        }
        if (icon == null) {
          icon = JIUtility.getSystemIcon(media);
        }
        glassPane.setIcon(icon);
        dragPressed = new Point(icon.getIconWidth() / 2, icon.getIconHeight() / 2);
        Point p = (Point) dge.getDragOrigin().clone();
        SwingUtilities.convertPointToScreen(p, comp);
        drawGlassPane(p);
        glassPane.setVisible(true);
        dge.startDrag(null, series, this);
      }
    }
  }

  @Override
  public void dragMouseMoved(DragSourceDragEvent dragEvent) {
    drawGlassPane(dragEvent.getLocation());
  }

  // --- DragSourceListener methods -----------------------------------

  @Override
  public void dragEnter(DragSourceDragEvent dragEvent) {}

  @Override
  public void dragOver(DragSourceDragEvent dragEvent) {}

  @Override
  public void dragExit(DragSourceEvent dragEvent) {}

  @Override
  public void dragDropEnd(DragSourceDropEvent dragEvent) {
    GhostGlassPane glassPane = AppProperties.glassPane;
    dragPressed = null;
    glassPane.setImagePosition(null);
    glassPane.setIcon(null);
    glassPane.setVisible(false);
  }

  @Override
  public void dropActionChanged(DragSourceDragEvent dragEvent) {}

  public void drawGlassPane(Point p) {
    if (dragPressed != null) {
      GhostGlassPane glassPane = AppProperties.glassPane;
      SwingUtilities.convertPointFromScreen(p, glassPane);
      p.translate(-dragPressed.x, -dragPressed.y);
      glassPane.setImagePosition(p);
    }
  }

  public List<E> getSelected(final MouseEvent e) {
    List<E> selected = getSelectedValuesList();
    if (!selected.isEmpty()) {
      int index = locationToIndex(e.getPoint());
      if (index >= 0) {
        E selectedMedia = getModel().getElementAt(index);
        if (selectedMedia != null && !selected.contains(selectedMedia)) {
          selected = Collections.singletonList(selectedMedia);
          setSelectedValue(selectedMedia, false);
        }
      }
    }
    return selected;
  }

  public abstract IThumbnailModel<E> newModel();

  public abstract JPopupMenu buildContextMenu(final MouseEvent e);

  public abstract void mouseClickedEvent(MouseEvent e);
}
