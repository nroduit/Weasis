/*******************************************************************************
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.weasis.dicom.explorer.pref.node;

import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;

import org.weasis.core.api.gui.util.AbstractItemDialogPage;
import org.weasis.core.api.gui.util.JMVUtils;
import org.weasis.core.api.util.StringUtil;
import org.weasis.dicom.explorer.Messages;

@SuppressWarnings("serial")
public class DicomNodeListView extends AbstractItemDialogPage {

    public DicomNodeListView() {
        super(Messages.getString("DicomNodeListView.node_list")); //$NON-NLS-1$
        initGUI();
    }

    private void initGUI() {
        setBorder(new EmptyBorder(15, 10, 10, 10));
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));

        buildPanel(AbstractDicomNode.Type.DICOM_CALLING);
        buildPanel(AbstractDicomNode.Type.DICOM);
        buildPanel(AbstractDicomNode.Type.WEB);
        // buildPanel(AbstractDicomNode.Type.WEB_QIDO);
    }

    private void buildPanel(AbstractDicomNode.Type nodeType) {
        JPanel panel = new JPanel();
        panel
            .setBorder(new TitledBorder(null, nodeType.toString(), TitledBorder.LEADING, TitledBorder.TOP, null, null));
        add(panel);
        GridBagLayout gblPanel1 = new GridBagLayout();
        panel.setLayout(gblPanel1);

        JLabel label1 = new JLabel();
        label1.setText(Messages.getString("DicomNodeListView.node") + StringUtil.COLON); //$NON-NLS-1$
        GridBagConstraints gbcLabel1 = new GridBagConstraints();
        gbcLabel1.anchor = GridBagConstraints.WEST;
        gbcLabel1.insets = new Insets(0, 0, 5, 5);
        gbcLabel1.gridx = 0;
        gbcLabel1.gridy = 0;
        panel.add(label1, gbcLabel1);

        final JComboBox<AbstractDicomNode> nodeComboBox = new JComboBox<>();
        AbstractDicomNode.loadDicomNodes(nodeComboBox, nodeType);
        GridBagConstraints gbcComboBox = new GridBagConstraints();
        gbcComboBox.anchor = GridBagConstraints.NORTHWEST;
        gbcComboBox.insets = new Insets(0, 0, 5, 5);
        gbcComboBox.gridx = 1;
        gbcComboBox.gridy = 0;
        panel.add(nodeComboBox, gbcComboBox);
        JMVUtils.setPreferredWidth(nodeComboBox, 185, 185);
        AbstractDicomNode.addTooltipToComboList(nodeComboBox);

        Component horizontalStrut = Box.createHorizontalStrut(20);
        GridBagConstraints gbcHorizontalStrut = new GridBagConstraints();
        gbcHorizontalStrut.anchor = GridBagConstraints.WEST;
        gbcHorizontalStrut.insets = new Insets(0, 0, 5, 5);
        gbcHorizontalStrut.gridx = 2;
        gbcHorizontalStrut.gridy = 0;
        panel.add(horizontalStrut, gbcHorizontalStrut);

        JButton editButton = new JButton();
        editButton.setText(Messages.getString("DicomNodeListView.edit")); //$NON-NLS-1$
        GridBagConstraints gbcButton1 = new GridBagConstraints();
        gbcButton1.anchor = GridBagConstraints.NORTHWEST;
        gbcButton1.insets = new Insets(0, 0, 5, 5);
        gbcButton1.gridx = 3;
        gbcButton1.gridy = 0;
        panel.add(editButton, gbcButton1);

        JButton deleteButton = new JButton();
        deleteButton.setText(Messages.getString("DicomNodeListView.delete")); //$NON-NLS-1$
        GridBagConstraints gbcButton2 = new GridBagConstraints();
        gbcButton2.insets = new Insets(0, 0, 5, 5);
        gbcButton2.anchor = GridBagConstraints.NORTHWEST;
        gbcButton2.gridx = 4;
        gbcButton2.gridy = 0;
        panel.add(deleteButton, gbcButton2);

        JButton addNodeButton = new JButton(Messages.getString("DicomNodeListView.add_new")); //$NON-NLS-1$
        GridBagConstraints gbcButton = new GridBagConstraints();
        gbcButton.gridwidth = 2;
        gbcButton.anchor = GridBagConstraints.NORTHWEST;
        gbcButton.insets = new Insets(10, 0, 5, 5);
        gbcButton.gridx = 3;
        gbcButton.gridy = 1;
        panel.add(addNodeButton, gbcButton);

        deleteButton.addActionListener(e -> AbstractDicomNode.deleteNodeActionPerformed(nodeComboBox));
        editButton.addActionListener(e -> AbstractDicomNode.editNodeActionPerformed(nodeComboBox));
        addNodeButton.addActionListener(e -> AbstractDicomNode.addNodeActionPerformed(nodeComboBox, nodeType));

        Box verticalBox = Box.createVerticalBox();
        GridBagConstraints gbcVerticalBox = new GridBagConstraints();
        gbcVerticalBox.weighty = 1.0;
        gbcVerticalBox.weightx = 1.0;
        gbcVerticalBox.insets = new Insets(0, 0, 5, 0);
        gbcVerticalBox.fill = GridBagConstraints.BOTH;
        gbcVerticalBox.anchor = GridBagConstraints.NORTHWEST;
        gbcVerticalBox.gridx = 4;
        gbcVerticalBox.gridy = 2;
        panel.add(verticalBox, gbcVerticalBox);
    }

    @Override
    public void closeAdditionalWindow() {
        // Do nothing
    }

    @Override
    public void resetoDefaultValues() {
        // Do nothing
    }
}
