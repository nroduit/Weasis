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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.parallel.Isolated;
import org.weasis.core.api.image.lut.ColorMapRegistry;
import org.weasis.dicom.viewer3d.gl.ReferenceScene.Frame;
import org.weasis.dicom.viewer3d.gl.ReferenceScene.RayCaster;
import org.weasis.dicom.viewer3d.vr.Preset;
import org.weasis.dicom.viewer3d.vr.RenderingType;

/**
 * Times the two stages of a frame that the OpenGL back-end is responsible for — the ray-cast and
 * blit on the GPU, and the read-back that hands the result to Java2D — on every rendering path, at
 * the two resolutions the viewer is judged at. It is the measured half of the baseline the FFM port
 * is compared against, and it is what tells whether an explicit {@code glReadPixels} costs more
 * than what {@code GLJPanel} does today.
 *
 * <p>Run on demand only, since a timing on a shared machine is not a build result:
 *
 * <pre>mvn -pl weasis-dicom/weasis-dicom-3d/viewer3d test -Dtest=BaselineTimingsTest \
 *   -Dweasis.3d.baseline.dir=/somewhere</pre>
 */
@Isolated("holds the process-wide OpenGL context")
@EnabledIfSystemProperty(named = BaselineArtifacts.P_DIR, matches = ".+")
class BaselineTimingsTest {

  private record Resolution(String name, int width, int height) {}

  private static final List<Resolution> RESOLUTIONS =
      List.of(new Resolution("1080p", 1920, 1080), new Resolution("4K", 3840, 2160)); // NON-NLS

  private static final int WARMUP_FRAMES = 5;
  private static final int TIMED_FRAMES = 20;

  private record Row(
      String rayCaster,
      String resolution,
      String frame,
      String pass,
      double gpuP50,
      double gpuP95,
      double readP50) {}

  @Test
  @DisplayName("GPU and read-back times of a frame, per path and resolution")
  void measure() {
    ColorMapRegistry registry = new ColorMapRegistry(null, path -> {});
    registry.addBuiltIn(Preset.builtInMaps());
    ColorMapRegistry.useInstance(registry);

    List<Frame> timed =
        List.of(
            Frame.of("composite", RenderingType.COMPOSITE).shaded(), // NON-NLS
            Frame.of("mip-max", RenderingType.MIP_MAX), // NON-NLS
            Frame.of("iso-surface", RenderingType.ISO2).shaded(), // NON-NLS
            Frame.of("cinematic", RenderingType.COMPOSITE).cinematicLit(), // NON-NLS
            Frame.of("path-tracing", RenderingType.PATH_TRACING).pathTraced(1)); // NON-NLS

    List<Row> rows = new ArrayList<>();
    String renderer;
    try (GlSession session = GlSession.openOrAbort()) {
      Gl gl = session.gl();
      renderer = session.describe();
      for (RayCaster rayCaster : RayCaster.values()) {
        if (rayCaster == RayCaster.COMPUTE && !gl.isComputeCapable()) {
          continue;
        }
        for (Resolution resolution : RESOLUTIONS) {
          try (ReferenceScene scene =
              new ReferenceScene(gl, rayCaster, resolution.width(), resolution.height())) {
            for (Frame frame : timed) {
              rows.add(time(gl, scene, rayCaster, resolution, frame, "full")); // NON-NLS
            }
            if (rayCaster == RayCaster.FBO) {
              // What a drag frame costs on a HiDPI display: the viewer ray-casts the logical
              // resolution and the blit upscales it.
              scene.setPassScale(0.5f);
              rows.add(
                  time(
                      gl,
                      scene,
                      rayCaster,
                      resolution,
                      Frame.of("composite", RenderingType.COMPOSITE).shaded(), // NON-NLS
                      "half")); // NON-NLS
              scene.setPassScale(1f);
            }
          }
        }
      }
    }
    String file = "timings.md";
    BaselineArtifacts.directory()
        .ifPresent(dir -> BaselineArtifacts.writeText(dir, file, table(renderer, rows)));
  }

  private static Row time(
      Gl gl,
      ReferenceScene scene,
      RayCaster rayCaster,
      Resolution resolution,
      Frame frame,
      String pass) {
    for (int i = 0; i < WARMUP_FRAMES; i++) {
      scene.render(frame);
    }
    double[] gpu = new double[TIMED_FRAMES];
    double[] read = new double[TIMED_FRAMES];
    for (int i = 0; i < TIMED_FRAMES; i++) {
      gl.glFinish();
      long start = System.nanoTime();
      scene.draw(frame);
      gl.glFinish();
      long drawn = System.nanoTime();
      scene.read();
      long readBack = System.nanoTime();
      gpu[i] = (drawn - start) / 1_000_000.0;
      read[i] = (readBack - drawn) / 1_000_000.0;
    }
    Arrays.sort(gpu);
    Arrays.sort(read);
    return new Row(
        rayCaster.name().toLowerCase(Locale.ROOT),
        resolution.name(),
        frame.name(),
        pass,
        percentile(gpu, 50),
        percentile(gpu, 95),
        percentile(read, 50));
  }

  private static double percentile(double[] sorted, int percentile) {
    int index = Math.clamp(Math.round(percentile / 100f * sorted.length) - 1, 0, sorted.length - 1);
    return sorted[index];
  }

  private static String table(String renderer, List<Row> rows) {
    StringBuilder out = new StringBuilder();
    out.append("Renderer: ").append(renderer).append("\n\n"); // NON-NLS
    out.append("Samples per ray 1024, ")
        .append(TIMED_FRAMES)
        .append(" timed frames after ")
        .append(WARMUP_FRAMES)
        .append(" warm-up frames, GPU drained with glFinish around the draw.\n\n"); // NON-NLS
    out.append("| Path | Resolution | Frame | Pass | GPU p50 (ms) | GPU p95 (ms) |")
        .append(" Read-back p50 (ms) |\n"); // NON-NLS
    out.append("|---|---|---|---|---:|---:|---:|\n");
    for (Row row : rows) {
      out.append(
          String.format(
              Locale.ROOT,
              "| %s | %s | %s | %s | %.2f | %.2f | %.2f |%n",
              row.rayCaster(),
              row.resolution(),
              row.frame(),
              row.pass(),
              row.gpuP50(),
              row.gpuP95(),
              row.readP50()));
    }
    return out.toString();
  }
}
