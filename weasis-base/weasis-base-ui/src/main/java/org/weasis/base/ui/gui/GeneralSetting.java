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
package org.weasis.base.ui.gui;

import java.awt.Component;
import java.awt.Frame;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;

import javax.swing.Box;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JTextPane;
import javax.swing.ListSelectionModel;
import javax.swing.LookAndFeel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.UIManager.LookAndFeelInfo;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.plaf.basic.BasicComboPopup;

import org.weasis.base.ui.Messages;
import org.weasis.core.api.gui.util.AbstractItemDialogPage;
import org.weasis.core.api.service.BundleTools;

public class GeneralSetting extends AbstractItemDialogPage {

    public static final String pageName = Messages.getString("LookSetting.gen"); //$NON-NLS-1$
    private LookInfo oldUILook;

    private Component component1;
    private final GridBagLayout gridBagLayout1 = new GridBagLayout();
    private final JLabel jLabelMLook = new JLabel();
    private final JComboBox jComboBox1 = new JComboBox();
    private final JLabel labelLocale = new JLabel(Messages.getString("LookSetting.locale")); //$NON-NLS-1$
    private final JComboBox comboBox = new JLocaleCombo();
    private final JTextPane txtpnNote = new JTextPane();
    private final JCheckBox chckbxConfirmClosing =
        new JCheckBox(Messages.getString("GeneralSetting.closingConfirmation")); //$NON-NLS-1$
    // private final Walkthrough uiSample = new Walkthrough(WinUtil.getParentDialogOrFrame(GeneralSetting.this));
    private int uiIndex = -1;

    private transient final ListSelectionListener listSelectionListener = new ListSelectionListener() {

        @Override
        public void valueChanged(ListSelectionEvent e) {
            if (!e.getValueIsAdjusting()) {
                ListSelectionModel model = (ListSelectionModel) e.getSource();
                int first = model.getMinSelectionIndex();
                if (first != uiIndex && first >= 0) {
                    uiIndex = first;
                    LookInfo item = (LookInfo) jComboBox1.getItemAt(uiIndex);
                    try {
                        UIManager.setLookAndFeel(item.getClassName());
                        Frame p = WeasisWin.getFrames()[0];
                        SwingUtilities.updateComponentTreeUI(p);
                        p.pack();

                    } catch (Exception e1) {
                        e1.printStackTrace();
                    }
                }
            }
        }

    };

    // private transient final ItemListener lafItemListener = new ItemListener() {
    //
    // @Override
    // public void itemStateChanged(ItemEvent e) {
    // if (e.getStateChange() == ItemEvent.SELECTED) {
    // LookInfo item = (LookInfo) jComboBox1.getSelectedItem();
    // try {
    //
    // UIManager.setLookAndFeel(item.getClassName());
    // // UIManager.getLookAndFeelDefaults().put("ClassLoader",
    // // UIManager.getLookAndFeel().getClass().getClassLoader());
    // // Window p = WinUtil.getParentDialogOrFrame(GeneralSetting.this);
    // Window p = WeasisWin.getInstance();
    // SwingUtilities.updateComponentTreeUI(p);
    // p.pack();
    //
    // // UIManager.getLookAndFeelDefaults().put("ClassLoader", SubstanceLoader.class.getClassLoader());
    // } catch (Exception e1) {
    // e1.printStackTrace();
    // }
    // }
    //
    // }
    // };

    public GeneralSetting() {
        setTitle(pageName);
        setList(jComboBox1, UIManager.getInstalledLookAndFeels());

        try {
            jbInit();
            initialize(true);
        } catch (Exception e) {
            e.printStackTrace();
        }
        Object comp = jComboBox1.getUI().getAccessibleChild(jComboBox1, 0);
        if (comp instanceof BasicComboPopup) {
            BasicComboPopup popup = (BasicComboPopup) comp;
            popup.getList().getSelectionModel().addListSelectionListener(listSelectionListener);
        }
        // jComboBox1.addItemListener(lafItemListener);
    }

