/*
 * @copyright Copyright (c) 2012 Animati Sistemas de Informática Ltda.
 * (http://www.animati.com.br)
 */
package br.com.animati.texture.mpr3dview.api;


import br.com.animati.texture.mpr3dview.EventPublisher;
import java.awt.Cursor;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.awt.geom.AffineTransform;
import java.beans.PropertyChangeEvent;
import java.util.ArrayList;
import java.util.List;
import org.weasis.core.api.gui.util.MouseActionAdapter;
import org.weasis.core.ui.graphic.AbstractDragGraphic;
import org.weasis.core.ui.graphic.DragSequence;
import org.weasis.core.ui.graphic.Graphic;
import org.weasis.core.ui.graphic.SelectGraphic;
import org.weasis.core.ui.graphic.model.AbstractLayerModel;
import org.weasis.core.ui.util.MouseEventDouble;

/**
 *
 *
 * @author Gabriela Bauermann (gabriela@animati.com.br)
 * @version 2013, 13 Oct.
 */
public class MeasureAdapter extends MouseActionAdapter {

    private ViewCore vImg;
    private DragSequence ds;

    public MeasureAdapter(ViewCore view) {
        vImg = view;
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

        // Convert mouse event point to real image coordinate point
        // (without geometric transformation)
        MouseEventDouble mouseEvt = new MouseEventDouble(e);
        mouseEvt.setImageCoordinates(vImg.getImageCoordinatesFromMouse(
                e.getX(), e.getY()));

        // Do nothing and return if current dragSequence is not completed
        if (ds != null && !ds.completeDrag(mouseEvt)) {
            return;
        }

        Cursor newCursor = AbstractLayerModel.DEFAULT_CURSOR;

        // Avoid any dragging on selection when Shift Button is Down
        if (!mouseEvt.isShiftDown()) {
            // Evaluates if mouse is on a dragging position, creates a DragSequence and changes cursor consequently
            Graphic firstGraphicIntersecting = vImg.getLayerModel().getFirstGraphicIntersecting(mouseEvt);

            if (firstGraphicIntersecting instanceof AbstractDragGraphic) {
                newCursor = dragStartAndGetCursor(
                        (AbstractDragGraphic) firstGraphicIntersecting,
                        mouseEvt);
            }
        }

        if (ds == null) {
            AbstractDragGraphic dragGraph = vImg.getLayerModel().createDragGraphic(mouseEvt);

            if (dragGraph != null) {
                ds = dragGraph.createResizeDrag();
                if (dragGraph instanceof SelectGraphic) {
                    vImg.getLayerModel().setSelectGraphic((SelectGraphic) dragGraph);
                } else {
                    vImg.getLayerModel().setSelectedGraphic(dragGraph);
                }
            }
        }

        vImg.getLayerModel().setCursor(newCursor);

        if (ds != null) {
            ds.startDrag(mouseEvt);
        } else {
            vImg.getLayerModel().setSelectedGraphics(null);
        }

    }
    
    private Cursor dragStartAndGetCursor(final AbstractDragGraphic dragGraph,
            final MouseEventDouble mouseEvt) {
        List<AbstractDragGraphic> selectedDragGraphList =
                vImg.getLayerModel().getSelectedDragableGraphics();
        Cursor newCursor = AbstractLayerModel.DEFAULT_CURSOR;
        if (selectedDragGraphList != null
                && selectedDragGraphList.contains(dragGraph)) {

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
            vImg.getLayerModel().setSelectedGraphic(dragGraph);
        }
        return newCursor;
    }
    
    private Cursor getCursor(final AbstractDragGraphic dragGraph,
            final MouseEventDouble mouseEvt, boolean select) {
        List<AbstractDragGraphic> selectedDragGraphList =
                vImg.getLayerModel().getSelectedDragableGraphics();
        Cursor newCursor = AbstractLayerModel.DEFAULT_CURSOR;
        if (selectedDragGraphList != null
                && selectedDragGraphList.contains(dragGraph)) {

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
            if (select) {
                vImg.getLayerModel().setSelectedGraphic(dragGraph);
            }
        }
        return newCursor;
    }

