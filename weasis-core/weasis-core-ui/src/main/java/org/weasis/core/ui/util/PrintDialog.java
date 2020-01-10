/*******************************************************************************
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.weasis.core.ui.util;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.util.List;

import javax.swing.DefaultComboBoxModel;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;

import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.api.util.StringUtil;
import org.weasis.core.ui.Messages;
import org.weasis.core.ui.editor.image.ImageViewerEventManager;
import org.weasis.core.ui.editor.image.ImageViewerPlugin;
import org.weasis.core.ui.editor.image.ViewCanvas;

/**
 *
 * @author Marcelo Porto (marcelo@animati.com.br)
 * @author Nicolas Roduit
 *  
 */
@SuppressWarnings("serial")
public class PrintDialog<I extends ImageElement> extends javax.swing.JDialog {

    private javax.swing.JCheckBox annotationsCheckBox;
    private javax.swing.JButton cancelButton;
    private javax.swing.JComboBox<String> positionComboBox;
    private javax.swing.JLabel positionLabel;
    private javax.swing.JButton printButton;
    private JLabel label;
    private JCheckBox chckbxSelectedView;
    private JComboBox<PrintOptions.DotPerInches> comboBoxDPI;
    private ImageViewerEventManager<I> eventManager;

    /** Creates new form PrintDialog */
    public PrintDialog(Window parent, String title, ImageViewerEventManager<I> eventManager) {
        super(parent, title, ModalityType.APPLICATION_MODAL);
        this.eventManager = eventManager;
        boolean layout = eventManager.getSelectedView2dContainer().getLayoutModel().getConstraints().size() > 1;
        initComponents(layout);
        pack();
    }

