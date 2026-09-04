/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.editor.image;

import java.awt.event.MouseEvent;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import javax.swing.JDialog;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JSeparator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.Messages;
import org.weasis.core.ui.dialog.MeasureDialog;
import org.weasis.core.ui.model.graphic.DragGraphic;
import org.weasis.core.ui.model.graphic.Graphic;
import org.weasis.core.ui.model.graphic.MidpointHandles;
import org.weasis.core.ui.model.graphic.imp.PathConversion;
import org.weasis.core.ui.model.graphic.imp.area.PolygonGraphic;
import org.weasis.core.ui.model.graphic.imp.line.LineGraphic;
import org.weasis.core.ui.model.graphic.imp.line.PolylineGraphic;
import org.weasis.core.ui.model.utils.bean.GraphicClipboard;
import org.weasis.core.ui.model.utils.bean.GraphicClipboard.PasteMode;
import org.weasis.core.ui.model.utils.exceptions.InvalidShapeException;
import org.weasis.core.ui.util.ColorLayerUI;
import org.weasis.core.ui.util.MouseEventDouble;
import org.weasis.core.ui.util.TitleMenuItem;

/**
 * The actions that edit a graphic once it exists, and the context menu that offers them. Every view
 * uses this one builder: a view that has more to offer adds its own items to the menu it gets back,
 * it does not rebuild it.
 */
public final class GraphicEditActions {

  private static final Logger LOGGER = LoggerFactory.getLogger(GraphicEditActions.class);

  private GraphicEditActions() {}

  /**
   * The menu of a right-click on a selection of graphics.
   *
   * @param view the view the click happened on
   * @param evt the original mouse event, for the position the actions work at
   * @param selected the selected graphics, possibly empty
   * @param handler the handler owning the drawing sequence, to stop or cancel a drawing
   */
  public static JPopupMenu buildGraphicContextMenu(
      ViewCanvas<?> view, MouseEvent evt, List<Graphic> selected, GraphicMouseHandler<?> handler) {
    if (selected == null) {
      return null;
    }
    Objects.requireNonNull(view);
    JPopupMenu popupMenu = new JPopupMenu();
    popupMenu.add(new TitleMenuItem(Messages.getString("GraphicEditActions.selection")));
    popupMenu.addSeparator();

    MouseEventDouble mouseEvt = imageEvent(view, evt);
    boolean graphicComplete = true;

    if (selected.size() == 1 && selected.getFirst() instanceof DragGraphic dragGraphic) {
      graphicComplete = Boolean.TRUE.equals(dragGraphic.isGraphicComplete());
      if (Boolean.TRUE.equals(dragGraphic.getVariablePointsNumber())) {
        if (graphicComplete) {
          addVertexItems(popupMenu, view, dragGraphic, mouseEvt, handler);
        } else {
          addDrawingItems(popupMenu, view, dragGraphic, handler);
        }
      }
    }

    if (graphicComplete) {
      addClipboardItems(popupMenu, view, selected, mouseEvt);
    }

    List<DragGraphic> dragGraphics = new ArrayList<>();
    for (Graphic graphic : selected) {
      if (graphic instanceof DragGraphic dragGraphic) {
        dragGraphics.add(dragGraphic);
      }
    }

    if (selected.size() == 1) {
      addSingleGraphicItems(popupMenu, view, selected.getFirst(), graphicComplete);
    }

    if (!dragGraphics.isEmpty()) {
      JMenuItem properties = new JMenuItem(Messages.getString("GraphicEditActions.properties"));
      properties.addActionListener(_ -> showProperties(view, dragGraphics));
      popupMenu.add(properties);
    }
    return popupMenu;
  }

  /** Adds the paste items to the menu of a right-click on the image, outside any graphic. */
  public static void addPasteItems(JPopupMenu popupMenu, ViewCanvas<?> view, MouseEvent evt) {
    GraphicClipboard clipboard = DefaultView2d.GRAPHIC_CLIPBOARD;
    if (!clipboard.hasContent()) {
      return;
    }
    Point2D cursor = imagePoint(view, evt);
    JMenuItem paste = new JMenuItem(Messages.getString("GraphicEditActions.paste"));
    paste.addActionListener(_ -> clipboard.paste(view, PasteMode.CURSOR, cursor));
    popupMenu.add(paste);

    JMenuItem inPlace = new JMenuItem(Messages.getString("GraphicEditActions.paste_in_place"));
    inPlace.addActionListener(_ -> clipboard.paste(view, PasteMode.IN_PLACE, null));
    popupMenu.add(inPlace);
  }

  // -- Actions, also reachable from the shortcuts --

  public static void copy(ViewCanvas<?> view) {
    DefaultView2d.GRAPHIC_CLIPBOARD.copy(view.getGraphicManager().getSelectedGraphics());
  }

