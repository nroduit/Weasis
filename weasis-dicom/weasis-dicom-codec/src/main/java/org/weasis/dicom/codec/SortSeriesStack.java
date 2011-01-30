/*******************************************************************************
 * Copyright (c) 2010 Nicolas Roduit.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     Nicolas Roduit - initial API and implementation
 ******************************************************************************/
package org.weasis.dicom.codec;

import java.util.Comparator;
import java.util.Date;

import org.weasis.core.api.media.data.TagW;

public final class SortSeriesStack {

    // Comparator cannot be a generic list of DicomOpImage because the Collection to sort has an AbstractImage type
    public final static Comparator<DicomImageElement> instanceNumber = new Comparator<DicomImageElement>() {

        public int compare(DicomImageElement m1, DicomImageElement m2) {
            Integer val1 = (Integer) m1.getTagValue(TagW.InstanceNumber);
            Integer val2 = (Integer) m2.getTagValue(TagW.InstanceNumber);
            if (val1 == null || val2 == null) {
                return 0;
            }
            return val1 < val2 ? -1 : (val1 == val2 ? 0 : 1);
        }

        @Override
        public String toString() {
            return Messages.getString("SortSeriesStack.inst"); //$NON-NLS-1$
        }
    };

    public final static Comparator<DicomImageElement> sliceLocation = new Comparator<DicomImageElement>() {

        public int compare(DicomImageElement m1, DicomImageElement m2) {
            Float val1 = (Float) m1.getTagValue(TagW.SliceLocation);
            Float val2 = (Float) m2.getTagValue(TagW.SliceLocation);
            if (val1 == null || val2 == null) {
                return 0;
            }
            return val1.compareTo(val2);
        }

        @Override
        public String toString() {
            return Messages.getString("SortSeriesStack.location"); //$NON-NLS-1$
        }
    };

    public final static Comparator<DicomImageElement> instanceTime = new Comparator<DicomImageElement>() {

        public int compare(DicomImageElement m1, DicomImageElement m2) {
            Date val1 = (Date) m1.getTagValue(TagW.AcquisitionTime);
            Date val2 = (Date) m2.getTagValue(TagW.AcquisitionTime);
            if (val1 == null || val2 == null) {
                return 0;
            }
            return val1.compareTo(val2);
        }

        @Override
        public String toString() {
            return Messages.getString("SortSeriesStack.time"); //$NON-NLS-1$
        }
    };

    public static Comparator<DicomImageElement>[] getValues() {
        return new Comparator[] { instanceNumber, sliceLocation, instanceTime };
    }
}
