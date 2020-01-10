/*******************************************************************************
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.weasis.core.ui.model.utils.bean;

import java.util.Objects;

public class MeasureItem {
    private final Measurement measurement;
    private final Object value;
    private final String unit;
    private final String labelExtension;

    public MeasureItem(Measurement measurement, Object value, String unit) {
        this(measurement, null, value, unit);
    }

    public MeasureItem(Measurement measurement, String labelExtension, Object value, String unit) {
        this.measurement = Objects.requireNonNull(measurement, "Measurement cannot be null!"); //$NON-NLS-1$
        this.value = value;
        this.unit = unit;
        this.labelExtension = labelExtension;
    }

    public Measurement getMeasurement() {
        return measurement;
    }

    public Object getValue() {
        return value;
    }

    public String getUnit() {
        return unit;
    }

    public String getLabelExtension() {
        return labelExtension;
    }

}
