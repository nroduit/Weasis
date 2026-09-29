/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.api.image.op;

import java.util.Objects;
import java.util.Scanner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Byte lookup tables (LUTs) in BGR order: inversion, and the parser of the legacy 256-line text
 * table used by the importer.
 */
public final class ByteLutCollection {

  private static final Logger LOGGER = LoggerFactory.getLogger(ByteLutCollection.class);

  private static final int BGR_BANDS = 3;
  private static final int STANDARD_LUT_SIZE = 256;
  private static final int BLUE_CHANNEL = 0;
  private static final int GREEN_CHANNEL = 1;
  private static final int RED_CHANNEL = 2;
  private static final int RGB_COMPONENTS = 3;
  private static final String WHITESPACE_PATTERN = "\\s+"; // NON-NLS

  private ByteLutCollection() {}

  /**
   * Inverts a lookup table by reversing the order of entries in each band.
   *
   * @param lut the lookup table to invert
   * @return inverted lookup table, or null if input is null
   * @throws IllegalArgumentException if LUT format is invalid
   */
  public static byte[][] invert(byte[][] lut) {
    if (lut == null) {
      return null;
    }
    validateLutFormat(lut);

    byte[][] invertedLut = new byte[lut.length][lut[0].length];
    for (int band = 0; band < lut.length; band++) {
      for (int i = 0; i < lut[band].length; i++) {
        invertedLut[band][i] = lut[band][lut[band].length - 1 - i];
      }
    }
    return invertedLut;
  }

  /**
   * Reads a LUT file from a Scanner and returns the parsed lookup table in BGR format. Lines
   * starting with '#' are treated as comments. Missing entries are filled with the last valid value
   * or identity mapping.
   *
   * @param scan the Scanner to read from
   * @return parsed lookup table in BGR format (3 bands × 256 entries)
   * @throws IllegalArgumentException if scanner is null
   */
  public static byte[][] readLutFile(Scanner scan) {
    Objects.requireNonNull(scan, "Scanner cannot be null");

    byte[][] lut = new byte[BGR_BANDS][STANDARD_LUT_SIZE];
    int lineIndex = 0;

    while (scan.hasNext() && lineIndex < STANDARD_LUT_SIZE) {
      String line = scan.nextLine().trim();

      if (line.isEmpty() || line.startsWith("#")) {
        continue;
      }

      if (parseLutLine(line, lut, lineIndex)) {
        lineIndex++;
      }
    }

    fillRemainingEntries(lut, lineIndex);
    return lut;
  }

  private static void validateLutFormat(byte[][] lut) {
    if (lut.length == 0) {
      throw new IllegalArgumentException("LUT must have at least one band");
    }

    int expectedLength = lut[0].length;
    for (byte[] band : lut) {
      if (band.length != expectedLength) {
        throw new IllegalArgumentException("All LUT bands must have the same length");
      }
    }
  }

  private static boolean parseLutLine(String line, byte[][] lut, int lineIndex) {
    String[] components = line.split(WHITESPACE_PATTERN);

    if (components.length != RGB_COMPONENTS) {
      LOGGER.debug("Invalid LUT line format at index {}: {}", lineIndex, line);
      return false;
    }

    try {
      lut[RED_CHANNEL][lineIndex] = parseColorComponent(components[0]);
      lut[GREEN_CHANNEL][lineIndex] = parseColorComponent(components[1]);
      lut[BLUE_CHANNEL][lineIndex] = parseColorComponent(components[2]);
      return true;
    } catch (NumberFormatException e) {
      LOGGER.debug("Invalid number format in LUT line at index {}: {}", lineIndex, line);
      return false;
    }
  }

  private static byte parseColorComponent(String component) {
    int value = Integer.parseInt(component.trim());
    return (byte) Math.clamp(value, 0, 255);
  }

  private static void fillRemainingEntries(byte[][] lut, int startIndex) {
    if (startIndex >= STANDARD_LUT_SIZE) {
      return;
    }

    for (int band = 0; band < BGR_BANDS; band++) {
      byte fillValue = startIndex > 0 ? lut[band][startIndex - 1] : 0;

      for (int i = startIndex; i < STANDARD_LUT_SIZE; i++) {
        lut[band][i] = startIndex > 0 ? fillValue : (byte) i;
      }
    }
  }
}
