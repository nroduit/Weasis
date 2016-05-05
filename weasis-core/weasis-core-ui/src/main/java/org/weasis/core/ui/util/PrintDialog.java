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
import java.util.List;

import javax.swing.JCheckBox;
import javax.swing.JOptionPane;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.api.util.StringUtil;
import org.weasis.core.ui.Messages;
import org.weasis.core.ui.editor.image.ExportImage;
import org.weasis.core.ui.editor.image.ImageViewerEventManager;
import org.weasis.core.ui.editor.image.ImageViewerPlugin;
import org.weasis.core.ui.editor.image.ViewCanvas;
import org.weasis.core.ui.util.PrintOptions.SCALE;

/**
 *
 * @author Marcelo Porto (marcelo@animati.com.br)
 */
public class PrintDialog<I extends ImageElement> extends javax.swing.JDialog {

    private static final Logger LOGGER = LoggerFactory.getLogger(PrintDialog.class);

    private javax.swing.JCheckBox annotationsCheckBox;
    private javax.swing.JButton cancelButton;
    private javax.swing.JLabel customImageSizeLabel;
    private javax.swing.JComboBox<SCALE> imageSizeComboBox;
    private javax.swing.JLabel imageSizeLabel;
    private javax.swing.JComboBox<String> positionComboBox;
    private javax.swing.JLabel positionLabel;
    private javax.swing.JButton printButton;

    private ImageViewerEventManager<I> eventManager;
    private JCheckBox chckbxSelctedView;
    private JSpinner spinner;

    /** Creates new form PrintDialog */
    public PrintDialog(Window parent, String title, ImageViewerEventManager<I> eventManager) {
        super(parent, title, ModalityType.APPLICATION_MODAL);
        this.eventManager = eventManager;
        boolean layout = eventManager.getSelectedView2dContainer().getLayoutModel().getConstraints().size() > 1;
        initComponents(layout);
        imageSizeComboBox();
        setOnlySelectedView(layout);
        pack();
    }

