/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.editor.image.lut;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Component;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JSeparator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.weasis.core.api.gui.util.RadioMenuItem;
import org.weasis.core.api.image.lut.ColorMapRegistry;
import org.weasis.opencv.op.lut.ByteLut;
import org.weasis.opencv.op.lut.ColorLut;
import org.weasis.opencv.op.lut.colormap.ColorMap;

@DisplayNameGeneration(ReplaceUnderscores.class)
class ColorMapRadioMenuTest {

  private ColorMapRegistry registry;
  private ColorMap pet;
  private ColorMap hot;

  @BeforeEach
  void setUp() {
    registry = new ColorMapRegistry(null, null);
    ColorMapRegistry.useInstance(registry);
    pet = registry.findById("weasis.pet-suv").orElseThrow();
    hot = registry.findById("weasis.dicom.hot-iron").orElseThrow();
  }

  private ColorMapRadioMenu menuFor(String modality) {
    List<ByteLut> luts = new ArrayList<>();
    luts.add(ColorLut.GRAY.getByteLut());
    luts.addAll(registry.byteLutsFor(modality));
    ColorMapRadioMenu menu = new ColorMapRadioMenu();
    menu.setModel(new DefaultComboBoxModel<>(luts.toArray(ByteLut[]::new)));
    menu.setModality(modality);
    return menu;
  }

  private static final String SEPARATOR = "|";

  private static List<String> rootTexts(JPopupMenu popup) {
    return rootTexts(Arrays.asList(popup.getComponents()));
  }

  private static List<String> rootTexts(List<Component> entries) {
    return entries.stream()
        .map(
            c ->
                switch (c) {
                  case JMenuItem item -> item.getText();
                  case JLabel header -> header.getText();
                  default -> SEPARATOR;
                })
        .toList();
  }

  private static List<Component> rootItems(JMenu menu) {
    return Arrays.stream(menu.getMenuComponents()).filter(c -> c instanceof RadioMenuItem).toList();
  }

  @Test
  void favorites_leave_their_section_for_the_root_under_a_title_with_a_hint() throws Exception {
    ColorMapRadioMenu menu = menuFor("CT");
    List<String> before = rootTexts(menu.createJPopupMenu());
    registry.setFavorite(hot.id(), true);
    List<String> after = rootTexts(menu.createJPopupMenu());
    String favorite = after.get(3);

    assertAll(
        () -> assertFalse(before.contains(hot.name()), "a category map lives in a submenu"),
        () -> assertEquals(List.of("Gray", SEPARATOR, "Favorites"), after.subList(0, 3)),
        () -> assertTrue(favorite.startsWith("<html>") && favorite.contains(hot.name())),
        () -> assertTrue(favorite.contains(hot.category()), "hint names the home submenu"),
        () -> assertEquals(SEPARATOR, after.get(4)),
        () -> assertTrue(after.contains("CT"), "modality maps get their own title"),
        () -> assertTrue(before.contains(hot.category()) && after.contains(hot.category())),
        () -> assertFalse(SEPARATOR.equals(after.getLast()), "no trailing separator"),
        () -> assertFalse(String.join(",", after).contains(SEPARATOR + "," + SEPARATOR)));

    registry.setFavorite(hot.id(), false);
    menu.createJPopupMenu();
    RadioMenuItem item =
        menu.getRadioMenuItemListCopy().stream()
            .filter(i -> i.getUserObject().equals(registry.byteLut(hot)))
            .findFirst()
            .orElseThrow();
    assertEquals(hot.name(), item.getText(), "the hint goes with the flag");
  }

  @Test
  void a_menu_gets_the_same_sections_as_a_popup() throws Exception {
    registry.setFavorite(pet.id(), true);
    JMenu menu = menuFor("PT").createMenu("LUT", null);
    List<Component> entries = Arrays.asList(menu.getMenuComponents());
    List<String> popup = rootTexts(menuFor("PT").createJPopupMenu());

    assertAll(
        () -> assertTrue(entries.stream().anyMatch(JSeparator.class::isInstance)),
        () -> assertEquals(popup, rootTexts(entries)),
        () ->
            assertTrue(
                rootItems(menu).stream()
                    .map(c -> ((JMenuItem) c).getText())
                    .anyMatch(t -> t.contains(pet.name()))));
  }

  @Test
  void favorite_item_toggles_the_shown_map_and_needs_a_registered_one() throws Exception {
    ColorMapRadioMenu menu = menuFor("PT");
    menu.setSelected(registry.byteLut(pet));
    JMenuItem add = menu.createFavoriteItem();
    assertNotNull(add);
    add.doClick();
    JMenuItem remove = menu.createFavoriteItem();
    assertTrue(registry.isFavorite(pet));
    remove.doClick();

    assertAll(
        () -> assertFalse(registry.isFavorite(pet)),
        () -> assertFalse(add.getText().equals(remove.getText())),
        () -> assertNull(ColorMapRadioMenu.createFavoriteItem(null)),
        () ->
            assertNull(
                ColorMapRadioMenu.createFavoriteItem(pet.toBuilder().id("user.unsaved").build())));
    menu.setSelected(ColorLut.GRAY.getByteLut());
    assertNull(menu.createFavoriteItem());
  }
}