  public static void cut(ViewCanvas<?> view) {
    List<Graphic> selected = view.getGraphicManager().getSelectedGraphics();
    if (selected.stream().anyMatch(GraphicClipboard::isEditable)) {
      DefaultView2d.GRAPHIC_CLIPBOARD.cut(selected, view);
    }
  }

  public static void paste(ViewCanvas<?> view, PasteMode mode, Point2D cursor) {
    DefaultView2d.GRAPHIC_CLIPBOARD.paste(view, mode, cursor);
  }

  public static void duplicate(ViewCanvas<?> view) {
    List<Graphic> selected = view.getGraphicManager().getSelectedGraphics();
    if (!selected.isEmpty()) {
      DefaultView2d.GRAPHIC_CLIPBOARD.duplicate(selected, view);
    }
  }

  // -- Menu sections --

  /** Vertex editing of a complete variable-point path, where the cursor is. */
  private static void addVertexItems(
      JPopupMenu popupMenu,
      ViewCanvas<?> view,
      DragGraphic graphic,
      MouseEventDouble mouseEvt,
      GraphicMouseHandler<?> handler) {
    int count = popupMenu.getComponentCount();
    int ptIndex = graphic.getHandlePointIndex(mouseEvt);

    if (ptIndex >= 0) {
      JMenuItem remove = new JMenuItem(Messages.getString("GraphicEditActions.remove_point"));
      remove.setEnabled(graphic.canRemoveHandlePoint(ptIndex));
      remove.addActionListener(
          _ -> {
            graphic.removeHandlePoint(ptIndex, mouseEvt);
            view.getJComponent().repaint();
          });
      popupMenu.add(remove);

      if (handler != null && GraphicMouseHandler.isOpenEnd(graphic, ptIndex)) {
        JMenuItem resume = new JMenuItem(Messages.getString("GraphicEditActions.continue_drawing"));
        resume.addActionListener(_ -> handler.continueDrawing(graphic, ptIndex));
        popupMenu.add(resume);
      }
    } else {
      int segment = MidpointHandles.segmentAt(graphic, mouseEvt);
      if (segment >= 0) {
        JMenuItem insert = new JMenuItem(Messages.getString("GraphicEditActions.insert_point"));
        insert.addActionListener(
            _ -> {
              Point2D point =
                  MidpointHandles.pointOnSegment(graphic, segment, mouseEvt.getImageCoordinates());
              if (graphic.insertHandlePoint(segment, point) >= 0) {
                graphic.buildShape(mouseEvt);
                view.getJComponent().repaint();
              }
            });
        popupMenu.add(insert);
      }
    }
    addSeparator(popupMenu, count);
  }

  /** Ending or cancelling a drawing in progress. */
  private static void addDrawingItems(
      JPopupMenu popupMenu,
      ViewCanvas<?> view,
      DragGraphic graphic,
      GraphicMouseHandler<?> handler) {
    if (handler == null
        || handler.getDragSequence() == null
        || !Objects.equals(graphic.getPtsNumber(), Graphic.UNDEFINED)) {
      return;
    }
    JMenuItem stop = new JMenuItem(Messages.getString("GraphicEditActions.stop_drawing"));
    stop.addActionListener(
        _ -> {
          MouseEventDouble event =
              new MouseEventDouble(view.getJComponent(), 0, 0, 16, 0, 0, 0, 0, 2, true, 1);
          handler.getDragSequence().completeDrag(event);
          handler.mouseReleased(event);
        });
    popupMenu.add(stop);

    JMenuItem cancel = new JMenuItem(Messages.getString("ShortcutManager.cancel_drawing"));
    cancel.addActionListener(_ -> handler.cancelDrawing());
    popupMenu.add(cancel);
    popupMenu.add(new JSeparator());
  }

  private static void addClipboardItems(
      JPopupMenu popupMenu, ViewCanvas<?> view, List<Graphic> selected, MouseEventDouble mouseEvt) {
    boolean editable = selected.stream().anyMatch(GraphicClipboard::isEditable);

    JMenuItem delete = new JMenuItem(Messages.getString("GraphicEditActions.delete"));
    delete.setEnabled(editable);
    delete.addActionListener(_ -> view.getGraphicManager().deleteSelectedGraphics(view, true));
    popupMenu.add(delete);

    JMenuItem cut = new JMenuItem(Messages.getString("GraphicEditActions.cut"));
    cut.setEnabled(editable);
    cut.addActionListener(_ -> DefaultView2d.GRAPHIC_CLIPBOARD.cut(selected, view));
    popupMenu.add(cut);

    JMenuItem copy = new JMenuItem(Messages.getString("GraphicEditActions.copy"));
    copy.addActionListener(_ -> DefaultView2d.GRAPHIC_CLIPBOARD.copy(selected));
    popupMenu.add(copy);

    JMenuItem duplicate = new JMenuItem(Messages.getString("GraphicEditActions.duplicate"));
    duplicate.addActionListener(_ -> DefaultView2d.GRAPHIC_CLIPBOARD.duplicate(selected, view));
    popupMenu.add(duplicate);

    if (DefaultView2d.GRAPHIC_CLIPBOARD.hasContent()) {
      JMenuItem paste = new JMenuItem(Messages.getString("GraphicEditActions.paste"));
      Point2D cursor = mouseEvt == null ? null : mouseEvt.getImageCoordinates();
      paste.addActionListener(
          _ -> DefaultView2d.GRAPHIC_CLIPBOARD.paste(view, PasteMode.CURSOR, cursor));
      popupMenu.add(paste);
    }
    popupMenu.add(new JSeparator());
  }

