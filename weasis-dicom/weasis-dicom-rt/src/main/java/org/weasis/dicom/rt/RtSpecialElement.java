/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.rt;

import java.util.concurrent.CopyOnWriteArraySet;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.weasis.core.api.util.ResourceUtil.OtherIcon;
import org.weasis.core.api.util.ResourceUtil.ResourceIconPath;
import org.weasis.dicom.codec.DicomMediaIO;
import org.weasis.dicom.codec.HiddenSeriesManager;
import org.weasis.dicom.codec.HiddenSpecialElement;
import org.weasis.dicom.codec.SpecialElementReferences;

/**
 * @author Tomas Skripcak
 * @author Nicolas Roduit
 */
public class RtSpecialElement extends HiddenSpecialElement implements SpecialElementReferences {
  private String modality;

  public RtSpecialElement(DicomMediaIO mediaIO) {
    super(mediaIO);
  }

  @Override
  public void initReferences(String originSeriesUID) {
    HiddenSeriesManager.getInstance()
        .series2Elements
        .computeIfAbsent(originSeriesUID, _ -> new CopyOnWriteArraySet<>())
        .add(this);

    Attributes dcmItems = getMediaReader().getDicomObject();
    if (dcmItems != null) {
      if (this instanceof Plan) {
        Attributes seriesRef = dcmItems.getNestedDataset(Tag.ReferencedStructureSetSequence);
        HiddenSeriesManager.addReferencedSOPInstanceUID(seriesRef, originSeriesUID);
      } else if (this instanceof Dose) {
        Attributes seriesRef = dcmItems.getNestedDataset(Tag.ReferencedRTPlanSequence);
        HiddenSeriesManager.addReferencedSOPInstanceUID(seriesRef, originSeriesUID);
      }
    }
  }

  @Override
  protected void initLabel() {
    Attributes dicom = ((DicomMediaIO) mediaIO).getDicomObject();
    this.modality = dicom.getString(Tag.Modality);

    String rtLabel = null;
    if (isRtStruct()) {
      rtLabel = dicom.getString(Tag.StructureSetLabel);
    } else if (isRtPlan()) {
      rtLabel = dicom.getString(Tag.RTPlanLabel);
    }

    if (rtLabel == null) {
      super.initLabel();
    } else {
      this.label = rtLabel;
    }
  }

  public boolean isRtStruct() {
    return "RTSTRUCT".equals(modality);
  }

  public boolean isRtPlan() {
    return "RTPLAN".equals(modality);
  }

  @Override
  public ResourceIconPath getIconPath() {
    return OtherIcon.RADIOACTIVE;
  }
}
