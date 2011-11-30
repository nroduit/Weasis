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
        None, ShrinkToPage, FitToPage, Custom
    }

    public static final Integer A4_PAPER = 1;

    public static final Integer PORTRAIT = 1;
    public static final Integer LANDSCAPE = 2;

    private Boolean hasAnnotations;
    private Integer fontSize;
    private Float imageScale;
    private boolean center;
    private SCALE scale;

    public PrintOptions(Boolean hasAnnotations, Integer fontSize, Float imageScale) {
        this.hasAnnotations = hasAnnotations;
        this.fontSize = fontSize;
        this.imageScale = imageScale;
        this.center = true;
        this.scale = SCALE.ShrinkToPage;
    }

    public Integer getFontSize() {
        return fontSize;
    }

    public SCALE getScale() {
        return scale;
    }

    public void setScale(SCALE scale) {
        this.scale = scale;
    }

    public void setFontSize(Integer fontSize) {
        this.fontSize = fontSize;
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
