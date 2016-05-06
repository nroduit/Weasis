package org.weasis.dicom.explorer.pref.node;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;

import org.weasis.core.api.media.data.TagUtil;
import org.weasis.dicom.codec.TransferSyntax;

public class DicomPrintNode extends DefaultDicomNode {

    private static final String T_COLOR = "colorPrintSupported";

    // For printer
    private boolean colorPrintSupported;

    public DicomPrintNode(String description, String aeTitle, String hostname, Integer port) {
        super(description, aeTitle, hostname, port);
        this.colorPrintSupported = false;
    }

    public boolean isColorPrintSupported() {
        return colorPrintSupported;
    }

    public void setColorPrintSupported(boolean colorPrintSupported) {
        this.colorPrintSupported = colorPrintSupported;
    }

    @Override
    public void saveDicomNode(XMLStreamWriter writer) throws XMLStreamException {
        super.saveDicomNode(writer);
        writer.writeAttribute(T_COLOR, Boolean.toString(isColorPrintSupported()));
    }

    public static DicomPrintNode buildDicomPrintNode(XMLStreamReader xmler) {
        DicomPrintNode node =
            new DicomPrintNode(xmler.getAttributeValue(null, T_DESCRIPTION), xmler.getAttributeValue(null, T_AETITLE),
                xmler.getAttributeValue(null, T_HOST), TagUtil.getIntegerTagAttribute(xmler, T_PORT, 104));
        node.setTsuid(TransferSyntax.getTransferSyntax(xmler.getAttributeValue(null, T_TSUID)));
        node.setColorPrintSupported(Boolean.valueOf(xmler.getAttributeValue(null, T_COLOR)));

        // TODO add tls
        return node;
    }
}
