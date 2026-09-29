/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.editor.image;

import java.awt.Cursor;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.event.MouseEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import javax.swing.SwingUtilities;
import org.weasis.core.api.gui.util.ActionState;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.gui.util.ComboItemListener;
import org.weasis.core.api.gui.util.Feature;
import org.weasis.core.api.gui.util.MouseActionAdapter;
import org.weasis.core.api.gui.util.ToggleButtonListener;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.ui.editor.image.DefaultView2d.BulkDragSequence;
import org.weasis.core.ui.model.AbstractGraphicModel;
import org.weasis.core.ui.model.GraphicModel;
import org.weasis.core.ui.model.graphic.DragGraphic;
import org.weasis.core.ui.model.graphic.Graphic;
import org.weasis.core.ui.model.graphic.MidpointHandles;
import org.weasis.core.ui.model.graphic.imp.BuiltinGraphicTools;
import org.weasis.core.ui.model.graphic.imp.area.SelectGraphic;
import org.weasis.core.ui.model.graphic.imp.line.LineGraphic;
import org.weasis.core.ui.model.utils.Draggable;
import org.weasis.core.ui.model.utils.imp.DefaultDragSequence;
import org.weasis.core.ui.util.MouseEventDouble;

public class GraphicMouseHandler<E extends ImageElement> extends MouseActionAdapter {
  private final ViewCanvas<E> vImg;
  private Draggable ds;

  /** The graphic being created by the current drag sequence, if it is a new one. */
  private Graphic created;

  /** The graphic the current drag sequence draws: the new one, or an open path being continued. */
  private Graphic drawing;

  private final CursorSet cursorSet;

  public GraphicMouseHandler(ViewCanvas<E> vImg) {
    this(
        vImg,
        new CursorSet(
            DefaultView2d.DEFAULT_CURSOR,
            DefaultView2d.MOVE_CURSOR,
            DefaultView2d.HAND_CURSOR,
            DefaultView2d.EDIT_CURSOR));
  }

  public GraphicMouseHandler(ViewCanvas<E> vImg, CursorSet cursors) {
    if (vImg == null) {
      throw new IllegalArgumentException();
    }
    this.vImg = vImg;
    this.cursorSet = cursors;
  }

  public Draggable getDragSequence() {
    return ds;
  }

