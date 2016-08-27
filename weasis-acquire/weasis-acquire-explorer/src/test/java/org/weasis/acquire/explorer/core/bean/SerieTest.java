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
public class SerieTest {

    private static final LocalDateTime today = LocalDateTime.of(2016, 5, 12, 14, 25);

    private Serie s1, s2, s3;

    @Mock
    TagW modality;
    
    @Before
    public void setUp() {
        PowerMockito.mockStatic(TagD.class);
        PowerMockito.when(TagD.get(Tag.Modality)).thenReturn(modality);
        
        s1 = new Serie();
        s2 = new Serie(today);
        s3 = new Serie("test serie 3");
    }

    @Test
    public void testToString() {
        assertThat(s1.toString()).isEqualTo("Other");
        assertThat(s2.toString()).isEqualTo(LocalUtil.getDateTimeFormatter().format(today));
        assertThat(s3.toString()).isEqualTo("test serie 3");
    }

    @Test
    public void testGetters() {
        assertThat(s1.getType()).isEqualTo(Serie.Type.NONE);
        assertThat(s2.getType()).isEqualTo(Serie.Type.DATE);
        assertThat(s3.getType()).isEqualTo(Serie.Type.NAME);
    }

    @Test
    public void testSort() {
        Serie s1 = new Serie();
        Serie s2 = new Serie(today);
        Serie s3 = new Serie("serie3");
        assetSorted(new Serie[] { s3, s2, s1 }, new Serie[] { s1, s2, s3 });
        assetSorted(new Serie[] { s2, s3, s1 }, new Serie[] { s1, s2, s3 });

        Serie s4 = new Serie(today.minusDays(1));
        assetSorted(new Serie[] { s3, s2, s1, s4 }, new Serie[] { s1, s4, s2, s3 });

        Serie s5 = new Serie("serie2");
        assetSorted(new Serie[] { s3, s2, s1, s4, s5 }, new Serie[] { s1, s4, s2, s5, s3 });

        Serie s6 = new Serie("2015");
        assetSorted(new Serie[] { s3, s2, s1, s4, s5, s6 }, new Serie[] { s1, s4, s2, s6, s5, s3 });
    }

    private void assetSorted(Serie[] input, Serie[] expected) {
        assertThat(Arrays.stream(input).sorted().toArray(Serie[]::new)).isEqualTo(expected);
    }

}
