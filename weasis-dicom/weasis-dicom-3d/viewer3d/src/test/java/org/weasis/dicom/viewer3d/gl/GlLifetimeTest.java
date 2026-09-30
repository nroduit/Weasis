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

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.weasis.core.api.image.lut.ColorMapRegistry;
import org.weasis.dicom.viewer3d.gl.ReferenceScene.RayCaster;
import org.weasis.dicom.viewer3d.vr.Preset;

/**
 * Opening and closing a volume over and over, which is the scenario of <a
 * href="https://github.com/nroduit/Weasis/issues/918">#918</a>: with one context and every deletion
 * going through the GL thread, a texture name released by one view has to come back to the next one
 * instead of the driver handing out a fresh one for ever.
 *
 * <p>A driver recycles the lowest free name, so a name that keeps climbing across cycles is a
 * release that never happened. That, and a clean error state, is what a leak shows up as from here.
 */
@Isolated("holds a process-wide OpenGL context")
@DisplayNameGeneration(ReplaceUnderscores.class)
class GlLifetimeTest {

  /** The count of the specification; lower it to keep a slow software renderer bearable. */
  private static final int CYCLES = Integer.getInteger("weasis.3d.cycles", 50);

  private static final int SIZE = 64;

  @BeforeAll
  static void installColorMaps() {
    ColorMapRegistry registry = new ColorMapRegistry(null, path -> {});
    registry.addBuiltIn(Preset.builtInMaps());
    ColorMapRegistry.useInstance(registry);
  }

  @Test
  @DisplayName("Opening and closing a volume many times leaks no OpenGL object")
  void repeated_open_and_close_leaks_nothing() {
    List<GlBackend> backends = GlSession.available();
    Assumptions.assumeFalse(backends.isEmpty(), "No OpenGL back-end on this machine");

    for (GlBackend backend : backends) {
      try (GlSession session = GlSession.openOrAbort(backend)) {
        Gl gl = session.gl();
        RayCaster rayCaster = gl.isComputeCapable() ? RayCaster.COMPUTE : RayCaster.FBO;
        List<Integer> firstName = new ArrayList<>();

        for (int cycle = 0; cycle < CYCLES; cycle++) {
          try (ReferenceScene scene = new ReferenceScene(gl, rayCaster, SIZE, SIZE)) {
            scene.render(ReferenceScene.frames().getFirst());
          }
          // Taken after the scene released everything: the lowest name the driver has free.
          int[] probe = gl.glGenTextures(1);
          firstName.add(probe[0]);
          gl.glDeleteTextures(probe);
          assertEquals(Gl.GL_NO_ERROR, gl.glGetError(), backend + ": cycle " + cycle);
        }

        int first = firstName.getFirst();
        int last = firstName.getLast();
        assertTrue(
            last <= first,
            () ->
                "%s: texture names grew from %d to %d over %d cycles, so objects were not released"
                    .formatted(backend, first, last, CYCLES));
      }
    }
  }
}
