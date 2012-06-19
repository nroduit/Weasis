/*******************************************************************************
 * Copyright (c) 2011 Weasis Team.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     Marcelo Porto - initial API and implementation, Animati Sistemas de Informática Ltda. (http://www.animati.com.br)
 *     
 ******************************************************************************/
package org.weasis.dicom.explorer.print;

/**
 * 
 * @author Marcelo Porto (marcelo@animati.com.br)
 * @version 09/01/2012
 */
public class DicomPrintOptions {

    private String mediumType;
    private String priority;
    private String filmDestination;
    private String imageDisplayFormat;
    private String filmSizeId;
    private String filmOrientation;
    private String magnificationType;
    private String smoothingType;
    private String borderDensity;
    private String trim;
    private Integer numOfCopies;
    private Integer minDensity;
    private Integer maxDensity;
    private Boolean printInColor;
    private DicomPrinter dicomPrinter;

    public Boolean getPrintInColor() {
        return printInColor;
    }

    public void setPrintInColor(Boolean printInColor) {
        this.printInColor = printInColor;
    }

    public String getBorderDensity() {
        return borderDensity;
    }

    public void setBorderDensity(String borderDensity) {
        this.borderDensity = borderDensity;
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

    public String getFilmSizeId() {
        return filmSizeId;
    }

    public void setFilmSizeId(String filmSizeId) {
        this.filmSizeId = filmSizeId;
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

    public Integer getMaxDensity() {
        return maxDensity;
    }

    public void setMaxDensity(Integer maxDensity) {
        this.maxDensity = maxDensity;
    }

    public String getMediumType() {
        return mediumType;
    }

    public void setMediumType(String mediumType) {
        this.mediumType = mediumType;
    }

    public Integer getMinDensity() {
        return minDensity;
    }

    public void setMinDensity(Integer minDensity) {
        this.minDensity = minDensity;
    }

    public Integer getNumOfCopies() {
        return numOfCopies;
    }

    public void setNumOfCopies(Integer numOfCopies) {
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

    public DicomPrinter getDicomPrinter() {
        return dicomPrinter;
    }

    public void setDicomPrinter(DicomPrinter dicomPrinter) {
        this.dicomPrinter = dicomPrinter;
    }

}
