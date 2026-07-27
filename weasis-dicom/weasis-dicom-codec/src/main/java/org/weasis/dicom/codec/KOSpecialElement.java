/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.codec;

import java.util.Map;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.dicom.macro.SOPInstanceReferenceAndMAC;

public class KOSpecialElement extends AbstractKOSpecialElement {

  public KOSpecialElement(DicomMediaIO mediaIO) {
    super(mediaIO);
  }

  public void toggleKeyObjectReference(DicomImageElement dicomImage) {
    Reference ref = new Reference(dicomImage);

    // Get the SOPInstanceReferenceMap for this seriesUID (lazily initialized)
    Map<String, SOPInstanceReferenceAndMAC> sopInstanceReferenceBySOPInstanceUID =
        getReferencedSOPInstanceUIDObject(ref.getSeriesInstanceUID());

    boolean isSelected =
        sopInstanceReferenceBySOPInstanceUID != null
            && sopInstanceReferenceBySOPInstanceUID.containsKey(ref.getSopInstanceUID());

    setKeyObjectReference(!isSelected, ref);
  }

  public boolean setKeyObjectReference(boolean selectedState, DicomImageElement dicomImage) {
    return setKeyObjectReference(selectedState, new Reference(dicomImage));
  }

  private boolean setKeyObjectReference(boolean selectedState, Reference ref) {
    if (selectedState) {
      return addKeyObject(ref);
    } else {
      return removeKeyObject(ref);
    }
  }

  public boolean setKeyObjectReference(
      boolean selectedState, MediaSeries<DicomImageElement> series) {
    boolean hasDataModelChanged = false;
    for (DicomImageElement dicomImage : series.getSortedMedias(null)) {
      hasDataModelChanged |= setKeyObjectReference(selectedState, new Reference(dicomImage));
    }
    return hasDataModelChanged;
  }
}
