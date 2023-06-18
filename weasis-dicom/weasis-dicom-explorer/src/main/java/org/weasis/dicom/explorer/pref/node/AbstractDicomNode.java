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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JOptionPane;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
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
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.service.BundlePreferences;
import org.weasis.core.api.util.ResourceUtil;
import org.weasis.core.util.FileUtil;
import org.weasis.core.util.StringUtil;
import org.weasis.dicom.codec.TransferSyntax;
import org.weasis.dicom.codec.utils.DicomResource;
import org.weasis.dicom.explorer.Messages;
import org.weasis.dicom.explorer.pref.node.DicomWebNode.WebType;

public abstract class AbstractDicomNode {
  private static final Logger LOGGER = LoggerFactory.getLogger(AbstractDicomNode.class);

  protected static final String T_NODES = "nodes"; // NON-NLS
  protected static final String T_NODE = "node"; // NON-NLS

  protected static final String T_DESCRIPTION = "description";
  protected static final String T_TYPE = "type";
  protected static final String T_USAGE_TYPE = "usageType";
  protected static final String T_TSUID = "tsuid"; // NON-NLS

  public enum Type {
    DICOM(Messages.getString("AbstractDicomNode.dcm_node"), "dicomNodes.xml"),
    DICOM_CALLING(
        Messages.getString("AbstractDicomNode.dcm_calling_node"),
        DicomResource.CALLING_NODES.getPath()),
    PRINTER(Messages.getString("AbstractDicomNode.dcm_printer"), "dicomPrinterNodes.xml"),
    WEB(Messages.getString("AbstractDicomNode.dcm_web_node"), "dicomWebNodes.xml");

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
  }

  public enum UsageType {
    STORAGE(Messages.getString("AbstractDicomNode.storage")),
    RETRIEVE(Messages.getString("AbstractDicomNode.retrieve")),
    BOTH(Messages.getString("AbstractDicomNode.both"));

    final String title;

    UsageType(String title) {
      this.title = title;
    }

    @Override
    public String toString() {
      return title;
    }
  }

  public enum RetrieveType {
    CMOVE("C-MOVE"), // NON-NLS
    CGET("C-GET"), // NON-NLS
    WADO("WADO-URI"); // NON-NLS

    final String title;

    RetrieveType(String title) {
      this.title = title;
    }

    @Override
    public String toString() {
      return title;
    }
  }

  private String description;
  private TransferSyntax tsuid;

  private Type type;
  private UsageType usageType;
  private boolean local;

  protected AbstractDicomNode(String description, Type type, UsageType usageType) {
    if (type == null) {
      throw new IllegalArgumentException("Type cannot be null");
    }
    this.description = description;
    this.tsuid = TransferSyntax.NONE;
    this.type = type;
    this.usageType = usageType;
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
    this.type = type == null ? Type.DICOM : type;
  }

  public UsageType getUsageType() {
    return usageType;
  }

  public void setUsageType(UsageType usageType) {
    this.usageType = usageType;
  }

  public boolean isLocal() {
    return local;
  }

  public void setLocal(boolean local) {
    this.local = local;
  }

  public void saveDicomNode(XMLStreamWriter writer) throws XMLStreamException {
    writer.writeAttribute(T_DESCRIPTION, description);
    writer.writeAttribute(T_TYPE, StringUtil.getEmptyStringIfNullEnum(type));
    writer.writeAttribute(T_USAGE_TYPE, StringUtil.getEmptyStringIfNullEnum(usageType));
    writer.writeAttribute(T_TSUID, StringUtil.getEmptyStringIfNullEnum(tsuid));
  }

  public static void loadDicomNodes(JComboBox<AbstractDicomNode> comboBox, Type type) {
    loadDicomNodes(comboBox, type, UsageType.BOTH);
  }

  public static void loadDicomNodes(
      JComboBox<AbstractDicomNode> comboBox, Type type, UsageType usage) {
    loadDicomNodes(comboBox, type, usage, null);
  }

