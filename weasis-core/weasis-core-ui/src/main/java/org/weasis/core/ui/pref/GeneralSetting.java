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
package org.weasis.core.ui.pref;

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
import java.text.DateFormat;
import java.util.Date;

import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTextPane;
import javax.swing.LookAndFeel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.UIManager.LookAndFeelInfo;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.StyleSheet;

import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.gui.util.AbstractItemDialogPage;
import org.weasis.core.api.gui.util.AppProperties;
import org.weasis.core.api.gui.util.GuiExecutor;
import org.weasis.core.api.gui.util.JMVUtils;
import org.weasis.core.api.gui.util.WinUtil;
import org.weasis.core.api.service.AuditLog;
import org.weasis.core.api.service.AuditLog.LEVEL;
import org.weasis.core.api.service.BundleTools;
import org.weasis.core.api.service.WProperties;
import org.weasis.core.api.util.LocalUtil;
import org.weasis.core.api.util.StringUtil;
import org.weasis.core.ui.Messages;

public class GeneralSetting extends AbstractItemDialogPage {
    private static final Logger LOGGER = LoggerFactory.getLogger(GeneralSetting.class);

    public static final String pageName = Messages.getString("GeneralSetting.gen"); //$NON-NLS-1$

    private LookInfo oldUILook;
    private final GridBagLayout gridBagLayout1 = new GridBagLayout();
    private final JLabel jLabelMLook = new JLabel();
    private final JComboBox jComboBoxlnf = new JComboBox();

    private final JLabel labelLocale = new JLabel(Messages.getString("GeneralSetting.language") + StringUtil.COLON); //$NON-NLS-1$
    private final JLocaleLanguage comboBoxLang = new JLocaleLanguage() {
        @Override
        protected void handleChange() {
            comboBoxFormat.refresh();
        }
    };
    private final JLabel labelLocale2 = new JLabel(
        Messages.getString("GeneralSetting.language.data") + StringUtil.COLON); //$NON-NLS-1$
    private final JLocaleFormat comboBoxFormat = new JLocaleFormat() {
        @Override
        protected void handleChange() {
            txtpnNote.setText(getText());
        }
    };

    private final JTextPane txtpnNote = new JTextPane();
    private final JCheckBox chckbxConfirmClosing = new JCheckBox(
        Messages.getString("GeneralSetting.closingConfirmation")); //$NON-NLS-1$

    private final JButton button = new JButton(Messages.getString("GeneralSetting.show")); //$NON-NLS-1$
    private final JCheckBox chckbxFileLog = new JCheckBox(Messages.getString("GeneralSetting.rol_log")); //$NON-NLS-1$
    private final JPanel panel = new JPanel();
    private final JLabel lblLogLevel = new JLabel(Messages.getString("GeneralSetting.log_level") + StringUtil.COLON); //$NON-NLS-1$
    private final JComboBox comboBoxLogLevel = new JComboBox(LEVEL.values());
    private final Component horizontalStrut = Box.createHorizontalStrut(10);
    private final JLabel labelNumber = new JLabel(Messages.getString("GeneralSetting.log_nb") + StringUtil.COLON); //$NON-NLS-1$
    private final JSpinner spinner = new JSpinner();
    private final JLabel labelSize = new JLabel(Messages.getString("GeneralSetting.log_size") + StringUtil.COLON); //$NON-NLS-1$
    private final JSpinner spinner_1 = new JSpinner();
    private final Component horizontalStrut_1 = Box.createHorizontalStrut(10);
    private final Component horizontalStrut_2 = Box.createHorizontalStrut(10);

