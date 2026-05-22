/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.qr;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.StringReader;
import java.io.StringWriter;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;
import org.dcm4che3.data.Tag;
import org.junit.jupiter.api.Test;
import org.weasis.dicom.param.DicomParam;
import org.weasis.dicom.qr.DicomQrView.Period;

/**
 * Tests {@link SearchParameters} — the persistent representation of a saved DICOM Q/R search. The
 * XML written by {@code saveSearchParameters} is read back by {@code loadSearchParameters} from the
 * user-preferences directory, so a regression in the schema or escaping silently changes the
 * studies that the clinician retrieves the next time they reopen the saved template.
 */
class SearchParametersTest {

  // -- Constructor + setters --------------------------------------------------

  @Test
  void constructor_setsName() {
    SearchParameters sp = new SearchParameters("MyTemplate");

    assertEquals("MyTemplate", sp.getName());
  }

  @Test
  void setName_rejectsNullKeepsCurrent() {
    SearchParameters sp = new SearchParameters("MyTemplate");

    sp.setName(null);

    assertEquals("MyTemplate", sp.getName(), "null name is rejected, current preserved");
  }

  @Test
  void setName_rejectsEmptyKeepsCurrent() {
    SearchParameters sp = new SearchParameters("MyTemplate");

    sp.setName("");

    assertEquals("MyTemplate", sp.getName(), "empty name is rejected");
  }

  @Test
  void setName_rejectsBlankKeepsCurrent() {
    SearchParameters sp = new SearchParameters("MyTemplate");

    sp.setName("   ");

    assertEquals("MyTemplate", sp.getName(), "blank-only name is rejected");
  }

  @Test
  void setPeriod_roundTrip() {
    SearchParameters sp = new SearchParameters("MyTemplate");

    sp.setPeriod(Period.TODAY);

    assertEquals(Period.TODAY, sp.getPeriod());
  }

  @Test
  void setPeriod_nullClearsValue() {
    SearchParameters sp = new SearchParameters("MyTemplate");
    sp.setPeriod(Period.TODAY);

    sp.setPeriod(null);

    assertNull(sp.getPeriod(), "null period is accepted (means no period filter)");
  }

  @Test
  void parameters_listIsInitiallyEmpty() {
    assertTrue(new SearchParameters("MyTemplate").getParameters().isEmpty());
  }

  @Test
  void parameters_listIsMutable() {
    SearchParameters sp = new SearchParameters("MyTemplate");
    DicomParam p = new DicomParam(Tag.PatientID, "MR-001");

    sp.getParameters().add(p);

    assertEquals(1, sp.getParameters().size());
    assertEquals(p, sp.getParameters().get(0));
  }

  @Test
  void toString_returnsName() {
    assertEquals("MyTemplate", new SearchParameters("MyTemplate").toString());
  }

  // -- saveSearchParameters: XML structure -----------------------------------

  @Test
  void save_writesNameAndPeriodAsAttributes() throws Exception {
    SearchParameters sp = new SearchParameters("MyTemplate");
    sp.setPeriod(Period.TODAY);

    String xml = saveAsXmlString(sp);

    assertAll(
        () -> assertTrue(xml.contains("name=\"MyTemplate\""), xml),
        () -> assertTrue(xml.contains("period=\"TODAY\""), xml));
  }

  @Test
  void save_nullPeriodWritesEmptyString() throws Exception {
    // The persisted XML must encode "no period" as empty, not omit the attribute, so load can
    // discriminate "absent" from "present but blank".
    SearchParameters sp = new SearchParameters("MyTemplate");

    String xml = saveAsXmlString(sp);

    assertTrue(xml.contains("period=\"\""), "null period -> period=\"\" attribute: " + xml);
  }

  @Test
  void save_singleDicomParamWritesTagAndValueElements() throws Exception {
    SearchParameters sp = new SearchParameters("MyTemplate");
    sp.getParameters().add(new DicomParam(Tag.PatientID, "MR-001"));

    String xml = saveAsXmlString(sp);

    assertAll(
        () -> assertTrue(xml.contains("<dicomParam"), xml),
        () ->
            assertTrue(
                xml.contains("tag=\"" + Tag.PatientID + "\""), "decimal tag attribute: " + xml),
        () -> assertTrue(xml.contains("<values>"), xml),
        () -> assertTrue(xml.contains("<value>MR-001</value>"), xml));
  }

