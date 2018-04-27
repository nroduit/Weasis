/*******************************************************************************
 * Copyright (c) 2009-2018 Weasis Team and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 *     Nicolas Roduit - initial API and implementation
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