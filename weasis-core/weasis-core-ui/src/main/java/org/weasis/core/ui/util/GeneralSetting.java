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
package org.weasis.core.ui.util;

import java.awt.Component;
import java.awt.Dialog;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.Locale;

import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextPane;
import javax.swing.LookAndFeel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.UIManager.LookAndFeelInfo;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.StyleSheet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.gui.util.AbstractItemDialogPage;
import org.weasis.core.api.gui.util.AbstractProperties;
import org.weasis.core.api.gui.util.GuiExecutor;
import org.weasis.core.api.gui.util.WinUtil;
import org.weasis.core.api.service.BundleTools;
import org.weasis.core.api.service.BundleTools.LEVEL;
import org.weasis.core.ui.Messages;

public class GeneralSetting extends AbstractItemDialogPage {
    private static final Logger LOGGER = LoggerFactory.getLogger(GeneralSetting.class);

    public static final String pageName = Messages.getString("LookSetting.gen"); //$NON-NLS-1$

    private LookInfo oldUILook;

    private Component component1;
    private final GridBagLayout gridBagLayout1 = new GridBagLayout();
    private final JLabel jLabelMLook = new JLabel();
    private final JComboBox jComboBoxlnf = new JComboBox();
    private final JLabel labelLocale = new JLabel(Messages.getString("LookSetting.locale")); //$NON-NLS-1$
    private final JComboBox comboBox = new JLocaleCombo();
    private final JTextPane txtpnNote = new JTextPane();
    private final JCheckBox chckbxConfirmClosing = new JCheckBox(
        Messages.getString("GeneralSetting.closingConfirmation")); //$NON-NLS-1$

    private final JButton button = new JButton(Messages.getString("GeneralSetting.show")); //$NON-NLS-1$
    private final JCheckBox chckbxFileLog = new JCheckBox("Files logging");
    private final JButton btnOptions = new JButton("Options");
    private final JPanel panel = new JPanel();
    private final JLabel lblLogLevel = new JLabel("Log level:");
    private final JComboBox comboBoxLogLevel = new JComboBox(LEVEL.values());
    private final Component horizontalStrut = Box.createHorizontalStrut(20);

    public GeneralSetting() {
        setTitle(pageName);
        setList(jComboBoxlnf, UIManager.getInstalledLookAndFeels());
        try {
            jbInit();
            initialize(true);
        } catch (Exception e) {
            e.printStackTrace();
        }
        // Object comp = jComboBoxlnf.getUI().getAccessibleChild(jComboBoxlnf, 0);
        // if (comp instanceof BasicComboPopup) {
        // BasicComboPopup popup = (BasicComboPopup) comp;
        // popup.getList().getSelectionModel().addListSelectionListener(listSelectionListener);
        // }
    }

