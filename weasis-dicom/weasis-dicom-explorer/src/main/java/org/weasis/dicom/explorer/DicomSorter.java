/*******************************************************************************
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.weasis.dicom.explorer;

import java.text.Collator;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.Locale;
import java.util.Objects;

import javax.swing.tree.DefaultMutableTreeNode;

import org.dcm4che3.data.Tag;
import org.weasis.core.api.media.data.MediaElement;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.media.data.MediaSeriesGroup;
import org.weasis.core.api.media.data.TagW;
import org.weasis.dicom.codec.TagD;
import org.weasis.dicom.explorer.DicomExplorer.SeriesPane;
import org.weasis.dicom.explorer.DicomExplorer.StudyPane;

public class DicomSorter {
    private static final Collator collator = Collator.getInstance(Locale.getDefault());
    public static final Comparator<Object> PATIENT_COMPARATOR =
        (o1, o2) -> collator.compare(o1.toString(), o2.toString());

    public static final Comparator<Object> STUDY_COMPARATOR = (o1, o2) -> {
        if (o1 instanceof StudyPane && o2 instanceof StudyPane) {
            o1 = ((StudyPane) o1).dicomStudy;
            o2 = ((StudyPane) o2).dicomStudy;
        } else if (o1 instanceof DefaultMutableTreeNode && o2 instanceof DefaultMutableTreeNode) {
            o1 = ((DefaultMutableTreeNode) o1).getUserObject();
            o2 = ((DefaultMutableTreeNode) o2).getUserObject();
        }

        if (o1 instanceof MediaSeriesGroup && o2 instanceof MediaSeriesGroup) {
            MediaSeriesGroup st1 = (MediaSeriesGroup) o1;
            MediaSeriesGroup st2 = (MediaSeriesGroup) o2;
            LocalDateTime date1 = TagD.dateTime(Tag.StudyDate, Tag.StudyTime, st1);
            LocalDateTime date2 = TagD.dateTime(Tag.StudyDate, Tag.StudyTime, st2);
            // LOGGER.debug("date1: {} date2: {}", date1, date2);
            int c = -1;
            if (date1 != null && date2 != null) {
                // Reverse chronological order.
                c = date2.compareTo(date1);
                if (c != 0) {
                    return c;
                }
            }

            if (c == 0 || (date1 == null && date2 == null)) {
                String d1 = TagD.getTagValue(st1, Tag.StudyDescription, String.class);
                String d2 = TagD.getTagValue(st2, Tag.StudyDescription, String.class);
                if (d1 != null && d2 != null) {
                    c = collator.compare(d1, d2);
                    if (c != 0) {
                        return c;
                    }
                }
                if (d1 == null) {
                    // Add o1 after o2
                    return d2 == null ? 0 : 1;
                }
                // Add o2 after o1
                return -1;
            } else {
                if (date1 == null) {
                    // Add o1 after o2
                    return 1;
                }
                if (date2 == null) {
                    return -1;
                }
            }
        } else {
            // Set non MediaSeriesGroup at the beginning of the list
            if (o1 instanceof MediaSeriesGroup) {
                // Add o1 after o2
                return 1;
            }
            if (o2 instanceof MediaSeriesGroup) {
                return -1;
            }
        }
        return Objects.equals(o1, o2) ? 0 : -1;
    };

    public static final Comparator<Object> SERIES_COMPARATOR = (o1, o2) -> {

        if (o1 instanceof SeriesPane && o2 instanceof SeriesPane) {
            o1 = ((SeriesPane) o1).sequence;
            o2 = ((SeriesPane) o2).sequence;
        } else if (o1 instanceof DefaultMutableTreeNode && o2 instanceof DefaultMutableTreeNode) {
            o1 = ((DefaultMutableTreeNode) o1).getUserObject();
            o2 = ((DefaultMutableTreeNode) o2).getUserObject();
        }

        if (o1 instanceof MediaSeriesGroup && o2 instanceof MediaSeriesGroup) {
            MediaSeriesGroup st1 = (MediaSeriesGroup) o1;
            MediaSeriesGroup st2 = (MediaSeriesGroup) o2;

            // Force Dose report to be at the end
            if (isDoseReport(st1)) {
                return 1;
            }
            if (isDoseReport(st2)) {
                return -1;
            }

            Integer val1 = TagD.getTagValue(st1, Tag.SeriesNumber, Integer.class);
            Integer val2 = TagD.getTagValue(st2, Tag.SeriesNumber, Integer.class);
            int c = -1;
            if (val1 != null && val2 != null) {
                c = val1.compareTo(val2);
                if (c != 0) {
                    return c;
                }
                Integer sval1 = TagW.getTagValue(st1, TagW.SplitSeriesNumber, Integer.class);
                Integer sval2 = TagW.getTagValue(st2, TagW.SplitSeriesNumber, Integer.class);
                if (sval1 != null && sval2 != null) {
                    c = sval1.compareTo(sval2);
                    if (c != 0) {
                        return c;
                    }
                }
            }

            if (c == 0 || (val1 == null && val2 == null)) {
                LocalDateTime date1 = TagD.dateTime(Tag.SeriesDate, Tag.SeriesTime, st1);
                LocalDateTime date2 = TagD.dateTime(Tag.SeriesDate, Tag.SeriesTime, st2);
                if (date1 != null && date2 != null) {
                    // Chronological order.
                    c = date1.compareTo(date2);
                    if (c != 0) {
                        return c;
                    }
                }

                if ((c == 0 || (date1 == null && date2 == null)) && st1 instanceof MediaSeries
                    && st2 instanceof MediaSeries) {
                    MediaElement media1 = ((MediaSeries<? extends MediaElement>) st1).getMedia(0, null, null);
                    MediaElement media2 = ((MediaSeries<? extends MediaElement>) st2).getMedia(0, null, null);
                    if (media1 != null && media2 != null) {
                        date1 = TagD.dateTime(Tag.AcquisitionDate, Tag.AcquisitionTime, media1);
                        date2 = TagD.dateTime(Tag.AcquisitionDate, Tag.AcquisitionTime, media2);
                        if (date1 == null) {
                            date1 = TagD.dateTime(Tag.ContentDate, Tag.ContentTime, media1);
                            if (date1 == null) {
                                date1 = TagD.dateTime(Tag.DateOfSecondaryCapture, Tag.TimeOfSecondaryCapture, media1);
                            }
                        }
                        if (date2 == null) {
                            date2 = TagD.dateTime(Tag.ContentDate, Tag.ContentTime, media2);
                            if (date2 == null) {
                                date2 = TagD.dateTime(Tag.DateOfSecondaryCapture, Tag.TimeOfSecondaryCapture, media2);
                            }
                        }
                        if (date1 != null && date2 != null) {
                            // Chronological order.
                            c = date1.compareTo(date2);
                            if (c != 0) {
                                return c;
                            }
                        }
                        if (c == 0 || (date1 == null && date2 == null)) {
                            Double tag1 = TagD.getTagValue(media1, Tag.SliceLocation, Double.class);
                            Double tag2 = TagD.getTagValue(media2, Tag.SliceLocation, Double.class);
                            if (tag1 != null && tag2 != null) {
                                c = tag1.compareTo(tag2);
                                if (c != 0) {
                                    return c;
                                }
                            }
                            if (c == 0 || (tag1 == null && tag2 == null)) {
                                String nb1 = TagD.getTagValue(media1, Tag.StackID, String.class);
                                String nb2 = TagD.getTagValue(media2, Tag.StackID, String.class);
                                if (nb1 != null && nb2 != null) {
                                    c = nb1.compareTo(nb2);
                                    if (c != 0) {
                                        try {
                                            c = Integer.compare(Integer.parseInt(nb1), Integer.parseInt(nb2));
                                        } catch (Exception ex) {
                                        }
                                        return c;
                                    }
                                }
                                if (c == 0 || (nb1 == null && nb2 == null)) {
                                    return 0;
                                }
                                if (nb1 == null) {
                                    return 1;
                                }
                                return -1;
                            }
                            if (tag1 == null) {
                                return 1;
                            }
                            return -1;
                        }
                        if (date1 == null) {
                            // Add o1 after o2
                            return 1;
                        }
                        // Add o2 after o1
                        return -1;
                    }
                    if (media2 == null) {
                        // Add o2 after o1
                        return media1 == null ? 0 : -1;
                    }
                    return 1;
                }
                if (date1 == null) {
                    return 1;
                }
                return -1;
            }
            if (val1 == null) {
                return 1;
            }
            return -1;
        }
        // Set non MediaSeriesGroup at the beginning of the list
        if (o1 instanceof MediaSeriesGroup) {
            // Add o1 after o2
            return 1;
        }
        if (o2 instanceof MediaSeriesGroup) {
            return -1;
        }
        return Objects.equals(o1, o2) ? 0 : -1;
    };

    private static boolean isDoseReport(MediaSeriesGroup series) {
        String s1 = TagD.getTagValue(series, Tag.SOPClassUID, String.class);
        if(s1 == null || !s1.startsWith("1.2.840.10008.5.1.4.1.1.88")) { //$NON-NLS-1$
            return false;
        }
        return "1.2.840.10008.5.1.4.1.1.88.67".equals(s1) || "1.2.840.10008.5.1.4.1.1.88.68".equals(s1) //$NON-NLS-1$ //$NON-NLS-2$
            || "1.2.840.10008.5.1.4.1.1.88.73".equals(s1); //$NON-NLS-1$
    }

}
