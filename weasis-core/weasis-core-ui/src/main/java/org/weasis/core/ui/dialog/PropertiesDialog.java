/*******************************************************************************
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.weasis.core.ui.dialog;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.awt.event.WindowEvent;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JColorChooser;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.border.EmptyBorder;

import org.weasis.core.api.gui.util.JMVUtils;
import org.weasis.core.api.util.StringUtil;
import org.weasis.core.ui.Messages;

public abstract class PropertiesDialog extends JDialog {
    private static final long serialVersionUID = 8140414207263317080L;

    private final JPanel panel1 = new JPanel();
    private final BorderLayout borderLayout1 = new BorderLayout();

    private final JPanel jPanelFooter = new JPanel();
    private final JButton jButtonOk = new JButton();
    private final JButton jButtonCancel = new JButton();
    private final GridBagLayout gridBagLayout2 = new GridBagLayout();
    private final JPanel jPanel1 = new JPanel();
    private final GridBagLayout gridBagLayout1 = new GridBagLayout();
    protected final JSpinner spinnerLineWidth = new JSpinner();
    protected final JLabel jLabelLineWidth = new JLabel();
    protected final JLabel jLabelLineColor = new JLabel();
    protected final JButton jButtonColor = new JButton();
    protected final JCheckBox jCheckBoxFilled = new JCheckBox();
    protected final JLabel lbloverridesmultipleValues =
        new JLabel(Messages.getString("PropertiesDialog.header_override")); //$NON-NLS-1$
    protected final JCheckBox checkBoxColor = new JCheckBox();
    protected final JCheckBox checkBoxWidth = new JCheckBox();
    protected final JCheckBox checkBoxFill = new JCheckBox();

    public PropertiesDialog(Window parent, String title) {
        super(parent, title, ModalityType.APPLICATION_MODAL);
        init();
    }

    private void init() {
        panel1.setBorder(new EmptyBorder(0, 15, 0, 15));
        panel1.setLayout(borderLayout1);
        jButtonOk.setText(Messages.getString("PropertiesDialog.ok")); //$NON-NLS-1$
        jButtonOk.addActionListener(e -> okAction());
        jButtonCancel.setText(Messages.getString("PropertiesDialog.cancel")); //$NON-NLS-1$
        jButtonCancel.addActionListener(e -> quitWithoutSaving());
        jPanelFooter.setLayout(gridBagLayout2);

        jPanel1.setLayout(gridBagLayout1);
        JMVUtils.setNumberModel(spinnerLineWidth, 1, 1, 8, 1);
        jLabelLineWidth.setText(Messages.getString("PropertiesDialog.line_width") + StringUtil.COLON); //$NON-NLS-1$
        jLabelLineColor.setText(Messages.getString("PropertiesDialog.line_color") + StringUtil.COLON); //$NON-NLS-1$
        jButtonColor.setText(Messages.getString("MeasureTool.pick")); //$NON-NLS-1$

        jButtonColor.addActionListener(e -> openColorChooser((JButton) e.getSource()));

        jCheckBoxFilled.setText(Messages.getString("PropertiesDialog.fill_shape")); //$NON-NLS-1$
        getContentPane().add(panel1);
        panel1.add(jPanelFooter, BorderLayout.SOUTH);
        jPanelFooter.add(jButtonCancel, new GridBagConstraints(1, 1, 1, 1, 1.0, 0.0, GridBagConstraints.CENTER,
            GridBagConstraints.NONE, new Insets(30, 15, 15, 15), 0, 0));
        jPanelFooter.add(jButtonOk, new GridBagConstraints(0, 1, 1, 1, 1.0, 0.0, GridBagConstraints.CENTER,
            GridBagConstraints.NONE, new Insets(30, 15, 15, 15), 0, 0));
        panel1.add(jPanel1, BorderLayout.CENTER);

        GridBagConstraints gbcLbloverridesmultipleValues = new GridBagConstraints();
        gbcLbloverridesmultipleValues.insets = new Insets(15, 10, 0, 25);
        gbcLbloverridesmultipleValues.gridx = 2;
        gbcLbloverridesmultipleValues.gridy = 0;
        lbloverridesmultipleValues.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createEtchedBorder(),
            BorderFactory.createEmptyBorder(3, 3, 3, 3)));
        lbloverridesmultipleValues.setOpaque(true);
        jPanel1.add(lbloverridesmultipleValues, gbcLbloverridesmultipleValues);
        jPanel1.add(jLabelLineColor, new GridBagConstraints(0, 1, 1, 1, 0.0, 0.0, GridBagConstraints.EAST,
            GridBagConstraints.NONE, new Insets(10, 25, 0, 0), 0, 0));
        jPanel1.add(jButtonColor, new GridBagConstraints(1, 1, 1, 1, 0.0, 0.0, GridBagConstraints.WEST,
            GridBagConstraints.NONE, new Insets(10, 2, 0, 0), 0, 0));

        GridBagConstraints gbcCheckBoxColor = new GridBagConstraints();
        gbcCheckBoxColor.insets = new Insets(10, 0, 0, 0);
        gbcCheckBoxColor.gridx = 2;
        gbcCheckBoxColor.gridy = 1;
        checkBoxColor.addActionListener(e -> {
            JCheckBox box = (JCheckBox) e.getSource();
            jButtonColor.setEnabled(box.isSelected());
        });
        checkBoxWidth.addActionListener(e -> {
            JCheckBox box = (JCheckBox) e.getSource();
            spinnerLineWidth.setEnabled(box.isSelected());
        });
        checkBoxFill.addActionListener(e -> {
            JCheckBox box = (JCheckBox) e.getSource();
            jCheckBoxFilled.setEnabled(box.isSelected());
        });
        jPanel1.add(checkBoxColor, gbcCheckBoxColor);
        jPanel1.add(jLabelLineWidth, new GridBagConstraints(0, 2, 1, 1, 0.0, 0.0, GridBagConstraints.EAST,
            GridBagConstraints.NONE, new Insets(10, 25, 0, 0), 0, 0));
        jPanel1.add(spinnerLineWidth, new GridBagConstraints(1, 2, 1, 1, 0.0, 0.0, GridBagConstraints.WEST,
            GridBagConstraints.NONE, new Insets(10, 2, 0, 0), 0, 0));

        GridBagConstraints gbcCheckBoxWidth = new GridBagConstraints();
        gbcCheckBoxWidth.insets = new Insets(10, 0, 0, 0);
        gbcCheckBoxWidth.gridx = 2;
        gbcCheckBoxWidth.gridy = 2;
        jPanel1.add(checkBoxWidth, gbcCheckBoxWidth);
        jPanel1.add(jCheckBoxFilled, new GridBagConstraints(0, 3, 2, 1, 0.0, 0.0, GridBagConstraints.CENTER,
            GridBagConstraints.NONE, new Insets(10, 0, 0, 0), 0, 0));

        GridBagConstraints gbcCheckBoxFill = new GridBagConstraints();
        gbcCheckBoxFill.insets = new Insets(10, 0, 0, 0);
        gbcCheckBoxFill.gridx = 2;
        gbcCheckBoxFill.gridy = 3;
        jPanel1.add(checkBoxFill, gbcCheckBoxFill);
    }

    // Overridden so we can exit when window is closed
    @Override
    protected void processWindowEvent(WindowEvent e) {
        if (e.getID() == WindowEvent.WINDOW_CLOSING) {
            quitWithoutSaving();
        }
        super.processWindowEvent(e);
    }

    protected abstract void okAction();

    protected void quitWithoutSaving() {
        dispose();
    }

    public static void openColorChooser(JButton button) {
        if (button != null) {
            Color newColor =
                JColorChooser.showDialog(button, Messages.getString("MeasureTool.pick_color"), button.getBackground()); //$NON-NLS-1$
            if (newColor != null) {
                button.setBackground(newColor);
            }
        }
    }
}