    @Override
    public void mouseReleased(MouseEvent e) {
        int buttonMask = getButtonMask();

        // Check if extended modifier of mouse event equals the current buttonMask
        // Note that extended modifiers are not triggered in mouse released
        // Also asserts that Mouse adapter is not disable
        if ((e.getModifiers() & buttonMask) == 0 || !vImg.hasContent()) {
            return;
        }

        // Do nothing and return if no dragSequence exist
        if (ds == null) {
            return;
        }

        // Convert mouse event point to real image coordinate point (without geometric transformation)
        MouseEventDouble mouseEvt = new MouseEventDouble(e);
        mouseEvt.setImageCoordinates(vImg.getImageCoordinatesFromMouse(e.getX(), e.getY()));

        SelectGraphic selectGraphic = vImg.getLayerModel().getSelectGraphic();

        if (selectGraphic != null) {

            AffineTransform transform = vImg.getAffineTransform();
            Rectangle selectionRect = selectGraphic.getBounds(transform);

            // Little size rectangle in selection click is interpreted as a single clic
            boolean isSelectionSingleClic =
                (selectionRect == null || (selectionRect.width < 5 && selectionRect.height < 5));

            List<Graphic> newSelectedGraphList = null;

            if (!isSelectionSingleClic) {
                newSelectedGraphList = vImg.getLayerModel().getSelectedAllGraphicsIntersecting(selectionRect, transform);
            } else {
                Graphic selectedGraph = vImg.getLayerModel().getFirstGraphicIntersecting(mouseEvt);
                if (selectedGraph != null) {
                    newSelectedGraphList = new ArrayList<Graphic>(1);
                    newSelectedGraphList.add(selectedGraph);
                }
            }

            // Add all graphics inside selection rectangle at any level in layers instead in the case of single
            // click where top level first graphic found is removed from list if already selected
            if (mouseEvt.isShiftDown()) {
                List<Graphic> selectedGraphList = new ArrayList<Graphic>(vImg.getLayerModel().getSelectedGraphics());

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

            vImg.getLayerModel().setSelectedGraphics(newSelectedGraphList);
            vImg.getLayerModel().setSelectGraphic(null);
        }

        if (ds.completeDrag(mouseEvt)) {
            EventPublisher.getInstance().publish(new PropertyChangeEvent(
                    vImg, "graphics.dragComplete", null, null));
            ds = null;
        }

        Cursor newCursor = AbstractLayerModel.DEFAULT_CURSOR;
        Graphic firstGraphicIntersecting = vImg.getLayerModel().getFirstGraphicIntersecting(mouseEvt);

        if (firstGraphicIntersecting instanceof AbstractDragGraphic) {
            newCursor = getCursor(
                    (AbstractDragGraphic) firstGraphicIntersecting,
                    mouseEvt, true);
        }
        vImg.getLayerModel().setCursor(newCursor);

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
        // Convert mouse event point to real image coordinate point
        // (without geometric transformation)
        MouseEventDouble mouseEvt = new MouseEventDouble(e);
        mouseEvt.setImageCoordinates(vImg.getImageCoordinatesFromMouse(
                e.getX(), e.getY()));

        if (ds != null) {
            ds.drag(mouseEvt);
        } else {
            Cursor newCursor = AbstractLayerModel.DEFAULT_CURSOR;
            if (!mouseEvt.isShiftDown()) {
                Graphic firstGraphicIntersecting =
                        vImg.getLayerModel().getFirstGraphicIntersecting(
                        mouseEvt);

                if (firstGraphicIntersecting instanceof AbstractDragGraphic) {
                    newCursor = getCursor(
                            (AbstractDragGraphic) firstGraphicIntersecting,
                            mouseEvt, false);
                }
            }
            vImg.getLayerModel().setCursor(newCursor);
        }
    }

     protected static class BulkDragSequence implements DragSequence {
        private final List<DragSequence> childDS;

        BulkDragSequence(List<AbstractDragGraphic> dragGraphList, MouseEventDouble mouseevent) {
            childDS = new ArrayList<DragSequence>(dragGraphList.size());

            for (AbstractDragGraphic dragGraph : dragGraphList) {
                DragSequence dragsequence = dragGraph.createMoveDrag();
                if (dragsequence != null) {
                    childDS.add(dragsequence);
                }
            }
        }

        @Override
        public void startDrag(MouseEventDouble mouseevent) {
            int i = 0;
            for (int j = childDS.size(); i < j; i++) {
                (childDS.get(i)).startDrag(mouseevent);
            }
        }

        @Override
        public void drag(MouseEventDouble mouseevent) {
            int i = 0;
            for (int j = childDS.size(); i < j; i++) {
                (childDS.get(i)).drag(mouseevent);
            }
        }

        @Override
        public boolean completeDrag(MouseEventDouble mouseevent) {
            int i = 0;
            for (int j = childDS.size(); i < j; i++) {
                (childDS.get(i)).completeDrag(mouseevent);
            }
            return true;
        }

    }

}
