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

import static org.weasis.core.api.image.util.Unit.PIXEL;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Objects;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.img.util.DicomUtils;
import org.weasis.dicom.macro.Code;

/**
 * One channel of a multiplex group: its lead, its position in the multiplexed samples and the
 * conversion of stored values. Voltage channels are kept in microvolts and displayed in millivolts;
 * any other unit (mmHg, ...) is kept and displayed as such, with a grid cell value of its own.
 */
public class ChannelDefinition {

  private final Double baseline;
  private final Unit amplitudeUnit;
  private final boolean voltage;
  private final int position;
  private final Lead lead;
  private Double minValue;
  private Double maxValue;
  private final Integer storedMinimum;
  private final Integer storedMaximum;

  /** Offset in channel units removed from the displayed values (DC-coupled channels). */
  private double displayOffset;

  /** Display units per grid cell: 1 mV for a voltage channel, a round value otherwise. */
  private double cellValue = 1.0;

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
    Double chSensisvity = DicomUtils.getDoubleFromDicomElement(dcm, Tag.ChannelSensitivity, null);
    if (chSensisvity == null) {
      this.baseline = 0.0;
      this.amplitudeUnit = new Unit(PIXEL.getFullName(), PIXEL.getAbbreviation(), 1.0);
      this.voltage = true;
    } else {
      this.baseline = DicomUtils.getDoubleFromDicomElement(dcm, Tag.ChannelBaseline, 0.0);
      Double sCorrectionFactor =
          DicomUtils.getDoubleFromDicomElement(dcm, Tag.ChannelSensitivityCorrectionFactor, 1.0);
      Attributes chs = dcm.getNestedDataset(Tag.ChannelSensitivityUnitsSequence);
      String unit = chs == null ? "uV" : chs.getString(Tag.CodeValue, "uV"); // NON-NLS
      String unitDesc =
          chs == null ? "microvolt" : chs.getString(Tag.CodeMeaning, "microvolt"); // NON-NLS
      double factorUnit = voltageFactor(unit);
      this.voltage = factorUnit > 0;
      this.amplitudeUnit =
          new Unit(
              unitDesc,
              voltage ? "mV" : unit, // NON-NLS
              chSensisvity * sCorrectionFactor * (voltage ? factorUnit : 1));
    }

    this.maxValue = 0.0;
    this.minValue = 0.0;
    this.storedMinimum = storedValue(dcm, Tag.ChannelMinimumValue);
    this.storedMaximum = storedValue(dcm, Tag.ChannelMaximumValue);
  }

  /** Microvolts per unit for a voltage unit code (UCUM), 0 for any other unit. */
  private static double voltageFactor(String unit) {
    return switch (unit) {
      case "uV", "µV", "μV" -> 1; // NON-NLS
      case "mV" -> 1000; // NON-NLS
      case "V" -> 1_000_000; // NON-NLS
      default -> 0;
    };
  }

  /** Channel Minimum / Maximum Value (5400,0110 / 0112): a stored sample value, OB or OW. */
  private static Integer storedValue(Attributes dcm, int tag) {
    try {
      byte[] bytes = dcm.getBytes(tag);
      if (bytes == null || bytes.length == 0) {
        return null;
      }
      ByteBuffer buf =
          ByteBuffer.wrap(bytes)
              .order(dcm.bigEndian() ? ByteOrder.BIG_ENDIAN : ByteOrder.LITTLE_ENDIAN);
      return switch (bytes.length) {
        case 1 -> (int) buf.get();
        case 2 -> (int) buf.getShort();
        case 4 -> buf.getInt();
        default -> null;
      };
    } catch (Exception e) {
      return null;
    }
  }

  public ChannelDefinition(ChannelDefinition channelDefinition, String title) {
    Objects.requireNonNull(channelDefinition);
    this.lead = Lead.buildLead(title);
    this.position = channelDefinition.position;
    this.baseline = channelDefinition.baseline;
    this.amplitudeUnit = channelDefinition.amplitudeUnit;
    this.voltage = channelDefinition.voltage;
    this.maxValue = channelDefinition.maxValue;
    this.minValue = channelDefinition.minValue;
    this.storedMinimum = channelDefinition.storedMinimum;
    this.storedMaximum = channelDefinition.storedMaximum;
    this.displayOffset = channelDefinition.displayOffset;
    this.cellValue = channelDefinition.cellValue;
  }

  public String getTitle() {
    return lead.toString();
  }

  public double getBaseline() {
    return baseline;
  }

  /** The minimum displayed value (display unit, offset removed). */
  public Double getMinValue() {
    return minValue;
  }

  public void setMinValue(Double minValue) {
    this.minValue = minValue;
  }

  /** The maximum displayed value (display unit, offset removed). */
  public Double getMaxValue() {
    return maxValue;
  }

  public void setMaxValue(Double maxValue) {
    this.maxValue = maxValue;
  }

  /** The Channel Minimum Value of the object, or null when absent. */
  public Integer getStoredMinimum() {
    return storedMinimum;
  }

  /** The Channel Maximum Value of the object, or null when absent. */
  public Integer getStoredMaximum() {
    return storedMaximum;
  }

  public Unit getAmplitudeUnit() {
    return amplitudeUnit;
  }

  /** Physical units (microvolts for a voltage channel) per stored unit. */
  public double getAmplitudeUnitScalingFactor() {
    return amplitudeUnit.scalingFactor();
  }

  /** Whether the channel is a voltage, displayed in millivolts on the millimetre grid. */
  public boolean isVoltage() {
    return voltage;
  }

  /** The unit of the displayed values: mV for a voltage, the channel unit otherwise. */
  public String getDisplayUnit() {
    return amplitudeUnit.abbreviation();
  }

  /**
   * The constant offset removed from the displayed values, in physical units: 0 for an ordinary
   * ECG, the DC level of a DC-coupled channel whose offset dwarfs the signal.
   */
  public double getDisplayOffset() {
    return displayOffset;
  }

  public void setDisplayOffset(double displayOffset) {
    this.displayOffset = displayOffset;
  }

  /** Display units per grid cell: 1 mV for a voltage channel. */
  public double getCellValue() {
    return cellValue;
  }

  public void setCellValue(double cellValue) {
    this.cellValue = cellValue > 0 ? cellValue : 1.0;
  }

  /**
   * The physical value of a stored sample (microvolts for a voltage), the display offset removed.
   */
  public double toPhysical(int raw) {
    return raw * amplitudeUnit.scalingFactor() + baseline - displayOffset;
  }

  /** The displayed value of a physical value: millivolts for a voltage, unchanged otherwise. */
  public double toDisplay(double physical) {
    return voltage ? physical / 1000 : physical;
  }

  public int getPosition() {
    return position;
  }

  public Lead getLead() {
    return lead;
  }
}
