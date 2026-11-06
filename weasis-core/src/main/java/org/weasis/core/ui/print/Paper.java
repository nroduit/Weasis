/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.print;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import javax.print.attribute.standard.MediaSizeName;

/**
 * A paper or film size in millimeters, portrait, with its DICOM Film Size ID (PS3.3 C.13.3) and its
 * Java print media name when they exist.
 */
public record Paper(
    String id, double widthMm, double heightMm, String filmSizeId, MediaSizeName media) {

  public static final double MM_PER_INCH = 25.4;
  public static final double POINTS_PER_INCH = 72.0;

  public static final Paper A3 = new Paper("A3", 297, 420, "A3", MediaSizeName.ISO_A3); // NON-NLS
  public static final Paper A4 = new Paper("A4", 210, 297, "A4", MediaSizeName.ISO_A4); // NON-NLS
  public static final Paper A5 = new Paper("A5", 148, 210, null, MediaSizeName.ISO_A5); // NON-NLS
  public static final Paper LETTER =
      new Paper("Letter", 215.9, 279.4, "8_5INX11IN", MediaSizeName.NA_LETTER); // NON-NLS
  public static final Paper LEGAL =
      new Paper("Legal", 215.9, 355.6, null, MediaSizeName.NA_LEGAL); // NON-NLS
  public static final Paper TABLOID =
      new Paper("Tabloid", 279.4, 431.8, "11INX17IN", MediaSizeName.TABLOID); // NON-NLS
  public static final Paper IN_8X10 = inches("8x10in", 8, 10, "8INX10IN"); // NON-NLS
  public static final Paper IN_10X12 = inches("10x12in", 10, 12, "10INX12IN"); // NON-NLS
  public static final Paper IN_10X14 = inches("10x14in", 10, 14, "10INX14IN"); // NON-NLS
  public static final Paper IN_11X14 = inches("11x14in", 11, 14, "11INX14IN"); // NON-NLS
  public static final Paper IN_14X14 = inches("14x14in", 14, 14, "14INX14IN"); // NON-NLS
  public static final Paper IN_14X17 = inches("14x17in", 14, 17, "14INX17IN"); // NON-NLS
  public static final Paper CM_24X24 = new Paper("24x24cm", 240, 240, "24CMX24CM", null); // NON-NLS
  public static final Paper CM_24X30 = new Paper("24x30cm", 240, 300, "24CMX30CM", null); // NON-NLS

  public static final List<Paper> CATALOGUE =
      List.of(
          A4, A3, A5, LETTER, LEGAL, TABLOID, IN_8X10, IN_10X12, IN_10X14, IN_11X14, IN_14X14,
          IN_14X17, CM_24X24, CM_24X30);

  /** Half a millimeter: the rounding of the sizes the print services report. */
  private static final double MATCH_TOLERANCE_MM = 0.5;

  public Paper {
    if (widthMm <= 0 || heightMm <= 0) {
      throw new IllegalArgumentException("Paper size must be positive");
    }
  }

  private static Paper inches(String id, double width, double height, String filmSizeId) {
    return new Paper(id, width * MM_PER_INCH, height * MM_PER_INCH, filmSizeId, null);
  }

  /** The catalogue paper of this size, whatever the orientation, or a custom one. */
  public static Paper fromMm(double widthMm, double heightMm) {
    double shortSide = Math.min(widthMm, heightMm);
    double longSide = Math.max(widthMm, heightMm);
    return CATALOGUE.stream()
        .filter(
            p ->
                Math.abs(p.widthMm - shortSide) <= MATCH_TOLERANCE_MM
                    && Math.abs(p.heightMm - longSide) <= MATCH_TOLERANCE_MM)
        .findFirst()
        .orElseGet(() -> custom(shortSide, longSide));
  }

  /** The catalogue paper of this size in printer points (1/72 inch), or a custom one. */
  public static Paper fromPoints(double widthPt, double heightPt) {
    return fromMm(pointsToMm(widthPt), pointsToMm(heightPt));
  }

  public static Optional<Paper> fromFilmSizeId(String filmSizeId) {
    return CATALOGUE.stream()
        .filter(p -> filmSizeId != null && filmSizeId.equals(p.filmSizeId))
        .findFirst();
  }

  public static Optional<Paper> fromMedia(MediaSizeName media) {
    return CATALOGUE.stream().filter(p -> media != null && media.equals(p.media)).findFirst();
  }

  /** The catalogue paper closest to this size by area difference. */
  public static Paper nearest(double widthMm, double heightMm) {
    double shortSide = Math.min(widthMm, heightMm);
    double longSide = Math.max(widthMm, heightMm);
    return CATALOGUE.stream()
        .min(
            Comparator.comparingDouble(
                p -> Math.abs(p.widthMm - shortSide) + Math.abs(p.heightMm - longSide)))
        .orElse(A4);
  }

  public static Paper custom(double widthMm, double heightMm) {
    double shortSide = Math.min(widthMm, heightMm);
    double longSide = Math.max(widthMm, heightMm);
    String id = String.format("%.0fx%.0fmm", shortSide, longSide); // NON-NLS
    return new Paper(id, shortSide, longSide, null, null);
  }

  public static double pointsToMm(double points) {
    return points / POINTS_PER_INCH * MM_PER_INCH;
  }

  public static double mmToPoints(double mm) {
    return mm / MM_PER_INCH * POINTS_PER_INCH;
  }

  public boolean hasFilmSizeId() {
    return filmSizeId != null;
  }

  @Override
  public String toString() {
    return id;
  }
}
