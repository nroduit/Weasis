/*******************************************************************************
 * Copyright (c) 2009-2018 Weasis Team and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 *     Nicolas Roduit - initial API and implementation
 *******************************************************************************/
package org.weasis.dicom.explorer.pref;

import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
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
import org.weasis.core.api.util.StringUtil;
import org.weasis.dicom.codec.TransferSyntax;
import org.weasis.dicom.explorer.wado.DicomManager;

public class WadoPrefView extends AbstractItemDialogPage {

    private final JPanel panel = new JPanel();
    private JComboBox<TransferSyntax> comboBox;
    private final JSpinner spinnerScroll = new JSpinner();
    private JLabel lblCompression;
    private final ItemListener changeViewListener = e -> {
        if (e.getStateChange() == ItemEvent.SELECTED) {
            selectTSUID((TransferSyntax) comboBox.getSelectedItem());
        }
    };

    public WadoPrefView() {
        super("WADO"); //$NON-NLS-1$
        setComponentPosition(5500);
        initGUI();
    }

    private void initGUI() {
        setBorder(new EmptyBorder(15, 10, 10, 10));
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        lblCompression = new JLabel("Compression" + StringUtil.COLON); //$NON-NLS-1$
        add(panel);
        GridBagLayout gblpanel = new GridBagLayout();
        gblpanel.columnWidths = new int[] { 0, 0, 0, 0 };
        gblpanel.rowHeights = new int[] { 0, 0, 0, 0 };
        gblpanel.columnWeights = new double[] { 0.0, 0.0, 0.0, Double.MIN_VALUE };
        gblpanel.rowWeights = new double[] { 0.0, 0.0, 0.0, Double.MIN_VALUE };
        panel.setLayout(gblpanel);
        panel.setBorder(new TitledBorder(null, "Compression", TitledBorder.LEADING, TitledBorder.TOP, null, null)); //$NON-NLS-1$

        final JLabel lblTranscodingTo = new JLabel("Transcoding to" + StringUtil.COLON); //$NON-NLS-1$
        GridBagConstraints gbclblTranscodingTo = new GridBagConstraints();
        gbclblTranscodingTo.insets = new Insets(0, 0, 5, 5);
        gbclblTranscodingTo.anchor = GridBagConstraints.WEST;
        gbclblTranscodingTo.gridx = 0;
        gbclblTranscodingTo.gridy = 0;
        panel.add(lblTranscodingTo, gbclblTranscodingTo);

        comboBox = new JComboBox(TransferSyntax.values());
        comboBox.addItemListener(changeViewListener);
        GridBagConstraints gbcComboBox = new GridBagConstraints();
        gbcComboBox.anchor = GridBagConstraints.WEST;
        gbcComboBox.insets = new Insets(0, 2, 5, 5);
        gbcComboBox.gridx = 1;
        gbcComboBox.gridy = 0;
        panel.add(comboBox, gbcComboBox);

        GridBagConstraints gbcLblstrut = new GridBagConstraints();
        gbcLblstrut.insets = new Insets(0, 0, 5, 0);
        gbcLblstrut.anchor = GridBagConstraints.WEST;
        gbcLblstrut.fill = GridBagConstraints.HORIZONTAL;
        gbcLblstrut.weightx = 1.0;
        gbcLblstrut.gridx = 2;
        gbcLblstrut.gridy = 0;
        panel.add(Box.createHorizontalStrut(2), gbcLblstrut);

        JTextArea txtpnNoteWhenThe = new JTextArea(
            "Note: When the WADO server cannot apply the Transfer Syntax, it should return an uncompressed image (1.2.840.10008.1.2.1) or it triggers an error."); //$NON-NLS-1$

        GridBagConstraints gbctxtpnNoteWhenThe = new GridBagConstraints();
        gbctxtpnNoteWhenThe.weighty = 1.0;
        gbctxtpnNoteWhenThe.fill = GridBagConstraints.BOTH;
        gbctxtpnNoteWhenThe.anchor = GridBagConstraints.NORTHWEST;
        gbctxtpnNoteWhenThe.gridwidth = 3;
        gbctxtpnNoteWhenThe.gridx = 0;
        gbctxtpnNoteWhenThe.gridy = 2;
        txtpnNoteWhenThe.setPreferredSize(new Dimension(250, 25));
        txtpnNoteWhenThe.setEditable(false);
        txtpnNoteWhenThe.setBorder(new EmptyBorder(2, 2, 2, 2));
        txtpnNoteWhenThe.setOpaque(false);
        txtpnNoteWhenThe.setLineWrap(true);
        txtpnNoteWhenThe.setWrapStyleWord(true);
        txtpnNoteWhenThe.setBackground(lblTranscodingTo.getBackground());
        txtpnNoteWhenThe.setForeground(lblTranscodingTo.getForeground());
        panel.add(txtpnNoteWhenThe, gbctxtpnNoteWhenThe);

        comboBox.setSelectedItem(DicomManager.getInstance().getWadoTSUID());

        JPanel panel2 = new JPanel();
        FlowLayout flowLayout1 = (FlowLayout) panel2.getLayout();
        flowLayout1.setHgap(10);
        flowLayout1.setAlignment(FlowLayout.RIGHT);
        flowLayout1.setVgap(7);
        add(panel2);

        JButton btnNewButton = new JButton(org.weasis.core.ui.Messages.getString("restore.values")); //$NON-NLS-1$
        panel2.add(btnNewButton);
        btnNewButton.addActionListener(e -> resetoDefaultValues());
    }

    private void selectTSUID(TransferSyntax tsuid) {
        if (tsuid != null && tsuid.getCompression() != null) {
            GridBagConstraints gbcLblCompression = new GridBagConstraints();
            gbcLblCompression.anchor = GridBagConstraints.EAST;
            gbcLblCompression.insets = new Insets(0, 0, 5, 5);
            gbcLblCompression.gridx = 0;
            gbcLblCompression.gridy = 1;
            panel.add(lblCompression, gbcLblCompression);

            spinnerScroll.setModel(new SpinnerNumberModel((int) tsuid.getCompression(), 0, 100, 1));
            GridBagConstraints gbcSpinner1 = new GridBagConstraints();
            gbcSpinner1.anchor = GridBagConstraints.WEST;
            gbcSpinner1.insets = new Insets(0, 2, 5, 5);
            gbcSpinner1.gridx = 1;
            gbcSpinner1.gridy = 1;
            panel.add(spinnerScroll, gbcSpinner1);
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
