/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.wsi.model;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.weasis.dicom.wsi.model.SyntheticSlides.instance;
import static org.weasis.dicom.wsi.model.SyntheticSlides.opticalPaths;
import static org.weasis.dicom.wsi.model.SyntheticSlides.sparse;
import static org.weasis.dicom.wsi.model.SyntheticSlides.tiledFull;

import java.util.List;
import java.util.Optional;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Sequence;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.dcm4che3.data.VR;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.weasis.core.api.image.color.IccProfile;

/** The slide model built from synthetic whole slide headers. */
@DisplayNameGeneration(ReplaceUnderscores.class)
class SlideBuilderTest {

  private static Slide single(Attributes... headers) {
    List<Slide> slides = SlideBuilder.build(List.of(headers));
    assertEquals(1, slides.size());
    return slides.getFirst();
  }

  private static Optional<FrameRef> ref(String uid, int frame) {
    return Optional.of(new FrameRef(uid, frame));
  }

  @Test
  void tiled_full_frames_run_along_the_rows_with_partial_edge_tiles() {
    PyramidLevel level = single(tiledFull("L0", 1000, 700, 256)).base(); // NON-NLS
    assertAll(
        () -> assertEquals(4, level.tilesAcross()),
        () -> assertEquals(3, level.tilesDown()),
        () -> assertEquals(ref("L0", 1), level.frame(0, 0)), // NON-NLS
        () -> assertEquals(ref("L0", 5), level.frame(0, 1)), // NON-NLS
        () -> assertEquals(ref("L0", 12), level.frame(3, 2)), // NON-NLS
        () -> assertEquals(Optional.empty(), level.frame(4, 0)),
        () -> assertEquals(Optional.empty(), level.frame(0, 3)),
        () -> assertFalse(level.isSparse()));
  }

  @Test
  void levels_are_sorted_from_the_base_with_their_downsample() {
    Slide slide =
        single(
            tiledFull("L2", 1000, 750, 256), // NON-NLS
            tiledFull("L0", 4000, 3000, 256), // NON-NLS
            tiledFull("L1", 2000, 1500, 256)); // NON-NLS
    List<PyramidLevel> levels = slide.levels();
    assertAll(
        () -> assertEquals(3, levels.size()),
        () -> assertEquals(4000, slide.width()),
        () -> assertEquals(3000, slide.height()),
        () -> assertEquals(1.0, levels.get(0).downsampleX()),
        () -> assertEquals(2.0, levels.get(1).downsampleX()),
        () -> assertEquals(4.0, levels.get(2).downsampleY()),
        () -> assertEquals(1, levels.get(1).index()),
        () -> assertEquals(java.util.Set.of("L1"), levels.get(1).instanceUIDs())); // NON-NLS
  }

  @Test
  void the_level_for_a_downsample_is_the_coarsest_still_fine_enough() {
    Slide slide =
        single(
            tiledFull("L0", 4000, 3000, 256), // NON-NLS
            tiledFull("L1", 2001, 1501, 256), // NON-NLS
            tiledFull("L2", 1000, 750, 256)); // NON-NLS
    assertAll(
        () -> assertEquals(0, slide.levelForDownsample(0.5).index()),
        () -> assertEquals(0, slide.levelForDownsample(1.9).index()),
        () -> assertEquals(1, slide.levelForDownsample(2.0).index(), "despite the odd size"),
        () -> assertEquals(1, slide.levelForDownsample(3.5).index()),
        () -> assertEquals(2, slide.levelForDownsample(100).index()));
  }

  @Test
  void a_concatenation_is_one_level_with_frame_offsets() {
    Attributes part1 = tiledFull("C1", 1000, 700, 256); // NON-NLS
    Attributes part2 = tiledFull("C2", 1000, 700, 256); // NON-NLS
    for (Attributes part : List.of(part1, part2)) {
      part.setString(Tag.ConcatenationUID, VR.UI, "9.9"); // NON-NLS
    }
    part1.setInt(Tag.NumberOfFrames, VR.IS, 7);
    part1.setInt(Tag.InConcatenationNumber, VR.US, 1);
    part1.setInt(Tag.ConcatenationFrameOffsetNumber, VR.UL, 0);
    part2.setInt(Tag.NumberOfFrames, VR.IS, 5);
    part2.setInt(Tag.InConcatenationNumber, VR.US, 2);
    part2.setInt(Tag.ConcatenationFrameOffsetNumber, VR.UL, 7);

    Slide slide = single(part2, part1);
    PyramidLevel level = slide.base();
    assertAll(
        () -> assertEquals(1, slide.levels().size()),
        () -> assertEquals(ref("C1", 7), level.frame(2, 1)), // NON-NLS
        () -> assertEquals(ref("C2", 1), level.frame(3, 1)), // NON-NLS
        () -> assertEquals(ref("C2", 5), level.frame(3, 2))); // NON-NLS
  }

