/*
 * Copyright (c) 2024 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.explorer.wado;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import java.io.ByteArrayInputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.weasis.core.api.media.data.AttributeSource;
import org.weasis.core.api.media.data.TagUtil;
import org.weasis.dicom.codec.TagD.Level;
import org.weasis.dicom.mf.ArcParameters;
import org.weasis.dicom.mf.WadoParameters;

/**
 * End-to-end parity check for the manifests produced by Viewer-Hub. The XML and JSON strings mirror
 * exactly what Viewer-Hub serializes (verified against a running instance); only the identifiers
 * are synthetic. It asserts that both formats, read through the Weasis manifest primitives ({@link
 * JsonManifestParser#children}, {@link JsonAttributeSource}, {@link
 * org.weasis.core.api.media.data.XmlAttributeSource}), build the same Patient/Study/Series/Instance
 * tree, and that a {@code DICOM_WEB} manifest truncated at the study or series level is detected as
 * partial so Weasis completes it through QIDO-RS.
 */
class ViewerHubManifestParityTest {

  private static final String[] PATIENT_KEYS = {
    "PatientID", "PatientName", "IssuerOfPatientID", "PatientBirthDate", "PatientSex"
  };
  private static final String[] STUDY_KEYS = {
    "StudyInstanceUID", "StudyDescription", "StudyDate", "StudyTime", "AccessionNumber", "StudyID"
  };
  private static final String[] SERIES_KEYS = {
    "SeriesInstanceUID", "SeriesDescription", "SeriesNumber", "Modality", "WadoTransferSyntaxUID"
  };
  private static final String[] INSTANCE_KEYS = {"SOPInstanceUID", "InstanceNumber"};

  // Full DICOM manifest (WADO-URI): the whole hierarchy down to the instances.
  private static final String FULL_XML =
      """
      <?xml version='1.0' encoding='UTF-8'?>\
      <manifest xmlns="http://www.weasis.org/xsd/2.5">\
      <arcQuery arcId="arc1" baseUrl="http://pacs/wado" webLogin="dXNlcjpwYXNz"\
       requireOnlySOPInstanceUID="false" queryMode="DICOM">\
      <Patient PatientID="P1" IssuerOfPatientID="ISS" PatientName="DOE^JOHN"\
       PatientBirthDate="19860611" PatientSex="F">\
      <Study StudyInstanceUID="ST1" StudyDescription="head" StudyDate="20260611"\
       StudyTime="103731" AccessionNumber="ACC1" StudyID="1">\
      <Series SeriesInstanceUID="SE1" SeriesDescription="t2" SeriesNumber="13" Modality="MR"\
       WadoTransferSyntaxUID="*">\
      <Instance SOPInstanceUID="IM1" InstanceNumber="1"/>\
      <Instance SOPInstanceUID="IM2" InstanceNumber="2"/>\
      </Series></Study></Patient></arcQuery></manifest>""";

  private static final String FULL_JSON =
      """
      {"manifest":{"arcQuery":[{"Patient":[{"PatientID":"P1","IssuerOfPatientID":"ISS",\
      "PatientName":"DOE^JOHN","PatientBirthDate":"19860611","PatientSex":"F","Study":[{"Series":[\
      {"Instance":[{"SOPInstanceUID":"IM1","InstanceNumber":1},\
      {"SOPInstanceUID":"IM2","InstanceNumber":2}],"SeriesInstanceUID":"SE1","SeriesDescription":"t2",\
      "SeriesNumber":13,"Modality":"MR","WadoTransferSyntaxUID":"*"}],"StudyInstanceUID":"ST1",\
      "StudyDescription":"head","StudyDate":"20260611","StudyTime":"103731","AccessionNumber":"ACC1",\
      "StudyID":"1"}]}],"arcId":"arc1","baseUrl":"http://pacs/wado","webLogin":"dXNlcjpwYXNz",\
      "requireOnlySOPInstanceUID":false,"queryMode":"DICOM"}]}}""";

