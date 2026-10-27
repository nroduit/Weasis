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
import java.util.List;
import java.util.Objects;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.viewer2d.Messages;
import org.weasis.dicom.viewer2d.subtraction.SubtractionSpec.Dsa;

/**
 * An entry of the mask combo for an X-ray run: a mask taken from the frames of the run itself,
 * without its shift, which the sliders of the section carry. Two entries are the same mask when
 * their frames are, whatever their label.
 */
public record FrameMask(Dsa base, String label) {

  public FrameMask {
    base = base.withShift(0, 0);
  }

  /** The mask of that frame, labelled by its number. */
  public static FrameMask ofFrame(int frame) {
    return new FrameMask(
        Dsa.ofFrame(frame), Messages.getString("SubtractionTool.frame") + " " + frame);
  }

  /** The masks of a run: the one its DICOM encodes first, then every frame. */
  public static List<FrameMask> of(MediaSeries<DicomImageElement> series) {
    List<FrameMask> masks = new ArrayList<>();
    if (!DsaMask.isRun(series)) {
      return masks;
    }
    DsaMask.encodedMask(series)
        .ifPresent(
            dsa ->
                masks.add(
                    new FrameMask(
                        dsa,
                        Messages.getString("SubtractionTool.encoded_mask") + " " + dsa.label())));
    int count = series.size(null);
    for (int frame = 1; frame <= count; frame++) {
      FrameMask mask = ofFrame(frame);
      if (!masks.contains(mask)) {
        masks.add(mask);
      }
    }
    return masks;
  }

  /** The entry of the list with the frames of that mask, if any. */
  public static FrameMask find(List<?> entries, Dsa dsa) {
    if (dsa == null) {
      return null;
    }
    FrameMask wanted = new FrameMask(dsa, "");
    return entries.stream()
        .filter(FrameMask.class::isInstance)
        .map(FrameMask.class::cast)
        .filter(wanted::equals)
        .findFirst()
        .orElse(null);
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof FrameMask mask && base.equals(mask.base);
  }

  @Override
  public int hashCode() {
    return Objects.hash(base);
  }

  @Override
  public String toString() {
    return label;
  }
}
