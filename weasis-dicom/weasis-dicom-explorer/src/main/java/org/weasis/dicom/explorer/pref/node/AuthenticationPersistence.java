/*
 * Copyright (c) 2021 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.explorer.pref.node;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.swing.JComboBox;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;
import org.osgi.framework.BundleContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.auth.AuthMethod;
import org.weasis.core.api.auth.AuthProvider;
import org.weasis.core.api.auth.AuthRegistration;
import org.weasis.core.api.auth.DefaultAuthMethod;
import org.weasis.core.api.auth.OAuth2ServiceFactory;
import org.weasis.core.api.gui.util.AppProperties;
import org.weasis.core.api.service.BundlePreferences;
import org.weasis.core.api.util.ResourceUtil;
import org.weasis.core.util.FileUtil;
import org.weasis.core.util.LangUtil;
import org.weasis.core.util.StringUtil;

public class AuthenticationPersistence {
  private static final Logger LOGGER = LoggerFactory.getLogger(AuthenticationPersistence.class);
  private static final String FILENAME = "authenticationNodes.xml";
  private static final String T_NODES = "methods"; // NON-NLS
  private static final String T_NODE = "method"; // NON-NLS
  private static final String T_UID = "uid"; // NON-NLS
  private static final String T_CODE = "code"; // NON-NLS
  private static final String T_REGISTRATION = "registration"; // NON-NLS
  private static final String T_PROVIDER = "provider"; // NON-NLS
  private static final String T_CLIENT_ID = "id"; // NON-NLS
  private static final String T_CLIENT_SECRET = "secret"; // NON-NLS
  private static final String T_SCOPE = "scope"; // NON-NLS
  private static final String T_NAME = "name"; // NON-NLS
  private static final String T_AUTH_URI = "auth"; // NON-NLS
  private static final String T_TOKEN_URI = "token"; // NON-NLS
  private static final String T_REVOKE_URI = "revoke"; // NON-NLS
  private static final String T_OPENID = "openid"; // NON-NLS

  private static final Map<String, AuthMethod> methods = new HashMap<>();

  private AuthenticationPersistence() {}

  public static AuthMethod getAuthMethod(String serviceId) {
    if (methods.isEmpty()) {
      loadMethods();
    }
    return methods.getOrDefault(serviceId, OAuth2ServiceFactory.noAuth);
  }

  public static Map<String, AuthMethod> getMethods() {
    if (methods.isEmpty()) {
      loadMethods();
    }
    return methods;
  }

  public static Collection<AuthMethod> loadMethods() {
    if (methods.isEmpty()) {
      // FIXME, for testing purpose
      // HttpsURLConnection.setDefaultHostnameVerifier((urlHostName, session) -> true);

      List<AuthMethod> list = new ArrayList<>();
      // Load nodes from resources
      loadMethods(list, ResourceUtil.getResource(FILENAME), false);

      // Load nodes from local data
      final BundleContext context = AppProperties.getBundleContext(AbstractDicomNode.class);
      loadMethods(list, new File(BundlePreferences.getDataFolder(context), FILENAME), true);
      for (AuthMethod m : list) {
        methods.put(m.getUid(), m);
      }
      return list;
    }
    return methods.values();
  }

  public static void loadMethods(JComboBox<AuthMethod> comboBox) {
    comboBox.addItem(OAuth2ServiceFactory.noAuth);
    Collection<AuthMethod> list = loadMethods();
    for (AuthMethod node : list) {
      comboBox.addItem(node);
    }
  }

  public static void saveMethod() {
    XMLStreamWriter writer = null;
    XMLOutputFactory factory = XMLOutputFactory.newInstance();
    final BundleContext context = AppProperties.getBundleContext(AbstractDicomNode.class);
    try {
      writer =
          factory.createXMLStreamWriter(
              new FileOutputStream(new File(BundlePreferences.getDataFolder(context), FILENAME)),
              "UTF-8"); // NON-NLS

      writer.writeStartDocument("UTF-8", "1.0"); // NON-NLS
      writer.writeStartElement(T_NODES);
      for (AuthMethod node : methods.values()) {
        if (node.isLocal()) {
          writer.writeStartElement(T_NODE);
          writeNode(writer, T_UID, node.getUid());
          writeNode(writer, T_CODE, node.getCode());

          AuthProvider p = node.getAuthProvider();
          writer.writeStartElement(T_PROVIDER);
          writeNode(writer, T_NAME, p.getName());
          writeNode(writer, T_AUTH_URI, p.getAuthorizationUri());
          writeNode(writer, T_TOKEN_URI, p.getTokenUri());
          writeNode(writer, T_REVOKE_URI, p.getRevokeTokenUri());
          writer.writeAttribute(T_OPENID, Boolean.toString(p.getOpenId()));
          writer.writeEndElement();

          AuthRegistration reg = node.getAuthRegistration();
          writer.writeStartElement(T_REGISTRATION);
          writeNode(writer, T_CLIENT_ID, reg.getClientId());
          writeNode(writer, T_CLIENT_SECRET, reg.getClientSecret());
          writeNode(writer, T_SCOPE, reg.getScope());
          writer.writeEndElement();

          writer.writeEndElement();
        }
      }
      writer.writeEndElement();
      writer.writeEndDocument();
      writer.flush();
    } catch (Exception e) {
      LOGGER.error("Error on writing DICOM node file", e);
    } finally {
      FileUtil.safeClose(writer);
    }
  }

  private static void writeNode(XMLStreamWriter writer, String key, String value)
      throws XMLStreamException {
    writer.writeAttribute(key, StringUtil.hasText(value) ? value : "");
  }

  private static void loadMethods(List<AuthMethod> list, File prefs, boolean local) {
    if (prefs.canRead()) {
      XMLStreamReader xmler = null;
      try {
        XMLInputFactory factory = XMLInputFactory.newInstance();
        // disable external entities for security
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, Boolean.FALSE);
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, Boolean.FALSE);
        xmler = factory.createXMLStreamReader(new FileInputStream(prefs));
        int eventType;
        while (xmler.hasNext()) {
          eventType = xmler.next();
          if (eventType == XMLStreamConstants.START_ELEMENT) {
            readMethods(xmler, list, local);
          }
        }
      } catch (Exception e) {
        LOGGER.error("Error on reading DICOM node file", e);
      } finally {
        FileUtil.safeClose(xmler);
      }
    }
  }

  private static void readMethods(XMLStreamReader xmler, List<AuthMethod> list, boolean local)
      throws XMLStreamException {
    String key = xmler.getName().getLocalPart();
    if (T_NODES.equals(key)) {
      while (xmler.hasNext()) {
        int eventType = xmler.next();
        if (eventType == XMLStreamConstants.START_ELEMENT) {
          readMethod(xmler, list, local);
        }
      }
    }
  }

  private static void readMethod(XMLStreamReader xmler, List<AuthMethod> list, boolean local) {
    String key = xmler.getName().getLocalPart();
    if (T_NODE.equals(key)) {
      try {
        String uid = xmler.getAttributeValue(null, T_UID);
        String code = xmler.getAttributeValue(null, T_CODE);

        AuthProvider p = new AuthProvider(null, null, null, null, false);
        AuthRegistration reg = new AuthRegistration(null, null, null);
        DefaultAuthMethod node = new DefaultAuthMethod(uid, p, reg);
        while (xmler.hasNext()) {
          int eventType = xmler.next();
          if (eventType == XMLStreamConstants.START_ELEMENT) {
            readSubElements(xmler, node);
          } else if (eventType == XMLStreamConstants.END_ELEMENT
              && T_REGISTRATION.equals(xmler.getName().getLocalPart())) {
            break;
          }
        }
        node.setCode(code);
        node.setLocal(local);
        list.add(node);
      } catch (Exception e) {
        LOGGER.error("Cannot read DicomNode", e);
      }
    }
  }

  private static void readSubElements(XMLStreamReader xmler, DefaultAuthMethod node) {
    String key = xmler.getName().getLocalPart();
    if (T_PROVIDER.equals(key)) {
      AuthProvider p = node.getAuthProvider();
      p.setName(xmler.getAttributeValue(null, T_NAME));
      p.setAuthorizationUri(xmler.getAttributeValue(null, T_AUTH_URI));
      p.setTokenUri(xmler.getAttributeValue(null, T_TOKEN_URI));
      p.setRevokeTokenUri(xmler.getAttributeValue(null, T_REVOKE_URI));
      p.setOpenId(LangUtil.getEmptytoFalse(xmler.getAttributeValue(null, T_OPENID)));
    } else if (T_REGISTRATION.equals(key)) {
      AuthRegistration reg = node.getAuthRegistration();
      reg.setClientId(xmler.getAttributeValue(null, T_CLIENT_ID));
      reg.setClientSecret(xmler.getAttributeValue(null, T_CLIENT_SECRET));
      reg.setScope(xmler.getAttributeValue(null, T_SCOPE));
    }
  }
}
