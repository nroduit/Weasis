/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.rt;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayNameGeneration(ReplaceUnderscores.class)
class DvhTest {

  private Dvh dvh;

  @BeforeEach
  void setUp() {
    dvh = new Dvh();
    dvh.setType("CUMULATIVE");
    dvh.setDoseUnit("CGY");
    dvh.setDvhVolumeUnit("CM3");
    // Cumulative DVH: 100% of the volume receives at least bin 0, decreasing afterwards.
    dvh.setDvhData(new double[] {100, 100, 90, 80, 60, 40, 20, 0});
    dvh.setDvhNumberOfBins(8);
  }

  @Test
  void simple_setters_and_getters_round_trip() {
    dvh.setReferencedRoiNumber(7);
    dvh.setDoseType("PHYSICAL");
    dvh.setDvhDoseScaling(1.5);
    dvh.setDvhSource(DataSource.PROVIDED);

    assertEquals(7, dvh.getReferencedRoiNumber());
    assertEquals("CUMULATIVE", dvh.getType());
    assertEquals("CGY", dvh.getDoseUnit());
    assertEquals("PHYSICAL", dvh.getDoseType());
    assertEquals(1.5, dvh.getDvhDoseScaling());
    assertEquals("CM3", dvh.getDvhVolumeUnit());
    assertEquals(8, dvh.getDvhNumberOfBins());
    assertEquals(DataSource.PROVIDED, dvh.getDvhSource());
    assertNull(dvh.getPlan());
  }

  @Test
  void plan_reference_can_be_attached() {
    Plan plan = org.mockito.Mockito.mock(Plan.class);
    dvh.setPlan(plan);
    assertSame(plan, dvh.getPlan());
  }

  @Test
  void calculate_dvh_min_returns_lower_edge_of_first_bin_below_max() {
    // First bin (i=2) where dvhData[i] < dvhData[0] -> (2*2-1)/2 = 1.5
    assertEquals(1.5, dvh.calculateDvhMin(), 1e-12);
  }

  @Test
  void calculate_dvh_min_returns_zero_when_data_never_drops() {
    Dvh flat = new Dvh();
    flat.setType("CUMULATIVE");
    flat.setDvhData(new double[] {100, 100, 100, 100});
    assertEquals(0.0, flat.calculateDvhMin());
  }

  @Test
  void other_dvh_data_is_differential_when_type_is_cumulative() {
    double[] expected = {0, 10, 10, 20, 20, 20, 20, 0};
    assertArrayEquals(expected, dvh.getOtherDvhData(), 1e-12);
  }

  @Test
  void other_dvh_data_is_zero_filled_when_type_is_not_cumulative() {
    Dvh diff = new Dvh();
    diff.setType("DIFFERENTIAL");
    diff.setDvhData(new double[] {1, 2, 3});
    assertArrayEquals(new double[] {0, 0, 0}, diff.getOtherDvhData());
  }

  @Test
  void calculate_dvh_max_returns_index_of_last_non_zero_differential_bin_plus_one() {
    // dDvh = [0,10,10,20,20,20,20,0]; last non-zero at index 6 -> 7
    assertEquals(7.0, dvh.calculateDvhMax(), 1e-12);
  }

  @Test
  void calculate_dvh_mean_uses_weighted_differential_dvh() {
    // sum(i * dDvh[i]) / dvhData[0] = 390 / 100 = 3.9
    assertEquals(3.9, dvh.calculateDvhMean(), 1e-12);
  }

  @Test
  void cached_min_max_mean_are_lazily_computed_and_remembered() {
    assertEquals(1.5, dvh.getDvhMinimumDose(), 1e-12);
    assertEquals(7.0, dvh.getDvhMaximumDose(), 1e-12);
    assertEquals(3.9, dvh.getDvhMeanDose(), 1e-12);
    // Subsequent calls return the cached value (still equal).
    assertEquals(1.5, dvh.getDvhMinimumDose(), 1e-12);
  }

  @Test
  void explicit_setters_override_lazy_computation() {
    dvh.setDvhMinimumDose(10);
    dvh.setDvhMaximumDose(50);
    dvh.setDvhMeanDose(25);
    assertEquals(10.0, dvh.getDvhMinimumDose());
    assertEquals(50.0, dvh.getDvhMaximumDose());
    assertEquals(25.0, dvh.getDvhMeanDose());
  }

  @Test
  void dvh_data_is_returned_as_is() {
    assertNotNull(dvh.getDvhData());
    assertEquals(8, dvh.getDvhData().length);
  }

  @Nested
  @DisplayNameGeneration(ReplaceUnderscores.class)
  class DoseUnitConversion {

    @Test
    void cgy_unit_returns_value_unchanged() {
      dvh.setDoseUnit("CGY");
      dvh.setDvhMinimumDose(2);
      dvh.setDvhMaximumDose(7);
      dvh.setDvhMeanDose(4);
      assertEquals(2, dvh.getDvhMinimumDoseCGy());
      assertEquals(7, dvh.getDvhMaximumDoseCGy());
      assertEquals(4, dvh.getDvhMeanDoseCGy());
    }

    @Test
    void gy_unit_is_scaled_to_cgy() {
      dvh.setDoseUnit("GY");
      dvh.setDvhMinimumDose(2);
      dvh.setDvhMaximumDose(7);
      dvh.setDvhMeanDose(4);
      assertEquals(200, dvh.getDvhMinimumDoseCGy());
      assertEquals(700, dvh.getDvhMaximumDoseCGy());
      assertEquals(400, dvh.getDvhMeanDoseCGy());
    }
  }
}
