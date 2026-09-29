/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.sr;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Sequence;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.dcm4che3.data.VR;
import org.joml.Vector3d;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.weasis.core.ui.model.graphic.Graphic;
import org.weasis.core.ui.model.layer.LayerType;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.codec.DicomMediaIO;
import org.weasis.dicom.codec.TagD;
import org.weasis.dicom.codec.geometry.GeometryOfSlice;

/** The SR element as an image overlay provider: index, applicability, highlight. */
@DisplayNameGeneration(ReplaceUnderscores.class)
class SRSpecialElementTest {

  private static final String IMAGE_UID = "1.2.826.0.1.3680043.8.498.1";
  private static final String FOR = "1.2.3";

  private SRSpecialElement element;

  @BeforeEach
  void setUp() {
    Attributes root = new Attributes();
    root.setString(Tag.SOPClassUID, VR.UI, UID.EnhancedSRStorage);
    root.setString(Tag.Modality, VR.CS, "SR");
    root.setString(Tag.ValueType, VR.CS, "CONTAINER");
    root.setString(Tag.ContinuityOfContent, VR.CS, "SEPARATE");
    root.newSequence(Tag.ConceptNameCodeSequence, 1).add(code("Imaging Measurement Report"));
    Sequence rootSeq = root.newSequence(Tag.ContentSequence, 3);

    // 1.1: IMAGE, 1.2: SCOORD selected from 1.1, 1.3: SCOORD3D in a frame of reference
    rootSeq.add(image("CONTAINS"));
    Attributes region = item("CONTAINS", "SCOORD", "Image Region");
    region.setString(Tag.GraphicType, VR.CS, "POLYLINE");
    region.setFloat(Tag.GraphicData, VR.FL, 10, 10, 50, 10, 50, 50, 10, 50, 10, 10);
    region.newSequence(Tag.ContentSequence, 1).add(image("SELECTED FROM"));
    rootSeq.add(region);
    Attributes volume = item("CONTAINS", "SCOORD3D", "Volume Region");
    volume.setString(Tag.GraphicType, VR.CS, "POINT");
    volume.setFloat(Tag.GraphicData, VR.FL, -90, -90, 50);
    volume.setString(Tag.ReferencedFrameOfReferenceUID, VR.UI, FOR);
    rootSeq.add(volume);

    DicomMediaIO io = mock(DicomMediaIO.class);
    when(io.getDicomObject()).thenReturn(root);
    element = new SRSpecialElement(io);
  }

  private static Attributes code(String meaning) {
    Attributes c = new Attributes();
    c.setString(Tag.CodeValue, VR.SH, "1");
    c.setString(Tag.CodingSchemeDesignator, VR.SH, "99TEST");
    c.setString(Tag.CodeMeaning, VR.LO, meaning);
    return c;
  }

  private static Attributes item(String relationship, String valueType, String meaning) {
    Attributes a = new Attributes();
    a.setString(Tag.RelationshipType, VR.CS, relationship);
    a.setString(Tag.ValueType, VR.CS, valueType);
    a.newSequence(Tag.ConceptNameCodeSequence, 1).add(code(meaning));
    return a;
  }

  private static Attributes image(String relationship) {
    Attributes a = item(relationship, "IMAGE", "Source of Measurement");
    Attributes ref = new Attributes();
    ref.setString(Tag.ReferencedSOPClassUID, VR.UI, UID.CTImageStorage);
    ref.setString(Tag.ReferencedSOPInstanceUID, VR.UI, IMAGE_UID);
    a.newSequence(Tag.ReferencedSOPSequence, 1).add(ref);
    return a;
  }

  /** An axial slice at z = 50 of the given frame of reference. */
  private static DicomImageElement slice(String sopUID, String frameOfReference, double z) {
    DicomImageElement img = mock(DicomImageElement.class);
    when(img.getTagValue(TagD.get(Tag.SOPInstanceUID))).thenReturn(sopUID);
    when(img.getFrameOfReferenceUID()).thenReturn(frameOfReference);
    when(img.getSliceGeometry())
        .thenReturn(
            new GeometryOfSlice(
                new Vector3d(1, 0, 0),
                new Vector3d(0, 1, 0),
                new Vector3d(-100, -100, z),
                new Vector3d(1, 1, 2),
                2.0,
                new Vector3d(256, 256, 20)));
    return img;
  }

  @Test
  void overlay_is_a_dicom_sr_layer_named_after_the_report() {
    assertEquals(LayerType.DICOM_SR, element.getOverlayLayerType());
    assertTrue(element.getOverlayLayerName().contains("Imaging Measurement Report"));
    assertEquals(2, element.getGraphicReferences().size());
  }

  @Test
  void applies_to_the_referenced_image_and_to_slices_of_the_frame_of_reference() {
    assertTrue(element.appliesTo(slice(IMAGE_UID, "9.9", 0)));
    assertTrue(element.appliesTo(slice("other", FOR, 50)));
    assertFalse(element.appliesTo(slice("other", "9.9", 50)));
  }

  @Test
  void builds_the_region_and_the_projected_point_on_the_referenced_slice() {
    List<Graphic> graphics = element.buildOverlayGraphics(slice(IMAGE_UID, FOR, 50));
    assertEquals(2, graphics.size(), "SCOORD region plus the SCOORD3D point in this slice");
    assertTrue(graphics.stream().allMatch(g -> g.getLineThickness() == SRGraphic.LINE_THICKNESS));

    List<Graphic> otherSlice = element.buildOverlayGraphics(slice("other", FOR, 60));
    assertTrue(otherSlice.isEmpty(), "the 3D point is 10 mm away from this slice");

    assertTrue(element.buildOverlayGraphics(slice("other", "9.9", 50)).isEmpty());
  }

  @Test
  void highlighted_items_are_drawn_thicker() {
    element.setHighlightedNodes(Set.of("1.2"));

    List<Graphic> graphics = element.buildOverlayGraphics(slice(IMAGE_UID, "9.9", 0));

    assertEquals(1, graphics.size());
    assertEquals(SRGraphic.HIGHLIGHT_THICKNESS, graphics.get(0).getLineThickness());

    element.setHighlightedNodes(null);
    assertEquals(
        SRGraphic.LINE_THICKNESS,
        element.buildOverlayGraphics(slice(IMAGE_UID, "9.9", 0)).get(0).getLineThickness());
  }
}
