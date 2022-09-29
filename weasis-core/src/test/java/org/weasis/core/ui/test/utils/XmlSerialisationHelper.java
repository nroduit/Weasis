/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.test.utils;

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Marshaller;
import jakarta.xml.bind.Unmarshaller;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import org.weasis.core.ui.model.graphic.Graphic;
import org.weasis.core.ui.model.layer.GraphicLayer;
import org.weasis.core.ui.serialize.XmlSerializer;

public class XmlSerialisationHelper implements XmlTemplate, UuidTemplate {
  protected JAXBContext context;
  protected Marshaller marshaller;
  protected Unmarshaller unmarshaller;

  protected GraphicLayer l1, l2;
  protected Graphic g1, g2, g3, g4;

  protected void consoleDisplay(Object object) throws JAXBException {
    context = XmlSerializer.getJaxbContext(object.getClass());
    marshaller = context.createMarshaller();

    // output pretty printed
    marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
    marshaller.marshal(object, System.out);
  }

  protected String serialize(Object object) throws JAXBException {
    StringWriter sw = new StringWriter();
    context = XmlSerializer.getJaxbContext(object.getClass());
    marshaller = context.createMarshaller();

    marshaller.marshal(object, sw);
    return sw.toString();
  }

  protected String serializeWithoutHeader(Object object) throws JAXBException {
    String result = serialize(object);
    return result.substring(TPL_XML_PREFIX.length());
  }

  protected <T> T deserialize(String input, Class<T> clazz) throws Exception {
    StringReader sr = new StringReader(input);
    return XmlSerializer.deserialize(sr, clazz);
  }

  protected <T> T deserialize(InputStream xmlInput, Class<T> clazz) throws Exception {
    Reader reader = new InputStreamReader(xmlInput, StandardCharsets.UTF_8); // NON-NLS
    return XmlSerializer.deserialize(reader, clazz);
  }
}
