/*******************************************************************************
 * Copyright (c) 2009-2018 Weasis Team and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 *     Nicolas Roduit - initial API and implementation
 *******************************************************************************/
package org.weasis.dicom.explorer.pref.node;

import java.net.MalformedURLException;
import java.net.URL;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;

import org.weasis.core.api.util.StringUtil;
import org.weasis.dicom.codec.TransferSyntax;

public class DicomQidoNode extends AbstractDicomNode {

    private static final String Q_URL = "urlQido"; //$NON-NLS-1$
    private static final String W_URL = "urlWado"; //$NON-NLS-1$

    private URL qidoUrl;
    private URL wadoUrl;

    public DicomQidoNode(String description, URL qidoUrl, URL wadoUrl) {
        super(description, Type.WEB, UsageType.RETRIEVE);
        this.qidoUrl = qidoUrl;
        this.wadoUrl = wadoUrl;
    }

    @Override
    public String getToolTips() {
        StringBuilder toolTips = new StringBuilder();
        toolTips.append("<html>"); //$NON-NLS-1$
        toolTips.append(toString());
        toolTips.append("<br>"); //$NON-NLS-1$
        toolTips.append("QIDO-RS"); //$NON-NLS-1$
        toolTips.append(StringUtil.COLON_AND_SPACE);
        toolTips.append(qidoUrl);
        toolTips.append("<br>"); //$NON-NLS-1$
        toolTips.append("WADO-RS"); //$NON-NLS-1$
        toolTips.append(StringUtil.COLON_AND_SPACE);
        toolTips.append(wadoUrl);
        toolTips.append("</html>"); //$NON-NLS-1$
        return toolTips.toString();
    }

    @Override
    public void saveDicomNode(XMLStreamWriter writer) throws XMLStreamException {
        super.saveDicomNode(writer);
        writer.writeAttribute(Q_URL, qidoUrl.toString());
        writer.writeAttribute(W_URL, wadoUrl.toString());
    }

    public static DicomQidoNode buildDicomWebNode(XMLStreamReader xmler) throws MalformedURLException {
        DicomQidoNode node = new DicomQidoNode(xmler.getAttributeValue(null, T_DESCRIPTION),
            new URL(xmler.getAttributeValue(null, Q_URL)), new URL(xmler.getAttributeValue(null, W_URL)));
        node.setTsuid(TransferSyntax.getTransferSyntax(xmler.getAttributeValue(null, T_TSUID)));
        return node;
    }

}