  @Override
  public void mousePressed(MouseEvent e) {
    int buttonMask = getButtonMaskEx();

    // Check if extended modifier of mouse event equals the current buttonMask
    // Also asserts that Mouse adapter is not disable
    if (e.isConsumed() || (e.getModifiersEx() & buttonMask) == 0) {
      return;
    }

    // Convert mouse event point to real image coordinate point (without geometric transformation)
    MouseEventDouble mouseEvt = new MouseEventDouble(e);
    mouseEvt.setImageCoordinates(vImg.getImageCoordinatesFromMouse(e.getX(), e.getY()));

    // Do nothing and return if current dragSequence is not completed
    if (ds != null) {
      Boolean c = ds.completeDrag(mouseEvt);
      if (mouseEvt.isConsumed()) {
        c = Boolean.FALSE;
        ds = null;
      }
      if (!c) {
        return;
      }
    }

    Cursor newCursor = cursorSet.getDrawingCursor();
    if (ds == null) {
      created = null;
    }
    // An Alt + click on a vertex is not a click in the void: the graphic stays selected, whether
    // or not the removal was allowed
    boolean vertexGesture = false;

    GraphicModel graphicList = vImg.getGraphicManager();
    // Avoid any dragging on selection when Shift Button is Down
    if (!mouseEvt.isShiftDown()) {
      // Evaluates if mouse is on a dragging position, creates a DragSequence and changes cursor
      // consequently
      Optional<Graphic> firstGraphicIntersecting =
          graphicList.getFirstGraphicIntersecting(mouseEvt);

      if (firstGraphicIntersecting.isPresent()
          && firstGraphicIntersecting.get() instanceof DragGraphic dragGraph) {
        List<DragGraphic> selectedDragGraphList = graphicList.getSelectedDraggableGraphics();
        boolean locked = dragGraph.getLayer().getLocked();

        if (!locked && selectedDragGraphList.contains(dragGraph)) {

          if (selectedDragGraphList.size() > 1
              && selectedDragGraphList.stream().noneMatch(g -> g.getLayer().getLocked())) {
            ds = new BulkDragSequence(selectedDragGraphList, mouseEvt);
            newCursor = cursorSet.getMoveCursor();

          } else if (selectedDragGraphList.size() == 1) {

            if (dragGraph.isOnGraphicLabel(mouseEvt)) {
              ds = dragGraph.createDragLabelSequence();
              newCursor = cursorSet.getHandCursor();

            } else {
              int handlePtIndex = dragGraph.getHandlePointIndex(mouseEvt);
              boolean variable = Boolean.TRUE.equals(dragGraph.getVariablePointsNumber());

              if (handlePtIndex >= 0 && variable && mouseEvt.isAltDown()) {
                if (isOpenEnd(dragGraph, handlePtIndex)) {
                  // Which of the two gestures it is can only be told on release
                  ds = new EndHandleSequence(dragGraph, handlePtIndex);
                } else {
                  removeVertex(dragGraph, handlePtIndex, mouseEvt);
                }
                vertexGesture = true;
                newCursor = cursorSet.getEditCursor();

              } else if (handlePtIndex >= 0) {
                ds = dragGraph.createResizeDrag(handlePtIndex);
                newCursor = cursorSet.getEditCursor();

              } else {
                int segmentIndex = MidpointHandles.hitTest(dragGraph, mouseEvt);
                int inserted =
                    segmentIndex < 0
                        ? Graphic.UNDEFINED
                        : dragGraph.insertHandlePoint(
                            segmentIndex, dragGraph.getSegmentMidPoint(segmentIndex));

                if (inserted >= 0) {
                  dragGraph.buildShape(mouseEvt);
                  ds = dragGraph.createResizeDrag(inserted);
                  newCursor = cursorSet.getEditCursor();

                } else {
                  ds = dragGraph.createMoveDrag();
                  newCursor = cursorSet.getMoveCursor();
                }
              }
            }
          }
        } else {
          if (!locked && dragGraph.isOnGraphicLabel(mouseEvt)) {
            ds = dragGraph.createDragLabelSequence();
            newCursor = cursorSet.getHandCursor();

          } else if (!locked) {
            ds = dragGraph.createMoveDrag();
            newCursor = cursorSet.getMoveCursor();
          }
          vImg.getGraphicManager().setSelectedGraphic(Collections.singletonList(dragGraph));
        }
      }
    }

    if (ds == null) {
      ImageViewerEventManager<E> eventManager = vImg.getEventManager();
      Optional<Feature<? extends ActionState>> action =
          eventManager.getMouseAction(e.getModifiersEx());
      if (action.isPresent() && action.get().isDrawingAction()) {
        Optional<ComboItemListener> items =
            eventManager.getActionFromActionKey(
                ActionW.DRAW_CMD_PREFIX + action.get().cmd(), ComboItemListener.class);
        if (items.isPresent()) {
          Object item = items.get().getSelectedItem();
          Graphic graph =
              AbstractGraphicModel.drawFromCurrentGraphic(
                  vImg, (Graphic) (item instanceof Graphic ? item : null));
          if (graph instanceof DragGraphic dragGraphic) {
            created = graph;
            drawing = graph;
            ds = dragGraphic.createResizeDrag();
            if (!(graph instanceof SelectGraphic)) {
              vImg.getGraphicManager().setSelectedGraphic(Collections.singletonList(graph));
            }
          }
        }
      }
    }
    vImg.getJComponent()
        .setCursor(Optional.ofNullable(newCursor).orElse(cursorSet.getDrawingCursor()));

    if (ds != null) {
      ds.startDrag(mouseEvt);
    } else if (!vertexGesture) {
      vImg.getGraphicManager().setSelectedGraphic(null);
    }

    // Throws to the tool listener the current graphic selection.
    vImg.getGraphicManager().fireGraphicsSelectionChanged(vImg.getMeasurableLayer());
  }

