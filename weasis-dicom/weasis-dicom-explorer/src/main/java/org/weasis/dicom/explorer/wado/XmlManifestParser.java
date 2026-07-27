/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.explorer.wado;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import javax.xml.XMLConstants;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.transform.stax.StAXSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.media.data.MediaSeriesGroup;
import org.weasis.core.api.media.data.XmlAttributeSource;
import org.weasis.core.api.util.BiConsumerWithException;
import org.weasis.core.util.StringUtil;
import org.weasis.dicom.codec.DicomSeries;
import org.weasis.dicom.codec.TagD.Level;
import org.weasis.dicom.codec.utils.SeriesInstanceList;
import org.weasis.dicom.mf.ArcParameters;
import org.weasis.dicom.mf.WadoParameters;
import org.xml.sax.SAXException;

/**
 * Parses the legacy and modern (2.5) XML manifest with a StAX reader. Preferred over JSON for large
 * per-instance manifests, which it reads by streaming rather than buffering the whole document.
 */
final class XmlManifestParser {
  private static final Logger LOGGER = LoggerFactory.getLogger(XmlManifestParser.class);

  /**
   * XSD validation is diagnostic-only (it never rejects the manifest) and requires a second full
   * pass over the document, so it is only performed when explicitly enabled.
   */
  private static final boolean XSD_VALIDATION =
      Boolean.getBoolean("weasis.manifest.xsd.validation");

  // Lazily compiled and cached: schema compilation is expensive and the schema is immutable.
  private static volatile Schema manifestSchema;

  private XmlManifestParser() {}

  static void parse(Path xmlFile, ReaderParams params) throws XMLStreamException {
    XMLInputFactory factory = XMLInputFactory.newInstance();
    // disable external entities for security
    factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, Boolean.FALSE);
    factory.setProperty(XMLInputFactory.SUPPORT_DTD, Boolean.FALSE);

    if (XSD_VALIDATION) {
      validate(xmlFile, factory);
    }

