/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.viewer3d;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.json.JsonObject;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.weasis.core.api.command.CommandModel;

/** The syntax of the {@code view3d} namespace is a public API: it has to keep parsing. */
@DisplayNameGeneration(ReplaceUnderscores.class)
class Viewer3dCommandsTest {

  private static final List<String> LINES =
      List.of("view3d:info", "view3d:info --json", "view3d:info -h");

  @Test
  @DisplayName("Every documented command line parses")
  void documented_command_lines_parse() {
    Object model = new Viewer3dCommands();
    assertAll(
        LINES.stream()
            .map(
                line ->
                    (Executable)
                        () ->
                            assertDoesNotThrow(
                                () -> CommandModel.parse(model, line), "line: " + line))
            .toList());
  }

  @Test
  @DisplayName("An unknown verb or option is a usage error, not a silent success")
  void unknown_input_is_refused() {
    Object model = new Viewer3dCommands();
    assertAll(
        () -> assertThrows(Exception.class, () -> CommandModel.parse(model, "view3d:unknown")),
        () -> assertThrows(Exception.class, () -> CommandModel.parse(model, "view3d:info --nope")));
  }

  @Test
  @DisplayName("The report names the path the renderer would take and whether it is usable")
  void the_report_describes_the_renderer() {
    OpenGLInfo capable = new OpenGLInfo("4.6.0 NVIDIA 595", "d", "NVIDIA", "RTX A4000", 16384);
    OpenGLInfo old = new OpenGLInfo("3.3.0", "d", "Intel", "HD Graphics", 2048);
    OpenGLInfo software = new OpenGLInfo("4.5 (Core Profile)", "d", "Mesa", "llvmpipe", 2048);

    JsonObject report = Viewer3dCommands.Info.json(true, "EGL", "desc", capable, false).build();
    assertAll(
        () -> assertTrue(report.getBoolean("available")),
        () -> assertEquals("EGL", report.getString("context")),
        () -> assertEquals("RTX A4000", report.getString("renderer")),
        () -> assertEquals(16384, report.getInt("maxTextureSize3D")),
        () -> assertEquals("compute", report.getString("path"), "4.6 takes the compute path"),
        () -> assertTrue(report.getBoolean("sufficient")),
        () ->
            assertEquals(
                "fbo",
                Viewer3dCommands.Info.json(true, "EGL", "d", capable, true)
                    .build()
                    .getString("path"),
                "the FBO path when it is forced"),
        () ->
            assertEquals(
                "fbo",
                Viewer3dCommands.Info.json(true, "CGL", "d", old, false).build().getString("path"),
                "3.3 has no compute shaders"),
        () ->
            assertEquals(
                false,
                Viewer3dCommands.Info.json(true, "EGL", "d", software, false)
                    .build()
                    .getBoolean("sufficient"),
                "a rasteriser is not enough"),
        () ->
            assertTrue(
                Viewer3dCommands.Info.json(true, "EGL", "d", software, false)
                    .build()
                    .getBoolean("softwareRenderer")));
  }

  @Test
  @DisplayName("Without a graphics adapter the report says so instead of inventing values")
  void the_report_survives_a_machine_without_3d() {
    JsonObject report = Viewer3dCommands.Info.json(false, null, null, null, false).build();
    assertAll(
        () -> assertEquals(false, report.getBoolean("available")),
        () -> assertTrue(report.containsKey("backend")),
        () -> assertEquals(false, report.containsKey("renderer")),
        () ->
            assertTrue(
                Viewer3dCommands.Info.text(false, null, null, false).contains("available: false")));
  }

  @Test
  @DisplayName("The namespace answers for its own commands only")
  void the_namespace_is_addressed_by_its_name() {
    Viewer3dCommands commands = new Viewer3dCommands();
    assertAll(
        () -> assertEquals("view3d", commands.getNamespace()),
        () -> assertTrue(commands.handles("view3d:info")),
        () -> assertTrue(commands.handles("view3d")),
        () -> assertEquals(false, commands.handles("view2d:grid")),
        () -> assertTrue(commands.isRemotable(), "reporting capabilities is safe remotely"));
  }
}
