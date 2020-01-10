/*******************************************************************************
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.weasis.core.api.image.util;

/**
 * <code>AbbreviationUnit</code> is similar to the Class <code>Unit</code>, except that the method
 * <code>toString()</code> returns the abbreviation of the unit.
 * <p>
 *
 * @author Nicolas Roduit
 * @see org.weasis.core.api.image.util.Unit
 */

public class AbbreviationUnit {

    private Unit unit;

    /**
     * Create a new instance
     *
     * @param unit
     *            Unit
     */
    public AbbreviationUnit(Unit unit) {
        this.unit = unit;
    }

    /**
     * Returns the abbreviation of the unit.
     *
     * @return the abbreviation of the unit
     */
    @Override
    public String toString() {
        return unit.getAbbreviation();
    }

    /**
     * Returns the unit.
     *
     * @return Unit
     * @see org.weasis.core.api.image.util.Unit
     */
    public Unit getUnit() {
        return unit;
    }

}
