/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.viewer2d.subtraction;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.codec.DicomSeries;
import org.weasis.dicom.codec.TagD;
import org.weasis.dicom.viewer2d.subtraction.SubtractionCompatibility.Pairing;
import org.weasis.dicom.viewer2d.subtraction.SubtractionCompatibility.Refusal;
import org.weasis.dicom.viewer2d.subtraction.SubtractionCompatibility.Result;
import org.weasis.dicom.viewer2d.subtraction.SubtractionCompatibility.SlicePair;
import org.weasis.dicom.viewer2d.subtraction.SubtractionSpec.Dsa;

@DisplayNameGeneration(ReplaceUnderscores.class)
class SubtractionCompatibilityTest {

  private static final String POST = "1.2.3.10"; // NON-NLS
  private static final String PRE = "1.2.3.11"; // NON-NLS

  @Test
  void same_grid_pairs_every_slice_with_the_mask_slice_at_its_position() {
    DicomSeries post = SyntheticSeries.mr(POST, 3, 0);
    DicomSeries pre = SyntheticSeries.mr(PRE, 3, 0);
    Pairing pairing = assertInstanceOf(Pairing.class, SubtractionCompatibility.pair(post, pre));
    assertAll(
        () -> assertEquals(3, pairing.slices().size()),
        () -> assertTrue(pairing.sameFrameOfReference()),
        () -> assertTrue(SubtractionCompatibility.isCompatible(post, pre)));
    for (SlicePair pair : pairing.slices()) {
      assertEquals(
          SubtractionCompatibility.position(pair.image()),
          SubtractionCompatibility.position(pair.mask()),
          1e-9);
    }
  }

  @Test
  void the_pairs_follow_the_acquisition_order_of_the_series_not_the_positions() {
    Attributes[] images = new Attributes[3];
    for (int i = 0; i < 3; i++) {
      // instance 1 is the highest slice: the series was acquired top-down
      images[i] =
          SyntheticSeries.slice(POST, "MR", i + 1, 2.0 * (2 - i), 1.0, 256, null); // NON-NLS
    }
    DicomSeries post = SyntheticSeries.series(images);
    DicomSeries pre = SyntheticSeries.mr(PRE, 3, 0, 1.0, 256, null);
    Pairing pairing = assertInstanceOf(Pairing.class, SubtractionCompatibility.pair(post, pre));
    assertAll(
        () -> assertEquals(1, instance(pairing.slices().getFirst().image())),
        () -> assertEquals(3, instance(pairing.slices().getLast().image())),
        () ->
            assertEquals(
                SubtractionCompatibility.position(pairing.slices().getFirst().image()),
                SubtractionCompatibility.position(pairing.slices().getFirst().mask()),
                1e-9));
  }

  private static int instance(DicomImageElement image) {
    return TagD.getTagValue(image, Tag.InstanceNumber, Integer.class);
  }

  @Test
  void a_mask_shifted_by_less_than_half_a_slice_pairs_directly_a_larger_shift_resamples() {
    DicomSeries post = SyntheticSeries.mr(POST, 3, 0);
    Result near = SubtractionCompatibility.pair(post, SyntheticSeries.mr(PRE, 3, 0.4));
    Result far = SubtractionCompatibility.pair(post, SyntheticSeries.mr(PRE, 3, 1.5));
    Result farElsewhere =
        SubtractionCompatibility.pair(
            SyntheticSeries.mr(POST, 3, 0, 1.0, 256, null),
            SyntheticSeries.mr(PRE, 3, 1.5, 1.0, 256, null));
    assertAll(
        () -> assertFalse(assertInstanceOf(Pairing.class, near).resampled()),
        () -> assertTrue(assertInstanceOf(Pairing.class, far).resampled()),
        () ->
            assertTrue(
                assertInstanceOf(Refusal.class, farElsewhere).reason().contains("No mask slice")));
  }

