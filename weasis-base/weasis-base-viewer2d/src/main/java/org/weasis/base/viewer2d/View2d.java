/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.base.viewer2d;

import java.awt.Dimension;
import java.awt.Rectangle;
import java.awt.datatransfer.Transferable;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.FocusEvent;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.Optional;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.explorer.model.DataExplorerModel;
import org.weasis.core.api.explorer.model.TreeModel;
import org.weasis.core.api.gui.util.ActionState;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.gui.util.Feature;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.gui.util.MouseActionAdapter;
import org.weasis.core.api.image.AffineTransformOp;
import org.weasis.core.api.image.FilterOp;
import org.weasis.core.api.image.PseudoColorOp;
import org.weasis.core.api.image.SimpleOpManager;
import org.weasis.core.api.image.WindowOp;
import org.weasis.core.api.image.util.ImageLayer;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.media.data.MediaSeriesGroup;
import org.weasis.core.api.media.data.Series;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.ui.editor.ViewerOpenOptions;
import org.weasis.core.ui.editor.ViewerPluginBuilder;
import org.weasis.core.ui.editor.image.ContextMenuHandler;
import org.weasis.core.ui.editor.image.DefaultView2d;
import org.weasis.core.ui.editor.image.GraphicEditActions;
import org.weasis.core.ui.editor.image.ImageViewerEventManager;
import org.weasis.core.ui.editor.image.ImageViewerPlugin;
import org.weasis.core.ui.editor.image.MouseActions;
import org.weasis.core.ui.editor.image.SequenceHandler;
import org.weasis.core.ui.editor.image.ViewCanvas;
import org.weasis.core.ui.editor.image.ViewerPlugin;
import org.weasis.core.ui.model.graphic.Graphic;

public class View2d extends DefaultView2d<ImageElement> {
  private static final Logger LOGGER = LoggerFactory.getLogger(View2d.class);

  private final Dimension oldSize;
  private final ContextMenuHandler contextMenuHandler;

  public View2d(ImageViewerEventManager<ImageElement> eventManager) {
    super(eventManager);
    SimpleOpManager manager = imageLayer.getDisplayOpManager();
    manager.addImageOperationAction(new WindowOp());
    manager.addImageOperationAction(new FilterOp());
    manager.addImageOperationAction(new PseudoColorOp());
    // Zoom and Rotation must be the last operations for the lens
    manager.addImageOperationAction(new AffineTransformOp());

    this.contextMenuHandler = new ContextMenuHandler(this);
    this.infoLayer = new InfoLayer(this);
    this.oldSize = new Dimension(0, 0);
  }

  @Override
  public void registerDefaultListeners() {
    buildPanner();
    super.registerDefaultListeners();
    setTransferHandler(new SeriesHandler());

    addComponentListener(
        new ComponentAdapter() {

          @Override
          public void componentResized(ComponentEvent e) {
            View2d.this.componentResized();
          }
        });
  }

  private void componentResized() {
    Double currentZoom = (Double) actionsInView.get(ActionW.ZOOM.cmd());
    /*
     * Negative value means a default value according to the zoom type (pixel size, best fit...). Set again to
     * default value to compute again the position. For instance, the image cannot be center aligned until the view
     * has been repaint once (because the size is null).
     */
    if (currentZoom <= 0.0) {
      zoom(0.0);
    }
    if (panner != null) {
      panner.updateImageSize();
    }
    if (lens != null) {
      int w = getWidth();
      int h = getHeight();
      if (w != 0 && h != 0) {
        Rectangle bound = lens.getBounds();
        if (oldSize.width != 0 && oldSize.height != 0) {
          int centerX = bound.width / 2;
          int centerY = bound.height / 2;
          bound.x = (bound.x + centerX) * w / oldSize.width - centerX;
          bound.y = (bound.y + centerY) * h / oldSize.height - centerY;
          lens.setLocation(bound.x, bound.y);
        }
        oldSize.width = w;
        oldSize.height = h;
      }
      lens.updateZoom();
    }
  }

  @Override
  protected void initActionWState() {
    super.initActionWState();
    actionsInView.put(ActionW.ZOOM.cmd(), -1.0);
    actionsInView.put(ViewCanvas.ZOOM_TYPE_CMD, ZoomType.PIXEL_SIZE);
  }

  @Override
  public synchronized void enableMouseAndKeyListener(MouseActions actions) {
    super.enableMouseAndKeyListener(actions);
    if (lens != null) {
      lens.enableMouseListener();
    }
  }

  public MouseActionAdapter getMouseAdapter(String command) {
    if (command.equals(ActionW.CONTEXTMENU.cmd())) {
      return contextMenuHandler;
    } else if (command.equals(ActionW.WINLEVEL.cmd())) {
      return getAction(ActionW.LEVEL);
    }

    Optional<Feature<? extends ActionState>> actionKey = eventManager.getActionKey(command);
    if (actionKey.isEmpty()) {
      return null;
    }

    if (actionKey.get().isDrawingAction()) {
      return graphicMouseHandler;
    }
    Optional<? extends ActionState> actionState = eventManager.getAction(actionKey.get());
    if (actionState.isPresent() && actionState.get() instanceof MouseActionAdapter listener) {
      return listener;
    }
    return null;
  }

  public void resetMouseAdapter() {
    super.resetMouseAdapter();

    // reset context menu that is a field of this instance
    contextMenuHandler.setButtonMaskEx(0);
    graphicMouseHandler.setButtonMaskEx(0);
  }

  @Override
  public void handleLayerChanged(ImageLayer layer) {
    repaint();
  }

