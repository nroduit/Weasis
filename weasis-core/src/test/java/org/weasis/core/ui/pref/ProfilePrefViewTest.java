/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.pref;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Stream;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JScrollPane;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.weasis.core.Messages;
import org.weasis.core.ui.model.graphic.Graphic;
import org.weasis.core.ui.model.graphic.GraphicRegistry;
import org.weasis.core.ui.model.graphic.profile.MeasurementProfile;
import org.weasis.core.ui.model.graphic.profile.MeasurementProfile.Defaults;
import org.weasis.core.ui.model.graphic.profile.MeasurementProfileRegistry;
import org.weasis.core.ui.model.utils.MeasureFormat;
import org.weasis.core.ui.model.utils.bean.Measurement;

/** The page shows a profile and gives it back unchanged unless the user edits it. */
@DisplayNameGeneration(ReplaceUnderscores.class)
class ProfilePrefViewTest {

  private static final MeasurementProfile FULL =
      new MeasurementProfile(
          "chest", // NON-NLS
          "Chest", // NON-NLS
          List.of("CR", "DX", "OT"), // NON-NLS
          List.of("weasis.ctr", "weasis.line", "weasis.arrow", "weasis.text"), // NON-NLS
          new Defaults(new Color(255, 0, 0, 128), 2, true, 0.5f, true, 3),
          Map.of("weasis.line", List.of("length", "length.dx")), // NON-NLS
          List.of("stats.mean", "stats.sum"), // NON-NLS
          false);

  private static final MeasurementProfile OPEN =
      new MeasurementProfile("open", "Open", List.of(), null, null, null, null, false); // NON-NLS

  private static final MeasurementProfile BUILT_IN =
      new MeasurementProfile(
          "default", "Default", List.of(), null, null, null, null, false); // NON-NLS

  private static ProfilePrefView page(Path dir, MeasurementProfile... userProfiles) {
    MeasurementProfileRegistry registry =
        new MeasurementProfileRegistry(
            List.of(BUILT_IN), null, dir.resolve("profiles.json")); // NON-NLS
    for (MeasurementProfile profile : userProfiles) {
      registry.saveUser(profile);
    }
    // Its own hide rule: the shared registry would ask the UI core, absent from the tests
    return new ProfilePrefView(registry, new GraphicRegistry(property -> false));
  }

  @Test
  void a_profile_shown_and_read_back_is_unchanged(@TempDir Path dir) {
    ProfilePrefView page = page(dir, FULL);
    page.showProfile(FULL);
    assertEquals(FULL, page.editedProfile());
  }

  @Test
  void sections_left_to_the_current_settings_stay_unset(@TempDir Path dir) {
    ProfilePrefView page = page(dir, OPEN);
    page.showProfile(OPEN);
    MeasurementProfile read = page.editedProfile();
    assertAll(
        () -> assertNull(read.tools()),
        () -> assertNull(read.defaults()),
        () -> assertNull(read.statistics()),
        () -> assertTrue(read.labels().isEmpty()),
        () -> assertEquals(OPEN, read));
  }

  @Test
  void a_built_in_profile_is_read_only(@TempDir Path dir) {
    ProfilePrefView page = page(dir);
    MeasurementProfile builtIn = BUILT_IN.withBuiltIn(true);
    page.showProfile(builtIn);
    assertSame(builtIn, page.editedProfile());
  }

  @Test
  void the_labels_of_the_profile_shown_on_opening_are_checked(@TempDir Path dir) {
    GraphicRegistry graphics = new GraphicRegistry(property -> false);
    ProfilePrefView probe = new ProfilePrefView(registry(dir, "probe"), graphics); // NON-NLS
    Graphic tool =
        (Graphic)
            find(probe, JComboBox.class, c -> c.getItemAt(0) instanceof Graphic).getSelectedItem();
    String toolKey = graphics.keyOf(tool).orElseThrow();
    List<Measurement> measurements = tool.getMeasurementList();
    String shownKey = measurements.getLast().getKey();
    MeasurementProfile profile =
        new MeasurementProfile(
            "labels", // NON-NLS
            "Labels", // NON-NLS
            List.of(),
            null,
            null,
            Map.of(toolKey, List.of(shownKey)),
            null,
            false);

    MeasurementProfileRegistry registry = registry(dir, "page"); // NON-NLS
    registry.saveUser(profile);
    registry.select(profile.id());
    ProfilePrefView page = new ProfilePrefView(registry, graphics);

    String setLabels = Messages.getString("ProfilePrefView.set_labels");
    assertAll(
        () ->
            assertTrue(
                find(page, JCheckBox.class, b -> setLabels.equals(b.getText())).isSelected()),
        () ->
            assertTrue(find(page, JCheckBox.class, b -> shownKey.equals(b.getName())).isSelected()),
        () ->
            assertFalse(
                find(
                        page,
                        JCheckBox.class,
                        b -> measurements.getFirst().getKey().equals(b.getName()))
                    .isSelected()));
  }

