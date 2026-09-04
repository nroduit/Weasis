/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.viewer2d.mpr.cmpr;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.function.IntFunction;
import org.joml.Vector3d;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.weasis.dicom.viewer2d.mpr.Volume;
import org.weasis.dicom.viewer2d.mpr.VolumeByte;
import org.weasis.dicom.viewer2d.mpr.VolumeShort;

@DisplayNameGeneration(ReplaceUnderscores.class)
class VolumeSamplerTest {
  private static final int SIZE = 12;
  private static final int WIDTH = 9; // not square, so rows and columns cannot be swapped
  private static final int HEIGHT = 5;

  // An oblique plane that leaves the volume on one side
  private static final VolumeSampler.PixelLocator OBLIQUE =
      (column, row, voxel) -> voxel.set(column * 1.3 - 1.0, row * 0.7 + 2.25, 3.0 + column * 0.4);

  @Test
  void unsigned_16_bit_pixels_match_the_interpolation_of_the_volume() throws IOException {
    var volume = new VolumeShort(SIZE, SIZE, SIZE, false, 1, null);
    fill(volume, (out, i) -> out.writeShort(20000 + i * 23)); // above the signed range

    short[] pixels =
        assertInstanceOf(short[].class, VolumeSampler.samplePixels(volume, WIDTH, HEIGHT, OBLIQUE));

    assertMatchesVolume(volume, pixels.length, i -> pixels[i]);
  }

  @Test
  void byte_pixels_match_the_interpolation_of_the_volume() throws IOException {
    var volume = new VolumeByte(SIZE, SIZE, SIZE, false, 1, null);
    fill(volume, (out, i) -> out.writeByte(i * 7));

    byte[] pixels =
        assertInstanceOf(byte[].class, VolumeSampler.samplePixels(volume, WIDTH, HEIGHT, OBLIQUE));

    assertMatchesVolume(volume, pixels.length, i -> pixels[i]);
  }

  @FunctionalInterface
  private interface VoxelWriter {
    void write(DataOutputStream out, int index) throws IOException;
  }

  private static void fill(Volume<?, ?> volume, VoxelWriter writer) throws IOException {
    var bytes = new ByteArrayOutputStream();
    try (var out = new DataOutputStream(bytes)) {
      for (int i = 0; i < SIZE * SIZE * SIZE; i++) {
        writer.write(out, i);
      }
    }
    try (var in = new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
      for (int z = 0; z < SIZE; z++) {
        for (int y = 0; y < SIZE; y++) {
          for (int x = 0; x < SIZE; x++) {
            volume.readVolume(in, x, y, z);
          }
        }
      }
    }
  }

  private static void assertMatchesVolume(
      Volume<?, ?> volume, int length, IntFunction<Number> pixel) {
    assertEquals(WIDTH * HEIGHT, length);
    var voxel = new Vector3d();
    int outside = 0;
    for (int row = 0; row < HEIGHT; row++) {
      for (int column = 0; column < WIDTH; column++) {
        OBLIQUE.locate(column, row, voxel);
        Number expected = volume.getInterpolatedValueFromSource(voxel.x, voxel.y, voxel.z, 0);
        if (expected == null) {
          expected = volume.getPhotometricMinValue();
          outside++;
        }
        assertEquals(
            expected.longValue(),
            pixel.apply(row * WIDTH + column).longValue(),
            "column " + column + ", row " + row);
      }
    }
    assertTrue(outside > 0 && outside < WIDTH * HEIGHT, "the plane must cross the border");
  }
}