  public static void loadDicomNodes(
      JComboBox<AbstractDicomNode> comboBox, Type type, UsageType usage, WebType webType) {
    List<AbstractDicomNode> list = loadDicomNodes(type, usage, webType);
    for (AbstractDicomNode node : list) {
      comboBox.addItem(node);
    }
  }

  public static List<AbstractDicomNode> loadDicomNodes(Type type, UsageType usage) {
    return loadDicomNodes(type, usage, null);
  }

  public static List<AbstractDicomNode> loadDicomNodes(
      Type type, UsageType usage, WebType webType) {
    List<AbstractDicomNode> list = new ArrayList<>();
    // Load nodes from resources
    loadDicomNodes(list, ResourceUtil.getResource(type.getFilename()), type, false, usage, webType);

    // Load nodes from local data
    final BundleContext context =
        FrameworkUtil.getBundle(AbstractDicomNode.class).getBundleContext();
    loadDicomNodes(
        list,
        new File(BundlePreferences.getDataFolder(context), type.getFilename()),
        type,
        true,
        usage,
        webType);

    return list;
  }

  public static void saveDicomNodes(JComboBox<? extends AbstractDicomNode> comboBox, Type type) {
    XMLStreamWriter writer = null;
    XMLOutputFactory factory = XMLOutputFactory.newInstance();
    final BundleContext context =
        FrameworkUtil.getBundle(AbstractDicomNode.class).getBundleContext();
    try {
      writer =
          factory.createXMLStreamWriter(
              new FileOutputStream(
                  new File(BundlePreferences.getDataFolder(context), type.getFilename())),
              "UTF-8"); // NON-NLS

      writer.writeStartDocument("UTF-8", "1.0"); // NON-NLS
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

  private static void loadDicomNodes(
      List<AbstractDicomNode> list,
      File prefs,
      Type type,
      boolean local,
      UsageType usage,
      WebType webType) {
    if (prefs.canRead()) {
      XMLStreamReader xmler = null;
      try {
        XMLInputFactory factory = XMLInputFactory.newInstance();
        // disable external entities for security
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, Boolean.FALSE);
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, Boolean.FALSE);
        xmler = factory.createXMLStreamReader(new FileInputStream(prefs));
        int eventType;
        while (xmler.hasNext()) {
          eventType = xmler.next();
          if (eventType == XMLStreamConstants.START_ELEMENT) {
            readDicomNodes(xmler, list, type, local, usage, webType);
          }
        }
      } catch (Exception e) {
        LOGGER.error("Error on reading DICOM node file", e);
      } finally {
        FileUtil.safeClose(xmler);
      }
    }
  }

  private static void readDicomNodes(
      XMLStreamReader xmler,
      List<AbstractDicomNode> list,
      Type type,
      boolean local,
      UsageType usage,
      WebType webType)
      throws XMLStreamException {
    String key = xmler.getName().getLocalPart();
    if (T_NODES.equals(key)) {
      while (xmler.hasNext()) {
        int eventType = xmler.next();
        if (eventType == XMLStreamConstants.START_ELEMENT) {
          readDicomNode(xmler, list, type, local, usage, webType);
        }
      }
    }
  }

  private static void readDicomNode(
      XMLStreamReader xmler,
      List<AbstractDicomNode> list,
      Type type,
      boolean local,
      UsageType usage,
      WebType webType) {
    String key = xmler.getName().getLocalPart();
    if (T_NODE.equals(key)) {
      try {
        Type t = Type.valueOf(xmler.getAttributeValue(null, T_TYPE));
        if (type != t) {
          return;
        }

        if (usage != UsageType.BOTH) {
          UsageType u = UsageType.valueOf(xmler.getAttributeValue(null, T_USAGE_TYPE));
          if (u != UsageType.BOTH && u != usage) {
            return;
          }
        }

        AbstractDicomNode node;
        if (AbstractDicomNode.Type.WEB == type) {
          WebType wt = WebType.valueOf(xmler.getAttributeValue(null, DicomWebNode.T_WEB_TYPE));
          if (webType != null) {
            if (webType == WebType.WADO && webType != wt
                || webType != wt && wt != WebType.DICOMWEB) {
              return;
            }
          }
          node = DicomWebNode.buildDicomWebNode(xmler);
        } else if (AbstractDicomNode.Type.PRINTER == type) {
          node = DicomPrintNode.buildDicomPrintNode(xmler);
        } else {
          node = DefaultDicomNode.buildDicomNodeEx(xmler);
        }

        node.setLocal(local);
        node.setType(t);

        list.add(node);
      } catch (Exception e) {
        LOGGER.error("Cannot read DicomNode", e);
      }
    }
  }