  // DICOM_WEB manifest truncated at the study level (Viewer-Hub dicom-web-level-limit=STUDY).
  private static final String STUDY_PARTIAL_JSON =
      """
      {"manifest":{"arcQuery":[{"Patient":[{"PatientID":"P1","Study":[{"StudyInstanceUID":"ST1"}]}],\
      "arcId":"arc1","baseUrl":"http://pacs/rs","requireOnlySOPInstanceUID":false,\
      "queryMode":"DICOM_WEB"}]}}""";

  // DICOM_WEB manifest truncated at the series level (Viewer-Hub dicom-web-level-limit=SERIE).
  private static final String SERIE_PARTIAL_JSON =
      """
      {"manifest":{"arcQuery":[{"Patient":[{"PatientID":"P1","Study":[{"Series":[\
      {"SeriesInstanceUID":"SE1","Modality":"MR"}],"StudyInstanceUID":"ST1"}]}],"arcId":"arc1",\
      "baseUrl":"http://pacs/rs","requireOnlySOPInstanceUID":false,"queryMode":"DICOM_WEB"}]}}""";

  @Test
  void jsonAndXmlBuildTheSameTree() throws Exception {
    assertEquals(xmlTree(FULL_XML), jsonTree(FULL_JSON));
  }

  @Test
  void jsonTreeHasTheExpectedContent() {
    assertEquals(
        List.of(
            "Patient PatientID=P1 PatientName=DOE^JOHN IssuerOfPatientID=ISS"
                + " PatientBirthDate=19860611 PatientSex=F",
            "  Study StudyInstanceUID=ST1 StudyDescription=head StudyDate=20260611"
                + " StudyTime=103731 AccessionNumber=ACC1 StudyID=1",
            "    Series SeriesInstanceUID=SE1 SeriesDescription=t2 SeriesNumber=13 Modality=MR"
                + " WadoTransferSyntaxUID=*",
            "      Instance SOPInstanceUID=IM1 InstanceNumber=1",
            "      Instance SOPInstanceUID=IM2 InstanceNumber=2"),
        jsonTree(FULL_JSON));
  }

  @Test
  void studyLevelDicomWebManifestIsPartialAtStudy() {
    JsonObject arcQuery = firstArcQuery(STUDY_PARTIAL_JSON);
    WadoParameters wado = wadoParameters(arcQuery);
    assertTrue(wado.isWadoRS());

    JsonObject study = onlyChild(onlyChild(arcQuery, Level.PATIENT), Level.STUDY);
    // No series in the manifest: Weasis must complete the study through QIDO-RS.
    boolean hasSeries = !JsonManifestParser.children(study, Level.SERIES.getTagName()).isEmpty();
    assertFalse(hasSeries);
    assertTrue(ManifestCompletion.isPartialLevel(wado, hasSeries));
  }

  @Test
  void seriesLevelDicomWebManifestIsPartialAtSeriesOnly() {
    JsonObject arcQuery = firstArcQuery(SERIE_PARTIAL_JSON);
    WadoParameters wado = wadoParameters(arcQuery);

    JsonObject study = onlyChild(onlyChild(arcQuery, Level.PATIENT), Level.STUDY);
    // The study carries its series, so the study level is complete...
    boolean studyHasSeries =
        !JsonManifestParser.children(study, Level.SERIES.getTagName()).isEmpty();
    assertTrue(studyHasSeries);
    assertFalse(ManifestCompletion.isPartialLevel(wado, studyHasSeries));

    // ...but the series has no instances, so it is completed through QIDO-RS.
    JsonObject series = onlyChild(study, Level.SERIES);
    boolean seriesHasInstances =
        !JsonManifestParser.children(series, Level.INSTANCE.getTagName()).isEmpty();
    assertFalse(seriesHasInstances);
    assertTrue(ManifestCompletion.isPartialLevel(wado, seriesHasInstances));
  }

  // --- JSON walk (Weasis JsonManifestParser primitives) -----------------------------------------

