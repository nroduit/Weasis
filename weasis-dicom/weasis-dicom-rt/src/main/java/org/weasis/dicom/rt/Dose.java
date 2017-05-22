/*******************************************************************************
 * Copyright (c) 2017 Weasis Team.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Nicolas Roduit - initial API and implementation
 *     Tomas Skripcak  - initial API and implementation
 ******************************************************************************/

package org.weasis.dicom.rt;

import org.weasis.core.api.media.data.MediaElement;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class Dose extends HashMap<Integer, Dvh> {
    private static final long serialVersionUID = 1L;

    private String sopInstanceUid;
    private String comment;
    private String doseUnit;
    private String doseType;
    private String doseSummationType;
    private double doseGridScaling;
    private double doseMax;
    private String referencedPlanUid;

    private List<MediaElement> images = new ArrayList<>();
    private List<IsoDose> isoDoses = new ArrayList<>();

    public String getSopInstanceUid() {
        return sopInstanceUid;
    }

    public void setSopInstanceUid(String sopInstanceUid) {
        this.sopInstanceUid = sopInstanceUid;
    }

    public String getComment() {
        return this.comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public String getDoseUnit() {
        return this.doseUnit;
    }

    public void setDoseUnit(String doseUnit) {
        this.doseUnit = doseUnit;
    }

    public String getDoseType() {
        return doseType;
    }

    public void setDoseType(String doseType) {
        this.doseType = doseType;
    }

    public String getDoseSummationType() {
        return doseSummationType;
    }

    public void setDoseSummationType(String doseSummationType) {
        this.doseSummationType = doseSummationType;
    }

    public double getDoseGridScaling() {
        return doseGridScaling;
    }

    public void setDoseGridScaling(double doseGridScaling) {
        this.doseGridScaling = doseGridScaling;
    }

    public double getDoseMax() {
        return doseMax;
    }

    public void setDoseMax(double doseMax) {
        this.doseMax = doseMax;
    }

    public String getReferencedPlanUid() {
        return this.referencedPlanUid;
    }

    public void setReferencedPlanUid(String referencedPlanUid) {
        this.referencedPlanUid = referencedPlanUid;
    }

    public List<MediaElement> getImages() {
        return this.images;
    }

    public void setImages(List<MediaElement> images) {
        this.images = images;
    }

    public List<IsoDose> getIsoDoses() {
        return this.isoDoses;
    }

    public void setIsoDoses(List<IsoDose> isoDoses) {
        this.isoDoses = isoDoses;
    }
}
