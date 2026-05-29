/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.rt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Sequence;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.weasis.dicom.codec.DicomMediaIO;

@DisplayNameGeneration(ReplaceUnderscores.class)
class RxDoseExtractionTest {

  private static Plan newPlan() {
    DicomMediaIO mediaIO = mock(DicomMediaIO.class);
    when(mediaIO.getDicomObject()).thenReturn(new Attributes());
    Plan plan = new Plan(mediaIO);
    // Mirror RtSet.initPlan: rxDose is initialised to 0.0 before extraction runs.
    plan.setRxDose(0.0);
    return plan;
  }

  private static Attributes doseRefItem(String type, Double targetDoseGy, String description) {
    Attributes a = new Attributes();
    if (type != null) {
      a.setString(Tag.DoseReferenceStructureType, VR.CS, type);
    }
    if (targetDoseGy != null) {
      a.setDouble(Tag.TargetPrescriptionDose, VR.DS, targetDoseGy);
    }
    if (description != null) {
      a.setString(Tag.DoseReferenceDescription, VR.LO, description);
    }
    return a;
  }

  private static Sequence doseRefSequence(Attributes... items) {
    Attributes parent = new Attributes();
    Sequence seq = parent.newSequence(Tag.DoseReferenceSequence, items.length);
    for (Attributes item : items) {
      seq.add(item);
    }
    return seq;
  }

  private static Attributes fractionGroupDcm(Integer fractions, Double... beamDoses) {
    Attributes dcm = new Attributes();
    Sequence fxSeq = dcm.newSequence(Tag.FractionGroupSequence, 1);
    Attributes fxGroup = new Attributes();
    if (fractions != null) {
      fxGroup.setInt(Tag.NumberOfFractionsPlanned, VR.IS, fractions);
    }
    Sequence beamSeq = fxGroup.newSequence(Tag.ReferencedBeamSequence, beamDoses.length);
    for (Double d : beamDoses) {
      Attributes beam = new Attributes();
      if (d != null) {
        beam.setDouble(Tag.BeamDose, VR.DS, d);
      } else {
        // Present-but-empty: contains() is true, containsValue() is false.
        beam.setNull(Tag.BeamDose, VR.DS);
      }
      beamSeq.add(beam);
    }
    fxSeq.add(fxGroup);
    return dcm;
  }

  @Nested
  @DisplayNameGeneration(ReplaceUnderscores.class)
  class FromDoseReferenceSequence {

    @Test
    void volume_type_converts_gy_target_to_cgy() {
      Plan plan = newPlan();
      RtSet.extractRxDoseFromDoseRef(plan, doseRefSequence(doseRefItem("VOLUME", 60.0, null)));
      // 60 Gy * 100 = 6000 cGy
      assertEquals(6000.0, plan.getRxDose(), 1e-9);
    }

    @Test
    void site_type_is_accepted() {
      Plan plan = newPlan();
      RtSet.extractRxDoseFromDoseRef(plan, doseRefSequence(doseRefItem("SITE", 50.0, null)));
      assertEquals(5000.0, plan.getRxDose(), 1e-9);
    }

    @Test
    void coordinates_type_is_accepted() {
      Plan plan = newPlan();
      RtSet.extractRxDoseFromDoseRef(plan, doseRefSequence(doseRefItem("COORDINATES", 70.0, null)));
      assertEquals(7000.0, plan.getRxDose(), 1e-9);
    }

    @Test
    void point_type_is_logged_and_ignored() {
      Plan plan = newPlan();
      RtSet.extractRxDoseFromDoseRef(plan, doseRefSequence(doseRefItem("POINT", 60.0, null)));
      assertEquals(0.0, plan.getRxDose(), 1e-9);
    }

    @Test
    void unknown_structure_type_is_ignored() {
      Plan plan = newPlan();
      RtSet.extractRxDoseFromDoseRef(
          plan, doseRefSequence(doseRefItem("ORGAN_AT_RISK", 60.0, null)));
      assertEquals(0.0, plan.getRxDose(), 1e-9);
    }

    @Test
    void null_structure_type_is_ignored() {
      Plan plan = newPlan();
      RtSet.extractRxDoseFromDoseRef(plan, doseRefSequence(doseRefItem(null, 60.0, null)));
      assertEquals(0.0, plan.getRxDose(), 1e-9);
    }

    @Test
    void highest_target_prescription_dose_wins_across_multiple_refs() {
      Plan plan = newPlan();
      RtSet.extractRxDoseFromDoseRef(
          plan,
          doseRefSequence(
              doseRefItem("VOLUME", 40.0, null),
              doseRefItem("VOLUME", 60.0, null),
              doseRefItem("VOLUME", 50.0, null)));
      // 60 Gy is the highest in the set
      assertEquals(6000.0, plan.getRxDose(), 1e-9);
    }

    @Test
    void lower_dose_does_not_overwrite_a_previously_set_higher_dose() {
      Plan plan = newPlan();
      plan.setRxDose(8000.0); // a previous, higher prescription
      RtSet.extractRxDoseFromDoseRef(plan, doseRefSequence(doseRefItem("VOLUME", 60.0, null)));
      assertEquals(8000.0, plan.getRxDose(), 1e-9);
    }

    @Test
    void equal_dose_does_not_overwrite_existing_dose() {
      Plan plan = newPlan();
      plan.setRxDose(6000.0);
      RtSet.extractRxDoseFromDoseRef(plan, doseRefSequence(doseRefItem("VOLUME", 60.0, null)));
      // strict >: an equal-value dose does NOT overwrite (no description leak either)
      assertEquals(6000.0, plan.getRxDose(), 1e-9);
    }

