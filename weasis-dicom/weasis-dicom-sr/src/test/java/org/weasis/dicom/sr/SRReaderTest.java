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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Sequence;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.dcm4che3.data.VR;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.weasis.core.api.media.data.TagUtil;
import org.weasis.dicom.sr.SRReader.ContentNode;

/**
 * Renders synthetic SR content trees (PS3.3 C.17.3) and checks the HTML and the link targets. The
 * trees mirror the documents attached to GitHub issues #922 and #923.
 */
@DisplayNameGeneration(ReplaceUnderscores.class)
class SRReaderTest {

  private static final String IMAGE_UID = "1.2.826.0.1.3680043.8.498.1";
  private static final String IMAGE_UID_2 = "1.2.826.0.1.3680043.8.498.2";

  // ===== Fixture builders =====

  private static Attributes code(String value, String scheme, String meaning) {
    Attributes c = new Attributes();
    c.setString(Tag.CodeValue, VR.SH, value);
    c.setString(Tag.CodingSchemeDesignator, VR.SH, scheme);
    c.setString(Tag.CodeMeaning, VR.LO, meaning);
    return c;
  }

  private static Attributes item(String relationship, String valueType, String meaning) {
    Attributes a = new Attributes();
    if (relationship != null) {
      a.setString(Tag.RelationshipType, VR.CS, relationship);
    }
    if (valueType != null) {
      a.setString(Tag.ValueType, VR.CS, valueType);
    }
    if (meaning != null) {
      a.newSequence(Tag.ConceptNameCodeSequence, 1).add(code("1", "99TEST", meaning));
    }
    return a;
  }

  private static Attributes container(String relationship, String meaning) {
    Attributes a = item(relationship, "CONTAINER", meaning);
    a.setString(Tag.ContinuityOfContent, VR.CS, "SEPARATE");
    return a;
  }

  private static Attributes text(String meaning, String value) {
    Attributes a = item("CONTAINS", "TEXT", meaning);
    a.setString(Tag.TextValue, VR.UT, value);
    return a;
  }

  private static Attributes image(String relationship, String sopInstanceUID) {
    Attributes a = item(relationship, "IMAGE", "Source of Measurement");
    Attributes ref = new Attributes();
    ref.setString(Tag.ReferencedSOPClassUID, VR.UI, UID.SecondaryCaptureImageStorage);
    ref.setString(Tag.ReferencedSOPInstanceUID, VR.UI, sopInstanceUID);
    a.newSequence(Tag.ReferencedSOPSequence, 1).add(ref);
    return a;
  }

  private static Attributes byReference(String relationship, int... id) {
    Attributes a = new Attributes();
    a.setString(Tag.RelationshipType, VR.CS, relationship);
    a.setInt(Tag.ReferencedContentItemIdentifier, VR.UL, id);
    return a;
  }

  private static Attributes polyline(float... points) {
    Attributes a = item("CONTAINS", "SCOORD", "Image Region");
    a.setString(Tag.GraphicType, VR.CS, "POLYLINE");
    a.setFloat(Tag.GraphicData, VR.FL, points);
    return a;
  }

  private static Attributes num(String meaning, String value, Attributes unit) {
    Attributes a = item("CONTAINS", "NUM", meaning);
    Attributes mv = new Attributes();
    mv.setString(Tag.NumericValue, VR.DS, value);
    mv.newSequence(Tag.MeasurementUnitsCodeSequence, 1).add(unit);
    a.newSequence(Tag.MeasuredValueSequence, 1).add(mv);
    return a;
  }

  private static Sequence children(Attributes parent, int size) {
    return parent.newSequence(Tag.ContentSequence, size);
  }

  private static Attributes root() {
    Attributes root = new Attributes();
    root.setString(Tag.SOPClassUID, VR.UI, UID.EnhancedSRStorage);
    root.setString(Tag.PatientName, VR.PN, "Doe^Jane");
    root.setString(Tag.PatientID, VR.LO, "P1");
    root.setString(Tag.ValueType, VR.CS, "CONTAINER");
    root.setString(Tag.ContinuityOfContent, VR.CS, "SEPARATE");
    root.newSequence(Tag.ConceptNameCodeSequence, 1)
        .add(code("126000", "DCM", "Imaging Measurement Report"));
    return root;
  }

