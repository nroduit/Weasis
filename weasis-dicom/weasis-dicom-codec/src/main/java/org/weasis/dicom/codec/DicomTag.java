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

import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

import org.dcm4che2.data.VR;
import org.weasis.core.api.media.data.MediaElement;
import org.weasis.core.api.media.data.TagElement;

public class DicomTag extends TagElement {

    private final VR vr;
    private final String vm;
    private final boolean retired;

    public DicomTag(int key, String name, VR vr, String vm, String format, boolean retired) {
        super(key, name, TagDictionary.getTagType(vr, vm));
        this.vr = vr;
        this.vm = vm;
        this.format = format;
        this.retired = retired;
    }

    public String getVm() {
        return vm;
    }

    public boolean isRetired() {
        return retired;
    }

    public String getFormattedText(MediaElement image) {
        // Let possiblity to search in series (mediaGroupNode) and its parent
        TagElement tag = image.getTagElement(id);
        if (tag == null) {
            return ""; //$NON-NLS-1$
        }
        Object value = image.getTagValue(tag);
        if (value == null) {
            return ""; //$NON-NLS-1$
        }
        String str;
        // TODO externalize date format in settings
        // Date
        if (vr == VR.DA) {
            str = getFormattedDate((Date) value, "dd-MM-yyyy"); //$NON-NLS-1$
        } else if (vr == VR.TM) {
            str = getFormattedDate((Date) value, "HH:mm:ss"); //$NON-NLS-1$
        }
        // Date Time
        else if (vr == VR.DT) {
            str = getFormattedDate((Date) value, "dd-MM-yyyy at HH:mm:ss"); //$NON-NLS-1$
        } else if (vr == VR.AS) {
            // 3 digits followed by one of the characters 'D' (Day),'W' (Week), 'M' (Month) or 'Y' (Year)
            // For ex: DICOM (0010,1010) = 031Y
            str = value.toString();
            char[] tab = str.toCharArray();
            for (int i = 0; i < tab.length; i++) {
                if (tab[i] == '0') {
                    str = str.substring(1);
                } else {
                    break;
                }
            }
            if (tab.length > 0) {
                switch (tab[tab.length - 1]) {
                    case 'Y':
                        str = str.replaceFirst("Y", " " + Messages.getString("DicomTag.year")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                        break;
                    case 'M':
                        str = str.replaceFirst("M", " " + Messages.getString("DicomTag.month")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                        break;
                    case 'W':
                        str = str.replaceFirst("W", " " + Messages.getString("DicomTag.week")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                        break;
                    case 'D':
                        str = str.replaceFirst("D", " " + Messages.getString("DicomTag.day")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                        break;
                }
            }
        } else {

            str = value.toString();
        }
        if (format != null && !format.trim().equals("$V") && !str.equals("")) { //$NON-NLS-1$ //$NON-NLS-2$
            int index = format.indexOf("$V"); //$NON-NLS-1$
            int fmLength = 2;
            if (index != -1) {
                boolean suffix = format.length() > index + fmLength;
                if (suffix && format.charAt(index + fmLength) == ':') {
                    fmLength++;
                    if (vr == VR.DS || vr == VR.FL || vr == VR.FD) {
                        String pattern = getPattern(index + fmLength);
                        if (pattern != null) {
                            fmLength += pattern.length() + 2;
                            try {
                                str = new DecimalFormat(pattern).format(Double.parseDouble(str));
                            } catch (NumberFormatException e) {
                            }
                        }
                    }
                }
                str = format.substring(0, index) + str;
                if (format.length() > index + fmLength) {
                    str += format.substring(index + fmLength);
                }
            }
        }
        return str;
    }

    private String getFormattedDate(Date date, String format) {
        if (date == null) {
            return ""; //$NON-NLS-1$
        }
        return new SimpleDateFormat(format).format(date);
    }

    public static String getFormattedTag(int key) {
        String tagVal = Integer.toHexString(key).toUpperCase();
        int cut = tagVal.length() - 4;
        String tag1 = tagVal.substring(0, cut);
        while (tag1.length() < 4) {
            tag1 = "0" + tag1; //$NON-NLS-1$
        }
        return " (" + tag1 + "," + tagVal.substring(cut) + ")"; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
    }

}
