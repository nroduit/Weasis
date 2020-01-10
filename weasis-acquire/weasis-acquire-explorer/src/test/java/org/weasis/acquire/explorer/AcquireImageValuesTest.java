/*******************************************************************************
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.weasis.acquire.explorer;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Test;
import org.weasis.core.api.image.util.Unit;

public class AcquireImageValuesTest {
    private AcquireImageValues a1;

    @Test
    public void testConstructor() {
        assertThat(a1).isNull();

        a1 = new AcquireImageValues();

        assertThat(a1).isNotNull();
        assertThat(a1.getBrightness()).isEqualTo(0);
        assertThat(a1.getCalibrationRatio()).isEqualTo(1.0);
        assertThat(a1.getCalibrationUnit()).isEqualTo(Unit.PIXEL);
        assertThat(a1.getContrast()).isEqualTo(100);
        assertThat(a1.getCropZone()).isNull();
        assertThat(a1.getFullRotation()).isEqualTo(0);
        assertThat(a1.getLayerOffset()).isNull();
        assertThat(a1.getOrientation()).isEqualTo(0);
        assertThat(a1.getRotation()).isEqualTo(0);
        assertThat(a1.isAutoLevel()).isFalse();
    }

}