  @Override
  public void focusGained(FocusEvent e) {
    if (!e.isTemporary()) {
      ImageViewerPlugin<ImageElement> pane = eventManager.getSelectedView2dContainer();
      if (pane != null && pane.isContainingView(this)) {
        pane.setSelectedImagePaneFromFocus(this);
      }
    }
  }

  public boolean hasValidContent() {
    return getSourceImage() != null;
  }

  public JPopupMenu buildGraphicContextMenu(final MouseEvent evt, final List<Graphic> selected) {
    return GraphicEditActions.buildGraphicContextMenu(this, evt, selected, graphicMouseHandler);
  }

  public JPopupMenu buildContextMenu(final MouseEvent evt) {
    JPopupMenu popupMenu = buildLeftMouseActionMenu();
    int count = popupMenu.getComponentCount();

    GraphicEditActions.addPasteItems(popupMenu, this, evt);
    count = addSeparatorToPopupMenu(popupMenu, count);

    if (eventManager instanceof EventManager manager) {
      GuiUtils.addItemToMenu(popupMenu, manager.getLutMenu("weasis.contextmenu.lut"));
      GuiUtils.addItemToMenu(popupMenu, manager.getLutInverseMenu("weasis.contextmenu.invertLut"));
      GuiUtils.addItemToMenu(popupMenu, manager.getFilterMenu("weasis.contextmenu.filter"));
      count = addSeparatorToPopupMenu(popupMenu, count);

      GuiUtils.addItemToMenu(popupMenu, manager.getZoomMenu("weasis.contextmenu.zoom"));
      GuiUtils.addItemToMenu(
          popupMenu, manager.getOrientationMenu("weasis.contextmenu.orientation"));
      addSeparatorToPopupMenu(popupMenu, count);

      GuiUtils.addItemToMenu(popupMenu, manager.getResetMenu("weasis.contextmenu.reset"));
    }

    if (GuiUtils.getUICore()
        .getSystemPreferences()
        .getBooleanProperty("weasis.contextmenu.close", true)) {
      JMenuItem close = new JMenuItem(Messages.getString("View2d.close"));
      close.addActionListener(e -> View2d.this.setSeries(null, null));
      popupMenu.add(close);
    }
    return popupMenu;
  }

  private class SeriesHandler extends SequenceHandler {

    public SeriesHandler() {
      super(true, true);
    }

    @Override
    protected boolean importDataExt(TransferSupport support) {
      try {
        Series<?> seq = extractSeries(support.getTransferable());
        if (seq == null) {
          return false;
        }

        DataExplorerModel model = (DataExplorerModel) seq.getTagValue(TagW.ExplorerModel);
        if (!(seq.getMedia(0, null, null) instanceof ImageElement)
            || !(model instanceof TreeModel treeModel)) {
          ViewerPluginBuilder.openInDefaultViewer(
              seq,
              model == null ? ViewerPluginBuilder.DefaultDataModel : model,
              ViewerOpenOptions.defaults());
          return true;
        }

        ImageViewerPlugin<ImageElement> selPlugin = eventManager.getSelectedView2dContainer();
        MediaSeriesGroup parentGroup =
            treeModel.getParent(seq, model.getTreeModelNodeForNewPlugin());

        if (parentGroup != null && !isCurrentPluginMatch(selPlugin, parentGroup)) {
          View2dContainer existingPlugin = findPluginForGroup(parentGroup);
          if (existingPlugin != null) {
            existingPlugin.setSelectedAndGetFocus();
            @SuppressWarnings("unchecked")
            MediaSeries<ImageElement> imageSeries = (MediaSeries<ImageElement>) seq;
            existingPlugin.addSeries(imageSeries);
            return false;
          }
          if (getSeries() != null) {
            ViewerPluginBuilder.openInDefaultViewer(seq, model, ViewerOpenOptions.defaults());
            return true;
          }
        }

        return addSeriesToCurrentView(seq, selPlugin);
      } catch (Exception e) {
        LOGGER.error("Opening series", e);
        return false;
      }
    }

    private Series<?> extractSeries(Transferable transferable) throws Exception {
      Series<?> seq = (Series<?>) transferable.getTransferData(Series.sequenceDataFlavor);
      // Do not add series without medias. BUG WEA-100
      return seq.size(null) > 0 ? seq : null;
    }

    private boolean isCurrentPluginMatch(
        ImageViewerPlugin<ImageElement> selPlugin, MediaSeriesGroup parentGroup) {
      return selPlugin instanceof View2dContainer
          && selPlugin.isContainingView(View2d.this)
          && parentGroup.equals(selPlugin.getGroupID());
    }

    private View2dContainer findPluginForGroup(MediaSeriesGroup parentGroup) {
      List<ViewerPlugin<?>> viewerPlugins = GuiUtils.getUICore().getViewerPlugins();
      synchronized (viewerPlugins) {
        for (ViewerPlugin<?> p : viewerPlugins) {
          if (parentGroup.equals(p.getGroupID())) {
            if (p instanceof View2dContainer container
                && !container.isContainingView(View2d.this)) {
              return container;
            }
            return null;
          }
        }
      }
      return null;
    }

    @SuppressWarnings("unchecked")
    private boolean addSeriesToCurrentView(
        Series<?> seq, ImageViewerPlugin<ImageElement> selPlugin) {
      if (selPlugin != null && Boolean.TRUE.equals(selPlugin.isContainingView(View2d.this))) {
        // A drop on a tile assigns the series to its whole viewport
        selPlugin.setSeriesToView(View2d.this, (MediaSeries<ImageElement>) seq);
        // Getting the focus has a delay and so it will trigger the view selection later
        selPlugin.setSelectedImagePaneFromFocus(View2d.this);
      } else {
        setSeries((MediaSeries<ImageElement>) seq);
      }
      return true;
    }
  }
}
