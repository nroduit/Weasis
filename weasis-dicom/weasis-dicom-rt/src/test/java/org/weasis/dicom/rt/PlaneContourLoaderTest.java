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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.weasis.core.ui.model.graphic.imp.seg.SegContour;

@DisplayNameGeneration(ReplaceUnderscores.class)
class PlaneContourLoaderTest {

  @Test
  void newly_created_loader_has_no_contours() {
    PlaneContourLoader loader = new PlaneContourLoader();
    assertNotNull(loader.getLazyContours());
    assertTrue(loader.getLazyContours().isEmpty());
  }

  @Test
  void add_contour_appends_a_single_contour_in_insertion_order() {
    PlaneContourLoader loader = new PlaneContourLoader();
    SegContour first = mock(SegContour.class);
    SegContour second = mock(SegContour.class);
    loader.addContour(first);
    loader.addContour(second);
    List<SegContour> ordered = loader.getLazyContours().stream().toList();
    assertEquals(2, ordered.size());
    assertSame(first, ordered.get(0));
    assertSame(second, ordered.get(1));
  }

  @Test
  void add_contour_silently_ignores_null() {
    PlaneContourLoader loader = new PlaneContourLoader();
    loader.addContour(null);
    assertTrue(loader.getLazyContours().isEmpty());
  }

  @Test
  void add_contours_appends_all_elements_and_deduplicates() {
    PlaneContourLoader loader = new PlaneContourLoader();
    SegContour a = mock(SegContour.class);
    SegContour b = mock(SegContour.class);
    loader.addContour(a);
    loader.addContours(Set.of(a, b));
    assertEquals(2, loader.getLazyContours().size());
    assertTrue(loader.getLazyContours().contains(a));
    assertTrue(loader.getLazyContours().contains(b));
  }

  @Test
  void add_contours_silently_ignores_null_collection() {
    PlaneContourLoader loader = new PlaneContourLoader();
    loader.addContours(null);
    assertFalse(loader.getLazyContours().iterator().hasNext());
  }
}
