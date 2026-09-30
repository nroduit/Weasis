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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertLinesMatch;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.weasis.core.api.image.lut.ColorMapRegistry;
import org.weasis.dicom.viewer3d.gl.ReferenceScene.Frame;
import org.weasis.dicom.viewer3d.gl.ReferenceScene.RayCaster;
import org.weasis.dicom.viewer3d.vr.Preset;

/**
 * The gate the port is held to: the same scene, rendered through JOGL and through the FFM binding
 * on this machine, must come out identical to the pixel.
 *
 * <p>A frame is only reproducible on the driver that rendered it, so nothing is compared against a
 * stored image: the two back-ends are run one after the other in the same process, on the same GPU,
 * and their frames are hashed. A driver that offers only one of the two back-ends skips the test.
 */
@Isolated("holds a process-wide OpenGL context")
class BackendEquivalenceTest {

  private static final int WIDTH = 384;
  private static final int HEIGHT = 384;

  @BeforeAll
  static void installColorMaps() {
    ColorMapRegistry registry = new ColorMapRegistry(null, path -> {});
    registry.addBuiltIn(Preset.builtInMaps());
    ColorMapRegistry.useInstance(registry);
  }

  @Test
  @DisplayName("The FFM binding renders every frame exactly as JOGL does")
  void bothBackendsRenderTheSameFrames() {
    List<GlBackend> backends = GlSession.available();
    Assumptions.assumeTrue(
        backends.containsAll(List.of(GlBackend.JOGL, GlBackend.FFM)),
        "Both back-ends are needed to compare them; this machine offers " + backends);

    Map<GlBackend, Map<String, String>> digests = new LinkedHashMap<>();
    Map<GlBackend, String> renderers = new LinkedHashMap<>();
    for (GlBackend backend : List.of(GlBackend.JOGL, GlBackend.FFM)) {
      try (GlSession session = GlSession.openOrAbort(backend)) {
        renderers.put(backend, session.renderer());
        digests.put(backend, render(session));
      }
    }

    assertFalse(digests.get(GlBackend.FFM).isEmpty(), "no frame was rendered");
    assertEquals(
        renderers.get(GlBackend.JOGL),
        renderers.get(GlBackend.FFM),
        "the two back-ends must be compared on the same renderer");
    assertLinesMatch(
        format(digests.get(GlBackend.JOGL)),
        format(digests.get(GlBackend.FFM)),
        "frames differ between the back-ends");
  }

  private static Map<String, String> render(GlSession session) {
    Gl gl = session.gl();
    Map<String, String> digests = new LinkedHashMap<>();
    for (RayCaster rayCaster : RayCaster.values()) {
      if (rayCaster == RayCaster.COMPUTE && !gl.isComputeCapable()) {
        continue;
      }
      try (ReferenceScene scene = new ReferenceScene(gl, rayCaster, WIDTH, HEIGHT)) {
        for (Frame frame : ReferenceScene.frames()) {
          String name = rayCaster.name().toLowerCase(Locale.ROOT) + "-" + frame.name();
          digests.put(name, BaselineArtifacts.digest(scene.render(frame)));
        }
      }
      assertEquals(Gl.GL_NO_ERROR, gl.glGetError(), rayCaster + ": releasing the scene");
    }
    return digests;
  }

  private static List<String> format(Map<String, String> digests) {
    List<String> lines = new ArrayList<>();
    digests.forEach((name, digest) -> lines.add(name + " " + digest));
    return lines;
  }
}