  private static float[] square(float x, float y, float size) {
    return new float[] {x, y, x + size, y, x + size, y + size, x, y + size, x, y};
  }

  private static String render(Attributes root, Map<String, SRImageReference> map) {
    StringBuilder html = new StringBuilder();
    new SRReader(null, root).readDocumentGeneralModule(html, map);
    return html.toString();
  }

  private static String link(String key) {
    return "<a href=\"" + SRReader.LINK_PREFIX + key + "\">";
  }

  // ===== Tree =====

  @Test
  void content_items_are_numbered_by_position() {
    Attributes root = root();
    Sequence rootSeq = children(root, 2);
    Attributes group = container("CONTAINS", "Measurement Group");
    rootSeq.add(group);
    rootSeq.add(text("Comment", "second"));
    Sequence groupSeq = children(group, 2);
    groupSeq.add(text("A", "a"));
    groupSeq.add(text("B", "b"));

    ContentNode tree = SRReader.buildTree(root);
    Map<String, ContentNode> index = new HashMap<>();
    SRReader.indexTree(tree, index);

    assertEquals("1", tree.id);
    assertEquals(2, tree.children.size());
    assertEquals("1.1", tree.children.get(0).id);
    assertEquals("1.2", tree.children.get(1).id);
    assertEquals("1.1.2", tree.children.get(0).children.get(1).id);
    assertEquals(5, index.size());
    assertEquals("b", index.get("1.1.2").content.getTextValue());
  }

  @Test
  void anchors_are_well_formed_and_match_node_ids() {
    Attributes root = root();
    Sequence rootSeq = children(root, 1);
    Attributes group = container("CONTAINS", "Group");
    rootSeq.add(group);
    children(group, 1).add(text("Comment", "x"));

    String html = render(root, new HashMap<>());

    assertTrue(html.contains("<a name=\"1.1\"></a>"), html);
    assertTrue(html.contains("<a name=\"1.1.1\"></a>"), html);
    assertFalse(html.contains("\"<>"), "malformed anchor markup");
  }

  // ===== SCOORD (TID 1500 layout, issue #923) =====

  @Test
  void scoord_with_by_value_selected_from_image_links_to_the_image_with_a_graphic() {
    Attributes root = root();
    Attributes group = container("CONTAINS", "Measurement Group");
    children(root, 1).add(group);
    Sequence groupSeq = children(group, 2);
    groupSeq.add(image("CONTAINS", IMAGE_UID));
    Attributes region = polyline(square(10, 10, 50));
    children(region, 1).add(image("SELECTED FROM", IMAGE_UID));
    groupSeq.add(region);

    Map<String, SRImageReference> map = new HashMap<>();
    String html = render(root, map);

    SRImageReference ref = map.get("1.1.2");
    assertNotNull(ref, "the SCOORD node registers a link target under its own id");
    assertEquals(IMAGE_UID, ref.getSopInstanceReference().getReferencedSOPInstanceUID());
    assertEquals(1, ref.getGraphics().size());
    assertTrue(html.contains(link("1.1.2") + "POLYLINE</a>"), html);
    // The plain IMAGE item keeps its own link, without graphics
    assertTrue(html.contains(link("1.1.1")), html);
    assertFalse(map.get("1.1.1").hasGraphics());
  }

