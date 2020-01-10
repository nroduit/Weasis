/*******************************************************************************
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
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

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFormattedTextField;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;

import org.weasis.core.api.gui.util.AbstractItemDialogPage;
import org.weasis.core.api.gui.util.DecFormater;
import org.weasis.core.api.gui.util.JMVUtils;
import org.weasis.core.api.gui.util.WinUtil;
import org.weasis.core.api.image.util.Unit;
import org.weasis.core.api.service.BundleTools;
import org.weasis.core.api.util.LocalUtil;
import org.weasis.core.api.util.StringUtil;
import org.weasis.core.ui.Messages;
import org.weasis.core.ui.editor.image.dockable.MeasureTool;

public class ScreenPrefView extends AbstractItemDialogPage {

    private final JPanel panelList = new JPanel();

    public ScreenPrefView() {
        super(Messages.getString("ScreenPrefView.monitors")); //$NON-NLS-1$
        setComponentPosition(100);
        setBorder(new EmptyBorder(15, 10, 10, 10));
        BorderLayout borderLayout = new BorderLayout();
        setLayout(borderLayout);

        JPanel panel2 = new JPanel();
        FlowLayout flowLayout1 = (FlowLayout) panel2.getLayout();
        flowLayout1.setHgap(10);
        flowLayout1.setAlignment(FlowLayout.RIGHT);
        flowLayout1.setVgap(7);
        add(panel2, BorderLayout.SOUTH);

        JButton btnNewButton = new JButton(org.weasis.core.ui.Messages.getString("restore.values")); //$NON-NLS-1$
        panel2.add(btnNewButton);
        btnNewButton.addActionListener(e -> resetoDefaultValues());

        JPanel panel1 = new JPanel();
        panel1.setBorder(new TitledBorder(null, Messages.getString("ScreenPrefView.settings"), TitledBorder.LEADING, //$NON-NLS-1$
            TitledBorder.TOP, null, null));
        add(panel1, BorderLayout.NORTH);
        panel1.setLayout(new BorderLayout(0, 0));

        panel1.add(panelList, BorderLayout.NORTH);
        panelList.setLayout(new BoxLayout(panelList, BoxLayout.Y_AXIS));

        final JComboBox<String> defMonitorComboBox = new JComboBox<>();
        List<Monitor> monitors = MeasureTool.viewSetting.getMonitors();
        for (int i = 0; i < monitors.size(); i++) {
            final Monitor monitor = monitors.get(i);
            Rectangle mb = monitor.getBounds();

            JPanel p = new JPanel();
            p.setAlignmentY(Component.TOP_ALIGNMENT);
            p.setAlignmentX(Component.LEFT_ALIGNMENT);
            p.setLayout(new FlowLayout(FlowLayout.LEFT, 3, 3));

            StringBuilder buf = new StringBuilder();
            buf.append(i + 1);
            buf.append(". "); //$NON-NLS-1$
            buf.append(Messages.getString("ScreenPrefView.monitor")); //$NON-NLS-1$
            buf.append(StringUtil.COLON_AND_SPACE);
            buf.append(monitor.getMonitorID());
            buf.append("."); //$NON-NLS-1$
            buf.append(mb.width);
            buf.append("x"); //$NON-NLS-1$
            buf.append(mb.height);
            final String title = buf.toString();
            defMonitorComboBox.addItem(title);

            if (monitor.getRealScaleFactor() > 0) {
                buf.append(" ("); //$NON-NLS-1$
                buf.append(
                    (int) Math.round(mb.width * Unit.MILLIMETER.getConversionRatio(monitor.getRealScaleFactor())));
                buf.append("x"); //$NON-NLS-1$
                buf.append(
                    (int) Math.round(mb.height * Unit.MILLIMETER.getConversionRatio(monitor.getRealScaleFactor())));
                buf.append(" "); //$NON-NLS-1$
                buf.append(Unit.MILLIMETER.getAbbreviation());
                buf.append(")"); //$NON-NLS-1$
            }
            p.add(new JLabel(buf.toString()));

            JButton realZoomButton = new JButton(Messages.getString("ScreenPrefView.sp_calib")); //$NON-NLS-1$
            realZoomButton.addActionListener(e -> {
                final CalibDialog dialog = new CalibDialog(WinUtil.getParentFrame((Component) e.getSource()), title,
                    ModalityType.APPLICATION_MODAL, monitor);
                dialog.setBounds(monitor.getFullscreenBounds());
                dialog.setVisible(true);

            });
            realZoomButton.setToolTipText(Messages.getString("ScreenPrefView.calib_real")); //$NON-NLS-1$
            p.add(realZoomButton);

            panelList.add(p);

        }

        int defIndex = getDefaultMonitor();
        if (defIndex < 0 || defIndex >= defMonitorComboBox.getItemCount()) {
            defIndex = 0;
        }
        defMonitorComboBox.setSelectedIndex(defIndex);
        defMonitorComboBox.addItemListener(e -> {
            if (e.getStateChange() == ItemEvent.SELECTED) {
                JComboBox<?> comboBox = (JComboBox<?>) e.getSource();
                BundleTools.LOCAL_UI_PERSISTENCE.putIntProperty("default.monitor", comboBox.getSelectedIndex()); //$NON-NLS-1$
            }
        });

        final JPanel panel3 = new JPanel();
        panel3.setAlignmentY(Component.TOP_ALIGNMENT);
        panel3.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel3.setLayout(new FlowLayout(FlowLayout.LEFT, 2, 3));
        final JLabel presetsLabel = new JLabel(Messages.getString("ScreenPrefView.def_monitor") + StringUtil.COLON); //$NON-NLS-1$
        panel3.add(presetsLabel);
        panel3.add(defMonitorComboBox);
        panelList.add(panel3);
    }

    public static int getDefaultMonitor() {
        return BundleTools.LOCAL_UI_PERSISTENCE.getIntProperty("default.monitor", 0); //$NON-NLS-1$
    }

    @Override
    public void closeAdditionalWindow() {
        // TODO close frames
    }

    @Override
    public void resetoDefaultValues() {

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
            if (g instanceof Graphics2D) {
                draw((Graphics2D) g);
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
                String hlength = DecFormater
                    .allNumber(Unit.MILLIMETER.getConversionRatio(monitor.getRealScaleFactor()) * horizontalLength)
                    + " " + Unit.MILLIMETER.getAbbreviation(); //$NON-NLS-1$
                String vlength = DecFormater
                    .allNumber(Unit.MILLIMETER.getConversionRatio(monitor.getRealScaleFactor()) * verticalLength) + " " //$NON-NLS-1$
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

    static class CalibDialog extends JDialog {
        private final Monitor monitor;

        private final Cross cross;
        private final JFormattedTextField jTextFieldLineWidth = new JFormattedTextField(LocalUtil.getIntegerInstance());
        private final JComboBox<String> jComboBoxType =
            new JComboBox<>(new String[] { Messages.getString("ScreenPrefView.horiz_line"), //$NON-NLS-1$
                Messages.getString("ScreenPrefView.vertical_line"), Messages.getString("ScreenPrefView.screen_size") }); //$NON-NLS-1$ //$NON-NLS-2$
        private final JComboBox<Unit> jComboBoxUnit =
            new JComboBox<>(new Unit[] { Unit.MILLIMETER, Unit.CENTIMETER, Unit.MILLIINCH, Unit.INCH });

        public CalibDialog(Window parentWindow, String title, ModalityType applicationModal, Monitor monitor) {
            super(parentWindow, title, applicationModal, monitor.getGraphicsConfiguration());
            this.monitor = monitor;
            this.cross = new Cross(monitor);
            init();
        }

        protected void init() {
            final Container content = this.getContentPane();

            final JPanel inputPanel = new JPanel();
            jTextFieldLineWidth.setValue(0L);
            JMVUtils.setPreferredWidth(jTextFieldLineWidth, 100);
            inputPanel.add(new JLabel(Messages.getString("ScreenPrefView.enter_dist") + StringUtil.COLON)); //$NON-NLS-1$
            inputPanel.add(jComboBoxType);
            inputPanel.add(jTextFieldLineWidth);
            inputPanel.add(jComboBoxUnit);
            inputPanel.add(Box.createHorizontalStrut(15));
            JButton apply = new JButton(Messages.getString("ScreenPrefView.apply")); //$NON-NLS-1$
            apply.addActionListener(e -> computeScaleFactor());
            inputPanel.add(apply);

            content.add(cross, BorderLayout.CENTER);
            content.add(inputPanel, BorderLayout.SOUTH);
        }

        private void computeScaleFactor() {
            Object object = jTextFieldLineWidth.getValue();
            if (object instanceof Long) {
                double val = ((Long) object).doubleValue();
                if (val <= 0) {
                    monitor.setRealScaleFactor(0.0);
                } else {
                    Unit unit = (Unit) jComboBoxUnit.getSelectedItem();
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
                JOptionPane.showMessageDialog(this, Messages.getString("ScreenPrefView.calib_desc"), //$NON-NLS-1$
                    Messages.getString("ScreenPrefView.sp_calib"), JOptionPane.WARNING_MESSAGE); //$NON-NLS-1$

                StringBuilder buf = new StringBuilder("screen."); //$NON-NLS-1$
                buf.append(monitor.getMonitorID());
                Rectangle b = monitor.getBounds();
                buf.append("."); //$NON-NLS-1$
                buf.append(b.width);
                buf.append("x"); //$NON-NLS-1$
                buf.append(b.height);
                buf.append(".pitch"); //$NON-NLS-1$
                BundleTools.LOCAL_UI_PERSISTENCE.putDoubleProperty(buf.toString(), monitor.getRealScaleFactor());
            }
        }

    }
}
