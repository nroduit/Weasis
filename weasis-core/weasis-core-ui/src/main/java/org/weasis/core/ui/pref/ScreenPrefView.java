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
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Insets;
import java.awt.Paint;
import java.awt.Rectangle;
import java.awt.Stroke;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.text.NumberFormat;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFormattedTextField;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;

import org.weasis.core.api.gui.util.AbstractItemDialogPage;
import org.weasis.core.api.gui.util.DecFormater;
import org.weasis.core.api.gui.util.JMVUtils;
import org.weasis.core.api.gui.util.WinUtil;
import org.weasis.core.api.image.util.Unit;
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

        Toolkit toolkit = Toolkit.getDefaultToolkit();
        GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
        GraphicsDevice[] gd = ge.getScreenDevices();

        JFrame[] frames = new JFrame[gd.length];
        for (int i = 0; i < gd.length; i++) {
            final GraphicsConfiguration config = gd[i].getDefaultConfiguration();
            if (config == null) {
                continue;
            }
            final Rectangle b = config.getBounds();
            String mID = gd[i].getIDstring();

            JPanel p = new JPanel();
            p.setAlignmentY(Component.TOP_ALIGNMENT);
            p.setAlignmentX(Component.LEFT_ALIGNMENT);
            p.setLayout(new FlowLayout(FlowLayout.LEFT, 3, 3));

            StringBuffer buf = new StringBuffer();
            buf.append(i + 1);
            buf.append(". ");
            buf.append("Monitor");
            buf.append(" ");
            buf.append(b.width);
            buf.append("x");
            buf.append(b.height);
            buf.append(" [");
            buf.append(mID);
            buf.append("] ");
            final String title = buf.toString();

            Monitor m = MeasureTool.viewSetting.getMonitor(mID);
            final Monitor monitor;
            if (m == null) {
                monitor = new Monitor(mID, b.width, b.height);
                MeasureTool.viewSetting.getMonitors().add(monitor);
            } else {
                monitor = m;
            }

            if (monitor.getWidth() != b.width || monitor.getHeight() != b.height) {
                monitor.changeResolution(b.width, b.height);
            }

            if (monitor.getRealScaleFactor() > 0) {
                buf.append(" ");
                buf.append((int) Math.round(b.width * Unit.MILLIMETER.getConversionRatio(monitor.getRealScaleFactor())));
                buf.append("x");
                buf.append((int) Math.round(b.height * Unit.MILLIMETER.getConversionRatio(monitor.getRealScaleFactor())));
                buf.append(" ");
                buf.append(Unit.MILLIMETER.getAbbreviation());
            }
            p.add(new JLabel(buf.toString()));

            // Not available on X11 windowing systems
            Insets inset = toolkit.getScreenInsets(config);
            b.x += inset.left;
            b.y += inset.top;
            b.width -= (inset.left + inset.right);
            b.height -= (inset.top + inset.bottom);

            JButton realZoomButton = new JButton("Spatial calibration");
            realZoomButton.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    final CalibDialog dialog =
                        new CalibDialog(WinUtil.getParentWindow((Component) e.getSource()), title,
                            ModalityType.APPLICATION_MODAL, config, b.getBounds(), monitor);
                    // dialog.setMaximizedBounds(bound);
                    //
                    // // set a valid size, insets of screen is often non consistent
                    // dialog.setBounds(bound.x, bound.y, bound.width - 150, bound.height - 150);
                    // dialog.setVisible(true);
                    //
                    // dialog.setExtendedState((dialog.getExtendedState() & Frame.MAXIMIZED_BOTH) ==
                    // Frame.MAXIMIZED_BOTH
                    // ? JFrame.NORMAL : JFrame.MAXIMIZED_BOTH);
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
        private final Rectangle bound;
        private final Monitor monitor;
        private int horizontalLength;
        private int verticalLength;

        public Cross(Rectangle bound, Monitor monitor) {
            super();
            this.bound = bound;
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

            boolean horizontal = width > height;

            // Draw horizontal line
            int x1 = x + offset;
            int y1 = y + getHeight() / 2;
            int x2 = x + bound.width - offset - 20;
            int y2 = y1;
            horizontalLength = x2 - x1;
            g2d.drawLine(x1, y1, x2, y2);

            // Draw vertical line
            int xv1 = x + getWidth() / 2;
            int yv1 = y + offset - 20;
            int xv2 = xv1;
            int yv2 = y + bound.height - offset - 30;
            verticalLength = yv2 - yv1;
            g2d.drawLine(xv1, yv1, xv2, yv2);

            if (monitor.getRealScaleFactor() > 0) {
                String length =
                    DecFormater.oneDecimal(Unit.MILLIMETER.getConversionRatio(monitor.getRealScaleFactor())
                        * horizontalLength)
                        + " " + Unit.MILLIMETER.getAbbreviation();
                g2d.drawString(length, x2 - 70, y2 + 15);
                g2d.drawString(length, x + 5, yv1 - 5);
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
        private final Rectangle bound;
        private final Rectangle originBound;

        private final Cross cross;
        private final JPanel linePanel = new JPanel();
        private final JPanel inputPanel = new JPanel();
        private final JFormattedTextField jTextFieldLineWidth = new JFormattedTextField(
            NumberFormat.getNumberInstance());
        private final JComboBox jComboBoxType = new JComboBox(new String[] { "Horizontal line length",
            "Vertical line length", "Screen size (diagonal)" });
        private final JComboBox jComboBoxUnit = new JComboBox(new Unit[] { Unit.MILLIMETER, Unit.CENTIMETER,
            Unit.MILLIINCH, Unit.INCH });

        public CalibDialog(Window parentWindow, String title, ModalityType applicationModal,
            GraphicsConfiguration config, Rectangle bounds, Monitor monitor) {
            super(parentWindow, title, applicationModal, config);
            this.monitor = monitor;
            this.originBound = bounds;
            this.bound = bounds.getBounds();
            this.cross = new Cross(bounds.getBounds(), monitor);
            init();
        }

        protected void init() {

            jTextFieldLineWidth.setValue(0);
            JMVUtils.setPreferredWidth(jTextFieldLineWidth, 100);

            inputPanel.add(jComboBoxType);
            inputPanel.add(jTextFieldLineWidth);
            inputPanel.add(jComboBoxUnit);
            JButton apply = new JButton("Apply");
            apply.addActionListener(new ActionListener() {

                @Override
                public void actionPerformed(ActionEvent e) {
                    computeScaleFactor();
                }

            });
            inputPanel.add(apply);
            inputPanel.add(Box.createHorizontalStrut(15));

            changeOrientation(originBound.width >= originBound.height);
        }

        private void computeScaleFactor() {
            Object object = jTextFieldLineWidth.getValue();
            if (object instanceof Long) {
                double val = ((Long) object).doubleValue();
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
                    double w = (double) monitor.getWidth() * monitor.getWidth();
                    double h = (double) monitor.getHeight() * monitor.getHeight();
                    double realHeight = Math.sqrt(val * val * h / (w + h));
                    monitor.setRealScaleFactor(unit.getConvFactor() * realHeight / monitor.getHeight());
                }
                cross.repaint();
            }

        }

        private void changeOrientation(boolean horizontal) {
            if (horizontal) {
                bound.x = originBound.x + 10;
                bound.y = originBound.y + originBound.height / 2 - 10;
                bound.height = 50;
                bound.width = originBound.width - 20;
            } else {
                bound.x = originBound.x + originBound.width / 2 - 10;
                bound.y = originBound.y + 10;
                bound.width = 95;
                bound.height = originBound.height - 20;
            }
            final Container content = this.getContentPane();
            content.removeAll();

            linePanel.removeAll();

            Dimension dim = new Dimension(bound.width, bound.height);
            cross.setLocation(0, 0);
            cross.setMinimumSize(dim);
            cross.setPreferredSize(dim);
            cross.setSize(dim);
            // Point p = new Point(bound.x, bound.y);
            // SwingUtilities.convertPointFromScreen(p, line);
            // line.setBounds(new Rectangle(0, 0, bound.width, bound.height));
            linePanel.add(cross);
            content.add(linePanel);

            content.setLayout(new BoxLayout(content, horizontal ? BoxLayout.Y_AXIS : BoxLayout.X_AXIS));
            if (horizontal) {
                content.add(inputPanel);
                this.setBounds(originBound.x, bound.y, originBound.width, bound.height);
            }
            this.pack();
        }

    }
}
