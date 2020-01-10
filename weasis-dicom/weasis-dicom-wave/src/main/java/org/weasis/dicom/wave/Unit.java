/*******************************************************************************
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.weasis.dicom.wave;

public class Unit {

    private final String fullName;
    private final String abbreviation;
    private final double scalingFactor;

    public Unit(String fullName, String abbreviation, double scalingFactor) {
        this.fullName = fullName;
        this.abbreviation = abbreviation;
        this.scalingFactor = scalingFactor;
    }

    public String getFullName() {
        return fullName;
    }

    public String getAbbreviation() {
        return abbreviation;
    }

    public double getScalingFactor() {
        return scalingFactor;
    }
}
