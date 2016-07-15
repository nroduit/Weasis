package org.weasis.core.ui.model.utils.imp;

import org.weasis.core.ui.model.graphic.DragGraphic;
import org.weasis.core.ui.util.MouseEventDouble;

public class SelectedDragSequence extends DefaultDragSequence {
     public SelectedDragSequence(DragGraphic graphic) {
        super(graphic);
    }

    @Override
    public Boolean completeDrag(MouseEventDouble mouseEvent) {
        graphic.fireRemoveAndRepaintAction();
        return true;
    }
}
