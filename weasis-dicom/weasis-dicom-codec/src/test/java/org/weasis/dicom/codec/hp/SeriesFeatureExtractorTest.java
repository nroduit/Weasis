/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.codec.hp;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.weasis.dicom.codec.hp.TestSeries.ctSlice;
import static org.weasis.dicom.codec.hp.TestSeries.image;
import static org.weasis.dicom.codec.hp.TestSeries.item;
import static org.weasis.dicom.codec.hp.TestSeries.series;

import java.util.List;
import java.util.Set;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.dcm4che3.data.VR;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.weasis.dicom.codec.DicomSeries;
import org.weasis.dicom.codec.hp.SeriesFeatures.Contrast;
import org.weasis.dicom.codec.hp.SeriesFeatures.CtKernel;
import org.weasis.dicom.codec.hp.SeriesFeatures.Interest;
import org.weasis.dicom.codec.hp.SeriesFeatures.MrWeighting;
import org.weasis.dicom.codec.hp.SeriesFeatures.Orientation;
import org.weasis.dicom.codec.hp.SeriesFeatures.Role;
import org.weasis.dicom.codec.hp.SeriesFeatures.Temporal;

@DisplayNameGeneration(ReplaceUnderscores.class)
class SeriesFeatureExtractorTest {

  private final SeriesFeatureExtractor extractor =
      new SeriesFeatureExtractor(new DerivationTables(), InterestProvider.NONE);

  @Test
  void a_ct_stack_gives_kernel_orientation_geometry_and_anatomy() {
    Attributes first = ctSlice(1, 0);
    first.setString(Tag.SeriesDescription, VR.LO, "Thorax  5mm soft"); // NON-NLS
    first.setString(Tag.ContrastBolusAgent, VR.LO, "OMNIPAQUE 350"); // NON-NLS
    first.setString(Tag.ProtocolName, VR.LO, "CT chest portal venous"); // NON-NLS
    DicomSeries series = series(first, ctSlice(2, 2.5), ctSlice(3, 5.0), ctSlice(4, 7.5));

    SeriesFeatures f = extractor.of(series);

    assertAll(
        () -> assertEquals("CT", f.modality()), // NON-NLS
        () -> assertEquals(UID.CTImageStorage, f.sopClass()),
        () -> assertEquals(Role.ORIGINAL, f.role()),
        () -> assertEquals(Orientation.AXIAL, f.orientation()),
        () -> assertEquals(new CtKernel("B30f", CtKernel.Family.SOFT), f.ctKernel()), // NON-NLS
        () -> assertEquals(2.5, f.sliceThickness(), 1e-9),
        () -> assertEquals(2.5, f.spacing(), 1e-9),
        () -> assertEquals("CHEST", f.anatomy().notation()), // NON-NLS
        () -> assertTrue(f.anatomy().groups().contains("CHEST")), // NON-NLS
        () -> assertEquals(new Contrast(Contrast.Agent.WITH, Contrast.Phase.PORTAL), f.contrast()),
        () -> assertEquals("1.2.3.4.5", f.frameOfReference()), // NON-NLS
        () -> assertEquals(Temporal.STATIC, f.temporal()),
        () ->
            assertEquals(
                List.of("THORAX", "5MM", "SOFT", "CT", "CHEST", "PORTAL", "VENOUS"), // NON-NLS
                f.descriptionTokens()),
        () -> assertEquals(Interest.NONE, f.interest()),
        () -> assertEquals(4, f.imageCount()),
        () -> assertTrue(f.isVolume()),
        () -> assertNull(f.mrWeighting()),
        () -> assertNull(f.petCorrection()));
  }

  @Test
  void the_kernel_family_follows_the_vendor_tables() {
    Attributes lung = ctSlice(1, 0);
    lung.setString(Tag.ConvolutionKernel, VR.SH, "B70f"); // NON-NLS
    Attributes bone = ctSlice(1, 0);
    bone.setString(Tag.Manufacturer, VR.LO, "GE MEDICAL SYSTEMS"); // NON-NLS
    bone.setString(Tag.ConvolutionKernel, VR.SH, "BONEPLUS"); // NON-NLS
    Attributes odd = ctSlice(1, 0);
    odd.setString(Tag.Manufacturer, VR.LO, "ACME"); // NON-NLS
    odd.setString(Tag.ConvolutionKernel, VR.SH, "XYZ"); // NON-NLS
    assertAll(
        () -> assertEquals(CtKernel.Family.LUNG, extractor.of(series(lung)).ctKernel().family()),
        () -> assertEquals(CtKernel.Family.BONE, extractor.of(series(bone)).ctKernel().family()),
        () -> assertEquals(CtKernel.Family.UNKNOWN, extractor.of(series(odd)).ctKernel().family()));
  }