  @Test
  void save_multipleValuesProduceMultipleValueElements() throws Exception {
    SearchParameters sp = new SearchParameters("MyTemplate");
    sp.getParameters().add(new DicomParam(Tag.ModalitiesInStudy, "CT", "MR", "US"));

    String xml = saveAsXmlString(sp);

    // Three separate <value> elements within a single <values> wrapper.
    long valueElementCount =
        xml.lines().flatMap(s -> Arrays.stream(s.split("<value>"))).count() - 1;
    assertAll(
        () -> assertTrue(xml.contains("<value>CT</value>"), xml),
        () -> assertTrue(xml.contains("<value>MR</value>"), xml),
        () -> assertTrue(xml.contains("<value>US</value>"), xml),
        () -> assertEquals(3, valueElementCount, "exactly 3 <value> tokens"));
  }

  @Test
  void save_parentSeqTagsAreCommaJoined() throws Exception {
    SearchParameters sp = new SearchParameters("MyTemplate");
    int[] parentSeq = {Tag.RequestedProcedureCodeSequence, Tag.ScheduledProtocolCodeSequence};
    sp.getParameters().add(new DicomParam(parentSeq, Tag.CodeValue, "ABC"));

    String xml = saveAsXmlString(sp);

    assertTrue(
        xml.contains("parentSeqTags=\"" + parentSeq[0] + "," + parentSeq[1] + "\""),
        "parent sequence tags joined with comma: " + xml);
  }

  @Test
  void save_emptyValuesArrayWritesEmptyValuesWrapper() throws Exception {
    // Documented behaviour: zero-length values array writes an empty <values></values> wrapper.
    // Loading this back yields a DicomParam with a zero-length values array, NOT a single
    // empty string — matching save-side semantics.
    SearchParameters sp = new SearchParameters("MyTemplate");
    sp.getParameters().add(new DicomParam(Tag.SOPInstanceUID));

    String xml = saveAsXmlString(sp);

    assertAll(
        () -> assertTrue(xml.contains("<dicomParam"), xml),
        () -> assertTrue(xml.contains("<values>"), "wrapper written even for empty array: " + xml),
        () ->
            assertTrue(!xml.contains("<value>"), "no nested <value> when array is empty: " + xml));
  }

  @Test
  void save_multipleParamsAllPresentInOrder() throws Exception {
    SearchParameters sp = new SearchParameters("MyTemplate");
    sp.getParameters().add(new DicomParam(Tag.PatientID, "MR-001"));
    sp.getParameters().add(new DicomParam(Tag.Modality, "CT"));

    String xml = saveAsXmlString(sp);

    int patientIdx = xml.indexOf("tag=\"" + Tag.PatientID + "\"");
    int modalityIdx = xml.indexOf("tag=\"" + Tag.Modality + "\"");
    assertAll(
        () -> assertTrue(patientIdx > 0, "PatientID present: " + xml),
        () -> assertTrue(modalityIdx > 0, "Modality present: " + xml),
        () -> assertTrue(patientIdx < modalityIdx, "insertion order preserved"));
  }

  // -- End-to-end round-trip (via reflection on the package-private parser) --

  @Test
  void roundTrip_singleParamWithSingleValuePreservesFields() throws Exception {
    SearchParameters original = new SearchParameters("MyTemplate");
    original.setPeriod(Period.TODAY);
    original.getParameters().add(new DicomParam(Tag.PatientID, "MR-001"));

    SearchParameters reloaded = saveAndReload(original);

    assertNotNull(reloaded, "reload must produce a SearchParameters");
    assertAll(
        () -> assertEquals("MyTemplate", reloaded.getName()),
        () -> assertEquals(Period.TODAY, reloaded.getPeriod()),
        () -> assertEquals(1, reloaded.getParameters().size()));
    DicomParam p = reloaded.getParameters().get(0);
    assertAll(
        () -> assertEquals(Tag.PatientID, p.getTag()),
        () -> assertEquals(1, p.getValues().length),
        () -> assertEquals("MR-001", p.getValues()[0]));
  }