  @Test
  void sparse_frames_are_placed_by_position_and_missing_tiles_are_empty() {
    PyramidLevel level =
        single(
                sparse(
                    "S0",
                    1500,
                    1100,
                    512,
                    new double[] {1, 1},
                    new double[] {513, 1},
                    new double[] {1025, 1025}))
            .base();
    assertAll(
        () -> assertTrue(level.isSparse()),
        () -> assertEquals(ref("S0", 1), level.frame(0, 0)), // NON-NLS
        () -> assertEquals(ref("S0", 2), level.frame(1, 0)), // NON-NLS
        () -> assertEquals(ref("S0", 3), level.frame(2, 2)), // NON-NLS
        () -> assertEquals(Optional.empty(), level.frame(0, 1), "background"));
  }

  @Test
  void sparse_focal_planes_are_indexed_by_increasing_z_offset() {
    PyramidLevel level =
        single(sparse("Z", 512, 512, 512, new double[] {1, 1, 1.5}, new double[] {1, 1, -0.5}))
            .base();
    assertAll(
        () -> assertEquals(2, level.focalPlanes()),
        () -> assertEquals(ref("Z", 2), level.frame(0, 0, 0, 0)), // NON-NLS
        () -> assertEquals(ref("Z", 1), level.frame(0, 0, 1, 0)), // NON-NLS
        () -> assertEquals(Optional.empty(), level.frame(0, 0, 2, 0)));
  }

  @Test
  void tiled_full_optical_paths_follow_the_focal_planes() {
    Attributes dcm = tiledFull("P", 1000, 700, 256); // NON-NLS
    opticalPaths(dcm, "DAPI", "FITC"); // NON-NLS
    dcm.setInt(Tag.TotalPixelMatrixFocalPlanes, VR.UL, 2);
    dcm.setInt(Tag.NumberOfFrames, VR.IS, 12 * 2 * 2);
    PyramidLevel level = single(dcm).base();
    assertAll(
        () -> assertEquals(List.of("DAPI", "FITC"), level.opticalPaths()), // NON-NLS
        () -> assertEquals(ref("P", 13), level.frame(0, 0, 1, 0)), // NON-NLS
        () -> assertEquals(ref("P", 25), level.frame(0, 0, 0, 1)), // NON-NLS
        () -> assertEquals(ref("P", 48), level.frame(3, 2, 1, 1)), // NON-NLS
        () -> assertEquals(Optional.empty(), level.frame(0, 0, 0, 2)));
  }

  @Test
  void each_optical_path_has_the_icc_profile_it_declares() {
    Attributes dcm = tiledFull("P", 1000, 700, 256); // NON-NLS
    opticalPaths(dcm, "BRIGHT", "DAPI", "BAD"); // NON-NLS
    dcm.setInt(Tag.NumberOfFrames, VR.IS, 12 * 3);
    Sequence paths = dcm.getSequence(Tag.OpticalPathSequence);
    paths.get(0).setBytes(Tag.ICCProfile, VR.OB, IccProfile.SRGB.data());
    paths.get(2).setBytes(Tag.ICCProfile, VR.OB, new byte[] {1, 2, 3});
    Slide slide = single(dcm);
    assertAll(
        () -> assertEquals(Optional.of(IccProfile.SRGB), slide.iccProfile(0)),
        () -> assertEquals(Optional.empty(), slide.iccProfile(1), "none declared: sRGB"),
        () -> assertEquals(Optional.empty(), slide.iccProfile(2), "an invalid profile is ignored"),
        () -> assertEquals(Optional.empty(), slide.iccProfile(7)));
  }

  @Test
  void associated_images_are_kept_out_of_the_pyramid() {
    Attributes label = instance("LB", "LABEL", 600, 400, 600); // NON-NLS
    Attributes overview = instance("OV", "LOCALIZER", 1200, 500, 1200); // NON-NLS
    Attributes thumbnail = instance("TH", "THUMBNAIL", 500, 375, 500); // NON-NLS
    Attributes other = tiledFull("CT", 512, 512, 512); // NON-NLS
    other.setString(Tag.SOPClassUID, VR.UI, UID.CTImageStorage);

    Slide slide =
        single(label, tiledFull("L0", 4000, 3000, 256), overview, thumbnail, other); // NON-NLS
    assertAll(
        () -> assertEquals(1, slide.levels().size()),
        () ->
            assertEquals(
                "LB",
                slide
                    .associatedImage(WsiImageType.LABEL)
                    .orElseThrow()
                    .sopInstanceUID()), // NON-NLS
        () ->
            assertEquals(1200, slide.associatedImage(WsiImageType.OVERVIEW).orElseThrow().width()),
        () ->
            assertEquals(
                375, slide.associatedImage(WsiImageType.THUMBNAIL).orElseThrow().height()));
  }

