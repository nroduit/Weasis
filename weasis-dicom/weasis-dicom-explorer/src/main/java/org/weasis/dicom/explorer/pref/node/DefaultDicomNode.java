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

import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import javax.xml.stream.XMLStreamReader;
import org.weasis.core.api.media.data.TagUtil;
import org.weasis.core.api.media.data.XmlAttributeSource;
import org.weasis.core.api.util.EntryIds;
import org.weasis.core.api.util.JsonUtil;
import org.weasis.core.util.StringUtil;
import org.weasis.dicom.codec.TransferSyntax;
import org.weasis.dicom.param.DicomNode;
import org.weasis.dicom.param.TlsOptions;

public class DefaultDicomNode extends AbstractDicomNode {

  protected static final String T_AETITLE = "aeTitle";
  protected static final String T_HOST = "hostname"; // NON-NLS
  protected static final String T_PORT = "port"; // NON-NLS

  // For C-MOVE, C-GET, C-STORE
  protected String aeTitle;
  protected String hostname;
  protected int port;
  protected TlsOptions tlsOptions;

  public DefaultDicomNode(
      String description, String aeTitle, String hostname, Integer port, UsageType usageType) {
    super(description, Type.DICOM, usageType);
    this.hostname = hostname;
    setAeTitle(aeTitle);
    setPort(port);
  }

  @Override
  public String getToolTips() {
    return """
      <html>
        %s<br>
        %s: <b>%s@%s:%d</b>
      </html>
      """
        .formatted(this, getType().toString(), aeTitle, hostname, port);
  }

  /** The key of the node in its document: {@code aeTitle@hostname:port}. */
  @Override
  public String deriveId(String prefix) {
    return EntryIds.derived(prefix, ID_KIND, aeTitle + "@" + hostname + ":" + port); // NON-NLS
  }

  public String getAeTitle() {
    return aeTitle;
  }

  public void setAeTitle(String aeTitle) {
    if (!StringUtil.hasText(aeTitle)) {
      throw new IllegalArgumentException("Missing AET");
    }
    if (aeTitle.length() > 16) {
      throw new IllegalArgumentException("AET has more than 16 characters");
    }
    this.aeTitle = aeTitle.trim();
  }

  public String getHostname() {
    return hostname;
  }

  public void setHostname(String hostname) {
    this.hostname = hostname;
  }

  public int getPort() {
    return port;
  }

  public void setPort(Integer port) {
    if (port != null && (port < 1 || port > 65535)) {
      throw new IllegalArgumentException("Port out of bound");
    }
    this.port = port == null ? 104 : port;
  }

  public TlsOptions getTlsOptions() {
    return tlsOptions;
  }

  public void setTlsOptions(TlsOptions tlsOptions) {
    this.tlsOptions = tlsOptions;
  }

  public DicomNode getDicomNode() {
    return new DicomNode(aeTitle, hostname, port);
  }

  public DicomNode getDicomNodeWithOnlyAET() {
    return new DicomNode(aeTitle);
  }

  @Override
  protected void writeJson(JsonObjectBuilder b, boolean userDocument) {
    b.add(T_AETITLE, aeTitle);
    JsonUtil.addIfPresent(b, T_HOST, hostname);
    b.add(T_PORT, port);
  }

  static DefaultDicomNode fromJson(JsonObject json) {
    DefaultDicomNode node =
        new DefaultDicomNode(
            json.getString(T_DESCRIPTION, null),
            json.getString(T_AETITLE, null),
            json.getString(T_HOST, null),
            JsonUtil.getInt(json, T_PORT, 104),
            usageType(json, UsageType.BOTH));
    node.readJson(json);
    return node;
  }

  public static DefaultDicomNode buildDicomNodeEx(XMLStreamReader xmler) {
    DefaultDicomNode node =
        new DefaultDicomNode(
            xmler.getAttributeValue(null, T_DESCRIPTION),
            xmler.getAttributeValue(null, T_AETITLE),
            xmler.getAttributeValue(null, T_HOST),
            TagUtil.getIntegerTagAttribute(new XmlAttributeSource(xmler), T_PORT, 104),
            UsageType.valueOf(xmler.getAttributeValue(null, T_USAGE_TYPE)));
    node.setTsuid(TransferSyntax.getTransferSyntax(xmler.getAttributeValue(null, T_TSUID)));

    // TODO add tls
    return node;
  }
}
