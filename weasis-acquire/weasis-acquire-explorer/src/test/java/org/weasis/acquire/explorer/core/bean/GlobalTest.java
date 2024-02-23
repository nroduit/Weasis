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

import static org.junit.jupiter.api.Assertions.*;

import java.util.AbstractMap;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import org.dcm4che3.data.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.weasis.core.api.media.data.TagW;
import org.weasis.dicom.codec.TagD;

class GlobalTest extends GlobalHelper {

  @Test
  void testInitWithEmptyTaggable() {
    Global global = new Global();
    Mockito.when(taggable.getTagEntrySetIterator()).thenReturn(Collections.emptyIterator());
    // Method to test
    global.init(taggable);

    // Tests
    assertTrue(global.containTagKey(TagD.get(Tag.StudyInstanceUID)));
    assertNotNull(global.getTagValue(TagD.get(Tag.StudyInstanceUID)));
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
    assertTrue(global.containTagKey(studyTag));
    global.setTag(studyTag, studyInstanceUIDValue);
    assertTrue(
        global
            .getTagEntrySet()
            .containsAll(
                Arrays.asList(
                    entry(studyTag, studyInstanceUIDValue),
                    entry(GlobalTag.patientId),
                    entry(GlobalTag.patientName),
                    entry(GlobalTag.issuerOfPatientId),
                    entry(GlobalTag.patientBirthDate),
                    entry(GlobalTag.patientSex),
                    entry(GlobalTag.studyDate),
                    entry(GlobalTag.modality))));
  }

  private static Map.Entry<TagW, Object> entry(GlobalTag tag) {
    return entry(tag.tagW, tag.value);
  }

  private static Map.Entry<TagW, Object> entry(TagW tag, Object value) {
    return new AbstractMap.SimpleEntry<>(tag, value);
  }
}
