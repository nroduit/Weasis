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

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.api.parallel.Isolated;
import org.weasis.core.api.image.lut.ColorMapRegistry;
import org.weasis.dicom.viewer3d.gl.ReferenceScene.Frame;
import org.weasis.dicom.viewer3d.gl.ReferenceScene.RayCaster;
import org.weasis.dicom.viewer3d.vr.Preset;
import org.weasis.dicom.viewer3d.vr.RenderingType;

/**
 * The gate the port has to pass on every machine of the hardware matrix: the same frames, measured
 * through both bindings in one run, and the new one must not be slower than the one it replaces.
 *
 * <p>Comparing within a run rather than against a stored number is what makes this usable on a
 * machine nobody has measured before: it needs no baseline, only both back-ends.
 *
 * <p>Run on demand, since a timing on a shared machine is not a build result:
 *
 * <pre>mvn -pl weasis-dicom/weasis-dicom-3d/viewer3d test -Dtest=PerformanceGateTest \
 *   -Dweasis.3d.baseline.dir=/somewhere</pre>
 */
@Isolated("holds a process-wide OpenGL context")
@EnabledIfSystemProperty(named = BaselineArtifacts.P_DIR, matches = ".+")
class PerformanceGateTest {

  /** What the median frame may cost above the binding it replaces before it counts as a cost. */
  private static final double MEDIAN_MARGIN = 1.15;

  /**
   * The tail is a guard against a gross regression, not a measurement. The 95th percentile of one
   * batch is one or two frames, and it wanders: timing the same case ten times over gave 8.2 to
   * 11.4 ms on one binding and 7.4 to 12.1 ms on the other — a spread of more than 1.6 either side
   * of nothing at all. Anything tighter than this would fail on the machine rather than on the
   * code, which is worse than not checking.
   */
  private static final double TAIL_MARGIN = 2.5;

  /** Below this, a difference is scheduling noise rather than a cost. */
  private static final double NOISE_MS = 0.5;

  /**
   * Creating the context happens once in a session, so what matters is that it stays negligible.
   */
  private static final double STARTUP_ALLOWANCE_MS = 100;

  private static final int STARTUP_CYCLES = 5;
  private static final int WARMUP_FRAMES = 10;
  private static final int TIMED_FRAMES = 30;

  /**
   * Interference only ever makes a frame slower, so the same case is timed several times and the
   * quietest run is kept. One batch of a hundred frames would not do: a single hiccup owns the tail
   * of whichever batch it lands in, and on a machine that is doing anything else it lands somewhere
   * every time.
   */
  private static final int BATCHES = 3;

  private record Resolution(String name, int width, int height) {}

  private static final List<Resolution> RESOLUTIONS =
      List.of(new Resolution("1080p", 1920, 1080), new Resolution("4K", 3840, 2160)); // NON-NLS

  private record Case(String name, RayCaster rayCaster, Frame frame, float passScale) {}

  private record Timing(double p50, double p95) {}

  @BeforeAll
  static void installColorMaps() {
    ColorMapRegistry registry = new ColorMapRegistry(null, path -> {});
    registry.addBuiltIn(Preset.builtInMaps());
    ColorMapRegistry.useInstance(registry);
  }

  @Test
  @DisplayName("The FFM binding renders no slower than JOGL, and starts no slower")
  void the_new_binding_is_not_slower() {
    List<GlBackend> backends = GlSession.available();
    Assumptions.assumeTrue(
        backends.containsAll(List.of(GlBackend.JOGL, GlBackend.FFM)),
        "Both back-ends are needed to compare them; this machine offers " + backends);

    Map<GlBackend, Map<String, Timing>> measured = new LinkedHashMap<>();
    Map<GlBackend, Double> startup = new LinkedHashMap<>();
    for (GlBackend backend : List.of(GlBackend.JOGL, GlBackend.FFM)) {
      startup.put(backend, contextCreationMillis(backend));
      try (GlSession session = GlSession.openOrAbort(backend)) {
        measured.put(backend, measure(session));
      }
    }
    String renderer = this.renderer;

    Map<String, Timing> jogl = measured.get(GlBackend.JOGL);
    Map<String, Timing> ffm = measured.get(GlBackend.FFM);
    BaselineArtifacts.directory()
        .ifPresent(
            dir ->
                BaselineArtifacts.writeText(dir, "gate.md", report(renderer, jogl, ffm, startup)));

    List<Executable> checks = new ArrayList<>();
    jogl.forEach(
        (name, reference) -> {
          Timing candidate = ffm.get(name);
          checks.add(
              () ->
                  assertTrue(
                      within(candidate.p50(), reference.p50(), MEDIAN_MARGIN),
                      () ->
                          regression(
                              name, "p50", candidate.p50(), reference.p50(), MEDIAN_MARGIN)));
          checks.add(
              () ->
                  assertTrue(
                      within(candidate.p95(), reference.p95(), TAIL_MARGIN),
                      () ->
                          regression(name, "p95", candidate.p95(), reference.p95(), TAIL_MARGIN)));
        });
    checks.add(
        () ->
            assertTrue(
                startup.get(GlBackend.FFM) <= startup.get(GlBackend.JOGL) + STARTUP_ALLOWANCE_MS,
                () ->
                    "creating the context takes %.0f ms against %.0f ms, more than the %.0f ms allowed"
                        .formatted(
                            startup.get(GlBackend.FFM),
                            startup.get(GlBackend.JOGL),
                            STARTUP_ALLOWANCE_MS)));
    assertAll("the FFM binding against JOGL on " + renderer, checks);
  }

