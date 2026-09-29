/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.acquire.explorer.gui.central.meta.panel;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.weasis.dicom.ref.AnatomicBuilder.Category;
import org.weasis.dicom.ref.AnatomicItem;
import org.weasis.dicom.ref.AnatomicRegion;
import org.weasis.dicom.ref.BodyPart;
import org.weasis.dicom.ref.RegionGroup;

class AnatomicRegionViewTest {

  @Test
  void the_area_filters_the_regions_of_the_category_and_keeps_the_selection() {
    AnatomicRegionView view =
        new AnatomicRegionView(
            new AnatomicRegion(Category.ALL_REGIONS, BodyPart.BRAIN, null), true);
    int all = view.offeredRegions().size();

    view.selectArea(RegionGroup.HEAD);
    List<AnatomicItem> head = view.offeredRegions();

    assertAll(
        () -> assertTrue(head.contains(BodyPart.BRAIN)),
        () -> assertFalse(head.contains(BodyPart.LIVER)),
        () -> assertTrue(head.size() < all),
        () -> assertTrue(head.stream().allMatch(i -> new AnatomicRegion(i).isIn(RegionGroup.HEAD))),
        () -> assertEquals(BodyPart.BRAIN, view.getSelectedAnatomicItem(), "selection kept"));

    view.selectArea(null);
    assertEquals(all, view.offeredRegions().size());
  }
}
