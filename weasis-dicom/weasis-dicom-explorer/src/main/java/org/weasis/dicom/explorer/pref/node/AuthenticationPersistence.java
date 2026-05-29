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
import org.weasis.core.api.gui.util.AppProperties;
import org.weasis.core.api.net.auth.AuthMethod;
import org.weasis.core.api.net.auth.AuthProvider;
import org.weasis.core.api.net.auth.AuthRegistration;
import org.weasis.core.api.net.auth.DefaultAuthMethod;
import org.weasis.core.api.net.auth.OAuth2ServiceFactory;
import org.weasis.core.api.service.BundlePreferences;
import org.weasis.core.api.util.ResourceUtil;
import org.weasis.core.util.LangUtil;
import org.weasis.core.util.StreamUtil;
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
  private static final String T_AUDIENCE = "audience"; // NON-NLS
  private static final String T_GRANT_TYPE = "grantType"; // NON-NLS

  private static final Map<String, AuthMethod> methods = new HashMap<>();

  private AuthenticationPersistence() {}

  public static AuthMethod getAuthMethod(String serviceId) {
    if (methods.isEmpty()) {
      loadMethods();
    }
    return methods.getOrDefault(serviceId, OAuth2ServiceFactory.NO_AUTH);
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
      loadMethods(list, BundlePreferences.getFileInDataFolder(context, FILENAME).toFile(), true);
      for (AuthMethod m : list) {
        methods.put(m.getUid(), m);
      }
      return list;
    }
    return methods.values();
  }

  public static void loadMethods(JComboBox<AuthMethod> comboBox) {
    comboBox.addItem(OAuth2ServiceFactory.NO_AUTH);
    Collection<AuthMethod> list = loadMethods();
    for (AuthMethod node : list) {
      comboBox.addItem(node);
    }
  }

  public static void addOrUpdateMethod(AuthMethod method) {
    if (method == null || !StringUtil.hasText(method.getUid())) {
      return;
    }
    if (methods.isEmpty()) {
      loadMethods();
    }
    methods.put(method.getUid(), method);
    saveMethod();
  }

  public static void removeMethod(AuthMethod method) {
    if (method == null || !StringUtil.hasText(method.getUid())) {
      return;
    }
    if (methods.isEmpty()) {
      loadMethods();
    }
    if (methods.remove(method.getUid()) != null) {
      saveMethod();
    }
  }

  public static void saveMethod() {
    XMLStreamWriter writer = null;
    XMLOutputFactory factory = XMLOutputFactory.newInstance();
    final BundleContext context = AppProperties.getBundleContext(AbstractDicomNode.class);
    try {
      writer =
          factory.createXMLStreamWriter(
              new FileOutputStream(
                  BundlePreferences.getFileInDataFolder(context, FILENAME).toFile()),
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
          writeNode(writer, T_NAME, p.name());
          writeNode(writer, T_AUTH_URI, p.authorizationUri());
          writeNode(writer, T_TOKEN_URI, p.tokenUri());
          writeNode(writer, T_REVOKE_URI, p.revokeTokenUri());
          writer.writeAttribute(T_OPENID, Boolean.toString(p.openId()));
          writer.writeEndElement();

          AuthRegistration reg = node.getAuthRegistration();
          writer.writeStartElement(T_REGISTRATION);
          writeNode(writer, T_CLIENT_ID, reg.clientId());
          writeNode(writer, T_CLIENT_SECRET, reg.clientSecret());
          writeNode(writer, T_SCOPE, reg.scope());
          writeNode(writer, T_AUDIENCE, reg.audience());
          writeNode(writer, T_GRANT_TYPE, reg.getAuthorizationGrantType());
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
      StreamUtil.safeClose(writer);
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
        StreamUtil.safeClose(xmler);
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

        AuthProviderBuilder providerBuilder = new AuthProviderBuilder();
        AuthRegistrationBuilder registrationBuilder = new AuthRegistrationBuilder();
        while (xmler.hasNext()) {
          int eventType = xmler.next();
          if (eventType == XMLStreamConstants.START_ELEMENT) {
            readSubElements(xmler, providerBuilder, registrationBuilder);
          } else if (eventType == XMLStreamConstants.END_ELEMENT
              && T_REGISTRATION.equals(xmler.getName().getLocalPart())) {
            break;
          }
        }
        // Create immutable records from collected values
        AuthProvider provider = providerBuilder.build();
        AuthRegistration registration = registrationBuilder.build();

        DefaultAuthMethod node = new DefaultAuthMethod(uid, provider, registration);
        node.setCode(code);
        node.setLocal(local);
        list.add(node);
      } catch (Exception e) {
        LOGGER.error("Cannot read DicomNode", e);
      }
    }
  }

  private static void readSubElements(
      XMLStreamReader xmler,
      AuthProviderBuilder providerBuilder,
      AuthRegistrationBuilder registrationBuilder) {
    String key = xmler.getName().getLocalPart();
    if (T_PROVIDER.equals(key)) {
      providerBuilder.name = xmler.getAttributeValue(null, T_NAME);
      providerBuilder.authorizationUri = xmler.getAttributeValue(null, T_AUTH_URI);
      providerBuilder.tokenUri = xmler.getAttributeValue(null, T_TOKEN_URI);
      providerBuilder.revokeTokenUri = xmler.getAttributeValue(null, T_REVOKE_URI);
      providerBuilder.openId = LangUtil.emptyToFalse(xmler.getAttributeValue(null, T_OPENID));
    } else if (T_REGISTRATION.equals(key)) {
      registrationBuilder.clientId = xmler.getAttributeValue(null, T_CLIENT_ID);
      registrationBuilder.clientSecret = xmler.getAttributeValue(null, T_CLIENT_SECRET);
      registrationBuilder.scope = xmler.getAttributeValue(null, T_SCOPE);
      registrationBuilder.audience = xmler.getAttributeValue(null, T_AUDIENCE);
      registrationBuilder.grantType = xmler.getAttributeValue(null, T_GRANT_TYPE);
    }
  }

  // Helper builder classes for collecting values
  private static class AuthProviderBuilder {
    String name;
    String authorizationUri;
    String tokenUri;
    String revokeTokenUri;
    boolean openId;

    AuthProvider build() {
      return new AuthProvider(name, authorizationUri, tokenUri, revokeTokenUri, openId);
    }
  }

  private static class AuthRegistrationBuilder {
    String clientId;
    String clientSecret;
    String scope;
    String audience;
    String grantType;

    AuthRegistration build() {
      return new AuthRegistration(clientId, clientSecret, scope, audience, null, grantType);
    }
  }
}
