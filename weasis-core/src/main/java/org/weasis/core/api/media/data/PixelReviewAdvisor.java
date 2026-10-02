/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.api.media.data;

/**
 * Whether an image should be looked at before it leaves Weasis, because identity may be printed
 * into its pixels and nothing hides it. Masking profiles never reach those pixels, so the dialogs
 * that produce an image ask this before writing one.
 *
 * <p>The verdict needs the acquisition attributes and the regions already burned, which is DICOM
 * knowledge: the codec installs the implementation, and a host without it warns about nothing.
 */
@FunctionalInterface
public interface PixelReviewAdvisor {

  boolean requiresReview(MediaElement image, MediaSeries<?> series);

  /** Installed once by the bundle that can answer; the last one wins. */
  static void install(PixelReviewAdvisor advisor) {
    Holder.current = advisor;
  }

  /** False when no bundle can answer, so a host without the DICOM codec warns about nothing. */
  static boolean review(MediaElement image, MediaSeries<?> series) {
    PixelReviewAdvisor advisor = Holder.current;
    return advisor != null && image != null && advisor.requiresReview(image, series);
  }

  /** Holds the installed advisor; an interface cannot have a mutable field. */
  final class Holder {
    private static volatile PixelReviewAdvisor current; // NOSONAR reference swapped whole

    private Holder() {}
  }
}
