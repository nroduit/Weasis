/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.viewer3d.vr;

import org.weasis.dicom.viewer3d.Messages;

/**
 * Cost level of the cinematic lighting: how many steps its shadow and occlusion probes march, and
 * how many bounces the path tracer follows. The probes run once per lit sample, so their count sets
 * the price of the mode on top of the ray cast.
 */
public enum CinematicQuality {
  LOW(Messages.getString("quality.low"), 12, 2, 2),
  MEDIUM(Messages.getString("quality.medium"), 24, 3, 4),
  HIGH(Messages.getString("quality.high"), 48, 4, 8);

  public static final CinematicQuality DEFAULT = MEDIUM;

  private final String title;
  private final int shadowSteps;
  private final int aoSteps;
  private final int bounces;

  CinematicQuality(String title, int shadowSteps, int aoSteps, int bounces) {
    this.title = title;
    this.shadowSteps = shadowSteps;
    this.aoSteps = aoSteps;
    this.bounces = bounces;
  }

  public int getShadowSteps() {
    return shadowSteps;
  }

  /** Steps of each of the eight occlusion probes. */
  public int getAoSteps() {
    return aoSteps;
  }

  /** Scattering events a path-traced ray may take after the first one. */
  public int getBounces() {
    return bounces;
  }

  public static CinematicQuality fromOrdinal(int ordinal) {
    CinematicQuality[] values = values();
    return ordinal >= 0 && ordinal < values.length ? values[ordinal] : DEFAULT;
  }

  @Override
  public String toString() {
    return title;
  }
}