  @Test
  void a_longer_mask_is_paired_by_position_not_by_index() {
    DicomSeries post = SyntheticSeries.mr(POST, 3, 4);
    DicomSeries pre = SyntheticSeries.mr(PRE, 6, 0);
    Pairing pairing = assertInstanceOf(Pairing.class, SubtractionCompatibility.pair(post, pre));
    assertEquals(3, pairing.slices().size());
    for (SlicePair pair : pairing.slices()) {
      assertEquals(
          SubtractionCompatibility.position(pair.image()),
          SubtractionCompatibility.position(pair.mask()),
          1e-9);
    }
  }

  @Test
  void a_slice_without_counterpart_leaves_the_direct_path_for_the_resampled_one() {
    DicomSeries post = SyntheticSeries.mr(POST, 4, 0);
    DicomSeries pre = SyntheticSeries.mr(PRE, 3, 0);
    Pairing pairing = assertInstanceOf(Pairing.class, SubtractionCompatibility.pair(post, pre));
    assertAll(
        () -> assertTrue(pairing.resampled()),
        () -> assertEquals(4, pairing.slices().size()),
        () -> assertSame(pre.getMedia(2, null, null), pairing.slices().getLast().mask()));
  }

  @Test
  void a_different_grid_in_the_same_frame_is_resampled_elsewhere_refused() {
    DicomSeries post = SyntheticSeries.mr(POST, 3, 0);
    Pairing spacing =
        assertInstanceOf(
            Pairing.class,
            SubtractionCompatibility.pair(
                post, SyntheticSeries.mr(PRE, 3, 0, 0.8, 256, SyntheticSeries.FRAME)));
    Pairing matrix =
        assertInstanceOf(
            Pairing.class,
            SubtractionCompatibility.pair(
                post, SyntheticSeries.mr(PRE, 3, 0, 1.0, 512, SyntheticSeries.FRAME)));
    Refusal frame =
        assertInstanceOf(
            Refusal.class,
            SubtractionCompatibility.pair(
                post, SyntheticSeries.mr(PRE, 3, 0, 1.0, 256, "9.9.9"))); // NON-NLS
    Refusal noFrame =
        assertInstanceOf(
            Refusal.class,
            SubtractionCompatibility.pair(
                SyntheticSeries.mr(POST, 3, 0, 1.0, 256, null),
                SyntheticSeries.mr(PRE, 3, 0, 1.0, 512, null)));
    assertAll(
        () -> assertTrue(spacing.resampled()),
        () -> assertTrue(matrix.resampled()),
        () -> assertEquals(3, matrix.slices().size(), "every slice, with the nearest mask slice"),
        () ->
            assertFalse(
                assertInstanceOf(
                        Pairing.class,
                        SubtractionCompatibility.pair(post, SyntheticSeries.mr(PRE, 3, 0)))
                    .resampled()),
        () -> assertTrue(frame.reason().contains("frames of reference")),
        () -> assertTrue(noFrame.reason().contains("matrices")));
  }

  @Test
  void operands_without_frame_of_reference_pair_with_a_warning_flag() {
    DicomSeries post = SyntheticSeries.mr(POST, 3, 0, 1.0, 256, null);
    DicomSeries pre = SyntheticSeries.mr(PRE, 3, 0, 1.0, 256, null);
    Pairing pairing = assertInstanceOf(Pairing.class, SubtractionCompatibility.pair(post, pre));
    assertFalse(pairing.sameFrameOfReference());
  }

  @Test
  void a_series_is_never_subtracted_from_itself_nor_across_modalities_or_studies() {
    DicomSeries post = SyntheticSeries.mr(POST, 3, 0);
    Attributes[] ct = new Attributes[3];
    Attributes[] otherStudy = new Attributes[3];
    for (int i = 0; i < 3; i++) {
      ct[i] = SyntheticSeries.slice(PRE, "CT", i + 1, 2.0 * i, 1.0, 256, SyntheticSeries.FRAME);
      otherStudy[i] =
          SyntheticSeries.slice(PRE, "MR", i + 1, 2.0 * i, 1.0, 256, SyntheticSeries.FRAME);
      otherStudy[i].setString(Tag.StudyInstanceUID, VR.UI, "7.7.7"); // NON-NLS
    }
    assertAll(
        () -> assertInstanceOf(Refusal.class, SubtractionCompatibility.pair(post, post)),
        () ->
            assertInstanceOf(
                Refusal.class, SubtractionCompatibility.pair(post, SyntheticSeries.series(ct))),
        () ->
            assertInstanceOf(
                Refusal.class,
                SubtractionCompatibility.pair(post, SyntheticSeries.series(otherStudy))),
        () -> assertInstanceOf(Refusal.class, SubtractionCompatibility.pair(post, null)));
  }