  @Test
  void by_value_scoord_with_stray_referenced_content_item_identifier_is_not_a_reference() {
    // Issue #923: the producer set (0040,DB73) on a by-value SCOORD, which PS3.3 C.17.3.2.5 forbids
    Attributes root = root();
    Attributes group = container("CONTAINS", "Measurement Group");
    children(root, 1).add(group);
    Sequence groupSeq = children(group, 2);
    groupSeq.add(image("CONTAINS", IMAGE_UID));
    Attributes region = polyline(square(10, 10, 50));
    region.setInt(Tag.ReferencedContentItemIdentifier, VR.UL, 1, 6, 1);
    children(region, 1).add(image("SELECTED FROM", IMAGE_UID));
    groupSeq.add(region);

    Map<String, SRImageReference> map = new HashMap<>();
    String html = render(root, map);

    assertFalse(html.contains(Messages.getString("SRReader.content_ref")), html);
    assertFalse(html.contains("href=\"#1.6.1\""), "no dead intra-document link");
    assertTrue(html.contains(link("1.1.2") + "POLYLINE</a>"), html);
    assertEquals(1, map.get("1.1.2").getGraphics().size());
  }

  @Test
  void scoord_selected_from_by_reference_resolves_backward_and_forward() {
    Attributes root = root();
    Attributes group = container("CONTAINS", "Measurement Group");
    children(root, 1).add(group);
    Sequence groupSeq = children(group, 3);
    groupSeq.add(image("CONTAINS", IMAGE_UID)); // 1.1.1
    Attributes backward = polyline(square(0, 0, 10)); // 1.1.2 -> 1.1.1
    children(backward, 1).add(byReference("SELECTED FROM", 1, 1, 1));
    groupSeq.add(backward);
    Attributes forward = polyline(square(20, 20, 10)); // 1.1.3 -> 1.1.4 (defined later)
    children(forward, 1).add(byReference("SELECTED FROM", 1, 1, 4));
    groupSeq.add(forward);
    groupSeq.add(image("CONTAINS", IMAGE_UID_2)); // 1.1.4

    Map<String, SRImageReference> map = new HashMap<>();
    render(root, map);

    assertEquals(
        IMAGE_UID, map.get("1.1.2").getSopInstanceReference().getReferencedSOPInstanceUID());
    assertEquals(1, map.get("1.1.2").getGraphics().size());
    assertEquals(
        IMAGE_UID_2, map.get("1.1.3").getSopInstanceReference().getReferencedSOPInstanceUID());
    assertEquals(1, map.get("1.1.3").getGraphics().size());
  }

  @Test
  void scoord_nested_under_an_image_item_falls_back_to_that_image() {
    // Issue #922 fixture: SCOORD items are children of the IMAGE item and have no SELECTED FROM
    Attributes root = root();
    Sequence rootSeq = children(root, 2);
    Attributes img1 = image("CONTAINS", IMAGE_UID);
    children(img1, 1).add(polyline(square(40, 40, 80)));
    rootSeq.add(img1);
    Attributes img2 = image("CONTAINS", IMAGE_UID);
    children(img2, 1).add(polyline(square(140, 150, 80)));
    rootSeq.add(img2);

    Map<String, SRImageReference> map = new HashMap<>();
    String html = render(root, map);

    SRImageReference first = map.get("1.1.1");
    SRImageReference second = map.get("1.2.1");
    assertNotNull(first);
    assertNotNull(second);
    assertEquals(IMAGE_UID, first.getSopInstanceReference().getReferencedSOPInstanceUID());
    assertEquals(IMAGE_UID, second.getSopInstanceReference().getReferencedSOPInstanceUID());
    assertEquals(1, first.getGraphics().size());
    assertEquals(1, second.getGraphics().size());
    assertEquals("1.1.1", first.getGraphics().get(0).nodeId());
    assertEquals("1.2.1", second.getGraphics().get(0).nodeId());
    assertTrue(html.contains(link("1.1.1") + "POLYLINE</a>"), html);
    assertTrue(html.contains(link("1.2.1") + "POLYLINE</a>"), html);
  }

  @Test
  void scoord_without_any_image_reference_has_no_link() {
    Attributes root = root();
    children(root, 1).add(polyline(square(0, 0, 10)));

    Map<String, SRImageReference> map = new HashMap<>();
    String html = render(root, map);

    assertNull(map.get("1.1"));
    assertFalse(html.contains(SRReader.LINK_PREFIX), html);
    assertTrue(html.contains(Messages.getString("SRReader.no_img_ref")), html);
  }

