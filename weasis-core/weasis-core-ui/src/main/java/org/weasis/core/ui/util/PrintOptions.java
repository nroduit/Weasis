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

import org.weasis.core.ui.Messages;

/**
 *
 * @author Marcelo Porto (marcelo@animati.com.br), Nicolas Roduit
 * @version 18/11/2011
 */
public class PrintOptions {

    public enum DotPerInches {
        DPI_150(150), DPI_300(300), DPI_600(600);

        private final int dpi;

        private DotPerInches(int dpi) {
            this.dpi = dpi;
        }

        public int getDpi() {
            return dpi;
        }

        @Override
        public String toString() {
            return String.valueOf(dpi);
        }
    }

    public enum Scale {
        SHRINK_TO_PAGE {
            @Override
            public String toString() {
                return Messages.getString("PrintOptions.shrink"); //$NON-NLS-1$
            }
        },
        FIT_TO_PAGE {
            @Override
            public String toString() {
                return Messages.getString("PrintOptions.fit"); //$NON-NLS-1$
            }
        },
        CUSTOM {
            @Override
            public String toString() {
                return Messages.getString("PrintOptions.custom"); //$NON-NLS-1$
            }
        }
    }

    private Boolean hasAnnotations;
    private double imageScale;
    private boolean center;
    private boolean color;
    private Scale scale;
    private DotPerInches dpi;

    public PrintOptions(Boolean hasAnnotations, double imageScale) {
        this.hasAnnotations = hasAnnotations;
        this.imageScale = imageScale;
        this.center = true;
        this.scale = Scale.SHRINK_TO_PAGE;
        this.color = true;
    }

    public Scale getScale() {
        return scale;
    }

    public void setScale(Scale scale) {
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

    public void setImageScale(double imageScale) {
        this.imageScale = imageScale;
    }

    public boolean isCenter() {
        return center;
    }

    public void setCenter(boolean center) {
        this.center = center;
    }

    public boolean isColor() {
        return color;
    }

    public void setColor(boolean color) {
        this.color = color;
    }

    public DotPerInches getDpi() {
        return dpi;
    }

    public void setDpi(DotPerInches dpi) {
        this.dpi = dpi;
    }
}
