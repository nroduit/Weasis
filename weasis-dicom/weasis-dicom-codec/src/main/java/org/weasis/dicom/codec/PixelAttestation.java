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

import java.time.Instant;
import java.util.Objects;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.api.media.data.Taggable;
import org.weasis.core.util.StringUtil;

/**
 * A person's statement that the pixels of an image carry no burned-in identity, for images whose
 * attributes say nothing ({@link PixelPrivacy#UNKNOWN}) on a modality that often has some. It
 * stands in for Burned In Annotation (0028,0301) = NO when the file cannot be changed, as in a test
 * corpus where the bytes are what is tested.
 *
 * <p>Held in memory on the image or its series, like a {@link Redaction} mask, and lost when the
 * study is unloaded: whoever relays a review keeps it (a catalog, a worklist) and states it again
 * on the next load. It never overrides an image that declares burned-in identity: that one needs a
 * redaction.
 *
 * @param by who reviewed the pixels
 * @param reason what the review was, or null
 * @param at when it was stated in this session
 */
public record PixelAttestation(String by, String reason, Instant at) {

  public PixelAttestation {
    if (!StringUtil.hasText(by)) {
      throw new IllegalArgumentException("an attestation names who reviewed the pixels");
    }
    by = by.strip();
    reason = StringUtil.hasText(reason) ? reason.strip() : null;
    Objects.requireNonNull(at);
  }

  public static PixelAttestation of(String by, String reason) {
    return new PixelAttestation(by, reason, Instant.now());
  }

  /** The attestation of the image, otherwise the one of its series; null when there is none. */
  public static PixelAttestation effective(ImageElement image, MediaSeries<?> series) {
    PixelAttestation own = read(image);
    return own != null ? own : read(series);
  }

  /**
   * Whether an attestation answers the review of that image: one exists, and the image does not
   * declare burned-in identity itself.
   */
  public static boolean covers(ImageElement image, MediaSeries<?> series) {
    return image != null
        && effective(image, series) != null
        && PixelPrivacy.of(image) != PixelPrivacy.BURNED_IN;
  }

  public static PixelAttestation read(Taggable taggable) {
    return taggable != null
            && taggable.getTagValue(TagW.PixelAttestation) instanceof PixelAttestation attestation
        ? attestation
        : null;
  }

  public static void write(Taggable taggable, PixelAttestation attestation) {
    if (taggable != null) {
      taggable.setTag(TagW.PixelAttestation, attestation);
    }
  }

  /** Withdraws the attestation of that image or series; the one of the series stays for a frame. */
  public static void clear(Taggable taggable) {
    write(taggable, null);
  }
}
