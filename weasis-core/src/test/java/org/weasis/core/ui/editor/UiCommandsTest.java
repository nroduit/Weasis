/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.editor;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import java.io.StringReader;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.weasis.core.api.command.CommandContext;
import org.weasis.core.api.command.CommandModel;
import org.weasis.core.api.command.CommandOrigin;
import org.weasis.core.api.command.CommandResult;
import org.weasis.core.api.setting.SettingEnvironment;
import org.weasis.core.api.setting.Settings;
import org.weasis.core.ui.docking.Shell;
import org.weasis.core.ui.model.graphic.profile.MeasurementProfile;
import org.weasis.core.ui.model.graphic.profile.MeasurementProfileRegistry;
import org.weasis.core.ui.util.ToolBarContainer;
import org.weasis.core.ui.util.WtoolBar;

class UiCommandsTest {

  private UiCommands commands;

  @BeforeEach
  void installLayers(@TempDir Path dir) {
    Settings.install(SettingEnvironment.inMemory(new HashMap<>()));
    UiProfiles.document()
        .setBuiltIn(
            List.of(
                new UiProfile("cmd.ct", "CT", null, List.of("viewer"), null, null, null).toJson(),
                new UiProfile("cmd.xr", "XR", null, null, null, null, null).toJson()));
    UiProfiles.document().configure(Optional.empty(), dir.resolve(UiProfiles.DOCUMENT));
    UiProfiles.setMeasurementRegistry(
        () ->
            new MeasurementProfileRegistry(
                List.of(
                    new MeasurementProfile(
                        "default", "Default", List.of(), null, null, null, null, false)),
                dir.resolve("site.json"), // NON-NLS
                dir.resolve("measurementProfiles.json"))); // NON-NLS
    UiProfiles.setShell(() -> null);
    UiProfiles.release();
    commands =
        new UiCommands()
            .withSelectedViewer(Optional::empty)
            .withShell(() -> null)
            .withToolbars(() -> null);
  }

  @AfterEach
  void releaseEverything() {
    UiProfiles.release();
    UiProfiles.document().setBuiltIn(List.of());
    UiProfiles.setShell(Shell::current);
    UiProfiles.setMeasurementRegistry(MeasurementProfileRegistry::getInstance);
  }

  private CommandResult run(String verb, String... args) {
    return CommandContext.runWith(
        new CommandContext(CommandOrigin.LAUNCH_ARGS),
        () -> CommandModel.execute(commands, verb, args));
  }

  private static JsonObject json(String text) {
    return Json.createReader(new StringReader(text)).readObject();
  }

  @Test
  void profilesListsEveryLayerWithTheActiveOne() {
    CommandResult result = run("profiles", "--json");
    JsonObject body = json(result.body());
    List<JsonObject> list = body.getJsonArray("profiles").getValuesAs(JsonObject.class);
    assertAll(
        () -> assertEquals(CommandResult.OK, result.status()),
        () -> assertEquals(2, list.size()),
        () -> assertEquals("cmd.ct", list.get(0).getString("id")),
        () -> assertEquals("built_in", list.get(0).getString("origin")),
        () -> assertFalse(list.get(0).getBoolean("active")),
        () -> assertFalse(body.containsKey("active")));
    String text = run("profiles").body();
    assertTrue(text.contains("  cmd.ct (built_in) CT"), text);
  }

  @Test
  void profileShowsAppliesAndClears() {
    JsonObject shown = json(run("profile", "--id", "cmd.ct").body());
    assertAll(
        () -> assertEquals("cmd.ct", shown.getString("id")),
        () ->
            assertEquals(
                "viewer", shown.getJsonObject("profile").getJsonArray("toolbars").getString(0)));

    JsonObject applied = json(run("profile", "--id", "cmd.ct", "--apply").body());
    assertAll(
        () -> assertEquals("cmd.ct", applied.getString("active")),
        () -> assertEquals("cmd.ct", UiProfiles.activeId()),
        () -> assertTrue(json(run("profiles", "--json").body()).containsKey("active")));

    JsonObject cleared = json(run("profile", "--clear").body());
    assertAll(
        () -> assertFalse(cleared.containsKey("active")),
        () -> assertTrue(UiProfiles.active().isEmpty()));
  }

  @Test
  void profileRefusesAnUnknownIdAndDeletesOnlyTheUserLayer() throws Exception {
    assertNotEquals(CommandResult.OK, run("profile", "--id", "cmd.none").status());
    assertNotEquals(CommandResult.OK, run("profile", "--id", "cmd.ct", "--delete").status());
    UiProfiles.document()
        .put(new UiProfile("cmd.ct", "Mine", null, List.of("measure"), null, null, null).toJson());
    assertEquals("Mine", UiProfiles.find("cmd.ct").orElseThrow().name());
    assertEquals(CommandResult.OK, run("profile", "--id", "cmd.ct", "--delete").status());
    assertEquals("CT", UiProfiles.find("cmd.ct").orElseThrow().name(), "the built-in again");
    assertNotEquals(
        CommandResult.OK, run("profile", "--id", "cmd.ct", "--apply", "--clear").status());
  }

