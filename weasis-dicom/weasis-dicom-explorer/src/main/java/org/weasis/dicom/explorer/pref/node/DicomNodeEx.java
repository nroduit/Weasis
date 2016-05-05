package org.weasis.dicom.explorer.pref.node;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;

import javax.swing.JComboBox;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;

import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.media.data.TagUtil;
import org.weasis.core.api.service.BundlePreferences;
import org.weasis.core.api.util.FileUtil;
import org.weasis.core.api.util.StringUtil;
import org.weasis.dicom.codec.TransferSyntax;
import org.weasis.dicom.param.DicomNode;
import org.weasis.dicom.param.TlsOptions;

public class DicomNodeEx extends DcmNode {
    private static final Logger LOGGER = LoggerFactory.getLogger(DicomNodeEx.class);

    private static final String T_AETITLE = "aeTitle";
    private static final String T_HOST = "hostname";
    private static final String T_PORT = "port";
    private static final String T_COLOR = "colorPrintSupported";

    public enum Type {
        ARCHIVE("Archive"), PRINTER("Printer");

        String title;

        Type(String title) {
            this.title = title;
        }

        @Override
        public String toString() {
            return title;
        }

        public static Type getType(String name) {
            try {
                return Type.valueOf(name);
            } catch (Exception e) {
                // DO nothing
            }
            return ARCHIVE;
        }
    };

    private Type type;
    // For C-MOVE, C-GET, C-STORE
    private String aeTitle;
    private String hostname;
    private int port;
    private TlsOptions tlsOptions;

    // For printer
    private boolean colorPrintSupported;

    public DicomNodeEx(String description, String aeTitle, String hostname, Integer port) {
        super(description);
        this.hostname = hostname;
        setAeTitle(aeTitle);
        setPort(port);
        this.type = Type.ARCHIVE;
        this.colorPrintSupported = false;
    }

    @Override
    public String getToolTips() {
        StringBuilder toolTips = new StringBuilder();
        toolTips.append("<html>"); //$NON-NLS-1$
        toolTips.append(toString());
        toolTips.append("<br>"); //$NON-NLS-1$
        toolTips.append(type.toString());
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

    public Type getType() {
        return type;
    }

    public void setType(Type type) {
        this.type = type;
    }

    public boolean isColorPrintSupported() {
        return colorPrintSupported;
    }

    public void setColorPrintSupported(boolean colorPrintSupported) {
        this.colorPrintSupported = colorPrintSupported;
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

    public static void saveDicomNodes(JComboBox<DicomNodeEx> comboBox) {
        XMLStreamWriter writer = null;
        XMLOutputFactory factory = XMLOutputFactory.newInstance();
        final BundleContext context = FrameworkUtil.getBundle(DicomNodeEx.class).getBundleContext();
        try {
            writer = factory.createXMLStreamWriter(
                new FileOutputStream(new File(BundlePreferences.getDataFolder(context), "dicomNodes.xml")), "UTF-8"); //$NON-NLS-1$ //$NON-NLS-2$

            writer.writeStartDocument("UTF-8", "1.0"); //$NON-NLS-1$ //$NON-NLS-2$
            writer.writeStartElement(T_NODES);
            for (int i = 0; i < comboBox.getItemCount(); i++) {
                DicomNodeEx node = comboBox.getItemAt(i);
                writer.writeStartElement(T_NODE);
                writer.writeAttribute(T_DESCRIPTION, node.getDescription());
                writer.writeAttribute(T_AETITLE, node.getAeTitle());
                writer.writeAttribute(T_HOST, node.getHostname());
                writer.writeAttribute(T_PORT, Integer.toString(node.getPort()));
                writer.writeAttribute(T_TYPE, StringUtil.getEmpty2NullEnum(node.getType()));
                if (Type.PRINTER.equals(node.getType())) {
                    writer.writeAttribute(T_COLOR, Boolean.toString(node.isColorPrintSupported()));
                } else {
                    writer.writeAttribute(T_TSUID, StringUtil.getEmpty2NullEnum(node.getTsuid()));
                }

                // writer.writeAttribute("tlsOptions", StringUtil.getEmpty2NullObject(printer.getTlsOptions()));
                writer.writeEndElement();
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

    public static void loadDicomNodes(JComboBox<DicomNodeEx> comboBox) {
        final BundleContext context = FrameworkUtil.getBundle(DicomNodeEx.class).getBundleContext();
        File prefs = new File(BundlePreferences.getDataFolder(context), "dicomNodes.xml"); //$NON-NLS-1$
        if (prefs.canRead()) {
            XMLStreamReader xmler = null;
            XMLInputFactory factory = XMLInputFactory.newInstance();
            try {
                xmler = factory.createXMLStreamReader(new FileInputStream(prefs));
                int eventType;
                while (xmler.hasNext()) {
                    eventType = xmler.next();
                    switch (eventType) {
                        case XMLStreamConstants.START_ELEMENT:
                            readDicomNodes(xmler, comboBox);
                            break;
                        default:
                            break;
                    }
                }
            } catch (Exception e) {
                LOGGER.error("Error on reading DICOM node file", e);
            } finally {
                FileUtil.safeClose(xmler);
            }
        }
    }

    private static void readDicomNodes(XMLStreamReader xmler, JComboBox<DicomNodeEx> comboBox)
        throws XMLStreamException {
        String key = xmler.getName().getLocalPart();
        if (T_NODES.equals(key)) {
            while (xmler.hasNext()) {
                int eventType = xmler.next();
                switch (eventType) {
                    case XMLStreamConstants.START_ELEMENT:
                        readDicomNode(xmler, comboBox);
                        break;
                    default:
                        break;
                }
            }
        }
    }

    private static void readDicomNode(XMLStreamReader xmler, JComboBox<DicomNodeEx> comboBox)
        throws XMLStreamException {
        String key = xmler.getName().getLocalPart();
        if (T_NODE.equals(key)) {
            try {
                DicomNodeEx node = new DicomNodeEx(xmler.getAttributeValue(null, T_DESCRIPTION),
                    xmler.getAttributeValue(null, T_AETITLE), xmler.getAttributeValue(null, T_HOST),
                    TagUtil.getIntegerTagAttribute(xmler, T_PORT, 104));

                node.setType(Type.getType(xmler.getAttributeValue(null, T_TYPE)));
                if (Type.PRINTER.equals(node.getType())) {
                    node.setColorPrintSupported(Boolean.valueOf(xmler.getAttributeValue(null, T_COLOR)));
                } else {
                    node.setTsuid(TransferSyntax.getTransferSyntax(xmler.getAttributeValue(null, T_TSUID)));
                }

                // TODO add tls

                comboBox.addItem(node);
            } catch (Exception e) {
                LOGGER.error("Cannot read DicomNode: {}", e);
            }
        }
    }

}
