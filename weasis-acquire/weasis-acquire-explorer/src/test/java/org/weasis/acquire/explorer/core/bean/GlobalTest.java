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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collections;
import org.assertj.core.api.Assertions;
import org.assertj.core.data.MapEntry;
import org.dcm4che3.data.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.weasis.core.api.media.data.TagW;
import org.weasis.dicom.codec.TagD;

class GlobalTest extends GlobalHelper {

  @Test
  void testInitWithEmptyTaggable() {
    Global global = new Global();

    Mockito.when(taggable.getTagEntrySetIterator())
        .thenReturn(Collections.EMPTY_MAP.entrySet().iterator());
    // Method to test
    global.init(taggable);

    // Tests
    assertThat(global.containTagKey(TagD.get(Tag.StudyInstanceUID))).isTrue();
    assertThat(global.getTagValue(TagD.get(Tag.StudyInstanceUID))).isNotNull();
  }

  @Test
  void testInit() {
    Global global = new Global();
    // Method to test
    DefaultTaggable tags = new DefaultTaggable();
    tags.setTag(GlobalTag.patientId.tagW, GlobalTag.patientId.value);
    tags.setTag(GlobalTag.patientName.tagW, GlobalTag.patientName.value);
    tags.setTag(GlobalTag.issuerOfPatientId.tagW, GlobalTag.issuerOfPatientId.value);
    tags.setTag(GlobalTag.patientBirthDate.tagW, GlobalTag.patientBirthDate.value);
    tags.setTag(GlobalTag.patientSex.tagW, GlobalTag.patientSex.value);
    tags.setTag(GlobalTag.studyDate.tagW, GlobalTag.studyDate.value);
    tags.setTag(GlobalTag.modality.tagW, GlobalTag.modality.value);
    global.init(tags);

    // Tests
    TagW studyTag = TagD.get(Tag.StudyInstanceUID);
    assertThat(global.containTagKey(studyTag)).isTrue();
    global.setTag(studyTag, studyInstanceUIDValue);
    assertThat(global.getTagEntrySet())
        .containsExactlyInAnyOrder(
            Assertions.entry(studyTag, studyInstanceUIDValue),
            entry(GlobalTag.patientId),
            entry(GlobalTag.patientName),
            entry(GlobalTag.issuerOfPatientId),
            entry(GlobalTag.patientBirthDate),
            entry(GlobalTag.patientSex),
            entry(GlobalTag.studyDate),
            entry(GlobalTag.modality));
  }

  private static MapEntry<TagW, Object> entry(GlobalTag tag) {
    return Assertions.entry(tag.tagW, tag.value);
  }
}
