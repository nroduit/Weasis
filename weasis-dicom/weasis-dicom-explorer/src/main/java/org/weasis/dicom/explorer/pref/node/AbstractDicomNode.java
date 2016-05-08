package org.weasis.dicom.explorer.pref.node;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;

import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JOptionPane;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.plaf.basic.BasicComboPopup;
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
import org.weasis.core.api.gui.util.JMVUtils;
import org.weasis.core.api.service.BundlePreferences;
import org.weasis.core.api.util.FileUtil;
import org.weasis.core.api.util.ResourceUtil;
import org.weasis.core.api.util.StringUtil;
import org.weasis.dicom.codec.TransferSyntax;
import org.weasis.dicom.explorer.Messages;

public abstract class AbstractDicomNode {
    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractDicomNode.class);

    protected static final String T_NODES = "nodes";
    protected static final String T_NODE = "node";

    protected static final String T_DESCRIPTION = "description";
    protected static final String T_TYPE = "type";
    protected static final String T_TSUID = "tsuid";

    public enum Type {
        ARCHIVE("Archive", "dicomNodes.xml"), PRINTER("Printer", "dicomPrinters.xml"),
        WEB("WEB Archive", "dicomWebNodes.xml");

        final String title;
        final String filename;

        Type(String title, String filename) {
            this.title = title;
            this.filename = filename;
        }

        @Override
        public String toString() {
            return title;
        }

        public String getFilename() {
            return filename;
        }

        public static Type getType(String name) {
            try {
                return Type.valueOf(name);
            } catch (Exception e) {
                // DO nothing
            }
            return ARCHIVE;
        }
    }

    private String description;
    private TransferSyntax tsuid;

    private Type type;
    private boolean local;

    public AbstractDicomNode(String description, Type type) {
        if (type == null) {
            throw new IllegalArgumentException("Type cannot be null");
        }
        this.description = description;
        this.type = type;
        this.local = true;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    @Override
    public String toString() {
        return description;
    }

    public TransferSyntax getTsuid() {
        return tsuid;
    }

    public void setTsuid(TransferSyntax tsuid) {
        this.tsuid = tsuid;
    }

    public String getToolTips() {
        return description;
    }

    public Type getType() {
        return type;
    }

    public void setType(Type type) {
        this.type = type == null ? Type.ARCHIVE : type;
    }

    public boolean isLocal() {
        return local;
    }

    public void setLocal(boolean local) {
        this.local = local;
    }

    public void saveDicomNode(XMLStreamWriter writer) throws XMLStreamException {
        writer.writeAttribute(T_DESCRIPTION, description);
        writer.writeAttribute(T_TYPE, StringUtil.getEmpty2NullEnum(type));
        writer.writeAttribute(T_TSUID, StringUtil.getEmpty2NullEnum(tsuid));
    }

    public static void loadDicomNodes(JComboBox<AbstractDicomNode> comboBox, Type type) {
        // Load nodes from ressources
        loadDicomNodes(comboBox, ResourceUtil.getResource(type.getFilename()), type, false);

        // Load nodes from local data
        final BundleContext context = FrameworkUtil.getBundle(AbstractDicomNode.class).getBundleContext();
        loadDicomNodes(comboBox, new File(BundlePreferences.getDataFolder(context), type.getFilename()), type, true);
    }

    public static void saveDicomNodes(JComboBox<? extends AbstractDicomNode> comboBox, Type type) {
        XMLStreamWriter writer = null;
        XMLOutputFactory factory = XMLOutputFactory.newInstance();
        final BundleContext context = FrameworkUtil.getBundle(AbstractDicomNode.class).getBundleContext();
        try {
            writer = factory.createXMLStreamWriter(
                new FileOutputStream(new File(BundlePreferences.getDataFolder(context), type.getFilename())), "UTF-8"); //$NON-NLS-1$ //$NON-NLS-2$

            writer.writeStartDocument("UTF-8", "1.0"); //$NON-NLS-1$ //$NON-NLS-2$
            writer.writeStartElement(T_NODES);
            for (int i = 0; i < comboBox.getItemCount(); i++) {
                AbstractDicomNode node = comboBox.getItemAt(i);
                if (node.isLocal() && type == node.getType()) {
                    writer.writeStartElement(T_NODE);
                    node.saveDicomNode(writer);
                    writer.writeEndElement();
                }
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

    private static void loadDicomNodes(JComboBox<AbstractDicomNode> comboBox, File prefs, Type type, boolean local) {
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
                            readDicomNodes(xmler, comboBox, type, local);
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

    private static void readDicomNodes(XMLStreamReader xmler, JComboBox<AbstractDicomNode> comboBox, Type type,
        boolean local) throws XMLStreamException {
        String key = xmler.getName().getLocalPart();
        if (T_NODES.equals(key)) {
            while (xmler.hasNext()) {
                int eventType = xmler.next();
                switch (eventType) {
                    case XMLStreamConstants.START_ELEMENT:
                        readDicomNode(xmler, comboBox, type, local);
                        break;
                    default:
                        break;
                }
            }
        }
    }

    private static void readDicomNode(XMLStreamReader xmler, JComboBox<AbstractDicomNode> comboBox, Type type,
        boolean local) throws XMLStreamException {
        String key = xmler.getName().getLocalPart();
        if (T_NODE.equals(key)) {
            try {
                Type t = Type.getType(xmler.getAttributeValue(null, T_TYPE));
                if (type != null && type != t) {
                    return;
                }

                AbstractDicomNode node;
                if (AbstractDicomNode.Type.WEB == type) {
                    node = DicomWebNode.buildDicomWebNode(xmler);
                } else if (AbstractDicomNode.Type.PRINTER == type) {
                    node = DicomPrintNode.buildDicomPrintNode(xmler);
                } else {
                    node = DefaultDicomNode.buildDicomNodeEx(xmler);
                }

                node.setLocal(local);
                node.setType(t);

                comboBox.addItem(node);
            } catch (Exception e) {
                LOGGER.error("Cannot read DicomNode: {}", e);
            }
        }
    }

    public static void addNodeActionPerformed(JComboBox<? extends AbstractDicomNode> comboBox, Type type) {
        JDialog dialog;
        if (AbstractDicomNode.Type.WEB == type) {
            dialog = new DicomWebNodeDialog(SwingUtilities.getWindowAncestor(comboBox), "DICOM Node", //$NON-NLS-1$
                null, (JComboBox<DicomWebNode>) comboBox);
        } else {
            dialog = new DicomNodeDialog(SwingUtilities.getWindowAncestor(comboBox), "DICOM Node", //$NON-NLS-1$
                null, (JComboBox<DefaultDicomNode>) comboBox, type);
        }
        JMVUtils.showCenterScreen(dialog, comboBox);
    }

    public static void editNodeActionPerformed(JComboBox<? extends AbstractDicomNode> comboBox) {
        AbstractDicomNode node = (AbstractDicomNode) comboBox.getSelectedItem();
        if (node != null) {
            if (node.isLocal()) {
                Type type = node.getType();
                JDialog dialog;
                if (AbstractDicomNode.Type.WEB == type) {
                    dialog = new DicomWebNodeDialog(SwingUtilities.getWindowAncestor(comboBox), "DICOM Node", //$NON-NLS-1$
                        (DicomWebNode) node, (JComboBox<DicomWebNode>) comboBox);
                } else {
                    dialog = new DicomNodeDialog(SwingUtilities.getWindowAncestor(comboBox), "DICOM Node", //$NON-NLS-1$
                        (DefaultDicomNode) node, (JComboBox<DefaultDicomNode>) comboBox, type);
                }
                JMVUtils.showCenterScreen(dialog, comboBox);
            } else {
                JOptionPane.showMessageDialog(comboBox, "Only user-created node can be modified!",
                    Messages.getString("DicomPrintDialog.error"), JOptionPane.ERROR_MESSAGE); // $NON-NLS-1$
            }
        }
    }

    public static void deleteNodeActionPerformed(JComboBox<? extends AbstractDicomNode> comboBox) {
        int index = comboBox.getSelectedIndex();
        if (index >= 0) {
            AbstractDicomNode node = comboBox.getItemAt(index);
            if (node.isLocal()) {
                int response = JOptionPane.showConfirmDialog(comboBox,
                    String.format("Do you really want to delete \"%s\"?", node), "DICOM Node", //$NON-NLS-2$
                    JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);

                if (response == 0) {
                    comboBox.removeItemAt(index);
                    AbstractDicomNode.saveDicomNodes(comboBox, node.getType());
                }
            } else {
                JOptionPane.showMessageDialog(comboBox, "Only user-created node can be modified!",
                    Messages.getString("DicomPrintDialog.error"), JOptionPane.ERROR_MESSAGE); // $NON-NLS-1$
            }
        }
    }

    public static void addTooltipToComboList(final JComboBox<? extends AbstractDicomNode> combo) {
        Object comp = combo.getUI().getAccessibleChild(combo, 0);
        if (comp instanceof BasicComboPopup) {
            final BasicComboPopup popup = (BasicComboPopup) comp;
            popup.getList().getSelectionModel().addListSelectionListener(new ListSelectionListener() {

                @Override
                public void valueChanged(ListSelectionEvent e) {
                    if (!e.getValueIsAdjusting()) {
                        ListSelectionModel model = (ListSelectionModel) e.getSource();
                        int first = model.getMinSelectionIndex();
                        if (first >= 0) {
                            AbstractDicomNode item = combo.getItemAt(first);
                            ((JComponent) combo.getRenderer()).setToolTipText(item.getToolTips());
                        }
                    }
                }
            });
        }
    }

}