  @Test
  void roundTrip_multipleValuesPreservedInOrder() throws Exception {
    SearchParameters original = new SearchParameters("MyTemplate");
    original.getParameters().add(new DicomParam(Tag.ModalitiesInStudy, "CT", "MR", "US"));

    SearchParameters reloaded = saveAndReload(original);

    assertNotNull(reloaded);
    DicomParam p = reloaded.getParameters().get(0);
    assertEquals(Arrays.asList("CT", "MR", "US"), Arrays.asList(p.getValues()));
  }

  @Test
  void roundTrip_parentSeqTagsPreserved() throws Exception {
    int[] parentSeq = {Tag.RequestedProcedureCodeSequence, Tag.ScheduledProtocolCodeSequence};
    SearchParameters original = new SearchParameters("MyTemplate");
    original.getParameters().add(new DicomParam(parentSeq, Tag.CodeValue, "ABC"));

    SearchParameters reloaded = saveAndReload(original);

    assertNotNull(reloaded);
    DicomParam p = reloaded.getParameters().get(0);
    assertAll(
        () -> assertEquals(Tag.CodeValue, p.getTag()),
        () -> assertEquals(2, p.getParentSeqTags().length),
        () -> assertEquals(parentSeq[0], p.getParentSeqTags()[0]),
        () -> assertEquals(parentSeq[1], p.getParentSeqTags()[1]));
  }

  @Test
  void roundTrip_nullPeriodPreservedAsNull() throws Exception {
    SearchParameters original = new SearchParameters("MyTemplate");
    original.getParameters().add(new DicomParam(Tag.PatientID, "MR-001"));

    SearchParameters reloaded = saveAndReload(original);

    assertNotNull(reloaded);
    assertNull(reloaded.getPeriod(), "null period round-trips to null, not to TODAY or ALL");
  }

  // -- Helpers ---------------------------------------------------------------

  /**
   * Serialise a single {@link SearchParameters} into a standalone XML document. Wraps it in a
   * top-level &lt;searchParameters&gt; element since {@code saveSearchParameters} expects its
   * caller to have already written the start element.
   */
  private static String saveAsXmlString(SearchParameters sp) throws Exception {
    StringWriter sw = new StringWriter();
    XMLStreamWriter writer = XMLOutputFactory.newInstance().createXMLStreamWriter(sw);
    writer.writeStartDocument();
    writer.writeStartElement(SearchParameters.T_NODE);
    sp.saveSearchParameters(writer);
    writer.writeEndElement();
    writer.writeEndDocument();
    writer.close();
    return sw.toString();
  }

  /**
   * Round-trip a {@link SearchParameters} through save + parse, invoking the package-private XML
   * loader via reflection. This pins the contract that the persisted schema is symmetric — a
   * regression on either side would surface as round-trip drift.
   */
  private static SearchParameters saveAndReload(SearchParameters original) throws Exception {
    String xml = saveAsXmlString(original);

    XMLInputFactory factory = XMLInputFactory.newInstance();
    factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, Boolean.FALSE);
    factory.setProperty(XMLInputFactory.SUPPORT_DTD, Boolean.FALSE);
    XMLStreamReader reader = factory.createXMLStreamReader(new StringReader(xml));

    // Advance to the searchParameters START_ELEMENT — the parser expects the cursor positioned
    // there.
    while (reader.hasNext()) {
      int evt = reader.next();
      if (evt == XMLStreamConstants.START_ELEMENT
          && SearchParameters.T_NODE.equals(reader.getName().getLocalPart())) {
        break;
      }
    }

    List<SearchParameters> result = new ArrayList<>();
    Method m =
        SearchParameters.class.getDeclaredMethod(
            "readSearchParameters", XMLStreamReader.class, List.class);
    m.setAccessible(true);
    m.invoke(null, reader, result);

    return result.isEmpty() ? null : result.get(0);
  }
}
