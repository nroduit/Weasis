/*******************************************************************************
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.weasis.acquire.explorer.core.bean;

import static org.assertj.core.api.Assertions.assertThat;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;

import org.assertj.core.api.Assertions;
import org.assertj.core.data.MapEntry;
import org.dcm4che3.data.Tag;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.core.classloader.annotations.SuppressStaticInitializationFor;
import org.powermock.modules.junit4.PowerMockRunner;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.api.media.data.TagW.TagType;
import org.weasis.core.api.media.data.Tagable;
import org.weasis.core.api.util.LocalUtil;
import org.weasis.dicom.codec.TagD;

@RunWith(PowerMockRunner.class)
@PrepareForTest({ TagD.class, LocalUtil.class })
@SuppressStaticInitializationFor("org.weasis.dicom.codec.TagD")
public class GlobalTest extends GlobalHelper {
    private Global global;

    @Before
    public void setUp() throws Exception {
        global = null;

        tagable = PowerMockito.mock(Tagable.class);
        Map<TagW, Object> map = new HashMap<>();
        Iterator<Entry<TagW, Object>> entrySet = map.entrySet().iterator();
        PowerMockito.when(tagable.getTagEntrySetIterator()).thenReturn(entrySet);

        PowerMockito.mockStatic(LocalUtil.class);
        PowerMockito.when(LocalUtil.getDateFormatter()).thenReturn(dateformat);
        PowerMockito.when(LocalUtil.getLocaleFormat()).thenReturn(Locale.ENGLISH);

        PowerMockito.mockStatic(TagD.class);
        Arrays.stream(GlobalTag.values()).forEach(e -> e.prepareMock());
    }

    @Test
    public void testGlobal() {
        assertThat(global).isNull();
        global = new Global();

        assertThat(global).isNotNull();
    }

    @Test
    public void testInitWithEmptyTagable() throws Exception {
        global = new Global();

        // Method to test
        global.init(tagable);

        // Tests
        assertThat(global.containTagKey(TagD.get(Tag.StudyInstanceUID))).isTrue();
        assertThat(global.getTagValue(TagD.get(Tag.StudyInstanceUID))).isNotNull();
    }

    @Test
    public void testInit() throws Exception {
        global = new Global();
        // Method to test
        DefaultTagable tags = new DefaultTagable();
        tags.setTag(GlobalTag.patientId.tagW, GlobalTag.patientId.value);
        tags.setTag(GlobalTag.patientName.tagW, GlobalTag.patientName.value);
        tags.setTag(GlobalTag.issuerOfPatientId.tagW, GlobalTag.issuerOfPatientId.value);
        tags.setTag(GlobalTag.patientBirthDate.tagW, GlobalTag.patientBirthDate.value);
        tags.setTag(GlobalTag.patientSex.tagW, GlobalTag.patientSex.value);
        tags.setTag(GlobalTag.studyDate.tagW, GlobalTag.studyDate.value);
        tags.setTag(GlobalTag.modality.tagW, GlobalTag.modality.value);
        global.init(tags);

        // Tests
        assertThat(global.getTagEntrySet()).hasSize(8);
    }

    private MapEntry<TagW, Object> entry(GlobalTag tag) throws ParseException {
        Object value;

        if (tag.type.equals(TagType.DATE)) {
            value = new SimpleDateFormat("yyyyMMdd", LocalUtil.getLocaleFormat()).parse(tag.value); //$NON-NLS-1$
        } else {
            value = tag.value;
        }
        return Assertions.entry(tag.tagW, value);
    }
}
