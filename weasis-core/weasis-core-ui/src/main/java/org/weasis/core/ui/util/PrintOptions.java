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
 * @author Marcelo Porto (marcelo@animati.com.br), Nicolas Roduit
 * @version 18/11/2011
 */
public class PrintOptions {

    public enum DotPerInches {
        DPI_100(100), DPI_150(150), DPI_200(200), DPI_250(250), DPI_300(300);

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

    private Boolean hasAnnotations;
    private boolean center;
    private boolean color;
    private DotPerInches dpi;

    public PrintOptions(Boolean hasAnnotations) {
        this.hasAnnotations = hasAnnotations;
        this.center = true;
        this.color = true;
    }

    public Boolean getHasAnnotations() {
        return hasAnnotations;
    }

    public void setHasAnnotations(Boolean hasAnnotations) {
        this.hasAnnotations = hasAnnotations;
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
