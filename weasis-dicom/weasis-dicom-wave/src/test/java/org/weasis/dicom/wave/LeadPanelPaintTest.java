/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.wave;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.swing.UIManager;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.weasis.opencv.natives.NativeLibrary;
import org.weasis.opencv.waveform.NativeWaveformReader;
import org.weasis.opencv.waveform.WaveformSampleInterpretation;

/**
 * Paints lead panels into an image the way the viewer does (full repaints, cursor strips, zoomed
 * clips) over a 10-second ECG and a 14-minute recording. Checks that the signal is drawn and prints
 * the time of each scenario, so that a regression of the painting cost is visible in the build log.
 */
@DisplayNameGeneration(ReplaceUnderscores.class)
class LeadPanelPaintTest {

  private static final int CHANNELS = 12;
  private static final int LEAD_WIDTH = 1600;
  private static final int LEAD_HEIGHT = 70;

  private static WaveformWindowData shortData;
  private static WaveformWindowData longData;

  @BeforeAll
  static void setUp() {
    // The marker colours come from the look and feel, absent in a headless test
    for (String key : new String[] {"Red", "Yellow", "Green", "Blue", "Grey", "GreyInline"}) {
      UIManager.put("Actions." + key, java.awt.Color.ORANGE); // NON-NLS
    }
    loadOpenCV();
    shortData = open(1000, 10 * 1000);
    longData = open(500, 842 * 500);
  }

  @AfterAll
  static void tearDown() {
    if (shortData != null) {
      shortData.close();
    }
    if (longData != null) {
      longData.close();
    }
  }

  private static void loadOpenCV() {
    String property = System.getProperty("weasis.opencv.lib");
    if (property != null) {
      NativeLibrary.loadLibraryFromAbsolutePath(Path.of(property));
      return;
    }
    try {
      NativeLibrary.loadLibraryFromLibraryName();
      return;
    } catch (UnsatisfiedLinkError e) {
      // fall through to the sibling module
    }
    String os = System.getProperty("os.name").toLowerCase().startsWith("linux") ? "linux" : null;
    String arch = System.getProperty("os.arch");
    String cpu = "amd64".equals(arch) ? "x86-64" : "aarch64".equals(arch) ? "aarch64" : null;
    Path lib =
        os == null || cpu == null
            ? null
            : Path.of("..", "..", "weasis-opencv", "weasis-opencv-core-" + os + "-" + cpu)
                .resolve(Path.of("target", "classes", "natives", "libopencv_java.so"));
    Assumptions.assumeTrue(
        lib != null && Files.isRegularFile(lib), "OpenCV native library not available");
    NativeLibrary.loadLibraryFromAbsolutePath(lib);
  }

  /** An ECG-like signal: a slow baseline wander, a QRS spike every 0.8 s and some noise. */
  private static WaveformWindowData open(int frequency, int samples) {
    short[] values = new short[samples * CHANNELS];
    java.util.Random random = new java.util.Random(11);
    for (int i = 0; i < samples; i++) {
      double t = i / (double) frequency;
      double beat = t % 0.8;
      double qrs = beat < 0.04 ? 1500 * Math.sin(beat / 0.04 * Math.PI) : 0;
      double tWave = beat > 0.2 && beat < 0.4 ? 250 * Math.sin((beat - 0.2) / 0.2 * Math.PI) : 0;
      double wander = 100 * Math.sin(2 * Math.PI * 0.2 * t);
      for (int c = 0; c < CHANNELS; c++) {
        double v =
            (qrs + tWave) * (0.5 + c / (double) CHANNELS) + wander + random.nextGaussian() * 8;
        values[i * CHANNELS + c] = (short) Math.round(v);
      }
    }
    ByteBuffer buf = ByteBuffer.allocate(2 * values.length).order(ByteOrder.LITTLE_ENDIAN);
    buf.asShortBuffer().put(values);
    return new WaveformWindowData(
        NativeWaveformReader.ofMemory(
            buf, CHANNELS, WaveformSampleInterpretation.SS, false, frequency));
  }

  private static WaveView view(WaveformWindowData data, int frequency) {
    WaveView view = Mockito.mock(WaveView.class);
    Mockito.when(view.getMvCells()).thenReturn(4);
    Mockito.when(view.getSeconds()).thenReturn(data.getNbSamplesPerChannel() / (double) frequency);
    Mockito.when(view.getSamplesPerSecond()).thenReturn(frequency);
    Mockito.when(view.getZoomRatio()).thenReturn(1.0);
    Mockito.when(view.getFilter()).thenReturn(SignalFilter.NONE);
    Mockito.when(view.getPixelPerMm()).thenReturn(96 / 25.4);
    Mockito.when(view.getGutterWidth()).thenReturn(0);
    return view;
  }

  /** A real channel definition (lead I, 1 microvolt per unit): mocks would dominate the timings. */
  private static ChannelDefinition channel(int position) {
    Attributes dcm = new Attributes();
    Attributes source = new Attributes();
    source.setString(Tag.CodeValue, VR.SH, "2:1");
    source.setString(Tag.CodingSchemeDesignator, VR.SH, "MDC");
    source.setString(Tag.CodeMeaning, VR.LO, "Lead I");
    dcm.newSequence(Tag.ChannelSourceSequence, 1).add(source);
    dcm.setDouble(Tag.ChannelSensitivity, VR.DS, 1.0);
    dcm.setDouble(Tag.ChannelBaseline, VR.DS, 0.0);
    Attributes unit = new Attributes();
    unit.setString(Tag.CodeValue, VR.SH, "uV");
    unit.setString(Tag.CodingSchemeDesignator, VR.SH, "UCUM");
    unit.setString(Tag.CodeMeaning, VR.LO, "microvolt");
    dcm.newSequence(Tag.ChannelSensitivityUnitsSequence, 1).add(unit);
    return new ChannelDefinition(dcm, position);
  }

