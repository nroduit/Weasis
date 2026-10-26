/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.editor;

import java.util.Locale;
import java.util.Objects;

/**
 * The names of the content facts of a {@link ViewContext}: what the selected series is capable of,
 * published by the plugin that knows the data (the DICOM explorer derives them from the series
 * features and the objects of the study) and read by the relevance predicates of every plugin
 * without a dependency on it. All of them are scalars, so they travel in the JSON of {@code
 * workspace:context}.
 */
public final class ContextFacts {

  /** Boolean: a stack of images of one plane that MPR and 3D can use. */
  public static final String VOLUME = "series.volume"; // NON-NLS

  /** String: {@code STATIC}, {@code DYNAMIC} (temporal positions) or {@code CINE} (multi-frame). */
  public static final String TEMPORAL = "series.temporal"; // NON-NLS

  /** Integer: the number of images or frames of the series. */
  public static final String IMAGES = "series.images"; // NON-NLS

  /** Boolean: a PET series with the attributes SUV needs (units, decay, dose, weight). */
  public static final String SUV = "series.suv"; // NON-NLS

  /**
   * Boolean: the pixel spacing of the images is reliable (Pixel Spacing of a calibrated plane, not
   * an estimate at the detector nor the regions of an ultrasound), so no calibration is needed.
   */
  public static final String CALIBRATED = "series.calibrated"; // NON-NLS

  /**
   * Boolean: another series of the study can be fused with this one, by a shared frame of reference
   * or as a functional (PET, NM) and an anatomical (CT, MR) pair.
   */
  public static final String FUSION = "study.fusion"; // NON-NLS

  /**
   * Prefix of the per-modality counts of the objects referencing the series: {@code
   * series.objects.KO}, {@code series.objects.PR}, {@code series.objects.SEG}, {@code
   * series.objects.RTSTRUCT}... Integer values, absent when none.
   */
  public static final String OBJECTS = "series.objects."; // NON-NLS

  /** String: the id of the hanging protocol that hung the viewer, absent when none did. */
  public static final String HANGING_PROTOCOL = "hp.protocol"; // NON-NLS

  private ContextFacts() {}

  /** The fact name of the count of the objects of a modality referencing the series. */
  public static String objects(String modality) {
    return OBJECTS + Objects.requireNonNull(modality).toUpperCase(Locale.ROOT);
  }

  /** A boolean fact, false when absent or not a boolean. */
  public static boolean flag(ViewContext context, String name) {
    return context != null && context.fact(name, Boolean.class).orElse(false);
  }

  /** An integer fact, 0 when absent or not a number. */
  public static int count(ViewContext context, String name) {
    return context == null ? 0 : context.fact(name, Number.class).map(Number::intValue).orElse(0);
  }

  /** A text fact, null when absent or not a string. */
  public static String text(ViewContext context, String name) {
    return context == null ? null : context.fact(name, String.class).orElse(null);
  }
}
