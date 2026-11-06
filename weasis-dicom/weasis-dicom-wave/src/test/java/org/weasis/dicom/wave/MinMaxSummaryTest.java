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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;

@DisplayNameGeneration(ReplaceUnderscores.class)
class MinMaxSummaryTest {

  private static final int CHANNELS = 3;
  private static final int SAMPLES = 100_003; // not a multiple of any block

  private static int[] signal() {
    Random random = new Random(3);
    int[] values = new int[SAMPLES * CHANNELS];
    for (int i = 0; i < values.length; i++) {
      values[i] = random.nextInt(20_000) - 10_000;
    }
    return values;
  }

  private static int[] exact(int[] values, int start, int end, int channel) {
    int min = Integer.MAX_VALUE;
    int max = Integer.MIN_VALUE;
    for (int i = start; i < end; i++) {
      min = Math.min(min, values[i * CHANNELS + channel]);
      max = Math.max(max, values[i * CHANNELS + channel]);
    }
    return new int[] {min, max};
  }

  @Test
  void base_block_bounds_the_number_of_blocks() {
    MinMaxSummary summary = new MinMaxSummary(CHANNELS, SAMPLES, 1000);
    assertEquals(128, summary.baseBlock()); // 100003 / 128 = 782 blocks, / 64 = 1563
    assertEquals(MinMaxSummary.MIN_BLOCK, new MinMaxSummary(CHANNELS, 100, 1000).baseBlock());
    assertTrue(summary.levels() >= 10);
  }

  @Test
  void ranges_are_bounded_by_the_exact_extrema_and_rounded_to_a_quarter_of_their_length() {
    int[] values = signal();
    MinMaxSummary summary = new MinMaxSummary(CHANNELS, SAMPLES, 4096);
    // fed in windows of an odd size, like decoder windows unrelated to the block
    int window = 777;
    for (int fed = 0; fed < SAMPLES; fed += window) {
      int count = Math.min(window, SAMPLES - fed);
      int[] chunk = new int[count * CHANNELS];
      System.arraycopy(values, fed * CHANNELS, chunk, 0, chunk.length);
      summary.append(chunk, count);
    }
    summary.finish();
    assertTrue(summary.isFinished());
    assertEquals(SAMPLES, summary.covered());

    Random random = new Random(5);
    int[] out = new int[2];
    for (int n = 0; n < 2000; n++) {
      int count = 2 * summary.baseBlock() + random.nextInt(20_000);
      int start = random.nextInt(SAMPLES) - 100;
      int channel = random.nextInt(CHANNELS);
      boolean answered = summary.minMax(start, count, channel, out);
      int first = Math.max(0, start);
      int end = Math.min(SAMPLES, start + count);
      if (end <= first) {
        assertFalse(answered);
        continue;
      }
      assertTrue(answered);
      int[] inner = exact(values, first, end, channel);
      // the summary covers at least the range
      assertTrue(out[0] <= inner[0], "min above the exact minimum");
      assertTrue(out[1] >= inner[1], "max below the exact maximum");
      // and at most a quarter of its length more on each side (the base block at least)
      int margin = Math.max(summary.baseBlock(), (end - first) / 4);
      int[] outer =
          exact(values, Math.max(0, first - margin), Math.min(SAMPLES, end + margin), channel);
      assertTrue(out[0] >= outer[0], "min outside the rounded range");
      assertTrue(out[1] <= outer[1], "max outside the rounded range");
    }
    // the whole recording, from the top level
    summary.minMax(0, SAMPLES, 1, out);
    assertEquals(exact(values, 0, SAMPLES, 1)[0], out[0]);
    assertEquals(exact(values, 0, SAMPLES, 1)[1], out[1]);
  }

  @Test
  void a_summary_being_built_answers_only_the_covered_ranges() {
    int[] values = signal();
    MinMaxSummary summary = new MinMaxSummary(CHANNELS, SAMPLES, 4096);
    int fed = 40_000;
    summary.append(values, fed);
    int block = summary.baseBlock();
    int covered = fed / block * block;
    assertEquals(covered, summary.covered());
    assertTrue(summary.covers(0, covered));
    assertFalse(summary.covers(0, covered + 1));
    assertTrue(summary.covers(covered - 10, 10));

    int[] out = new int[2];
    assertFalse(summary.minMax(covered - 100, 200, 0, out));
    assertTrue(summary.minMax(0, covered, 0, out));
    int[] inner = exact(values, 0, covered, 0);
    assertEquals(inner[0], out[0]);
    assertEquals(inner[1], out[1]);

    // a range ending right at the frontier can only use complete upper blocks
    int count = 8 * block + 3;
    assertTrue(summary.minMax(covered - count, count, 2, out));
    inner = exact(values, covered - count, covered, 2);
    assertTrue(out[0] <= inner[0]);
    assertTrue(out[1] >= inner[1]);
    int[] outer = exact(values, covered - count - count / 4 - block, covered, 2);
    assertTrue(out[0] >= outer[0]);
    assertTrue(out[1] <= outer[1]);
  }
}