  @Test
  void revealShowsAHiddenToolbarAgainAndReportsWhatStaysHidden() {
    ToolBarContainer container = new ToolBarContainer();
    WtoolBar cine = new WtoolBar("cine", 80); // id "w" (WtoolBar), not in the profile
    container.registerToolBar(List.of(cine));
    container.applyContext(ViewContext.empty(), UiProfiles.find("cmd.ct").orElseThrow());
    commands.withToolbars(() -> container);

    JsonObject before = json(run("profiles", "--json").body());
    assertEquals("w", before.getJsonObject("hidden").getJsonArray("toolbars").getString(0));

    assertNotEquals(CommandResult.OK, run("reveal").status(), "one option is required");
    assertNotEquals(CommandResult.OK, run("reveal", "--toolbar", "nope").status());
    assertNotEquals(CommandResult.OK, run("reveal", "--tool", "measure").status());
    CommandResult result = run("reveal", "--toolbar", "w");
    JsonObject after = json(result.body());
    assertAll(
        () -> assertEquals(CommandResult.OK, result.status()),
        () -> assertEquals("w", after.getJsonArray("toolbars").getString(0)),
        () -> assertTrue(after.getJsonObject("hidden").getJsonArray("toolbars").isEmpty()),
        () -> assertTrue(after.getJsonObject("hidden").getJsonArray("tools").isEmpty()));
  }

  @Test
  void actionsListsTheCatalogActionRunsOneAndContextReportsTheView() {
    List<String> ran = new java.util.ArrayList<>();
    java.util.function.Supplier<List<ActionEntry>> source =
        () ->
            List.of(
                new ActionEntry(
                    "t/zoom", "Zoom in", "Viewer", "Ctrl+I", "zoomIn", true, () -> ran.add("zoom")),
                new ActionEntry("t/mpr", "MPR", "3D", "", "", false, () -> ran.add("mpr")));
    ActionCatalog.addSource(source);
    try {
      JsonObject all = json(run("actions", "--json").body());
      List<JsonObject> list = all.getJsonArray("actions").getValuesAs(JsonObject.class);
      JsonObject relevant = json(run("actions", "--json", "--relevant").body());
      JsonObject filtered = json(run("actions", "--json", "--filter", "mpr").body());
      String text = run("actions").body();
      assertAll(
          () -> assertTrue(list.stream().anyMatch(a -> a.getString("id").equals("t/zoom"))),
          () -> assertTrue(list.stream().anyMatch(a -> a.getString("id").equals("t/mpr"))),
          () ->
              assertTrue(
                  relevant.getJsonArray("actions").getValuesAs(JsonObject.class).stream()
                      .noneMatch(a -> a.getString("id").equals("t/mpr"))),
          () ->
              assertEquals(
                  "t/mpr", filtered.getJsonArray("actions").getJsonObject(0).getString("id")),
          () -> assertTrue(text.contains("- t/mpr  MPR (3D)"), text),
          () -> assertTrue(text.contains("  t/zoom  Zoom in (Viewer, Ctrl+I)"), text));

      CommandResult result = run("action", "--id", "t/mpr");
      assertAll(
          () -> assertEquals(CommandResult.OK, result.status(), result.error()),
          () -> assertEquals(List.of("mpr"), ran),
          () -> assertTrue(json(result.body()).containsKey("hidden")));
      assertNotEquals(CommandResult.OK, run("action", "--id", "t/none").status());
      assertNotEquals(CommandResult.OK, run("action").status(), "--id is required");

      run("profile", "--id", "cmd.ct", "--apply");
      JsonObject context = json(run("context").body());
      assertAll(
          () -> assertTrue(context.containsKey("context")),
          () -> assertEquals("cmd.ct", context.getJsonObject("profile").getString("id")),
          () -> assertEquals("CT", context.getJsonObject("profile").getString("name")),
          () -> assertEquals("cmd.ct", context.getString("active")),
          () -> assertTrue(context.containsKey("hidden")),
          () ->
              assertEquals(
                  List.of("t/zoom"),
                  context.getJsonArray("actions").getValuesAs(JsonObject.class).stream()
                      .map(a -> a.getString("id"))
                      .toList(),
                  "only the relevant actions"));
    } finally {
      ActionCatalog.removeSource(source);
    }
  }
}
