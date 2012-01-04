/*******************************************************************************
 * Copyright (c) 2011 Weasis Team.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     Marcelo Porto - initial API and implementation Animati Sistemas de Informática Ltda. (http://www.animati.com.br)
 *     Nicolas Roduit
 *     
 ******************************************************************************/

package org.weasis.core.ui.util;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JCheckBox;
import javax.swing.JOptionPane;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;

import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.ui.editor.image.ExportImage;
import org.weasis.core.ui.editor.image.ImageViewerEventManager;
import org.weasis.core.ui.editor.image.ImageViewerPlugin;
import org.weasis.core.ui.util.PrintOptions.SCALE;

/**
 * 
 * @author Marcelo Porto (marcelo@animati.com.br)
 */
public class PrintDialog extends javax.swing.JDialog {

    /** A return status code - returned if Cancel button has been pressed */
    public static final int RET_CANCEL = 0;
    /** A return status code - returned if OK button has been pressed */
    public static final int RET_OK = 1;
    private javax.swing.JCheckBox annotationsCheckBox;
    private javax.swing.JButton cancelButton;
    private javax.swing.JLabel customImageSizeLabel;
    private javax.swing.JComboBox imageSizeComboBox;
    private javax.swing.JLabel imageSizeLabel;
    private javax.swing.JComboBox positionComboBox;
    private javax.swing.JLabel positionLabel;
    private javax.swing.JButton printButton;

    private int returnStatus = RET_CANCEL;
    private ImageViewerEventManager eventManager;
    private JCheckBox chckbxNewCheckBox;
    private JSpinner spinner;

    /** Creates new form PrintDialog */
    public PrintDialog(Window parent, String title, ImageViewerEventManager eventManager) {
        super(parent, ModalityType.APPLICATION_MODAL);
        this.eventManager = eventManager;
        boolean layout = eventManager.getSelectedView2dContainer().getImagePanels().size() > 1;
        initComponents(layout);
        imageSizeComboBox();
        setOnlySelectedView(layout);
        pack();
    }

