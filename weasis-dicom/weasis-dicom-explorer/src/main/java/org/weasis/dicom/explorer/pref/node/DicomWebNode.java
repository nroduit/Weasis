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

public class DicomWebNode extends AbstractDicomNode {

    private static final String T_URL = "url"; //$NON-NLS-1$
    private static final String T_WEB_TYPE = "webtype"; //$NON-NLS-1$

    public enum WebType {
        STOWRS("STOW-RS"), WADO("WADO"), WADORS("WADO-RS"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$

        String title;

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

    public DicomWebNode(String description, WebType webType, URL url, UsageType usageType) {
        super(description, Type.WEB, usageType);
        this.url = url;
        this.webType = webType;
    }

    @Override
    public String getToolTips() {
        StringBuilder toolTips = new StringBuilder();
        toolTips.append("<html>"); //$NON-NLS-1$
        toolTips.append(toString());
        toolTips.append("<br>"); //$NON-NLS-1$
        toolTips.append(webType.toString());
        toolTips.append(StringUtil.COLON_AND_SPACE);
        toolTips.append(url);
        toolTips.append("</html>"); //$NON-NLS-1$
        return toolTips.toString();
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

    @Override
    public void saveDicomNode(XMLStreamWriter writer) throws XMLStreamException {
        super.saveDicomNode(writer);
        writer.writeAttribute(T_URL, url.toString());
        writer.writeAttribute(T_WEB_TYPE, StringUtil.getEmptyStringIfNullEnum(webType));
    }

    public static UsageType getUsageType(WebType webType) {
        return DicomWebNode.WebType.STOWRS.equals(webType) ? UsageType.STORAGE : UsageType.RETRIEVE;
    }

    public static DicomWebNode buildDicomWebNode(XMLStreamReader xmler) throws MalformedURLException {
        WebType webType = WebType.valueOf(xmler.getAttributeValue(null, T_WEB_TYPE));

        DicomWebNode node = new DicomWebNode(xmler.getAttributeValue(null, T_DESCRIPTION), webType,
            new URL(xmler.getAttributeValue(null, T_URL)), getUsageType(webType));
        node.setTsuid(TransferSyntax.getTransferSyntax(xmler.getAttributeValue(null, T_TSUID)));
        return node;
    }

}
