/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.wave;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The leads of a format in reading order: the leads the format names, then the channels the format
 * does not know (extra leads of a 15-lead ECG, intracardiac channels...), the rhythm strips the
 * format asks for last.
 */
class StandardWaveLayout implements WaveLayout {

  @Override
  public List<LeadPanel> getSortedComponents(
      Format format, boolean cabrera, Map<Lead, LeadPanel> components) {
    Map<Lead, LeadPanel> copy = new LinkedHashMap<>(components);
    List<LeadPanel> list = new ArrayList<>(copy.size());
    List<LeadPanel> rhythms = new ArrayList<>();
    for (Lead lead : format.getLeads(cabrera)) {
      LeadPanel val = copy.remove(lead);
      if (val != null) {
        if (lead.isRhythm()) {
          rhythms.add(val);
        } else {
          list.add(val);
        }
      }
    }

    copy.keySet().removeIf(Lead::isRhythm);
    if (!copy.isEmpty()) {
      list.addAll(copy.values());
    }
    list.addAll(rhythms);
    return list;
  }
}
