package org.weasis.dicom.qr;
/*******************************************************************************
 * Copyright (c) 2010 Nicolas Roduit.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Nicolas Roduit - initial API and implementation
 ******************************************************************************/

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.text.ParseException;
import java.util.Calendar;
import java.util.Properties;

import javax.swing.Box;
import javax.swing.JComboBox;
import javax.swing.JFormattedTextField.AbstractFormatter;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;

import org.jdatepicker.impl.JDatePanelImpl;
import org.jdatepicker.impl.JDatePickerImpl;
import org.jdatepicker.impl.UtilDateModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.gui.util.AbstractItemDialogPage;
import org.weasis.core.api.util.LocalUtil;
import org.weasis.core.api.util.StringUtil;
import org.weasis.dicom.explorer.DicomModel;
import org.weasis.dicom.explorer.ImportDicom;
import org.weasis.dicom.explorer.pref.node.AbstractDicomNode;

public class DicomQrView extends AbstractItemDialogPage implements ImportDicom {
    public class DateLabelFormatter extends AbstractFormatter {

        @Override
        public Object stringToValue(String text) throws ParseException {
            return LocalUtil.getDateInstance().parseObject(text);
        }

        @Override
        public String valueToString(Object value) throws ParseException {
            if (value != null) {
                Calendar cal = (Calendar) value;
                return LocalUtil.getDateInstance().format(cal.getTime());
            }
            return "";
        }
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(DicomQrView.class);

    private JPanel panel;
    private final Component horizontalStrut = Box.createHorizontalStrut(20);
    private final JLabel lblDest = new JLabel("Archive" + StringUtil.COLON);
    private final JComboBox comboNode = new JComboBox();

    public DicomQrView() {
        super("DICOM Query/Retrieve");
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
        UtilDateModel model = new UtilDateModel();
        JDatePanelImpl datePanel = new JDatePanelImpl(model, new Properties());
        JDatePickerImpl datePicker = new JDatePickerImpl(datePanel, new DateLabelFormatter());
        panel.add(datePicker, gbc_checkBoxCompression);

    }

    protected void initialize(boolean afirst) {
        if (afirst) {
            Properties pref = DicomQrFactory.IMPORT_PERSISTENCE;
            AbstractDicomNode.loadDicomNodes(comboNode, AbstractDicomNode.Type.ARCHIVE);
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
