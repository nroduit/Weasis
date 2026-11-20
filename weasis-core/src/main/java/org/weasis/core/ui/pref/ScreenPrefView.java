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

import com.formdev.flatlaf.util.SystemFileChooser;
import com.formdev.flatlaf.util.SystemFileChooser.FileNameExtensionFilter;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dialog.ModalityType;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Paint;
import java.awt.Rectangle;
import java.awt.Stroke;
import java.awt.Window;
import java.awt.event.ItemEvent;
import java.io.IOException;
import java.nio.file.Path;
import java.text.NumberFormat;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFormattedTextField;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import org.weasis.core.Messages;
import org.weasis.core.api.command.ScreenRole;
import org.weasis.core.api.gui.util.AbstractItemDialogPage;
import org.weasis.core.api.gui.util.DecFormatter;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.gui.util.WinUtil;
import org.weasis.core.api.image.color.ColorManagement;
import org.weasis.core.api.image.color.IccProfile;
import org.weasis.core.api.image.util.Unit;
import org.weasis.core.api.setting.CoreSettings;
import org.weasis.core.api.setting.Settings;
import org.weasis.core.ui.editor.ExternalDisplay;
import org.weasis.core.ui.editor.ScreenRoles;
import org.weasis.core.ui.editor.image.DisplayColor;
import org.weasis.core.ui.editor.image.dockable.MeasureTool;
import org.weasis.core.util.StringUtil;

public class ScreenPrefView extends AbstractItemDialogPage {

  public ScreenPrefView() {
    super(Messages.getString("ScreenPrefView.monitors"), 108);
    getProperties()
        .setProperty(
            PreferenceDialog.KEY_HELP,
            "read-studies/adjust/zoom/#real-world-size-display"); // NON-NLS

    final JComboBox<String> defMonitorComboBox = new JComboBox<>();
    List<Monitor> monitors = MeasureTool.viewSetting.getMonitors();
    for (int i = 0; i < monitors.size(); i++) {
      final Monitor monitor = monitors.get(i);
      Rectangle mb = monitor.getBounds();
      StringBuilder buf = new StringBuilder();
      buf.append(i + 1);
      buf.append(". ");
      buf.append(Messages.getString("ScreenPrefView.monitor"));
      buf.append(StringUtil.COLON_AND_SPACE);
      buf.append(monitor.getMonitorID());
      buf.append(".");
      buf.append(mb.width);
      buf.append("x"); // NON-NLS
      buf.append(mb.height);
      final String title = buf.toString();
      defMonitorComboBox.addItem(title);

      final JLabel monitorLabel = new JLabel(buildMonitorLabel(title, monitor));

      JButton realZoomButton = new JButton(Messages.getString("ScreenPrefView.sp_calib"));
      realZoomButton.addActionListener(
          e -> {
            final CalibrationDialog dialog =
                new CalibrationDialog(
                    WinUtil.getParentFrame((Component) e.getSource()),
                    title,
                    ModalityType.APPLICATION_MODAL,
                    monitor);
            dialog.setBounds(ExternalDisplay.usableBounds(monitor.getGraphicsDevice()));
            dialog.setVisible(true);
            monitorLabel.setText(buildMonitorLabel(title, monitor));
          });
      realZoomButton.setToolTipText(Messages.getString("ScreenPrefView.calib_real"));
      add(GuiUtils.getFlowLayoutPanel(monitorLabel, realZoomButton));
      add(GuiUtils.getFlowLayoutPanel(roleLabel(), roleComboBox(monitor.getMonitorID())));
      add(screenProfilePanel(monitor.getMonitorID()));
    }

    int defIndex = getDefaultMonitor();
    if (defIndex < 0 || defIndex >= defMonitorComboBox.getItemCount()) {
      defIndex = 0;
    }
    defMonitorComboBox.setSelectedIndex(defIndex);
    defMonitorComboBox.addItemListener(
        e -> {
          if (e.getStateChange() == ItemEvent.SELECTED) {
            JComboBox<?> comboBox = (JComboBox<?>) e.getSource();
            GuiUtils.getUICore()
                .getLocalPersistence()
                .putIntProperty("default.monitor", comboBox.getSelectedIndex());
          }
        });

    final JLabel presetsLabel =
        new JLabel(Messages.getString("ScreenPrefView.def_monitor") + StringUtil.COLON);
    add(
        GuiUtils.getFlowLayoutPanel(
            FlowLayout.LEADING,
            ITEM_SEPARATOR_SMALL,
            ITEM_SEPARATOR,
            presetsLabel,
            defMonitorComboBox));

    add(colorManagementPanel());
    add(GuiUtils.boxYLastElement(LAST_FILLER_HEIGHT));
  }