  private static List<String> jsonTree(String json) {
    JsonObject arcQuery = firstArcQuery(json);
    List<String> lines = new ArrayList<>();
    for (JsonObject patient : JsonManifestParser.children(arcQuery, Level.PATIENT.getTagName())) {
      AttributeSource p = new JsonAttributeSource(patient);
      lines.add(line("Patient", 0, p, PATIENT_KEYS));
      for (JsonObject study : JsonManifestParser.children(patient, Level.STUDY.getTagName())) {
        lines.add(line("Study", 1, new JsonAttributeSource(study), STUDY_KEYS));
        for (JsonObject series : JsonManifestParser.children(study, Level.SERIES.getTagName())) {
          lines.add(line("Series", 2, new JsonAttributeSource(series), SERIES_KEYS));
          for (JsonObject inst : JsonManifestParser.children(series, Level.INSTANCE.getTagName())) {
            lines.add(line("Instance", 3, new JsonAttributeSource(inst), INSTANCE_KEYS));
          }
        }
      }
    }
    return lines;
  }

  private static JsonObject firstArcQuery(String json) {
    try (var reader = Json.createReader(new StringReader(json))) {
      JsonObject manifest = reader.readObject().getJsonObject(ArcParameters.TAG_DOCUMENT_ROOT);
      return JsonManifestParser.children(manifest, ArcParameters.TAG_ARC_QUERY).getFirst();
    }
  }

  private static JsonObject onlyChild(JsonObject parent, Level level) {
    return JsonManifestParser.children(parent, level.getTagName()).getFirst();
  }

  private static WadoParameters wadoParameters(JsonObject arcQuery) {
    AttributeSource source = new JsonAttributeSource(arcQuery);
    boolean wadoRs =
        "DICOM_WEB".equals(TagUtil.getTagAttribute(source, ArcParameters.QUERY_MODE, null));
    return new WadoParameters(
        TagUtil.getTagAttribute(source, ArcParameters.ARCHIVE_ID, ""),
        TagUtil.getTagAttribute(source, ArcParameters.BASE_URL, null),
        false,
        "",
        null,
        null,
        wadoRs);
  }

  // --- XML walk (DOM, reading the same attribute names) -----------------------------------------

  private static List<String> xmlTree(String xml) throws Exception {
    var factory = DocumentBuilderFactory.newInstance();
    factory.setNamespaceAware(true);
    Element manifest =
        factory
            .newDocumentBuilder()
            .parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)))
            .getDocumentElement();
    Element arcQuery = childElements(manifest, ArcParameters.TAG_ARC_QUERY).getFirst();

    List<String> lines = new ArrayList<>();
    for (Element patient : childElements(arcQuery, Level.PATIENT.getTagName())) {
      lines.add(line("Patient", 0, domSource(patient), PATIENT_KEYS));
      for (Element study : childElements(patient, Level.STUDY.getTagName())) {
        lines.add(line("Study", 1, domSource(study), STUDY_KEYS));
        for (Element series : childElements(study, Level.SERIES.getTagName())) {
          lines.add(line("Series", 2, domSource(series), SERIES_KEYS));
          for (Element inst : childElements(series, Level.INSTANCE.getTagName())) {
            lines.add(line("Instance", 3, domSource(inst), INSTANCE_KEYS));
          }
        }
      }
    }
    return lines;
  }

  private static AttributeSource domSource(Element element) {
    return name -> {
      String value = element.getAttribute(name);
      return value.isEmpty() ? null : value;
    };
  }

  private static List<Element> childElements(Element parent, String tagName) {
    List<Element> result = new ArrayList<>();
    NodeList nodes = parent.getChildNodes();
    for (int i = 0; i < nodes.getLength(); i++) {
      Node node = nodes.item(i);
      if (node instanceof Element e && tagName.equals(e.getLocalName())) {
        result.add(e);
      }
    }
    return result;
  }

  // --- shared -----------------------------------------------------------------------------------

  private static String line(String level, int depth, AttributeSource source, String... keys) {
    StringBuilder sb = new StringBuilder("  ".repeat(depth)).append(level);
    for (String key : keys) {
      String value = source.getAttribute(key);
      if (value != null) {
        sb.append(' ').append(key).append('=').append(value);
      }
    }
    return sb.toString();
  }
}
