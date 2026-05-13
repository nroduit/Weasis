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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.awt.Color;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;

@DisplayNameGeneration(ReplaceUnderscores.class)
class StructRegionTest {

  @Test
  void simple_setters_and_getters_round_trip() {
    StructRegion r = new StructRegion(1, "Lung", Color.RED);
    r.setObservationNumber(11);
    r.setRoiObservationLabel("Right Lung");
    r.setThickness(2.5);
    Dvh dvh = new Dvh();
    r.setDvh(dvh);

    assertEquals(11, r.getObservationNumber());
    assertEquals("Right Lung", r.getRoiObservationLabel());
    assertEquals(2.5, r.getThickness());
    assertSame(dvh, r.getDvh());
  }

  @Test
  void rt_roi_interpreted_type_external_disables_fill() {
    StructRegion r = new StructRegion(1, "Body", Color.BLACK);
    r.setRtRoiInterpretedType("EXTERNAL");
    assertEquals("EXTERNAL", r.getRtRoiInterpretedType());
    assertFalse(r.isFilled());

    r.setRtRoiInterpretedType("ORGAN");
    assertTrue(r.isFilled());
  }

  @Test
  void volume_is_zero_for_null_or_empty_planes_and_marked_calculated() {
    StructRegion empty = new StructRegion(1, "Empty", Color.WHITE);
    assertNull(empty.getVolumeSource());
    assertEquals(0.0, empty.getVolume());
    assertEquals(DataSource.CALCULATED, empty.getVolumeSource());

    StructRegion withMap = new StructRegion(2, "EmptyMap", Color.WHITE);
    withMap.setPlanes(new HashMap<>());
    assertEquals(0.0, withMap.getVolume());
  }

  @Test
  void set_volume_marks_source_as_provided() {
    StructRegion r = new StructRegion(1, "Liver", Color.GREEN);
    r.setVolume(123.4);
    assertEquals(123.4, r.getVolume());
    assertEquals(DataSource.PROVIDED, r.getVolumeSource());
  }

  @Test
  void calculate_largest_contour_returns_index_and_area_of_max() {
    StructRegion r = new StructRegion(1, "ROI", Color.GRAY);
    StructContour c1 = mock(StructContour.class);
    StructContour c2 = mock(StructContour.class);
    StructContour c3 = mock(StructContour.class);
    when(c1.getArea()).thenReturn(10.0);
    when(c2.getArea()).thenReturn(40.0);
    when(c3.getArea()).thenReturn(25.0);

    LargestContour largest = r.calculateLargestContour(List.of(c1, c2, c3));
    assertEquals(1, largest.index());
    assertEquals(40.0, largest.area());
  }

  @Test
  void calculate_largest_contour_returns_zero_for_empty_list() {
    StructRegion r = new StructRegion(1, "ROI", Color.GRAY);
    LargestContour largest = r.calculateLargestContour(List.of());
    assertEquals(0, largest.index());
    assertEquals(0.0, largest.area());
  }

  @Test
  void sort_label_combines_interpreted_type_and_label_when_set() {
    StructRegion r = new StructRegion(1, "Lung", Color.RED);
    assertEquals("Lung", r.getSortLabel());
    r.setRtRoiInterpretedType("ORGAN");
    assertEquals("ORGANLung", r.getSortLabel());
  }

  @Test
  void compare_to_uses_natural_collator_on_sort_label() {
    StructRegion a = new StructRegion(1, "Aorta", Color.RED);
    StructRegion b = new StructRegion(2, "Brain", Color.RED);
    assertTrue(a.compareTo(b) < 0);
    assertTrue(b.compareTo(a) > 0);
    assertEquals(0, a.compareTo(new StructRegion(3, "Aorta", Color.RED)));
  }

  @Test
  void sort_orders_lists_by_their_first_region_with_empty_lists_last() {
    StructRegion aorta = new StructRegion(1, "Aorta", Color.RED);
    StructRegion brain = new StructRegion(2, "Brain", Color.RED);
    Map<String, List<StructRegion>> grouped = new HashMap<>();
    grouped.put("b", List.of(brain));
    grouped.put("a", List.of(aorta));
    grouped.put("empty", List.of());

    List<List<StructRegion>> sorted = StructRegion.sort(grouped.values());
    assertEquals("Aorta", sorted.get(0).getFirst().getLabel());
    assertEquals("Brain", sorted.get(1).getFirst().getLabel());
    assertTrue(sorted.get(2).isEmpty());
  }

  @Test
  void planes_round_trip() {
    StructRegion r = new StructRegion(1, "Heart", Color.PINK);
    Map<KeyDouble, List<StructContour>> planes = new HashMap<>();
    planes.put(new KeyDouble(1.0), List.of());
    r.setPlanes(planes);
    assertSame(planes, r.getPlanes());
  }
}