  private static List<LeadPanel> panels(WaveformWindowData data, int frequency, int width) {
    WaveView view = view(data, frequency);
    Mockito.when(view.getPixelsPerSecond())
        .thenReturn(width / (data.getNbSamplesPerChannel() / (double) frequency));
    List<LeadPanel> panels = new ArrayList<>();
    for (int c = 0; c < CHANNELS; c++) {
      LeadPanel panel = new LeadPanel(view, data, channel(c));
      panel.setTime(0, Integer.MAX_VALUE);
      panel.setPreferredSize(new Dimension(width, LEAD_HEIGHT));
      panel.setSize(width, LEAD_HEIGHT);
      panels.add(panel);
    }
    return panels;
  }

  /**
   * Paints every panel through the clip and returns the milliseconds of the best of 8 rounds after
   * 4 rounds of warm-up.
   */
  private static double paint(List<LeadPanel> panels, Rectangle clip, BufferedImage target) {
    double best = Double.MAX_VALUE;
    for (int round = -4; round < 8; round++) {
      long start = System.nanoTime();
      for (LeadPanel panel : panels) {
        Graphics2D g = target.createGraphics();
        try {
          g.setClip(clip);
          panel.paintComponent(g);
        } finally {
          g.dispose();
        }
      }
      if (round >= 0) {
        best = Math.min(best, (System.nanoTime() - start) / 1e6);
      }
    }
    return best;
  }

  private static boolean hasSignal(BufferedImage image, Rectangle clip) {
    int dark = 0;
    for (int y = clip.y; y < clip.y + clip.height; y++) {
      for (int x = clip.x; x < clip.x + clip.width; x++) {
        int rgb = image.getRGB(x, y) & 0xFFFFFF;
        if ((rgb & 0xFF) < 40 && ((rgb >> 8) & 0xFF) < 40 && ((rgb >> 16) & 0xFF) < 40) {
          dark++;
        }
      }
    }
    return dark > clip.width / 4;
  }

  private static void report(String scenario, double ms) {
    System.out.printf("[LeadPanelPaint] %-58s %8.2f ms%n", scenario, ms);
  }

  @Test
  void ten_second_ecg_full_repaint_and_cursor_strip() {
    List<LeadPanel> panels = panels(shortData, 1000, LEAD_WIDTH);
    BufferedImage image = new BufferedImage(LEAD_WIDTH, LEAD_HEIGHT, BufferedImage.TYPE_INT_RGB);
    Rectangle full = new Rectangle(0, 0, LEAD_WIDTH, LEAD_HEIGHT);
    double ms = paint(panels, full, image);
    report("10 s @ 1 kHz, 12 leads, full repaint (1600 px)", ms);
    assertTrue(hasSignal(image, full));

    Rectangle strip = new Rectangle(800, 0, 6, LEAD_HEIGHT);
    report("10 s @ 1 kHz, 12 leads, cursor strip (6 px)", paint(panels, strip, image));

    List<LeadPanel> zoomed = panels(shortData, 1000, LEAD_WIDTH * 4);
    BufferedImage big = new BufferedImage(LEAD_WIDTH * 4, LEAD_HEIGHT, BufferedImage.TYPE_INT_RGB);
    Rectangle clip = new Rectangle(2000, 0, LEAD_WIDTH, LEAD_HEIGHT);
    ms = paint(zoomed, clip, big);
    report("10 s @ 1 kHz, 12 leads, zoom x4, visible clip 1600 px", ms);
    assertTrue(hasSignal(big, clip));
  }

  @Test
  void fourteen_minute_recording_fit_to_width_and_at_paper_speed() throws InterruptedException {
    List<LeadPanel> panels = panels(longData, 500, LEAD_WIDTH);
    BufferedImage image = new BufferedImage(LEAD_WIDTH, LEAD_HEIGHT, BufferedImage.TYPE_INT_RGB);
    Rectangle full = new Rectangle(0, 0, LEAD_WIDTH, LEAD_HEIGHT);
    if (!longData.isSummaryComplete()) {
      double ms = paint(panels, full, image);
      report("14 min @ 500 Hz, 12 leads, fit to 1600 px, before the summary", ms);
      assertTrue(hasSignal(image, full));
    }
    long deadline = System.currentTimeMillis() + 30_000;
    while (!longData.isSummaryComplete() && System.currentTimeMillis() < deadline) {
      Thread.sleep(20);
    }
    assertTrue(longData.isSummaryComplete(), "summary not built within 30 s");
    double ms = paint(panels, full, image);
    report("14 min @ 500 Hz, 12 leads, fit to 1600 px (263 samples/px)", ms);
    assertTrue(hasSignal(image, full));

    Rectangle strip = new Rectangle(800, 0, 6, LEAD_HEIGHT);
    report("14 min @ 500 Hz, 12 leads, cursor strip (6 px)", paint(panels, strip, image));

    int paperWidth = (int) (842 * 25 * 96 / 25.4); // 25 mm/s at 96 dpi
    List<LeadPanel> paper = panels(longData, 500, paperWidth);
    BufferedImage big = new BufferedImage(LEAD_WIDTH, LEAD_HEIGHT, BufferedImage.TYPE_INT_RGB);
    Rectangle clip = new Rectangle(0, 0, LEAD_WIDTH, LEAD_HEIGHT);
    ms = paint(paper, clip, big);
    report("14 min @ 500 Hz, 12 leads, 25 mm/s, visible clip 1600 px", ms);
    assertTrue(hasSignal(big, clip));
  }
}
