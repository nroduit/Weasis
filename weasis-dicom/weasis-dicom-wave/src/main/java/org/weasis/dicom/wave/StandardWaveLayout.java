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