/*******************************************************************************
 * Copyright (c) 2017 Weasis Team.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     Tomas Skripcak  - initial API and implementation
 *     Nicolas Roduit - initial API and implementation
 ******************************************************************************/

package org.weasis.dicom.rt;

import java.util.Date;

public class Plan {

    private String sopInstanceUid;
    private String label;
    private Date date;
    private String name;
    private String description;
    private String geometry;
    private Double rxDose;

    public String getSopInstanceUid() {
        return this.sopInstanceUid;
    }

    public void setSopInstanceUid(String sopInstanceUid) {
        this.sopInstanceUid = sopInstanceUid;
    }

    public String getLabel() {
        return this.label;
    }

    public void setLabel(String value) {
        this.label = value;
    }

    public Date getDate() {
        return this.date;
    }

    public void setDate(Date value) {
        this.date = value;
    }

    public String getName() {
        return this.name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return this.description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getGeometry() {
        return this.geometry;
    }

    public void setGeometry(String geometry) {
        this.geometry = geometry;
    }

    public Double getRxDose() {
        return this.rxDose;
    }

    public void setRxDose(Double rxDose) {
        this.rxDose = rxDose;
    }

}
