/*******************************************************************************
 * Copyright (c) 2009-2018 Weasis Team and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 *     Nicolas Roduit - initial API and implementation
 *******************************************************************************/
package org.weasis.acquire.explorer.core.bean;

import static org.mockito.ArgumentMatchers.eq;
import static org.powermock.api.mockito.PowerMockito.mock;
import static org.powermock.api.mockito.PowerMockito.when;

import org.dcm4che3.data.Tag;
import org.mockito.Mock;
import org.weasis.acquire.test.utils.MockHelper;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.api.media.data.TagW.TagType;
import org.weasis.core.api.media.data.Tagable;
import org.weasis.dicom.codec.TagD;

public class GlobalHelper extends MockHelper {
    protected static String patientIdValue = "12345"; //$NON-NLS-1$
    protected static String patientNameValue = "John DOE"; //$NON-NLS-1$
    protected static String issuerOfPatientIdValue = "6789"; //$NON-NLS-1$
    protected static String patientBirthDateValue = "19850216"; //$NON-NLS-1$
    protected static String patientSexValue = "M"; //$NON-NLS-1$
    protected static String studyDateValue = "20160603"; //$NON-NLS-1$
    protected static String studyInstanceUIDValue = "2.25.35.13108031698769009477890994130583367923"; //$NON-NLS-1$
    protected static String modalityValue = "CR"; //$NON-NLS-1$

    @Mock
    protected static Tagable tagable;

    @Mock
    protected static TagW patientIdW;
    @Mock
    protected static TagW patientNameW;
    @Mock
    protected static TagW issuerOfPatientIdW;
    @Mock
    protected static TagW patientBirthDateW;
    @Mock
    protected static TagW patientSexW;
    @Mock
    protected static TagW studyDateW;
    @Mock
    protected static TagW studyInstanceUIDW;
    @Mock
    protected static TagW modalityW;

    protected enum GlobalTag {
        patientId(Tag.PatientID, patientIdW, TagType.STRING, patientIdValue),
        patientName(Tag.PatientName, patientNameW, TagType.STRING, patientNameValue),
        issuerOfPatientId(Tag.IssuerOfPatientID, issuerOfPatientIdW, TagType.STRING, issuerOfPatientIdValue),
        patientBirthDate(Tag.PatientBirthDate, patientBirthDateW, TagType.DATE, patientBirthDateValue),
        patientSex(Tag.PatientSex, patientSexW, TagType.DICOM_SEX, patientSexValue),
        studyDate(Tag.StudyDate, studyDateW, TagType.DATE, studyDateValue),
        studyinstanceUID(Tag.StudyInstanceUID, studyInstanceUIDW, TagType.STRING, studyInstanceUIDValue),
        modality(Tag.Modality, modalityW, TagType.STRING, modalityValue);

        public int tagId;
        public TagW tagW;
        public TagType type;
        public String value;

        private GlobalTag(int tagId, TagW tagW, TagType type, String value) {
            this.tagId = tagId;
            this.tagW = tagW;
            this.type = type;
            this.value = value;
        }

        public void prepareMock() {
            tagW = mock(TagW.class);

            when(TagD.get(eq(tagId))).thenReturn(tagW);
            when(tagW.getType()).thenReturn(type);
            when(tagW.getKeyword()).thenReturn(name());
        }

    }
}
