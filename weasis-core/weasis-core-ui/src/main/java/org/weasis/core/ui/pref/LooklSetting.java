/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.pref;

import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.FlatSystemProperties;
import java.awt.Window;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Objects;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JSpinner;
import javax.swing.LookAndFeel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.UIManager.LookAndFeelInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.gui.util.AbstractItemDialogPage;
import org.weasis.core.api.gui.util.GuiExecutor;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.service.BundleTools;
import org.weasis.core.api.service.WProperties;
import org.weasis.core.ui.Messages;
import org.weasis.core.util.StringUtil;

public class LooklSetting extends AbstractItemDialogPage {
  private static final Logger LOGGER = LoggerFactory.getLogger(LooklSetting.class);

  public static final String PAGE_NAME = "Appearance";

  private final JLabel jLabelMLook = new JLabel();
  private final JComboBox<LookInfo> jComboBoxlnf = new JComboBox<>();
  private final JButton button = new JButton(Messages.getString("GeneralSetting.show"));

  private final JRadioButton systemScaleRadio = new JRadioButton("Use the system scale factor");
  private final JRadioButton userScaleRadio = new JRadioButton("Use a custom scale factor (%)");
  private final ButtonGroup buttonGroup = new ButtonGroup();
  private final JSpinner spinner1 = new JSpinner();

  private LookInfo oldUILook;

  public LooklSetting() {
    super(PAGE_NAME, 102);
    setList(jComboBoxlnf, UIManager.getInstalledLookAndFeels());
    try {
      GuiUtils.setNumberModel(spinner1, 100, 20, 400, 5);
      jbInit();
      initialize(true);
    } catch (Exception e) {
      LOGGER.error("Cannot initialize GeneralSetting", e);
    }
  }

  private void jbInit() {
    jLabelMLook.setText("Theme" + StringUtil.COLON);

    add(GuiUtils.getComponentsInJPanel(3, 10, jLabelMLook, jComboBoxlnf, button));
    add(GuiUtils.createVerticalStrut(15));

    JPanel panel = new JPanel();
    panel.setBorder(GuiUtils.getTitledBorder("Display Scale Factor"));
    panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
    panel.add(GuiUtils.getComponentsInJPanel(5, 0, systemScaleRadio));
    panel.add(Box.createVerticalGlue());
    panel.add(GuiUtils.getComponentsInJPanel(5, 0, userScaleRadio, spinner1));
    add(panel);

    this.buttonGroup.add(systemScaleRadio);
    this.buttonGroup.add(userScaleRadio);
    systemScaleRadio.addActionListener(e -> spinner1.setEnabled(false));
    userScaleRadio.addActionListener(e -> spinner1.setEnabled(true));

    button.addActionListener(
        e -> {
          LookInfo item = (LookInfo) jComboBoxlnf.getSelectedItem();
          final String finalLafClassName = Objects.requireNonNull(item).getClassName();
          Runnable runnable =
              () -> {
                try {
                  UIManager.setLookAndFeel(finalLafClassName);
                  for (final Window w : Window.getWindows()) {
                    SwingUtilities.updateComponentTreeUI(w);
                  }
                } catch (Exception e1) {
                  LOGGER.error("Can't change look and feel", e1);
                }
              };
          GuiExecutor.instance().execute(runnable);
        });

    add(GuiUtils.getBoxYLastElement(5));
    getProperties().setProperty(PreferenceDialog.KEY_SHOW_RESTORE, Boolean.TRUE.toString());
    getProperties().setProperty(PreferenceDialog.KEY_HELP, "theme");
  }

  protected void initialize(boolean afirst) {
    WProperties prfs = BundleTools.SYSTEM_PREFERENCES;

    String className = prfs.getProperty("weasis.theme");
    if (className == null) {
      LookAndFeel currentLAF = UIManager.getLookAndFeel();
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

    float scale = parseScaleFactor(prfs.getProperty(FlatSystemProperties.UI_SCALE));
    if (scale <= 0F) {
      scale = 1.0F;
      systemScaleRadio.doClick();
    } else {
      userScaleRadio.doClick();
    }
    spinner1.setValue(Math.round(scale * 100));
  }

  public void setList(JComboBox<LookInfo> jComboBox, LookAndFeelInfo[] look) {
    jComboBox.removeAllItems();
    for (LookAndFeelInfo lookAndFeelInfo :
        Arrays.stream(look).sorted(Comparator.comparing(LookAndFeelInfo::getName)).toList()) {
      jComboBox.addItem(new LookInfo(lookAndFeelInfo.getName(), lookAndFeelInfo.getClassName()));
    }
  }

  private static float parseScaleFactor(String s) {
    if (s == null) return -1;

    float units = 1;
    if (s.endsWith("x")) s = s.substring(0, s.length() - 1);
    else if (s.endsWith("dpi")) {
      units = 96;
      s = s.substring(0, s.length() - 3);
    } else if (s.endsWith("%")) {
      units = 100;
      s = s.substring(0, s.length() - 1);
    }

    try {
      float scale = Float.parseFloat(s);
      return scale > 0 ? scale / units : -1;
    } catch (NumberFormatException ex) {
      return -1;
    }
  }

  @Override
  public void closeAdditionalWindow() {
    LookInfo look = (LookInfo) jComboBoxlnf.getSelectedItem();
    if (look != null) {
      BundleTools.SYSTEM_PREFERENCES.setProperty("weasis.theme", look.getClassName());
    }
    // save preferences
    BundleTools.saveSystemPreferences();

    // Restore old laf to avoid display issues.
    final String finalLafClassName = oldUILook.getClassName();
    LookAndFeel currentLAF = UIManager.getLookAndFeel();
    if (currentLAF != null && !finalLafClassName.equals(currentLAF.getClass().getName())) {
      Runnable runnable =
          () -> {
            try {
              UIManager.setLookAndFeel(finalLafClassName);
              FlatLaf.updateUI();
            } catch (Exception e) {
              LOGGER.error("Can't change look and feel", e);
            }
          };
      GuiExecutor.instance().execute(runnable);
    }

    String scale = "-1";
    if (userScaleRadio.isSelected()) {
      if (spinner1.getValue() instanceof Integer val) scale = String.valueOf(val / 100.f);
    }
    BundleTools.SYSTEM_PREFERENCES.setProperty(FlatSystemProperties.UI_SCALE, scale);
  }

  @Override
  public void resetToDefaultValues() {
    BundleTools.SYSTEM_PREFERENCES.resetProperty("weasis.theme", null);
    BundleTools.SYSTEM_PREFERENCES.resetProperty("flatlaf.uiScale", null);

    initialize(false);
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
