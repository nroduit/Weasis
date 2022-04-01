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
import java.util.List;
import java.util.Objects;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFormattedTextField;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import org.weasis.core.api.gui.util.AbstractItemDialogPage;
import org.weasis.core.api.gui.util.DecFormatter;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.gui.util.WinUtil;
import org.weasis.core.api.image.util.Unit;
import org.weasis.core.api.service.BundleTools;
import org.weasis.core.api.util.LocalUtil;
import org.weasis.core.ui.Messages;
import org.weasis.core.ui.editor.image.dockable.MeasureTool;
import org.weasis.core.util.StringUtil;

public class ScreenPrefView extends AbstractItemDialogPage {

  public ScreenPrefView() {
    super(Messages.getString("ScreenPrefView.monitors"), 108);

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

      if (monitor.getRealScaleFactor() > 0) {
        buf.append(" (");
        buf.append(
            (int)
                Math.round(
                    mb.width * Unit.MILLIMETER.getConversionRatio(monitor.getRealScaleFactor())));
        buf.append("x"); // NON-NLS
        buf.append(
            (int)
                Math.round(
                    mb.height * Unit.MILLIMETER.getConversionRatio(monitor.getRealScaleFactor())));
        buf.append(" ");
        buf.append(Unit.MILLIMETER.getAbbreviation());
        buf.append(")");
      }

      JButton realZoomButton = new JButton(Messages.getString("ScreenPrefView.sp_calib"));
      realZoomButton.addActionListener(
          e -> {
            final CalibrationDialog dialog =
                new CalibrationDialog(
                    WinUtil.getParentFrame((Component) e.getSource()),
                    title,
                    ModalityType.APPLICATION_MODAL,
                    monitor);
            dialog.setBounds(monitor.getFullscreenBounds());
            dialog.setVisible(true);
          });
      realZoomButton.setToolTipText(Messages.getString("ScreenPrefView.calib_real"));
      add(GuiUtils.getFlowLayoutPanel(new JLabel(buf.toString()), realZoomButton));
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
            BundleTools.LOCAL_UI_PERSISTENCE.putIntProperty(
                "default.monitor", comboBox.getSelectedIndex());
          }
        });

    final JLabel presetsLabel =
        new JLabel(Messages.getString("ScreenPrefView.def_monitor") + StringUtil.COLON);
    add(
        GuiUtils.getFlowLayoutPanel(
            FlowLayout.LEFT,
            ITEM_SEPARATOR_SMALL,
            ITEM_SEPARATOR,
            presetsLabel,
            defMonitorComboBox));

    add(GuiUtils.boxYLastElement(LAST_FILLER_HEIGHT));
  }

  public static int getDefaultMonitor() {
    return BundleTools.LOCAL_UI_PERSISTENCE.getIntProperty("default.monitor", 0);
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
        new JFormattedTextField(LocalUtil.getIntegerInstance());
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
      JButton apply = new JButton(Messages.getString("ScreenPrefView.apply"));
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
              monitor.setRealScaleFactor(unit.getConvFactor() * val / lineLength);
            }
          } else if (index == 1) {
            int lineLength = cross.getVerticalLength();
            if (lineLength > 100) {
              monitor.setRealScaleFactor(unit.getConvFactor() * val / lineLength);
            }
          } else if (index == 2) {
            Rectangle bound = monitor.getBounds();
            double w = bound.getWidth() * bound.getWidth();
            double h = bound.getHeight() * bound.getHeight();
            double realHeight = Math.sqrt(val * val * h / (w + h));
            monitor.setRealScaleFactor(unit.getConvFactor() * realHeight / bound.getHeight());
          }
        }
        cross.repaint();
        JOptionPane.showMessageDialog(
            this,
            Messages.getString("ScreenPrefView.calib_desc"),
            Messages.getString("ScreenPrefView.sp_calib"),
            JOptionPane.WARNING_MESSAGE);

        StringBuilder buf = new StringBuilder("screen."); // NON-NLS
        buf.append(monitor.getMonitorID());
        Rectangle b = monitor.getBounds();
        buf.append(".");
        buf.append(b.width);
        buf.append("x"); // NON-NLS
        buf.append(b.height);
        buf.append(".pitch");
        BundleTools.LOCAL_UI_PERSISTENCE.putDoubleProperty(
            buf.toString(), monitor.getRealScaleFactor());
      }
    }
  }
}
