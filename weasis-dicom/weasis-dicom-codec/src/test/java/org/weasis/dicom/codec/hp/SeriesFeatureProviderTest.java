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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.weasis.dicom.codec.hp.TestSeries.ctSlice;
import static org.weasis.dicom.codec.hp.TestSeries.series;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.junit.jupiter.api.Test;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.provenance.Provenance;
import org.weasis.core.api.provenance.Provenance.Kind;
import org.weasis.core.api.provenance.Sourced;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.codec.hp.FeatureSelector.Key;
import org.weasis.dicom.codec.hp.SeriesFeatures.CtKernel;
import org.weasis.dicom.codec.hp.SeriesFeatures.Orientation;

/** A provider fills what the headers do not say, contests what they do, and never blocks. */
class SeriesFeatureProviderTest {

  private static SeriesFeatureProvider provider(
      String id, Map<String, Sourced<?>> values, boolean fails) {
    return new SeriesFeatureProvider() {
      @Override
      public String id() {
        return id;
      }

      @Override
      public Set<String> provides() {
        return values.keySet();
      }

      @Override
      public Map<String, Sourced<?>> derive(
          MediaSeries<DicomImageElement> series, SeriesFeatures soFar) {
        if (fails) {
          throw new IllegalStateException("model not loaded");
        }
        return values;
      }
    };
  }

  private static SeriesFeatureExtractor extractor(double threshold, SeriesFeatureProvider... p) {
    return new SeriesFeatureExtractor(
        new DerivationTables(), InterestProvider.NONE, List.of(p), id -> threshold);
  }

  @Test
  void aClassifierContestsAHeaderAndFillsAnUnknownFeature() {
    Attributes first = ctSlice(1, 0);
    first.remove(Tag.ConvolutionKernel);
    MediaSeries<DicomImageElement> ct = series(first, ctSlice(2, 2.5), ctSlice(3, 5.0));
    Provenance strong =
        Provenance.classifier("seqclass", "kernel family from the pixels", 0.93, "1.2"); // NON-NLS
    Provenance weak =
        Provenance.classifier("seqclass", "coronal from the pixels", 0.55, "1.2"); // NON-NLS
    SeriesFeatureProvider classifier =
        provider(
            "seqclass", // NON-NLS
            Map.of(
                Key.CT_KERNEL_FAMILY.id(), new Sourced<>("BONE", strong), // NON-NLS
                Key.ORIENTATION.id(), new Sourced<>("CORONAL", weak)), // NON-NLS
            false);
    SeriesFeatures f = extractor(0.8, classifier).extract(ct);

    assertAll(
        () ->
            assertEquals(CtKernel.Family.BONE, f.ctKernel().family(), "filled: no header said it"),
        () -> assertEquals(Kind.CLASSIFIER, f.provenanceOf(Key.CT_KERNEL_FAMILY.id()).kind()),
        () -> assertEquals(Orientation.AXIAL, f.orientation(), "a header is never overridden"),
        () -> assertEquals(Kind.HEADER, f.provenanceOf(Key.ORIENTATION.id()).kind()),
        () -> assertEquals(1, f.contestsOf(Key.ORIENTATION.id()).size()),
        () -> assertEquals("CORONAL", f.contestsOf(Key.ORIENTATION.id()).getFirst().value()),
        () ->
            assertTrue(
                ReasonProvenance.annotate("match orientation in [AXIAL]", f)
                    .contains("[header, contested: classifier seqclass 55%"),
                ReasonProvenance.annotate("match orientation in [AXIAL]", f)),
        () -> assertTrue(f.contestsJson().build().containsKey("orientation")));
  }

  @Test
  void belowTheThresholdAClassifierOnlyContestsAndAFailingProviderIsSkipped() {
    Attributes first = ctSlice(1, 0);
    first.remove(Tag.ConvolutionKernel);
    MediaSeries<DicomImageElement> ct = series(first, ctSlice(2, 2.5));
    Provenance weak = Provenance.classifier("seqclass", null, 0.5, "1.2"); // NON-NLS
    SeriesFeatureProvider timid =
        provider("seqclass", Map.of(Key.CT_KERNEL_FAMILY.id(), new Sourced<>("BONE", weak)), false);
    SeriesFeatureProvider broken = provider("broken", Map.of(), true); // NON-NLS
    SeriesFeatures f = extractor(0.8, broken, timid).extract(ct);
    assertAll(
        () -> assertEquals(null, f.ctKernel() == null ? null : f.ctKernel().family()),
        () -> assertEquals(1, f.contestsOf(Key.CT_KERNEL_FAMILY.id()).size()),
        () -> assertEquals("BONE", f.contestsOf(Key.CT_KERNEL_FAMILY.id()).getFirst().value()));
  }

  @Test
  void aUserValueOutranksEverythingAndARawFeatureCannotBeSet() {
    MediaSeries<DicomImageElement> ct = series(ctSlice(1, 0), ctSlice(2, 2.5));
    SeriesFeatures f =
        new SeriesFeatureExtractor(new DerivationTables(), InterestProvider.NONE).extract(ct);
    Provenance agent = Provenance.user("agent:remote", "orientation=CORONAL"); // NON-NLS
    SeriesFeatures corrected =
        f.with(Key.ORIENTATION, f.parse(Key.ORIENTATION, "coronal"), agent); // NON-NLS
    assertAll(
        () -> assertEquals(Orientation.CORONAL, corrected.orientation()),
        () -> assertEquals(Kind.USER, corrected.provenanceOf(Key.ORIENTATION.id()).kind()),
        () ->
            assertEquals(
                "agent:remote", corrected.sourcedOf(Key.ORIENTATION).provenance().source()),
        () -> assertEquals(f.imageCount(), corrected.imageCount()),
        () ->
            assertThrows(
                IllegalArgumentException.class, () -> f.with(Key.MODALITY, "MR", agent)), // NON-NLS
        () -> assertThrows(IllegalArgumentException.class, () -> f.parse(Key.ROLE, "nope")));
  }
}