  @Test
  void scoord_selected_from_two_images_gets_one_link_and_graphic_per_image() {
    Attributes root = root();
    Attributes region = polyline(square(0, 0, 10));
    Sequence sel = children(region, 2);
    sel.add(image("SELECTED FROM", IMAGE_UID));
    sel.add(image("SELECTED FROM", IMAGE_UID_2));
    children(root, 1).add(region);

    Map<String, SRImageReference> map = new HashMap<>();
    String html = render(root, map);

    assertEquals(IMAGE_UID, map.get("1.1").getSopInstanceReference().getReferencedSOPInstanceUID());
    assertEquals(
        IMAGE_UID_2, map.get("1.1/1").getSopInstanceReference().getReferencedSOPInstanceUID());
    // The same item is drawn on both images; the graphic itself is built per image on demand
    assertEquals(1, map.get("1.1").getGraphics().size());
    assertEquals(1, map.get("1.1/1").getGraphics().size());
    assertEquals("1.1", map.get("1.1/1").getGraphics().get(0).nodeId());
    assertTrue(
        html.contains(link("1.1") + "POLYLINE</a>, " + link("1.1/1") + "POLYLINE</a>"), html);
  }

  // ===== Rendering Intent (CAD reports) =====

  private static Attributes renderingIntent(String codeValue, String meaning) {
    Attributes a = item("HAS CONCEPT MOD", "CODE", "Rendering Intent");
    a.getNestedDataset(Tag.ConceptNameCodeSequence).setString(Tag.CodeValue, VR.SH, "111056");
    a.getNestedDataset(Tag.ConceptNameCodeSequence)
        .setString(Tag.CodingSchemeDesignator, VR.SH, "DCM");
    Attributes value = code(codeValue, "DCM", meaning);
    a.newSequence(Tag.ConceptCodeSequence, 1).add(value);
    return a;
  }

  private static Attributes finding(String intentCode, String meaning) {
    Attributes finding = item("CONTAINS", "CODE", "Single Image Finding");
    finding
        .newSequence(Tag.ConceptCodeSequence, 1)
        .add(code("F-01796", "SRT", "Mammography breast density"));
    Sequence seq = children(finding, 2);
    if (intentCode != null) {
      seq.add(renderingIntent(intentCode, meaning));
    }
    Attributes region = polyline(square(10, 10, 20));
    region.setString(Tag.RelationshipType, VR.CS, "HAS PROPERTIES");
    children(region, 1).add(image("SELECTED FROM", IMAGE_UID));
    seq.add(region);
    return finding;
  }

  @Test
  void rendering_intent_of_a_finding_applies_to_its_regions() {
    Attributes root = root();
    Sequence rootSeq = children(root, 3);
    rootSeq.add(finding("111152", "Not for Presentation"));
    rootSeq.add(finding("111151", "Presentation Optional"));
    rootSeq.add(finding(null, null));

    Map<String, SRImageReference> map = new HashMap<>();
    String html = render(root, map);

    assertEquals(
        SRGraphic.RenderingIntent.NOT_FOR_PRESENTATION,
        map.get("1.1.2").getGraphics().get(0).intent());
    assertEquals(
        SRGraphic.RenderingIntent.PRESENTATION_OPTIONAL,
        map.get("1.2.2").getGraphics().get(0).intent());
    assertEquals(
        SRGraphic.RenderingIntent.PRESENTATION_REQUIRED,
        map.get("1.3.1").getGraphics().get(0).intent());
    assertTrue(
        html.contains("POLYLINE</a> <i>(" + Messages.getString("SRReader.intent_hidden") + ")</i>"),
        html);
    assertTrue(
        html.contains(
            "POLYLINE</a> <i>(" + Messages.getString("SRReader.intent_optional") + ")</i>"),
        html);
  }