    public GeneralSetting() {
        super(pageName);
        setComponentPosition(0);
        setList(jComboBoxlnf, UIManager.getInstalledLookAndFeels());
        try {
            JMVUtils.setNumberModel(spinner, getIntPreferences(AuditLog.LOG_FILE_NUMBER, 5, null), 1, 99, 1);
            JMVUtils.setNumberModel(spinner_1, getIntPreferences(AuditLog.LOG_FILE_SIZE, 10, "MB"), 1, 99, 1); //$NON-NLS-1$
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
        this.setLayout(gridBagLayout1);
        jLabelMLook.setText(Messages.getString("GeneralSetting.lf") + StringUtil.COLON); //$NON-NLS-1$

        GridBagConstraints gbc_button = new GridBagConstraints();
        gbc_button.insets = new Insets(7, 5, 5, 15);
        gbc_button.gridx = 2;
        gbc_button.gridy = 0;
        button.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                LookInfo item = (LookInfo) jComboBoxlnf.getSelectedItem();
                final String finalLafClassName = item.getClassName();
                Runnable runnable = new Runnable() {
                    @Override
                    public void run() {
                        try {
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

        GridBagConstraints gbc_label = new GridBagConstraints();
        gbc_label.insets = new Insets(15, 10, 5, 5);
        gbc_label.anchor = GridBagConstraints.LINE_END;
        gbc_label.gridx = 0;
        gbc_label.gridy = 1;
        add(labelLocale, gbc_label);

        GridBagConstraints gbc_comboBox = new GridBagConstraints();
        gbc_comboBox.gridwidth = 3;
        gbc_comboBox.anchor = GridBagConstraints.WEST;
        gbc_comboBox.insets = new Insets(15, 0, 5, 0);
        gbc_comboBox.gridx = 1;
        gbc_comboBox.gridy = 1;
        add(comboBoxLang, gbc_comboBox);

        GridBagConstraints gbc_label2 = new GridBagConstraints();
        gbc_label2.insets = new Insets(5, 10, 5, 5);
        gbc_label2.anchor = GridBagConstraints.LINE_END;
        gbc_label2.gridx = 0;
        gbc_label2.gridy = 2;
        add(labelLocale2, gbc_label2);

        GridBagConstraints gbc_comboBox2 = new GridBagConstraints();
        gbc_comboBox2.gridwidth = 3;
        gbc_comboBox2.anchor = GridBagConstraints.WEST;
        gbc_comboBox2.insets = new Insets(5, 0, 5, 0);
        gbc_comboBox2.gridx = 1;
        gbc_comboBox2.gridy = 2;
        add(comboBoxFormat, gbc_comboBox2);

        GridBagConstraints gbc_txtpnNote = new GridBagConstraints();
        gbc_txtpnNote.anchor = GridBagConstraints.WEST;
        gbc_txtpnNote.gridwidth = 4;
        gbc_txtpnNote.insets = new Insets(5, 10, 5, 10);
        gbc_txtpnNote.fill = GridBagConstraints.HORIZONTAL;
        gbc_txtpnNote.gridx = 0;
        gbc_txtpnNote.gridy = 3;
        txtpnNote.setEditable(false);
        txtpnNote.setContentType("text/html"); //$NON-NLS-1$

        HTMLEditorKit kit = new HTMLEditorKit();
        StyleSheet ss = kit.getStyleSheet();
        ss.addRule("body {font-family:sans-serif;font-size:12pt;background-color:#" + Integer.toHexString((txtpnNote.getBackground().getRGB() & 0xffffff) | 0x1000000).substring(1) + ";color:#" //$NON-NLS-1$ //$NON-NLS-2$
            + Integer.toHexString((txtpnNote.getForeground().getRGB() & 0xffffff) | 0x1000000).substring(1)
            + ";margin:3;font-weight:normal;}"); //$NON-NLS-1$
        txtpnNote.setEditorKit(kit);
        txtpnNote.setText(getText());
        add(txtpnNote, gbc_txtpnNote);

        GridBagConstraints gbc_chckbxConfirmationMessageWhen = new GridBagConstraints();
        gbc_chckbxConfirmationMessageWhen.gridwidth = 4;
        gbc_chckbxConfirmationMessageWhen.anchor = GridBagConstraints.WEST;
        gbc_chckbxConfirmationMessageWhen.insets = new Insets(10, 10, 5, 0);
        gbc_chckbxConfirmationMessageWhen.gridx = 0;
        gbc_chckbxConfirmationMessageWhen.gridy = 4;
        add(chckbxConfirmClosing, gbc_chckbxConfirmationMessageWhen);

        GridBagConstraints gbc_panel = new GridBagConstraints();
        gbc_panel.anchor = GridBagConstraints.WEST;
        gbc_panel.gridwidth = 4;
        gbc_panel.insets = new Insets(5, 10, 5, 10);
        gbc_panel.fill = GridBagConstraints.HORIZONTAL;
        gbc_panel.gridx = 0;
        gbc_panel.gridy = 5;
        FlowLayout flowLayout = (FlowLayout) panel.getLayout();
        flowLayout.setAlignment(FlowLayout.LEADING);
        add(panel, gbc_panel);
        panel.add(lblLogLevel);
        panel.add(comboBoxLogLevel);
        panel.add(horizontalStrut);
        chckbxFileLog.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                checkRolingLog();
            }
        });
        panel.add(chckbxFileLog);

        panel.add(horizontalStrut_1);

        panel.add(labelNumber);

        panel.add(spinner);

        panel.add(horizontalStrut_2);

        panel.add(labelSize);

        panel.add(spinner_1);
        this.add(jLabelMLook, new GridBagConstraints(0, 0, 1, 1, 0.0, 0.0, GridBagConstraints.LINE_END,
            GridBagConstraints.NONE, new Insets(7, 10, 5, 5), 0, 0));
        this.add(jComboBoxlnf, new GridBagConstraints(1, 0, 1, 1, 0.0, 0.0, GridBagConstraints.WEST,
            GridBagConstraints.NONE, new Insets(7, 2, 5, 15), 5, -2));

        JPanel panel_2 = new JPanel();
        FlowLayout flowLayout_1 = (FlowLayout) panel_2.getLayout();
        flowLayout_1.setHgap(10);
        flowLayout_1.setAlignment(FlowLayout.RIGHT);
        flowLayout_1.setVgap(7);
        GridBagConstraints gbc_panel_2 = new GridBagConstraints();
        gbc_panel_2.weighty = 1.0;
        gbc_panel_2.weightx = 1.0;
        gbc_panel_2.anchor = GridBagConstraints.SOUTHWEST;
        gbc_panel_2.gridwidth = 4;
        gbc_panel_2.insets = new Insets(5, 10, 5, 10);
        gbc_panel_2.fill = GridBagConstraints.HORIZONTAL;
        gbc_panel_2.gridx = 0;
        gbc_panel_2.gridy = 6;
        add(panel_2, gbc_panel_2);
        JButton btnNewButton = new JButton(Messages.getString("restore.values")); //$NON-NLS-1$
        panel_2.add(btnNewButton);
        btnNewButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                resetoDefaultValues();
                initialize(false);
            }
        });

    }

    private String getText() {
        return String.format(
            Messages.getString("GeneralSetting.txtNote"),//$NON-NLS-1$
            new Object[] { LocalUtil.getDateInstance(DateFormat.SHORT).format(new Date()),
                LocalUtil.getDateInstance(DateFormat.MEDIUM).format(new Date()),
                LocalUtil.getNumberInstance().format(2543456.3465) });
    }

    private void checkRolingLog() {
        boolean rolling = chckbxFileLog.isSelected();
        spinner.setEnabled(rolling);
        spinner_1.setEnabled(rolling);
    }

    private static int getIntPreferences(String key, int defaultvalue, String removedSuffix) {
        if (key != null) {
            String s = BundleTools.SYSTEM_PREFERENCES.getProperty(key);
            if (s != null) {
                if (removedSuffix != null) {
                    int index = s.lastIndexOf(removedSuffix);
                    if (index > 0) {
                        s = s.substring(0, index);
                    }
                }
                try {
                    return Integer.parseInt(s);
                } catch (NumberFormatException ignore) {
                }
            }
        }
        return defaultvalue;
    }

    protected void initialize(boolean afirst) {
        WProperties prfs = BundleTools.SYSTEM_PREFERENCES;
        chckbxConfirmClosing.setSelected(prfs.getBooleanProperty(BundleTools.CONFIRM_CLOSE, false));
        comboBoxLogLevel.setSelectedItem(LEVEL.getLevel(prfs.getProperty(AuditLog.LOG_LEVEL, "INFO")));//$NON-NLS-1$
        chckbxFileLog.setSelected(StringUtil.hasText(prfs.getProperty(AuditLog.LOG_FILE, ""))); //$NON-NLS-1$
        spinner.setValue(getIntPreferences(AuditLog.LOG_FILE_NUMBER, 5, null));
        spinner_1.setValue(getIntPreferences(AuditLog.LOG_FILE_SIZE, 10, "MB")); //$NON-NLS-1$
        checkRolingLog();

        comboBoxLang.selectLocale(prfs.getProperty("locale.lang.code")); //$NON-NLS-1$
        comboBoxFormat.selectLocale();

        String className = prfs.getProperty("weasis.look"); //$NON-NLS-1$
        if (className == null) {
            LookAndFeel currentLAF = javax.swing.UIManager.getLookAndFeel();
            if (currentLAF != null) {
                className = currentLAF.getClass().getName();
            }
        }
        LookInfo oldLaf = null;
        if (className != null) {
            for (int i = 0; i < jComboBoxlnf.getItemCount(); i++) {
                LookInfo look = (LookInfo) jComboBoxlnf.getItemAt(i);
                if (className.equals(look.getClassName())) {
                    oldLaf = look;
                    break;
                }
            }
        }
        if (oldLaf == null) {
            jComboBoxlnf.setSelectedIndex(0);
            oldLaf = (LookInfo) jComboBoxlnf.getSelectedItem();
        } else {
            jComboBoxlnf.setSelectedItem(oldLaf);
        }
        if (afirst) {
            oldUILook = oldLaf;
        }

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
        BundleTools.SYSTEM_PREFERENCES.setProperty(AuditLog.LOG_LEVEL, level.toString());
        BundleTools.SYSTEM_PREFERENCES.setProperty(AuditLog.LOG_FILE_ACTIVATION,
            String.valueOf(chckbxFileLog.isSelected()));
        String logFile =
            chckbxFileLog.isSelected() ? AppProperties.WEASIS_PATH + File.separator + "log" + File.separator //$NON-NLS-1$
                + "default.log" : ""; //$NON-NLS-1$ //$NON-NLS-2$
        BundleTools.SYSTEM_PREFERENCES.setProperty(AuditLog.LOG_FILE, logFile);
        String fileNb = null;
        String fileSize = null;
        if (chckbxFileLog.isSelected()) {
            fileNb = spinner.getValue().toString();
            fileSize = spinner_1.getValue().toString() + "MB"; //$NON-NLS-1$
            BundleTools.SYSTEM_PREFERENCES.setProperty(AuditLog.LOG_FILE_NUMBER, fileNb);
            BundleTools.SYSTEM_PREFERENCES.setProperty(AuditLog.LOG_FILE_SIZE, fileSize);
        }
        String pattern =
            BundleTools.SYSTEM_PREFERENCES.getProperty(AuditLog.LOG_PATTERN,
                "{0,date,dd.MM.yyyy HH:mm:ss.SSS} *{4}* [{2}]() {3} {5}"); //$NON-NLS-1$
        BundleContext context = FrameworkUtil.getBundle(this.getClass()).getBundleContext();
        AuditLog.createOrUpdateLogger(context,
            "default.log", new String[] { "org" }, level.toString(), logFile, pattern, fileNb, //$NON-NLS-1$ //$NON-NLS-2$
            fileSize);

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
        BundleTools.SYSTEM_PREFERENCES.resetProperty(BundleTools.CONFIRM_CLOSE, "false");//$NON-NLS-1$

        // Reset properties used by OSGI service (Sling Logger)
        BundleTools.SYSTEM_PREFERENCES.resetServiceProperty(AuditLog.LOG_LEVEL, "INFO"); //$NON-NLS-1$
        BundleTools.SYSTEM_PREFERENCES.resetServiceProperty(AuditLog.LOG_FILE, ""); //$NON-NLS-1$
        BundleTools.SYSTEM_PREFERENCES.resetServiceProperty(AuditLog.LOG_FILE_NUMBER, "5"); //$NON-NLS-1$
        BundleTools.SYSTEM_PREFERENCES.resetServiceProperty(AuditLog.LOG_FILE_SIZE, "10MB"); //$NON-NLS-1$

        BundleTools.SYSTEM_PREFERENCES.resetProperty("locale.lang.code", "en"); //$NON-NLS-1$ //$NON-NLS-2$
        // Reset cache of locale format
        LocalUtil.setLocaleFormat(null);
        // Reset format to the config.properties value or null (default system value)
        BundleTools.SYSTEM_PREFERENCES.resetProperty("locale.format.code", null);//$NON-NLS-1$ 

        BundleTools.SYSTEM_PREFERENCES.resetProperty("weasis.look", null); //$NON-NLS-1$ 

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