    private void initComponents(boolean layout) {

        GridBagLayout gridBagLayout = new GridBagLayout();
        getContentPane().setLayout(gridBagLayout);
        imageSizeLabel = new javax.swing.JLabel();

        imageSizeLabel.setText(Messages.getString("PrintDialog.img_size") + StringUtil.COLON); //$NON-NLS-1$
        GridBagConstraints gbcImageSizeLabel = new GridBagConstraints();
        gbcImageSizeLabel.anchor = GridBagConstraints.EAST;
        gbcImageSizeLabel.insets = new Insets(15, 15, 10, 5);
        gbcImageSizeLabel.gridx = 0;
        gbcImageSizeLabel.gridy = 0;
        getContentPane().add(imageSizeLabel, gbcImageSizeLabel);
        imageSizeComboBox = new javax.swing.JComboBox<>(SCALE.values());

        imageSizeComboBox.addActionListener(new java.awt.event.ActionListener() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                imageSizeComboBox();
            }
        });
        GridBagConstraints gbcImageSizeComboBox = new GridBagConstraints();
        gbcImageSizeComboBox.anchor = GridBagConstraints.WEST;
        gbcImageSizeComboBox.insets = new Insets(15, 0, 10, 5);
        gbcImageSizeComboBox.gridx = 1;
        gbcImageSizeComboBox.gridy = 0;
        getContentPane().add(imageSizeComboBox, gbcImageSizeComboBox);
        customImageSizeLabel = new javax.swing.JLabel();

        customImageSizeLabel.setText(Messages.getString("PrintDialog.zoom") + StringUtil.COLON); //$NON-NLS-1$
        GridBagConstraints gbcCustomImageSizeLabel = new GridBagConstraints();
        gbcCustomImageSizeLabel.anchor = GridBagConstraints.EAST;
        gbcCustomImageSizeLabel.insets = new Insets(0, 15, 10, 5);
        gbcCustomImageSizeLabel.gridx = 0;
        gbcCustomImageSizeLabel.gridy = 1;
        getContentPane().add(customImageSizeLabel, gbcCustomImageSizeLabel);

        spinner = new JSpinner();
        spinner.setModel(new SpinnerNumberModel(100, 5, 200, 1));
        GridBagConstraints gbcSpinner = new GridBagConstraints();
        gbcSpinner.anchor = GridBagConstraints.WEST;
        gbcSpinner.insets = new Insets(0, 0, 10, 5);
        gbcSpinner.gridx = 1;
        gbcSpinner.gridy = 1;
        getContentPane().add(spinner, gbcSpinner);
        positionLabel = new javax.swing.JLabel();

        positionLabel.setText(Messages.getString("PrintDialog.pos") + StringUtil.COLON); //$NON-NLS-1$
        GridBagConstraints gbcPositionLabel = new GridBagConstraints();
        gbcPositionLabel.anchor = GridBagConstraints.EAST;
        gbcPositionLabel.insets = new Insets(0, 15, 10, 5);
        gbcPositionLabel.gridx = 0;
        gbcPositionLabel.gridy = 2;
        getContentPane().add(positionLabel, gbcPositionLabel);
        positionComboBox = new javax.swing.JComboBox<>();

        positionComboBox.setModel(new javax.swing.DefaultComboBoxModel<String>(
            new String[] { Messages.getString("PrintDialog.center"), Messages.getString("PrintDialog.top") })); //$NON-NLS-1$ //$NON-NLS-2$
        GridBagConstraints gbcPositionComboBox = new GridBagConstraints();
        gbcPositionComboBox.anchor = GridBagConstraints.WEST;
        gbcPositionComboBox.insets = new Insets(0, 0, 10, 5);
        gbcPositionComboBox.gridx = 1;
        gbcPositionComboBox.gridy = 2;
        getContentPane().add(positionComboBox, gbcPositionComboBox);
        annotationsCheckBox = new javax.swing.JCheckBox();

        annotationsCheckBox.setText(Messages.getString("PrintDialog.annotate")); //$NON-NLS-1$
        annotationsCheckBox.setSelected(true);
        GridBagConstraints gbcAnnotationsCheckBox = new GridBagConstraints();
        gbcAnnotationsCheckBox.anchor = GridBagConstraints.WEST;
        gbcAnnotationsCheckBox.insets = new Insets(0, 15, 10, 0);
        gbcAnnotationsCheckBox.gridwidth = 3;
        gbcAnnotationsCheckBox.gridx = 0;
        gbcAnnotationsCheckBox.gridy = 3;
        getContentPane().add(annotationsCheckBox, gbcAnnotationsCheckBox);
        cancelButton = new javax.swing.JButton();

        cancelButton.setText(Messages.getString("PrintDialog.cancel")); //$NON-NLS-1$
        cancelButton.addActionListener(new java.awt.event.ActionListener() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                doClose();
            }
        });

        printButton = new javax.swing.JButton();

        printButton.setText(Messages.getString("PrintDialog.print")); //$NON-NLS-1$
        printButton.addActionListener(new java.awt.event.ActionListener() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                printAction();
            }
        });

        if (layout) {
            chckbxSelctedView = new JCheckBox(Messages.getString("PrintDialog.selected_view")); //$NON-NLS-1$
            chckbxSelctedView.addActionListener(new ActionListener() {

                @Override
                public void actionPerformed(ActionEvent e) {
                    setOnlySelectedView(!chckbxSelctedView.isSelected());
                }
            });
            GridBagConstraints gbcChckbxNewCheckBox = new GridBagConstraints();
            gbcChckbxNewCheckBox.anchor = GridBagConstraints.WEST;
            gbcChckbxNewCheckBox.gridwidth = 3;
            gbcChckbxNewCheckBox.insets = new Insets(0, 15, 10, 5);
            gbcChckbxNewCheckBox.gridx = 0;
            gbcChckbxNewCheckBox.gridy = 4;
            getContentPane().add(chckbxSelctedView, gbcChckbxNewCheckBox);
        }

        getRootPane().setDefaultButton(printButton);
        GridBagConstraints gbcPrintButton = new GridBagConstraints();
        gbcPrintButton.anchor = GridBagConstraints.EAST;
        gbcPrintButton.insets = new Insets(25, 0, 15, 5);
        gbcPrintButton.gridx = 1;
        gbcPrintButton.gridy = 5;
        getContentPane().add(printButton, gbcPrintButton);
        GridBagConstraints gbcCancelButton = new GridBagConstraints();
        gbcCancelButton.insets = new Insets(25, 10, 15, 15);
        gbcCancelButton.anchor = GridBagConstraints.NORTHWEST;
        gbcCancelButton.gridx = 2;
        gbcCancelButton.gridy = 5;
        getContentPane().add(cancelButton, gbcCancelButton);

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
            doClose();
            return;
        }

        // if (container.getLayoutModel().getConstraints().size() != views.size()) {
        // int res = JOptionPane.showConfirmDialog(this,
        // "This layout is partially printable. Do you want to continue?", null, JOptionPane.YES_NO_OPTION);
        // if (res == JOptionPane.NO_OPTION) {
        // doClose();
        // return;
        // }
        // }
        doClose();

        if (chckbxSelctedView != null && !chckbxSelctedView.isSelected()) {
            // Several views
            ExportLayout<I> layout = new ExportLayout<>(container.getLayoutModel());
            ImagePrint print = new ImagePrint(layout, printOptions);
            print.print();
            layout.dispose();
        } else {
            // One View
            ExportImage<I> exportImage = new ExportImage<>(eventManager.getSelectedViewPane());
            exportImage.getInfoLayer().setBorder(2);
            ImagePrint print = new ImagePrint(exportImage, printOptions);
            print.print();
            exportImage.disposeView();
        }

    }

    private void doClose() {
        dispose();
    }

}