  @Test
  void pyramid_uids_split_a_series_into_slides_largest_first() {
    Attributes small = tiledFull("B0", 2000, 1000, 256); // NON-NLS
    small.setString(Tag.PyramidUID, VR.UI, "7.2"); // NON-NLS
    Attributes large = tiledFull("A0", 8000, 4000, 256); // NON-NLS
    large.setString(Tag.PyramidUID, VR.UI, "7.1"); // NON-NLS
    Attributes largeL1 = tiledFull("A1", 4000, 2000, 256); // NON-NLS
    largeL1.setString(Tag.PyramidUID, VR.UI, "7.1"); // NON-NLS

    List<Slide> slides = SlideBuilder.build(List.of(small, large, largeL1));
    assertAll(
        () -> assertEquals(2, slides.size()),
        () -> assertEquals(Optional.of("7.1"), slides.get(0).pyramidUID()), // NON-NLS
        () -> assertEquals(2, slides.get(0).levels().size()),
        () -> assertEquals(1, slides.get(1).levels().size()));
  }

  @Test
  void spacing_comes_from_pixel_measures_and_gives_the_magnification() {
    Attributes dcm = tiledFull("L0", 4000, 3000, 256); // NON-NLS
    Attributes measures = new Attributes();
    measures.setDouble(Tag.PixelSpacing, VR.DS, 0.0005, 0.00025);
    Attributes shared = new Attributes();
    shared.newSequence(Tag.PixelMeasuresSequence, 1).add(measures);
    dcm.newSequence(Tag.SharedFunctionalGroupsSequence, 1).add(shared);

    Slide slide = single(dcm);
    assertAll(
        () ->
            assertEquals(
                new PixelSpacing(0.0005, 0.00025), slide.base().pixelSpacing().orElseThrow()),
        () -> assertEquals(40.0, slide.magnification().orElseThrow(), 1e-9),
        () -> assertTrue(slide.objectiveLensPower().isEmpty()));
  }

  @Test
  void spacing_falls_back_on_the_imaged_volume_and_the_objective_wins() {
    Attributes dcm = tiledFull("L0", 4000, 2000, 256); // NON-NLS
    dcm.setFloat(Tag.ImagedVolumeWidth, VR.FL, 2.0f);
    dcm.setFloat(Tag.ImagedVolumeHeight, VR.FL, 1.0f);
    opticalPaths(dcm, "1");
    dcm.getSequence(Tag.OpticalPathSequence)
        .getFirst()
        .setDouble(Tag.ObjectiveLensPower, VR.DS, 20.0);

    Slide slide = single(dcm);
    assertAll(
        () ->
            assertEquals(
                new PixelSpacing(0.0005, 0.0005), slide.base().pixelSpacing().orElseThrow()),
        () -> assertEquals(20.0, slide.magnification().orElseThrow(), 1e-9));
  }

  @Test
  void frames_without_organization_or_positions_are_read_as_tiled_full() {
    Attributes dcm = tiledFull("L0", 512, 512, 256); // NON-NLS
    dcm.remove(Tag.DimensionOrganizationType);
    assertEquals(ref("L0", 4), single(dcm).base().frame(1, 1)); // NON-NLS
  }

  /** White with a* and b* written as 0 instead of 0x8080, as in the Imaging Data Commons slides. */
  private static Attributes unencodedWhite() {
    Attributes dcm = tiledFull("W", 512, 512, 256); // NON-NLS
    dcm.setInt(Tag.RecommendedAbsentPixelCIELabValue, VR.US, 65535, 0, 0);
    return dcm;
  }

  @Test
  void the_background_follows_the_recommended_absent_pixel_colour() {
    Attributes dark = tiledFull("L0", 512, 512, 256); // NON-NLS
    dark.setInt(Tag.RecommendedAbsentPixelCIELabValue, VR.US, 0, 0x8080, 0x8080);
    assertAll(
        () -> assertEquals(java.awt.Color.BLACK, single(dark).background()),
        () -> assertEquals(java.awt.Color.WHITE, single(unencodedWhite()).background()),
        () ->
            assertEquals(
                java.awt.Color.WHITE,
                single(tiledFull("L1", 512, 512, 256)).background(), // NON-NLS
                "glass of a brightfield slide"));
  }

  @Test
  void a_series_without_slide_instances_gives_no_slide() {
    Attributes ct = tiledFull("CT", 512, 512, 512); // NON-NLS
    ct.setString(Tag.SOPClassUID, VR.UI, UID.CTImageStorage);
    assertTrue(SlideBuilder.build(List.of(ct)).isEmpty());
  }
}
