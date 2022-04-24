/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.explorer.print;

import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Window;
import java.util.List;
import javax.swing.Box;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.WindowConstants;
import net.miginfocom.swing.MigLayout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.ui.editor.image.ImageViewerEventManager;
import org.weasis.core.ui.editor.image.ImageViewerPlugin;
import org.weasis.core.ui.editor.image.ViewCanvas;
import org.weasis.core.ui.util.ExportLayout;
import org.weasis.core.ui.util.PrintOptions;
import org.weasis.core.util.StringUtil;
import org.weasis.dicom.explorer.Messages;
import org.weasis.dicom.explorer.pref.node.AbstractDicomNode;
import org.weasis.dicom.explorer.pref.node.DicomPrintNode;

/**
 * @author Marcelo Porto (marcelo@animati.com.br)
 * @author Nicolas Roduit
 */
public class DicomPrintDialog<I extends ImageElement> extends JDialog {
  private static final Logger LOGGER = LoggerFactory.getLogger(DicomPrintDialog.class);

  public enum FilmSize {
    IN8X10("8INX10IN", 8, 10), // NON-NLS
    IN8_5X11("8_5INX11IN", 8.5, 11), // NON-NLS
    IN10X12("10INX12IN", 10, 12), // NON-NLS
    IN10X14(
        "10INX14IN", // NON-NLS
        10,
        14),
    IN11X14("11INX14IN", 11, 14), // NON-NLS
    IN11X17("11INX17IN", 11, 17), // NON-NLS
    IN14X14("14INX14IN", 14, 14), // NON-NLS
    IN14X17(
        "14INX17IN", // NON-NLS
        14,
        17),
    CM24X24("24CMX24CM", convertMM2Inch(240), convertMM2Inch(240)), // NON-NLS
    CM24X30(
        "24CMX30CM", // NON-NLS
        convertMM2Inch(240), // NON-NLS
        convertMM2Inch(300)),
    A4("A4", convertMM2Inch(210), convertMM2Inch(297)), // NON-NLS
    A3("A3", convertMM2Inch(297), convertMM2Inch(420)); // NON-NLS

    private final String name;
    private final double width;
    private final double height;

    FilmSize(String name, double width, double height) {
      this.name = name;
      this.width = width;
      this.height = height;
    }

    @Override
    public String toString() {
      return name;
    }

    public int getWidth(PrintOptions.DotPerInches dpi) {
      return getLengthFromInch(width, dpi);
    }

    public int getHeight(PrintOptions.DotPerInches dpi) {
      return getLengthFromInch(height, dpi);
    }

    public static int getLengthFromInch(double size, PrintOptions.DotPerInches dpi) {
      PrintOptions.DotPerInches dpi2 = dpi == null ? PrintOptions.DotPerInches.DPI_150 : dpi;
      double val = size * dpi2.getDpi();
      return (int) (val + 0.5);
    }

    public static double convertMM2Inch(int size) {
      return size / 25.4;
    }

    public static FilmSize getInstance(String val, FilmSize defaultValue) {
      if (StringUtil.hasText(val)) {
        try {
          return FilmSize.valueOf(val);
        } catch (Exception e) {
          LOGGER.error("Cannot find FilmSize: {}", val, e);
        }
      }
      return defaultValue;
    }
  }

  private DicomPrintOptionPane optionPane;
  private JComboBox<AbstractDicomNode> printersComboBox;
  private final ImageViewerEventManager<I> eventManager;

  /** Creates new form DicomPrintDialog */
  public DicomPrintDialog(Window parent, String title, ImageViewerEventManager<I> eventManager) {
    super(parent, title, ModalityType.APPLICATION_MODAL);
    setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

    this.eventManager = eventManager;
    initComponents();
    applyOptionsFromSelected();
    pack();
  }