  // ===== IMAGE references with segment and presentation state =====

  @Test
  void image_item_exposes_segment_number_and_presentation_state() {
    Attributes root = root();
    Sequence rootSeq = children(root, 2);
    Attributes segRef = image("CONTAINS", "1.2.3.seg");
    Attributes segItem = segRef.getNestedDataset(Tag.ReferencedSOPSequence);
    segItem.setString(Tag.ReferencedSOPClassUID, VR.UI, UID.SegmentationStorage);
    segItem.setInt(Tag.ReferencedSegmentNumber, VR.US, 3);
    rootSeq.add(segRef);
    Attributes withPr = image("CONTAINS", IMAGE_UID);
    Attributes pr = new Attributes();
    pr.setString(Tag.ReferencedSOPClassUID, VR.UI, UID.GrayscaleSoftcopyPresentationStateStorage);
    pr.setString(Tag.ReferencedSOPInstanceUID, VR.UI, "1.2.3.pr");
    withPr
        .getNestedDataset(Tag.ReferencedSOPSequence)
        .newSequence(Tag.ReferencedSOPSequence, 1)
        .add(pr);
    rootSeq.add(withPr);

    Map<String, SRImageReference> map = new HashMap<>();
    String html = render(root, map);

    assertTrue(map.get("1.1").isSegmentReference());
    assertEquals(3, map.get("1.1").getSegmentNumbers()[0]);
    assertNull(map.get("1.1").getPresentationStateUID());
    assertTrue(
        html.contains(
            link("1.1")
                + Messages.getString("SRReader.show_seg")
                + "</a> ("
                + Messages.getString("SRReader.segment")
                + " 3)"),
        html);
    assertFalse(map.get("1.2").isSegmentReference());
    assertEquals("1.2.3.pr", map.get("1.2").getPresentationStateUID());
    assertTrue(html.contains("(" + Messages.getString("SRReader.pr") + ")"), html);
  }

  // ===== TCOORD =====

  @Test
  void tcoord_registers_annotations_on_the_referenced_waveform() {
    Attributes root = root();
    Attributes num = num("QT Interval", "0.4", code("s", "UCUM", "s"));
    Attributes tcoord = item("INFERRED FROM", "TCOORD", "Interval");
    tcoord.setString(Tag.TemporalRangeType, VR.CS, "SEGMENT");
    tcoord.setInt(Tag.ReferencedSamplePositions, VR.UL, 100, 300);
    Attributes wave = item("SELECTED FROM", "WAVEFORM", null);
    Attributes ref = new Attributes();
    ref.setString(Tag.ReferencedSOPClassUID, VR.UI, UID.TwelveLeadECGWaveformStorage);
    ref.setString(Tag.ReferencedSOPInstanceUID, VR.UI, "1.2.3.ecg");
    ref.setInt(Tag.ReferencedWaveformChannels, VR.US, 1, 2);
    wave.newSequence(Tag.ReferencedSOPSequence, 1).add(ref);
    children(tcoord, 1).add(wave);
    children(num, 1).add(tcoord);
    children(root, 1).add(num);

    Map<String, SRImageReference> map = new HashMap<>();
    String html = render(root, map);

    SRImageReference r = map.get("1.1.1");
    assertNotNull(r);
    assertEquals("1.2.3.ecg", r.getSopInstanceReference().getReferencedSOPInstanceUID());
    assertEquals(1, r.getAnnotations().size());
    assertEquals("SEGMENT", r.getAnnotations().get(0).temporalRangeType());
    assertEquals("QT Interval = 0.4 s", r.getAnnotations().get(0).label());
    assertTrue(r.getAnnotations().get(0).appliesToChannel(2));
    assertFalse(r.getAnnotations().get(0).appliesToChannel(1));
    assertTrue(
        html.contains(
            link("1.1.1") + "SEGMENT</a>: 100, 300 " + Messages.getString("SRReader.samples")),
        html);
  }

  // ===== TABLE =====

