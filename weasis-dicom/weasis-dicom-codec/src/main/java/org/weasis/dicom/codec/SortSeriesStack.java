/*******************************************************************************
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.weasis.dicom.codec;

import java.time.LocalTime;

import org.dcm4che3.data.Tag;
import org.weasis.core.api.media.data.SeriesComparator;
import org.weasis.core.api.media.data.TagW;

public final class SortSeriesStack {

    // Comparator cannot be a generic list of DicomOpImage because the Collection to sort has an AbstractImage type
    public static final SeriesComparator<DicomImageElement> instanceNumber = new SeriesComparator<DicomImageElement>() {

        @Override
        public int compare(DicomImageElement m1, DicomImageElement m2) {
            Integer val1 = TagD.getTagValue(m1, Tag.InstanceNumber, Integer.class);
            Integer val2 = TagD.getTagValue(m2, Tag.InstanceNumber, Integer.class);
            if (val1 == null || val2 == null) {
                return 0;
            }
            return val1.compareTo(val2);
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
            Double val1 = TagD.getTagValue(m1, Tag.SliceLocation, Double.class);
            Double val2 = TagD.getTagValue(m2, Tag.SliceLocation, Double.class);
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
                LocalTime val1 = TagD.getTagValue(m1, Tag.AcquisitionTime, LocalTime.class);
                LocalTime val2 = TagD.getTagValue(m2, Tag.AcquisitionTime, LocalTime.class);
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
            LocalTime val1 = TagD.getTagValue(m1, Tag.ContentTime, LocalTime.class);
            LocalTime val2 = TagD.getTagValue(m2, Tag.ContentTime, LocalTime.class);
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

    public static final SeriesComparator<DicomImageElement> diffusionBValue =
        new SeriesComparator<DicomImageElement>() {

            @Override
            public int compare(DicomImageElement m1, DicomImageElement m2) {
                Double val1 = TagD.getTagValue(m1, Tag.DiffusionBValue, Double.class);
                Double val2 = TagD.getTagValue(m2, Tag.DiffusionBValue, Double.class);
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

    private SortSeriesStack() {
    }

    public static SeriesComparator<DicomImageElement>[] getValues() {
        return new SeriesComparator[] { instanceNumber, slicePosition, sliceLocation, contentTime, acquisitionTime,
            diffusionBValue };
    }
}
