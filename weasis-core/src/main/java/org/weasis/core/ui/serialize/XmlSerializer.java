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
import java.io.InputStream;
import java.io.Reader;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import org.glassfish.jaxb.runtime.v2.ContextFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.api.media.data.TagW;
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

  /**
   * A {@link JAXBContext} is immutable and thread-safe, but building one costs ~185 ms the first
   * time and a few ms afterwards, which every conversion used to pay. {@link ClassValue} keys on
   * the root class itself, so a context is released together with the pf4j class loader that
   * declared it.
   */
  private static final ClassValue<JAXBContext> CONTEXTS =
      new ClassValue<>() {
        @Override
        protected JAXBContext computeValue(Class<?> type) {
          try {
            return ContextFactory.createContext(new Class<?>[] {type}, null);
          } catch (JAXBException e) {
            throw new IllegalStateException(e);
          }
        }
      };

  /** Shared so the StAX factory lookup is not repeated on every conversion. */
  private static final XMLInputFactory XML_INPUT_FACTORY = createXmlInputFactory();

  /**
   * Context of the presentation model and of every tool type of the registry. Dropped when the
   * registry changes (a plugin started or stopped) and rebuilt on the next use.
   */
  private static volatile JAXBContext presentationContext;

  static {
    GraphicRegistry.getInstance().addListener(() -> presentationContext = null);
  }

  private XmlSerializer() {}

  private static XMLInputFactory createXmlInputFactory() {
    XMLInputFactory factory = XMLInputFactory.newInstance();
    factory.setProperty(XMLInputFactory.SUPPORT_DTD, Boolean.FALSE);
    factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, Boolean.FALSE);
    factory.setProperty(XMLInputFactory.IS_COALESCING, Boolean.TRUE);
    return factory;
  }

  /** Only the readers an {@link XMLInputFactory} hands out are specified as thread-safe. */
  private static XMLStreamReader createStreamReader(InputStream in) throws XMLStreamException {
    synchronized (XML_INPUT_FACTORY) {
      return XML_INPUT_FACTORY.createXMLStreamReader(in);
    }
  }

  private static XMLStreamReader createStreamReader(Reader in) throws XMLStreamException {
    synchronized (XML_INPUT_FACTORY) {
      return XML_INPUT_FACTORY.createXMLStreamReader(in);
    }
  }

  /**
   * Builds the presentation context ahead of the first presentation state, so its one-off cost is
   * not paid on a user action. Safe to call from any thread and more than once.
   */
  public static void preloadContext() {
    try {
      presentationContext();
    } catch (JAXBException e) {
      LOGGER.error("Cannot preload the JAXB context", e);
    }
  }

  public static GraphicModel readPresentationModel(File gpxFile) {
    return readPresentationModel(gpxFile.toPath());
  }

  public static GraphicModel readPresentationModel(Path gpxFile) {
    if (Files.isReadable(gpxFile)) {
      try (InputStream in = Files.newInputStream(gpxFile)) {
        return getGraphicModel(unmarshalPresentation(createStreamReader(in)));
      } catch (Exception e) {
        LOGGER.error("Cannot load xml: ", e);
      }
    }
    return null;
  }

  public static GraphicModel readPresentationModel(Reader reader) throws JAXBException {
    try {
      return getGraphicModel(unmarshalPresentation(createStreamReader(reader)));
    } catch (XMLStreamException e) {
      throw new JAXBException(e);
    }
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
    marshaller.setProperty(Marshaller.JAXB_ENCODING, "UTF-8"); // NON-NLS
    marshaller.marshal(model, writer);
  }

  public static String toXml(GraphicModel model) throws JAXBException {
    StringWriter writer = new StringWriter();
    Marshaller marshaller = presentationContext().createMarshaller();
    marshaller.marshal(model, writer);
    return writer.toString();
  }

  public static <T> T deserialize(Reader reader, Class<T> clazz) throws JAXBException {
    JAXBContext context =
        GraphicModel.class.isAssignableFrom(clazz) ? presentationContext() : getJaxbContext(clazz);
    XMLStreamReader streamReader = null;
    try {
      streamReader = createStreamReader(reader);
      return clazz.cast(context.createUnmarshaller().unmarshal(streamReader));
    } catch (XMLStreamException e) {
      throw new JAXBException(e);
    } finally {
      close(streamReader);
    }
  }

  public static GraphicModel buildPresentationModel(byte[] gzipData) {
    // Inflates as it parses instead of materializing the whole XML in an intermediate byte[].
    try (InputStream in = new GZIPInputStream(new ByteArrayInputStream(gzipData))) {
      return getGraphicModel(unmarshalPresentation(createStreamReader(in)));
    } catch (IOException | JAXBException | XMLStreamException e) {
      LOGGER.error("Cannot load xml graphic model: ", e);
    }
    return null;
  }

  private static GraphicModel unmarshalPresentation(XMLStreamReader streamReader)
      throws JAXBException {
    try {
      Unmarshaller unmarshaller = presentationContext().createUnmarshaller();
      return (GraphicModel) unmarshaller.unmarshal(streamReader);
    } finally {
      close(streamReader);
    }
  }

  private static void close(XMLStreamReader streamReader) {
    if (streamReader != null) {
      try {
        streamReader.close();
      } catch (XMLStreamException e) {
        LOGGER.debug("Cannot close the xml stream reader", e);
      }
    }
  }

  private static GraphicModel getGraphicModel(GraphicModel model) {
    if (model == null) {
      return null;
    }
    int length = model.getModels().size();
    model.getModels().removeIf(g -> g.getLayer() == null);
    int removed = length - model.getModels().size();
    if (removed > 0) {
      LOGGER.warn("Removing {} graphics without an attached layer", removed);
    }
    return model;
  }

  /** Context of the presentation model and of every registered tool type. */
  public static JAXBContext presentationContext() throws JAXBException {
    JAXBContext context = presentationContext;
    if (context == null) {
      List<Class<?>> types = new ArrayList<>(GraphicRegistry.getInstance().xmlTypes());
      types.addFirst(XmlGraphicModel.class);
      context = ContextFactory.createContext(types.toArray(new Class<?>[0]), null);
      presentationContext = context;
    }
    return context;
  }

  public static JAXBContext getJaxbContext(Class<?>... clazz) throws JAXBException {
    if (clazz.length == 1) {
      try {
        return CONTEXTS.get(clazz[0]);
      } catch (IllegalStateException e) {
        throw e.getCause() instanceof JAXBException jaxbException
            ? jaxbException
            : new JAXBException(e);
      }
    }
    return getJaxbContext(null, clazz);
  }

  public static JAXBContext getJaxbContext(Map<String, Object> properties, Class<?>... clazz)
      throws JAXBException {
    if (properties == null && clazz.length == 1) {
      return getJaxbContext(clazz);
    }
    return ContextFactory.createContext(clazz, properties);
  }
}
