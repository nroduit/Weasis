/*******************************************************************************
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.weasis.core.ui.editor.image;

import java.awt.Cursor;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.awt.geom.AffineTransform;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.gui.util.ComboItemListener;
import org.weasis.core.api.gui.util.MouseActionAdapter;
import org.weasis.core.api.gui.util.ToggleButtonListener;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.ui.editor.image.DefaultView2d.BulkDragSequence;
import org.weasis.core.ui.model.AbstractGraphicModel;
import org.weasis.core.ui.model.GraphicModel;
import org.weasis.core.ui.model.graphic.DragGraphic;
import org.weasis.core.ui.model.graphic.Graphic;
import org.weasis.core.ui.model.graphic.imp.area.SelectGraphic;
import org.weasis.core.ui.model.utils.Draggable;
import org.weasis.core.ui.model.utils.imp.DefaultDragSequence;
import org.weasis.core.ui.util.MouseEventDouble;

public class GraphicMouseHandler<E extends ImageElement> extends MouseActionAdapter {
    private ViewCanvas<E> vImg;
    private Draggable ds;
    private CursorSet cursorSet;

    public GraphicMouseHandler(ViewCanvas<E> vImg, CursorSet cursors) {
        if (vImg == null) {
            throw new IllegalArgumentException();
        }
        this.vImg = vImg;
        this.cursorSet = cursors;
    }

    public GraphicMouseHandler(ViewCanvas<E> vImg) {
        this(vImg, new CursorSet(DefaultView2d.DEFAULT_CURSOR, DefaultView2d.MOVE_CURSOR, DefaultView2d.HAND_CURSOR,
            DefaultView2d.EDIT_CURSOR));
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

        GraphicModel graphicList = vImg.getGraphicManager();
        // Avoid any dragging on selection when Shift Button is Down
        if (!mouseEvt.isShiftDown()) {
            // Evaluates if mouse is on a dragging position, creates a DragSequence and changes cursor consequently
            Optional<Graphic> firstGraphicIntersecting = graphicList.getFirstGraphicIntersecting(mouseEvt);

            if (firstGraphicIntersecting.isPresent() && firstGraphicIntersecting.get() instanceof DragGraphic) {
                DragGraphic dragGraph = (DragGraphic) firstGraphicIntersecting.get();
                List<DragGraphic> selectedDragGraphList = graphicList.getSelectedDragableGraphics();
                boolean locked = dragGraph.getLayer().getLocked();

                if (!locked && selectedDragGraphList.contains(dragGraph)) {

                    if (selectedDragGraphList.size() > 1
                        && selectedDragGraphList.stream().allMatch(g -> !g.getLayer().getLocked())) {
                        ds = new BulkDragSequence(selectedDragGraphList, mouseEvt);
                        newCursor = cursorSet.getMoveCursor();

                    } else if (selectedDragGraphList.size() == 1) {

                        if (dragGraph.isOnGraphicLabel(mouseEvt)) {
                            ds = dragGraph.createDragLabelSequence();
                            newCursor = cursorSet.getHandCursor();

                        } else {
                            int handlePtIndex = dragGraph.getHandlePointIndex(mouseEvt);

                            if (handlePtIndex >= 0) {
                                dragGraph.moveMouseOverHandlePoint(handlePtIndex, mouseEvt);
                                ds = dragGraph.createResizeDrag(handlePtIndex);
                                newCursor = cursorSet.getEditCursor();

                            } else {
                                ds = dragGraph.createMoveDrag();
                                newCursor = cursorSet.getMoveCursor();
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
                    vImg.getGraphicManager().setSelectedGraphic(Arrays.asList(dragGraph));
                }
            }
        }

        if (ds == null) {
            ImageViewerEventManager<E> eventManager = vImg.getEventManager();
            Optional<ActionW> action = eventManager.getMouseAction(e.getModifiersEx());
            if (action.isPresent() && action.get().isDrawingAction()) {
                Optional<ComboItemListener> items =
                    eventManager.getAction(ActionW.DRAW_CMD_PREFIX + action.get().cmd(), ComboItemListener.class);
                if (items.isPresent()) {
                    Object item = items.get().getSelectedItem();
                    Graphic graph = AbstractGraphicModel.drawFromCurrentGraphic(vImg,
                        (Graphic) (item instanceof Graphic ? item : null));
                    if (graph instanceof DragGraphic) {
                        ds = ((DragGraphic) graph).createResizeDrag();
                        if (!(graph instanceof SelectGraphic)) {
                            vImg.getGraphicManager().setSelectedGraphic(Arrays.asList(graph));
                        }
                    }
                }
            }
        }
        vImg.getJComponent().setCursor(Optional.ofNullable(newCursor).orElse(cursorSet.getDrawingCursor()));

        if (ds != null) {
            ds.startDrag(mouseEvt);
        } else {
            vImg.getGraphicManager().setSelectedGraphic(null);
        }

        // Throws to the tool listener the current graphic selection.
        vImg.getGraphicManager().fireGraphicsSelectionChanged(vImg.getMeasurableLayer());
    }

    @Override
    public void mouseReleased(MouseEvent e) {
        int buttonMask = getButtonMask();

        // Check if extended modifier of mouse event equals the current buttonMask
        // Note that extended modifiers are not triggered in mouse released
        // Also asserts that Mouse adapter is not disable
        if ((e.getModifiers() & buttonMask) == 0) {
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

            // Little size rectangle in selection click is interpreted as a single clic
            boolean isSelectionSingleClic =
                selectionRect == null || (selectionRect.width < 5 && selectionRect.height < 5);

            final List<Graphic> newSelectedGraphList = new ArrayList<>();
            if (isSelectionSingleClic) {
                vImg.getGraphicManager().getFirstGraphicIntersecting(mouseEvt).ifPresent(newSelectedGraphList::add);
            } else {
                newSelectedGraphList
                    .addAll(vImg.getGraphicManager().getSelectedAllGraphicsIntersecting(selectionRect, transform));
            }

            // Add all graphics inside selection rectangle at any level in layers instead in the case of single
            // click where top level first graphic found is removed from list if already selected
            if (mouseEvt.isShiftDown()) {
                List<Graphic> selectedGraphList = vImg.getGraphicManager().getSelectedGraphics();

                if (!selectedGraphList.isEmpty()) {
                    if (newSelectedGraphList.isEmpty()) {
                        newSelectedGraphList.addAll(selectedGraphList);
                    } else {
                        selectedGraphList.forEach(g -> {
                            if (!newSelectedGraphList.contains(g)) {
                                newSelectedGraphList.add(g);
                            } else if (isSelectionSingleClic) {
                                newSelectedGraphList.remove(g);
                            }
                        });
                    }
                }
            }

            vImg.getGraphicManager().setSelectedGraphic(newSelectedGraphList);
        }

        if (ds.completeDrag(mouseEvt)) {
            vImg.getEventManager().getAction(ActionW.DRAW_ONLY_ONCE, ToggleButtonListener.class)
                .filter(ToggleButtonListener::isSelected).ifPresent(a -> {
                    vImg.getEventManager().getAction(ActionW.DRAW_MEASURE, ComboItemListener.class)
                        .ifPresent(c -> c.setSelectedItem(MeasureToolBar.selectionGraphic));
                    vImg.getEventManager().getAction(ActionW.DRAW_GRAPHICS, ComboItemListener.class)
                        .ifPresent(c -> c.setSelectedItem(MeasureToolBar.selectionGraphic));
                });
            ds = null;
        }

        // Throws to the tool listener the current graphic selection.
        vImg.getGraphicManager().fireGraphicsSelectionChanged(vImg.getMeasurableLayer());

        Cursor newCursor = cursorSet.getDrawingCursor();

        // Evaluates if mouse is on a dragging position, and changes cursor image consequently
        List<DragGraphic> selectedDragGraphList = vImg.getGraphicManager().getSelectedDragableGraphics();
        Optional<Graphic> firstGraphicIntersecting = vImg.getGraphicManager().getFirstGraphicIntersecting(mouseEvt);

        if (firstGraphicIntersecting.isPresent() && firstGraphicIntersecting.get() instanceof DragGraphic
            && !firstGraphicIntersecting.get().getLayer().getLocked()) {
            DragGraphic dragGraph = (DragGraphic) firstGraphicIntersecting.get();

            if (selectedDragGraphList.contains(dragGraph)) {

                if (selectedDragGraphList.size() > 1) {
                    newCursor = cursorSet.getMoveCursor();

                } else if (selectedDragGraphList.size() == 1) {
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
        }

        vImg.getJComponent().setCursor(Optional.ofNullable(newCursor).orElse(cursorSet.getDrawingCursor()));
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

        // Handle special case when drawing in mode [click > release > move/drag > release] instead of [click + drag >
        // release]
        if (ds instanceof DefaultDragSequence) {
            ds.drag(mouseEvt);
        } else {

            Cursor newCursor = cursorSet.getDrawingCursor();
            GraphicModel graphicList = vImg.getGraphicManager();

            if (!mouseEvt.isShiftDown()) {
                // Evaluates if mouse is on a dragging position, and changes cursor image consequently
                Optional<Graphic> firstGraphicIntersecting = graphicList.getFirstGraphicIntersecting(mouseEvt);

                if (firstGraphicIntersecting.isPresent() && firstGraphicIntersecting.get() instanceof DragGraphic
                    && !firstGraphicIntersecting.get().getLayer().getLocked()) {
                    DragGraphic dragGraph = (DragGraphic) firstGraphicIntersecting.get();
                    List<DragGraphic> selectedDragGraphList = vImg.getGraphicManager().getSelectedDragableGraphics();

                    if (selectedDragGraphList.contains(dragGraph)) {

                        if (selectedDragGraphList.size() > 1) {
                            newCursor = cursorSet.getMoveCursor();

                        } else if (selectedDragGraphList.size() == 1) {

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
                }
            }
            vImg.getJComponent().setCursor(Optional.ofNullable(newCursor).orElse(cursorSet.getDrawingCursor()));
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
