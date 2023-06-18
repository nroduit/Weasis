/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.acquire.explorer.core.bean;

import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Locale;
import org.dcm4che3.data.Tag;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.api.media.data.TagW.TagType;
import org.weasis.core.api.media.data.Taggable;
import org.weasis.core.api.util.LocalUtil;
import org.weasis.dicom.codec.TagD;

@ExtendWith(MockitoExtension.class)
public class GlobalHelper {
  public static final DateTimeFormatter dateformat = DateTimeFormatter.ofPattern("yyyy-MM-dd");
  protected static String patientIdValue = "12345";
  protected static String patientNameValue = "John DOE"; // NON-NLS
  protected static String issuerOfPatientIdValue = "6789";
  protected static String patientBirthDateValue = "19850216";
  protected static String patientSexValue = "M"; // NON-NLS
  protected static String studyDateValue = "20160603";
  protected static String studyInstanceUIDValue = "2.25.35.13108031698769009477890994130583367923";
  protected static String modalityValue = "CR";

  @Mock protected static Taggable taggable;

  @Mock protected static TagW patientIdW;
  @Mock protected static TagW patientNameW;
  @Mock protected static TagW issuerOfPatientIdW;
  @Mock protected static TagW patientBirthDateW;
  @Mock protected static TagW patientSexW;
  @Mock protected static TagW studyDateW;
  @Mock protected static TagW studyInstanceUIDW;
  @Mock protected static TagW modalityW;

  protected enum GlobalTag {
    patientId(Tag.PatientID, patientIdW, TagType.STRING, patientIdValue),
    patientName(Tag.PatientName, patientNameW, TagType.STRING, patientNameValue),
    issuerOfPatientId(
        Tag.IssuerOfPatientID, issuerOfPatientIdW, TagType.STRING, issuerOfPatientIdValue),
    patientBirthDate(Tag.PatientBirthDate, patientBirthDateW, TagType.DATE, patientBirthDateValue),
    patientSex(Tag.PatientSex, patientSexW, TagType.DICOM_SEX, patientSexValue),
    studyDate(Tag.StudyDate, studyDateW, TagType.DATE, studyDateValue),
    studyInstanceUID(
        Tag.StudyInstanceUID, studyInstanceUIDW, TagType.STRING, studyInstanceUIDValue),
    modality(Tag.Modality, modalityW, TagType.STRING, modalityValue);

    public final int tagId;
    public TagW tagW;
    public final TagType type;
    public final String value;

    GlobalTag(int tagId, TagW tagW, TagType type, String value) {
      this.tagId = tagId;
      this.tagW = tagW;
      this.type = type;
      this.value = value;
    }

    public void prepareMock() {
      tagW = Mockito.mock(TagW.class);
      Mockito.when(TagD.get(tagId)).thenReturn(tagW);
      Mockito.when(tagW.getType()).thenReturn(type);
      Mockito.when(tagW.getKeyword()).thenReturn(name());
    }
  }

  @BeforeAll
  static void setUp() {
    taggable = Mockito.mock(Taggable.class);

    Mockito.mockStatic(LocalUtil.class);
    Mockito.when(LocalUtil.getDateFormatter()).thenReturn(dateformat);
    Mockito.when(LocalUtil.getDateTimeFormatter()).thenReturn(dateformat);
    Mockito.when(LocalUtil.getLocaleFormat()).thenReturn(Locale.ENGLISH);

    Mockito.mockStatic(TagD.class);
    Arrays.stream(GlobalTag.values()).forEach(GlobalTag::prepareMock);
  }
}
