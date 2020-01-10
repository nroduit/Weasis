/*******************************************************************************
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.weasis.dicom.explorer.print;

import org.weasis.core.ui.util.PrintOptions;
import org.weasis.dicom.explorer.print.DicomPrintDialog.FilmSize;

/**
 *
 * @author Marcelo Porto (marcelo@animati.com.br)
 * @author Nicolas Roduit
 * 
 * @since 09/01/2012
 */
public class DicomPrintOptions extends PrintOptions {
    public static final String DEF_MEDIUM_TYPE = "BLUE FILM"; //$NON-NLS-1$
    public static final String DEF_PRIORITY = "LOW"; //$NON-NLS-1$
    public static final String DEF_FILM_DEST = "MAGAZINE"; //$NON-NLS-1$
    public static final int DEF_NUM_COPIES = 1;
    public static final boolean DEF_COLOR = false;
    public static final String DEF_FILM_ORIENTATION = "PORTRAIT"; //$NON-NLS-1$
    public static final String DEF_IMG_DISP_FORMAT = "STANDARD\\1,1"; //$NON-NLS-1$
    public static final FilmSize DEF_FILM_SIZE = FilmSize.IN8X10;
    public static final String DEF_MAGNIFICATION_TYPE = "CUBIC"; //$NON-NLS-1$
    public static final String DEF_SMOOTHING_TYPE = "MEDIUM"; //$NON-NLS-1$
    public static final String DEF_BORDER_DENSITY = "WHITE"; //$NON-NLS-1$
    public static final String DEF_TRIM = "NO"; //$NON-NLS-1$
    public static final String DEF_EMPTY_DENSITY = "BLACK"; //$NON-NLS-1$
    public static final boolean DEF_SHOW_ANNOTATIONS = true;
    public static final boolean DEF_PRINT_SEL_VIEW = false;
    public static final PrintOptions.DotPerInches DEF_DPI = PrintOptions.DotPerInches.DPI_150;

    private String mediumType;
    private String priority;
    private String filmDestination;
    private int numOfCopies;
    private String filmOrientation;
    private FilmSize filmSizeId;
    private String imageDisplayFormat;
    private String magnificationType;
    private String smoothingType;
    private String borderDensity;
    private String trim;
    private String emptyDensity;
    private int minDensity;
    private int maxDensity;
    private boolean printOnlySelectedView;

    public DicomPrintOptions() {
        super();
        this.mediumType = DEF_MEDIUM_TYPE;
        this.priority = DEF_PRIORITY;
        this.filmDestination = DEF_FILM_DEST;
        this.numOfCopies = DEF_NUM_COPIES;
        setColorPrint(DEF_COLOR);
        this.filmOrientation = DEF_FILM_ORIENTATION;
        this.filmSizeId = DEF_FILM_SIZE;
        this.imageDisplayFormat = DEF_IMG_DISP_FORMAT;
        this.magnificationType = DEF_MAGNIFICATION_TYPE;
        this.smoothingType = DEF_SMOOTHING_TYPE;
        this.borderDensity = DEF_BORDER_DENSITY;
        this.trim = DEF_TRIM;
        this.emptyDensity = DEF_EMPTY_DENSITY;
        this.minDensity = 0;
        this.maxDensity = 255;
        setShowingAnnotations(DEF_SHOW_ANNOTATIONS);
        this.printOnlySelectedView = DEF_PRINT_SEL_VIEW;
        setDpi(DEF_DPI);
    }

    public String getBorderDensity() {
        return borderDensity;
    }

    public void setBorderDensity(String borderDensity) {
        this.borderDensity = borderDensity;
    }

    public String getEmptyDensity() {
        return emptyDensity;
    }

    public void setEmptyDensity(String emptyDensity) {
        this.emptyDensity = emptyDensity;
    }

    public String getFilmDestination() {
        return filmDestination;
    }

    public void setFilmDestination(String filmDestination) {
        this.filmDestination = filmDestination;
    }

    public String getFilmOrientation() {
        return filmOrientation;
    }

    public void setFilmOrientation(String filmOrientation) {
        this.filmOrientation = filmOrientation;
    }

    public FilmSize getFilmSizeId() {
        return filmSizeId;
    }

    public void setFilmSizeId(FilmSize filmSize) {
        this.filmSizeId = filmSize == null ? DEF_FILM_SIZE : filmSize;
    }

    public String getImageDisplayFormat() {
        return imageDisplayFormat;
    }

    public void setImageDisplayFormat(String imageDisplayFormat) {
        this.imageDisplayFormat = imageDisplayFormat;
    }

    public String getMagnificationType() {
        return magnificationType;
    }

    public void setMagnificationType(String magnificationType) {
        this.magnificationType = magnificationType;
    }

    public int getMaxDensity() {
        return maxDensity;
    }

    public void setMaxDensity(int maxDensity) {
        this.maxDensity = maxDensity;
    }

    public String getMediumType() {
        return mediumType;
    }

    public void setMediumType(String mediumType) {
        this.mediumType = mediumType;
    }

    public int getMinDensity() {
        return minDensity;
    }

    public void setMinDensity(int minDensity) {
        this.minDensity = minDensity;
    }

    public int getNumOfCopies() {
        return numOfCopies;
    }

    public void setNumOfCopies(int numOfCopies) {
        this.numOfCopies = numOfCopies;
    }

    public String getPriority() {
        return priority;
    }

    public void setPriority(String priority) {
        this.priority = priority;
    }

    public String getSmoothingType() {
        return smoothingType;
    }

    public void setSmoothingType(String smoothingType) {
        this.smoothingType = smoothingType;
    }

    public String getTrim() {
        return trim;
    }

    public void setTrim(String trim) {
        this.trim = trim;
    }

    public boolean isPrintOnlySelectedView() {
        return printOnlySelectedView;
    }

    public void setPrintOnlySelectedView(boolean printOnlySelectedView) {
        this.printOnlySelectedView = printOnlySelectedView;
    }

}
