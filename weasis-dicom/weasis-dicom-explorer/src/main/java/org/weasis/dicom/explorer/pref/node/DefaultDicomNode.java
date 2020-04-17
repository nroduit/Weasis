/*******************************************************************************
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.weasis.dicom.explorer.pref.node;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;

import org.weasis.core.api.media.data.TagUtil;
import org.weasis.core.util.StringUtil;
import org.weasis.dicom.codec.TransferSyntax;
import org.weasis.dicom.param.DicomNode;
import org.weasis.dicom.param.TlsOptions;

public class DefaultDicomNode extends AbstractDicomNode {

    protected static final String T_AETITLE = "aeTitle"; //$NON-NLS-1$
    protected static final String T_HOST = "hostname"; //$NON-NLS-1$
    protected static final String T_PORT = "port"; //$NON-NLS-1$

    // For C-MOVE, C-GET, C-STORE
    protected String aeTitle;
    protected String hostname;
    protected int port;
    protected TlsOptions tlsOptions;

    public DefaultDicomNode(String description, String aeTitle, String hostname, Integer port, UsageType usageType) {
        super(description, Type.DICOM, usageType);
        this.hostname = hostname;
        setAeTitle(aeTitle);
        setPort(port);
    }

    @Override
    public String getToolTips() {
        StringBuilder toolTips = new StringBuilder();
        toolTips.append("<html>"); //$NON-NLS-1$
        toolTips.append(toString());
        toolTips.append("<br>"); //$NON-NLS-1$
        toolTips.append(getType().toString());
        toolTips.append(StringUtil.COLON_AND_SPACE);
        toolTips.append(aeTitle);
        toolTips.append("@"); //$NON-NLS-1$
        toolTips.append(hostname);
        toolTips.append(":"); //$NON-NLS-1$
        toolTips.append(port);
        toolTips.append("</html>"); //$NON-NLS-1$
        return toolTips.toString();
    }

    public String getAeTitle() {
        return aeTitle;
    }

    public void setAeTitle(String aeTitle) {
        if (!StringUtil.hasText(aeTitle)) {
            throw new IllegalArgumentException("Missing AET"); //$NON-NLS-1$
        }
        if (aeTitle.length() > 16) {
            throw new IllegalArgumentException("AET has more than 16 characters"); //$NON-NLS-1$
        }
        this.aeTitle = aeTitle;
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
            throw new IllegalArgumentException("Port out of bound"); //$NON-NLS-1$
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
    public void saveDicomNode(XMLStreamWriter writer) throws XMLStreamException {
        super.saveDicomNode(writer);
        writer.writeAttribute(T_AETITLE, aeTitle);
        writer.writeAttribute(T_HOST, hostname);
        writer.writeAttribute(T_PORT, Integer.toString(port));

        // writer.writeAttribute("tlsOptions", StringUtil.getEmpty2NullObject(printer.getTlsOptions()));
    }

    public static DefaultDicomNode buildDicomNodeEx(XMLStreamReader xmler) {
        DefaultDicomNode node =
            new DefaultDicomNode(xmler.getAttributeValue(null, T_DESCRIPTION), xmler.getAttributeValue(null, T_AETITLE),
                xmler.getAttributeValue(null, T_HOST), TagUtil.getIntegerTagAttribute(xmler, T_PORT, 104),
                UsageType.valueOf(xmler.getAttributeValue(null, T_USAGE_TYPE)));
        node.setTsuid(TransferSyntax.getTransferSyntax(xmler.getAttributeValue(null, T_TSUID)));

        // TODO add tls
        return node;
    }

}
