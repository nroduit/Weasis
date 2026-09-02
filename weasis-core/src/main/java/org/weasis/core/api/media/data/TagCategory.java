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
 * What kind of information a tag carries. Classification lives on the tag; the action to take lives
 * in a {@link MaskingProfile}, so a tag is classified once and every profile handles it.
 */
public enum TagCategory {
  /** Names and numbers that identify a person or a visit on their own. */
  DIRECT_ID,
  /** Age, sex, weight, size: clinical, and needed to compute SUV or dose. */
  PATIENT_CHARACTERISTIC,
  /** Study, series and acquisition dates, whose intervals carry the clinical timeline. */
  DATE,
  /** Birth date and time, identifying in a way a study date is not. */
  BIRTH_DATE,
  /** The site: institution and department. */
  INSTITUTION,
  /** The equipment: station name, serial number. */
  DEVICE,
  /** Study, series and protocol descriptions: what the image is, rarely who it is. */
  DESCRIPTOR,
  /** Operator-typed text, where anything at all may end up. */
  FREE_TEXT,
  /** Everything else; never masked. */
  OTHER
}
