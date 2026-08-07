/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.codec.utils;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.weasis.core.api.media.data.SimpleTaggable;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.api.media.data.Taggable;
import org.weasis.dicom.codec.TagD;

class SuvFactorTest {

  /** Injected dose in Bq, and the F-18 half-life in seconds. */
  private static final double TOTAL_DOSE = 370_000_000.0;

  private static final double HALF_LIFE = 6586.0;
  private static final double WEIGHT_KG = 70.0;

  /** One hour of uptake: injection at 11:00, series at 12:00. */
  private static final double UPTAKE_SECONDS = 3600.0;

  /** A PET image carrying everything the SUVbw conversion needs. */
  private static Attributes petAttributes() {
    Attributes dcm = new Attributes();
    dcm.setString(Tag.CorrectedImage, VR.CS, "ATTN", "DECY");
    dcm.setString(Tag.Units, VR.CS, "BQML");
    dcm.setString(Tag.DecayCorrection, VR.CS, "START");
    dcm.setDouble(Tag.PatientWeight, VR.DS, WEIGHT_KG);
    dcm.setString(Tag.SeriesDate, VR.DA, "20260102");
    dcm.setString(Tag.SeriesTime, VR.TM, "120000");
    dcm.setString(Tag.AcquisitionDate, VR.DA, "20260102");
    dcm.setString(Tag.AcquisitionTime, VR.TM, "120005");

    Attributes radiopharmaceutical = new Attributes();
    radiopharmaceutical.setDouble(Tag.RadionuclideTotalDose, VR.DS, TOTAL_DOSE);
    radiopharmaceutical.setDouble(Tag.RadionuclideHalfLife, VR.DS, HALF_LIFE);
    radiopharmaceutical.setString(Tag.RadiopharmaceuticalStartTime, VR.TM, "110000");
    dcm.newSequence(Tag.RadiopharmaceuticalInformationSequence, 1).add(radiopharmaceutical);
    return dcm;
  }

  private static Object suvFactorOf(Attributes dcm) {
    return suvFactorOf(dcm, "PT");
  }

  private static Object suvFactorOf(Attributes dcm, String modality) {
    Taggable taggable = new SimpleTaggable();
    taggable.setTag(TagD.get(Tag.Modality), modality);
    DicomMediaUtils.computeSUVFactor(dcm, taggable, 0);
    return taggable.getTagValue(TagW.SuvFactor);
  }

  @Test
  @DisplayName("A complete BQML acquisition yields the dose decayed to the series time")
  void bqmlFactorIsDecayCorrected() {
    double decayed = TOTAL_DOSE * Math.pow(2, -UPTAKE_SECONDS / HALF_LIFE);
    assertEquals(WEIGHT_KG * 1000.0 / decayed, (Double) suvFactorOf(petAttributes()), 1e-12);
  }

  @Test
  @DisplayName("Values already in grams/milliliter are SUVbw")
  void gmlNeedsNoConversion() {
    Attributes dcm = petAttributes();
    dcm.setString(Tag.Units, VR.CS, "GML");
    assertEquals(1.0, (Double) suvFactorOf(dcm), 1e-12);
  }

  @Test
  @DisplayName("No factor is set when any element of the conversion is missing")
  void incompleteAcquisitionsGetNoFactor() {
    Attributes notCorrected = petAttributes();
    notCorrected.setString(Tag.CorrectedImage, VR.CS, "ATTN");

    Attributes counts = petAttributes();
    counts.setString(Tag.Units, VR.CS, "CPS");

    Attributes noWeight = petAttributes();
    noWeight.remove(Tag.PatientWeight);

    Attributes noDose = petAttributes();
    noDose.remove(Tag.RadiopharmaceuticalInformationSequence);

    Attributes noDecay = petAttributes();
    noDecay.setString(Tag.DecayCorrection, VR.CS, "ADMIN");

    Attributes noSeriesTime = petAttributes();
    noSeriesTime.remove(Tag.SeriesDate);
    noSeriesTime.remove(Tag.SeriesTime);

    assertAll(
        () -> assertNull(suvFactorOf(notCorrected)),
        () -> assertNull(suvFactorOf(counts)),
        () -> assertNull(suvFactorOf(noWeight)),
        () -> assertNull(suvFactorOf(noDose)),
        () -> assertNull(suvFactorOf(noDecay)),
        // Used to throw: the series time was dereferenced before being checked.
        () -> assertNull(suvFactorOf(noSeriesTime)));
  }

  @Test
  @DisplayName("Nothing is computed on a series that is not a PET")
  void otherModalitiesAreLeftAlone() {
    assertNull(suvFactorOf(petAttributes(), "CT"));
  }
}
