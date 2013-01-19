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
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.text.NumberFormat;
import java.util.List;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFormattedTextField;
import javax.swing.JFrame;
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
import org.weasis.core.ui.editor.image.dockable.MeasureTool;

public class ScreenPrefView extends AbstractItemDialogPage {

    private final JPanel panelList = new JPanel();

    public ScreenPrefView() {
        super("Monitors");
        setBorder(new EmptyBorder(15, 10, 10, 10));
        BorderLayout borderLayout = new BorderLayout();
        setLayout(borderLayout);

        JPanel panel_2 = new JPanel();
        FlowLayout flowLayout_1 = (FlowLayout) panel_2.getLayout();
        flowLayout_1.setHgap(10);
        flowLayout_1.setAlignment(FlowLayout.RIGHT);
        flowLayout_1.setVgap(7);
        add(panel_2, BorderLayout.SOUTH);

        JButton btnNewButton = new JButton(org.weasis.core.ui.Messages.getString("restore.values")); //$NON-NLS-1$
        panel_2.add(btnNewButton);
        btnNewButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                resetoDefaultValues();
            }
        });

        JPanel panel1 = new JPanel();
        panel1.setBorder(new TitledBorder(null, "Monitor Settings", TitledBorder.LEADING, TitledBorder.TOP, null, //$NON-NLS-1$
            null));
        add(panel1, BorderLayout.NORTH);
        panel1.setLayout(new BorderLayout(0, 0));

        panel1.add(panelList, BorderLayout.NORTH);
        panelList.setLayout(new BoxLayout(panelList, BoxLayout.Y_AXIS));

        List<Monitor> monitors = MeasureTool.viewSetting.getMonitors();
        for (int i = 0; i < monitors.size(); i++) {
            final Monitor monitor = monitors.get(i);
            Rectangle mb = monitor.getBounds();

            JPanel p = new JPanel();
            p.setAlignmentY(Component.TOP_ALIGNMENT);
            p.setAlignmentX(Component.LEFT_ALIGNMENT);
            p.setLayout(new FlowLayout(FlowLayout.LEFT, 3, 3));

            StringBuffer buf = new StringBuffer();
            buf.append(i + 1);
            buf.append(". ");
            buf.append("Monitor");
            buf.append(": ");
            buf.append(monitor.getMonitorID());
            buf.append(".");
            buf.append(mb.width);
            buf.append("x");
            buf.append(mb.height);
            final String title = buf.toString();

            if (monitor.getPitch() > 0) {
                buf.append(" ");
                buf.append((int) Math.round(mb.width * Unit.MILLIMETER.getConversionRatio(monitor.getPitch())));
                buf.append("x");
                buf.append((int) Math.round(mb.height
                    * Unit.MILLIMETER.getConversionRatio(monitor.getPitch())));
                buf.append(" ");
                buf.append(Unit.MILLIMETER.getAbbreviation());
            }
            p.add(new JLabel(buf.toString()));

            JButton realZoomButton = new JButton("Spatial calibration");
            realZoomButton.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    /*
                     * As screen insets are not available on all the systems (on X11 windowing systems), the only way to
                     * get the maximum visible size desktop is to maximize a JFrame
                     */
                    JFrame frame = new JFrame(monitor.getGraphicsConfiguration());
                    Rectangle bound = monitor.getBounds();
                    frame.setMaximizedBounds(bound);
                    frame.setBounds(bound.x, bound.y, bound.width - 150, bound.height - 150);
                    frame.setVisible(true);
                    frame.setExtendedState(JFrame.MAXIMIZED_BOTH);

                    try {
                        // Let time to maximize window
                        Thread.sleep(500);
                    } catch (InterruptedException e1) {
                        // Do noting
                    }

                    final CalibDialog dialog =
                        new CalibDialog(WinUtil.getParentWindow((Component) e.getSource()), title,
                            ModalityType.APPLICATION_MODAL, monitor);
                    dialog.setBounds(frame.getBounds());
                    frame.dispose();
                    dialog.setVisible(true);

                }
            });
            realZoomButton.setToolTipText("Calibrate screen for getting real size zoom");
            p.add(realZoomButton);

            panelList.add(p);

        }
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

            if (monitor.getPitch() > 0) {
                String hlength =
                    DecFormater.oneDecimal(Unit.MILLIMETER.getConversionRatio(monitor.getPitch())
                        * horizontalLength)
                        + " " + Unit.MILLIMETER.getAbbreviation();
                String vlength =
                    DecFormater.oneDecimal(Unit.MILLIMETER.getConversionRatio(monitor.getPitch())
                        * verticalLength)
                        + " " + Unit.MILLIMETER.getAbbreviation();
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
        private final JFormattedTextField jTextFieldLineWidth = new JFormattedTextField(
            NumberFormat.getIntegerInstance());
        private final JComboBox jComboBoxType = new JComboBox(new String[] { "Displayed horizontal line length",
            "Displayed vertical line length", "Screen size (diagonal)" });
        private final JComboBox jComboBoxUnit = new JComboBox(new Unit[] { Unit.MILLIMETER, Unit.CENTIMETER,
            Unit.MILLIINCH, Unit.INCH });

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
            inputPanel.add(new JLabel("Enter one of the following distance" + " :"));
            inputPanel.add(jComboBoxType);
            inputPanel.add(jTextFieldLineWidth);
            inputPanel.add(jComboBoxUnit);
            inputPanel.add(Box.createHorizontalStrut(15));
            JButton apply = new JButton("Apply");
            apply.addActionListener(new ActionListener() {

                @Override
                public void actionPerformed(ActionEvent e) {
                    computeScaleFactor();
                }

            });
            inputPanel.add(apply);

            content.add(cross, BorderLayout.CENTER);
            content.add(inputPanel, BorderLayout.SOUTH);
        }

        private void computeScaleFactor() {
            Object object = jTextFieldLineWidth.getValue();
            if (object instanceof Long) {
                double val = ((Long) object).doubleValue();
                if (val <= 0) {
                    monitor.setPitch(0.0);
                } else {
                    Unit unit = (Unit) jComboBoxUnit.getSelectedItem();
                    int index = jComboBoxType.getSelectedIndex();
                    if (index == 0) {
                        int lineLength = cross.getHorizontalLength();
                        if (lineLength > 100) {
                            monitor.setPitch(unit.getConvFactor() * val / lineLength);
                        }
                    } else if (index == 1) {
                        int lineLength = cross.getVerticalLength();
                        if (lineLength > 100) {
                            monitor.setPitch(unit.getConvFactor() * val / lineLength);
                        }
                    } else if (index == 2) {
                        Rectangle bound = monitor.getBounds();
                        double w = bound.getWidth() * bound.getWidth();
                        double h = bound.getHeight() * bound.getHeight();
                        double realHeight = Math.sqrt(val * val * h / (w + h));
                        monitor.setPitch(unit.getConvFactor() * realHeight / bound.getHeight());
                    }
                }
                cross.repaint();
                JOptionPane
                    .showMessageDialog(
                        this,
                        "To obtain a correct real zoom factor, make sure that the size of \nthe two white lines must match exactly with the real measurement",
                        "Spatial calibration", JOptionPane.WARNING_MESSAGE);

                StringBuffer buf = new StringBuffer("screen.");
                buf.append(monitor.getMonitorID());
                Rectangle b = monitor.getBounds();
                buf.append(".");
                buf.append(b.width);
                buf.append("x");
                buf.append(b.height);
                buf.append(".pitch");
                BundleTools.LOCAL_PERSISTENCE.putDoubleProperty(buf.toString(), monitor.getPitch());
            }
        }

    }
}
