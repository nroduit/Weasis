package org.weasis.dicom.explorer.pref.node;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.net.URL;

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
import org.weasis.core.api.service.BundlePreferences;
import org.weasis.core.api.util.FileUtil;
import org.weasis.core.api.util.StringUtil;
import org.weasis.dicom.codec.TransferSyntax;

public class DicomWebNode extends DcmNode {
    private static final Logger LOGGER = LoggerFactory.getLogger(DicomWebNode.class);

    private static final String T_URL = "url";

    public enum Type {
        WADO("WADO"), WADORS("WADO-RS"), STOWRS("STOW-RS");

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
            return WADO;
        }
    };

    private Type type;
    // For WADO, WADO-RS and STOW
    private URL url;

    public DicomWebNode(String description, Type type, URL url) {
        super(description);
        this.url = url;
        this.type = type;
    }

    @Override
    public String getToolTips() {
        StringBuilder toolTips = new StringBuilder();
        toolTips.append("<html>"); //$NON-NLS-1$
        toolTips.append(toString());
        toolTips.append("<br>"); //$NON-NLS-1$
        toolTips.append(type.toString());
        toolTips.append(StringUtil.COLON_AND_SPACE);
        toolTips.append(url);
        toolTips.append("</html>"); //$NON-NLS-1$
        return toolTips.toString();
    }

    public Type getType() {
        return type;
    }

    public void setType(Type type) {
        this.type = type;
    }

    public URL getUrl() {
        return url;
    }

    public void setUrl(URL url) {
        this.url = url;
    }

    public static void saveDicomNodes(JComboBox<DicomWebNode> comboBox) {
        XMLStreamWriter writer = null;
        XMLOutputFactory factory = XMLOutputFactory.newInstance();
        final BundleContext context = FrameworkUtil.getBundle(DicomWebNode.class).getBundleContext();
        try {
            writer = factory.createXMLStreamWriter(
                new FileOutputStream(new File(BundlePreferences.getDataFolder(context), "dicomWebNodes.xml")), "UTF-8"); //$NON-NLS-1$ //$NON-NLS-2$

            writer.writeStartDocument("UTF-8", "1.0"); //$NON-NLS-1$ //$NON-NLS-2$
            writer.writeStartElement(T_NODES);
            for (int i = 0; i < comboBox.getItemCount(); i++) {
                DicomWebNode node = comboBox.getItemAt(i);
                writer.writeStartElement(T_NODE);
                writer.writeAttribute(T_DESCRIPTION, node.getDescription());
                writer.writeAttribute(T_URL, node.getUrl().toString());
                writer.writeAttribute(T_TYPE, StringUtil.getEmpty2NullEnum(node.getType()));
                writer.writeAttribute(T_TSUID, StringUtil.getEmpty2NullEnum(node.getTsuid()));
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

    public static void loadDicomNodes(JComboBox<DicomWebNode> comboBox) {
        final BundleContext context = FrameworkUtil.getBundle(DicomWebNode.class).getBundleContext();
        File prefs = new File(BundlePreferences.getDataFolder(context), "dicomWebNodes.xml"); //$NON-NLS-1$
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

    private static void readDicomNodes(XMLStreamReader xmler, JComboBox<DicomWebNode> comboBox)
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

    private static void readDicomNode(XMLStreamReader xmler, JComboBox<DicomWebNode> comboBox)
        throws XMLStreamException {
        String key = xmler.getName().getLocalPart();
        if (T_NODE.equals(key)) {
            try {
                DicomWebNode node = new DicomWebNode(xmler.getAttributeValue(null, T_DESCRIPTION),
                    Type.getType(xmler.getAttributeValue(null, T_TYPE)), new URL(xmler.getAttributeValue(null, T_URL)));
                node.setTsuid(TransferSyntax.getTransferSyntax(xmler.getAttributeValue(null, T_TSUID)));
                comboBox.addItem(node);
            } catch (Exception e) {
                LOGGER.error("Cannot read DicomNode: {}", e);
            }
        }
    }

}
