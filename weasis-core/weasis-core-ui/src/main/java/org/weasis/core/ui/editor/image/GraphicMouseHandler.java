package org.weasis.core.ui.editor.image;

import java.awt.Cursor;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.awt.geom.AffineTransform;
import java.util.ArrayList;
import java.util.List;

import org.weasis.core.api.gui.util.ActionState;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.gui.util.ComboItemListener;
import org.weasis.core.api.gui.util.MouseActionAdapter;
import org.weasis.core.api.gui.util.ToggleButtonListener;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.ui.editor.image.DefaultView2d.BulkDragSequence;
import org.weasis.core.ui.graphic.AbstractDragGraphic;
import org.weasis.core.ui.graphic.DragSequence;
import org.weasis.core.ui.graphic.Graphic;
import org.weasis.core.ui.graphic.SelectGraphic;
import org.weasis.core.ui.graphic.model.AbstractLayerModel;
import org.weasis.core.ui.util.MouseEventDouble;

public class GraphicMouseHandler<E extends ImageElement> extends MouseActionAdapter {
    private ViewCanvas<E> vImg;
    private DragSequence ds;

    public GraphicMouseHandler(ViewCanvas<E> vImg) {
        if (vImg == null) {
            throw new IllegalArgumentException();
        }
        this.vImg = vImg;
    }

