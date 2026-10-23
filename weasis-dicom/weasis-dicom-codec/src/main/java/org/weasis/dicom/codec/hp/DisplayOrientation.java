/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.codec.hp;

import java.util.Locale;
import java.util.Map;

/**
 * The rotation and flip that show an image with a requested patient orientation, as the Display Set
 * Patient Orientation of a hanging protocol asks (e.g. mammography views mirrored, chest walls back
 * to back: RCC {@code P\L}, LCC {@code A\R}, RMLO {@code P\F}, LMLO {@code A\F}).
 *
 * <p>Both orientations are DICOM Patient Orientation pairs: the direction of the rows (left to
 * right on screen) and of the columns (top to bottom). An oblique value such as {@code FL} counts
 * by its main direction, the first one. The transform follows the view: a clockwise rotation, then
 * a horizontal flip on screen.
 */
public final class DisplayOrientation {

  /**
   * A rotation and a flip of a view.
   *
   * @param rotation the clockwise rotation in degrees: 0, 90, 180 or 270
   * @param flip whether the view is flipped horizontally after the rotation
   */
  public record Framing(int rotation, boolean flip) {
    public static final Framing NONE = new Framing(0, false);
  }

  private static final Map<String, String> OPPOSITES =
      Map.ofEntries(
          Map.entry("R", "L"), // NON-NLS
          Map.entry("L", "R"), // NON-NLS
          Map.entry("A", "P"), // NON-NLS
          Map.entry("P", "A"), // NON-NLS
          Map.entry("H", "F"), // NON-NLS
          Map.entry("F", "H"), // NON-NLS
          Map.entry("RT", "LE"), // NON-NLS
          Map.entry("LE", "RT"), // NON-NLS
          Map.entry("CR", "CD"), // NON-NLS
          Map.entry("CD", "CR"), // NON-NLS
          Map.entry("V", "D"), // NON-NLS
          Map.entry("D", "V")); // NON-NLS

  private DisplayOrientation() {}

  /**
   * The framing that turns the image orientation into the wanted one: both directions when
   * possible, else the row direction, else the column direction; {@link Framing#NONE} when either
   * orientation is missing or nothing can be matched. Among equal choices, no flip and the smallest
   * rotation win.
   *
   * @param image the Patient Orientation of the image
   * @param wanted the Display Set Patient Orientation
   */
  public static Framing framing(String[] image, String[] wanted) {
    String row = direction(image, 0);
    String col = direction(image, 1);
    String wantedRow = direction(wanted, 0);
    String wantedCol = direction(wanted, 1);
    if ((row == null && col == null) || (wantedRow == null && wantedCol == null)) {
      return Framing.NONE;
    }
    Framing best = null;
    int bestScore = 0;
    for (boolean flip : new boolean[] {false, true}) {
      for (int rotation = 0; rotation < 360; rotation += 90) {
        String[] screen = screenDirections(row, col, rotation, flip);
        int score = 0;
        if (wantedRow != null && wantedRow.equals(screen[0])) {
          score += 2;
        }
        if (wantedCol != null && wantedCol.equals(screen[1])) {
          score += 1;
        }
        if (score > bestScore) {
          bestScore = score;
          best = new Framing(rotation, flip);
        }
      }
    }
    return best == null ? Framing.NONE : best;
  }

  /**
   * The patient directions toward the screen right and the screen bottom once the image is rotated
   * clockwise and then flipped horizontally; null when that axis has no known direction.
   */
  static String[] screenDirections(String row, String col, int rotation, boolean flip) {
    // screen vectors of the image axes: x (the row direction) and y (the column direction)
    int[] x = transform(1, 0, rotation, flip);
    int[] y = transform(0, 1, rotation, flip);
    String right = null;
    String down = null;
    if (x[0] != 0) {
      right = x[0] > 0 ? row : opposite(row);
      down = y[1] > 0 ? col : opposite(col);
    } else {
      right = y[0] > 0 ? col : opposite(col);
      down = x[1] > 0 ? row : opposite(row);
    }
    return new String[] {right, down};
  }

  private static int[] transform(int x, int y, int rotation, boolean flip) {
    int rx = x;
    int ry = y;
    for (int r = 0; r < rotation; r += 90) {
      // clockwise quarter turn on a screen whose y axis points down
      int t = rx;
      rx = -ry;
      ry = t;
    }
    return new int[] {flip ? -rx : rx, ry};
  }

  private static String opposite(String direction) {
    return direction == null ? null : OPPOSITES.get(direction);
  }

  /** The main direction of one value of a Patient Orientation, null when unknown. */
  static String direction(String[] orientation, int index) {
    if (orientation == null || orientation.length <= index || orientation[index] == null) {
      return null;
    }
    String value = orientation[index].trim().toUpperCase(Locale.ROOT);
    if (value.length() >= 2 && OPPOSITES.containsKey(value.substring(0, 2))) {
      return value.substring(0, 2);
    }
    return !value.isEmpty() && OPPOSITES.containsKey(value.substring(0, 1))
        ? value.substring(0, 1)
        : null;
  }
}