  /** The switch and the display profile, applied at once through {@link DisplayColor}. */
  private JPanel colorManagementPanel() {
    JCheckBox enabled = new JCheckBox(Messages.getString("ScreenPrefView.color_convert"));
    enabled.setSelected(ColorManagement.isEnabled());
    enabled.setEnabled(!Settings.isLocked(CoreSettings.COLOR_MANAGEMENT));
    enabled.addActionListener(e -> DisplayColor.setEnabled(enabled.isSelected()));

    JLabel profileLabel = new JLabel(displayProfileText());
    boolean locked = Settings.isLocked(CoreSettings.DISPLAY_PROFILE);
    JButton choose = new JButton(Messages.getString("ScreenPrefView.choose_profile"));
    choose.setEnabled(!locked);
    choose.addActionListener(
        e -> {
          chooseDisplayProfile((Component) e.getSource());
          profileLabel.setText(displayProfileText());
        });
    JButton srgb = new JButton(Messages.getString("ScreenPrefView.profile_srgb"));
    srgb.setEnabled(!locked);
    srgb.addActionListener(
        e -> {
          DisplayColor.resetDisplayProfile();
          profileLabel.setText(displayProfileText());
        });

    JPanel panel = GuiUtils.getVerticalBoxLayoutPanel();
    panel.add(GuiUtils.getFlowLayoutPanel(ITEM_SEPARATOR_SMALL, ITEM_SEPARATOR, enabled));
    panel.add(
        GuiUtils.getFlowLayoutPanel(
            new JLabel(
                Messages.getString("ScreenPrefView.default_display_profile") + StringUtil.COLON),
            profileLabel,
            choose,
            srgb));
    panel.setBorder(
        GuiUtils.getTitledBorder(Messages.getString("ScreenPrefView.color_management")));
    return panel;
  }

  /**
   * The profile of one monitor: the default one of the workstation, or a file of its own, kept in
   * the local settings and applied at once.
   */
  private static JPanel screenProfilePanel(String monitorId) {
    JLabel label =
        new JLabel(Messages.getString("ScreenPrefView.display_profile") + StringUtil.COLON);
    label.setBorder(GuiUtils.getEmptyBorderLeading(0, ITEM_SEPARATOR_LARGE, 0, 0));
    JLabel profileLabel = new JLabel(screenProfileText(monitorId));
    JButton choose = new JButton(Messages.getString("ScreenPrefView.choose_profile"));
    JButton useDefault = new JButton(Messages.getString("ScreenPrefView.profile_use_default"));
    useDefault.setEnabled(ColorManagement.screenProfileFile(monitorId).isPresent());
    choose.addActionListener(
        e -> {
          Path file =
              chooseProfileFile(
                  (Component) e.getSource(),
                  ColorManagement.screenProfileFile(monitorId)
                      .or(ColorManagement::displayProfileFile));
          if (file != null) {
            try {
              DisplayColor.setScreenProfile(monitorId, file);
            } catch (IOException ex) {
              showProfileError((Component) e.getSource(), ex);
            }
            profileLabel.setText(screenProfileText(monitorId));
            useDefault.setEnabled(ColorManagement.screenProfileFile(monitorId).isPresent());
          }
        });
    useDefault.addActionListener(
        e -> {
          DisplayColor.resetScreenProfile(monitorId);
          profileLabel.setText(screenProfileText(monitorId));
          useDefault.setEnabled(false);
        });
    return GuiUtils.getFlowLayoutPanel(label, profileLabel, choose, useDefault);
  }

  private static String screenProfileText(String monitorId) {
    Optional<Path> file = ColorManagement.screenProfileFile(monitorId);
    if (file.isEmpty()) {
      return Messages.getString("ScreenPrefView.profile_default");
    }
    return ColorManagement.displayProfile(monitorId)
        .description()
        .orElseGet(() -> file.get().getFileName().toString());
  }

  private static String displayProfileText() {
    if (!ColorManagement.hasDisplayProfile()) {
      return Messages.getString("ScreenPrefView.profile_none");
    }
    IccProfile profile = ColorManagement.displayProfile();
    return profile
        .description()
        .orElseGet(
            () ->
                ColorManagement.displayProfileFile()
                    .map(p -> p.getFileName().toString())
                    .orElse(""));
  }

  private static void chooseDisplayProfile(Component parent) {
    Path file = chooseProfileFile(parent, ColorManagement.displayProfileFile());
    if (file != null) {
      try {
        DisplayColor.setDisplayProfile(file);
      } catch (IOException ex) {
        showProfileError(parent, ex);
      }
    }
  }

