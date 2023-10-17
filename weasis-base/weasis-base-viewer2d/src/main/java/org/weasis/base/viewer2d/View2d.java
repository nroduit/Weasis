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
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import javax.swing.JDialog;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPopupMenu;
import javax.swing.JSeparator;
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
import org.weasis.core.api.media.data.MediaSeriesGroup;
import org.weasis.core.api.media.data.Series;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.ui.dialog.MeasureDialog;
import org.weasis.core.ui.editor.ViewerPluginBuilder;
import org.weasis.core.ui.editor.image.CalibrationView;
import org.weasis.core.ui.editor.image.ContextMenuHandler;
import org.weasis.core.ui.editor.image.DefaultView2d;
import org.weasis.core.ui.editor.image.ImageViewerEventManager;
import org.weasis.core.ui.editor.image.ImageViewerPlugin;
import org.weasis.core.ui.editor.image.MouseActions;
import org.weasis.core.ui.editor.image.SequenceHandler;
import org.weasis.core.ui.editor.image.SynchData;
import org.weasis.core.ui.editor.image.ViewCanvas;
import org.weasis.core.ui.editor.image.ViewerPlugin;
import org.weasis.core.ui.model.graphic.DragGraphic;
import org.weasis.core.ui.model.graphic.Graphic;
import org.weasis.core.ui.model.graphic.imp.line.LineGraphic;
import org.weasis.core.ui.util.ColorLayerUI;
import org.weasis.core.ui.util.MouseEventDouble;
import org.weasis.core.ui.util.TitleMenuItem;

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
    if (selected != null) {
      final JPopupMenu popupMenu = new JPopupMenu();
      TitleMenuItem itemTitle = new TitleMenuItem(Messages.getString("View2d.selection"));
      popupMenu.add(itemTitle);
      popupMenu.addSeparator();
      boolean graphicComplete = true;
      if (selected.size() == 1) {
        final Graphic graph = selected.get(0);
        if (graph instanceof final DragGraphic dragGraphic) {
          if (!dragGraphic.isGraphicComplete()) {
            graphicComplete = false;
          }
          if (dragGraphic.getVariablePointsNumber()) {
            if (graphicComplete) {
              /*
               * Convert mouse event point to real image coordinate point (without geometric
               * transformation)
               */
              final MouseEventDouble mouseEvt =
                  new MouseEventDouble(
                      View2d.this,
                      MouseEvent.MOUSE_RELEASED,
                      evt.getWhen(),
                      16,
                      0,
                      0,
                      0,
                      0,
                      1,
                      true,
                      1);
              mouseEvt.setSource(View2d.this);
              mouseEvt.setImageCoordinates(getImageCoordinatesFromMouse(evt.getX(), evt.getY()));
              final int ptIndex = dragGraphic.getHandlePointIndex(mouseEvt);
              if (ptIndex >= 0) {
                JMenuItem menuItem = new JMenuItem(Messages.getString("View2d.rem_point"));
                menuItem.addActionListener(e -> dragGraphic.removeHandlePoint(ptIndex, mouseEvt));
                popupMenu.add(menuItem);

                menuItem = new JMenuItem(Messages.getString("View2d.add_point"));
                menuItem.addActionListener(
                    e -> {
                      dragGraphic.forceToAddPoints(ptIndex);
                      MouseEventDouble evt2 =
                          new MouseEventDouble(
                              View2d.this,
                              MouseEvent.MOUSE_PRESSED,
                              evt.getWhen(),
                              16,
                              evt.getX(),
                              evt.getY(),
                              evt.getXOnScreen(),
                              evt.getYOnScreen(),
                              1,
                              true,
                              1);
                      graphicMouseHandler.mousePressed(evt2);
                    });
                popupMenu.add(menuItem);
                popupMenu.add(new JSeparator());
              }
            } else if (graphicMouseHandler.getDragSequence() != null
                && Objects.equals(dragGraphic.getPtsNumber(), Graphic.UNDEFINED)) {
              final JMenuItem item2 = new JMenuItem(Messages.getString("View2d.stop_draw"));
              item2.addActionListener(
                  e -> {
                    MouseEventDouble event =
                        new MouseEventDouble(View2d.this, 0, 0, 16, 0, 0, 0, 0, 2, true, 1);
                    graphicMouseHandler.getDragSequence().completeDrag(event);
                    graphicMouseHandler.mouseReleased(event);
                  });
              popupMenu.add(item2);
              popupMenu.add(new JSeparator());
            }
          }
        }
      }

      if (graphicComplete) {
        JMenuItem menuItem = new JMenuItem(Messages.getString("View2d.delete_selec"));
        menuItem.addActionListener(
            e -> View2d.this.getGraphicManager().deleteSelectedGraphics(View2d.this, true));
        popupMenu.add(menuItem);

        menuItem = new JMenuItem(Messages.getString("View2d.cut"));
        menuItem.addActionListener(
            e -> {
              DefaultView2d.GRAPHIC_CLIPBOARD.setGraphics(selected);
              View2d.this.getGraphicManager().deleteSelectedGraphics(View2d.this, false);
            });
        popupMenu.add(menuItem);
        menuItem = new JMenuItem(Messages.getString("View2d.copy"));
        menuItem.addActionListener(e -> DefaultView2d.GRAPHIC_CLIPBOARD.setGraphics(selected));
        popupMenu.add(menuItem);
        popupMenu.add(new JSeparator());
      }

      final ArrayList<DragGraphic> list = new ArrayList<>();
      for (Graphic graphic : selected) {
        if (graphic instanceof DragGraphic dragGraphic) {
          list.add(dragGraphic);
        }
      }

      if (selected.size() == 1) {
        final Graphic graph = selected.get(0);
        JMenuItem item = new JMenuItem(Messages.getString("View2d.front"));
        item.addActionListener(e -> graph.toFront());
        popupMenu.add(item);
        item = new JMenuItem(Messages.getString("View2d.back"));
        item.addActionListener(e -> graph.toBack());
        popupMenu.add(item);
        popupMenu.add(new JSeparator());

        if (graphicComplete && graph instanceof LineGraphic lineGraphic) {

          final JMenuItem calibMenu = new JMenuItem(Messages.getString("View2d.calib"));
          calibMenu.addActionListener(
              e -> {
                String title = Messages.getString("View2d.man_calib");
                CalibrationView calibrationDialog =
                    new CalibrationView(lineGraphic, View2d.this, false);
                ColorLayerUI layer = ColorLayerUI.createTransparentLayerUI(View2d.this);
                int res =
                    JOptionPane.showConfirmDialog(
                        ColorLayerUI.getContentPane(layer),
                        calibrationDialog,
                        title,
                        JOptionPane.OK_CANCEL_OPTION);
                if (layer != null) {
                  layer.hideUI();
                }
                if (res == JOptionPane.OK_OPTION) {
                  calibrationDialog.applyNewCalibration();
                }
              });
          popupMenu.add(calibMenu);
          popupMenu.add(new JSeparator());
        }
      }

      if (!list.isEmpty()) {
        JMenuItem properties = new JMenuItem(Messages.getString("View2d.prop"));
        properties.addActionListener(
            e -> {
              ColorLayerUI layer = ColorLayerUI.createTransparentLayerUI(View2d.this);
              JDialog dialog = new MeasureDialog(View2d.this, list);
              ColorLayerUI.showCenterScreen(dialog, layer);
            });
        popupMenu.add(properties);
      }
      return popupMenu;
    }
    return null;
  }

  public JPopupMenu buildContextMenu(final MouseEvent evt) {
    JPopupMenu popupMenu = buildLeftMouseActionMenu();
    int count = popupMenu.getComponentCount();

    if (DefaultView2d.GRAPHIC_CLIPBOARD.hasGraphics()) {
      JMenuItem menuItem = new JMenuItem(Messages.getString("View2d.paste_draw"));
      menuItem.addActionListener(e -> copyGraphicsFromClipboard());
      popupMenu.add(menuItem);
    }
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
      Transferable transferable = support.getTransferable();

      ImageViewerPlugin<ImageElement> selPlugin = eventManager.getSelectedView2dContainer();
      Series seq;
      try {
        seq = (Series) transferable.getTransferData(Series.sequenceDataFlavor);
        // Do not add series without medias. BUG WEA-100
        if (seq.size(null) == 0) {
          return false;
        }
        DataExplorerModel model = (DataExplorerModel) seq.getTagValue(TagW.ExplorerModel);
        if (seq.getMedia(0, null, null) instanceof ImageElement
            && model instanceof TreeModel treeModel) {

          MediaSeriesGroup p1 = treeModel.getParent(seq, model.getTreeModelNodeForNewPlugin());
          ViewerPlugin openPlugin = null;
          if (p1 != null) {
            if (selPlugin instanceof View2dContainer
                && selPlugin.isContainingView(View2d.this)
                && p1.equals(selPlugin.getGroupID())) {
            } else {
              List<ViewerPlugin<?>> viewerPlugins = GuiUtils.getUICore().getViewerPlugins();
              synchronized (viewerPlugins) {
                for (final ViewerPlugin<?> p : viewerPlugins) {
                  if (p1.equals(p.getGroupID())) {
                    if (!((View2dContainer) p).isContainingView(View2d.this)) {
                      openPlugin = p;
                    }
                    break;
                  }
                }
              }
              if (openPlugin == null) {
                if (View2d.this.getSeries() != null) {
                  ViewerPluginBuilder.openSequenceInDefaultPlugin(seq, model, true, true);
                  return true;
                }
              } else {
                openPlugin.setSelectedAndGetFocus();
                openPlugin.addSeries(seq);
                // openPlugin.setSelected(true);
                return false;
              }
            }
          }
        } else {
          ViewerPluginBuilder.openSequenceInDefaultPlugin(
              seq, model == null ? ViewerPluginBuilder.DefaultDataModel : model, true, true);
          return true;
        }
      } catch (Exception e) {
        LOGGER.error("Opening series", e);
        return false;
      }

      if (selPlugin != null
          && SynchData.Mode.TILE.equals(selPlugin.getSynchView().getSynchData().getMode())) {
        selPlugin.addSeries(seq);
        return true;
      }

      setSeries(seq);
      // Getting the focus has a delay and so it will trigger the view selection later
      // requestFocusInWindow();
      if (selPlugin != null && selPlugin.isContainingView(View2d.this)) {
        selPlugin.setSelectedImagePaneFromFocus(View2d.this);
      }
      return true;
    }
  }
}