  public static void addNodeActionPerformed(
      JComboBox<? extends AbstractDicomNode> comboBox, Type type) {
    JDialog dialog;
    if (Type.WEB == type) {
      dialog =
          new DicomWebNodeDialog(
              SwingUtilities.getWindowAncestor(comboBox),
              Type.WEB.toString(),
              null,
              (JComboBox<DicomWebNode>) comboBox);
    } else {
      dialog =
          new DicomNodeDialog(
              SwingUtilities.getWindowAncestor(comboBox),
              Type.DICOM.toString(),
              null,
              (JComboBox<DefaultDicomNode>) comboBox,
              type);
    }
    GuiUtils.showCenterScreen(dialog, comboBox);
  }

  public static void editNodeActionPerformed(JComboBox<? extends AbstractDicomNode> comboBox) {
    AbstractDicomNode node = (AbstractDicomNode) comboBox.getSelectedItem();
    if (node != null) {
      if (node.isLocal()) {
        Type type = node.getType();
        JDialog dialog;
        if (Type.WEB == type) {
          dialog =
              new DicomWebNodeDialog(
                  SwingUtilities.getWindowAncestor(comboBox),
                  Type.WEB.toString(),
                  (DicomWebNode) node,
                  (JComboBox<DicomWebNode>) comboBox);
        } else {
          dialog =
              new DicomNodeDialog(
                  SwingUtilities.getWindowAncestor(comboBox),
                  Type.DICOM.toString(),
                  (DefaultDicomNode) node,
                  (JComboBox<DefaultDicomNode>) comboBox,
                  type);
        }
        GuiUtils.showCenterScreen(dialog, comboBox);
      } else {
        JOptionPane.showMessageDialog(
            comboBox,
            Messages.getString("AbstractDicomNode.only_usr_cr_msg"),
            Messages.getString("DicomPrintDialog.error"),
            JOptionPane.ERROR_MESSAGE);
      }
    }
  }

  public static void deleteNodeActionPerformed(JComboBox<? extends AbstractDicomNode> comboBox) {
    int index = comboBox.getSelectedIndex();
    if (index >= 0) {
      AbstractDicomNode node = comboBox.getItemAt(index);
      if (node.isLocal()) {
        int response =
            JOptionPane.showConfirmDialog(
                comboBox,
                String.format(Messages.getString("AbstractDicomNode.delete_msg"), node),
                Type.DICOM.toString(),
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE);

        if (response == 0) {
          comboBox.removeItemAt(index);
          AbstractDicomNode.saveDicomNodes(comboBox, node.getType());
        }
      } else {
        JOptionPane.showMessageDialog(
            comboBox,
            Messages.getString("AbstractDicomNode.only_usr_cr_msg"),
            Messages.getString("DicomPrintDialog.error"),
            JOptionPane.ERROR_MESSAGE);
      }
    }
  }

  public static void addTooltipToComboList(final JComboBox<? extends AbstractDicomNode> combo) {
    Object comp = combo.getUI().getAccessibleChild(combo, 0);
    if (comp instanceof final BasicComboPopup popup) {
      popup
          .getList()
          .getSelectionModel()
          .addListSelectionListener(
              e -> {
                if (!e.getValueIsAdjusting()) {
                  ListSelectionModel model = (ListSelectionModel) e.getSource();
                  int first = model.getMinSelectionIndex();
                  if (first >= 0) {
                    AbstractDicomNode item = combo.getItemAt(first);
                    ((JComponent) combo.getRenderer()).setToolTipText(item.getToolTips());
                  }
                }
              });
    }
  }
}
