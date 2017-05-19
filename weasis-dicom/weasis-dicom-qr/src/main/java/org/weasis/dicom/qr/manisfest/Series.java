/*******************************************************************************
 * Copyright (c) 2014 Weasis Team.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Nicolas Roduit - initial API and implementation
 *******************************************************************************/
package org.weasis.dicom.qr.manisfest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.dcm4che3.data.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.dicom.qr.manisfest.xml.TagUtil;
import org.weasis.dicom.qr.manisfest.xml.XmlDescription;

public class Series implements XmlDescription {
    private static final Logger LOGGER = LoggerFactory.getLogger(Series.class);

    private final String seriesInstanceUID;
    private String seriesDescription = null;
    private final ArrayList<SOPInstance> sopInstancesList;
    private String modality = null;
    private String seriesNumber = null;
    private String wadoTransferSyntaxUID = null;
    // Image quality within the range 1 to 100, 100 being the best quality.
    private int wadoCompression = 0;
    private String thumbnail = null;

    public Series(String seriesInstanceUID) {
        if (seriesInstanceUID == null) {
            throw new IllegalArgumentException("seriesInstanceUID is null"); //$NON-NLS-1$
        }
        this.seriesInstanceUID = seriesInstanceUID;
        sopInstancesList = new ArrayList<>();
    }

    public String getSeriesInstanceUID() {
        return seriesInstanceUID;
    }

    public String getSeriesDescription() {
        return seriesDescription;
    }

    public String getSeriesNumber() {
        return seriesNumber;
    }

    public void setSeriesNumber(String seriesNumber) {
        this.seriesNumber = seriesNumber == null ? null : seriesNumber.trim();
    }

    public String getWadoTransferSyntaxUID() {
        return wadoTransferSyntaxUID;
    }

    public void setWadoTransferSyntaxUID(String wadoTransferSyntaxUID) {
        this.wadoTransferSyntaxUID = wadoTransferSyntaxUID;
    }

    public int getWadoCompression() {
        return wadoCompression;
    }

    public void setWadoCompression(int wadoCompression) {
        this.wadoCompression = wadoCompression > 100 ? 100 : wadoCompression < 0 ? 0 : wadoCompression;
    }

    public void setWadoCompression(String wadoCompression) {
        try {
            setWadoCompression(Integer.parseInt(wadoCompression));
        } catch (NumberFormatException e) {
            LOGGER.warn("Invalid compression value: {}", wadoCompression); //$NON-NLS-1$
        }
    }

    public void setSeriesDescription(String s) {
        seriesDescription = s == null ? "" : s; //$NON-NLS-1$
    }

    public void addSOPInstance(SOPInstance s) {
        if (s != null) {
            sopInstancesList.add(s);
        }
    }

    public String getModality() {
        return modality;
    }

    public void setModality(String modality) {
        this.modality = modality;
    }

    public String getThumbnail() {
        return thumbnail;
    }

    public void setThumbnail(String thumbnail) {
        this.thumbnail = thumbnail;
    }

    public List<SOPInstance> getSopInstancesList() {
        return sopInstancesList;
    }

    public void sortByInstanceNumber() {
        Collections.sort(sopInstancesList, new Comparator<SOPInstance>() {

            @Override
            public int compare(SOPInstance o1, SOPInstance o2) {
                int nubmer1 = 0;
                int nubmer2 = 0;
                try {
                    nubmer1 = Integer.parseInt(o1.getInstanceNumber());
                    nubmer2 = Integer.parseInt(o2.getInstanceNumber());
                } catch (NumberFormatException e) {
                    // Do nothing
                }

                return nubmer1 < nubmer2 ? -1 : (nubmer1 == nubmer2 ? 0 : 1);
            }
        });
    }

    @Override
    public String toXml() {
        StringBuilder result = new StringBuilder();
        if (seriesInstanceUID != null) {
            result.append("\n<"); //$NON-NLS-1$
            result.append(TagUtil.Level.SERIES);
            result.append(" "); //$NON-NLS-1$
            TagUtil.addXmlAttribute(Tag.SeriesInstanceUID, seriesInstanceUID, result);
            TagUtil.addXmlAttribute(Tag.SeriesDescription, seriesDescription, result);
            TagUtil.addXmlAttribute(Tag.SeriesNumber, seriesNumber, result);
            TagUtil.addXmlAttribute(Tag.Modality, modality, result);
            TagUtil.addXmlAttribute(TagUtil.DirectDownloadThumbnail, thumbnail, result);
            TagUtil.addXmlAttribute(TagUtil.WadoTransferSyntaxUID, wadoTransferSyntaxUID, result);
            TagUtil.addXmlAttribute(TagUtil.WadoCompressionRate,
                wadoCompression < 1 ? null : Integer.toString(wadoCompression), result);
            result.append(">"); //$NON-NLS-1$
            sortByInstanceNumber();
            for (SOPInstance s : sopInstancesList) {
                result.append(s.toXml());
            }
            result.append("\n</"); //$NON-NLS-1$
            result.append(TagUtil.Level.SERIES);
            result.append(">"); //$NON-NLS-1$
        }
        return result.toString();
    }

    public boolean isEmpty() {
        return sopInstancesList.isEmpty();
    }
}
