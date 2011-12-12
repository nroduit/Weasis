/*
 * @copyright Copyright (c) 2009 Animati Sistemas de Inform�tica Ltda. (http://www.animati.com.br)
 */
package org.weasis.core.ui.util;

/**
 * 
 * @author Marcelo Porto (marcelo@animati.com.br)
 * @version 18/11/2011
 */
public class PrintOptions {
    public enum SCALE {
        None {
            @Override
            public String toString() {
                return "Original";
            }
        }, 
        ShrinkToPage {
            @Override
            public String toString() {
                return "Shrink to Page";
            }
        }, 
        FitToPage {
            @Override
            public String toString() {
                return "Fit to Page";
            }
        }, 
        Custom
    }

    private Boolean hasAnnotations;
    private Float imageScale;
    private boolean center;
    private SCALE scale;

    public PrintOptions(Boolean hasAnnotations, Float imageScale) {
        this.hasAnnotations = hasAnnotations;
        this.imageScale = imageScale;
        this.center = true;
        this.scale = SCALE.ShrinkToPage;
    }

    public SCALE getScale() {
        return scale;
    }

    public void setScale(SCALE scale) {
        this.scale = scale;
    }

    public Boolean getHasAnnotations() {
        return hasAnnotations;
    }

    public void setHasAnnotations(Boolean hasAnnotations) {
        this.hasAnnotations = hasAnnotations;
    }

    public Float getImageScale() {
        return imageScale;
    }

    public void setImageScale(Float imageScale) {
        this.imageScale = imageScale;
    }

    public boolean isCenter() {
        return center;
    }

    public void setCenter(boolean center) {
        this.center = center;
    }

}
