package org.weasis.dicom.wave;

import java.util.List;
import java.util.Map;

public interface WaveLayout {

    List<LeadPanel> getSortedComponents(Format format, Map<Lead, LeadPanel> components);
}