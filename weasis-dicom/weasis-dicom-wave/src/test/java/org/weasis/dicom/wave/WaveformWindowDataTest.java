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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.weasis.opencv.natives.NativeLibrary;
import org.weasis.opencv.waveform.NativeWaveformReader;
import org.weasis.opencv.waveform.WaveformSampleInterpretation;

@DisplayNameGeneration(ReplaceUnderscores.class)
class WaveformWindowDataTest {

  private static final int CHANNELS = 3;

  /** Larger than one memory-mapped window of the reader, so the cache spans several windows. */
  private static final int SAMPLES = 400_000;

  private static short[] samples;

  @BeforeAll
  static void setUp() {
    loadOpenCV();
    Random random = new Random(7);
    samples = new short[SAMPLES * CHANNELS];
    for (int i = 0; i < samples.length; i++) {
      samples[i] = (short) (random.nextInt(4096) - 2048);
    }
  }

  /**
   * Weasis unit tests have no native library on {@code java.library.path}: use the {@code
   * weasis.opencv.lib} system property, then the library of the sibling OpenCV module of this
   * platform, and skip the tests when neither is available.
   */
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

  private static WaveformWindowData open() {
    ByteBuffer buf = ByteBuffer.allocate(2 * samples.length).order(ByteOrder.LITTLE_ENDIAN);
    buf.asShortBuffer().put(samples);
    return new WaveformWindowData(
        NativeWaveformReader.ofMemory(buf, CHANNELS, WaveformSampleInterpretation.SS, false, 500));
  }

  private static ChannelDefinition channel(int position) {
    ChannelDefinition channel = Mockito.mock(ChannelDefinition.class);
    Mockito.when(channel.getPosition()).thenReturn(position);
    Mockito.when(channel.getBaseline()).thenReturn(10.0);
    Mockito.when(channel.getAmplitudeUnitScalingFactor()).thenReturn(1.0);
    return channel;
  }

  @Test
  void samples_are_read_across_windows() {
    try (WaveformWindowData data = open()) {
      assertEquals(CHANNELS, data.getNbOfChannels());
      assertEquals(SAMPLES, data.getNbSamplesPerChannel());
      assertTrue(data.getWindow() < SAMPLES, "several windows expected");
      ChannelDefinition second = channel(1);
      int[] probes = {0, data.getWindow() - 1, data.getWindow(), 123_456, SAMPLES - 1};
      for (int index : probes) {
        assertEquals(samples[index * CHANNELS + 1], data.getRawSample(index, second));
        assertEquals(samples[index * CHANNELS + 1] + 10.0, data.getSample(index, second));
      }
      assertEquals(0, data.getRawSample(SAMPLES, second));
      assertEquals(0, data.getRawSample(-1, second));
    }
  }

  @Test
  void min_max_spans_window_boundaries_and_clips_to_the_recording() {
    try (WaveformWindowData data = open()) {
      ChannelDefinition third = channel(2);
      int start = data.getWindow() - 500;
      int count = 1200;
      int[] range = new int[2];
      data.rawMinMax(start, count, third, range);
      int min = Integer.MAX_VALUE;
      int max = Integer.MIN_VALUE;
      for (int i = start; i < start + count; i++) {
        min = Math.min(min, samples[i * CHANNELS + 2]);
        max = Math.max(max, samples[i * CHANNELS + 2]);
      }
      assertEquals(min, range[0]);
      assertEquals(max, range[1]);

      data.rawMinMax(SAMPLES - 10, 1000, third, range);
      min = Integer.MAX_VALUE;
      max = Integer.MIN_VALUE;
      for (int i = SAMPLES - 10; i < SAMPLES; i++) {
        min = Math.min(min, samples[i * CHANNELS + 2]);
        max = Math.max(max, samples[i * CHANNELS + 2]);
      }
      assertEquals(min, range[0]);
      assertEquals(max, range[1]);

      data.rawMinMax(SAMPLES + 5, 10, third, range);
      assertEquals(0, range[0]);
      assertEquals(0, range[1]);
    }
  }

  @Test
  void prefetch_then_read_gives_the_same_values() throws InterruptedException {
    try (WaveformWindowData data = open()) {
      data.prefetch(data.getWindow() * 2 - 10, 100);
      Thread.sleep(100);
      ChannelDefinition first = channel(0);
      int index = data.getWindow() * 2 + 5;
      assertEquals(samples[index * CHANNELS], data.getRawSample(index, first));
    }
  }
}
