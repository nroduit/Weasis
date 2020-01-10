/*******************************************************************************
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.weasis.core.ui.test.utils;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;

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
        context =  XmlSerializer.getJaxbContext(object.getClass());
        marshaller = context.createMarshaller();

        // output pretty printed
        marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
        marshaller.marshal(object, System.out);
    }

    protected String serialize(Object object) throws JAXBException {
        StringWriter sw = new StringWriter();
        context =  XmlSerializer.getJaxbContext(object.getClass());
        marshaller = context.createMarshaller();

        marshaller.marshal(object, sw);
        return sw.toString();
    }

    protected String serializeWithoutHeader(Object object) throws JAXBException {
        String result = serialize(object);
        String result2 = result.substring(TPL_XML_PREFIX.length());
        return result2;
    }

    protected <T> T deserialize(String input, Class<T> clazz) throws Exception {
        StringReader sr = new StringReader(input);
        return XmlSerializer.deserialize(sr, clazz);
    }

    protected <T> T deserialize(InputStream xmlInput, Class<T> clazz) throws Exception {
        Reader reader = new InputStreamReader(xmlInput, "UTF-8"); //$NON-NLS-1$
        return XmlSerializer.deserialize(reader, clazz);
    }
}
