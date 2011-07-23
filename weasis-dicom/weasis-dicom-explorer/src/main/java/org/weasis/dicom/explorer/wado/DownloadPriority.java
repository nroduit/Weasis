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
package org.weasis.dicom.explorer.wado;

import java.util.Date;
import java.util.concurrent.atomic.AtomicInteger;

import org.weasis.dicom.explorer.Messages;

public class DownloadPriority {

    public static final AtomicInteger COUNTER = new AtomicInteger(Integer.MAX_VALUE - 1);
    private final String patientName;
    private final String studyInstanceUID;
    private final Date studyDate;
    private final Integer seriesNumber;
    private Integer priority;

    public DownloadPriority(String patientName, String studyInstanceUID, Date studyDate, Integer seriesNumber) {
        this.patientName = patientName == null ? "" : patientName; //$NON-NLS-1$
        this.studyInstanceUID = studyInstanceUID == null ? "" : studyInstanceUID; //$NON-NLS-1$
        this.studyDate = studyDate;
        this.seriesNumber = seriesNumber == null ? 0 : seriesNumber;
        priority = Integer.MAX_VALUE;
    }

    public String getPatientName() {
        return patientName;
    }

    public Date getStudyDate() {
        return studyDate;
    }

    public Integer getSeriesNumber() {
        return seriesNumber;
    }

    public String getStudyInstanceUID() {
        return studyInstanceUID;
    }

    public Integer getPriority() {
        return priority;
    }

    public void setPriority(Integer priority) {
        this.priority = priority == null ? Integer.MAX_VALUE : priority;
    }

}