    @Test
    void missing_target_prescription_dose_skips_the_item_without_throwing() {
      Plan plan = newPlan();
      RtSet.extractRxDoseFromDoseRef(plan, doseRefSequence(doseRefItem("VOLUME", null, null)));
      assertEquals(0.0, plan.getRxDose(), 1e-9);
    }

    @Test
    void description_is_appended_to_plan_name_when_dose_wins() {
      Plan plan = newPlan();
      plan.setName("PTV");
      RtSet.extractRxDoseFromDoseRef(plan, doseRefSequence(doseRefItem("VOLUME", 60.0, "Boost")));
      assertEquals(6000.0, plan.getRxDose(), 1e-9);
      assertEquals("PTV - Boost", plan.getName());
    }

    @Test
    void description_seeds_the_plan_name_when_no_name_is_set() {
      Plan plan = newPlan();
      RtSet.extractRxDoseFromDoseRef(plan, doseRefSequence(doseRefItem("VOLUME", 60.0, "Boost")));
      assertEquals("Boost", plan.getName());
    }

    @Test
    void description_is_not_appended_when_dose_does_not_win() {
      Plan plan = newPlan();
      plan.setRxDose(8000.0);
      plan.setName("PTV");
      RtSet.extractRxDoseFromDoseRef(plan, doseRefSequence(doseRefItem("VOLUME", 60.0, "Boost")));
      // dose was rejected -> description must not contaminate the plan name
      assertEquals("PTV", plan.getName());
    }
  }

  @Nested
  @DisplayNameGeneration(ReplaceUnderscores.class)
  class FromFractionGroupSequence {

    @Test
    void beam_dose_times_fractions_converts_gy_to_cgy() {
      Plan plan = newPlan();
      // 2 Gy/beam * 30 fractions * 100 = 6000 cGy
      RtSet.extractRxDoseFromFractionGroup(plan, fractionGroupDcm(30, 2.0));
      assertEquals(6000.0, plan.getRxDose(), 1e-9);
    }

    @Test
    void multiple_beams_sum_within_the_fraction_group() {
      Plan plan = newPlan();
      // 1.5 Gy * 20 fx * 100 + 0.5 Gy * 20 fx * 100 = 3000 + 1000 = 4000 cGy
      RtSet.extractRxDoseFromFractionGroup(plan, fractionGroupDcm(20, 1.5, 0.5));
      assertEquals(4000.0, plan.getRxDose(), 1e-9);
    }

    @Test
    void contribution_is_added_to_a_previously_set_rx_dose() {
      Plan plan = newPlan();
      plan.setRxDose(1000.0); // e.g. a prior partial extraction
      RtSet.extractRxDoseFromFractionGroup(plan, fractionGroupDcm(30, 2.0));
      // 1000 + 2*30*100 = 7000
      assertEquals(7000.0, plan.getRxDose(), 1e-9);
    }

    @Test
    void missing_number_of_fractions_returns_without_changing_rx_dose() {
      Plan plan = newPlan();
      RtSet.extractRxDoseFromFractionGroup(plan, fractionGroupDcm(null, 2.0));
      assertEquals(0.0, plan.getRxDose(), 1e-9);
    }

    @Test
    void missing_fraction_group_sequence_returns_without_changing_rx_dose() {
      Plan plan = newPlan();
      RtSet.extractRxDoseFromFractionGroup(plan, new Attributes());
      assertEquals(0.0, plan.getRxDose(), 1e-9);
    }

    @Test
    void missing_referenced_beam_sequence_returns_without_changing_rx_dose() {
      Plan plan = newPlan();
      Attributes dcm = new Attributes();
      Sequence fxSeq = dcm.newSequence(Tag.FractionGroupSequence, 1);
      Attributes fxGroup = new Attributes();
      fxGroup.setInt(Tag.NumberOfFractionsPlanned, VR.IS, 30);
      fxSeq.add(fxGroup);
      RtSet.extractRxDoseFromFractionGroup(plan, dcm);
      assertEquals(0.0, plan.getRxDose(), 1e-9);
    }

    @Test
    void beam_without_beam_dose_tag_is_skipped() {
      Plan plan = newPlan();
      Attributes dcm = new Attributes();
      Sequence fxSeq = dcm.newSequence(Tag.FractionGroupSequence, 1);
      Attributes fxGroup = new Attributes();
      fxGroup.setInt(Tag.NumberOfFractionsPlanned, VR.IS, 30);
      Sequence beamSeq = fxGroup.newSequence(Tag.ReferencedBeamSequence, 2);
      // First beam has no BeamDose tag at all
      beamSeq.add(new Attributes());
      // Second beam carries the prescription
      Attributes withDose = new Attributes();
      withDose.setDouble(Tag.BeamDose, VR.DS, 2.0);
      beamSeq.add(withDose);
      fxSeq.add(fxGroup);

      RtSet.extractRxDoseFromFractionGroup(plan, dcm);
      // Only the second beam contributes: 2.0 * 30 * 100 = 6000
      assertEquals(6000.0, plan.getRxDose(), 1e-9);
    }

    @Test
    void beam_with_empty_beam_dose_value_is_skipped() {
      Plan plan = newPlan();
      // null in the varargs translates to a setNull(BeamDose) - contains=true, containsValue=false
      RtSet.extractRxDoseFromFractionGroup(plan, fractionGroupDcm(30, (Double) null));
      assertEquals(0.0, plan.getRxDose(), 1e-9);
    }

    @Test
    void rx_dose_null_at_entry_silently_skips_every_beam() {
      Plan plan = newPlan();
      plan.setRxDose(null); // worst-case: caller forgot to initialise
      RtSet.extractRxDoseFromFractionGroup(plan, fractionGroupDcm(30, 2.0));
      // Source guards on `rxDose != null`; verify null-in / null-out (no NPE).
      assertEquals(null, plan.getRxDose());
    }
  }
}