  @Test
  void the_frames_of_a_run_pair_with_the_averaged_mask_frames_within_the_range() {
    DicomSeries run = DsaMaskTest.run(null, null);
    Dsa dsa = new Dsa(List.of(1, 2), null, 2, 4, 0, 0);
    Pairing pairing =
        assertInstanceOf(Pairing.class, SubtractionCompatibility.pairFrames(run, dsa));
    List<DicomImageElement> frames = DsaMask.frames(run);
    assertAll(
        () -> assertEquals(3, pairing.slices().size(), "frames 2 to 4"),
        () -> assertSame(frames.get(1), pairing.slices().getFirst().image()),
        () ->
            assertEquals(
                List.of(frames.get(0), frames.get(1)), pairing.slices().getFirst().masks()),
        () -> assertSame(frames.get(0), pairing.slices().getFirst().mask()),
        () ->
            assertInstanceOf(
                Refusal.class, SubtractionCompatibility.pairFrames(run, Dsa.ofFrame(9))),
        () ->
            assertInstanceOf(
                Refusal.class,
                SubtractionCompatibility.pairFrames(
                    SyntheticSeries.mr(PRE, 3, 0), Dsa.ofFrame(1))));
  }

  @Test
  void a_time_interval_mask_pairs_each_frame_with_the_one_the_offset_points_to() {
    DicomSeries run = DsaMaskTest.run(null, null);
    List<DicomImageElement> frames = DsaMask.frames(run);
    Pairing forward =
        assertInstanceOf(
            Pairing.class,
            SubtractionCompatibility.pairFrames(run, new Dsa(List.of(), 2, null, null, 0, 0)));
    Pairing reverse =
        assertInstanceOf(
            Pairing.class,
            SubtractionCompatibility.pairFrames(run, new Dsa(List.of(), -1, null, null, 0, 0)));
    assertAll(
        () -> assertEquals(3, forward.slices().size(), "the first two frames have no mask"),
        () -> assertSame(frames.get(2), forward.slices().getFirst().image()),
        () -> assertSame(frames.get(0), forward.slices().getFirst().mask()),
        () -> assertEquals(4, reverse.slices().size(), "the last frame has no mask"),
        () -> assertSame(frames.get(1), reverse.slices().getFirst().mask()));
  }

  @Test
  void planar_images_without_geometry_pair_by_order_when_the_counts_match() {
    Attributes[] a = new Attributes[2];
    Attributes[] b = new Attributes[2];
    Attributes[] c = new Attributes[3];
    for (int i = 0; i < 3; i++) {
      if (i < 2) {
        a[i] = planar(POST, i + 1);
        b[i] = planar(PRE, i + 1);
      }
      c[i] = planar(PRE, i + 1);
    }
    DicomSeries post = SyntheticSeries.series(a);
    assertAll(
        () ->
            assertEquals(
                2,
                assertInstanceOf(
                        Pairing.class,
                        SubtractionCompatibility.pair(post, SyntheticSeries.series(b)))
                    .slices()
                    .size()),
        () ->
            assertInstanceOf(
                Refusal.class, SubtractionCompatibility.pair(post, SyntheticSeries.series(c))));
  }

  private static Attributes planar(String seriesUid, int instance) {
    Attributes a = SyntheticSeries.slice(seriesUid, "XA", instance, 0, 0.2, 1024, null); // NON-NLS
    a.remove(Tag.ImageOrientationPatient);
    a.remove(Tag.ImagePositionPatient);
    return a;
  }
}
