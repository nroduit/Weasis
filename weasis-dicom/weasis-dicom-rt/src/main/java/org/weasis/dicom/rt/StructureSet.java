/*******************************************************************************
 * Copyright (c) 2017 Weasis Team.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     Nicolas Roduit - initial API and implementation
 ******************************************************************************/

package org.weasis.dicom.rt;

import java.util.Date;
import java.util.HashMap;
import java.util.Objects;

public class StructureSet extends HashMap<Integer, StructureLayer> {
    private static final long serialVersionUID = 6156886965129631894L;

    private final String label;
    private final Date date;

    public StructureSet(String label, Date date) {
        this.label = Objects.requireNonNull(label);
        this.date = date;
    }
    
    public String getLabel() {
        return this.label;
    }

    public Date getDate() {
        return this.date;
    }

    @Override
    public String toString() {
        return this.label;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = super.hashCode();
        result = prime * result + ((date == null) ? 0 : date.hashCode());
        result = prime * result + ((label == null) ? 0 : label.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (!super.equals(obj))
            return false;
        if (getClass() != obj.getClass())
            return false;
        StructureSet other = (StructureSet) obj;
        if (date == null) {
            if (other.date != null)
                return false;
        } else if (!date.equals(other.date))
            return false;
        if (label == null) {
            if (other.label != null)
                return false;
        } else if (!label.equals(other.label))
            return false;
        return true;
    }

}