  @Test
  void mixed_planes_and_a_missing_orientation_are_told_apart() {
    Attributes axial = ctSlice(1, 0);
    Attributes coronal = ctSlice(2, 1);
    coronal.setDouble(Tag.ImageOrientationPatient, VR.DS, 1, 0, 0, 0, 0, -1);
    Attributes none = image("CT", UID.CTImageStorage, 1); // NON-NLS
    assertAll(
        () -> assertEquals(Orientation.MIXED, extractor.of(series(axial, coronal)).orientation()),
        () -> assertEquals(Orientation.UNKNOWN, extractor.of(series(none)).orientation()),
        () -> assertFalse(extractor.of(series(axial, coronal)).isVolume()));
  }

  @Test
  void mr_weighting_comes_from_timing_inversion_diffusion_and_image_type() {
    assertAll(
        () ->
            assertEquals(
                MrWeighting.T1,
                extractor.of(series(mr("SE", 500, 15, null, null))).mrWeighting()), // NON-NLS
        () ->
            assertEquals(
                MrWeighting.T2,
                extractor.of(series(mr("SE", 4000, 100, null, null))).mrWeighting()), // NON-NLS
        () ->
            assertEquals(
                MrWeighting.PD,
                extractor.of(series(mr("SE", 3000, 20, null, null))).mrWeighting()), // NON-NLS
        () ->
            assertEquals(
                MrWeighting.FLAIR,
                extractor.of(series(mr("IR", 9000, 100, 2500.0, null))).mrWeighting()), // NON-NLS
        () ->
            assertEquals(
                MrWeighting.STIR,
                extractor.of(series(mr("IR", 4000, 60, 150.0, null))).mrWeighting()), // NON-NLS
        () ->
            assertEquals(
                MrWeighting.DWI,
                extractor.of(series(mr("EP", 5000, 80, null, 1000.0))).mrWeighting()), // NON-NLS
        () -> {
          Attributes adc = mr("EP", 5000, 80, null, null); // NON-NLS
          adc.setString(Tag.ImageType, VR.CS, "DERIVED", "PRIMARY", "DIFFUSION", "ADC"); // NON-NLS
          assertEquals(MrWeighting.ADC, extractor.of(series(adc)).mrWeighting());
        },
        () -> {
          Attributes swi = mr("GR", 30, 20, null, null); // NON-NLS
          swi.setString(Tag.SeriesDescription, VR.LO, "SWI axial"); // NON-NLS
          assertEquals(MrWeighting.SWI, extractor.of(series(swi)).mrWeighting());
        },
        () ->
            assertEquals(
                MrWeighting.UNKNOWN,
                extractor.of(series(image("MR", UID.MRImageStorage, 1))).mrWeighting())); // NON-NLS
  }

  private static Attributes mr(String sequence, double tr, double te, Double ti, Double bValue) {
    Attributes a = image("MR", UID.MRImageStorage, 1); // NON-NLS
    a.setString(Tag.ScanningSequence, VR.CS, sequence);
    a.setDouble(Tag.RepetitionTime, VR.DS, tr);
    a.setDouble(Tag.EchoTime, VR.DS, te);
    if (ti != null) {
      a.setDouble(Tag.InversionTime, VR.DS, ti);
    }
    if (bValue != null) {
      a.setDouble(Tag.DiffusionBValue, VR.FD, bValue);
    }
    return a;
  }

  @Test
  void a_mammogram_gives_view_and_laterality() {
    Attributes mg =
        image("MG", UID.DigitalMammographyXRayImageStorageForPresentation, 1); // NON-NLS
    mg.setString(Tag.ImageLaterality, VR.CS, "L"); // NON-NLS
    Attributes view = item(mg, Tag.ViewCodeSequence);
    view.setString(Tag.CodeValue, VR.SH, "R-10226"); // NON-NLS
    view.setString(Tag.CodingSchemeDesignator, VR.SH, "SRT"); // NON-NLS
    view.setString(Tag.CodeMeaning, VR.LO, "medio-lateral oblique"); // NON-NLS
    Attributes cr = image("CR", UID.ComputedRadiographyImageStorage, 1); // NON-NLS
    cr.setString(Tag.ViewPosition, VR.CS, "PA"); // NON-NLS
    cr.setString(Tag.Laterality, VR.CS, "X"); // NON-NLS

    SeriesFeatures f = extractor.of(series(mg));
    SeriesFeatures g = extractor.of(series(cr));
    assertAll(
        () -> assertEquals("MLO", f.view()), // NON-NLS
        () -> assertEquals("L", f.laterality()), // NON-NLS
        () -> assertEquals("PA", g.view()), // NON-NLS
        () -> assertNull(g.laterality(), "not a DICOM laterality value"));
  }

