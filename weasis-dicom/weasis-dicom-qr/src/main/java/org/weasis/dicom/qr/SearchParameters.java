/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.qr;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.swing.JComboBox;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;
import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.service.BundlePreferences;
import org.weasis.core.util.FileUtil;
import org.weasis.core.util.StringUtil;
import org.weasis.dicom.param.DicomParam;
import org.weasis.dicom.qr.DicomQrView.Period;

public class SearchParameters {
  private static final Logger LOGGER = LoggerFactory.getLogger(SearchParameters.class);

  static final String FILENAME = "searchParameters.xml";

  static final String T_NODES = "searchParametersList"; // NON-NLS
  static final String T_NODE = "searchParameters"; // NON-NLS

  static final String T_NAME = "name"; // NON-NLS
  static final String T_PERIOD = "period"; // NON-NLS

  static final String T_PARAMS = "dicomParams"; // NON-NLS
  static final String T_PARAM = "dicomParam"; // NON-NLS

  static final String T_TAG = "tag"; // NON-NLS
  static final String T_VALUES = "values"; // NON-NLS
  static final String T_VALUE = "value"; // NON-NLS
  static final String T_PARENT_SEQ = "parentSeqTags"; // NON-NLS

  private String name;
  private Period period;
  private final ArrayList<DicomParam> parameters = new ArrayList<>();

  public SearchParameters(String name) {
    setName(name);
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    if (StringUtil.hasText(name)) {
      this.name = name;
    }
  }

  public List<DicomParam> getParameters() {
    return parameters;
  }

  public Period getPeriod() {
    return period;
  }

  public void setPeriod(Period period) {
    this.period = period;
  }

  @Override
  public String toString() {
    return name;
  }

  public void saveSearchParameters(XMLStreamWriter writer) throws XMLStreamException {
    writer.writeAttribute(T_NAME, name);
    writer.writeAttribute(T_PERIOD, StringUtil.getEmptyStringIfNullEnum(period));
    writer.writeStartElement(T_PARAMS);
    for (DicomParam p : parameters) {
      writer.writeStartElement(T_PARAM);
      writer.writeAttribute(T_TAG, String.valueOf(p.getTag()));
      if (p.getParentSeqTags() != null) {
        writer.writeAttribute(
            T_PARENT_SEQ,
            String.join(
                ",",
                Arrays.stream(p.getParentSeqTags())
                    .mapToObj(String::valueOf)
                    .toArray(String[]::new)));
      }

      if (p.getValues() != null) {
        writer.writeStartElement(T_VALUES);
        for (String val : p.getValues()) {
          writer.writeStartElement(T_VALUE);
          writer.writeCharacters(val);
          writer.writeEndElement();
        }
        writer.writeEndElement();
      }
      writer.writeEndElement();
    }
    writer.writeEndElement();
  }

  public static void loadSearchParameters(JComboBox<SearchParameters> comboBox) {
    List<SearchParameters> list = loadSearchParameters();
    for (SearchParameters node : list) {
      comboBox.addItem(node);
    }
  }

  public static List<SearchParameters> loadSearchParameters() {
    List<SearchParameters> list = new ArrayList<>();

    final BundleContext context = FrameworkUtil.getBundle(DicomQrView.class).getBundleContext();
    File file = new File(BundlePreferences.getDataFolder(context), FILENAME);
    if (file.canRead()) {
      XMLStreamReader xmler = null;
      try {
        XMLInputFactory factory = XMLInputFactory.newInstance();
        // disable external entities for security
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, Boolean.FALSE);
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, Boolean.FALSE);
        xmler = factory.createXMLStreamReader(new FileInputStream(file));
        int eventType;
        while (xmler.hasNext()) {
          eventType = xmler.next();
          if (eventType == XMLStreamConstants.START_ELEMENT) {
            String key = xmler.getName().getLocalPart();
            if (T_NODES.equals(key)) {
              while (xmler.hasNext()) {
                eventType = xmler.next();
                if (eventType == XMLStreamConstants.START_ELEMENT) {
                  readSearchParameters(xmler, list);
                }
              }
            }
          }
        }
      } catch (Exception e) {
        LOGGER.error("Error on reading DICOM node file", e);
      } finally {
        FileUtil.safeClose(xmler);
      }
    }
    return list;
  }

  private static void readSearchParameters(XMLStreamReader xmler, List<SearchParameters> list) {
    if (T_NODE.equals(xmler.getName().getLocalPart())) {
      try {
        SearchParameters node = new SearchParameters(xmler.getAttributeValue(null, T_NAME));
        node.setPeriod(Period.getPeriod(xmler.getAttributeValue(null, T_PERIOD)));
        boolean stateNode = true;
        while (xmler.hasNext() && stateNode) {
          int eventType = xmler.next();
          if (eventType == XMLStreamConstants.START_ELEMENT) {
            if (T_PARAMS.equals(xmler.getName().getLocalPart())) {
              boolean stateParams = true;
              while (xmler.hasNext() && stateParams) {
                eventType = xmler.next();
                if (eventType == XMLStreamConstants.START_ELEMENT) {
                  buildDicomParams(xmler, node);
                } else if (eventType == XMLStreamConstants.END_ELEMENT
                    && T_PARAMS.equals(xmler.getName().getLocalPart())) {
                  stateParams = false;
                }
              }
            }
          } else if (eventType == XMLStreamConstants.END_ELEMENT
              && T_NODE.equals(xmler.getName().getLocalPart())) {
            stateNode = false;
          }
        }
        list.add(node);
      } catch (Exception e) {
        LOGGER.error("Cannot read SearchParameters", e);
      }
    }
  }

  private static void buildDicomParams(XMLStreamReader xmler, SearchParameters node)
      throws XMLStreamException {
    String key = xmler.getName().getLocalPart();
    if (T_PARAM.equals(key)) {
      int tag = StringUtil.getInt(xmler.getAttributeValue(null, T_TAG));
      int[] parentSeq =
          StringUtil.getIntegerArray(xmler.getAttributeValue(null, T_PARENT_SEQ), ",");

      List<String> values = new ArrayList<>();
      boolean state = true;
      while (xmler.hasNext() && state) {
        int eventType = xmler.next();
        if (eventType == XMLStreamConstants.START_ELEMENT) {
          if (T_VALUES.equals(xmler.getName().getLocalPart())) {
            boolean stateValues = true;
            while (xmler.hasNext() && stateValues) {
              eventType = xmler.next();
              if (eventType == XMLStreamConstants.START_ELEMENT) {
                buildDicomParam(xmler, values);
              } else if (eventType == XMLStreamConstants.END_ELEMENT
                  && T_VALUES.equals(xmler.getName().getLocalPart())) { // NON-NLS
                stateValues = false;
              }
            }
          }
          DicomParam param = new DicomParam(parentSeq, tag, values.toArray(new String[0]));
          node.getParameters().add(param);
        } else if (eventType == XMLStreamConstants.END_ELEMENT
            && T_PARAM.equals(xmler.getName().getLocalPart())) { // NON-NLS
          state = false;
        }
      }
    }
  }

  private static void buildDicomParam(XMLStreamReader xmler, List<String> values)
      throws XMLStreamException {
    String key = xmler.getName().getLocalPart();
    if (T_VALUE.equals(key)) {
      values.add(xmler.getElementText());
    }
  }
}
