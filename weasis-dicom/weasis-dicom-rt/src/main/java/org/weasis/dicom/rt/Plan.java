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

/**
 * Created by toskrip on 2/1/15.
 */
public class Plan {

    private String label;
    private Date date;
    private String name;
    private Double rxDose;

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

    public Double getRxDose() {
        return this.rxDose;
    }

    public void setRxDose(Double rxDose2) {
        this.rxDose = rxDose2;
    }

}