  @Test
  void table_item_is_rendered_as_an_html_table() {
    Attributes root = root();
    Attributes tableItem = item("CONTAINS", "TABLE", "Results");
    Attributes table = new Attributes();
    table.setInt(Tag.NumberOfTableRows, VR.US, 2);
    table.setInt(Tag.NumberOfTableColumns, VR.US, 2);
    Sequence cols = table.newSequence(Tag.TableColumnDefinitionSequence, 2);
    Attributes c1 = new Attributes();
    c1.setInt(Tag.TableColumnNumber, VR.US, 1);
    c1.newSequence(Tag.ConceptNameCodeSequence, 1).add(code("42798000", "SCT", "Area"));
    c1.newSequence(Tag.MeasurementUnitsCodeSequence, 1).add(code("mm2", "UCUM", "mm2"));
    cols.add(c1);
    Attributes c2 = new Attributes();
    c2.setInt(Tag.TableColumnNumber, VR.US, 2);
    c2.newSequence(Tag.ConceptNameCodeSequence, 1).add(code("121071", "DCM", "Finding"));
    cols.add(c2);
    Sequence rows = table.newSequence(Tag.TableRowDefinitionSequence, 2);
    for (int r = 1; r <= 2; r++) {
      Attributes def = new Attributes();
      def.setInt(Tag.TableRowNumber, VR.US, r);
      def.newSequence(Tag.ConceptNameCodeSequence, 1).add(code("L" + r, "99TEST", "Lesion " + r));
      rows.add(def);
    }
    Sequence cells = table.newSequence(Tag.CellValuesSequence, 3);
    Attributes v11 = new Attributes();
    v11.setInt(Tag.TableRowNumber, VR.US, 1);
    v11.setInt(Tag.TableColumnNumber, VR.US, 1);
    v11.setString(Tag.SelectorAttributeVR, VR.CS, "DS");
    v11.setString(Tag.SelectorDSValue, VR.DS, "12.5");
    cells.add(v11);
    Attributes v12 = new Attributes();
    v12.setInt(Tag.TableRowNumber, VR.US, 1);
    v12.setInt(Tag.TableColumnNumber, VR.US, 2);
    v12.setString(Tag.SelectorAttributeVR, VR.CS, "SQ");
    v12.newSequence(Tag.ConceptCodeSequence, 1).add(code("4147007", "SCT", "Mass"));
    cells.add(v12);
    Attributes v21 = new Attributes();
    v21.setInt(Tag.TableRowNumber, VR.US, 2);
    v21.setInt(Tag.TableColumnNumber, VR.US, 1);
    v21.setInt(Tag.ReferencedContentItemIdentifier, VR.UL, 1, 2);
    cells.add(v21);
    tableItem.newSequence(Tag.TabulatedValuesSequence, 1).add(table);
    Sequence rootSeq = children(root, 2);
    rootSeq.add(tableItem);
    rootSeq.add(text("Comment", "target"));

    String html = render(root, new HashMap<>());

    assertFalse(html.contains(Messages.getString("SRReader.tag_missing")), html);
    assertTrue(html.contains("<th>Area (mm2)</th><th>Finding</th>"), html);
    assertTrue(html.contains("<th align=\"left\">Lesion 1</th><td>12.5</td><td>Mass</td>"), html);
    assertTrue(
        html.contains(
            "<th align=\"left\">Lesion 2</th><td><a href=\"#1.2\">"
                + Messages.getString("SRReader.node")
                + " 1.2</a> Comment</td><td></td>"),
        html);
  }

  // ===== By-reference items =====

