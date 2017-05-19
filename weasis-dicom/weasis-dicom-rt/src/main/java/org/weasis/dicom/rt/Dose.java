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

import java.util.List;

public class Dose {

    private String sopInstanceUid;
    private String doseUnit;
    private String doseType;
    private String doseSummationType;
    private double doseGridScaling;
    private double doseMax;

    private Plan rtPlan;
    private List<Dvh> rtDvhs;

    public String getSopInstanceUid() {
        return sopInstanceUid;
    }

    public void setSopInstanceUid(String sopInstanceUid) {
        this.sopInstanceUid = sopInstanceUid;
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

    public Plan getRtPlan() {
        return rtPlan;
    }

    public void setRtPlan(Plan rtPlan) {
        this.rtPlan = rtPlan;
    }

    public List<Dvh> getRtDvhs() {
        return rtDvhs;
    }

    public void setRtDvhs(List<Dvh> rtDvhs) {
        this.rtDvhs = rtDvhs;
    }

    public int getRtDvhsCount() {
        if (this.rtDvhs != null) {
            return this.rtDvhs.size();
        }

        return 0;
    }
    
}
