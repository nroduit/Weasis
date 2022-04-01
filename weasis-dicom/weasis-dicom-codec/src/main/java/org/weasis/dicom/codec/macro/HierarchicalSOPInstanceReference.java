/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.codec.macro;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Sequence;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;

public class HierarchicalSOPInstanceReference extends Module {

  public HierarchicalSOPInstanceReference(Attributes dcmItems) {
    super(dcmItems);
  }

  public HierarchicalSOPInstanceReference() {
    super(new Attributes());
  }

  public static Collection<HierarchicalSOPInstanceReference>
      toHierarchicalSOPInstanceReferenceMacros(Sequence seq) {
    if (seq == null || seq.isEmpty()) {
      return Collections.emptyList();
    }

    ArrayList<HierarchicalSOPInstanceReference> list = new ArrayList<>(seq.size());

    for (Attributes attr : seq) {
      list.add(new HierarchicalSOPInstanceReference(attr));
    }

    return list;
  }

  public String getStudyInstanceUID() {
    return dcmItems.getString(Tag.StudyInstanceUID);
  }

  public void setStudyInstanceUID(String uid) {
    dcmItems.setString(Tag.StudyInstanceUID, VR.UI, uid);
  }

  public Collection<SeriesAndInstanceReference> getReferencedSeries() {
    return SeriesAndInstanceReference.toSeriesAndInstanceReferenceMacros(
        dcmItems.getSequence(Tag.ReferencedSeriesSequence));
  }

  public void setReferencedSeries(Collection<SeriesAndInstanceReference> referencedSeries) {
    updateSequence(Tag.ReferencedSeriesSequence, referencedSeries);
  }
}
