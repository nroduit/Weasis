/*
 * @copyright Copyright (c) 2009 Animati Sistemas de Informática Ltda. (http://www.animati.com.br)
 */
package org.weasis.core.api.image.util;

/**
 *
 * @author Marcelo Porto (marcelo@animati.com.br)
 * @version 18/11/2011
 */
public class PrintOptions {
    
    public static final Integer A4_PAPER = 1;
    
    public static final Integer CENTRALIZED = 1;
    public static final Integer TOP_LEFT = 2;
    
    public static final Integer PORTRAIT = 1;
    public static final Integer LANDSCAPE = 2;
    
    private Boolean hasAnnotations;
    private Integer fontSize;
    private Float imageScale;

    public PrintOptions(Boolean hasAnnotations, Integer fontSize, Float imageScale) {
        this.hasAnnotations = hasAnnotations;
        this.fontSize = fontSize;
        this.imageScale = imageScale;
    }

    public Integer getFontSize() {
        return fontSize;
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
}