    private void initComponents(boolean layout) {

        GridBagLayout gridBagLayout = new GridBagLayout();
        getContentPane().setLayout(gridBagLayout);
        positionLabel = new javax.swing.JLabel();

        positionLabel.setText(Messages.getString("PrintDialog.pos") + StringUtil.COLON); //$NON-NLS-1$
        GridBagConstraints gbcPositionLabel = new GridBagConstraints();
        gbcPositionLabel.anchor = GridBagConstraints.EAST;
        gbcPositionLabel.insets = new Insets(15, 20, 10, 5);
        gbcPositionLabel.gridx = 0;
        gbcPositionLabel.gridy = 0;
        getContentPane().add(positionLabel, gbcPositionLabel);
        positionComboBox = new javax.swing.JComboBox<>();

        positionComboBox.setModel(new javax.swing.DefaultComboBoxModel<>(
            new String[] { Messages.getString("PrintDialog.center"), Messages.getString("PrintDialog.top") })); //$NON-NLS-1$ //$NON-NLS-2$
        GridBagConstraints gbcPositionComboBox = new GridBagConstraints();
        gbcPositionComboBox.anchor = GridBagConstraints.WEST;
        gbcPositionComboBox.insets = new Insets(15, 0, 10, 5);
        gbcPositionComboBox.gridx = 1;
        gbcPositionComboBox.gridy = 0;
        getContentPane().add(positionComboBox, gbcPositionComboBox);

        label = new JLabel();
        label.setText(Messages.getString("PrintDialog.dpi") + StringUtil.COLON); //$NON-NLS-1$
        GridBagConstraints gbcLabel = new GridBagConstraints();
        gbcLabel.anchor = GridBagConstraints.EAST;
        gbcLabel.insets = new Insets(0, 20, 10, 5);
        gbcLabel.gridx = 0;
        gbcLabel.gridy = 1;
        getContentPane().add(label, gbcLabel);

        comboBoxDPI = new JComboBox<>();
        GridBagConstraints gbcComboBoxDPI = new GridBagConstraints();
        gbcComboBoxDPI.anchor = GridBagConstraints.NORTHWEST;
        gbcComboBoxDPI.insets = new Insets(0, 0, 10, 5);
        gbcComboBoxDPI.gridx = 1;
        gbcComboBoxDPI.gridy = 1;
        comboBoxDPI.setModel(new DefaultComboBoxModel<>(PrintOptions.DotPerInches.values()));
        comboBoxDPI.setSelectedItem(PrintOptions.DotPerInches.DPI_150);
        getContentPane().add(comboBoxDPI, gbcComboBoxDPI);

        annotationsCheckBox = new javax.swing.JCheckBox();
        annotationsCheckBox.setText(Messages.getString("PrintDialog.annotate")); //$NON-NLS-1$
        annotationsCheckBox.setSelected(true);
        GridBagConstraints gbcAnnotationsCheckBox = new GridBagConstraints();
        gbcAnnotationsCheckBox.anchor = GridBagConstraints.WEST;
        gbcAnnotationsCheckBox.insets = new Insets(0, 17, 10, 0);
        gbcAnnotationsCheckBox.gridwidth = 3;
        gbcAnnotationsCheckBox.gridx = 0;
        gbcAnnotationsCheckBox.gridy = 2;
        getContentPane().add(annotationsCheckBox, gbcAnnotationsCheckBox);

        cancelButton = new javax.swing.JButton();

        cancelButton.setText(Messages.getString("PrintDialog.cancel")); //$NON-NLS-1$
        cancelButton.addActionListener(e -> dispose());

        printButton = new javax.swing.JButton();

        printButton.setText(Messages.getString("PrintDialog.print")); //$NON-NLS-1$
        printButton.addActionListener(e -> printAction());

        if (layout) {
            chckbxSelectedView = new JCheckBox(Messages.getString("PrintDialog.selected_view")); //$NON-NLS-1$
            GridBagConstraints gbcChckbxNewCheckBox = new GridBagConstraints();
            gbcChckbxNewCheckBox.anchor = GridBagConstraints.WEST;
            gbcChckbxNewCheckBox.gridwidth = 3;
            gbcChckbxNewCheckBox.insets = new Insets(0, 15, 10, 5);
            gbcChckbxNewCheckBox.gridx = 0;
            gbcChckbxNewCheckBox.gridy = 3;
            getContentPane().add(chckbxSelectedView, gbcChckbxNewCheckBox);
        }

        getRootPane().setDefaultButton(printButton);
        GridBagConstraints gbcPrintButton = new GridBagConstraints();
        gbcPrintButton.anchor = GridBagConstraints.EAST;
        gbcPrintButton.insets = new Insets(25, 0, 15, 5);
        gbcPrintButton.gridx = 1;
        gbcPrintButton.gridy = 4;
        getContentPane().add(printButton, gbcPrintButton);
        GridBagConstraints gbcCancelButton = new GridBagConstraints();
        gbcCancelButton.insets = new Insets(25, 10, 15, 15);
        gbcCancelButton.anchor = GridBagConstraints.NORTHWEST;
        gbcCancelButton.gridx = 2;
        gbcCancelButton.gridy = 4;
        getContentPane().add(cancelButton, gbcCancelButton);

        pack();
    }

    private void printAction() {
        PrintOptions printOptions = new PrintOptions();
        printOptions.setShowingAnnotations(annotationsCheckBox.isSelected());
        printOptions.setDpi((PrintOptions.DotPerInches) comboBoxDPI.getSelectedItem());
        if (positionComboBox.getSelectedItem().equals(Messages.getString("PrintDialog.center"))) { //$NON-NLS-1$
            printOptions.setCenter(true);
        } else {
            printOptions.setCenter(false);
        }

        ImageViewerPlugin<I> container = eventManager.getSelectedView2dContainer();

        List<ViewCanvas<I>> views = container.getImagePanels();
        if (views.isEmpty()) {
            JOptionPane.showMessageDialog(this, Messages.getString("PrintDialog.no_print"), null, //$NON-NLS-1$
                JOptionPane.ERROR_MESSAGE);
            dispose();
            return;
        }
        dispose();

        ExportLayout<I> layout;
        if (chckbxSelectedView != null && !chckbxSelectedView.isSelected()) {
            // Several views
            layout = new ExportLayout<>(container.getLayoutModel());
        } else {
            // One View
            layout = new ExportLayout<>(eventManager.getSelectedViewPane());
        }

        ImagePrint print = new ImagePrint(layout, printOptions);
        print.print();
        layout.dispose();

    }

}