    private void jbInit() throws Exception {
        component1 = Box.createHorizontalStrut(8);
        gridBagLayout1.rowWeights = new double[] { 0.0, 0.0, 0.0, 1.0, 0.0 };
        gridBagLayout1.columnWeights = new double[] { 0.0, 0.0, 0.0 };
        this.setLayout(gridBagLayout1);
        jLabelMLook.setText(Messages.getString("LookSetting.lf"));

        GridBagConstraints gbc_label = new GridBagConstraints();
        gbc_label.insets = new Insets(15, 10, 5, 0);
        gbc_label.anchor = GridBagConstraints.LINE_START;
        gbc_label.gridx = 0;
        gbc_label.gridy = 0;
        add(labelLocale, gbc_label);

        GridBagConstraints gbc_comboBox = new GridBagConstraints();
        gbc_comboBox.anchor = GridBagConstraints.WEST;
        gbc_comboBox.insets = new Insets(15, 0, 5, 5);
        gbc_comboBox.gridx = 1;
        gbc_comboBox.gridy = 0;
        add(comboBox, gbc_comboBox);

        GridBagConstraints gbc_chckbxConfirmationMessageWhen = new GridBagConstraints();
        gbc_chckbxConfirmationMessageWhen.gridwidth = 2;
        gbc_chckbxConfirmationMessageWhen.anchor = GridBagConstraints.WEST;
        gbc_chckbxConfirmationMessageWhen.insets = new Insets(7, 10, 5, 5);
        gbc_chckbxConfirmationMessageWhen.gridx = 0;
        gbc_chckbxConfirmationMessageWhen.gridy = 2;
        add(chckbxConfirmClosing, gbc_chckbxConfirmationMessageWhen);

        GridBagConstraints gbc_txtpnNote = new GridBagConstraints();
        gbc_txtpnNote.anchor = GridBagConstraints.NORTHWEST;
        gbc_txtpnNote.gridwidth = 2;
        gbc_txtpnNote.insets = new Insets(5, 10, 5, 5);
        gbc_txtpnNote.fill = GridBagConstraints.HORIZONTAL;
        gbc_txtpnNote.gridx = 0;
        gbc_txtpnNote.gridy = 3;
        txtpnNote.setEditable(false);
        txtpnNote.setText(Messages.getString("GeneralSetting.txtpnNote")); //$NON-NLS-1$
        add(txtpnNote, gbc_txtpnNote);
        this.add(component1, new GridBagConstraints(2, 4, 1, 1, 1.0, 1.0, GridBagConstraints.WEST,
            GridBagConstraints.NONE, new Insets(0, 0, 0, 0), 0, 0));
        this.add(jLabelMLook, new GridBagConstraints(0, 1, 1, 1, 0.0, 0.0, GridBagConstraints.EAST,
            GridBagConstraints.NONE, new Insets(7, 10, 5, 0), 0, 0));
        this.add(jComboBox1, new GridBagConstraints(1, 1, 1, 1, 0.0, 0.0, GridBagConstraints.WEST,
            GridBagConstraints.NONE, new Insets(7, 2, 5, 15), 5, -2));
    }

    protected void initialize(boolean afirst) {
        chckbxConfirmClosing.setSelected(BundleTools.SYSTEM_PREFERENCES.getBooleanProperty(
            "weasis.confirm.closing", true));//$NON-NLS-1$
        String className = null;
        LookAndFeel currentLAF = javax.swing.UIManager.getLookAndFeel();
        if (currentLAF != null) {
            className = currentLAF.getClass().getName();
        }

        if (className != null) {
            for (int i = 0; i < jComboBox1.getItemCount(); i++) {
                LookInfo look = (LookInfo) jComboBox1.getItemAt(i);
                if (className.equals(look.getClassName())) {
                    oldUILook = look;
                }
            }
        }
        jComboBox1.setSelectedItem(oldUILook);

    }

    public void setList(JComboBox jComboBox, LookAndFeelInfo[] look) {
        jComboBox.removeAllItems();
        for (int i = 0; i < look.length; i++) {
            jComboBox.addItem(new LookInfo(look[i].getName(), look[i].getClassName()));
        }
    }

    @Override
    public void closeAdditionalWindow() {
        BundleTools.SYSTEM_PREFERENCES.putBooleanProperty("weasis.confirm.closing", chckbxConfirmClosing.isSelected()); //$NON-NLS-1$
        LookInfo look = (LookInfo) jComboBox1.getSelectedItem();
        if (look != null) {
            BundleTools.SYSTEM_PREFERENCES.put("weasis.look", look.getClassName()); //$NON-NLS-1$
        }
    }

    @Override
    public void resetoDefaultValues() {
        // TODO Auto-generated method stub
    }

    static class LookInfo {

        private final String name;
        private final String className;

        public LookInfo(String name, String className) {
            this.name = name;
            this.className = className;
        }

        public String getName() {
            return name;
        }

        public String getClassName() {
            return className;
        }

        @Override
        public String toString() {
            return name;
        }
    }
}