    public DragSequence getDragSequence() {
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
        if (ds != null && !ds.completeDrag(mouseEvt)) {
            return;
        }

        Cursor newCursor = AbstractLayerModel.DEFAULT_CURSOR;
        AbstractLayerModel layerModel = vImg.getLayerModel();

        // Avoid any dragging on selection when Shift Button is Down
        if (!mouseEvt.isShiftDown()) {
            // Evaluates if mouse is on a dragging position, creates a DragSequence and changes cursor consequently
            Graphic firstGraphicIntersecting = layerModel.getFirstGraphicIntersecting(mouseEvt);

            if (firstGraphicIntersecting instanceof AbstractDragGraphic) {
                AbstractDragGraphic dragGraph = (AbstractDragGraphic) firstGraphicIntersecting;
                List<AbstractDragGraphic> selectedDragGraphList = layerModel.getSelectedDragableGraphics();

                if (selectedDragGraphList != null && selectedDragGraphList.contains(dragGraph)) {

                    if ((selectedDragGraphList.size() > 1)) {
                        ds = new BulkDragSequence(selectedDragGraphList, mouseEvt);
                        newCursor = AbstractLayerModel.MOVE_CURSOR;

                    } else if (selectedDragGraphList.size() == 1) {

                        if (dragGraph.isOnGraphicLabel(mouseEvt)) {
                            ds = dragGraph.createDragLabelSequence();
                            newCursor = AbstractLayerModel.HAND_CURSOR;

                        } else {
                            int handlePtIndex = dragGraph.getHandlePointIndex(mouseEvt);

                            if (handlePtIndex >= 0) {
                                dragGraph.moveMouseOverHandlePoint(handlePtIndex, mouseEvt);
                                ds = dragGraph.createResizeDrag(handlePtIndex);
                                newCursor = AbstractLayerModel.EDIT_CURSOR;

                            } else {
                                ds = dragGraph.createMoveDrag();
                                newCursor = AbstractLayerModel.MOVE_CURSOR;
                            }
                        }
                    }
                } else {
                    if (dragGraph.isOnGraphicLabel(mouseEvt)) {
                        ds = dragGraph.createDragLabelSequence();
                        newCursor = AbstractLayerModel.HAND_CURSOR;

                    } else {
                        ds = dragGraph.createMoveDrag();
                        newCursor = AbstractLayerModel.MOVE_CURSOR;
                    }
                    layerModel.setSelectedGraphic(dragGraph);
                }
            }
        }

        if (ds == null) {
            AbstractDragGraphic dragGraph = layerModel.createDragGraphic(mouseEvt);

            if (dragGraph != null) {
                ds = dragGraph.createResizeDrag();
                if (dragGraph instanceof SelectGraphic) {
                    layerModel.setSelectGraphic((SelectGraphic) dragGraph);
                } else {
                    layerModel.setSelectedGraphic(dragGraph);
                }
            }
        }

        layerModel.setCursor(newCursor);

        if (ds != null) {
            ds.startDrag(mouseEvt);
        } else {
            layerModel.setSelectedGraphics(null);
        }

        // Throws to the tool listener the current graphic selection.
        layerModel.fireGraphicsSelectionChanged(vImg.getImageLayer());

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

        AbstractLayerModel layerModel = vImg.getLayerModel();
        SelectGraphic selectGraphic = layerModel.getSelectGraphic();

        if (selectGraphic != null) {

            AffineTransform transform = DefaultView2d.getAffineTransform(mouseEvt);
            Rectangle selectionRect = selectGraphic.getBounds(transform);

            // Little size rectangle in selection click is interpreted as a single clic
            boolean isSelectionSingleClic =
                (selectionRect == null || (selectionRect.width < 5 && selectionRect.height < 5));

            List<Graphic> newSelectedGraphList = null;

            if (!isSelectionSingleClic) {
                newSelectedGraphList = layerModel.getSelectedAllGraphicsIntersecting(selectionRect, transform);
            } else {
                Graphic selectedGraph = layerModel.getFirstGraphicIntersecting(mouseEvt);
                if (selectedGraph != null) {
                    newSelectedGraphList = new ArrayList<Graphic>(1);
                    newSelectedGraphList.add(selectedGraph);
                }
            }

            // Add all graphics inside selection rectangle at any level in layers instead in the case of single
            // click where top level first graphic found is removed from list if already selected
            if (mouseEvt.isShiftDown()) {
                List<Graphic> selectedGraphList = new ArrayList<Graphic>(layerModel.getSelectedGraphics());

                if (selectedGraphList != null && selectedGraphList.size() > 0) {
                    if (newSelectedGraphList == null) {
                        newSelectedGraphList = new ArrayList<Graphic>(selectedGraphList);
                    } else {
                        for (Graphic graphic : selectedGraphList) {
                            if (!newSelectedGraphList.contains(graphic)) {
                                newSelectedGraphList.add(graphic);
                            } else if (isSelectionSingleClic) {
                                newSelectedGraphList.remove(graphic);
                            }
                        }
                    }
                }
            }

            layerModel.setSelectedGraphics(newSelectedGraphList);
            layerModel.setSelectGraphic(null);
        }

        if (ds.completeDrag(mouseEvt)) {
            ActionState drawOnceAction = vImg.getEventManager().getAction(ActionW.DRAW_ONLY_ONCE);
            if (drawOnceAction instanceof ToggleButtonListener) {
                if (((ToggleButtonListener) drawOnceAction).isSelected()) {
                    ActionState measure = vImg.getEventManager().getAction(ActionW.DRAW_MEASURE);
                    if (measure instanceof ComboItemListener) {
                        ((ComboItemListener) measure).setSelectedItem(MeasureToolBar.selectionGraphic);
                    }
                }
            }
            ds = null;
        }

        // Throws to the tool listener the current graphic selection.
        layerModel.fireGraphicsSelectionChanged(vImg.getImageLayer());

        Cursor newCursor = AbstractLayerModel.DEFAULT_CURSOR;

        // TODO below is the same code as this is in mouseMoved, can be a function instead
        // Evaluates if mouse is on a dragging position, and changes cursor image consequently
        List<AbstractDragGraphic> selectedDragGraphList = layerModel.getSelectedDragableGraphics();
        Graphic firstGraphicIntersecting = layerModel.getFirstGraphicIntersecting(mouseEvt);

        if (firstGraphicIntersecting instanceof AbstractDragGraphic) {
            AbstractDragGraphic dragGraph = (AbstractDragGraphic) firstGraphicIntersecting;

            if (selectedDragGraphList != null && selectedDragGraphList.contains(dragGraph)) {

                if ((selectedDragGraphList.size() > 1)) {
                    newCursor = AbstractLayerModel.MOVE_CURSOR;

                } else if (selectedDragGraphList.size() == 1) {

                    if (dragGraph.isOnGraphicLabel(mouseEvt)) {
                        newCursor = AbstractLayerModel.HAND_CURSOR;

                    } else {
                        if (dragGraph.getHandlePointIndex(mouseEvt) >= 0) {
                            newCursor = AbstractLayerModel.EDIT_CURSOR;
                        } else {
                            newCursor = AbstractLayerModel.MOVE_CURSOR;
                        }
                    }
                }
            } else {
                if (dragGraph.isOnGraphicLabel(mouseEvt)) {
                    newCursor = AbstractLayerModel.HAND_CURSOR;
                } else {
                    newCursor = AbstractLayerModel.MOVE_CURSOR;
                }
            }
        }

        layerModel.setCursor(newCursor);

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

        // Convert mouse event point to real image coordinate point (without geometric transformation)
        MouseEventDouble mouseEvt = new MouseEventDouble(e);
        mouseEvt.setImageCoordinates(vImg.getImageCoordinatesFromMouse(e.getX(), e.getY()));

        if (ds != null) {
            ds.drag(mouseEvt);
        } else {

            Cursor newCursor = AbstractLayerModel.DEFAULT_CURSOR;
            AbstractLayerModel layerModel = vImg.getLayerModel();

            if (!mouseEvt.isShiftDown()) {
                // Evaluates if mouse is on a dragging position, and changes cursor image consequently
                Graphic firstGraphicIntersecting = layerModel.getFirstGraphicIntersecting(mouseEvt);

                if (firstGraphicIntersecting instanceof AbstractDragGraphic) {
                    AbstractDragGraphic dragGraph = (AbstractDragGraphic) firstGraphicIntersecting;
                    List<AbstractDragGraphic> selectedDragGraphList = layerModel.getSelectedDragableGraphics();

                    if (selectedDragGraphList != null && selectedDragGraphList.contains(dragGraph)) {

                        if ((selectedDragGraphList.size() > 1)) {
                            newCursor = AbstractLayerModel.MOVE_CURSOR;

                        } else if (selectedDragGraphList.size() == 1) {

                            if (dragGraph.isOnGraphicLabel(mouseEvt)) {
                                newCursor = AbstractLayerModel.HAND_CURSOR;

                            } else {
                                if (dragGraph.getHandlePointIndex(mouseEvt) >= 0) {
                                    newCursor = AbstractLayerModel.EDIT_CURSOR;
                                } else {
                                    newCursor = AbstractLayerModel.MOVE_CURSOR;
                                }
                            }
                        }
                    } else {
                        if (dragGraph.isOnGraphicLabel(mouseEvt)) {
                            newCursor = AbstractLayerModel.HAND_CURSOR;
                        } else {
                            newCursor = AbstractLayerModel.MOVE_CURSOR;
                        }
                    }
                }
            }
            layerModel.setCursor(newCursor);
        }
    }
}
