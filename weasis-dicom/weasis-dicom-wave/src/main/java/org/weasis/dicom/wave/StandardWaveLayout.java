/*******************************************************************************
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.weasis.dicom.wave;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

class StandardWaveLayout implements WaveLayout {

    @Override
    public List<LeadPanel> getSortedComponents(Format format, Map<Lead, LeadPanel> components) {
        Map<Lead, LeadPanel> copy = new LinkedHashMap<>(components);
        List<LeadPanel> list = new ArrayList<>(copy.size());
        for (Lead lead : format.getLeads()) {
            LeadPanel val = copy.remove(lead);
            if (val != null) {
                list.add(val);
            }
        }
        
        copy.remove(Lead.RYTHM);
        if(!copy.isEmpty()) {
            list.addAll(copy.values());
        }

        return list;
    }
}