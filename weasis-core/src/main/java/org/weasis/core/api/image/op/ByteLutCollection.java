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

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.List;
import java.util.Scanner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.util.FileUtil;
import org.weasis.opencv.op.lut.ByteLut;

/**
 * <code>ByteLutCollection</code> contains a collection of lookup tables (LUT) stored in BGR for
 * OpenCV.
 */
public class ByteLutCollection {
  private static final Logger LOGGER = LoggerFactory.getLogger(ByteLutCollection.class);

  private ByteLutCollection() {}

  public static byte[][] invert(byte[][] lut) {
    if (lut == null) {
      return null;
    }
    int bands = lut.length;
    int dynamic = lut[0].length - 1;
    byte[][] invertLut = new byte[bands][dynamic + 1];
    for (int j = 0; j < bands; j++) {
      for (int i = 0; i <= dynamic; i++) {
        invertLut[j][i] = lut[j][dynamic - i];
      }
    }
    return invertLut;
  }

  public static void readLutFilesFromResourcesDir(List<ByteLut> luts, File lutFolder) {
    if (luts != null && lutFolder != null && lutFolder.exists() && lutFolder.isDirectory()) {
      File[] files = lutFolder.listFiles();
      if (files != null) {
        for (File file : files) {
          if (file.isFile() && file.canRead()) {
            try (Scanner scan = new Scanner(file, StandardCharsets.UTF_8)) {
              byte[][] lut = readLutFile(scan);
              luts.add(new ByteLut(FileUtil.nameWithoutExtension(file.getName()), lut));
            } catch (Exception e) {
              LOGGER.error("Reading LUT file {}", file, e);
            }
          }
        }
        luts.sort(Comparator.comparing(ByteLut::name));
      }
    }
  }

  public static byte[][] readLutFile(Scanner scan) {
    final byte[][] lut = new byte[3][256];
    int lineIndex = 0;

    while (scan.hasNext()) {
      if (lineIndex >= 256) {
        break;
      }

      String[] line = scan.nextLine().split("\\s+"); // NON-NLS
      if (line.length == 3) {
        // Convert rgb to bgr
        lut[2][lineIndex] = (byte) Integer.parseInt(line[0]);
        lut[1][lineIndex] = (byte) Integer.parseInt(line[1]);
        lut[0][lineIndex] = (byte) Integer.parseInt(line[2]);
      }

      lineIndex++;
    }
    return lut;
  }
}
