/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.wsi.sr;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.weasis.dicom.wsi.model.SyntheticSlides.tiledFull;

import java.util.List;
import java.util.Set;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.weasis.core.ui.model.graphic.Graphic;
import org.weasis.dicom.macro.SOPInstanceReference;
import org.weasis.dicom.sr.SRGraphic;
import org.weasis.dicom.sr.SRImageReference;
import org.weasis.dicom.wsi.model.Slide;
import org.weasis.dicom.wsi.model.SlideBuilder;

@DisplayNameGeneration(ReplaceUnderscores.class)
class SlideReportOverlayTest {

  private static Slide slide() {
    return SlideBuilder.build(
            List.of(tiledFull("L0", 1024, 1024, 256), tiledFull("L1", 512, 512, 256))) // NON-NLS
        .getFirst();
  }

  private static Attributes scoord(String origin, String type, float... data) {
    Attributes item = new Attributes();
    item.setString(Tag.ValueType, VR.CS, "SCOORD"); // NON-NLS
    item.setString(Tag.GraphicType, VR.CS, type);
    item.setFloat(Tag.GraphicData, VR.FL, data);
    item.setString(Tag.PixelOriginInterpretation, VR.CS, origin);
    return item;
  }

  private static SRImageReference reference(String uid, int[] frames, SRGraphic... graphics) {
    SRImageReference ref = new SRImageReference("1"); // NON-NLS
    SOPInstanceReference sop = new SOPInstanceReference();
    sop.setReferencedSOPInstanceUID(uid);
    if (frames != null) {
      sop.setReferencedFrameNumber(frames);
    }
    ref.setSopInstanceReference(sop);
    for (SRGraphic g : graphics) {
      ref.addGraphic(g);
    }
    return ref;
  }

  @Test
  void regions_of_the_pyramid_instances_come_to_the_base_level() {
    SRGraphic volume = new SRGraphic("1.1", scoord("VOLUME", "POINT", 10, 10), false); // NON-NLS
    // Frame 2 of level 1 (2 x 2 tiles of 256) is the tile (1, 0): its origin is (256, 0)
    SRGraphic frame = new SRGraphic("1.2", scoord("FRAME", "POINT", 10, 10), false); // NON-NLS
    SRGraphic elsewhere = new SRGraphic("1.3", scoord("VOLUME", "POINT", 5, 5), false); // NON-NLS
    List<Graphic> graphics =
        SlideReportOverlay.build(
            List.of(
                reference("L1", null, volume), // NON-NLS
                reference("L1", new int[] {2}, frame), // NON-NLS
                reference("OTHER", null, elsewhere)), // NON-NLS
            slide(),
            Set.of());
    assertAll(
        () -> assertEquals(2, graphics.size(), "the instance of another slide is left out"),
        () ->
            assertEquals(2 * 10 - 0.5, graphics.get(0).getShape().getBounds2D().getCenterX(), 1e-6),
        () ->
            assertEquals(
                2 * (256 + 10) - 0.5, graphics.get(1).getShape().getBounds2D().getCenterX(), 1e-6),
        () ->
            assertEquals(
                2 * 10 - 0.5, graphics.get(1).getShape().getBounds2D().getCenterY(), 1e-6));
  }

  @Test
  void an_optional_region_is_drawn_only_when_highlighted() {
    SRGraphic optional =
        new SRGraphic(
            "1.1", // NON-NLS
            scoord("VOLUME", "POINT", 10, 10), // NON-NLS
            false,
            SRGraphic.RenderingIntent.PRESENTATION_OPTIONAL);
    List<SRImageReference> refs = List.of(reference("L0", null, optional)); // NON-NLS
    assertAll(
        () -> assertTrue(SlideReportOverlay.build(refs, slide(), Set.of()).isEmpty()),
        () ->
            assertEquals(
                1, SlideReportOverlay.build(refs, slide(), Set.of("1.1")).size())); // NON-NLS
  }
}
