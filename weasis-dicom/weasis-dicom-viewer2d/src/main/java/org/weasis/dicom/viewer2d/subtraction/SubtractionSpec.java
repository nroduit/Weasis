/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.viewer2d.subtraction;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.dcm4che3.util.UIDUtils;
import org.weasis.core.util.StringUtil;

/**
 * What a subtraction computes: the series minus {@code factor} times the mask, plus an offset, on
 * the grid of the series. An operand is a series, or one phase of a dynamic series when the series
 * holds several. The same spec always names the same derived series and instances, so a subtraction
 * created twice is one object.
 *
 * @param seriesUid the series subtracted from (post-contrast)
 * @param maskUid the series subtracted (pre-contrast, mask)
 * @param phase the phase of the series that is subtracted from, 1-based, null for the whole series
 * @param maskPhase the phase of the mask series that is subtracted, 1-based, null for the whole
 *     series
 * @param factor weight of the mask, 1 subtracts it fully
 * @param offset value added to every difference
 * @param absolute whether the absolute difference is stored
 * @param signed whether the signed window is the default one instead of black below zero
 */
public record SubtractionSpec(
    String seriesUid,
    String maskUid,
    Integer phase,
    Integer maskPhase,
    double factor,
    double offset,
    boolean absolute,
    boolean signed) {

  public SubtractionSpec {
    if (!StringUtil.hasText(seriesUid) || !StringUtil.hasText(maskUid)) {
      throw new IllegalArgumentException("A subtraction needs a series and a mask");
    }
    if (seriesUid.equals(maskUid) && (maskPhase == null || maskPhase.equals(phase))) {
      throw new IllegalArgumentException("A series cannot be subtracted from itself");
    }
    if ((phase != null && phase < 1) || (maskPhase != null && maskPhase < 1)) {
      throw new IllegalArgumentException("A phase counts from 1");
    }
    if (!Double.isFinite(factor) || !Double.isFinite(offset)) {
      throw new IllegalArgumentException("The factor and the offset are finite numbers");
    }
  }

  /** A subtraction of whole series. */
  public SubtractionSpec(
      String seriesUid,
      String maskUid,
      double factor,
      double offset,
      boolean absolute,
      boolean signed) {
    this(seriesUid, maskUid, null, null, factor, offset, absolute, signed);
  }

  /** The plain difference: the series minus the mask. */
  public static SubtractionSpec of(String seriesUid, String maskUid) {
    return new SubtractionSpec(seriesUid, maskUid, 1.0, 0.0, false, false);
  }

  /** The same subtraction for another phase of the series. */
  public SubtractionSpec withPhase(Integer phase) {
    return new SubtractionSpec(
        seriesUid, maskUid, phase, maskPhase, factor, offset, absolute, signed);
  }

  /** Series Instance UID of the derived series. */
  public String derivedSeriesUid() {
    return UIDUtils.createNameBasedUID(key().getBytes(StandardCharsets.UTF_8));
  }

  /** SOP Instance UID of the derived slice built from the given source instance. */
  public String derivedInstanceUid(String sourceSopInstanceUid) {
    return UIDUtils.createNameBasedUID(
        (key() + "|" + sourceSopInstanceUid).getBytes(StandardCharsets.UTF_8));
  }

  /** Text of Derivation Description (0008,2111). */
  public String derivationDescription() {
    String text =
        String.format(
            Locale.ROOT,
            "Pixel by pixel subtraction, A - k*B + offset, k=%.2f, offset=%s, direct grid",
            factor,
            number(offset));
    if (phase != null || maskPhase != null) {
      text +=
          ", phase "
              + (phase == null ? "all" : phase)
              + " - phase "
              + (maskPhase == null ? "all" : maskPhase); // NON-NLS
    }
    return absolute ? text + ", absolute value" : text;
  }

  public JsonObjectBuilder toJson() {
    return Json.createObjectBuilder()
        .add("series", seriesUid) // NON-NLS
        .add("mask", maskUid) // NON-NLS
        .add("phase", phase == null ? 0 : phase) // NON-NLS
        .add("maskPhase", maskPhase == null ? 0 : maskPhase) // NON-NLS
        .add("factor", factor) // NON-NLS
        .add("offset", offset) // NON-NLS
        .add("absolute", absolute) // NON-NLS
        .add("signed", signed); // NON-NLS
  }

  /** The spec written by {@link #toJson()}. */
  public static SubtractionSpec fromJson(JsonObject json) {
    int phase = json.getInt("phase", 0); // NON-NLS
    int maskPhase = json.getInt("maskPhase", 0); // NON-NLS
    return new SubtractionSpec(
        json.getString("series"), // NON-NLS
        json.getString("mask"), // NON-NLS
        phase == 0 ? null : phase,
        maskPhase == 0 ? null : maskPhase,
        json.getJsonNumber("factor").doubleValue(), // NON-NLS
        json.getJsonNumber("offset").doubleValue(), // NON-NLS
        json.getBoolean("absolute", false), // NON-NLS
        json.getBoolean("signed", false)); // NON-NLS
  }

  private String key() {
    return String.join(
        "|",
        "weasis.subtraction", // NON-NLS
        seriesUid,
        maskUid,
        phase == null ? "" : phase.toString(),
        maskPhase == null ? "" : maskPhase.toString(),
        number(factor),
        number(offset),
        Boolean.toString(absolute),
        Boolean.toString(signed));
  }

  private static String number(double value) {
    return String.format(Locale.ROOT, "%.6g", value);
  }
}
