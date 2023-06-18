/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.explorer.pref.node;

import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;
import org.weasis.core.api.auth.AuthMethod;
import org.weasis.core.api.auth.OAuth2ServiceFactory;
import org.weasis.core.util.StringUtil;
import org.weasis.dicom.codec.TransferSyntax;

public class DicomWebNode extends AbstractDicomNode {

  public static final String T_URL = "url"; // NON-NLS
  public static final String T_WEB_TYPE = "webtype"; // NON-NLS
  public static final String T_HEADER = "headers"; // NON-NLS
  public static final String T_AUTH = "auth"; // NON-NLS

  public enum WebType {
    DICOMWEB("DICOMWeb (all RESTful services)"), // NON-NLS
    QIDORS("QIDO-RS (query)"), // NON-NLS
    STOWRS("STOW-RS (store)"), // NON-NLS
    WADO("WADO-URI (non-RS)"), // NON-NLS
    WADORS("WADO-RS (retrieve)"); // NON-NLS

    final String title;

    WebType(String title) {
      this.title = title;
    }

    @Override
    public String toString() {
      return title;
    }
  }

  private URL url;
  private WebType webType;
  private final Map<String, String> headers;
  private String authMethodUid;

  public DicomWebNode(String description, WebType webType, URL url, UsageType usageType) {
    super(description, Type.WEB, usageType);
    this.url = url;
    this.webType = webType;
    this.headers = new HashMap<>();
    this.authMethodUid = OAuth2ServiceFactory.NO;
  }

  @Override
  public String getToolTips() {
    return """
    <html>
      %s<br>
      %s: <b>%s</b>
    </html>
    """
        .formatted(this, webType.toString(), url);
  }

  public String getAuthMethodUid() {
    return authMethodUid;
  }

  public void setAuthMethodUid(String authMethodUid) {
    this.authMethodUid =
        StringUtil.hasText(authMethodUid) ? authMethodUid : OAuth2ServiceFactory.NO;
  }

  public AuthMethod getAuthMethod() {
    return AuthenticationPersistence.getAuthMethod(authMethodUid);
  }

  public WebType getWebType() {
    return webType;
  }

  public void setWebType(WebType webType) {
    this.webType = webType;
  }

  public URL getUrl() {
    return url;
  }

  public void setUrl(URL url) {
    this.url = url;
  }

  public Map<String, String> getHeaders() {
    return headers;
  }

  public void addHeader(String key, String value) {
    if (StringUtil.hasText(key)) {
      headers.put(key, value);
    }
  }

  public void removeHeader(String key) {
    if (StringUtil.hasText(key)) {
      headers.remove(key);
    }
  }

  @Override
  public void saveDicomNode(XMLStreamWriter writer) throws XMLStreamException {
    super.saveDicomNode(writer);
    writer.writeAttribute(T_URL, url.toString());
    writer.writeAttribute(T_WEB_TYPE, StringUtil.getEmptyStringIfNullEnum(webType));
    writer.writeAttribute(T_AUTH, authMethodUid);
    String val =
        headers.entrySet().stream()
            .map(map -> map.getKey() + ":" + map.getValue())
            .collect(Collectors.joining("\n"));
    writer.writeAttribute(
        T_HEADER, Base64.getEncoder().encodeToString(val.getBytes(StandardCharsets.UTF_8)));
  }

  public static UsageType getUsageType(WebType webType) {
    if (WebType.DICOMWEB.equals(webType)) {
      return UsageType.BOTH;
    } else {
      if (WebType.STOWRS.equals(webType)) {
        return UsageType.STORAGE;
      }
      return UsageType.RETRIEVE;
    }
  }

  public static DicomWebNode buildDicomWebNode(XMLStreamReader xmler) throws MalformedURLException {
    WebType webType = WebType.valueOf(xmler.getAttributeValue(null, T_WEB_TYPE));

    DicomWebNode node =
        new DicomWebNode(
            xmler.getAttributeValue(null, T_DESCRIPTION),
            webType,
            new URL(xmler.getAttributeValue(null, T_URL)),
            getUsageType(webType));
    node.setTsuid(TransferSyntax.getTransferSyntax(xmler.getAttributeValue(null, T_TSUID)));
    node.setAuthMethodUid(xmler.getAttributeValue(null, T_AUTH));

    String val = xmler.getAttributeValue(null, T_HEADER);
    if (StringUtil.hasText(val)) {
      String result = new String(Base64.getDecoder().decode(val), StandardCharsets.UTF_8);
      String[] entry = result.split("[\\n]+"); // NON-NLS
      for (String s : entry) {
        String[] kv = s.split(":", 2);
        if (kv.length == 2) {
          node.addHeader(kv[0].trim(), kv[1].trim());
        }
      }
    }
    return node;
  }
}