  /** True for the first or the last vertex of an open path, where a path can be continued. */
  public static boolean isOpenEnd(DragGraphic graphic, int index) {
    int size = graphic.getPts().size();
    return !graphic.isClosedPath() && size >= 2 && (index == 0 || index == size - 1);
  }

  /** Removes a vertex, when the path may lose one. */
  private void removeVertex(DragGraphic graphic, int index, MouseEventDouble mouseEvt) {
    if (graphic.canRemoveHandlePoint(index)) {
      graphic.removeHandlePoint(index, mouseEvt);
      vImg.getJComponent().repaint();
    }
  }

  /** Reopens an open path from one of its two ends, reversing it when it is the first one. */
  private static boolean reopenFromEnd(DragGraphic graphic, int index) {
    if (!isOpenEnd(graphic, index)) {
      return false;
    }
    if (index == 0) {
      Collections.reverse(graphic.getPts());
    }
    return graphic.resumeDrawing();
  }

  /**
   * Continues an open path from one of its ends, driven by the mouse: the vertex that follows the
   * cursor is the one the drag started on.
   */
  private Draggable resumeByDrag(DragGraphic graphic, int index, MouseEventDouble mouseEvt) {
    if (!reopenFromEnd(graphic, index)) {
      return null;
    }
    created = null;
    drawing = graphic;
    Draggable sequence = graphic.createResizeDrag();
    sequence.startDrag(mouseEvt);
    return sequence;
  }

  /**
   * Continues an open path from one of its ends, from the context menu. There is no drag to carry
   * the next vertex, so one is appended on the end itself: it follows the cursor as soon as the
   * mouse moves over the view, and the next click places it, exactly as while drawing. Without it
   * the graphic would merely be marked incomplete and the next click would start a new one.
   *
   * @return true when the view is now drawing that path
   */
  public boolean continueDrawing(DragGraphic graphic, int index) {
    if (ds != null || !reopenFromEnd(graphic, index)) {
      return false;
    }
    created = null;
    drawing = graphic;
    List<Point2D> pts = graphic.getPts();
    Point2D end = pts.getLast();
    pts.add(new Point2D.Double(end.getX(), end.getY()));
    ds = graphic.createResizeDrag(pts.size() - 1);
    vImg.getGraphicManager().setSelectedGraphic(Collections.singletonList(graphic));
    graphic.buildShape(null);
    vImg.getJComponent().repaint();
    return true;
  }

  /**
   * Alt on one of the two ends of an open path, where the two gestures of the vocabulary meet: a
   * click removes that vertex, a drag continues the path from it. Which one it is cannot be known
   * when the button goes down, so the decision is deferred to the first real move.
   */
  private final class EndHandleSequence implements Draggable {

    /** Screen pixels the cursor must travel before the gesture counts as a drag. */
    private static final double DRAG_THRESHOLD = 3.0;

    private final DragGraphic graphic;
    private final int index;
    private final Point pressPoint = new Point();
    private Draggable resumed;

    private EndHandleSequence(DragGraphic graphic, int index) {
      this.graphic = graphic;
      this.index = index;
    }

    @Override
    public void startDrag(MouseEventDouble mouseEvent) {
      pressPoint.setLocation(mouseEvent.getX(), mouseEvent.getY());
    }

    @Override
    public void drag(MouseEventDouble mouseEvent) {
      if (resumed == null) {
        if (pressPoint.distance(mouseEvent.getX(), mouseEvent.getY()) < DRAG_THRESHOLD) {
          return;
        }
        resumed = resumeByDrag(graphic, index, mouseEvent);
        if (resumed == null) {
          return;
        }
        // From here the handler drives the draw sequence directly, as for any other drawing
        ds = resumed;
      }
      resumed.drag(mouseEvent);
    }

