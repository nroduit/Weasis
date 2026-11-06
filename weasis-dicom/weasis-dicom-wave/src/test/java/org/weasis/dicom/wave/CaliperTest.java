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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Dimension;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
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

/** Measures calipers on a synthetic 75 bpm rhythm at 500 Hz (R every 400 samples). */
@DisplayNameGeneration(ReplaceUnderscores.class)
class CaliperTest {

  private static final int FS = 500;
  private static final int SAMPLES = 10 * FS;
  private static WaveformWindowData data;
  private static LeadPanel lead;
  private static WaveView view;

  @BeforeAll
  static void setUp() {
    for (String key : new String[] {"Red", "Yellow", "Green", "Blue", "Grey", "GreyInline"}) {
      javax.swing.UIManager.put("Actions." + key, java.awt.Color.ORANGE); // NON-NLS
    }
    loadOpenCV();
    short[] values = new short[SAMPLES];
    for (int i = 0; i < SAMPLES; i++) {
      double t = i / (double) FS;
      double beat = t % 0.8;
      double qrs = beat < 0.04 ? 1500 * Math.sin(beat / 0.04 * Math.PI) : 0;
      double tWave = beat > 0.2 && beat < 0.4 ? 250 * Math.sin((beat - 0.2) / 0.2 * Math.PI) : 0;
      values[i] = (short) Math.round(qrs + tWave);
    }
    ByteBuffer buf = ByteBuffer.allocate(2 * values.length).order(ByteOrder.LITTLE_ENDIAN);
    buf.asShortBuffer().put(values);
    data =
        new WaveformWindowData(
            NativeWaveformReader.ofMemory(buf, 1, WaveformSampleInterpretation.SS, false, FS));
    view = Mockito.mock(WaveView.class);
    Mockito.when(view.getMvCells()).thenReturn(4);
    Mockito.when(view.getSeconds()).thenReturn(10.0);
    Mockito.when(view.getSamplesPerSecond()).thenReturn(FS);
    Mockito.when(view.getZoomRatio()).thenReturn(1.0);
    Mockito.when(view.getFilter()).thenReturn(SignalFilter.NONE);
    Mockito.when(view.getPixelPerMm()).thenReturn(96 / 25.4);
    Mockito.when(view.getPixelsPerSecond()).thenReturn(100.0);
    lead = new LeadPanel(view, data, channel());
    lead.setTime(0, Integer.MAX_VALUE);
    lead.setPreferredSize(new Dimension(1000, 200));
    lead.setSize(1000, 200);
  }

  @AfterAll
  static void tearDown() {
    if (data != null) {
      data.close();
    }
  }

  private static void loadOpenCV() {
    try {
      NativeLibrary.loadLibraryFromLibraryName();
      return;
    } catch (UnsatisfiedLinkError e) {
      // fall through to the sibling module
    }
    String arch = System.getProperty("os.arch");
    String cpu = "amd64".equals(arch) ? "x86-64" : "aarch64".equals(arch) ? "aarch64" : null;
    Path lib =
        cpu == null
            ? null
            : Path.of("..", "..", "weasis-opencv", "weasis-opencv-core-linux-" + cpu)
                .resolve(Path.of("target", "classes", "natives", "libopencv_java.so"));
    Assumptions.assumeTrue(
        lib != null && Files.isRegularFile(lib), "OpenCV native library not available");
    NativeLibrary.loadLibraryFromAbsolutePath(lib);
  }

  private static ChannelDefinition channel() {
    Attributes dcm = new Attributes();
    Attributes source = new Attributes();
    source.setString(Tag.CodeValue, VR.SH, "2:2");
    source.setString(Tag.CodingSchemeDesignator, VR.SH, "MDC");
    source.setString(Tag.CodeMeaning, VR.LO, "Lead II");
    dcm.newSequence(Tag.ChannelSourceSequence, 1).add(source);
    dcm.setDouble(Tag.ChannelSensitivity, VR.DS, 1.0);
    dcm.setDouble(Tag.ChannelBaseline, VR.DS, 0.0);
    Attributes unit = new Attributes();
    unit.setString(Tag.CodeValue, VR.SH, "uV");
    unit.setString(Tag.CodingSchemeDesignator, VR.SH, "UCUM");
    unit.setString(Tag.CodeMeaning, VR.LO, "microvolt");
    dcm.newSequence(Tag.ChannelSensitivityUnitsSequence, 1).add(unit);
    return new ChannelDefinition(dcm, 0);
  }

  @Test
  void a_caliper_over_one_interval_gives_its_duration_and_rate() {
    Caliper c = lead.addTimeCaliper(10, 410, "RR");
    assertEquals(0.8, c.getDuration(), 1e-9);
    assertEquals(75, c.getRate(), 1e-9);
    // from the R peak (1.5 mV) down to the isoelectric line
    assertEquals(1.5, c.getPeakToPeak(), 0.01);
    assertEquals("RR", c.getLabel());
    lead.removeCaliper(c);
  }

  @Test
  void a_caliper_over_several_beats_counts_them_and_gives_the_mean_rr() {
    // R peaks at samples 10, 410, 810, 1210, 1610: span from just before the first to after the
    // fifth
    Caliper c = lead.addTimeCaliper(0, 1650, null);
    assertEquals(5, c.getBeats());
    assertEquals(0.8, c.getMeanRR(), 0.01);
    assertEquals(75, c.getMeanRate(), 1.0);
    lead.removeCaliper(c);
  }

  @Test
  void calipers_paint_without_error() {
    Caliper time = lead.addTimeCaliper(10, 1650, "RR");
    Caliper amplitude = lead.addAmplitudeCaliper(500, 0.1, 0.35, "ST");
    java.awt.image.BufferedImage image =
        new java.awt.image.BufferedImage(1000, 200, java.awt.image.BufferedImage.TYPE_INT_RGB);
    java.awt.Graphics2D g2 = image.createGraphics();
    try {
      lead.paintComponent(g2);
    } finally {
      g2.dispose();
    }
    // the blue amplitude levels cross the lead: some blue pixels on the left margin
    boolean blue = false;
    for (int y = 0; y < 200 && !blue; y++) {
      int rgb = image.getRGB(2, y);
      blue = ((rgb >> 16) & 0xFF) < 60 && (rgb & 0xFF) > 150;
    }
    assertTrue(blue, "amplitude levels drawn");
    lead.removeCaliper(time);
    lead.removeCaliper(amplitude);
  }

  @Test
  void an_amplitude_caliper_measures_the_difference_of_its_levels() {
    Caliper c = lead.addAmplitudeCaliper(500, 0.1, 0.35, "ST");
    assertEquals(0.25, c.getDelta(), 1e-9);
    assertNull(c.getDuration());
    assertTrue(lead.getCalipers().contains(c));
    lead.removeAllCalipers();
    assertTrue(lead.getCalipers().isEmpty());
  }
}
