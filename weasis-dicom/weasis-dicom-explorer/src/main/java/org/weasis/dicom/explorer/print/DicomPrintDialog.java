/*******************************************************************************
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/

package org.weasis.dicom.explorer.print;

import java.awt.BorderLayout;
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
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.gui.util.JMVUtils;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.api.util.StringUtil;
import org.weasis.core.ui.editor.image.ImageViewerEventManager;
import org.weasis.core.ui.editor.image.ImageViewerPlugin;
import org.weasis.core.ui.editor.image.ViewCanvas;
import org.weasis.core.ui.util.ExportLayout;
import org.weasis.core.ui.util.PrintOptions;
import org.weasis.dicom.explorer.Messages;
import org.weasis.dicom.explorer.pref.node.AbstractDicomNode;
import org.weasis.dicom.explorer.pref.node.DicomPrintNode;

/**
 *
 * @author Marcelo Porto (marcelo@animati.com.br)
 * @author Nicolas Roduit
 */
public class DicomPrintDialog<I extends ImageElement> extends JDialog {
    private static final Logger LOGGER = LoggerFactory.getLogger(DicomPrintDialog.class);

    public enum FilmSize {
        IN8X10("8INX10IN", 8, 10), IN8_5X11("8_5INX11IN", 8.5, 11), IN10X12("10INX12IN", 10, 12), //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        IN10X14("10INX14IN", //$NON-NLS-1$
                        10, 14),
        IN11X14("11INX14IN", 11, 14), IN11X17("11INX17IN", 11, 17), IN14X14("14INX14IN", 14, 14), //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        IN14X17("14INX17IN", //$NON-NLS-1$
                        14, 17),
        CM24X24("24CMX24CM", convertMM2Inch(240), convertMM2Inch(240)), //$NON-NLS-1$
        CM24X30("24CMX30CM", convertMM2Inch(240), //$NON-NLS-1$
                        convertMM2Inch(300)),
        A4("A4", convertMM2Inch(210), convertMM2Inch(297)), //$NON-NLS-1$
        A3("A3", convertMM2Inch(297), convertMM2Inch(420)); //$NON-NLS-1$

        private final String name;
        private final double width;
        private final double height;

        private FilmSize(String name, double width, double height) {
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
                    LOGGER.error("Cannot find FilmSize: {}", val, e); //$NON-NLS-1$
                }
            }
            return defaultValue;
        }

    }

    private DicomPrintOptionPane optionPane;
    private JButton addPrinterButton;
    private JButton cancelButton;
    private JButton deleteButton;
    private JButton editButton;
    private JButton printButton;
    private JLabel printerLabel;
    private JComboBox<AbstractDicomNode> printersComboBox;
    private ImageViewerEventManager<I> eventManager;
    private Component horizontalStrut;
    private JPanel footPanel;

    /** Creates new form DicomPrintDialog */
    public DicomPrintDialog(Window parent, String title, ImageViewerEventManager<I> eventManager) {
        super(parent, title, ModalityType.APPLICATION_MODAL);
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

        this.eventManager = eventManager;
        initComponents();
        applyOptionsfromSelected();
        pack();
    }

    private void initComponents() {
        final JPanel rootPane = new JPanel();
        rootPane.setBorder(new EmptyBorder(10, 15, 10, 15));
        this.setContentPane(rootPane);

        final JPanel printersCfg = new JPanel();
        printersCfg.setBorder(new TitledBorder(null, Messages.getString("DicomPrintDialog.print_title"), //$NON-NLS-1$
            TitledBorder.LEADING, TitledBorder.TOP, null, null));
        FlowLayout flPrintersCfg = new FlowLayout();
        flPrintersCfg.setAlignment(FlowLayout.LEFT);
        printersCfg.setLayout(flPrintersCfg);

        rootPane.setLayout(new BorderLayout(0, 0));
        this.getContentPane().add(printersCfg, BorderLayout.NORTH);

        printerLabel = new JLabel();
        printersCfg.add(printerLabel);

        printerLabel.setText(Messages.getString("DicomPrintDialog.printer") + StringUtil.COLON); //$NON-NLS-1$
        printersComboBox = new JComboBox<>();
        printersCfg.add(printersComboBox);

        printersComboBox.setModel(new DefaultComboBoxModel<AbstractDicomNode>());

        AbstractDicomNode.loadDicomNodes(printersComboBox, AbstractDicomNode.Type.PRINTER);
        JMVUtils.setPreferredWidth(printersComboBox, 185, 185);
        AbstractDicomNode.addTooltipToComboList(printersComboBox);

        horizontalStrut = Box.createHorizontalStrut(20);
        printersCfg.add(horizontalStrut);
        addPrinterButton = new JButton(Messages.getString("DicomPrintDialog.add")); //$NON-NLS-1$
        printersCfg.add(addPrinterButton);

        editButton = new JButton();
        printersCfg.add(editButton);

        editButton.setText(Messages.getString("DicomPrintDialog.edit")); //$NON-NLS-1$
        deleteButton = new JButton();
        printersCfg.add(deleteButton);

        deleteButton.setText(Messages.getString("DicomPrintDialog.delete")); //$NON-NLS-1$
        deleteButton.addActionListener(evt -> {
            AbstractDicomNode.deleteNodeActionPerformed(printersComboBox);
            applyOptionsfromSelected();
        });
        editButton.addActionListener(evt -> {
            AbstractDicomNode.editNodeActionPerformed(printersComboBox);
            applyOptionsfromSelected();
        });
        addPrinterButton.addActionListener(evt -> {
            AbstractDicomNode.addNodeActionPerformed(printersComboBox, AbstractDicomNode.Type.PRINTER);
            applyOptionsfromSelected();
        });
        printersComboBox.addActionListener(evt -> applyOptionsfromSelected());

        optionPane = new DicomPrintOptionPane();
        this.getContentPane().add(optionPane, BorderLayout.CENTER);

        footPanel = new JPanel();
        FlowLayout flowLayout = (FlowLayout) footPanel.getLayout();
        flowLayout.setVgap(15);
        flowLayout.setAlignment(FlowLayout.RIGHT);
        flowLayout.setHgap(20);
        getContentPane().add(footPanel, BorderLayout.SOUTH);
        printButton = new JButton();
        footPanel.add(printButton);

        printButton.setText(Messages.getString("DicomPrintDialog.print")); //$NON-NLS-1$
        printButton.addActionListener(this::printButtonActionPerformed);

        getRootPane().setDefaultButton(printButton);
        cancelButton = new JButton(Messages.getString("DicomPrintDialog.cancel")); //$NON-NLS-1$
        footPanel.add(cancelButton);
        cancelButton.addActionListener(evt -> doClose());
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
            JOptionPane.showMessageDialog(this, Messages.getString("DicomPrintDialog.no_print"), //$NON-NLS-1$
                null, JOptionPane.ERROR_MESSAGE);
            doClose();
            return;
        }

        doClose();

        ExportLayout<I> layout;
        if (optionPane.chckbxSelctedView.isSelected()) {
            layout = new ExportLayout<>(eventManager.getSelectedViewPane());
        } else {
            layout = new ExportLayout<>(container.getLayoutModel());
        }

        try {
            dicomPrint.printImage(dicomPrint.printImage(layout));
        } catch (Exception e) {
            LOGGER.error("DICOM Print Service", e); //$NON-NLS-1$
            JOptionPane.showMessageDialog(this, Messages.getString("DicomPrintDialog.error_print"), //$NON-NLS-1$
                Messages.getString("DicomPrintDialog.error"), JOptionPane.ERROR_MESSAGE); //$NON-NLS-1$
        } finally {
            layout.dispose();
        }
    }

    private void doClose() {
        dispose();
    }

    private void applyOptionsfromSelected() {
        Object selectedItem = printersComboBox.getSelectedItem();
        if (selectedItem instanceof DicomPrintNode) {
            optionPane.applyOptions(((DicomPrintNode) selectedItem).getPrintOptions());
        }
    }
}
