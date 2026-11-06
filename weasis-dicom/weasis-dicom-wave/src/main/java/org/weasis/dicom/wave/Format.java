/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.wave;

import java.util.Arrays;
import org.weasis.core.util.StringUtil;

/**
 * The lead layouts of a 12-lead ECG, each in the standard sequence (I, II, III, aVR, aVL, aVF) and
 * in the Cabrera sequence (aVL, I, -aVR, II, aVF, III: the limb leads in anatomical order, aVR
 * inverted). The lead arrays are in reading order, row by row; rhythm strips come last and take a
 * row each.
 */
public enum Format {
  DEFAULT(
      Messages.getString("Format.1_10_sec"),
      1,
      0,
      Lead.DEFAULT_12LEAD,
      new Lead[] {
        Lead.AVL, Lead.I, Lead.AVR, Lead.II, Lead.AVF, Lead.III, Lead.V1, Lead.V2, Lead.V3, Lead.V4,
        Lead.V5, Lead.V6
      }),

  TWO(
      Messages.getString("Format.2_5_sec"),
      2,
      6,
      new Lead[] {
        Lead.I, Lead.V1, Lead.II, Lead.V2, Lead.III, Lead.V3, Lead.AVR, Lead.V4, Lead.AVL, Lead.V5,
        Lead.AVF, Lead.V6
      },
      new Lead[] {
        Lead.AVL, Lead.V1, Lead.I, Lead.V2, Lead.AVR, Lead.V3, Lead.II, Lead.V4, Lead.AVF, Lead.V5,
        Lead.III, Lead.V6
      }),

  TWO_RHYTHM(
      Messages.getString("Format.2_5_sec_rhythm"),
      2,
      7,
      with(TWO.leads, Lead.RHYTHM),
      with(TWO.cabreraLeads, Lead.RHYTHM)),

  FOUR(
      Messages.getString("Format.4_2_sec"),
      4,
      3,
      new Lead[] {
        Lead.I, Lead.AVR, Lead.V1, Lead.V4, Lead.II, Lead.AVL, Lead.V2, Lead.V5, Lead.III, Lead.AVF,
        Lead.V3, Lead.V6
      },
      new Lead[] {
        Lead.AVL, Lead.II, Lead.V1, Lead.V4, Lead.I, Lead.AVF, Lead.V2, Lead.V5, Lead.AVR, Lead.III,
        Lead.V3, Lead.V6
      }),

  FOUR_RHYTHM(
      Messages.getString("Format.rhythm"),
      4,
      4,
      with(FOUR.leads, Lead.RHYTHM),
      with(FOUR.cabreraLeads, Lead.RHYTHM)),

  FOUR_RHYTHM3(
      Messages.getString("Format.rhythm3"),
      4,
      6,
      with(FOUR.leads, Lead.RHYTHM, Lead.RHYTHM_V1, Lead.RHYTHM_V5),
      with(FOUR.cabreraLeads, Lead.RHYTHM, Lead.RHYTHM_V1, Lead.RHYTHM_V5)),

  /** Every lead drawn in one panel, each in its colour: median beats compared at a glance. */
  SUPERIMPOSED(
      Messages.getString("Format.superimposed"), 1, 1, DEFAULT.leads, DEFAULT.cabreraLeads);

  private final String value;
  private final int xlayoutSize;
  private final int ylayoutSize;
  private final Lead[] leads;
  private final Lead[] cabreraLeads;

  Format(String value, int xlayoutSize, int ylayoutSize, Lead[] leads, Lead[] cabreraLeads) {
    this.value = value;
    this.xlayoutSize = xlayoutSize;
    this.ylayoutSize = ylayoutSize;
    this.leads = leads;
    this.cabreraLeads = cabreraLeads;
  }

  private static Lead[] with(Lead[] leads, Lead... more) {
    Lead[] all = Arrays.copyOf(leads, leads.length + more.length);
    System.arraycopy(more, 0, all, leads.length, more.length);
    return all;
  }

  public int getXlayoutSize() {
    return xlayoutSize;
  }

  public int getYlayoutSize() {
    return ylayoutSize;
  }

  public String getValue() {
    return value;
  }

  public Lead[] getLeads() {
    return leads;
  }

  /** The leads in reading order, in the Cabrera sequence when asked. */
  public Lead[] getLeads(boolean cabrera) {
    return cabrera ? cabreraLeads : leads;
  }

  /** The number of rhythm strips of the layout. */
  public int getRhythmCount() {
    int count = 0;
    for (Lead lead : leads) {
      if (lead.isRhythm()) {
        count++;
      }
    }
    return count;
  }

  /** The seconds shown by one column: 10 s split between the columns, the whole recording else. */
  public double getColumnSeconds() {
    return this == DEFAULT || this == SUPERIMPOSED ? -1 : 10.0 / xlayoutSize;
  }

  public boolean isSuperimposed() {
    return this == SUPERIMPOSED;
  }

  @Override
  public String toString() {
    return value;
  }

  public static Format getValue(String value) {
    if (StringUtil.hasText(value)) {
      try {
        return Format.valueOf(value);
      } catch (Exception e) {
        // Ignore
      }
    }
    return DEFAULT;
  }
}
