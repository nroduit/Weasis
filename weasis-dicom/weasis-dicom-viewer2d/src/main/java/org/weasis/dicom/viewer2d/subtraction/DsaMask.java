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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Sequence;
import org.dcm4che3.data.Tag;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.codec.SortSeriesStack;
import org.weasis.dicom.codec.TagD;
import org.weasis.dicom.viewer2d.subtraction.SubtractionSpec.Dsa;

/**
 * The angiographic runs and what their DICOM says about subtracting them: the frames of a run in
 * acquisition order, the mask the Mask Subtraction Sequence (PS3.3 C.7.6.10) encodes and whether
 * the Recommended Viewing Mode (0028,1090) asks for the subtracted display.
 */
public final class DsaMask {

  private static final Set<String> RUN_MODALITIES = Set.of("XA", "RF", "XRF"); // NON-NLS

  private DsaMask() {}

  /** An X-ray run of at least two frames, the series a DSA mask is taken from. */
  public static boolean isRun(MediaSeries<DicomImageElement> series) {
    if (series == null || series.size(null) < 2) {
      return false;
    }
    String modality = TagD.getTagValue(series, Tag.Modality, String.class);
    if (modality == null) {
      DicomImageElement first = series.getMedia(0, null, null);
      modality = first == null ? null : TagD.getTagValue(first, Tag.Modality, String.class);
    }
    return modality != null && RUN_MODALITIES.contains(modality);
  }

  /** The frames of the run in acquisition order: frame 1 first. */
  public static List<DicomImageElement> frames(MediaSeries<DicomImageElement> series) {
    return series.copyOfMedias(null, SortSeriesStack.instanceNumber);
  }

  /** The number, from 1, of a frame of the run; 0 when the image is not one of its frames. */
  public static int frameOf(MediaSeries<DicomImageElement> series, DicomImageElement image) {
    return frames(series).indexOf(image) + 1;
  }

  /**
   * The mask the run itself encodes, when a Mask Subtraction Sequence names one ({@code AVG_SUB},
   * {@code TID} or {@code REV_TID}); a {@code NONE} operation or no sequence gives nothing.
   */
  public static Optional<Dsa> encodedMask(MediaSeries<DicomImageElement> series) {
    Attributes dicom = header(series);
    return dicom == null ? Optional.empty() : encodedMask(dicom);
  }

  /** As {@link #encodedMask(MediaSeries)} on the dataset of the run. */
  public static Optional<Dsa> encodedMask(Attributes dicom) {
    Sequence sequence = dicom.getSequence(Tag.MaskSubtractionSequence);
    if (sequence == null) {
      return Optional.empty();
    }
    for (Attributes item : sequence) {
      Dsa mask = maskOf(item);
      if (mask != null) {
        return Optional.of(mask);
      }
    }
    return Optional.empty();
  }

  private static Dsa maskOf(Attributes item) {
    String operation = item.getString(Tag.MaskOperation, "");
    int[] range = item.getInts(Tag.ApplicableFrameRange);
    Integer first = range != null && range.length > 0 ? range[0] : null;
    Integer last = range != null && range.length > 1 ? range[1] : null;
    float[] shift = item.getFloats(Tag.MaskSubPixelShift);
    // Mask Sub-pixel Shift is a vertical (row) then horizontal (column) pair; its sign is taken
    // as the shift of the mask, down and right for positive values
    double rows = shift != null && shift.length > 0 ? shift[0] : 0;
    double columns = shift != null && shift.length > 1 ? shift[1] : 0;
    switch (operation.toUpperCase()) {
      case "AVG_SUB": // NON-NLS
        int[] frames = item.getInts(Tag.MaskFrameNumbers);
        if (frames == null || frames.length == 0) {
          return null;
        }
        List<Integer> maskFrames = new ArrayList<>(frames.length);
        Arrays.stream(frames).filter(f -> f > 0).forEach(maskFrames::add);
        return maskFrames.isEmpty() ? null : new Dsa(maskFrames, null, first, last, rows, columns);
      case "TID": // NON-NLS
      case "REV_TID": // NON-NLS
        int offset = Math.abs(item.getInt(Tag.TIDOffset, 1));
        if (offset == 0) {
          return null;
        }
        boolean reverse = operation.equalsIgnoreCase("REV_TID"); // NON-NLS
        return new Dsa(List.of(), reverse ? -offset : offset, first, last, rows, columns);
      default:
        // NONE, or an operation this version does not know: the run is shown as acquired
        return null;
    }
  }

  /** Whether the run asks to be viewed subtracted: Recommended Viewing Mode {@code SUB}. */
  public static boolean recommendsSubtraction(MediaSeries<DicomImageElement> series) {
    Attributes dicom = header(series);
    return dicom != null
        && "SUB".equalsIgnoreCase(dicom.getString(Tag.RecommendedViewingMode, "")) // NON-NLS
        && encodedMask(dicom).isPresent();
  }

  /** The mask a reader starts from: the encoded one, else the first frame. */
  public static Dsa proposeMask(MediaSeries<DicomImageElement> series) {
    return encodedMask(series).orElse(Dsa.ofFrame(1));
  }

  private static Attributes header(MediaSeries<DicomImageElement> series) {
    if (!isRun(series)) {
      return null;
    }
    DicomImageElement first = series.getMedia(0, null, null);
    return first == null || first.getMediaReader() == null
        ? null
        : first.getMediaReader().getDicomObject();
  }
}
