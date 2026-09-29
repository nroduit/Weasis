/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.codec;

import java.util.Set;
import org.dcm4che3.data.Tag;
import org.weasis.core.api.media.data.TagReadable;

/**
 * Whether identity may be present in the pixels themselves, where no {@code IdentityMask} can reach
 * it. Derived from Burned In Annotation (0028,0301) and Recognizable Visual Features (0028,0302).
 *
 * <p>Both are Type 3 and widely omitted, so an absent value is {@link #UNKNOWN}, never {@link
 * #CLEAN}. Treating silence as a guarantee is the mistake this type exists to prevent.
 */
public enum PixelPrivacy {
  /** The image declares burned-in text or recognizable features. */
  BURNED_IN,
  /** The image declares that it has neither. */
  CLEAN,
  /** The image says nothing; assume the worst when it matters. */
  UNKNOWN;

  /**
   * Modalities whose images commonly carry burned-in identity even without declaring it:
   * ultrasound, angiography, external/visible-light photography, and anything already
   * secondary-captured.
   */
  private static final Set<String> HIGH_RISK_MODALITIES =
      Set.of("US", "XA", "XC", "SC", "OT", "ES", "GM", "OP"); // NON-NLS

  private static final String YES = "YES"; // NON-NLS

  /** Verdict for a single image, series or study group. */
  public static PixelPrivacy of(TagReadable taggable) {
    if (taggable == null) {
      return UNKNOWN;
    }
    String burnedIn = TagD.getTagValue(taggable, Tag.BurnedInAnnotation, String.class);
    String features = TagD.getTagValue(taggable, Tag.RecognizableVisualFeatures, String.class);
    if (isYes(burnedIn) || isYes(features)) {
      return BURNED_IN;
    }
    return burnedIn == null && features == null ? UNKNOWN : CLEAN;
  }

  /**
   * True when the pixels must be reviewed before the image leaves Weasis: either the image declares
   * burned-in identity, or it declares nothing and comes from a modality that often has it.
   */
  public static boolean requiresReview(TagReadable taggable, String modality) {
    PixelPrivacy privacy = of(taggable);
    return privacy == BURNED_IN
        || (privacy == UNKNOWN && modality != null && HIGH_RISK_MODALITIES.contains(modality));
  }

  private static boolean isYes(String value) {
    return value != null && YES.equalsIgnoreCase(value.trim());
  }
}
