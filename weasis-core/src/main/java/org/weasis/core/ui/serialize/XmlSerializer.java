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
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Marshaller;
import jakarta.xml.bind.Unmarshaller;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.glassfish.jaxb.runtime.v2.ContextFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.api.util.GzipManager;
import org.weasis.core.ui.model.GraphicModel;
import org.weasis.core.ui.model.graphic.GraphicRegistry;
import org.weasis.core.ui.model.imp.XmlGraphicModel;

/**
 * XML binding of the presentation model. The context knows every tool of the {@link
 * GraphicRegistry}, so a plugin tool round-trips as long as its plugin is loaded, and it is rebuilt
 * when the registry changes.
 */
public class XmlSerializer {
  private static final Logger LOGGER = LoggerFactory.getLogger(XmlSerializer.class);

  private static volatile JAXBContext presentationContext;

  static {
    GraphicRegistry.getInstance().addListener(() -> presentationContext = null);
  }

  private XmlSerializer() {}

  public static GraphicModel readPresentationModel(File gpxFile) {
    return readPresentationModel(gpxFile.toPath());
  }

  public static GraphicModel readPresentationModel(Path gpxFile) {
    if (Files.isReadable(gpxFile)) {
      try (Reader reader = Files.newBufferedReader(gpxFile)) {
        return readPresentationModel(reader);
      } catch (Exception e) {
        LOGGER.error("Cannot load xml: ", e);
      }
    }
    return null;
  }

  public static GraphicModel readPresentationModel(Reader reader) throws JAXBException {
    Unmarshaller unmarshaller = presentationContext().createUnmarshaller();
    return getGraphicModel((GraphicModel) unmarshaller.unmarshal(reader));
  }

  public static void writePresentation(ImageElement img, File destinationFile) {
    writePresentation(img, destinationFile.toPath());
  }

  /** Writes the model of the image next to the destination file, as {@code <name>.xml}. */
  public static void writePresentation(ImageElement img, Path destinationFile) {
    GraphicModel model = (GraphicModel) img.getTagValue(TagW.PresentationModel);
    if (model != null && !model.getModels().isEmpty()) {
      Path gpxFile = destinationFile.resolveSibling(destinationFile.getFileName() + ".xml");
      try (Writer writer = Files.newBufferedWriter(gpxFile)) {
        writePresentation(model, writer);
      } catch (Exception e) {
        LOGGER.error("Cannot save xml: ", e);
      }
    }
  }

  public static void writePresentation(GraphicModel model, Writer writer) throws JAXBException {
    Marshaller marshaller = presentationContext().createMarshaller();
    marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
    marshaller.marshal(model, writer);
  }

  public static String toXml(GraphicModel model) throws JAXBException {
    StringWriter writer = new StringWriter();
    Marshaller marshaller = presentationContext().createMarshaller();
    marshaller.marshal(model, writer);
    return writer.toString();
  }

  @SuppressWarnings("unchecked")
  public static <T> T deserialize(Reader reader, Class<T> clazz) throws JAXBException {
    JAXBContext context =
        GraphicModel.class.isAssignableFrom(clazz) ? presentationContext() : getJaxbContext(clazz);
    return (T) context.createUnmarshaller().unmarshal(reader);
  }

  public static GraphicModel buildPresentationModel(byte[] gzipData) {
    try {
      Unmarshaller unmarshaller = presentationContext().createUnmarshaller();
      ByteArrayInputStream inputStream =
          new ByteArrayInputStream(GzipManager.gzipUncompressToByte(gzipData));
      return getGraphicModel((GraphicModel) unmarshaller.unmarshal(inputStream));
    } catch (IOException | JAXBException e) {
      LOGGER.error("Cannot load xml graphic model: ", e);
    }
    return null;
  }

  private static GraphicModel getGraphicModel(GraphicModel model) {
    int length = model.getModels().size();
    model.getModels().removeIf(g -> g.getLayer() == null);
    if (length > model.getModels().size()) {
      LOGGER.error(
          "Removing {} graphics without a attached layer", length - model.getModels().size());
    }
    return model;
  }

  /** Context of the presentation model and of every registered tool type. */
  public static JAXBContext presentationContext() throws JAXBException {
    JAXBContext context = presentationContext;
    if (context == null) {
      List<Class<?>> types = new ArrayList<>(GraphicRegistry.getInstance().xmlTypes());
      types.addFirst(XmlGraphicModel.class);
      context = getJaxbContext(types.toArray(new Class<?>[0]));
      presentationContext = context;
    }
    return context;
  }

  public static JAXBContext getJaxbContext(Class<?>... clazz) throws JAXBException {
    return getJaxbContext(null, clazz);
  }

  public static JAXBContext getJaxbContext(Map<String, Object> properties, Class<?>... clazz)
      throws JAXBException {
    return ContextFactory.createContext(clazz, properties);
  }
}
