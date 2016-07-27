package org.weasis.core.ui.model.utils.imp;

import org.weasis.core.ui.model.graphic.AbstractGraphicLabel;
import org.weasis.core.ui.model.graphic.GraphicLabel;

public class DefaultGraphicLabel extends AbstractGraphicLabel {
    public DefaultGraphicLabel() {
        super();
    }
    public DefaultGraphicLabel(DefaultGraphicLabel object) {
        super(object);
    }
    
    @Override
    public GraphicLabel copy() {
        return new DefaultGraphicLabel(this);
    }
    
    
}
