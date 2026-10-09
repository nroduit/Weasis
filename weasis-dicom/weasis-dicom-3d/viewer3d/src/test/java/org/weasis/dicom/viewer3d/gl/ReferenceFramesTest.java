/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.viewer3d.gl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.weasis.core.api.image.lut.ColorMapRegistry;
import org.weasis.dicom.viewer3d.gl.ReferenceScene.Frame;
import org.weasis.dicom.viewer3d.gl.ReferenceScene.RayCaster;
import org.weasis.dicom.viewer3d.vr.Preset;

/**
 * Renders every case of the baseline on each ray-casting path available on this machine: every
 * shader of the viewer is compiled and linked, a frame is produced and read back, and the GL error
 * state is checked — so a GLSL error fails the build instead of only reaching the log. Run with
 * {@code -Dweasis.3d.baseline.dir=…} it also writes the images and their digests, which is how the
 * step-0 baseline of the FFM port was recorded.
 */
@Isolated("holds the process-wide OpenGL context")
class ReferenceFramesTest {

  private static final int WIDTH = 512;
  private static final int HEIGHT = 512;

  @BeforeAll
  static void installColorMaps() {
    ColorMapRegistry registry = new ColorMapRegistry(null, path -> {});
    registry.addBuiltIn(Preset.builtInMaps());
    ColorMapRegistry.useInstance(registry);
  }

  @Test
  @DisplayName("Every rendering type draws a frame on every path this machine supports")
  void everyRenderingTypeDrawsAFrame() {
    Optional<Path> artifacts = BaselineArtifacts.directory();
    Map<String, String> digests = new LinkedHashMap<>();
    String renderer;
    try (GlSession session = GlSession.openOrAbort()) {
      Gl gl = session.gl();
      renderer = session.describe();
      for (RayCaster rayCaster : RayCaster.values()) {
        if (rayCaster == RayCaster.COMPUTE && !gl.isComputeCapable()) {
          continue;
        }
        try (ReferenceScene scene = new ReferenceScene(gl, rayCaster, WIDTH, HEIGHT)) {
          scene
              .programs()
              .forEach(
                  program ->
                      assertTrue(
                          program.isValid(),
                          () -> rayCaster + ": program " + program + " did not build"));
          for (Frame frame : ReferenceScene.frames()) {
            String name = rayCaster.name().toLowerCase(Locale.ROOT) + "-" + frame.name();
            int[] pixels = scene.render(frame);
            assertEquals(Gl.GL_NO_ERROR, gl.glGetError(), name);
            assertTrue(BaselineArtifacts.hasContent(pixels), name + " is a flat image");
            digests.put(name, BaselineArtifacts.digest(pixels));
            artifacts.ifPresent(
                dir -> BaselineArtifacts.writePng(dir, name, pixels, WIDTH, HEIGHT));
          }
        }
        assertEquals(Gl.GL_NO_ERROR, gl.glGetError(), rayCaster + ": releasing the scene");
      }
    }
    String report = report(renderer, digests);
    artifacts.ifPresent(dir -> BaselineArtifacts.writeText(dir, "frames.txt", report));
  }

  private static String report(String renderer, Map<String, String> digests) {
    StringBuilder report = new StringBuilder();
    report.append("# renderer: ").append(renderer).append('\n'); // NON-NLS
    report
        .append("# frame size: ")
        .append(WIDTH)
        .append('x')
        .append(HEIGHT)
        .append('\n'); // NON-NLS
    digests.forEach((name, digest) -> report.append(digest).append("  ").append(name).append('\n'));
    return report.toString();
  }
}
