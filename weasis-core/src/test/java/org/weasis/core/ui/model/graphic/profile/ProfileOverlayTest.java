/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.model.graphic.profile;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.weasis.core.ui.model.graphic.Graphic;
import org.weasis.core.ui.model.graphic.GraphicRegistry;
import org.weasis.core.ui.model.graphic.GraphicToolDescriptor;
import org.weasis.core.ui.model.graphic.ToolCategory;
import org.weasis.core.ui.model.graphic.imp.line.LineGraphic;
import org.weasis.core.ui.model.graphic.profile.MeasurementProfile.Defaults;
import org.weasis.core.ui.model.utils.MeasureFormat;
import org.weasis.core.ui.model.utils.bean.Measurement;
import org.weasis.core.ui.pref.ViewSetting;

/** A profile is laid over the user's settings and leaves them as they were when it goes. */
@DisplayNameGeneration(ReplaceUnderscores.class)
class ProfileOverlayTest {

  private static final String TOOL = "test.overlay-line"; // NON-NLS

  // Own measurements: the flags of the built-in ones are shared by every test
  private final Measurement length = new Measurement("length", "Length", 1, true, true, true);
  private final Measurement angle = new Measurement("orientation", "Angle", 2, true, true, false);
  private final Measurement mean = new Measurement("stats.mean", "Mean", 1, false, true, true);
  private final Measurement sum = new Measurement("stats.sum", "Sum", 2, false, true, false);
  private final Measurement min = new Measurement("stats.min", "Min", 3, false, false, false);

  private final ViewSetting setting = new ViewSetting();
  private final ProfileOverlay overlay = new ProfileOverlay(setting, registry(), statistics());

  private Measurement[] statistics() {
    return new Measurement[] {mean, sum, min};
  }

  private GraphicRegistry registry(String... keys) {
    GraphicRegistry registry = new GraphicRegistry(property -> false);
    registry.register(
        () ->
            Stream.concat(Stream.of(TOOL), Stream.of(keys))
                .map(
                    key -> {
                      // Like polyline and freehand, the tools share one list of measurements
                      Graphic tool =
                          new LineGraphic() {
                            @Override
                            public List<Measurement> getMeasurementList() {
                              return List.of(length, angle);
                            }
                          };
                      return new GraphicToolDescriptor(
                          key, ToolCategory.ADVANCED, () -> tool, null, null, null, 0, 0);
                    })
                .toList());
    return registry;
  }

  private static MeasurementProfile profile(
      String id, Defaults defaults, Map<String, List<String>> labels, List<String> statistics) {
    return new MeasurementProfile(id, id, List.of(), null, defaults, labels, statistics, false);
  }

  private final MeasurementProfile radiography =
      profile(
          "radiography", // NON-NLS
          new Defaults(Color.GREEN, null, null, null, true, 1),
          Map.of(TOOL, List.of("orientation")), // NON-NLS
          List.of());

  private final MeasurementProfile plain = profile("default", null, null, null); // NON-NLS

  @Test
  void a_profile_replaces_only_what_it_states() {
    setting.setLineColor(Color.MAGENTA);
    overlay.apply(radiography);
    assertAll(
        () -> assertEquals(Color.GREEN, setting.getLineColor()),
        () -> assertTrue(setting.isUprightByDrag()),
        () -> assertEquals(1, setting.getDecimals()),
        () -> assertFalse(setting.isFilled(), "a field the profile leaves out is untouched"),
        () -> assertFalse(length.getGraphicLabel()),
        () -> assertTrue(angle.getGraphicLabel()),
        () -> assertFalse(mean.getComputed()),
        () -> assertFalse(setting.isBasicStatistics()));
  }

  @Test
  void the_next_profile_finds_the_users_settings_not_the_previous_profile() {
    setting.setLineColor(Color.MAGENTA);
    overlay.apply(radiography);
    overlay.apply(plain);
    assertAll(
        () -> assertEquals(Color.MAGENTA, setting.getLineColor()),
        () -> assertFalse(setting.isUprightByDrag()),
        () -> assertEquals(MeasureFormat.AUTO, setting.getDecimals()),
        () -> assertTrue(length.getGraphicLabel()),
        () -> assertFalse(angle.getGraphicLabel()),
        () -> assertTrue(mean.getComputed()),
        () -> assertTrue(sum.getComputed()),
        () -> assertFalse(min.getComputed(), "off before, off again"),
        () -> assertTrue(setting.isBasicStatistics()));
  }

  @Test
  void a_setting_changed_in_a_section_the_profile_leaves_alone_is_kept() {
    overlay.apply(profile("stats-only", null, null, List.of("stats.sum"))); // NON-NLS
    setting.setLineColor(Color.ORANGE);
    overlay.apply(plain);
    assertAll(
        () -> assertEquals(Color.ORANGE, setting.getLineColor()),
        () -> assertTrue(mean.getComputed()));
  }

  @Test
  void a_setting_the_user_changes_while_the_profile_is_active_is_kept() {
    setting.setLineColor(Color.MAGENTA);
    overlay.apply(radiography);
    setting.setLineColor(Color.BLUE);
    mean.setComputed(true);
    angle.setGraphicLabel(false);
    overlay.apply(plain);
    assertAll(
        () -> assertEquals(Color.BLUE, setting.getLineColor()),
        () -> assertTrue(mean.getComputed()),
        () -> assertFalse(angle.getGraphicLabel()),
        () -> assertTrue(sum.getComputed(), "left as the profile set it: the user's again"),
        () -> assertEquals(MeasureFormat.AUTO, setting.getDecimals()),
        () -> assertTrue(length.getGraphicLabel()));
  }

  @Test
  void tools_sharing_their_measurements_give_back_the_users_labels() {
    String freehand = "test.overlay-freehand"; // NON-NLS
    ProfileOverlay shared = new ProfileOverlay(setting, registry(freehand), statistics());
    shared.apply(
        profile(
            "shared", // NON-NLS
            null,
            Map.of(TOOL, List.of("orientation"), freehand, List.of("orientation")), // NON-NLS
            null));
    shared.restore();
    assertAll(
        () -> assertTrue(length.getGraphicLabel()), () -> assertFalse(angle.getGraphicLabel()));
  }

  @Test
  void a_user_change_under_a_profile_becomes_the_users_and_the_profile_stays() {
    overlay.apply(radiography);
    overlay.changeUserSettings(
        () -> {
          assertFalse(angle.getGraphicLabel(), "made on the user's settings");
          length.setGraphicLabel(false);
        });
    assertTrue(angle.getGraphicLabel(), "the profile is laid over again");
    overlay.restore();
    assertAll(
        () -> assertFalse(length.getGraphicLabel()), () -> assertFalse(angle.getGraphicLabel()));
  }

  @Test
  void a_user_change_without_a_profile_is_simply_applied() {
    overlay.changeUserSettings(() -> angle.setGraphicLabel(true));
    overlay.restore();
    assertTrue(angle.getGraphicLabel());
  }

  @Test
  void restoring_gives_back_what_is_saved_as_the_users_preferences() {
    setting.setLineColor(Color.MAGENTA);
    overlay.apply(radiography);
    overlay.restore();
    overlay.restore();
    assertAll(
        () -> assertEquals(Color.MAGENTA, setting.getLineColor()),
        () -> assertTrue(mean.getComputed()),
        () -> assertTrue(length.getGraphicLabel()));
  }
}