  /** Asks for an ICC profile file, starting in the folder of {@code current}; null when none. */
  private static Path chooseProfileFile(Component parent, Optional<Path> current) {
    SystemFileChooser chooser =
        new SystemFileChooser(current.map(Path::getParent).map(Path::toString).orElse(""));
    chooser.setFileSelectionMode(SystemFileChooser.FILES_ONLY);
    chooser.setFileFilter(new FileNameExtensionFilter("ICC profile", "icc", "icm")); // NON-NLS
    return chooser.showOpenDialog(parent) == SystemFileChooser.APPROVE_OPTION
            && chooser.getSelectedFile() != null
        ? chooser.getSelectedFile().toPath()
        : null;
  }

  private static void showProfileError(Component parent, IOException ex) {
    JOptionPane.showMessageDialog(
        WinUtil.getValidComponent(parent),
        ex.getMessage(),
        Messages.getString("ScreenPrefView.display_profile"),
        JOptionPane.ERROR_MESSAGE);
  }

  private static JLabel roleLabel() {
    JLabel label = new JLabel(Messages.getString("ScreenPrefView.role") + StringUtil.COLON);
    label.setBorder(GuiUtils.getEmptyBorderLeading(0, ITEM_SEPARATOR_LARGE, 0, 0));
    return label;
  }

  /**
   * The role of a monitor: automatic (derived from its place, see {@link ScreenRoles}) or one the
   * user chooses, kept in the local settings of this workstation.
   */
  private static JComboBox<Object> roleComboBox(String monitorId) {
    String automatic = Messages.common("automatic");
    JComboBox<Object> combo = new JComboBox<>();
    combo.addItem(automatic);
    for (ScreenRole role : ScreenRole.values()) {
      combo.addItem(role);
    }
    ScreenRole current = ScreenRoles.explicitRoles().get(monitorId);
    combo.setSelectedItem(current == null ? automatic : current);
    combo.addItemListener(
        e -> {
          if (e.getStateChange() == ItemEvent.SELECTED) {
            Object item = ((JComboBox<?>) e.getSource()).getSelectedItem();
            ScreenRoles.setExplicitRole(monitorId, item instanceof ScreenRole role ? role : null);
          }
        });
    return combo;
  }

  public static int getDefaultMonitor() {
    return GuiUtils.getUICore().getLocalPersistence().getIntProperty("default.monitor", 0);
  }

  private static String buildMonitorLabel(String title, Monitor monitor) {
    StringBuilder buf = new StringBuilder(title);
    if (monitor.getRealScaleFactor() > 0) {
      Rectangle mb = monitor.getBounds();
      double ratio = Unit.MILLIMETER.getConversionRatio(monitor.getRealScaleFactor());
      buf.append(" (");
      buf.append((int) Math.round(mb.width * ratio));
      buf.append("x"); // NON-NLS
      buf.append((int) Math.round(mb.height * ratio));
      buf.append(" ");
      buf.append(Unit.MILLIMETER.getAbbreviation());
      buf.append(")");
    }
    return buf.toString();
  }

  @Override
  public void closeAdditionalWindow() {
    // No action
  }

  @Override
  public void resetToDefaultValues() {
    // No action
  }

  static class Cross extends JLabel {
    private final Monitor monitor;
    private int horizontalLength;
    private int verticalLength;

    public Cross(Monitor monitor) {
      this.monitor = monitor;
      this.horizontalLength = 0;
      this.verticalLength = 0;
    }

    @Override
    public void paintComponent(Graphics g) {
      if (g instanceof Graphics2D graphics2D) {
        draw(graphics2D);
      }
    }

    protected void draw(Graphics2D g2d) {
      Stroke oldStroke = g2d.getStroke();
      Paint oldColor = g2d.getPaint();

      g2d.setPaint(Color.BLACK);
      int x = getX();
      int y = getY();
      int width = getWidth();
      int height = getHeight();
      g2d.fillRect(x, y, width, height);
      g2d.setPaint(Color.WHITE);
      // Do not draw any extended decoration
      g2d.setStroke(new BasicStroke(3.0f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL));

      int offset = 50;

      // Draw horizontal line
      int x1 = x + offset;
      int y1 = y + height / 2;
      int x2 = x + width - offset;
      int y2 = y1;
      horizontalLength = x2 - x1;
      g2d.drawLine(x1, y1, x2, y2);

      // Draw vertical line
      int xv1 = x + width / 2;
      int yv1 = y + offset;
      int xv2 = xv1;
      int yv2 = y + height - offset;
      verticalLength = yv2 - yv1;
      g2d.drawLine(xv1, yv1, xv2, yv2);

      if (monitor.getRealScaleFactor() > 0) {
        String hlength =
            DecFormatter.allNumber(
                    Unit.MILLIMETER.getConversionRatio(monitor.getRealScaleFactor())
                        * horizontalLength)
                + " "
                + Unit.MILLIMETER.getAbbreviation();
        String vlength =
            DecFormatter.allNumber(
                    Unit.MILLIMETER.getConversionRatio(monitor.getRealScaleFactor())
                        * verticalLength)
                + " "
                + Unit.MILLIMETER.getAbbreviation();
        g2d.drawString(hlength, x2 - 70, y2 + 15);
        g2d.drawString(vlength, xv1 + 10, yv2 - 5);
      }

      g2d.setPaint(oldColor);
      g2d.setStroke(oldStroke);
    }

