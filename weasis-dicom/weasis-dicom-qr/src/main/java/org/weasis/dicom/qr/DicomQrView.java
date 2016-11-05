package org.weasis.dicom.qr;
/*******************************************************************************
 * Copyright (c) 2016 Weasis Team and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Nicolas Roduit - initial API and implementation
 *******************************************************************************/

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.Properties;

import javax.swing.Box;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.gui.util.AbstractItemDialogPage;
import org.weasis.core.api.util.StringUtil;
import org.weasis.dicom.explorer.DicomModel;
import org.weasis.dicom.explorer.ImportDicom;
import org.weasis.dicom.explorer.pref.node.AbstractDicomNode;
import org.weasis.dicom.explorer.pref.node.AbstractDicomNode.UsageType;

public class DicomQrView extends AbstractItemDialogPage implements ImportDicom {

    private static final Logger LOGGER = LoggerFactory.getLogger(DicomQrView.class);

    private JPanel panel;
    private final Component horizontalStrut = Box.createHorizontalStrut(20);
    private final JLabel lblDest = new JLabel(Messages.getString("DicomQrView.arc") + StringUtil.COLON); //$NON-NLS-1$
    private final JComboBox comboNode = new JComboBox();

    public DicomQrView() {
        super(Messages.getString("DicomQrView.title")); //$NON-NLS-1$
        initGUI();
        initialize(true);
    }

    public void initGUI() {
        setLayout(new BorderLayout());
        panel = new JPanel();

        add(panel, BorderLayout.NORTH);
        GridBagLayout gbl_panel = new GridBagLayout();
        panel.setLayout(gbl_panel);

        GridBagConstraints gbc_lblDest = new GridBagConstraints();
        gbc_lblDest.anchor = GridBagConstraints.EAST;
        gbc_lblDest.insets = new Insets(10, 10, 5, 0);
        gbc_lblDest.gridx = 0;
        gbc_lblDest.gridy = 0;
        panel.add(lblDest, gbc_lblDest);

        GridBagConstraints gbc_comboBox = new GridBagConstraints();
        gbc_comboBox.insets = new Insets(10, 2, 5, 5);
        gbc_comboBox.fill = GridBagConstraints.HORIZONTAL;
        gbc_comboBox.gridx = 1;
        gbc_comboBox.gridy = 0;
        panel.add(comboNode, gbc_comboBox);

        GridBagConstraints gbc_horizontalStrut = new GridBagConstraints();
        gbc_horizontalStrut.weightx = 1.0;
        gbc_horizontalStrut.gridx = 4;
        gbc_horizontalStrut.gridy = 0;
        panel.add(horizontalStrut, gbc_horizontalStrut);

        GridBagConstraints gbc_checkBoxCompression = new GridBagConstraints();
        gbc_checkBoxCompression.anchor = GridBagConstraints.NORTHWEST;
        gbc_checkBoxCompression.insets = new Insets(0, 0, 5, 5);
        gbc_checkBoxCompression.gridx = 0;
        gbc_checkBoxCompression.gridy = 1;
      //  panel.add(datePicker, gbc_checkBoxCompression);

    }

    protected void initialize(boolean afirst) {
        if (afirst) {
            Properties pref = DicomQrFactory.IMPORT_PERSISTENCE;
            AbstractDicomNode.loadDicomNodes(comboNode, AbstractDicomNode.Type.DICOM, UsageType.RETRIEVE);
        }
    }

    public void resetSettingsToDefault() {
        initialize(false);
    }

    public void applyChange() {

    }

    protected void updateChanges() {
    }

    @Override
    public void closeAdditionalWindow() {
        applyChange();
    }

    @Override
    public void resetoDefaultValues() {
    }

    @Override
    public void importDICOM(DicomModel dicomModel, JProgressBar info) {
        // TODO Auto-generated method stub

    }

}