    // Read the manifest in a single pass, tolerating a document that does not match the schema.
    try (InputStream in = new BufferedInputStream(Files.newInputStream(xmlFile))) {
      XMLStreamReader xmler = factory.createXMLStreamReader(in);
      try {
        readManifest(xmler, params);
      } finally {
        xmler.close();
      }
    } catch (IOException e) {
      throw new XMLStreamException(e);
    }
  }

  private static void validate(Path xmlFile, XMLInputFactory factory) {
    try (InputStream in = new BufferedInputStream(Files.newInputStream(xmlFile))) {
      XMLStreamReader xmler = factory.createXMLStreamReader(in);
      Validator validator = manifestSchema().newValidator();
      validator.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, StringUtil.EMPTY_STRING);
      validator.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, StringUtil.EMPTY_STRING);
      validator.validate(new StAXSource(xmler));
      xmler.close();
      LOGGER.info("[Validate with XSD schema] the manifest is valid");
    } catch (SAXException e) {
      LOGGER.error("[Validate with XSD schema] the manifest is NOT valid", e);
    } catch (Exception e) {
      LOGGER.error("Error when validate XSD schema.", e);
    }
  }

  private static Schema manifestSchema() throws SAXException {
    Schema schema = manifestSchema;
    if (schema == null) {
      SchemaFactory schemaFactory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
      schema = schemaFactory.newSchema(XmlManifestParser.class.getResource("/config/manifest.xsd"));
      manifestSchema = schema;
    }
    return schema;
  }

  private static void readManifest(XMLStreamReader xmler, ReaderParams params)
      throws XMLStreamException {
    // xmlns="http://www.weasis.org/xsd/2.5"
    BiConsumerWithException<XMLStreamReader, ReaderParams, XMLStreamException> method =
        (x, r) -> {
          if (ArcParameters.TAG_DOCUMENT_ROOT.equals(x.getName().getLocalPart())) {
            BiConsumerWithException<XMLStreamReader, ReaderParams, XMLStreamException> method2 =
                (x2, r2) -> {
                  if (ArcParameters.TAG_ARC_QUERY.equals(x2.getName().getLocalPart())) {
                    readArcQuery(x2, r2);
                  }
                };
            readElement(x, ArcParameters.TAG_DOCUMENT_ROOT, method2, r);
          }
        };
    readElement(xmler, ArcParameters.TAG_DOCUMENT_ROOT, method, params);
  }

  private static void readArcQuery(XMLStreamReader xmler, ReaderParams params)
      throws XMLStreamException {
    WadoParameters wadoParameters =
        ManifestModelBuilder.buildArcQueryParameters(new XmlAttributeSource(xmler), params);
    Set<MediaSeriesGroup> patients = new LinkedHashSet<>();

    BiConsumerWithException<XMLStreamReader, ReaderParams, XMLStreamException> method =
        (x, r) -> {
          String key = x.getName().getLocalPart();
          if (Level.PATIENT.getTagName().equals(key)) {
            patients.add(readPatient(x, params, wadoParameters));
          } else if (ArcParameters.TAG_HTTP_TAG.equals(key)) {
            ManifestModelBuilder.addHttpTag(new XmlAttributeSource(x), wadoParameters);
          } else if ("Message".equals(key)) { // NON-NLS
            ManifestModelBuilder.showViewerMessage(new XmlAttributeSource(x));
          }
        };

    readElement(xmler, ArcParameters.TAG_ARC_QUERY, method, params);

    ManifestModelBuilder.focusUniquePatient(patients);
    ManifestModelBuilder.startDownloads(params, wadoParameters);
  }

  private static MediaSeriesGroup readPatient(
      XMLStreamReader xmler, ReaderParams params, WadoParameters wadoParameters)
      throws XMLStreamException {
    MediaSeriesGroup patient =
        ManifestModelBuilder.buildPatient(new XmlAttributeSource(xmler), params, wadoParameters);

    BiConsumerWithException<XMLStreamReader, ReaderParams, XMLStreamException> method =
        (x, r) -> readStudy(x, params, patient, wadoParameters);
    readElement(xmler, Level.STUDY.getTagName(), Level.PATIENT.getTagName(), method, params);

    // Patient-level (partial) DICOMweb manifest: query the missing studies.
    if (ManifestCompletion.isPartialLevel(
        wadoParameters, !params.getModel().getChildren(patient).isEmpty())) {
      ManifestCompletion.completePatient(params, wadoParameters, patient);
    }
    return patient;
  }

  private static void readStudy(
      XMLStreamReader xmler,
      ReaderParams params,
      MediaSeriesGroup patient,
      WadoParameters wadoParameters)
      throws XMLStreamException {
    MediaSeriesGroup study =
        ManifestModelBuilder.buildStudy(new XmlAttributeSource(xmler), params, patient);

    BiConsumerWithException<XMLStreamReader, ReaderParams, XMLStreamException> method =
        (x, r) -> readSeries(x, params, patient, study, wadoParameters);
    readElement(xmler, Level.SERIES.getTagName(), Level.STUDY.getTagName(), method, params);

    // Study-level (partial) DICOMweb manifest: query the missing series.
    if (ManifestCompletion.isPartialLevel(
        wadoParameters, !params.getModel().getChildren(study).isEmpty())) {
      ManifestCompletion.completeStudy(params, wadoParameters, patient, study);
    }
  }

  private static void readSeries(
      XMLStreamReader xmler,
      ReaderParams params,
      MediaSeriesGroup patient,
      MediaSeriesGroup study,
      WadoParameters wadoParameters)
      throws XMLStreamException {
    DicomSeries dicomSeries =
        ManifestModelBuilder.buildSeries(
            new XmlAttributeSource(xmler), params, study, wadoParameters);
    if (dicomSeries == null) {
      return;
    }

    SeriesInstanceList seriesInstanceList = ManifestModelBuilder.getSeriesInstanceList(dicomSeries);
    BiConsumerWithException<XMLStreamReader, ReaderParams, XMLStreamException> method =
        (x, r) ->
            ManifestModelBuilder.addSopInstance(new XmlAttributeSource(x), seriesInstanceList);
    readElement(xmler, Level.INSTANCE.getTagName(), Level.SERIES.getTagName(), method, params);

    ManifestModelBuilder.completeSeries(params, patient, study, dicomSeries, seriesInstanceList);
  }

  private static void readElement(
      XMLStreamReader xmler,
      String endElement,
      BiConsumerWithException<XMLStreamReader, ReaderParams, XMLStreamException> method,
      ReaderParams params)
      throws XMLStreamException {
    boolean state = true;
    while (xmler.hasNext() && state) {
      int eventType = xmler.next();
      switch (eventType) {
        case XMLStreamConstants.START_ELEMENT -> method.accept(xmler, params);
        case XMLStreamConstants.END_ELEMENT -> {
          if (endElement.equals(xmler.getName().getLocalPart())) {
            state = false;
          }
        }
        default -> {
          // Ignore other events
        }
      }
    }
  }

  private static void readElement(
      XMLStreamReader xmler,
      String startElement,
      String endElement,
      BiConsumerWithException<XMLStreamReader, ReaderParams, XMLStreamException> method,
      ReaderParams params)
      throws XMLStreamException {
    boolean state = true;
    while (xmler.hasNext() && state) {
      int eventType = xmler.next();
      switch (eventType) {
        case XMLStreamConstants.START_ELEMENT -> {
          if (startElement.equals(xmler.getName().getLocalPart())) {
            method.accept(xmler, params);
          }
        }
        case XMLStreamConstants.END_ELEMENT -> {
          if (endElement.equals(xmler.getName().getLocalPart())) {
            state = false;
          }
        }
        default -> {
          // Ignore other events
        }
      }
    }
  }
}
