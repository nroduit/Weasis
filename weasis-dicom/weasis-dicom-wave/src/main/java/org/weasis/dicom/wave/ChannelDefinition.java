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

import java.util.Objects;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.weasis.dicom.codec.macro.Code;
import org.weasis.dicom.codec.utils.DicomMediaUtils;

public class ChannelDefinition {

  private final Double baseline;
  private final Unit amplitudeUnit;
  private final int position;
  private final Lead lead;
  private Double minValue;
  private Double maxValue;

  /**
   * @see <a
   *     href="http://dicom.nema.org/medical/dicom/current/output/chtml/part03/sect_C.10.9.html">Waveform
   *     Module</a>
   */
  public ChannelDefinition(Attributes dcm, int position) {
    this.position = position;
    Attributes channelSourceSequence =
        Objects.requireNonNull(
            dcm.getNestedDataset(Tag.ChannelSourceSequence), "no ChannelSourceSequence");
    this.lead = Lead.buildLead(new Code(channelSourceSequence));
    Double chSensisvity =
        DicomMediaUtils.getDoubleFromDicomElement(dcm, Tag.ChannelSensitivity, null);
    if (chSensisvity == null) {
      this.baseline = 0.0;
      this.amplitudeUnit =
          new Unit(
              org.weasis.core.api.image.util.Unit.PIXEL.getFullName(),
              org.weasis.core.api.image.util.Unit.PIXEL.getAbbreviation(),
              1.0);
    } else {
      this.baseline = DicomMediaUtils.getDoubleFromDicomElement(dcm, Tag.ChannelBaseline, 0.0);
      Double sCorrectionFactor =
          DicomMediaUtils.getDoubleFromDicomElement(
              dcm, Tag.ChannelSensitivityCorrectionFactor, 1.0);
      Attributes chs =
          Objects.requireNonNull(
              dcm.getNestedDataset(Tag.ChannelSensitivityUnitsSequence),
              "no ChannelSensitivityUnitsSequence found");
      String unit =
          chs.getString(Tag.CodeValue, org.weasis.core.api.image.util.Unit.PIXEL.getAbbreviation());
      String unitDesc =
          chs.getString(Tag.CodeMeaning, org.weasis.core.api.image.util.Unit.PIXEL.getFullName());
      double factorUnit = "mV".equals(unit) ? 1000 : 1;
      this.amplitudeUnit = new Unit(unitDesc, unit, chSensisvity * sCorrectionFactor * factorUnit);
    }

    this.maxValue = 0.0;
    this.minValue = 0.0;
  }

  public ChannelDefinition(ChannelDefinition channelDefinition, String title) {
    Objects.requireNonNull(channelDefinition);
    this.lead = Lead.buildLead(title);
    this.position = channelDefinition.position;
    this.baseline = channelDefinition.baseline;
    this.amplitudeUnit = channelDefinition.amplitudeUnit;
    this.maxValue = channelDefinition.maxValue;
    this.minValue = channelDefinition.minValue;
  }

  public String getTitle() {
    return lead.toString();
  }

  public double getBaseline() {
    return baseline;
  }

  public Double getMinValue() {
    return minValue;
  }

  public void setMinValue(Double minValue) {
    this.minValue = minValue;
  }

  public Double getMaxValue() {
    return maxValue;
  }

  public void setMaxValue(Double maxValue) {
    this.maxValue = maxValue;
  }

  public Unit getAmplitudeUnit() {
    return amplitudeUnit;
  }

  public double getAmplitudeUnitScalingFactor() {
    return amplitudeUnit.scalingFactor();
  }

  public int getPosition() {
    return position;
  }

  public Lead getLead() {
    return lead;
  }
}
