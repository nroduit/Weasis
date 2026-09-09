/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.explorer.wado;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import javax.xml.XMLConstants;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import org.junit.jupiter.api.Test;
import org.xml.sax.SAXException;

/** Checks the arcQuery attributes of the 2.5 schema used to validate a manifest. */
class ManifestSchemaTest {

  private static final String MANIFEST =
      """
      <?xml version='1.0' encoding='UTF-8'?>\
      <manifest xmlns="http://www.weasis.org/xsd/2.5">\
      <arcQuery arcId="arc1" baseUrl="http://pacs/dicomweb" queryMode="DICOM_WEB"\
       seriesRetrieve="true" thumbnailMode="%s"/>\
      </manifest>""";

  @Test
  void thumbnailModeAcceptsTheSupportedServices() {
    for (ThumbnailMode mode : ThumbnailMode.values()) {
      assertDoesNotThrow(() -> validate(MANIFEST.formatted(mode.name())), mode.name());
    }
  }

  @Test
  void thumbnailModeRejectsAnUnknownService() {
    assertThrows(SAXException.class, () -> validate(MANIFEST.formatted("RENDERED_JPEG")));
  }

  private static void validate(String manifest) throws Exception {
    SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
    Schema schema = factory.newSchema(ManifestSchemaTest.class.getResource("/config/manifest.xsd"));
    schema
        .newValidator()
        .validate(
            new StreamSource(new ByteArrayInputStream(manifest.getBytes(StandardCharsets.UTF_8))));
  }
}
