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
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonNumber;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import org.dcm4che3.util.UIDUtils;
import org.weasis.core.util.StringUtil;

/**
 * What a subtraction computes: the series minus {@code factor} times the mask, plus an offset, on
 * the grid of the series. An operand is a series, or one phase of a dynamic series when the series
 * holds several. The same spec always names the same derived series and instances, so a subtraction
 * created twice is one object.
 *
 * @param seriesUid the series subtracted from (post-contrast)
 * @param maskUid the series subtracted (pre-contrast, mask); the series itself for a run
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
    boolean signed,
    Dsa dsa) {

  /**
   * The mask of an angiographic run taken from its own frames, as the Mask Subtraction Sequence
   * (PS3.3 C.7.6.10) encodes it: the frames averaged into one mask ({@code AVG_SUB}), or a
   * time-interval offset ({@code TID}: the mask of a frame is the frame that many positions before
   * it; a negative offset is {@code REV_TID}, the frame after), a range of frames the subtraction
   * applies to, and the sub-pixel shift of the mask, positive values moving it down and right.
   *
   * @param maskFrames frame numbers from 1, empty with an offset
   * @param tidOffset null with mask frames
   */
  public record Dsa(
      List<Integer> maskFrames,
      Integer tidOffset,
      Integer firstFrame,
      Integer lastFrame,
      double shiftRows,
      double shiftColumns) {

    public Dsa {
      maskFrames = maskFrames == null ? List.of() : List.copyOf(maskFrames);
      boolean averaged = !maskFrames.isEmpty();
      if (averaged == (tidOffset != null)) {
        throw new IllegalArgumentException("A DSA mask is mask frames or a TID offset");
      }
      if (tidOffset != null && tidOffset == 0) {
        throw new IllegalArgumentException("A TID offset is not zero");
      }
      if (maskFrames.stream().anyMatch(f -> f < 1)
          || (firstFrame != null && firstFrame < 1)
          || (lastFrame != null && firstFrame != null && lastFrame < firstFrame)) {
        throw new IllegalArgumentException("Frames count from 1 and a range is ordered");
      }
      if (!Double.isFinite(shiftRows) || !Double.isFinite(shiftColumns)) {
        throw new IllegalArgumentException("The shift is finite");
      }
    }

    /** The mask is one frame, unshifted. */
    public static Dsa ofFrame(int frame) {
      return new Dsa(List.of(frame), null, null, null, 0, 0);
    }

    public Dsa withFrames(List<Integer> frames) {
      return new Dsa(frames, null, firstFrame, lastFrame, shiftRows, shiftColumns);
    }

    public Dsa withShift(double rows, double columns) {
      return new Dsa(maskFrames, tidOffset, firstFrame, lastFrame, rows, columns);
    }

    public boolean isShifted() {
      return shiftRows != 0 || shiftColumns != 0;
    }

    /** Whether the subtraction applies to that frame (number from 1). */
    public boolean applies(int frame) {
      return (firstFrame == null || frame >= firstFrame)
          && (lastFrame == null || frame <= lastFrame);
    }

    /** The mask frames in one word: {@code 3}, {@code 2-4}, {@code 1,5}, or {@code TID 2}. */
    public String label() {
      if (tidOffset != null) {
        return (tidOffset < 0 ? "REV_TID " : "TID ") + Math.abs(tidOffset); // NON-NLS
      }
      List<Integer> sorted = maskFrames.stream().sorted().toList();
      boolean contiguous =
          sorted.size() > 1 && sorted.getLast() - sorted.getFirst() == sorted.size() - 1;
      if (contiguous) {
        return sorted.getFirst() + "-" + sorted.getLast();
      }
      return sorted.stream().map(String::valueOf).collect(Collectors.joining(","));
    }

    public JsonObjectBuilder toJson() {
      JsonObjectBuilder json = Json.createObjectBuilder();
      if (!maskFrames.isEmpty()) {
        JsonArrayBuilder frames = Json.createArrayBuilder();
        maskFrames.forEach(frames::add);
        json.add("maskFrames", frames); // NON-NLS
      }
      if (tidOffset != null) {
        json.add("tidOffset", tidOffset); // NON-NLS
      }
      if (firstFrame != null) {
        json.add("firstFrame", firstFrame); // NON-NLS
      }
      if (lastFrame != null) {
        json.add("lastFrame", lastFrame); // NON-NLS
      }
      return json.add("shiftRows", shiftRows).add("shiftColumns", shiftColumns); // NON-NLS
    }

    public static Dsa fromJson(JsonObject json) {
      List<Integer> frames =
          json.containsKey("maskFrames") // NON-NLS
              ? json.getJsonArray("maskFrames").getValuesAs(JsonNumber.class).stream() // NON-NLS
                  .map(JsonNumber::intValue)
                  .toList()
              : List.of();
      return new Dsa(
          frames,
          json.containsKey("tidOffset") ? json.getInt("tidOffset") : null, // NON-NLS
          json.containsKey("firstFrame") ? json.getInt("firstFrame") : null, // NON-NLS
          json.containsKey("lastFrame") ? json.getInt("lastFrame") : null, // NON-NLS
          json.getJsonNumber("shiftRows").doubleValue(), // NON-NLS
          json.getJsonNumber("shiftColumns").doubleValue()); // NON-NLS
    }

    private String key() {
      return String.join(
          "|",
          maskFrames.stream().map(String::valueOf).collect(Collectors.joining(",")),
          tidOffset == null ? "" : tidOffset.toString(),
          firstFrame == null ? "" : firstFrame.toString(),
          lastFrame == null ? "" : lastFrame.toString(),
          number(shiftRows),
          number(shiftColumns));
    }
  }

  public SubtractionSpec {
    if (!StringUtil.hasText(seriesUid) || !StringUtil.hasText(maskUid)) {
      throw new IllegalArgumentException("A subtraction needs a series and a mask");
    }
    if (dsa != null) {
      if (!seriesUid.equals(maskUid) || phase != null || maskPhase != null) {
        throw new IllegalArgumentException("A DSA mask is taken from the frames of the run");
      }
    } else if (seriesUid.equals(maskUid) && (maskPhase == null || maskPhase.equals(phase))) {
      throw new IllegalArgumentException("A series cannot be subtracted from itself");
    }
    if ((phase != null && phase < 1) || (maskPhase != null && maskPhase < 1)) {
      throw new IllegalArgumentException("A phase counts from 1");
    }
    if (!Double.isFinite(factor) || !Double.isFinite(offset)) {
      throw new IllegalArgumentException("The factor and the offset are finite numbers");
    }
  }

  /** A subtraction of two series or of two phases, not of the frames of a run. */
  public SubtractionSpec(
      String seriesUid,
      String maskUid,
      Integer phase,
      Integer maskPhase,
      double factor,
      double offset,
      boolean absolute,
      boolean signed) {
    this(seriesUid, maskUid, phase, maskPhase, factor, offset, absolute, signed, null);
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
        seriesUid, maskUid, phase, maskPhase, factor, offset, absolute, signed, dsa);
  }

  /** The run minus a mask taken from its own frames. */
  public static SubtractionSpec ofRun(String seriesUid, Dsa dsa, double factor) {
    return new SubtractionSpec(seriesUid, seriesUid, null, null, factor, 0.0, false, false, dsa);
  }

  public SubtractionSpec withDsa(Dsa dsa) {
    return new SubtractionSpec(
        seriesUid, maskUid, phase, maskPhase, factor, offset, absolute, signed, dsa);
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
    if (dsa != null) {
      text += ", mask frames " + dsa.label(); // NON-NLS
      if (dsa.isShifted()) {
        text +=
            String.format(
                Locale.ROOT,
                " shifted by %.2f rows %.2f columns",
                dsa.shiftRows(),
                dsa.shiftColumns()); // NON-NLS
      }
    }
    return absolute ? text + ", absolute value" : text;
  }

  public JsonObjectBuilder toJson() {
    JsonObjectBuilder json =
        Json.createObjectBuilder()
            .add("series", seriesUid) // NON-NLS
            .add("mask", maskUid) // NON-NLS
            .add("phase", phase == null ? 0 : phase) // NON-NLS
            .add("maskPhase", maskPhase == null ? 0 : maskPhase) // NON-NLS
            .add("factor", factor) // NON-NLS
            .add("offset", offset) // NON-NLS
            .add("absolute", absolute) // NON-NLS
            .add("signed", signed); // NON-NLS
    if (dsa != null) {
      json.add("dsa", dsa.toJson()); // NON-NLS
    }
    return json;
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
        json.getBoolean("signed", false), // NON-NLS
        json.get("dsa") instanceof JsonObject d ? Dsa.fromJson(d) : null); // NON-NLS
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
        Boolean.toString(signed),
        dsa == null ? "" : dsa.key());
  }

  private static String number(double value) {
    return String.format(Locale.ROOT, "%.6g", value);
  }
}
