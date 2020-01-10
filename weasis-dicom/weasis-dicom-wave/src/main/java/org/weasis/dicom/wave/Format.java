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

public enum Format {

    DEFAULT(Messages.getString("Format.1_10_sec"), 1, 0, Lead.DEFAULT_12LEAD), //$NON-NLS-1$

    TWO(Messages.getString("Format.2_5_sec"), 2, 6, //$NON-NLS-1$
                    new Lead[] { Lead.I, Lead.V1, Lead.II, Lead.V2, Lead.III, Lead.V3, Lead.AVR, Lead.V4, Lead.AVL,
                        Lead.V5, Lead.AVF, Lead.V6 }),

    FOUR(Messages.getString("Format.4_2_sec"), 4, 3, //$NON-NLS-1$
                    new Lead[] { Lead.I, Lead.AVR, Lead.V1, Lead.V4, Lead.II, Lead.AVL, Lead.V2, Lead.V5, Lead.III,
                        Lead.AVF, Lead.V3, Lead.V6 }),

    FOUR_RYTHM(Messages.getString("Format.rhythm"), 4, 4, new Lead[] { Lead.I, Lead.AVR, Lead.V1, Lead.V4, Lead.II, //$NON-NLS-1$
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