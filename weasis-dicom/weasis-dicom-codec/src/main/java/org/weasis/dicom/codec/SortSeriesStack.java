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

import java.util.Date;

import org.weasis.core.api.media.data.SeriesComparator;
import org.weasis.core.api.media.data.TagW;

public final class SortSeriesStack {

    // Comparator cannot be a generic list of DicomOpImage because the Collection to sort has an AbstractImage type
    public static final SeriesComparator<DicomImageElement> instanceNumber = new SeriesComparator<DicomImageElement>() {

        @Override
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
    public static final SeriesComparator<DicomImageElement> slicePosition = new SeriesComparator<DicomImageElement>() {

        @Override
        public int compare(DicomImageElement m1, DicomImageElement m2) {
            double[] val1 = (double[]) m1.getTagValue(TagW.SlicePosition);
            double[] val2 = (double[]) m2.getTagValue(TagW.SlicePosition);
            if (val1 == null || val2 == null) {
                return 0;
            }
            return Double.compare(val1[0] + val1[1] + val1[2], val2[0] + val2[1] + val2[2]);
        }

        @Override
        public String toString() {
            return Messages.getString("SortSeriesStack.pos_orient"); //$NON-NLS-1$
        }
    };

    public static final SeriesComparator<DicomImageElement> sliceLocation = new SeriesComparator<DicomImageElement>() {

        @Override
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

    public static final SeriesComparator<DicomImageElement> acquisitionTime =
        new SeriesComparator<DicomImageElement>() {

            @Override
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
    public static final SeriesComparator<DicomImageElement> contentTime = new SeriesComparator<DicomImageElement>() {

        @Override
        public int compare(DicomImageElement m1, DicomImageElement m2) {
            Date val1 = (Date) m1.getTagValue(TagW.ContentTime);
            Date val2 = (Date) m2.getTagValue(TagW.ContentTime);
            if (val1 == null || val2 == null) {
                return 0;
            }
            return val1.compareTo(val2);
        }

        @Override
        public String toString() {
            return Messages.getString("SortSeriesStack.content_time"); //$NON-NLS-1$
        }
    };
    
    public static final SeriesComparator<DicomImageElement> diffusionBValue = new SeriesComparator<DicomImageElement>() {

        @Override
        public int compare(DicomImageElement m1, DicomImageElement m2) {
            Double val1 = (Double) m1.getTagValue(TagW.DiffusionBValue);
            Double val2 = (Double) m2.getTagValue(TagW.DiffusionBValue);
            if (val1 == null || val2 == null) {
                return 0;
            }
            return val1.compareTo(val2);
        }

        @Override
        public String toString() {
            return Messages.getString("SortSeriesStack.dvalue"); //$NON-NLS-1$
        }
    };

    public static SeriesComparator<DicomImageElement>[] getValues() {
        return new SeriesComparator[] { instanceNumber, slicePosition, sliceLocation, contentTime, acquisitionTime,
            diffusionBValue };
    }
}
