/*******************************************************************************
 * Copyright (c) 2011 Weasis Team.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     Nicolas Roduit - initial API and implementation
 ******************************************************************************/
package org.weasis.core.ui.graphic;

public class MeasureItem {
    private final Measurement measurement;
    private final Double value;
    private final String unit;

    public MeasureItem(Measurement measurement, Double value, String unit) {
        if (measurement == null)
            throw new IllegalArgumentException("Measurement cannot be null!"); //$NON-NLS-1$
        this.measurement = measurement;
        this.value = value;
        this.unit = unit;
    }

    public Measurement getMeasurement() {
        return measurement;
    }

    public Double getValue() {
        return value;
    }

    public String getUnit() {
        return unit;
    }

}