  @Test
  void pet_corrections_and_suv_capability_are_read() {
    Attributes ac = image("PT", UID.PositronEmissionTomographyImageStorage, 1); // NON-NLS
    ac.setString(Tag.CorrectedImage, VR.CS, "DECY", "ATTN", "SCAT"); // NON-NLS
    ac.setString(Tag.Units, VR.CS, "BQML"); // NON-NLS
    ac.setString(Tag.DecayCorrection, VR.CS, "START"); // NON-NLS
    ac.setDouble(Tag.PatientWeight, VR.DS, 70);
    Attributes radio = item(ac, Tag.RadiopharmaceuticalInformationSequence);
    radio.setDouble(Tag.RadionuclideTotalDose, VR.DS, 3.5e8);
    radio.setString(Tag.RadiopharmaceuticalStartTime, VR.TM, "101500"); // NON-NLS
    Attributes nac = image("PT", UID.PositronEmissionTomographyImageStorage, 1); // NON-NLS
    nac.setString(Tag.CorrectedImage, VR.CS, "DECY"); // NON-NLS
    nac.setString(Tag.Units, VR.CS, "CNTS"); // NON-NLS

    assertAll(
        () -> assertTrue(extractor.of(series(ac)).petCorrection().attenuationCorrected()),
        () -> assertTrue(extractor.of(series(ac)).petCorrection().suvCapable()),
        () -> assertFalse(extractor.of(series(nac)).petCorrection().attenuationCorrected()),
        () -> assertFalse(extractor.of(series(nac)).petCorrection().suvCapable()));
  }

  @Test
  void roles_come_from_image_type_and_sop_class() {
    Attributes localizer = ctSlice(1, 0);
    localizer.setString(Tag.ImageType, VR.CS, "ORIGINAL", "PRIMARY", "LOCALIZER"); // NON-NLS
    Attributes derived = ctSlice(1, 0);
    derived.setString(Tag.ImageType, VR.CS, "DERIVED", "PRIMARY", "AXIAL"); // NON-NLS
    Attributes reformat = ctSlice(1, 0);
    reformat.setString(
        Tag.ImageType, VR.CS, "DERIVED", "SECONDARY", "REFORMATTED", "AVERAGE"); // NON-NLS
    Attributes mip = ctSlice(1, 0);
    mip.setString(Tag.ImageType, VR.CS, "DERIVED", "SECONDARY", "MIP"); // NON-NLS
    Attributes shot = image("OT", UID.SecondaryCaptureImageStorage, 1); // NON-NLS
    shot.setString(Tag.ImageType, VR.CS, "DERIVED", "SECONDARY"); // NON-NLS
    Attributes dose = image("OT", UID.SecondaryCaptureImageStorage, 1); // NON-NLS
    dose.setString(Tag.SeriesDescription, VR.LO, "Patient Protocol / Dose report"); // NON-NLS
    Attributes ko = image("KO", UID.KeyObjectSelectionDocumentStorage, 1); // NON-NLS
    Attributes sr = image("SR", UID.ComprehensiveSRStorage, 1); // NON-NLS
    Attributes rdsr = image("SR", UID.XRayRadiationDoseSRStorage, 1); // NON-NLS

    assertAll(
        () -> assertEquals(Role.LOCALIZER, extractor.of(series(localizer)).role()),
        () -> assertEquals(Role.DERIVED, extractor.of(series(derived)).role()),
        () -> assertEquals(Role.REFORMAT, extractor.of(series(reformat)).role()),
        () -> assertEquals(Role.MIP, extractor.of(series(mip)).role()),
        () -> assertEquals(Role.SCREENSHOT, extractor.of(series(shot)).role()),
        () -> assertEquals(Role.DOSE_REPORT, extractor.of(series(dose)).role()),
        () -> assertEquals(Role.KEY_IMAGES, extractor.of(series(ko)).role()),
        () -> assertEquals(Role.DOCUMENT, extractor.of(series(sr)).role()),
        () -> assertEquals(Role.DOSE_REPORT, extractor.of(series(rdsr)).role()),
        () -> assertEquals(Orientation.UNKNOWN, extractor.of(series(ko)).orientation()));
  }

