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

import java.util.Objects;

public class Lead {

    public static final Lead I = new Lead("I"); //$NON-NLS-1$
    public static final Lead II = new Lead("II"); //$NON-NLS-1$
    public static final Lead III = new Lead("III"); //$NON-NLS-1$
    public static final Lead AVR = new Lead("aVR"); //$NON-NLS-1$
    public static final Lead AVL = new Lead("aVL"); //$NON-NLS-1$
    public static final Lead AVF = new Lead("aVF"); //$NON-NLS-1$
    public static final Lead V1 = new Lead("V1"); //$NON-NLS-1$
    public static final Lead V2 = new Lead("V2"); //$NON-NLS-1$
    public static final Lead V3 = new Lead("V3"); //$NON-NLS-1$
    public static final Lead V4 = new Lead("V4"); //$NON-NLS-1$
    public static final Lead V5 = new Lead("V5"); //$NON-NLS-1$
    public static final Lead V6 = new Lead("V6"); //$NON-NLS-1$
    public static final Lead RYTHM = new Lead("II (Rythm)"); //$NON-NLS-1$
    
    static final Lead[] DEFAULT_12LEAD = { I, II, III, AVR, AVL, AVF, V1, V2, V3, V4, V5, V6 };

    private final String name;

    private Lead(String name) {
        this.name = Objects.requireNonNull(name);
    }

    @Override
    public int hashCode() {
        return 31 + getName().hashCode();
    }

    @Override
    public String toString() {
        return name;
    }

    public String getName() {
        return name.toUpperCase();
    }
    
    private static String getCode(String n) {
        String[] val = n.split(" "); //$NON-NLS-1$
        return val[val.length -1];
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        Lead other = (Lead) obj;
        return getName().equals(other.getName());
    }

    public static Lead buildLead(String title) {
        String val = getCode(title.toUpperCase());
        for (Lead lead : DEFAULT_12LEAD) {
            if (val.equals(getCode(lead.getName()))) {
                return lead;
            }
        }
        return new Lead(title);
    }
}
