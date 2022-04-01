/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.serialize;

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBElement;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Marshaller;
import jakarta.xml.bind.Unmarshaller;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.Reader;
import java.io.Writer;
import java.util.Map;
import javax.xml.stream.XMLStreamReader;
import org.glassfish.jaxb.runtime.v2.ContextFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.api.util.GzipManager;
import org.weasis.core.ui.model.GraphicModel;
import org.weasis.core.ui.model.imp.XmlGraphicModel;

public class XmlSerializer {
  private static final Logger LOGGER = LoggerFactory.getLogger(XmlSerializer.class);

  private XmlSerializer() {}

  public static GraphicModel readPresentationModel(File gpxFile) {
    if (gpxFile.canRead()) {
      try {
        JAXBContext jaxbContext = getJaxbContext(XmlGraphicModel.class);
        Unmarshaller jaxbUnmarshaller = jaxbContext.createUnmarshaller();
        GraphicModel model = (GraphicModel) jaxbUnmarshaller.unmarshal(gpxFile);
        int length = model.getModels().size();
        model.getModels().removeIf(g -> g.getLayer() == null);
        if (length > model.getModels().size()) {
          LOGGER.error(
              "Removing {} graphics without a attached layer", model.getModels().size() - length);
        }
        return model;
      } catch (Exception e) {
        LOGGER.error("Cannot load xml: ", e);
      }
    }
    return null;
  }

  public static void writePresentation(ImageElement img, File destinationFile) {
    GraphicModel model = (GraphicModel) img.getTagValue(TagW.PresentationModel);
    if (model != null && !model.getModels().isEmpty()) {
      File gpxFile = new File(destinationFile.getParent(), destinationFile.getName() + ".xml");

      try {
        JAXBContext jaxbContext = getJaxbContext(model.getClass());
        Marshaller jaxbMarshaller = jaxbContext.createMarshaller();

        // output pretty printed
        jaxbMarshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);

        // jaxbMarshaller.marshal(model, System.out);
        jaxbMarshaller.marshal(model, gpxFile);
      } catch (Exception e) {
        LOGGER.error("Cannot save xml: ", e);
      }
    }
  }

  public static void writePresentation(ImageElement img, Writer writer) {
    GraphicModel model = (GraphicModel) img.getTagValue(TagW.PresentationModel);
    writePresentation(model, writer);
  }

  public static void writePresentation(GraphicModel model, Writer writer) {
    if (model != null && model.hasSerializableGraphics()) {
      try {
        JAXBContext jaxbContext = getJaxbContext(model.getClass());
        Marshaller jaxbMarshaller = jaxbContext.createMarshaller();
        // Remove the xml header tag
        jaxbMarshaller.setProperty(Marshaller.JAXB_FRAGMENT, Boolean.TRUE);
        jaxbMarshaller.marshal(model, writer);
      } catch (Exception e) {
        LOGGER.error("Cannot write GraphicModel", e);
      }
    }
  }

  public static GraphicModel readPresentation(XMLStreamReader xmler) {
    try {
      JAXBContext jaxbContext = getJaxbContext(XmlGraphicModel.class);
      Unmarshaller jaxbUnmarshaller = jaxbContext.createUnmarshaller();
      JAXBElement<XmlGraphicModel> unmarshalledObj =
          jaxbUnmarshaller.unmarshal(
              new NoNamespaceStreamReaderDelegate(xmler), XmlGraphicModel.class);
      GraphicModel model = unmarshalledObj.getValue();

      int length = model.getModels().size();
      model.getModels().removeIf(g -> g.getLayer() == null);
      if (length > model.getModels().size()) {
        LOGGER.error(
            "Removing {} graphics without a attached layer", model.getModels().size() - length);
      }
      return model;
    } catch (Exception e) {
      LOGGER.error("Cannot write GraphicModel", e);
    }
    return null;
  }

  @SuppressWarnings("unchecked")
  public static <T> T deserialize(Reader reader, Class<T> clazz) throws JAXBException {
    JAXBContext context = getJaxbContext(clazz);
    Unmarshaller unmarshaller = context.createUnmarshaller();

    return (T) unmarshaller.unmarshal(reader);
  }

  public static GraphicModel buildPresentationModel(byte[] gzipData) {
    try {
      JAXBContext jaxbContext = getJaxbContext(XmlGraphicModel.class);
      Unmarshaller jaxbUnmarshaller = jaxbContext.createUnmarshaller();
      ByteArrayInputStream inputStream =
          new ByteArrayInputStream(GzipManager.gzipUncompressToByte(gzipData));
      GraphicModel model = (GraphicModel) jaxbUnmarshaller.unmarshal(inputStream);
      int length = model.getModels().size();
      model.getModels().removeIf(g -> g.getLayer() == null);
      if (length > model.getModels().size()) {
        LOGGER.error(
            "Removing {} graphics without a attached layer", model.getModels().size() - length);
      }
      return model;
    } catch (Exception e) {
      LOGGER.error("Cannot load xml graphic model: ", e);
    }
    return null;
  }

  public static JAXBContext getJaxbContext(Class<?>... clazz) throws JAXBException {
    return ContextFactory.createContext(clazz, null);
  }

  public static JAXBContext getJaxbContext(Map<String, Object> properties, Class<?>... clazz)
      throws JAXBException {
    return ContextFactory.createContext(clazz, properties);
  }
}