    public int getHorizontalLength() {
      return horizontalLength;
    }

    public int getVerticalLength() {
      return verticalLength;
    }
  }

  static class CalibrationDialog extends JDialog {
    private final Monitor monitor;

    private final Cross cross;
    private final JFormattedTextField jTextFieldLineWidth =
        new JFormattedTextField(NumberFormat.getIntegerInstance());
    private final JComboBox<String> jComboBoxType =
        new JComboBox<>(
            new String[] {
              Messages.getString("ScreenPrefView.horiz_line"),
              Messages.getString("ScreenPrefView.vertical_line"),
              Messages.getString("ScreenPrefView.screen_size")
            });
    private final JComboBox<Unit> jComboBoxUnit =
        new JComboBox<>(new Unit[] {Unit.MILLIMETER, Unit.CENTIMETER, Unit.MILLIINCH, Unit.INCH});

    public CalibrationDialog(
        Window parentWindow, String title, ModalityType applicationModal, Monitor monitor) {
      super(parentWindow, title, applicationModal, monitor.getGraphicsConfiguration());
      this.monitor = monitor;
      this.cross = new Cross(monitor);
      init();
    }

    protected void init() {
      final Container content = this.getContentPane();

      final JPanel inputPanel = new JPanel();
      jTextFieldLineWidth.setValue(0L);
      GuiUtils.setPreferredWidth(jTextFieldLineWidth, 100);
      inputPanel.add(
          new JLabel(Messages.getString("ScreenPrefView.enter_dist") + StringUtil.COLON));
      inputPanel.add(jComboBoxType);
      inputPanel.add(jTextFieldLineWidth);
      inputPanel.add(jComboBoxUnit);
      inputPanel.add(Box.createHorizontalStrut(15));
      JButton apply = new JButton(Messages.common("apply"));
      apply.addActionListener(e -> computeScaleFactor());
      inputPanel.add(apply);

      content.add(cross, BorderLayout.CENTER);
      content.add(inputPanel, BorderLayout.SOUTH);
    }

    private void computeScaleFactor() {
      Object object = jTextFieldLineWidth.getValue();
      if (object instanceof Number number) {
        double val = number.doubleValue();
        if (val <= 0) {
          monitor.setRealScaleFactor(0.0);
        } else {
          Unit unit = (Unit) Objects.requireNonNull(jComboBoxUnit.getSelectedItem());
          int index = jComboBoxType.getSelectedIndex();
          if (index == 0) {
            int lineLength = cross.getHorizontalLength();
            if (lineLength > 100) {
              monitor.setRealScaleFactor(unit.getFactorToMeters() * val / lineLength);
            }
          } else if (index == 1) {
            int lineLength = cross.getVerticalLength();
            if (lineLength > 100) {
              monitor.setRealScaleFactor(unit.getFactorToMeters() * val / lineLength);
            }
          } else if (index == 2) {
            Rectangle bound = monitor.getBounds();
            double w = bound.getWidth() * bound.getWidth();
            double h = bound.getHeight() * bound.getHeight();
            double realHeight = Math.sqrt(val * val * h / (w + h));
            monitor.setRealScaleFactor(unit.getFactorToMeters() * realHeight / bound.getHeight());
          }
        }
        cross.repaint();
        JOptionPane.showMessageDialog(
            WinUtil.getValidComponent(this),
            Messages.getString("ScreenPrefView.calib_desc"),
            Messages.getString("ScreenPrefView.sp_calib"),
            JOptionPane.WARNING_MESSAGE);

        String screen = ViewSetting.buildScreenItem(monitor);
        GuiUtils.getUICore()
            .getLocalPersistence()
            .putDoubleProperty(screen, monitor.getRealScaleFactor());
      }
    }
  }
}
