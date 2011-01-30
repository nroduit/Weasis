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

import org.dcm4che2.data.VR;
import org.weasis.core.api.media.data.MediaElement;
import org.weasis.core.api.media.data.TagW;

public class DicomTag extends TagW {

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
        TagW tag = image.getTagElement(id);
        if (tag == null) {
            return ""; //$NON-NLS-1$
        }
        return super.getFormattedText(image.getTagValue(tag));
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
