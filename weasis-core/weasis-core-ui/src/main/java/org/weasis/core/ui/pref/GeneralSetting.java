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
package org.weasis.core.ui.pref;

import java.awt.Component;
import java.awt.Dialog;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.io.File;
import java.time.ZonedDateTime;
import java.time.format.FormatStyle;
import java.util.Enumeration;

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

    public static final String PAGE_NAME = Messages.getString("GeneralSetting.gen"); //$NON-NLS-1$

    private LookInfo oldUILook;
    private final GridBagLayout gridBagLayout1 = new GridBagLayout();
    private final JLabel jLabelMLook = new JLabel();
    private final JComboBox<LookInfo> jComboBoxlnf = new JComboBox<>();

    private final JTextPane txtpnNote = new JTextPane();
    private final JLabel labelLocale2 =
        new JLabel(Messages.getString("GeneralSetting.language.data") + StringUtil.COLON); //$NON-NLS-1$
    @SuppressWarnings("serial")
    private final JLocaleFormat comboBoxFormat = new JLocaleFormat() {
        @Override
        public void valueHasChanged() {
            txtpnNote.setText(getText());
        }
    };

    private final JLabel labelLocale = new JLabel(Messages.getString("GeneralSetting.language") + StringUtil.COLON); //$NON-NLS-1$
    @SuppressWarnings("serial")
    private final JLocaleLanguage comboBoxLang = new JLocaleLanguage() {
        @Override
        public void valueHasChanged() {
            comboBoxFormat.refresh();
        }
    };

    private final JCheckBox chckbxConfirmClosing =
        new JCheckBox(Messages.getString("GeneralSetting.closingConfirmation")); //$NON-NLS-1$

    private final JButton button = new JButton(Messages.getString("GeneralSetting.show")); //$NON-NLS-1$
    private final JCheckBox chckbxFileLog = new JCheckBox(Messages.getString("GeneralSetting.rol_log")); //$NON-NLS-1$
    private final JPanel panel = new JPanel();
    private final JLabel lblLogLevel = new JLabel(Messages.getString("GeneralSetting.log_level") + StringUtil.COLON); //$NON-NLS-1$
    private final JComboBox<LEVEL> comboBoxLogLevel = new JComboBox<>(LEVEL.values());
    private final Component horizontalStrut = Box.createHorizontalStrut(10);
    private final JLabel labelNumber = new JLabel(Messages.getString("GeneralSetting.log_nb") + StringUtil.COLON); //$NON-NLS-1$
    private final JSpinner spinner = new JSpinner();
    private final JLabel labelSize = new JLabel(Messages.getString("GeneralSetting.log_size") + StringUtil.COLON); //$NON-NLS-1$
    private final JSpinner spinner1 = new JSpinner();
    private final Component horizontalStrut1 = Box.createHorizontalStrut(10);
    private final Component horizontalStrut2 = Box.createHorizontalStrut(10);
    private final JPanel panel1 = new JPanel();
    private final JLabel lblStacktraceLimit =
        new JLabel(Messages.getString("GeneralSetting.stack_limit") + StringUtil.COLON); //$NON-NLS-1$
    private final JComboBox<String> comboBoxStackLimit =
        new JComboBox<>(new String[] { "", "0", "1", "3", "5", "10", "20", "50", "100" }); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$ //$NON-NLS-7$ //$NON-NLS-8$ //$NON-NLS-9$

    public GeneralSetting() {
        super(PAGE_NAME);
        setComponentPosition(0);
        setList(jComboBoxlnf, UIManager.getInstalledLookAndFeels());
        try {
            JMVUtils.setNumberModel(spinner, getIntPreferences(AuditLog.LOG_FILE_NUMBER, 5, null), 1, 99, 1);
            JMVUtils.setNumberModel(spinner1, getIntPreferences(AuditLog.LOG_FILE_SIZE, 10, "MB"), 1, 99, 1); //$NON-NLS-1$
            jbInit();
            initialize(true);
        } catch (Exception e) {
            LOGGER.error("Cannot initialize GeneralSetting", e); //$NON-NLS-1$
        }
    }

    private void jbInit() {
        this.setLayout(gridBagLayout1);
        jLabelMLook.setText(Messages.getString("GeneralSetting.lf") + StringUtil.COLON); //$NON-NLS-1$

        GridBagConstraints gbcButton = new GridBagConstraints();
        gbcButton.insets = new Insets(7, 5, 5, 15);
        gbcButton.gridx = 2;
        gbcButton.gridy = 0;
        button.addActionListener(e -> {
            LookInfo item = (LookInfo) jComboBoxlnf.getSelectedItem();
            final String finalLafClassName = item.getClassName();
            Runnable runnable = () -> {
                try {
                    Dialog dialog = WinUtil.getParentDialog(GeneralSetting.this);
                    UIManager.setLookAndFeel(finalLafClassName);
                    SwingUtilities.updateComponentTreeUI(dialog);
                } catch (Exception e1) {
                    LOGGER.error("Can't change look and feel", e1); //$NON-NLS-1$
                }
            };
            GuiExecutor.instance().execute(runnable);
        });
        add(button, gbcButton);

        GridBagConstraints gbcLabel = new GridBagConstraints();
        gbcLabel.insets = new Insets(15, 10, 5, 5);
        gbcLabel.anchor = GridBagConstraints.LINE_END;
        gbcLabel.gridx = 0;
        gbcLabel.gridy = 1;
        add(labelLocale, gbcLabel);

        GridBagConstraints gbcComboBox = new GridBagConstraints();
        gbcComboBox.gridwidth = 3;
        gbcComboBox.anchor = GridBagConstraints.WEST;
        gbcComboBox.insets = new Insets(15, 0, 5, 0);
        gbcComboBox.gridx = 1;
        gbcComboBox.gridy = 1;
        add(comboBoxLang, gbcComboBox);

        GridBagConstraints gbcLabel2 = new GridBagConstraints();
        gbcLabel2.insets = new Insets(5, 10, 5, 5);
        gbcLabel2.anchor = GridBagConstraints.LINE_END;
        gbcLabel2.gridx = 0;
        gbcLabel2.gridy = 2;
        add(labelLocale2, gbcLabel2);

        GridBagConstraints gbcComboBox2 = new GridBagConstraints();
        gbcComboBox2.gridwidth = 3;
        gbcComboBox2.anchor = GridBagConstraints.WEST;
        gbcComboBox2.insets = new Insets(5, 0, 5, 0);
        gbcComboBox2.gridx = 1;
        gbcComboBox2.gridy = 2;
        add(comboBoxFormat, gbcComboBox2);

        GridBagConstraints gbcTxtpnNote = new GridBagConstraints();
        gbcTxtpnNote.anchor = GridBagConstraints.WEST;
        gbcTxtpnNote.gridwidth = 4;
        gbcTxtpnNote.insets = new Insets(5, 10, 5, 10);
        gbcTxtpnNote.fill = GridBagConstraints.HORIZONTAL;
        gbcTxtpnNote.gridx = 0;
        gbcTxtpnNote.gridy = 3;
        txtpnNote.setEditorKit(JMVUtils.buildHTMLEditorKit(txtpnNote));
        txtpnNote.setContentType("text/html"); //$NON-NLS-1$
        txtpnNote.setEditable(false);
        txtpnNote.setText(getText());
        add(txtpnNote, gbcTxtpnNote);

        GridBagConstraints gbcChckbxConfirmationMessageWhen = new GridBagConstraints();
        gbcChckbxConfirmationMessageWhen.gridwidth = 4;
        gbcChckbxConfirmationMessageWhen.anchor = GridBagConstraints.WEST;
        gbcChckbxConfirmationMessageWhen.insets = new Insets(10, 10, 5, 0);
        gbcChckbxConfirmationMessageWhen.gridx = 0;
        gbcChckbxConfirmationMessageWhen.gridy = 4;
        add(chckbxConfirmClosing, gbcChckbxConfirmationMessageWhen);

        GridBagConstraints gbcPanel = new GridBagConstraints();
        gbcPanel.anchor = GridBagConstraints.WEST;
        gbcPanel.gridwidth = 4;
        gbcPanel.insets = new Insets(5, 5, 0, 10);
        gbcPanel.fill = GridBagConstraints.HORIZONTAL;
        gbcPanel.gridx = 0;
        gbcPanel.gridy = 5;
        FlowLayout flowLayout = (FlowLayout) panel.getLayout();
        flowLayout.setAlignment(FlowLayout.LEADING);
        add(panel, gbcPanel);
        chckbxFileLog.addActionListener(e -> checkRolingLog());
        panel.add(chckbxFileLog);

        panel.add(horizontalStrut1);

        panel.add(labelNumber);

        panel.add(spinner);

        panel.add(horizontalStrut2);

        panel.add(labelSize);

        panel.add(spinner1);
        this.add(jLabelMLook, new GridBagConstraints(0, 0, 1, 1, 0.0, 0.0, GridBagConstraints.LINE_END,
            GridBagConstraints.NONE, new Insets(7, 10, 5, 5), 0, 0));
        this.add(jComboBoxlnf, new GridBagConstraints(1, 0, 1, 1, 0.0, 0.0, GridBagConstraints.WEST,
            GridBagConstraints.NONE, new Insets(7, 2, 5, 15), 5, -2));

        GridBagConstraints gbcPanel1 = new GridBagConstraints();
        gbcPanel1.gridwidth = 4;
        gbcPanel1.insets = new Insets(0, 10, 5, 10);
        gbcPanel1.fill = GridBagConstraints.BOTH;
        gbcPanel1.gridx = 0;
        gbcPanel1.gridy = 6;
        FlowLayout flowLayout2 = (FlowLayout) panel1.getLayout();
        flowLayout2.setAlignment(FlowLayout.LEADING);
        add(panel1, gbcPanel1);
        panel1.add(lblLogLevel);

        JPanel panel2 = new JPanel();
        FlowLayout flowLayout1 = (FlowLayout) panel2.getLayout();
        flowLayout1.setHgap(10);
        flowLayout1.setAlignment(FlowLayout.RIGHT);
        flowLayout1.setVgap(7);
        GridBagConstraints gbcPanel2 = new GridBagConstraints();
        gbcPanel2.weighty = 1.0;
        gbcPanel2.weightx = 1.0;
        gbcPanel2.anchor = GridBagConstraints.SOUTHWEST;
        gbcPanel2.gridwidth = 4;
        gbcPanel2.insets = new Insets(5, 10, 0, 10);
        gbcPanel2.fill = GridBagConstraints.HORIZONTAL;
        gbcPanel2.gridx = 0;
        gbcPanel2.gridy = 7;
        add(panel2, gbcPanel2);
        JButton btnNewButton = new JButton(Messages.getString("restore.values")); //$NON-NLS-1$
        panel2.add(btnNewButton);
        btnNewButton.addActionListener(e -> {
            resetoDefaultValues();
            initialize(false);
        });

    }

    private static String getText() {
        ZonedDateTime now = ZonedDateTime.now();
        return String.format(Messages.getString("GeneralSetting.txtNote"), //$NON-NLS-1$
            new Object[] { LocalUtil.getDateTimeFormatter(FormatStyle.SHORT).format(now),
                LocalUtil.getDateTimeFormatter(FormatStyle.MEDIUM).format(now),
                LocalUtil.getDateTimeFormatter(FormatStyle.LONG).format(now),
                LocalUtil.getDateTimeFormatter(FormatStyle.FULL).format(now),
                LocalUtil.getNumberInstance().format(2543456.3465) });
    }

    private void checkRolingLog() {
        boolean rolling = chckbxFileLog.isSelected();
        spinner.setEnabled(rolling);
        spinner1.setEnabled(rolling);
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
        panel1.add(comboBoxLogLevel);
        comboBoxLogLevel.setSelectedItem(LEVEL.getLevel(prfs.getProperty(AuditLog.LOG_LEVEL, "INFO")));//$NON-NLS-1$
        panel1.add(horizontalStrut);

        panel1.add(lblStacktraceLimit);

        int limit = getIntPreferences(AuditLog.LOG_STACKTRACE_LIMIT, 3, null);
        if (limit > 0 && limit != 1 && limit != 3 && limit != 5 && limit != 10 && limit != 20 && limit != 50
            && limit != 100) {
            comboBoxStackLimit.addItem(Integer.toString(limit));
        }
        comboBoxStackLimit.setSelectedItem(limit >= 0 ? Integer.toString(limit) : "");// $NON-NLS-1$ //$NON-NLS-1$
        panel1.add(comboBoxStackLimit);
        chckbxFileLog.setSelected(StringUtil.hasText(prfs.getProperty(AuditLog.LOG_FILE, ""))); //$NON-NLS-1$
        spinner.setValue(getIntPreferences(AuditLog.LOG_FILE_NUMBER, 5, null));
        spinner1.setValue(getIntPreferences(AuditLog.LOG_FILE_SIZE, 10, "MB")); //$NON-NLS-1$
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
                LookInfo look = jComboBoxlnf.getItemAt(i);
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

    public void setList(JComboBox<LookInfo> jComboBox, LookAndFeelInfo[] look) {
        jComboBox.removeAllItems();
        for (int i = 0; i < look.length; i++) {
            jComboBox.addItem(new LookInfo(look[i].getName(), look[i].getClassName()));
        }
    }

    @Override
    public void closeAdditionalWindow() {
        BundleTools.SYSTEM_PREFERENCES.putBooleanProperty("weasis.confirm.closing", chckbxConfirmClosing.isSelected()); //$NON-NLS-1$

        String limit = (String) comboBoxStackLimit.getSelectedItem();
        BundleTools.SYSTEM_PREFERENCES.setProperty(AuditLog.LOG_STACKTRACE_LIMIT,
            StringUtil.hasText(limit) ? limit : "-1"); //$NON-NLS-1$

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
            fileSize = spinner1.getValue().toString() + "MB"; //$NON-NLS-1$
            BundleTools.SYSTEM_PREFERENCES.setProperty(AuditLog.LOG_FILE_NUMBER, fileNb);
            BundleTools.SYSTEM_PREFERENCES.setProperty(AuditLog.LOG_FILE_SIZE, fileSize);
        }
        String pattern = BundleTools.SYSTEM_PREFERENCES.getProperty(AuditLog.LOG_PATTERN,
            "{0,date,dd.MM.yyyy HH:mm:ss.SSS} *{4}* [{2}] {3}: {5}"); //$NON-NLS-1$
        BundleContext context = FrameworkUtil.getBundle(this.getClass()).getBundleContext();
        AuditLog.createOrUpdateLogger(context, "default.log", new String[] { "org" }, level.toString(), logFile, //$NON-NLS-1$ //$NON-NLS-2$
            pattern, fileNb, fileSize, limit);

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
            Runnable runnable = () -> {
                try {
                    WinUtil.getParentDialog(GeneralSetting.this).setVisible(false);

                    UIManager.setLookAndFeel(finalLafClassName);
                    for (Window window : Window.getWindows()) {
                        SwingUtilities.updateComponentTreeUI(window);
                    }

                } catch (Exception e) {
                    LOGGER.error("Can't change look and feel", e); //$NON-NLS-1$
                }
            };
            GuiExecutor.instance().execute(runnable);
        }
    }

    @Override
    public void resetoDefaultValues() {
        BundleTools.SYSTEM_PREFERENCES.resetProperty(BundleTools.CONFIRM_CLOSE, "false");//$NON-NLS-1$

        // Reset properties used by OSGI service (Sling Logger)
        BundleTools.SYSTEM_PREFERENCES.resetServiceProperty(AuditLog.LOG_STACKTRACE_LIMIT, "3"); //$NON-NLS-1$
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

    public static String setLookAndFeel(String look) {
        // Do not display metal LAF in bold, it is ugly
        UIManager.put("swing.boldMetal", Boolean.FALSE); //$NON-NLS-1$
        // Display slider value is set to false (already in all LAF by the panel title), used by GTK LAF
        UIManager.put("Slider.paintValue", Boolean.FALSE); //$NON-NLS-1$

        String laf = getAvailableLookAndFeel(look);
        try {
            UIManager.setLookAndFeel(laf);
        } catch (Exception e) {
            laf = UIManager.getSystemLookAndFeelClassName();
            LOGGER.error("Unable to set the Look&Feel", e); //$NON-NLS-1$
        }
        // Fix font issue for displaying some Asiatic characters. Some L&F have special fonts.
        setUIFont(new javax.swing.plaf.FontUIResource(Font.SANS_SERIF, Font.PLAIN, 12));
        return laf;
    }

    public static String getAvailableLookAndFeel(String look) {
        UIManager.LookAndFeelInfo[] lafs = UIManager.getInstalledLookAndFeels();
        String laf = null;
        if (look != null) {
            for (int i = 0, n = lafs.length; i < n; i++) {
                if (lafs[i].getClassName().equals(look)) {
                    laf = look;
                    break;
                }
            }
        }
        if (laf == null) {
            if (AppProperties.OPERATING_SYSTEM.startsWith("mac")) { //$NON-NLS-1$
                laf = "com.apple.laf.AquaLookAndFeel"; //$NON-NLS-1$
            } else {
                // Try to set Nimbus, concurrent thread issue
                // http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=6785663
                for (int i = 0, n = lafs.length; i < n; i++) {
                    if ("Nimbus".equals(lafs[i].getName())) { //$NON-NLS-1$
                        laf = lafs[i].getClassName();
                        break;
                    }
                }
            }
            // Should never happen
            if (laf == null) {
                laf = UIManager.getSystemLookAndFeelClassName();
            }

        }
        return laf;
    }

    public static void setUIFont(javax.swing.plaf.FontUIResource font) {
        Enumeration<Object> keys = UIManager.getDefaults().keys();
        while (keys.hasMoreElements()) {
            Object key = keys.nextElement();
            if (UIManager.get(key) instanceof javax.swing.plaf.FontUIResource) {
                UIManager.put(key, font);
            }
        }
    }
}
