/*******************************************************************************
 * Copyright (c) 2009-2018 Weasis Team and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 *     Nicolas Roduit - initial API and implementation
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