  private static MeasurementProfileRegistry registry(Path dir, String name) {
    return new MeasurementProfileRegistry(
        List.of(BUILT_IN), null, dir.resolve(name + ".json")); // NON-NLS
  }

  private static <T extends Component> T find(Container root, Class<T> type, Predicate<T> test) {
    return components(root)
        .filter(type::isInstance)
        .map(type::cast)
        .filter(test)
        .findFirst()
        .orElseThrow(() -> new AssertionError("No matching " + type.getSimpleName()));
  }

  private static Stream<Component> components(Container root) {
    return Arrays.stream(root.getComponents())
        .flatMap(
            c ->
                c instanceof Container container
                    ? Stream.concat(Stream.of(c), components(container))
                    : Stream.of(c));
  }

  @Test
  void modalities_are_typed_with_any_separator() {
    assertEquals(
        List.of("CT", "MR", "PT"), ProfilePrefView.parseModalities(" ct; mr ,CT  pt,")); // NON-NLS
  }

  @Test
  void the_width_asked_for_does_not_depend_on_the_width_given(@TempDir Path dir) {
    ProfilePrefView page = page(dir, FULL);
    page.showProfile(FULL);
    page.setSize(600, 450);
    page.doLayout();
    int atDialogSize = page.getPreferredSize().width;
    page.setSize(1500, 450);
    page.doLayout();
    int stretched = page.getPreferredSize().width;
    assertAll(
        () -> assertEquals(atDialogSize, stretched, "no feedback between size and preferred size"),
        () -> assertTrue(page.getScrollableTracksViewportWidth()));
  }

  @Test
  void in_the_dialog_the_page_takes_the_width_of_the_viewport_and_never_scrolls_sideways(
      @TempDir Path dir) {
    ProfilePrefView page = page(dir, FULL);
    page.showProfile(FULL);
    JScrollPane scroll = new JScrollPane(page);
    for (int width : new int[] {600, 420, 600}) {
      scroll.setSize(width, 450);
      for (int pass = 0; pass < 3; pass++) {
        scroll.doLayout();
        scroll.getViewport().doLayout();
        page.doLayout();
      }
      int viewport = scroll.getViewport().getWidth();
      for (java.awt.Component section : page.getComponents()) {
        if (section.isVisible()) {
          assertTrue(
              section.getX() + section.getWidth() <= viewport,
              section.getClass().getSimpleName()
                  + " ends at "
                  + (section.getX() + section.getWidth()));
        }
      }
      assertAll(
          () -> assertEquals(viewport, page.getWidth(), "page as wide as the viewport"),
          () -> assertFalse(scroll.getHorizontalScrollBar().isVisible(), "no horizontal bar"));
    }
  }

  @Test
  void the_page_fits_the_preference_dialog(@TempDir Path dir) {
    ProfilePrefView page = page(dir, FULL);
    page.showProfile(FULL);
    Dimension size = page.getPreferredSize();
    assertAll(
        () -> assertTrue(size.width <= 580, "width " + size.width),
        () -> assertTrue(size.height <= 480, "height " + size.height));
  }

  @Test
  void automatic_decimals_survive_the_round_trip(@TempDir Path dir) {
    MeasurementProfile auto =
        new MeasurementProfile(
            "auto", // NON-NLS
            "Auto", // NON-NLS
            List.of("CT"), // NON-NLS
            null,
            new Defaults(Color.CYAN, 1, false, 1f, false, MeasureFormat.AUTO),
            null,
            null,
            false);
    ProfilePrefView page = page(dir, auto);
    page.showProfile(auto);
    assertEquals(auto, page.editedProfile());
  }
}