    @Override
    public Boolean completeDrag(MouseEventDouble mouseEvent) {
      if (resumed != null) {
        return resumed.completeDrag(mouseEvent);
      }
      removeVertex(graphic, index, mouseEvent);
      // The sequence ends here, but nothing was drawn: FALSE keeps the release from completing a
      // graphic, which would otherwise give the draw tool back to the selection
      ds = null;
      return Boolean.FALSE;
    }
  }

  /**
   * Cancels the drawing in progress and leaves the tool selected. A draft started from scratch is
   * removed; a path that was only being continued goes back to the shape it had, because the user
   * asked to stop drawing, not to lose the graphic. Bound to Escape, which the draw sequence takes
   * before it reaches the display reset.
   */
  public boolean cancelDrawing() {
    if (ds == null || drawing == null) {
      return false;
    }
    Graphic graphic = drawing;
    boolean isNew = graphic == created;
    ds = null;
    created = null;
    drawing = null;

    if (isNew) {
      // The draft is incomplete, so only the draft removal takes it out of the model
      graphic.fireRemoveDraftAction();
      vImg.getGraphicManager().setSelectedGraphic(null);
    } else if (graphic instanceof DragGraphic dragGraphic) {
      closeResumedPath(dragGraphic);
    }
    vImg.getJComponent().setCursor(cursorSet.getDrawingCursor());
    vImg.getJComponent().repaint();
    return true;
  }

  /** Drops the vertex that follows the cursor and makes the path complete again. */
  private static void closeResumedPath(DragGraphic graphic) {
    List<Point2D> pts = graphic.getPts();
    if (pts.size() > graphic.getMinPoints()) {
      pts.removeLast();
    }
    graphic.setPointNumber(pts.size());
    // The drag sequence never completed, so the flag that hides the handles is still set
    graphic.setResizeOrMoving(Boolean.FALSE);
    graphic.buildShape(null);
  }

  /**
   * Drops the last placed vertex of the drawing in progress; on the first one it cancels the whole
   * draft. Bound to Backspace.
   */
  public boolean removeLastVertex() {
    if (ds == null || !(drawing instanceof DragGraphic graphic)) {
      return false;
    }
    List<Point2D> pts = graphic.getPts();
    // The last point of a running sequence is the one following the cursor, not a placed vertex
    if (pts.size() <= 2) {
      return cancelDrawing();
    }
    pts.remove(pts.size() - 2);
    if (ds instanceof DefaultDragSequence sequence) {
      sequence.syncToLastPoint();
    }
    graphic.buildShape(null);
    vImg.getJComponent().repaint();
    return true;
  }

  /** True while a graphic is being drawn, so the key listeners know Escape belongs here. */
  public boolean isDrawing() {
    return ds != null && drawing != null && !Boolean.TRUE.equals(drawing.isGraphicComplete());
  }