    private void initComponents(boolean layout) {

        GridBagLayout gridBagLayout = new GridBagLayout();
        getContentPane().setLayout(gridBagLayout);
        imageSizeLabel = new javax.swing.JLabel();

        imageSizeLabel.setText("Image size:");
        GridBagConstraints gbc_imageSizeLabel = new GridBagConstraints();
        gbc_imageSizeLabel.anchor = GridBagConstraints.EAST;
        gbc_imageSizeLabel.insets = new Insets(15, 15, 10, 5);
        gbc_imageSizeLabel.gridx = 0;
        gbc_imageSizeLabel.gridy = 0;
        getContentPane().add(imageSizeLabel, gbc_imageSizeLabel);
        imageSizeComboBox = new javax.swing.JComboBox(SCALE.values());

        imageSizeComboBox.addActionListener(new java.awt.event.ActionListener() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                imageSizeComboBox();
            }
        });
        GridBagConstraints gbc_imageSizeComboBox = new GridBagConstraints();
        gbc_imageSizeComboBox.anchor = GridBagConstraints.WEST;
        gbc_imageSizeComboBox.insets = new Insets(15, 0, 10, 5);
        gbc_imageSizeComboBox.gridx = 1;
        gbc_imageSizeComboBox.gridy = 0;
        getContentPane().add(imageSizeComboBox, gbc_imageSizeComboBox);
        customImageSizeLabel = new javax.swing.JLabel();

        customImageSizeLabel.setText("Custom image size (%):");
        GridBagConstraints gbc_customImageSizeLabel = new GridBagConstraints();
        gbc_customImageSizeLabel.anchor = GridBagConstraints.EAST;
        gbc_customImageSizeLabel.insets = new Insets(0, 15, 10, 5);
        gbc_customImageSizeLabel.gridx = 0;
        gbc_customImageSizeLabel.gridy = 1;
        getContentPane().add(customImageSizeLabel, gbc_customImageSizeLabel);

        spinner = new JSpinner();
        spinner.setModel(new SpinnerNumberModel(100, 5, 200, 1));
        GridBagConstraints gbc_spinner = new GridBagConstraints();
        gbc_spinner.anchor = GridBagConstraints.WEST;
        gbc_spinner.insets = new Insets(0, 0, 10, 5);
        gbc_spinner.gridx = 1;
        gbc_spinner.gridy = 1;
        getContentPane().add(spinner, gbc_spinner);
        positionLabel = new javax.swing.JLabel();

        positionLabel.setText("Image position:");
        GridBagConstraints gbc_positionLabel = new GridBagConstraints();
        gbc_positionLabel.anchor = GridBagConstraints.EAST;
        gbc_positionLabel.insets = new Insets(0, 15, 10, 5);
        gbc_positionLabel.gridx = 0;
        gbc_positionLabel.gridy = 2;
        getContentPane().add(positionLabel, gbc_positionLabel);
        positionComboBox = new javax.swing.JComboBox();

        positionComboBox.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "Centralized", "Top-left" }));
        GridBagConstraints gbc_positionComboBox = new GridBagConstraints();
        gbc_positionComboBox.anchor = GridBagConstraints.WEST;
        gbc_positionComboBox.insets = new Insets(0, 0, 10, 5);
        gbc_positionComboBox.gridx = 1;
        gbc_positionComboBox.gridy = 2;
        getContentPane().add(positionComboBox, gbc_positionComboBox);
        annotationsCheckBox = new javax.swing.JCheckBox();

        annotationsCheckBox.setText("Print image with annotations");
        annotationsCheckBox.setSelected(true);
        GridBagConstraints gbc_annotationsCheckBox = new GridBagConstraints();
        gbc_annotationsCheckBox.anchor = GridBagConstraints.WEST;
        gbc_annotationsCheckBox.insets = new Insets(0, 15, 10, 0);
        gbc_annotationsCheckBox.gridwidth = 3;
        gbc_annotationsCheckBox.gridx = 0;
        gbc_annotationsCheckBox.gridy = 3;
        getContentPane().add(annotationsCheckBox, gbc_annotationsCheckBox);
        cancelButton = new javax.swing.JButton();

        cancelButton.setText("Cancel");
        cancelButton.addActionListener(new java.awt.event.ActionListener() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                doClose();
            }
        });

        printButton = new javax.swing.JButton();

        printButton.setText("Print");
        printButton.addActionListener(new java.awt.event.ActionListener() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                printAction();
            }
        });

        if (layout) {
            chckbxNewCheckBox = new JCheckBox("Print only the selected view");
            chckbxNewCheckBox.addActionListener(new ActionListener() {

                @Override
                public void actionPerformed(ActionEvent e) {
                    setOnlySelectedView(!chckbxNewCheckBox.isSelected());
                }
            });
            GridBagConstraints gbc_chckbxNewCheckBox = new GridBagConstraints();
            gbc_chckbxNewCheckBox.anchor = GridBagConstraints.WEST;
            gbc_chckbxNewCheckBox.gridwidth = 3;
            gbc_chckbxNewCheckBox.insets = new Insets(0, 15, 10, 5);
            gbc_chckbxNewCheckBox.gridx = 0;
            gbc_chckbxNewCheckBox.gridy = 4;
            getContentPane().add(chckbxNewCheckBox, gbc_chckbxNewCheckBox);
        }

        getRootPane().setDefaultButton(printButton);
        GridBagConstraints gbc_printButton = new GridBagConstraints();
        gbc_printButton.anchor = GridBagConstraints.EAST;
        gbc_printButton.insets = new Insets(25, 0, 15, 5);
        gbc_printButton.gridx = 1;
        gbc_printButton.gridy = 5;
        getContentPane().add(printButton, gbc_printButton);
        GridBagConstraints gbc_cancelButton = new GridBagConstraints();
        gbc_cancelButton.insets = new Insets(25, 10, 15, 15);
        gbc_cancelButton.anchor = GridBagConstraints.NORTHWEST;
        gbc_cancelButton.gridx = 2;
        gbc_cancelButton.gridy = 5;
        getContentPane().add(cancelButton, gbc_cancelButton);

        pack();
    }

    private void setOnlySelectedView(boolean layout) {
        if (layout) {
            imageSizeComboBox.setSelectedItem(SCALE.ShrinkToPage);
            imageSizeComboBox();
        }
        imageSizeLabel.setEnabled(!layout);
        imageSizeComboBox.setEnabled(!layout);
    }

    private void imageSizeComboBox() {
        if (imageSizeComboBox.getSelectedItem() != SCALE.Custom) {
            customImageSizeLabel.setEnabled(false);
            spinner.setEnabled(false);
        } else {
            customImageSizeLabel.setEnabled(true);
            spinner.setEnabled(true);
        }
    }

    private void printAction() {
        double imageScale = (Integer) spinner.getValue() / 100.0;
        PrintOptions printOptions = new PrintOptions(annotationsCheckBox.isSelected(), imageScale);
        printOptions.setScale((SCALE) imageSizeComboBox.getSelectedItem());
        printOptions.setHasAnnotations(annotationsCheckBox.isSelected());
        if (positionComboBox.getSelectedItem().equals("Centralized")) {
            printOptions.setCenter(true);
        } else {
            printOptions.setCenter(false);
        }

        ImageViewerPlugin container = eventManager.getSelectedView2dContainer();
        // TODO make printable component
        if (container.getLayoutModel().getUIName().equals("DICOM information")) {
            JOptionPane.showMessageDialog(this, "Cannot print image in the current layout.", "Error",
                JOptionPane.ERROR_MESSAGE);
            doClose();
            return;
        }
        doClose();

        if (container.getImagePanels().size() > 1 && !chckbxNewCheckBox.isSelected()) {
            // Several views
            ExportLayout<ImageElement> layout =
                new ExportLayout<ImageElement>(container.getImagePanels(), container.getLayoutModel());
            ImagePrint print = new ImagePrint(layout, printOptions);
            print.print();
            layout.dispose();
        } else {
            // One View
            ExportImage<ImageElement> exportImage = new ExportImage<ImageElement>(eventManager.getSelectedViewPane());
            exportImage.getInfoLayer().setBorder(3);
            ImagePrint print = new ImagePrint(exportImage, printOptions);
            print.print();
            exportImage.dispose();
        }

    }

    private void doClose() {
        dispose();
    }

}
