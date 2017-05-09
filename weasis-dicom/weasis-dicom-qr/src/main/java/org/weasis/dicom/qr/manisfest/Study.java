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
import org.weasis.dicom.qr.manisfest.xml.TagUtil;
import org.weasis.dicom.qr.manisfest.xml.XmlDescription;
import org.weasis.dicom.util.StringUtil;

public class Study implements XmlDescription {

    private final String studyInstanceUID;
    private String studyID = null;
    private String studyDescription = null;
    private String studyDate = null;
    private String studyTime = null;
    private String accessionNumber = null;
    private String referringPhysicianName = null;
    private final List<Series> seriesList;

    public Study(String studyInstanceUID) {
        if (studyInstanceUID == null) {
            throw new IllegalArgumentException("studyInstanceUID cannot be null!");
        }
        this.studyInstanceUID = studyInstanceUID;
        seriesList = new ArrayList<>();
    }

    public String getStudyInstanceUID() {
        return studyInstanceUID;
    }

    public String getStudyDescription() {
        return studyDescription;
    }

    public String getStudyDate() {
        return studyDate;
    }

    public String getStudyID() {
        return studyID;
    }

    public void setStudyID(String studyID) {
        this.studyID = studyID;
    }

    public String getStudyTime() {
        return studyTime;
    }

    public void setStudyTime(String studyTime) {
        this.studyTime = studyTime;
    }

    public String getReferringPhysicianName() {
        return referringPhysicianName;
    }

    public void setReferringPhysicianName(String referringPhysicianName) {
        this.referringPhysicianName = referringPhysicianName;
    }

    public void setStudyDescription(String studyDesc) {
        this.studyDescription = studyDesc;
    }

    public void setStudyDate(String studyDate) {
        this.studyDate = studyDate;
    }

    public String getAccessionNumber() {
        return accessionNumber;
    }

    public void setAccessionNumber(String accessionNumber) {
        this.accessionNumber = accessionNumber;
    }

    public void addSeries(Series s) {
        if (!seriesList.contains(s)) {
            seriesList.add(s);
        }
    }

    @Override
    public String toXml() {
        StringBuilder result = new StringBuilder();
        if (studyInstanceUID != null) {
            result.append("\n<");
            result.append(TagUtil.Level.STUDY);
            result.append(" ");
            TagUtil.addXmlAttribute(Tag.StudyInstanceUID, studyInstanceUID, result);
            TagUtil.addXmlAttribute(Tag.StudyDescription, studyDescription, result);
            TagUtil.addXmlAttribute(Tag.StudyDate, studyDate, result);
            TagUtil.addXmlAttribute(Tag.StudyTime, studyTime, result);
            TagUtil.addXmlAttribute(Tag.AccessionNumber, accessionNumber, result);
            TagUtil.addXmlAttribute(Tag.StudyID, studyID, result);
            TagUtil.addXmlAttribute(Tag.ReferringPhysicianName, referringPhysicianName, result);
            result.append(">");
            Collections.sort(seriesList, new Comparator<Series>() {

                @Override
                public int compare(Series o1, Series o2) {
                    int nubmer1 = 0;
                    int nubmer2 = 0;
                    try {
                        if (StringUtil.hasText(o1.getSeriesNumber())) {
                            nubmer1 = Integer.parseInt(o1.getSeriesNumber());
                        }
                        if (StringUtil.hasText(o2.getSeriesNumber())) {
                            nubmer2 = Integer.parseInt(o2.getSeriesNumber());
                        }
                    } catch (NumberFormatException e) {
                        // Do nothing
                    }
                    int rep = nubmer1 < nubmer2 ? -1 : (nubmer1 == nubmer2 ? 0 : 1);
                    if (rep != 0) {
                        return rep;
                    }
                    return o1.getSeriesInstanceUID().compareTo(o2.getSeriesInstanceUID());
                }
            });
            for (Series s : seriesList) {
                result.append(s.toXml());
            }

            result.append("\n</");
            result.append(TagUtil.Level.STUDY);
            result.append(">");
        }
        return result.toString();
    }

    public boolean isEmpty() {
        for (Series s : seriesList) {
            if (!s.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    public Series getSeries(String uid) {
        for (Series s : seriesList) {
            if (s.getSeriesInstanceUID().equals(uid)) {
                return s;
            }
        }
        return null;
    }

    public List<Series> getSeriesList() {
        return seriesList;
    }

}
