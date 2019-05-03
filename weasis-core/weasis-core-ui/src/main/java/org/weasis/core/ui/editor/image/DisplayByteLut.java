package org.weasis.core.ui.editor.image;

import org.weasis.core.api.image.op.ByteLut;

public class DisplayByteLut extends ByteLut {
    private boolean invert;
    
    public DisplayByteLut(ByteLut lut) {
        super(lut.getName(), lut.getLutTable());
    }
    
    
    public DisplayByteLut(String name, byte[][] lutTable) {
        super(name, lutTable);
    }
    
    public boolean isInvert() {
        return invert;
    }

    public void setInvert(boolean invert) {
        this.invert = invert;
    }
}
