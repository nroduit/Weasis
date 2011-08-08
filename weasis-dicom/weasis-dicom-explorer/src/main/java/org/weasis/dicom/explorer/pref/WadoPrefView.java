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
package org.weasis.dicom.explorer.pref;

import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.SpinnerNumberModel;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;

import org.weasis.core.api.gui.util.AbstractItemDialogPage;
import org.weasis.dicom.codec.TransferSyntax;
import org.weasis.dicom.explorer.Messages;
import org.weasis.dicom.explorer.wado.DicomManager;

public class WadoPrefView extends AbstractItemDialogPage {

    private final ItemListener changeViewListener = new ItemListener() {

        @Override
        public void itemStateChanged(final ItemEvent e) {
            if (e.getStateChange() == ItemEvent.SELECTED) {
                selectTSUID((TransferSyntax) comboBox.getSelectedItem());
            }
        }
    };
    private final JPanel panel = new JPanel();
    private JComboBox comboBox;
    private final JSpinner spinnerScroll = new JSpinner();
    private JLabel lblCompression;

    public WadoPrefView() {
        initGUI();
    }

    private void initGUI() {
        setBorder(new EmptyBorder(15, 10, 10, 10));
        setTitle(Messages.getString("WadoPrefView.wado")); //$NON-NLS-1$
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        lblCompression = new JLabel(Messages.getString("WadoPrefView.compr")); //$NON-NLS-1$
        add(panel);
        GridBagLayout gbl_panel = new GridBagLayout();
        gbl_panel.columnWidths = new int[] { 0, 0, 0, 0 };
        gbl_panel.rowHeights = new int[] { 0, 0, 0, 0 };
        gbl_panel.columnWeights = new double[] { 0.0, 0.0, 0.0, Double.MIN_VALUE };
        gbl_panel.rowWeights = new double[] { 0.0, 0.0, 0.0, Double.MIN_VALUE };
        panel.setLayout(gbl_panel);
        panel.setBorder(new TitledBorder(null,
            Messages.getString("WadoPrefView.compr2"), TitledBorder.LEADING, TitledBorder.TOP, null, null)); //$NON-NLS-1$

        final JLabel lblTranscodingTo = new JLabel(Messages.getString("WadoPrefView.trans")); //$NON-NLS-1$
        GridBagConstraints gbc_lblTranscodingTo = new GridBagConstraints();
        gbc_lblTranscodingTo.insets = new Insets(0, 0, 5, 5);
        gbc_lblTranscodingTo.anchor = GridBagConstraints.WEST;
        gbc_lblTranscodingTo.gridx = 0;
        gbc_lblTranscodingTo.gridy = 0;
        panel.add(lblTranscodingTo, gbc_lblTranscodingTo);

        comboBox = new JComboBox(TransferSyntax.values());
        comboBox.addItemListener(changeViewListener);
        GridBagConstraints gbc_comboBox = new GridBagConstraints();
        gbc_comboBox.anchor = GridBagConstraints.WEST;
        gbc_comboBox.insets = new Insets(0, 2, 5, 5);
        gbc_comboBox.gridx = 1;
        gbc_comboBox.gridy = 0;
        panel.add(comboBox, gbc_comboBox);

        GridBagConstraints gbc_lblstrut = new GridBagConstraints();
        gbc_lblstrut.insets = new Insets(0, 0, 5, 0);
        gbc_lblstrut.anchor = GridBagConstraints.WEST;
        gbc_lblstrut.fill = GridBagConstraints.HORIZONTAL;
        gbc_lblstrut.weightx = 1.0;
        gbc_lblstrut.gridx = 2;
        gbc_lblstrut.gridy = 0;
        panel.add(Box.createHorizontalStrut(2), gbc_lblstrut);

        JTextArea txtpnNoteWhenThe = new JTextArea(Messages.getString("WadoPrefView.mes")); //$NON-NLS-1$

        GridBagConstraints gbc_txtpnNoteWhenThe = new GridBagConstraints();
        gbc_txtpnNoteWhenThe.weighty = 1.0;
        gbc_txtpnNoteWhenThe.fill = GridBagConstraints.BOTH;
        gbc_txtpnNoteWhenThe.anchor = GridBagConstraints.NORTHWEST;
        gbc_txtpnNoteWhenThe.gridwidth = 3;
        gbc_txtpnNoteWhenThe.gridx = 0;
        gbc_txtpnNoteWhenThe.gridy = 2;
        txtpnNoteWhenThe.setPreferredSize(new Dimension(250, 25));
        txtpnNoteWhenThe.setEditable(false);
        txtpnNoteWhenThe.setBorder(new EmptyBorder(2, 2, 2, 2));
        txtpnNoteWhenThe.setOpaque(false);
        txtpnNoteWhenThe.setLineWrap(true);
        txtpnNoteWhenThe.setWrapStyleWord(true);
        txtpnNoteWhenThe.setBackground(lblTranscodingTo.getBackground());
        txtpnNoteWhenThe.setForeground(lblTranscodingTo.getForeground());
        panel.add(txtpnNoteWhenThe, gbc_txtpnNoteWhenThe);

        comboBox.setSelectedItem(DicomManager.getInstance().getWadoTSUID());

        JPanel panel_2 = new JPanel();
        FlowLayout flowLayout_1 = (FlowLayout) panel_2.getLayout();
        flowLayout_1.setHgap(10);
        flowLayout_1.setAlignment(FlowLayout.RIGHT);
        flowLayout_1.setVgap(7);
        add(panel_2);

        JButton btnNewButton = new JButton(org.weasis.core.ui.Messages.getString("restore.values")); //$NON-NLS-1$
        panel_2.add(btnNewButton);
        btnNewButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                resetoDefaultValues();
            }
        });
    }

    private void selectTSUID(TransferSyntax tsuid) {
        if (tsuid != null && tsuid.getCompression() != null) {
            GridBagConstraints gbc_lblCompression = new GridBagConstraints();
            gbc_lblCompression.anchor = GridBagConstraints.EAST;
            gbc_lblCompression.insets = new Insets(0, 0, 5, 5);
            gbc_lblCompression.gridx = 0;
            gbc_lblCompression.gridy = 1;
            panel.add(lblCompression, gbc_lblCompression);

            spinnerScroll.setModel(new SpinnerNumberModel((int) tsuid.getCompression(), 0, 100, 1));
            GridBagConstraints gbc_spinner_1 = new GridBagConstraints();
            gbc_spinner_1.anchor = GridBagConstraints.WEST;
            gbc_spinner_1.insets = new Insets(0, 2, 5, 5);
            gbc_spinner_1.gridx = 1;
            gbc_spinner_1.gridy = 1;
            panel.add(spinnerScroll, gbc_spinner_1);
        } else {
            panel.remove(lblCompression);
            panel.remove(spinnerScroll);
        }
        panel.revalidate();
        panel.repaint();
    }

    @Override
    public void closeAdditionalWindow() {
        TransferSyntax tsuid = (TransferSyntax) comboBox.getSelectedItem();
        if (tsuid != null) {
            if (tsuid.getCompression() != null) {
                tsuid.setCompression((Integer) spinnerScroll.getValue());
            }
            DicomManager.getInstance().setWadoTSUID(tsuid);
        }
    }

    @Override
    public void resetoDefaultValues() {
        DicomManager.getInstance().restoreDefaultValues();
        comboBox.setSelectedItem(DicomManager.getInstance().getWadoTSUID());
    }

}
