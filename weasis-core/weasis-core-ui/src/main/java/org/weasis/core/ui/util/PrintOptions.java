/*******************************************************************************
 * Copyright (c) 2011 Weasis Team.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     Nicolas Roduit, Marcelo Porto  - initial API and implementation
 ******************************************************************************/
package org.weasis.core.ui.util;

/**
 * 
 * @author Marcelo Porto (marcelo@animati.com.br)
 * @version 18/11/2011
 */
public class PrintOptions {
    public enum SCALE {
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
        Custom {
            @Override
            public String toString() {
                return "Custom";
            }
        }
    }

    private Boolean hasAnnotations;
    private double imageScale;
    private boolean center;
    private SCALE scale;

    public PrintOptions(Boolean hasAnnotations, double imageScale) {
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

    public double getImageScale() {
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
