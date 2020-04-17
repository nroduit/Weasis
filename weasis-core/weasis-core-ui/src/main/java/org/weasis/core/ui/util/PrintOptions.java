/*******************************************************************************
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.weasis.core.ui.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.util.StringUtil;

/**
 *
 * @author Marcelo Porto (marcelo@animati.com.br)
 * @author Nicolas Roduit
 * 
 * @since 18/11/2011
 */
public class PrintOptions {
    private static final Logger LOGGER = LoggerFactory.getLogger(PrintOptions.class);

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

        public static DotPerInches getInstance(String val, DotPerInches defaultValue) {
            if (StringUtil.hasText(val)) {
                try {
                    return DotPerInches.valueOf(val);
                } catch (Exception e) {
                    LOGGER.error("Cannot find DotPerInches: {}", val, e); //$NON-NLS-1$
                }
            }
            return defaultValue;
        }
    }

    private boolean showingAnnotations;
    private boolean center;
    private boolean colorPrint;
    private DotPerInches dpi;

    public PrintOptions() {
        this.showingAnnotations = true;
        this.center = true;
        this.colorPrint = true;
        this.dpi = DotPerInches.DPI_150;
    }

    public boolean isShowingAnnotations() {
        return showingAnnotations;
    }

    public void setShowingAnnotations(boolean showingAnnotations) {
        this.showingAnnotations = showingAnnotations;
    }

    public boolean isCenter() {
        return center;
    }

    public void setCenter(boolean center) {
        this.center = center;
    }

    public boolean isColorPrint() {
        return colorPrint;
    }

    public void setColorPrint(boolean colorPrint) {
        this.colorPrint = colorPrint;
    }

    public DotPerInches getDpi() {
        return dpi;
    }

    public void setDpi(DotPerInches dpi) {
        this.dpi = dpi == null ? DotPerInches.DPI_150 : dpi;
    }
}