  private static void addSingleGraphicItems(
      JPopupMenu popupMenu, ViewCanvas<?> view, Graphic graphic, boolean graphicComplete) {
    JMenuItem front = new JMenuItem(Messages.getString("GraphicEditActions.to_front"));
    front.addActionListener(_ -> graphic.toFront());
    popupMenu.add(front);

    JMenuItem back = new JMenuItem(Messages.getString("GraphicEditActions.to_back"));
    back.addActionListener(_ -> graphic.toBack());
    popupMenu.add(back);
    popupMenu.add(new JSeparator());

    if (!graphicComplete) {
      return;
    }

    int count = popupMenu.getComponentCount();
    if (graphic instanceof PolygonGraphic polygon) {
      JMenuItem openPath = new JMenuItem(Messages.getString("GraphicEditActions.open_path"));
      openPath.addActionListener(_ -> convert(view, polygon, () -> PathConversion.open(polygon)));
      popupMenu.add(openPath);
    } else if (graphic instanceof PolylineGraphic polyline) {
      JMenuItem closePath = new JMenuItem(Messages.getString("GraphicEditActions.close_path"));
      closePath.addActionListener(
          _ -> convert(view, polyline, () -> PathConversion.close(polyline)));
      popupMenu.add(closePath);
    }

    if (graphic instanceof DragGraphic dragGraphic
        && Boolean.TRUE.equals(dragGraphic.getVariablePointsNumber())
        && dragGraphic.getPts().size() > SimplifyDialog.MIN_POINTS) {
      JMenuItem simplify = new JMenuItem(Messages.getString("GraphicEditActions.simplify"));
      simplify.addActionListener(_ -> SimplifyDialog.show(view, dragGraphic));
      popupMenu.add(simplify);
    }
    addSeparator(popupMenu, count);

    if (graphic instanceof LineGraphic lineGraphic && CalibrationView.accepts(lineGraphic)) {
      JMenuItem calibrate = new JMenuItem(Messages.getString("GraphicEditActions.calibrate"));
      calibrate.addActionListener(_ -> CalibrationView.showDialog(lineGraphic, view));
      popupMenu.add(calibrate);
      popupMenu.add(new JSeparator());
    }
  }

  // -- Helpers --

  private interface Conversion {
    Graphic apply() throws InvalidShapeException;
  }

  private static void convert(ViewCanvas<?> view, Graphic graphic, Conversion conversion) {
    try {
      PathConversion.replace(view, graphic, conversion.apply());
    } catch (InvalidShapeException e) {
      LOGGER.warn("Cannot convert the path", e);
    }
  }

  private static void showProperties(ViewCanvas<?> view, List<DragGraphic> graphics) {
    ColorLayerUI layer = ColorLayerUI.createTransparentLayerUI(view.getJComponent());
    JDialog dialog = new MeasureDialog(view, new ArrayList<>(graphics));
    ColorLayerUI.showCenterScreen(dialog, layer);
  }

  /** A synthetic release event carrying the image coordinates of the click. */
  private static MouseEventDouble imageEvent(ViewCanvas<?> view, MouseEvent evt) {
    if (evt == null) {
      return null;
    }
    MouseEventDouble mouseEvt =
        new MouseEventDouble(
            view.getJComponent(),
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
    mouseEvt.setSource(view.getJComponent());
    mouseEvt.setImageCoordinates(view.getImageCoordinatesFromMouse(evt.getX(), evt.getY()));
    return mouseEvt;
  }

  private static Point2D imagePoint(ViewCanvas<?> view, MouseEvent evt) {
    return evt == null ? null : view.getImageCoordinatesFromMouse(evt.getX(), evt.getY());
  }

  /** Adds a separator only when the section really added something. */
  private static void addSeparator(JPopupMenu popupMenu, int countBefore) {
    if (popupMenu.getComponentCount() > countBefore) {
      popupMenu.add(new JSeparator());
    }
  }
}
