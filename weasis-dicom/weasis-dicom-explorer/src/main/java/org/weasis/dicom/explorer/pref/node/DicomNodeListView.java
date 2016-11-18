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
import org.weasis.dicom.explorer.Messages;
import org.weasis.dicom.util.StringUtil;

@SuppressWarnings("serial")
public class DicomNodeListView extends AbstractItemDialogPage {

    public DicomNodeListView() {
        super(Messages.getString("DicomNodeListView.node_list")); //$NON-NLS-1$
        initGUI();
    }

    private void initGUI() {
        setBorder(new EmptyBorder(15, 10, 10, 10));
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));

        JPanel panel = new JPanel();
        panel.setBorder(new TitledBorder(null, AbstractDicomNode.Type.DICOM.toString(), TitledBorder.LEADING,
            TitledBorder.TOP, null, null));
        add(panel);
        GridBagLayout gblPanel = new GridBagLayout();
        panel.setLayout(gblPanel);

        JLabel label = new JLabel();
        label.setText(Messages.getString("DicomNodeListView.node") + StringUtil.COLON); //$NON-NLS-1$
        GridBagConstraints gbcLabel = new GridBagConstraints();
        gbcLabel.anchor = GridBagConstraints.WEST;
        gbcLabel.insets = new Insets(0, 0, 5, 5);
        gbcLabel.gridx = 0;
        gbcLabel.gridy = 0;
        panel.add(label, gbcLabel);

        final JComboBox<AbstractDicomNode> nodeComboBox = new JComboBox<>();
        AbstractDicomNode.loadDicomNodes(nodeComboBox, AbstractDicomNode.Type.DICOM);
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
        addNodeButton.addActionListener(
            e -> AbstractDicomNode.addNodeActionPerformed(nodeComboBox, AbstractDicomNode.Type.DICOM));

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

        JPanel panel1 = new JPanel();
        panel1.setBorder(new TitledBorder(null, AbstractDicomNode.Type.WEB.toString(), TitledBorder.LEADING,
            TitledBorder.TOP, null, null));
        add(panel1);
        GridBagLayout gblPanel1 = new GridBagLayout();
        panel1.setLayout(gblPanel1);

        JLabel label1 = new JLabel();
        label1.setText(Messages.getString("DicomNodeListView.node") + StringUtil.COLON); //$NON-NLS-1$
        GridBagConstraints gbcLabel1 = new GridBagConstraints();
        gbcLabel1.anchor = GridBagConstraints.WEST;
        gbcLabel1.insets = new Insets(0, 0, 5, 5);
        gbcLabel1.gridx = 0;
        gbcLabel1.gridy = 0;
        panel1.add(label1, gbcLabel1);

        final JComboBox<AbstractDicomNode> comboBoxWeb = new JComboBox<>();
        AbstractDicomNode.loadDicomNodes(comboBoxWeb, AbstractDicomNode.Type.WEB);
        GridBagConstraints gbccomboBox = new GridBagConstraints();
        gbccomboBox.anchor = GridBagConstraints.NORTHWEST;
        gbccomboBox.insets = new Insets(0, 0, 5, 5);
        gbccomboBox.gridx = 1;
        gbccomboBox.gridy = 0;
        panel1.add(comboBoxWeb, gbccomboBox);
        JMVUtils.setPreferredWidth(comboBoxWeb, 185, 185);
        AbstractDicomNode.addTooltipToComboList(comboBoxWeb);

        Component horizontalStrut1 = Box.createHorizontalStrut(20);
        GridBagConstraints gbcHorizontalStrut1 = new GridBagConstraints();
        gbcHorizontalStrut1.anchor = GridBagConstraints.WEST;
        gbcHorizontalStrut1.insets = new Insets(0, 0, 5, 5);
        gbcHorizontalStrut1.gridx = 2;
        gbcHorizontalStrut1.gridy = 0;
        panel1.add(horizontalStrut1, gbcHorizontalStrut1);

        JButton editBtn1 = new JButton();
        editBtn1.setText(Messages.getString("DicomNodeListView.edit")); //$NON-NLS-1$
        GridBagConstraints gbcbutton = new GridBagConstraints();
        gbcbutton.anchor = GridBagConstraints.NORTHWEST;
        gbcbutton.insets = new Insets(0, 0, 5, 5);
        gbcbutton.gridx = 3;
        gbcbutton.gridy = 0;
        panel1.add(editBtn1, gbcbutton);

        JButton deleteBtn1 = new JButton();
        deleteBtn1.setText(Messages.getString("DicomNodeListView.delete")); //$NON-NLS-1$
        GridBagConstraints gbcButton3 = new GridBagConstraints();
        gbcButton3.anchor = GridBagConstraints.NORTHWEST;
        gbcButton3.insets = new Insets(0, 0, 5, 0);
        gbcButton3.gridx = 4;
        gbcButton3.gridy = 0;
        panel1.add(deleteBtn1, gbcButton3);

        JButton addBtn1 = new JButton(Messages.getString("DicomNodeListView.add_new")); //$NON-NLS-1$
        GridBagConstraints gbcButton4 = new GridBagConstraints();
        gbcButton4.anchor = GridBagConstraints.NORTHWEST;
        gbcButton4.gridwidth = 2;
        gbcButton4.insets = new Insets(10, 0, 5, 0);
        gbcButton4.gridx = 3;
        gbcButton4.gridy = 1;
        panel1.add(addBtn1, gbcButton4);

        Box verticalBox1 = Box.createVerticalBox();
        GridBagConstraints gbcVerticalBox1 = new GridBagConstraints();
        gbcVerticalBox1.weighty = 1.0;
        gbcVerticalBox1.weightx = 1.0;
        gbcVerticalBox1.insets = new Insets(0, 0, 5, 0);
        gbcVerticalBox1.fill = GridBagConstraints.BOTH;
        gbcVerticalBox1.anchor = GridBagConstraints.NORTHWEST;
        gbcVerticalBox1.gridx = 4;
        gbcVerticalBox1.gridy = 2;
        panel1.add(verticalBox1, gbcVerticalBox1);

        deleteBtn1.addActionListener(e -> AbstractDicomNode.deleteNodeActionPerformed(comboBoxWeb));
        editBtn1.addActionListener(e -> AbstractDicomNode.editNodeActionPerformed(comboBoxWeb));
        addBtn1
            .addActionListener(e -> AbstractDicomNode.addNodeActionPerformed(comboBoxWeb, AbstractDicomNode.Type.WEB));

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
