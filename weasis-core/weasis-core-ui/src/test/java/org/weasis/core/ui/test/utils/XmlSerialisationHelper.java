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

public class XmlSerialisationHelper implements XmlTemplate, UuidTemplate {
    protected JAXBContext context;
    protected Marshaller marshaller;
    protected Unmarshaller unmarshaller;

    protected GraphicLayer l1, l2;
    protected Graphic g1, g2, g3, g4;

    protected void consoleDisplay(Object object) throws JAXBException {
        context = JAXBContext.newInstance(object.getClass());
        marshaller = context.createMarshaller();

        // output pretty printed
        marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
        marshaller.marshal(object, System.out);
    }

    protected String serialize(Object object) throws JAXBException {
        StringWriter sw = new StringWriter();
        context = JAXBContext.newInstance(object.getClass());
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
        return deserialize(sr, clazz);
    }
    
    protected <T> T deserialize(InputStream xmlInput, Class<T> clazz) throws Exception {
        Reader reader = new InputStreamReader(xmlInput, "UTF-8");     //$NON-NLS-1$
        return deserialize(reader, clazz);
    }
    
    @SuppressWarnings("unchecked")
    private <T> T deserialize(Reader reader, Class<T> clazz) throws Exception {
        context = JAXBContext.newInstance(clazz);
        unmarshaller = context.createUnmarshaller();

        return (T) unmarshaller.unmarshal(reader);
    } 
}