    private void jbInit() throws Exception {
        component1 = Box.createHorizontalStrut(8);
        this.setLayout(gridBagLayout1);
        jLabelMLook.setText(Messages.getString("LookSetting.lf")); //$NON-NLS-1$

        GridBagConstraints gbc_label = new GridBagConstraints();
        gbc_label.insets = new Insets(15, 10, 5, 5);
        gbc_label.anchor = GridBagConstraints.LINE_START;
        gbc_label.gridx = 0;
        gbc_label.gridy = 0;
        add(labelLocale, gbc_label);

        GridBagConstraints gbc_comboBox = new GridBagConstraints();
        gbc_comboBox.gridwidth = 3;
        gbc_comboBox.anchor = GridBagConstraints.WEST;
        gbc_comboBox.insets = new Insets(15, 0, 5, 0);
        gbc_comboBox.gridx = 1;
        gbc_comboBox.gridy = 0;
        add(comboBox, gbc_comboBox);

        GridBagConstraints gbc_button = new GridBagConstraints();
        gbc_button.insets = new Insets(7, 5, 5, 15);
        gbc_button.gridx = 2;
        gbc_button.gridy = 1;
        button.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                LookInfo item = (LookInfo) jComboBoxlnf.getSelectedItem();
                final String finalLafClassName = item.getClassName();
                Runnable runnable = new Runnable() {
                    @Override
                    public void run() {
                        try {
                            // JFrame dialog = WeasisWin.getInstance();
                            Dialog dialog = WinUtil.getParentDialog(GeneralSetting.this);
                            UIManager.setLookAndFeel(finalLafClassName);
                            SwingUtilities.updateComponentTreeUI(dialog);
                        } catch (Exception exception) {
                            System.out.println("Can't change look and feel"); //$NON-NLS-1$
                        }
                    }
                };
                GuiExecutor.instance().execute(runnable);
            }
        });
        add(button, gbc_button);

        GridBagConstraints gbc_txtpnNote = new GridBagConstraints();
        gbc_txtpnNote.anchor = GridBagConstraints.WEST;
        gbc_txtpnNote.gridwidth = 4;
        gbc_txtpnNote.insets = new Insets(5, 10, 5, 10);
        gbc_txtpnNote.fill = GridBagConstraints.HORIZONTAL;
        gbc_txtpnNote.gridx = 0;
        gbc_txtpnNote.gridy = 2;
        txtpnNote.setEditable(false);
        txtpnNote.setContentType("text/html"); //$NON-NLS-1$
        StyleSheet ss = ((HTMLEditorKit) txtpnNote.getEditorKit()).getStyleSheet();
        ss.addRule("body {font-family:sans-serif;font-size:12pt;color:#" //$NON-NLS-1$
            + Integer.toHexString((labelLocale.getForeground().getRGB() & 0xffffff) | 0x1000000).substring(1)
            + ";margin-right:0;margin-left:0;font-weight:normal;}"); //$NON-NLS-1$
        txtpnNote.setText(String.format(Messages.getString("GeneralSetting.txtpnNote"), getInstalledLanguages())); //$NON-NLS-1$
        add(txtpnNote, gbc_txtpnNote);

        GridBagConstraints gbc_chckbxConfirmationMessageWhen = new GridBagConstraints();
        gbc_chckbxConfirmationMessageWhen.gridwidth = 4;
        gbc_chckbxConfirmationMessageWhen.anchor = GridBagConstraints.WEST;
        gbc_chckbxConfirmationMessageWhen.insets = new Insets(10, 10, 5, 0);
        gbc_chckbxConfirmationMessageWhen.gridx = 0;
        gbc_chckbxConfirmationMessageWhen.gridy = 3;
        add(chckbxConfirmClosing, gbc_chckbxConfirmationMessageWhen);

        GridBagConstraints gbc_panel = new GridBagConstraints();
        gbc_panel.anchor = GridBagConstraints.WEST;
        gbc_panel.gridwidth = 4;
        gbc_panel.insets = new Insets(5, 10, 5, 10);
        gbc_panel.fill = GridBagConstraints.HORIZONTAL;
        gbc_panel.gridx = 0;
        gbc_panel.gridy = 4;
        FlowLayout flowLayout = (FlowLayout) panel.getLayout();
        flowLayout.setAlignment(FlowLayout.LEADING);
        add(panel, gbc_panel);
        panel.add(lblLogLevel);
        panel.add(comboBoxLogLevel);
        panel.add(horizontalStrut);
        panel.add(chckbxFileLog);
        panel.add(btnOptions);
        this.add(component1, new GridBagConstraints(3, 5, 1, 1, 1.0, 1.0, GridBagConstraints.NORTHWEST,
            GridBagConstraints.BOTH, new Insets(0, 0, 0, 0), 0, 0));
        this.add(jLabelMLook, new GridBagConstraints(0, 1, 1, 1, 0.0, 0.0, GridBagConstraints.EAST,
            GridBagConstraints.NONE, new Insets(7, 10, 5, 5), 0, 0));
        this.add(jComboBoxlnf, new GridBagConstraints(1, 1, 1, 1, 0.0, 0.0, GridBagConstraints.WEST,
            GridBagConstraints.NONE, new Insets(7, 2, 5, 15), 5, -2));
    }

    protected void initialize(boolean afirst) {
        chckbxConfirmClosing.setSelected(BundleTools.SYSTEM_PREFERENCES.getBooleanProperty(
            "weasis.confirm.closing", true));//$NON-NLS-1$

        comboBoxLogLevel.setSelectedItem(LEVEL.getLevel(BundleTools.SYSTEM_PREFERENCES.getProperty(
            BundleTools.LOG_LEVEL, "INFO")));//$NON-NLS-1$
        String fileLogger = BundleTools.SYSTEM_PREFERENCES.getProperty(BundleTools.LOG_FILE, "");//$NON-NLS-1$s
        chckbxFileLog.setSelected(!("".equals(fileLogger.trim())));

        String className = null;
        LookAndFeel currentLAF = javax.swing.UIManager.getLookAndFeel();
        if (currentLAF != null) {
            className = currentLAF.getClass().getName();
        }

        if (className != null) {
            for (int i = 0; i < jComboBoxlnf.getItemCount(); i++) {
                LookInfo look = (LookInfo) jComboBoxlnf.getItemAt(i);
                if (className.equals(look.getClassName())) {
                    oldUILook = look;
                    break;
                }
            }
        }
        if (oldUILook == null) {
            jComboBoxlnf.setSelectedIndex(0);
            oldUILook = (LookInfo) jComboBoxlnf.getSelectedItem();
        } else {
            jComboBoxlnf.setSelectedItem(oldUILook);
        }

    }

    private String getInstalledLanguages() {
        StringBuffer buffer = new StringBuffer();
        String langs = System.getProperty("weasis.languages", null); //$NON-NLS-1$
        if (langs != null) {
            String[] items = langs.split(","); //$NON-NLS-1$
            for (int i = 0; i < items.length; i++) {
                String item = items[i].trim();
                int index = item.indexOf(' ');
                String autor = null;
                String lg;
                if (index > 0) {
                    lg = item.substring(0, index);
                    autor = item.substring(index);
                } else {
                    lg = item;
                }
                Locale l = toLocale(lg);
                if (l == null) {
                    continue;
                }
                buffer.append("<BR>"); //$NON-NLS-1$
                buffer.append(l.getDisplayName());
                if (autor != null) {
                    buffer.append(" - "); //$NON-NLS-1$
                    buffer.append("<i>"); //$NON-NLS-1$
                    buffer.append(autor);
                    buffer.append("</i>"); //$NON-NLS-1$
                }
            }
        }

        return buffer.toString();
    }

    /**
     * <p>
     * Converts a String to a Locale.
     * </p>
     * <p>
     * This method validates the input strictly. The language code must be lowercase. The country code must be
     * uppercase. The separator must be an underscore. The length must be correct.
     * </p>
     * 
     * @param input
     *            the locale String to convert, null returns null
     * @return a Locale, null if null input
     * @throws IllegalArgumentException
     *             if the string is an invalid format
     */
    public static Locale toLocale(String input) {
        if (input == null) {
            return null;
        }

        int len = input.length();
        if (len != 2 && len != 5 && len < 7) {
            return null;
        }

        char ch0 = input.charAt(0);
        char ch1 = input.charAt(1);

        if (ch0 < 'a' || ch0 > 'z' || ch1 < 'a' || ch1 > 'z') {
            return null;
        }

        if (len == 2) {
            return new Locale(input, ""); //$NON-NLS-1$
        }

        if (input.charAt(2) != '_') {
            return null;
        }

        char ch3 = input.charAt(3);
        if (ch3 == '_') {
            return new Locale(input.substring(0, 2), "", input.substring(4)); //$NON-NLS-1$
        }

        char ch4 = input.charAt(4);
        if (ch3 < 'A' || ch3 > 'Z' || ch4 < 'A' || ch4 > 'Z') {
            return null;
        }

        if (len == 5) {
            return new Locale(input.substring(0, 2), input.substring(3, 5));
        }

        if (input.charAt(5) != '_') {
            return null;
        }

        return new Locale(input.substring(0, 2), input.substring(3, 5), input.substring(6));
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

        LEVEL level = (LEVEL) comboBoxLogLevel.getSelectedItem();
        BundleTools.SYSTEM_PREFERENCES.setProperty("org.apache.sling.commons.log.level", level.toString()); //$NON-NLS-1$
        String logFile =
            chckbxFileLog.isSelected() ? AbstractProperties.WEASIS_PATH + File.separator + "log" + File.separator
                + "default.log" : "";
        BundleTools.SYSTEM_PREFERENCES.setProperty("org.apache.sling.commons.log.file", logFile); //$NON-NLS-1$
        BundleTools.createOrUpdateLogger("default.log", new String[] { "org" }, level.toString(), logFile, null, null);

        LookInfo look = (LookInfo) jComboBoxlnf.getSelectedItem();
        if (look != null) {
            BundleTools.SYSTEM_PREFERENCES.put("weasis.look", look.getClassName()); //$NON-NLS-1$
        }
        // save preferences in local file
        BundleTools.saveSystemPreferences();

        // Restore old laf to avoid display issues.
        final String finalLafClassName = oldUILook.getClassName();
        LookAndFeel currentLAF = javax.swing.UIManager.getLookAndFeel();
        if (currentLAF != null && !finalLafClassName.equals(currentLAF.getClass().getName())) {
            Runnable runnable = new Runnable() {
                @Override
                public void run() {
                    try {
                        WinUtil.getParentDialog(GeneralSetting.this).setVisible(false);

                        UIManager.setLookAndFeel(finalLafClassName);
                        for (Window window : Window.getWindows()) {
                            SwingUtilities.updateComponentTreeUI(window);
                        }

                    } catch (Exception exception) {
                        System.out.println("Can't change look and feel"); //$NON-NLS-1$
                    }
                }
            };
            GuiExecutor.instance().execute(runnable);
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
