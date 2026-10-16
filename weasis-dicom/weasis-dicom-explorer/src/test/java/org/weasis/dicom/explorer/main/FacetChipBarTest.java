/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.explorer.main;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.AbstractButton;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.weasis.dicom.codec.ObjectKind;
import org.weasis.dicom.explorer.items.DownloadState;
import org.weasis.dicom.explorer.items.FacetCounts;
import org.weasis.dicom.explorer.items.InterestSource;

@DisplayNameGeneration(ReplaceUnderscores.class)
class FacetChipBarTest {

  private static final FacetCounts COUNTS =
      new FacetCounts(
          3,
          Map.of("CT", 2, "MR", 1),
          Map.of(ObjectKind.KEY_OBJECT, 1),
          1,
          Map.of(InterestSource.KEY_OBJECT, 1),
          Map.of(DownloadState.COMPLETE, 2, DownloadState.PENDING, 1));

  @Test
  void menu_lists_each_facet_value_with_its_count_and_toggles_the_facet() {
    SeriesFilter filter = new SeriesFilter();
    AtomicInteger applied = new AtomicInteger();
    JPopupMenu menu = FacetChipBar.menu(COUNTS, filter, applied::incrementAndGet);

    List<String> labels = labels(menu);
    assertAll(
        () ->
            assertTrue(
                labels.containsAll(
                    List.of(
                        "CT (2)",
                        "MR (1)",
                        "KO (1)",
                        "★ Any (1)",
                        "★ KO (1)",
                        "Downloaded (2)",
                        "Not downloaded (1)",
                        "Clear")),
                labels.toString()),
        () -> assertFalse(item(menu, "Clear").isEnabled()));

    item(menu, "CT (2)").doClick(0);
    item(menu, "Not downloaded (1)").doClick(0);
    item(menu, "★ Any (1)").doClick(0);
    assertAll(
        () -> assertEquals(Set.of("CT"), filter.getModalities()),
        () -> assertEquals(Set.of(DownloadState.PENDING), filter.getDownloadStates()),
        () -> assertTrue(filter.isInterestAny()),
        () -> assertEquals(3, applied.get()));

    JPopupMenu again = FacetChipBar.menu(COUNTS, filter, applied::incrementAndGet);
    assertAll(
        () -> assertTrue(item(again, "CT (2)").isSelected()),
        () -> assertFalse(item(again, "MR (1)").isSelected()),
        () -> assertTrue(item(again, "Clear").isEnabled()));
    item(again, "CT (2)").doClick(0);
    assertTrue(filter.getModalities().isEmpty());
    item(again, "Clear").doClick(0);
    assertAll(() -> assertFalse(filter.isActive()), () -> assertEquals(5, applied.get()));
  }

  @Test
  void chips_show_the_active_facets_and_a_click_removes_one() {
    DicomExplorer explorer = mock(DicomExplorer.class);
    FacetChipBar bar = new FacetChipBar(explorer);
    SeriesFilter filter = new SeriesFilter();

    bar.update(filter, null);
    assertFalse(bar.isVisible());

    filter.setText("Chest");
    filter.setModalities(List.of("MR", "CT"));
    filter.setObjectKinds(List.of(ObjectKind.SEGMENTATION));
    filter.setInterest(true, List.of(InterestSource.GRAPHICS));
    filter.setDownloadStates(List.of(DownloadState.DOWNLOADING));
    bar.update(filter, new int[] {2, 125});

    List<String> labels = labels(bar);
    assertAll(
        () -> assertTrue(bar.isVisible()),
        () ->
            assertEquals(
                List.of(
                    "“chest”",
                    "CT",
                    "MR",
                    "SEG",
                    "★ Any",
                    "★ Drawn graphics",
                    "Downloading",
                    "Clear"),
                labels),
        () -> assertTrue(bar.getComponent(0).toString().contains("2/125")));

    button(bar, "MR").doClick(0);
    verify(explorer).refreshFilter();
    assertEquals(Set.of("CT"), filter.getModalities());

    button(bar, "★ Any").doClick(0);
    assertAll(
        () -> assertFalse(filter.isInterestAny()),
        () -> assertEquals(Set.of(InterestSource.GRAPHICS), filter.getInterestSources()));

    button(bar, "Clear").doClick(0);
    verify(explorer, times(3)).refreshFilter();
    assertFalse(filter.isActive());
  }

  private static List<String> labels(java.awt.Container container) {
    return Arrays.stream(container.getComponents())
        .filter(AbstractButton.class::isInstance)
        .map(c -> ((AbstractButton) c).getText())
        .toList();
  }

  private static JMenuItem item(JPopupMenu menu, String label) {
    return (JMenuItem) button(menu, label);
  }

  private static AbstractButton button(java.awt.Container container, String label) {
    return Arrays.stream(container.getComponents())
        .filter(AbstractButton.class::isInstance)
        .map(AbstractButton.class::cast)
        .filter(b -> label.equals(b.getText()))
        .findFirst()
        .orElseThrow(() -> new AssertionError("No item " + label));
  }
}
