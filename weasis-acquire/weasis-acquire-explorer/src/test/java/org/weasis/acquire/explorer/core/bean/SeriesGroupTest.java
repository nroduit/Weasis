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

import java.time.LocalDateTime;
import java.util.Arrays;

import org.dcm4che3.data.Tag;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.core.classloader.annotations.SuppressStaticInitializationFor;
import org.powermock.modules.junit4.PowerMockRunner;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.api.util.LocalUtil;
import org.weasis.dicom.codec.TagD;

@RunWith(PowerMockRunner.class)
@PrepareForTest({ TagD.class, LocalUtil.class })
@SuppressStaticInitializationFor("org.weasis.dicom.codec.TagD")
public class SeriesGroupTest {

    private static final LocalDateTime today = LocalDateTime.of(2016, 5, 12, 14, 25);

    private SeriesGroup s1, s2, s3;

    @Mock
    TagW modality;

    @Before
    public void setUp() {
        PowerMockito.mockStatic(TagD.class);
        PowerMockito.when(TagD.get(Tag.Modality)).thenReturn(modality);

        s1 = new SeriesGroup();
        s2 = new SeriesGroup(today);
        s3 = new SeriesGroup("test serie 3"); //$NON-NLS-1$
    }

    @Test
    public void testToString() {
        assertThat(s1.toString()).isEqualTo("Other"); //$NON-NLS-1$
        assertThat(s2.toString()).isEqualTo(LocalUtil.getDateTimeFormatter().format(today));
        assertThat(s3.toString()).isEqualTo("test serie 3"); //$NON-NLS-1$
    }

    @Test
    public void testGetters() {
        assertThat(s1.getType()).isEqualTo(SeriesGroup.Type.NONE);
        assertThat(s2.getType()).isEqualTo(SeriesGroup.Type.DATE);
        assertThat(s3.getType()).isEqualTo(SeriesGroup.Type.NAME);
    }

    @Test
    public void testSort() {
        SeriesGroup s1 = new SeriesGroup();
        SeriesGroup s2 = new SeriesGroup(today);
        SeriesGroup s3 = new SeriesGroup("serie3"); //$NON-NLS-1$
        assetSorted(new SeriesGroup[] { s3, s2, s1 }, new SeriesGroup[] { s1, s2, s3 });
        assetSorted(new SeriesGroup[] { s2, s3, s1 }, new SeriesGroup[] { s1, s2, s3 });

        SeriesGroup s4 = new SeriesGroup(today.minusDays(1));
        assetSorted(new SeriesGroup[] { s3, s2, s1, s4 }, new SeriesGroup[] { s1, s4, s2, s3 });

        SeriesGroup s5 = new SeriesGroup("serie2"); //$NON-NLS-1$
        assetSorted(new SeriesGroup[] { s3, s2, s1, s4, s5 }, new SeriesGroup[] { s1, s4, s2, s5, s3 });

        SeriesGroup s6 = new SeriesGroup("2015"); //$NON-NLS-1$
        assetSorted(new SeriesGroup[] { s3, s2, s1, s4, s5, s6 }, new SeriesGroup[] { s1, s4, s2, s6, s5, s3 });
    }

    private void assetSorted(SeriesGroup[] input, SeriesGroup[] expected) {
        assertThat(Arrays.stream(input).sorted().toArray(SeriesGroup[]::new)).isEqualTo(expected);
    }

}