  @Test
  void by_reference_item_links_to_its_target_and_flags_a_missing_one() {
    Attributes root = root();
    Sequence rootSeq = children(root, 3);
    rootSeq.add(text("Finding", "nodule")); // 1.1
    rootSeq.add(byReference("INFERRED FROM", 1, 1)); // 1.2 -> 1.1
    rootSeq.add(byReference("INFERRED FROM", 1, 9)); // 1.3 -> missing

    String html = render(root, new HashMap<>());

    assertTrue(
        html.contains("<a href=\"#1.1\">" + Messages.getString("SRReader.node") + " 1.1</a>"),
        html);
    assertTrue(html.contains("<a href=\"#1.1\">node 1.1</a> <B>Finding</B>"), html);
    assertTrue(html.contains("INFERRED FROM"), html);
    assertFalse(html.contains("href=\"#1.9\""), html);
    assertTrue(html.contains("1.9 (" + Messages.getString("SRReader.ref_missing") + ")"), html);
  }

  // ===== Values =====

  @Test
  void numeric_value_shows_units_and_qualifier() {
    Attributes root = root();
    Sequence rootSeq = children(root, 3);
    rootSeq.add(num("Diameter", "12.5", code("mm", "UCUM", "millimeter")));
    rootSeq.add(num("Probability", "0.503496", code("1", "UCUM", "no units")));
    Attributes nan = item("CONTAINS", "NUM", "Density");
    nan.newSequence(Tag.NumericValueQualifierCodeSequence, 1)
        .add(code("114000", "DCM", "Not a number"));
    rootSeq.add(nan);

    String html = render(root, new HashMap<>());

    assertTrue(html.contains("<B>Diameter</B> = 12.5 millimeter"), html);
    assertTrue(html.contains("<B>Probability</B> = 0.503496<"), "no unit text for UCUM 1: " + html);
    assertTrue(html.contains("<B>Density</B> = <i>Not a number</i>"), html);
  }

  @Test
  void date_time_values_are_formatted_and_text_is_escaped() {
    Attributes root = root();
    root.setString(Tag.InstitutionName, VR.LO, "Clinic <A&B>");
    Sequence rootSeq = children(root, 3);
    Attributes date = item("CONTAINS", "DATE", "Exam Date");
    date.setString(Tag.Date, VR.DA, "20260926");
    rootSeq.add(date);
    Attributes time = item("CONTAINS", "TIME", "Exam Time");
    time.setString(Tag.Time, VR.TM, "182941");
    rootSeq.add(time);
    rootSeq.add(text("Comment", "a <b>bold</b> line\nsecond"));

    String html = render(root, new HashMap<>());

    assertTrue(html.contains(TagUtil.formatDateTime(LocalDate.of(2026, 9, 26))), html);
    assertFalse(html.contains("1970"), "TM must not be rendered as a java.util.Date: " + html);
    assertTrue(html.contains("a &lt;b&gt;bold&lt;/b&gt; line<BR>second"), html);
    assertTrue(html.contains("Clinic &lt;A&amp;B&gt;"), html);
    assertFalse(html.contains("<A&B>"), html);
  }

  @Test
  void composite_and_waveform_references_show_the_sop_class_name_and_a_link() {
    Attributes root = root();
    Sequence rootSeq = children(root, 2);
    Attributes composite = item("CONTAINS", "COMPOSITE", "Prior Report");
    Attributes ref = new Attributes();
    ref.setString(Tag.ReferencedSOPClassUID, VR.UI, UID.ComprehensiveSRStorage);
    ref.setString(Tag.ReferencedSOPInstanceUID, VR.UI, "1.2.3.4");
    composite.newSequence(Tag.ReferencedSOPSequence, 1).add(ref);
    rootSeq.add(composite);
    Attributes wave = item("CONTAINS", "WAVEFORM", "ECG");
    Attributes wref = new Attributes();
    wref.setString(Tag.ReferencedSOPClassUID, VR.UI, UID.TwelveLeadECGWaveformStorage);
    wref.setString(Tag.ReferencedSOPInstanceUID, VR.UI, "1.2.3.5");
    wref.setInt(Tag.ReferencedWaveformChannels, VR.US, 1, 2, 1, 3);
    wave.newSequence(Tag.ReferencedSOPSequence, 1).add(wref);
    rootSeq.add(wave);

    Map<String, SRImageReference> map = new HashMap<>();
    String html = render(root, map);

    assertTrue(html.contains(SRReader.sopClassName(UID.ComprehensiveSRStorage)), html);
    assertFalse(
        html.contains("1.2.840.10008.5.1.4.1.1.88.33 "), "SOP class shown by name: " + html);
    assertTrue(html.contains(link("1.1") + Messages.getString("SRReader.show_obj")), html);
    assertTrue(html.contains(link("1.2") + Messages.getString("SRReader.show_wave")), html);
    assertTrue(html.contains("1/2, 1/3"), html);
    assertEquals("1.2.3.5", map.get("1.2").getSopInstanceReference().getReferencedSOPInstanceUID());
  }

