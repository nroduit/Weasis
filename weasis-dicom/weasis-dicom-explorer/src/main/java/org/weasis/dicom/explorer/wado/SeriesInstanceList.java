package org.weasis.dicom.explorer.wado;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.weasis.dicom.mf.SopInstance;

public class SeriesInstanceList {
    private final Map<String, SopInstance> dicomInstanceMap = new HashMap<>();
    private final Map<String, SopInstance> dicomUniqueSopInstanceMap = new HashMap<>();
    private boolean containsMultiframes = false;

    public void addSopInstance(SopInstance s) {
        if (s != null) {
            SopInstance.addSopInstance(dicomInstanceMap, s);
            SopInstance sop = dicomUniqueSopInstanceMap.get(s.getSopInstanceUID());
            if (sop == null) {
                dicomUniqueSopInstanceMap.put(s.getSopInstanceUID(), s);
            } else {
                containsMultiframes = true;
            }
        }
    }

    public SopInstance getSopInstance(String sopUID, Integer instanceNumber) {
        return SopInstance.getSopInstance(dicomInstanceMap, sopUID, instanceNumber);
    }

    public SopInstance getSopInstance(String sopUID) {
        return dicomUniqueSopInstanceMap.get(sopUID);
    }

    public boolean isContainsMultiframes() {
        return containsMultiframes;
    }

    public boolean isEmpty() {
        return dicomInstanceMap.isEmpty();
    }

    public int size() {
        return dicomInstanceMap.size();
    }

    public List<SopInstance> getSortedList() {
        ArrayList<SopInstance> sopList = new ArrayList<>(dicomInstanceMap.values());
        Collections.sort(sopList);
        return sopList;
    }
}
