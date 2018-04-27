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

import java.util.Objects;

public class Lead {

    public static final Lead I = new Lead("I");
    public static final Lead II = new Lead("II");
    public static final Lead III = new Lead("III");
    public static final Lead AVR = new Lead("aVR");
    public static final Lead AVL = new Lead("aVL");
    public static final Lead AVF = new Lead("aVF");
    public static final Lead V1 = new Lead("V1");
    public static final Lead V2 = new Lead("V2");
    public static final Lead V3 = new Lead("V3");
    public static final Lead V4 = new Lead("V4");
    public static final Lead V5 = new Lead("V5");
    public static final Lead V6 = new Lead("V6");
    public static final Lead RYTHM = new Lead("II (Rythm)");
    
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
        String[] val = n.split(" ");
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
