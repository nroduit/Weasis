package org.weasis.dicom.explorer.pref.node;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.media.data.TagUtil;
import org.weasis.core.api.util.StringUtil;
import org.weasis.dicom.codec.TransferSyntax;
import org.weasis.dicom.param.DicomNode;
import org.weasis.dicom.param.TlsOptions;

public class DefaultDicomNode extends AbstractDicomNode {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultDicomNode.class);

    protected static final String T_AETITLE = "aeTitle";
    protected static final String T_HOST = "hostname";
    protected static final String T_PORT = "port";

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
        toolTips.append("@");
        toolTips.append(hostname);
        toolTips.append(":");
        toolTips.append(port);
        toolTips.append("</html>"); //$NON-NLS-1$
        return toolTips.toString();
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
            throw new IllegalArgumentException("Port out of bound");
        }
        this.port = port;
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