  private String renderer;

  /**
   * The median of several create-and-destroy cycles, so that loading the libraries — which either
   * binding pays once — does not land on whichever one happens to run first.
   */
  private static double contextCreationMillis(GlBackend backend) {
    double[] samples = new double[STARTUP_CYCLES];
    for (int i = 0; i < STARTUP_CYCLES; i++) {
      long start = System.nanoTime();
      GlSession.openOrAbort(backend).close();
      samples[i] = (System.nanoTime() - start) / 1_000_000.0;
    }
    Arrays.sort(samples);
    return percentile(samples, 50);
  }

  private static boolean within(double candidate, double reference, double margin) {
    return candidate <= reference * margin + NOISE_MS;
  }

  private static String regression(
      String name, String statistic, double candidate, double ref, double margin) {
    return "%s %s: %.2f ms against %.2f ms, more than the %.0f%% allowed"
        .formatted(name, statistic, candidate, ref, (margin - 1) * 100);
  }

  private Map<String, Timing> measure(GlSession session) {
    this.renderer = session.renderer();
    Gl gl = session.gl();
    Map<String, Timing> timings = new LinkedHashMap<>();
    for (Resolution resolution : RESOLUTIONS) {
      for (Case testCase : cases(gl.isComputeCapable())) {
        try (ReferenceScene scene =
            new ReferenceScene(gl, testCase.rayCaster(), resolution.width(), resolution.height())) {
          scene.setPassScale(testCase.passScale());
          timings.put(resolution.name() + " " + testCase.name(), time(gl, scene, testCase.frame()));
        }
      }
    }
    return timings;
  }

  /** Still and dragging, on the path the machine would really use and on the fallback. */
  private static List<Case> cases(boolean computeCapable) {
    List<Case> cases = new ArrayList<>();
    Frame composite = Frame.of("composite", RenderingType.COMPOSITE).shaded(); // NON-NLS
    Frame cinematic = Frame.of("cinematic", RenderingType.COMPOSITE).cinematicLit(); // NON-NLS
    if (computeCapable) {
      cases.add(new Case("compute composite", RayCaster.COMPUTE, composite, 1f)); // NON-NLS
      cases.add(new Case("compute cinematic", RayCaster.COMPUTE, cinematic, 1f)); // NON-NLS
    }
    cases.add(new Case("fbo composite", RayCaster.FBO, composite, 1f)); // NON-NLS
    cases.add(new Case("fbo composite dragging", RayCaster.FBO, composite, 0.5f)); // NON-NLS
    return cases;
  }

  private static Timing time(Gl gl, ReferenceScene scene, Frame frame) {
    for (int i = 0; i < WARMUP_FRAMES; i++) {
      scene.render(frame);
    }
    Timing quietest = null;
    for (int batch = 0; batch < BATCHES; batch++) {
      Timing timing = timeBatch(gl, scene, frame);
      if (quietest == null || timing.p95() < quietest.p95()) {
        quietest = timing;
      }
    }
    return quietest;
  }

  private static Timing timeBatch(Gl gl, ReferenceScene scene, Frame frame) {
    double[] samples = new double[TIMED_FRAMES];
    for (int i = 0; i < TIMED_FRAMES; i++) {
      gl.glFinish();
      long start = System.nanoTime();
      scene.draw(frame);
      gl.glFinish();
      samples[i] = (System.nanoTime() - start) / 1_000_000.0;
    }
    Arrays.sort(samples);
    return new Timing(percentile(samples, 50), percentile(samples, 95));
  }

  private static double percentile(double[] sorted, int percentile) {
    int index = Math.clamp(Math.round(percentile / 100f * sorted.length) - 1, 0, sorted.length - 1);
    return sorted[index];
  }

  private static String report(
      String renderer,
      Map<String, Timing> jogl,
      Map<String, Timing> ffm,
      Map<GlBackend, Double> startup) {
    StringBuilder out = new StringBuilder();
    out.append("Renderer: ").append(renderer).append("\n\n"); // NON-NLS
    out.append(BATCHES)
        .append(" batches of ")
        .append(TIMED_FRAMES)
        .append(" timed frames after ")
        .append(WARMUP_FRAMES)
        .append(", quietest batch kept, GPU drained around each draw. The gate allows ")
        .append(Math.round((MEDIAN_MARGIN - 1) * 100))
        .append("% over JOGL on the median and ")
        .append(Math.round((TAIL_MARGIN - 1) * 100))
        .append("% on the tail.\n\n"); // NON-NLS
    out.append("| Case | JOGL p50 | FFM p50 | JOGL p95 | FFM p95 |\n");
    out.append("|---|---:|---:|---:|---:|\n");
    jogl.forEach(
        (name, reference) -> {
          Timing candidate = ffm.get(name);
          out.append(
              String.format(
                  Locale.ROOT,
                  "| %s | %.2f | %.2f | %.2f | %.2f |%n",
                  name,
                  reference.p50(),
                  candidate.p50(),
                  reference.p95(),
                  candidate.p95()));
        });
    out.append(
        String.format(
            Locale.ROOT,
            "%nContext creation, median of %d cycles: JOGL %.0f ms, FFM %.0f ms.%n",
            STARTUP_CYCLES,
            startup.get(GlBackend.JOGL),
            startup.get(GlBackend.FFM)));
    return out.toString();
  }
}
