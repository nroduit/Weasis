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
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.nio.file.Files;
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
import org.weasis.core.ui.model.imp.XmlGraphicModel;

public class XmlSerializer {
  private static final Logger LOGGER = LoggerFactory.getLogger(XmlSerializer.class);

  /**
   * A {@link JAXBContext} is immutable and thread-safe, but building one costs ~185 ms the first
   * time and a few ms afterwards, which every conversion used to pay. {@link ClassValue} keys on
   * the root class itself, so a context is released together with the pf4j class loader that
   * declared it — graphic classes may come from a plugin (e.g. {@code CrossLineGraphic}).
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
   * Builds the graphic model context ahead of the first presentation state, so its one-off cost is
   * not paid on a user action. Safe to call from any thread and more than once.
   */
  public static void preloadContext() {
    try {
      getJaxbContext(XmlGraphicModel.class);
    } catch (JAXBException e) {
      LOGGER.error("Cannot preload the JAXB context", e);
    }
  }

  public static GraphicModel readPresentationModel(File gpxFile) {
    if (gpxFile.canRead()) {
      try (InputStream in = Files.newInputStream(gpxFile.toPath())) {
        return getGraphicModel(unmarshal(in, XmlGraphicModel.class));
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

      try (OutputStream out = new BufferedOutputStream(Files.newOutputStream(gpxFile.toPath()))) {
        Marshaller jaxbMarshaller = getJaxbContext(model.getClass()).createMarshaller();

        // output pretty printed
        jaxbMarshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
        jaxbMarshaller.setProperty(Marshaller.JAXB_ENCODING, "UTF-8");

        jaxbMarshaller.marshal(model, out);
      } catch (Exception e) {
        LOGGER.error("Cannot save xml: ", e);
      }
    }
  }

  public static <T> T deserialize(Reader reader, Class<T> clazz) throws JAXBException {
    XMLStreamReader streamReader = null;
    try {
      streamReader = createStreamReader(reader);
      return clazz.cast(getJaxbContext(clazz).createUnmarshaller().unmarshal(streamReader));
    } catch (XMLStreamException e) {
      throw new JAXBException(e);
    } finally {
      close(streamReader);
    }
  }

  public static GraphicModel buildPresentationModel(byte[] gzipData) {
    // Inflates as it parses instead of materializing the whole XML in an intermediate byte[].
    try (InputStream in = new GZIPInputStream(new ByteArrayInputStream(gzipData))) {
      return getGraphicModel(unmarshal(in, XmlGraphicModel.class));
    } catch (Exception e) {
      LOGGER.error("Cannot load xml graphic model: ", e);
    }
    return null;
  }

  private static <T> T unmarshal(InputStream in, Class<T> clazz)
      throws JAXBException, XMLStreamException {
    XMLStreamReader streamReader = null;
    try {
      streamReader = createStreamReader(in);
      Unmarshaller unmarshaller = getJaxbContext(clazz).createUnmarshaller();
      return clazz.cast(unmarshaller.unmarshal(streamReader));
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