  @Test
  void scoord3d_without_image_links_to_its_frame_of_reference() {
    Attributes root = root();
    Sequence rootSeq = children(root, 2);
    Attributes s3d = item("CONTAINS", "SCOORD3D", "Volume Region");
    s3d.setString(Tag.GraphicType, VR.CS, "POLYGON");
    s3d.setFloat(Tag.GraphicData, VR.FL, 0, 0, 0, 1, 0, 0, 1, 1, 0, 0, 0, 0);
    s3d.setString(Tag.ReferencedFrameOfReferenceUID, VR.UI, "1.2.3");
    rootSeq.add(s3d);
    Attributes tc = item("CONTAINS", "TCOORD", "Interval");
    tc.setString(Tag.TemporalRangeType, VR.CS, "SEGMENT");
    tc.setInt(Tag.ReferencedSamplePositions, VR.UL, 10, 200);
    rootSeq.add(tc);

    Map<String, SRImageReference> map = new HashMap<>();
    String html = render(root, map);

    assertFalse(html.contains(Messages.getString("SRReader.tag_missing")), html);
    assertTrue(
        html.contains(
            link("1.1") + "POLYGON</a> <i>3D, 4 " + Messages.getString("SRReader.points")),
        html);
    SRImageReference ref = map.get("1.1");
    assertNull(ref.getSopInstanceReference());
    assertEquals("1.2.3", ref.getFrameOfReferenceUID());
    assertEquals(1, ref.getGraphics().size());
    assertTrue(ref.getGraphics().get(0).threeD());
    assertEquals("POLYGON", ref.getGraphics().get(0).getGraphicType());
    assertTrue(html.contains("SEGMENT: 10, 200 " + Messages.getString("SRReader.samples")), html);
  }

  @Test
  void scoord3d_selected_from_an_image_links_to_that_image() {
    Attributes root = root();
    Attributes s3d = item("CONTAINS", "SCOORD3D", "Volume Region");
    s3d.setString(Tag.GraphicType, VR.CS, "POINT");
    s3d.setFloat(Tag.GraphicData, VR.FL, 1, 2, 3);
    s3d.setString(Tag.ReferencedFrameOfReferenceUID, VR.UI, "1.2.3");
    children(s3d, 1).add(image("SELECTED FROM", IMAGE_UID));
    children(root, 1).add(s3d);

    Map<String, SRImageReference> map = new HashMap<>();
    render(root, map);

    SRImageReference ref = map.get("1.1");
    assertEquals(IMAGE_UID, ref.getSopInstanceReference().getReferencedSOPInstanceUID());
    assertEquals("1.2.3", ref.getFrameOfReferenceUID());
    assertEquals("1.1", ref.getGraphics().get(0).nodeId());
  }

  @Test
  void document_header_shows_the_template_identifier() {
    Attributes root = root();
    Attributes tpl = new Attributes();
    tpl.setString(Tag.MappingResource, VR.CS, "DCMR");
    tpl.setString(Tag.TemplateIdentifier, VR.CS, "1500");
    root.newSequence(Tag.ContentTemplateSequence, 1).add(tpl);

    String html = render(root, new HashMap<>());

    assertTrue(
        html.contains("<B>" + Messages.getString("SRReader.template") + "</B>: DCMR 1500"), html);
    assertTrue(html.contains("<h1>Imaging Measurement Report</h1>"), html);
  }
}