  private void initComponents() {
    JPanel panel = new JPanel();
    panel.setLayout(new MigLayout("insets 10lp 15lp 10lp 15lp", "[grow ,fill][grow 0]")); // NON-NLS

    JPanel printersCfg = GuiUtils.getFlowLayoutPanel(FlowLayout.LEADING, 5, 5);
    printersCfg.setBorder(
        GuiUtils.getTitledBorder(Messages.getString("DicomPrintDialog.print_title")));

    JLabel printerLabel =
        new JLabel(Messages.getString("DicomPrintDialog.printer") + StringUtil.COLON);
    printersCfg.add(printerLabel);

    printersComboBox = new JComboBox<>();
    printersCfg.add(printersComboBox);

    printersComboBox.setModel(new DefaultComboBoxModel<>());

    AbstractDicomNode.loadDicomNodes(printersComboBox, AbstractDicomNode.Type.PRINTER);
    GuiUtils.setPreferredWidth(printersComboBox, 200, 185);
    AbstractDicomNode.addTooltipToComboList(printersComboBox);

    Component horizontalStrut = Box.createHorizontalStrut(20);
    printersCfg.add(horizontalStrut);
    JButton addPrinterButton = new JButton(Messages.getString("DicomPrintDialog.add"));
    printersCfg.add(addPrinterButton);

    JButton editButton = new JButton(Messages.getString("DicomPrintDialog.edit"));
    printersCfg.add(editButton);

    JButton deleteButton = new JButton(Messages.getString("DicomPrintDialog.delete"));
    printersCfg.add(deleteButton);

    deleteButton.addActionListener(
        evt -> {
          AbstractDicomNode.deleteNodeActionPerformed(printersComboBox);
          applyOptionsFromSelected();
        });
    editButton.addActionListener(
        evt -> {
          AbstractDicomNode.editNodeActionPerformed(printersComboBox);
          applyOptionsFromSelected();
        });
    addPrinterButton.addActionListener(
        evt -> {
          AbstractDicomNode.addNodeActionPerformed(
              printersComboBox, AbstractDicomNode.Type.PRINTER);
          applyOptionsFromSelected();
        });
    printersComboBox.addActionListener(evt -> applyOptionsFromSelected());

    panel.add(printersCfg, "newline, spanx"); // NON-NLS

    optionPane = new DicomPrintOptionPane();
    panel.add(optionPane, "newline, gaptop 10, spanx"); // NON-NLS

    JButton printButton = new JButton(Messages.getString("DicomPrintDialog.print"));
    printButton.addActionListener(this::printButtonActionPerformed);
    getRootPane().setDefaultButton(printButton);
    JButton cancelButton = new JButton(Messages.getString("DicomPrintDialog.cancel"));
    cancelButton.addActionListener(evt -> doClose());

    panel.add(printButton, "newline, skip, growx 0, alignx trailing"); // NON-NLS
    panel.add(cancelButton, "gap 15lp 0lp 10lp 10lp"); // NON-NLS
    setContentPane(panel);
  }

  private void printButtonActionPerformed(java.awt.event.ActionEvent evt) {

    DicomPrintNode node = (DicomPrintNode) printersComboBox.getSelectedItem();
    if (node == null) {
      return;
    }
    // Get current options from UI
    DicomPrintOptions options = new DicomPrintOptions();
    optionPane.saveOptions(options);

    DicomPrint dicomPrint = new DicomPrint(node, options);
    ImageViewerPlugin<I> container = eventManager.getSelectedView2dContainer();

    List<ViewCanvas<I>> views = container.getImagePanels();
    if (views.isEmpty()) {
      JOptionPane.showMessageDialog(
          this, Messages.getString("DicomPrintDialog.no_print"), null, JOptionPane.ERROR_MESSAGE);
      doClose();
      return;
    }

    doClose();

    ExportLayout<I> layout;
    if (optionPane.checkboxSelectedView.isSelected()) {
      layout = new ExportLayout<>(eventManager.getSelectedViewPane());
    } else {
      layout = new ExportLayout<>(container.getLayoutModel());
    }

    try {
      dicomPrint.printImage(dicomPrint.printImage(layout));
    } catch (Exception e) {
      LOGGER.error("DICOM Print Service", e);
      JOptionPane.showMessageDialog(
          this,
          Messages.getString("DicomPrintDialog.error_print"),
          Messages.getString("DicomPrintDialog.error"),
          JOptionPane.ERROR_MESSAGE);
    } finally {
      layout.dispose();
    }
  }

  private void doClose() {
    dispose();
  }

  private void applyOptionsFromSelected() {
    Object selectedItem = printersComboBox.getSelectedItem();
    if (selectedItem instanceof DicomPrintNode printNode) {
      optionPane.applyOptions(printNode.getPrintOptions());
    }
  }
}