  @Override
  public void mouseReleased(MouseEvent e) {
    // The extended modifiers no longer hold the released button, so the button itself is tested.
    // Also asserts that the mouse adapter is not disabled.
    if (!isBoundButton(e)) {
      return;
    }

    // Do nothing and return if no dragSequence exist
    if (ds == null) {
      return;
    }

    // Convert mouse event point to real image coordinate point (without geometric transformation)
    MouseEventDouble mouseEvt = new MouseEventDouble(e);
    mouseEvt.setImageCoordinates(vImg.getImageCoordinatesFromMouse(e.getX(), e.getY()));

    Optional<SelectGraphic> selection = vImg.getGraphicManager().getSelectGraphic();
    if (selection.isPresent()) {
      Graphic selectGraphic = selection.get();
      AffineTransform transform = DefaultView2d.getAffineTransform(mouseEvt);
      Rectangle selectionRect = selectGraphic.getBounds(transform);

      // Little size rectangle in selection click is interpreted as a single click
      boolean isSelectionSingleClick =
          selectionRect == null || (selectionRect.width < 5 && selectionRect.height < 5);

      final List<Graphic> newSelectedGraphList = new ArrayList<>();
      if (isSelectionSingleClick) {
        vImg.getGraphicManager()
            .getFirstGraphicIntersecting(mouseEvt)
            .ifPresent(newSelectedGraphList::add);
      } else {
        newSelectedGraphList.addAll(
            vImg.getGraphicManager().getSelectedAllGraphicsIntersecting(selectionRect, transform));
      }

      // Add all graphics inside selection rectangle at any level in layers instead in the case of
      // single
      // click where top level first graphic found is removed from list if already selected
      if (mouseEvt.isShiftDown()) {
        List<Graphic> selectedGraphList = vImg.getGraphicManager().getSelectedGraphics();

        if (!selectedGraphList.isEmpty()) {
          if (newSelectedGraphList.isEmpty()) {
            newSelectedGraphList.addAll(selectedGraphList);
          } else {
            selectedGraphList.forEach(
                g -> {
                  if (!newSelectedGraphList.contains(g)) {
                    newSelectedGraphList.add(g);
                  } else if (isSelectionSingleClick) {
                    newSelectedGraphList.remove(g);
                  }
                });
          }
        }
      }

      vImg.getGraphicManager().setSelectedGraphic(newSelectedGraphList);
    }

    if (ds.completeDrag(mouseEvt)) {
      calibrateIfRequested(e);
      vImg.getEventManager()
          .getAction(ActionW.DRAW_ONLY_ONCE)
          .filter(ToggleButtonListener::isSelected)
          .ifPresent(
              a -> {
                vImg.getEventManager()
                    .getAction(ActionW.DRAW_MEASURE)
                    .ifPresent(c -> c.setSelectedItem(BuiltinGraphicTools.SELECTION));
                vImg.getEventManager()
                    .getAction(ActionW.DRAW_GRAPHICS)
                    .ifPresent(c -> c.setSelectedItem(BuiltinGraphicTools.SELECTION));
              });
      ds = null;
    }

    // Throws to the tool listener the current graphic selection.
    vImg.getGraphicManager().fireGraphicsSelectionChanged(vImg.getMeasurableLayer());

    Cursor newCursor = cursorSet.getDrawingCursor();

    // Evaluates if mouse is on a dragging position, and changes cursor image consequently
    List<DragGraphic> selectedDragGraphList =
        vImg.getGraphicManager().getSelectedDraggableGraphics();
    Optional<Graphic> firstGraphicIntersecting =
        vImg.getGraphicManager().getFirstGraphicIntersecting(mouseEvt);

    if (firstGraphicIntersecting.isPresent()
        && firstGraphicIntersecting.get() instanceof DragGraphic dragGraph
        && !firstGraphicIntersecting.get().getLayer().getLocked()) {
      newCursor = getCursor(mouseEvt, selectedDragGraphList, dragGraph, cursorSet);
    }

    vImg.getJComponent()
        .setCursor(Optional.ofNullable(newCursor).orElse(cursorSet.getDrawingCursor()));
  }

  /**
   * A measurement line drawn with the menu shortcut key held (Ctrl, Cmd on macOS) is a line over an
   * object of known length: its calibration dialog opens as soon as it is drawn.
   */
  private void calibrateIfRequested(MouseEvent e) {
    Graphic graphic = created;
    created = null;
    drawing = null;
    int shortcutMask = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
    if ((e.getModifiersEx() & shortcutMask) != 0 && CalibrationView.accepts(graphic)) {
      SwingUtilities.invokeLater(() -> CalibrationView.showDialog((LineGraphic) graphic, vImg));
    }
  }