  @Test
  void temporal_positions_and_frame_timing_make_dynamic_and_cine() {
    Attributes dynamic = mr("GR", 5, 2, null, null); // NON-NLS
    dynamic.setInt(Tag.NumberOfTemporalPositions, VR.IS, 20);
    Attributes cine = image("XA", UID.XRayAngiographicImageStorage, 1); // NON-NLS
    cine.setInt(Tag.NumberOfFrames, VR.IS, 30);
    cine.setInt(Tag.CineRate, VR.IS, 15);
    Attributes multi = image("US", UID.UltrasoundMultiFrameImageStorage, 1); // NON-NLS
    multi.setInt(Tag.NumberOfFrames, VR.IS, 12);

    assertAll(
        () -> assertEquals(Temporal.DYNAMIC, extractor.of(series(dynamic)).temporal()),
        () -> assertEquals(Temporal.CINE, extractor.of(series(cine)).temporal()),
        () -> assertEquals(Temporal.CINE, extractor.of(series(multi)).temporal()),
        () -> assertEquals(Temporal.STATIC, extractor.of(series(ctSlice(1, 0))).temporal()));
  }

  @Test
  void contrast_is_unknown_without_the_module_and_none_when_the_agent_is_empty() {
    Attributes none = ctSlice(1, 0);
    none.setNull(Tag.ContrastBolusAgent, VR.LO);
    Attributes byName = ctSlice(1, 0);
    byName.setString(Tag.SeriesDescription, VR.LO, "Abdomen C+ arterial"); // NON-NLS
    assertAll(
        () -> assertEquals(Contrast.UNKNOWN, extractor.of(series(ctSlice(1, 0))).contrast()),
        () -> assertEquals(Contrast.NONE, extractor.of(series(none)).contrast()),
        () ->
            assertEquals(
                new Contrast(Contrast.Agent.WITH, Contrast.Phase.ARTERIAL),
                extractor.of(series(byName)).contrast()));
  }

  @Test
  void the_features_are_cached_until_an_image_is_added() {
    DicomSeries series = series(ctSlice(1, 0), ctSlice(2, 2.5));
    SeriesFeatures first = extractor.of(series);
    assertSame(first, extractor.of(series));

    series.addMedia(TestSeries.element(ctSlice(3, 5.0)));
    SeriesFeatures again = extractor.of(series);
    assertNotSame(first, again);
    assertEquals(3, again.imageCount());

    SeriesFeatureExtractor.invalidate(series);
    assertNotSame(again, extractor.of(series));
  }

  @Test
  void the_interest_provider_fills_the_interest() {
    SeriesFeatureExtractor withInterest =
        new SeriesFeatureExtractor(
            new DerivationTables(), s -> new Interest(2, Set.of("KO"))); // NON-NLS
    assertEquals(
        new Interest(2, Set.of("KO")),
        withInterest.of(series(ctSlice(1, 0))).interest()); // NON-NLS
  }

  @Test
  void enhanced_objects_are_read_from_the_shared_functional_groups() {
    Attributes mr = image("MR", UID.EnhancedMRImageStorage, 1); // NON-NLS
    Attributes shared = item(mr, Tag.SharedFunctionalGroupsSequence);
    Attributes timing = item(shared, Tag.MRTimingAndRelatedParametersSequence);
    timing.setDouble(Tag.RepetitionTime, VR.FD, 500);
    Attributes echo = item(shared, Tag.MREchoSequence);
    echo.setDouble(Tag.EffectiveEchoTime, VR.FD, 12);
    echo.setDouble(Tag.EchoTime, VR.DS, 12);
    Attributes anatomy = item(shared, Tag.FrameAnatomySequence);
    anatomy.setString(Tag.FrameLaterality, VR.CS, "R"); // NON-NLS
    Attributes measures = item(shared, Tag.PixelMeasuresSequence);
    measures.setDouble(Tag.SliceThickness, VR.DS, 3);

    SeriesFeatures f = extractor.of(series(mr));
    assertAll(
        () -> assertEquals(MrWeighting.T1, f.mrWeighting()),
        () -> assertEquals("R", f.laterality()), // NON-NLS
        () -> assertEquals(3.0, f.sliceThickness(), 1e-9));
  }

  @Test
  void a_large_series_is_sampled() {
    Attributes[] images = new Attributes[SeriesFeatureExtractor.SAMPLE * 3];
    for (int i = 0; i < images.length; i++) {
      images[i] = ctSlice(i + 1, i * 1.25);
    }
    SeriesFeatures f = extractor.of(series(images));
    assertAll(
        () -> assertEquals(images.length, f.imageCount()),
        () -> assertEquals(Orientation.AXIAL, f.orientation()),
        () -> assertTrue(f.spacing() > 1.25, "the sample skips slices"));
  }
}
