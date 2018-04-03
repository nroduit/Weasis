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

public enum Format {

    DEFAULT("1x10 Seconds", 1, 0, Lead.DEFAULT_12LEAD),

    TWO("2x5 Seconds", 2, 6,
                    new Lead[] { Lead.I, Lead.V1, Lead.II, Lead.V2, Lead.III, Lead.V3, Lead.AVR, Lead.V4, Lead.AVL,
                        Lead.V5, Lead.AVF, Lead.V6 }),

    FOUR("4x2.5 Seconds", 4, 3,
                    new Lead[] { Lead.I, Lead.AVR, Lead.V1, Lead.V4, Lead.II, Lead.AVL, Lead.V2, Lead.V5, Lead.III,
                        Lead.AVF, Lead.V3, Lead.V6 }),

    FOUR_RYTHM("4x2.5 Seconds with rhythm", 4, 4, new Lead[] { Lead.I, Lead.AVR, Lead.V1, Lead.V4, Lead.II,
        Lead.AVL, Lead.V2, Lead.V5, Lead.III, Lead.AVF, Lead.V3, Lead.V6, Lead.RYTHM });

    private final String value;
    private final int xlayoutSize;
    private final int ylayoutSize;
    private final Lead[] leads;

    private Format(String value, int xlayoutSize, int ylayoutSize, Lead[] layoutNames) {
        this.value = value;
        this.xlayoutSize = xlayoutSize;
        this.ylayoutSize = ylayoutSize;
        this.leads = layoutNames;
    }

    public int getXlayoutSize() {
        return xlayoutSize;
    }

    public int getYlayoutSize() {
        return ylayoutSize;
    }

    public String getValue() {
        return value;
    }

    public Lead[] getLeads() {
        return leads;
    }

    @Override
    public String toString() {
        return value;
    }
}