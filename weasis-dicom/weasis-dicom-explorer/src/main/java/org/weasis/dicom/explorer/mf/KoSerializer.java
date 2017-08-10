/*******************************************************************************
 * Copyright (c) 2016 Weasis Team and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Nicolas Roduit - initial API and implementation
 *******************************************************************************/
package org.weasis.dicom.explorer.mf;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import org.dcm4che3.data.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.media.data.TagW;
import org.weasis.dicom.codec.KOSpecialElement;
import org.weasis.dicom.codec.TagD;
import org.weasis.dicom.mf.ArcParameters;
import org.weasis.dicom.mf.Xml;

public class KoSerializer {
    private static final Logger LOGGER = LoggerFactory.getLogger(KoSerializer.class);

    public static final String TAG_SEL_ROOT = "selections";
    public static final String TAG_SEL = "selection";
    public static final String SEL_NAME = "name";
    public static final String SERIES_UID = "seriesUID";
    public static final String SOP_UID = "sopUID";

    private KoSerializer() {
    }

    public static void writeSelection(Collection<KOSpecialElement> list, StringBuilder buf) {
        if (list != null && buf != null) {
            try {
                buf.append("\n<");
                buf.append(TAG_SEL_ROOT);
                buf.append(">");
                for (KOSpecialElement ko : list) {
                    writeKoElement(ko, buf);
                }

                buf.append("\n</");
                buf.append(TAG_SEL_ROOT);
                buf.append(">");

            } catch (Exception e) {
                LOGGER.error("Cannot write Key Object Selection: ", e); //$NON-NLS-1$
            }
        }
    }

    private static void writeKoElement(KOSpecialElement ko, StringBuilder buf) {
        buf.append("\n<");
        buf.append(TAG_SEL);
        buf.append(" ");
        Xml.addXmlAttribute(SEL_NAME, ko.getLabel(), buf);
        buf.append(">");
        
        
        buf.append("\n<");
        buf.append(Xml.Level.SERIES);
        buf.append(" ");
        String sopUID = TagD.getTagValue(ko, Tag.SOPInstanceUID, String.class);
        Xml.addXmlAttribute(SOP_UID, sopUID, buf);
        buf.append(" ");
        String seriesUID = TagD.getTagValue(ko, Tag.SeriesInstanceUID, String.class);
        Xml.addXmlAttribute(SERIES_UID, seriesUID, buf);
        buf.append(">");
        
        writeImage(ko.getReferencedSeriesInstanceUIDSet(), buf);
        
        
        buf.append("\n</");
        buf.append(Xml.Level.SERIES);
        buf.append(">");

        buf.append("\n</");
        buf.append(TAG_SEL);
        buf.append(">");
    }
    
    private static void writeImage(Set<String> set, StringBuilder buf) {

       
        buf.append("\n<");
        buf.append(Xml.Level.INSTANCE);
        buf.append(" ");


        buf.append(" />");
    }
}
