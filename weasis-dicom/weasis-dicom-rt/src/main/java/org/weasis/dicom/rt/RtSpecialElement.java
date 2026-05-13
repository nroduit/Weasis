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
 * Base hidden DICOM element type for the DICOM-RT family (RTSTRUCT, RTPLAN, RTDOSE…). Concrete
 * subclasses ({@link Plan}, {@link Dose}, {@link StructureSet}) refine the per-modality behavior;
 * raw instances of this class are used as a generic fallback.
 *
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
    if (dcmItems == null) {
      return;
    }
    int refTag =
        switch (this) {
          case Plan _ -> Tag.ReferencedStructureSetSequence;
          case Dose _ -> Tag.ReferencedRTPlanSequence;
          default -> 0;
        };
    if (refTag != 0) {
      HiddenSeriesManager.addReferencedSOPInstanceUID(
          dcmItems.getNestedDataset(refTag), originSeriesUID);
    }
  }

  @Override
  protected void initLabel() {
    Attributes dicom = ((DicomMediaIO) mediaIO).getDicomObject();
    if (dicom == null) {
      super.initLabel();
      return;
    }
    this.modality = dicom.getString(Tag.Modality);

    String rtLabel =
        switch (modality) {
          case "RTSTRUCT" -> dicom.getString(Tag.StructureSetLabel);
          case "RTPLAN" -> dicom.getString(Tag.RTPlanLabel);
          case null, default -> null;
        };

    if (rtLabel == null) {
      super.initLabel();
    } else {
      this.label = rtLabel;
    }
  }

  /** {@code true} if the underlying object is an RT Structure Set. */
  public boolean isRtStruct() {
    return "RTSTRUCT".equals(modality);
  }

  /** {@code true} if the underlying object is an RT Plan. */
  public boolean isRtPlan() {
    return "RTPLAN".equals(modality);
  }

  @Override
  public ResourceIconPath getIconPath() {
    return OtherIcon.RADIOACTIVE;
  }
}
