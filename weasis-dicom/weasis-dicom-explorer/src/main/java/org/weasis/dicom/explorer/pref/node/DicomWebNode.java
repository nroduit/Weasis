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

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import javax.xml.stream.XMLStreamReader;
import org.weasis.core.api.net.auth.AuthMethod;
import org.weasis.core.api.net.auth.OAuth2ServiceFactory;
import org.weasis.core.api.service.SecretStore;
import org.weasis.core.api.util.EntryIds;
import org.weasis.core.api.util.JsonUtil;
import org.weasis.core.util.StringUtil;
import org.weasis.dicom.codec.TransferSyntax;

public class DicomWebNode extends AbstractDicomNode {

  public static final String T_URL = "url"; // NON-NLS

  /** The legacy XML attribute of the service. */
  public static final String T_WEB_TYPE = "webtype"; // NON-NLS

  /** The JSON member of the service. */
  public static final String T_WEB_TYPE_JSON = "webType"; // NON-NLS

  public static final String T_HEADER = "headers"; // NON-NLS

  /** The legacy XML attribute of the authentication method uid. */
  public static final String T_AUTH = "auth"; // NON-NLS

  /** The JSON member of the authentication method uid; {@value #T_AUTH} is read as an alias. */
  public static final String T_AUTH_METHOD = "authMethod"; // NON-NLS

  public enum WebType {
    DICOMWEB("DICOMweb (all RESTful services)"), // NON-NLS
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
    this.authMethodUid = OAuth2ServiceFactory.NO_AUTH_ID; // NOSONAR !secret
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

  /** The key of the node in its document: its URL. */
  @Override
  public String deriveId(String prefix) {
    return EntryIds.derived(prefix, ID_KIND, url == null ? null : url.toString());
  }

  public String getAuthMethodUid() {
    return authMethodUid;
  }

  public void setAuthMethodUid(String authMethodUid) {
    this.authMethodUid =
        StringUtil.hasText(authMethodUid)
            ? authMethodUid
            : OAuth2ServiceFactory.NO_AUTH_ID; // NOSONAR !secret
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

  /**
   * The header values are secrets: in the user document they are written empty and kept in the
   * {@link SecretStore} under the node id, by header name. A site document carries them as is. The
   * authentication method is referenced by its uid under {@code authMethod}.
   */
  @Override
  protected void writeJson(JsonObjectBuilder b, boolean userDocument) {
    if (url != null) {
      b.add(T_URL, url.toString());
    }
    if (webType != null) {
      b.add(T_WEB_TYPE_JSON, webType.name());
    }
    JsonUtil.addIfPresent(b, T_AUTH_METHOD, authMethodUid);
    JsonObjectBuilder h = Json.createObjectBuilder();
    headers.forEach((name, value) -> h.add(name, userDocument || value == null ? "" : value));
    b.add(T_HEADER, h);
  }

  /** The usage of a DICOMweb node follows its service, see {@link #getUsageType(WebType)}. */
  @Override
  protected UsageType defaultUsageType() {
    return getUsageType(webType);
  }

  @Override
  protected void storeSecrets() {
    if (StringUtil.hasText(getId())) {
      SecretStore.getInstance().putAll(getId(), headers);
    }
  }

  @Override
  protected void loadSecrets() {
    if (!StringUtil.hasText(getId())) {
      return;
    }
    SecretStore store = SecretStore.getInstance();
    headers.replaceAll(
        (name, value) ->
            StringUtil.hasText(value) ? value : store.get(getId(), name).orElse(value));
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

  static DicomWebNode fromJson(JsonObject json) throws MalformedURLException {
    WebType webType = WebType.valueOf(json.getString(T_WEB_TYPE_JSON, WebType.DICOMWEB.name()));
    String url = json.getString(T_URL, null);
    if (!StringUtil.hasText(url)) {
      throw new MalformedURLException("Missing URL");
    }
    DicomWebNode node =
        new DicomWebNode(
            json.getString(T_DESCRIPTION, null),
            webType,
            new URL(url),
            usageType(json, getUsageType(webType)));
    node.readJson(json);
    node.setAuthMethodUid(json.getString(T_AUTH_METHOD, json.getString(T_AUTH, null)));
    JsonUtil.getStringMap(json, T_HEADER).forEach(node::addHeader);
    return node;
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