  private static Cursor getCursor(
      MouseEventDouble mouseEvt,
      List<DragGraphic> selectedDragGraphList,
      DragGraphic dragGraph,
      CursorSet cursorSet) {
    Cursor newCursor;
    if (selectedDragGraphList.contains(dragGraph)) {

      if (selectedDragGraphList.size() > 1) {
        newCursor = cursorSet.getMoveCursor();

      } else {
        if (dragGraph.isOnGraphicLabel(mouseEvt)) {
          newCursor = cursorSet.getHandCursor();

        } else {
          if (dragGraph.getHandlePointIndex(mouseEvt) >= 0) {
            newCursor = cursorSet.getEditCursor();
          } else {
            newCursor = cursorSet.getMoveCursor();
          }
        }
      }
    } else {
      if (dragGraph.isOnGraphicLabel(mouseEvt)) {
        newCursor = cursorSet.getHandCursor();
      } else {
        newCursor = cursorSet.getMoveCursor();
      }
    }
    return newCursor;
  }

  @Override
  public void mouseDragged(MouseEvent e) {
    int buttonMask = getButtonMaskEx();

    // Check if extended modifier of mouse event equals the current buttonMask
    // Also asserts that Mouse adapter is not disable
    if (e.isConsumed() || (e.getModifiersEx() & buttonMask) == 0) {
      return;
    }

    if (ds != null) {
      // Convert mouse event point to real image coordinate point (without geometric transformation)
      MouseEventDouble mouseEvt = new MouseEventDouble(e);
      mouseEvt.setImageCoordinates(vImg.getImageCoordinatesFromMouse(e.getX(), e.getY()));

      ds.drag(mouseEvt);
    }
  }

  @Override
  public void mouseMoved(MouseEvent e) {
    if (e.isConsumed()) {
      return;
    }

    // Convert mouse event point to real image coordinate point (without geometric transformation)
    MouseEventDouble mouseEvt = new MouseEventDouble(e);
    mouseEvt.setImageCoordinates(vImg.getImageCoordinatesFromMouse(e.getX(), e.getY()));

    // Handle special case when drawing in mode [click > release > move/drag > release] instead of
    // [click + drag > release]
    if (ds instanceof DefaultDragSequence) {
      ds.drag(mouseEvt);
    } else {
      Cursor newCursor = null;
      GraphicModel graphicList = vImg.getGraphicManager();
      if (!mouseEvt.isShiftDown()) {
        // Evaluates if mouse is on a dragging position, and changes cursor image consequently
        Optional<Graphic> firstGraphicIntersecting =
            graphicList.getFirstGraphicIntersecting(mouseEvt);

        if (firstGraphicIntersecting.isPresent()
            && firstGraphicIntersecting.get() instanceof DragGraphic dragGraph
            && !firstGraphicIntersecting.get().getLayer().getLocked()) {
          List<DragGraphic> selectedDragGraphList =
              vImg.getGraphicManager().getSelectedDraggableGraphics();

          newCursor = getCursor(mouseEvt, selectedDragGraphList, dragGraph, cursorSet);
        }
      }
      if (newCursor != null) {
        vImg.getJComponent().setCursor(newCursor);
      }
    }
  }

  public static class CursorSet {
    private final Cursor drawingCursor;
    private final Cursor moveCursor;
    private final Cursor handCursor;
    private final Cursor editCursor;

    public CursorSet(Cursor drawing, Cursor move, Cursor hand, Cursor edit) {
      this.drawingCursor = Optional.ofNullable(drawing).orElse(DefaultView2d.DEFAULT_CURSOR);
      this.moveCursor = Optional.ofNullable(move).orElse(DefaultView2d.MOVE_CURSOR);
      this.handCursor = Optional.ofNullable(hand).orElse(DefaultView2d.HAND_CURSOR);
      this.editCursor = Optional.ofNullable(edit).orElse(DefaultView2d.EDIT_CURSOR);
    }

    public Cursor getDrawingCursor() {
      return drawingCursor;
    }

    public Cursor getMoveCursor() {
      return moveCursor;
    }

    public Cursor getHandCursor() {
      return handCursor;
    }

    public Cursor getEditCursor() {
      return editCursor;
    }
  }
}
