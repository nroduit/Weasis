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

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import java.io.StringReader;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamReader;
import org.dcm4che3.data.Tag;
import org.junit.jupiter.api.Test;
import org.weasis.core.api.media.data.AttributeSource;
import org.weasis.core.api.media.data.TagUtil;
import org.weasis.core.api.media.data.XmlAttributeSource;
import org.weasis.dicom.codec.TagD;

/**
 * Verifies that an XML manifest element and its JSON counterpart yield identical values when read
 * through the shared {@link TagUtil}/{@link TagD} pipeline. This is what guarantees the JSON and
 * XML manifests build the same model.
 */
class AttributeSourceParityTest {

  private static final String SERIES_XML =
      "<Series SeriesInstanceUID=\"1.2.840.10008.1\" Modality=\"CT\" SeriesNumber=\"3\""
          + " WadoTransferSyntaxUID=\"1.2.840.10008.1.2.1\" WadoCompressionRate=\"75\"/>";

  private static final JsonObject SERIES_JSON =
      Json.createObjectBuilder()
          .add("SeriesInstanceUID", "1.2.840.10008.1")
          .add("Modality", "CT")
          .add("SeriesNumber", 3)
          .add("WadoTransferSyntaxUID", "1.2.840.10008.1.2.1")
          .add("WadoCompressionRate", 75)
          .build();

  private static XmlAttributeSource xmlSource() throws Exception {
    XMLInputFactory factory = XMLInputFactory.newInstance();
    XMLStreamReader reader = factory.createXMLStreamReader(new StringReader(SERIES_XML));
    while (reader.next() != XMLStreamConstants.START_ELEMENT) {
      // Advance to the <Series> start element
    }
    return new XmlAttributeSource(reader);
  }

  @Test
  void xmlAndJsonReadIdenticalValues() throws Exception {
    AttributeSource xml = xmlSource();
    AttributeSource json = new JsonAttributeSource(SERIES_JSON);

    assertAll(
        () ->
            assertEquals(
                TagUtil.getTagAttribute(xml, "Modality", null),
                TagUtil.getTagAttribute(json, "Modality", null)),
        () ->
            assertEquals(
                TagUtil.getIntegerTagAttribute(xml, "WadoCompressionRate", null),
                TagUtil.getIntegerTagAttribute(json, "WadoCompressionRate", null)),
        () ->
            assertEquals(
                TagD.get(Tag.SeriesInstanceUID).getValue(xml),
                TagD.get(Tag.SeriesInstanceUID).getValue(json)),
        () ->
            assertEquals(
                TagD.get(Tag.SeriesNumber).getValue(xml),
                TagD.get(Tag.SeriesNumber).getValue(json)),
        () -> assertEquals("CT", TagD.get(Tag.Modality).getValue(json)),
        () ->
            assertEquals(
                Integer.valueOf(75),
                TagUtil.getIntegerTagAttribute(json, "WadoCompressionRate", null)));
  }
}
