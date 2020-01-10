/*******************************************************************************
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.weasis.dicom.wave;

import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;

import org.weasis.core.api.gui.util.DecFormater;
import org.weasis.core.api.util.StringUtil;

class ToolPanel extends JPanel {
    private static final long serialVersionUID = 2827148456926205919L;

    public enum Speed {
        AUTO(WaveLayoutManager.AUTO_SPEED), TWELWE(12.5), TWENTY_FIVE(25.0), FIFTY(50.0), CENT(100.0);

        private final double value;

        private Speed(double value) {
            this.value = value;
        }

        public double getValue() {
            return value;
        }

        @Override
        public String toString() {
            if (this == AUTO) {
                return "auto mm/s"; //$NON-NLS-1$
            }
            return DecFormater.allNumber(value) + " mm/s"; //$NON-NLS-1$
        }
        
        public static Speed fromValue(double value) {
            for (Speed s : values()) {
                if (s.getValue() == value) {
                    return s;
                }
            }
            return AUTO;
        }
    }

    public enum Amplitude {
        AUTO(WaveLayoutManager.AUTO_AMPLITUDE), THREE(3), FIVE(5), TEN(10), FIFTEEN(15), TWENTY(20), THIRTY(30);

        private final int value;

        private Amplitude(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }

        @Override
        public String toString() {
            if (this == AUTO) {
                return "auto mm/mV"; //$NON-NLS-1$
            }
            return String.format("%d mm/mV", value); //$NON-NLS-1$
        }

        public static Amplitude fromValue(int value) {
            for (Amplitude a : values()) {
                if (a.getValue() == value) {
                    return a;
                }
            }
            return AUTO;
        }
    }

    private WaveView view;
    private JLabel formatLabel;
    private JComboBox<Format> formatCombo;

    public ToolPanel(WaveView view) {
        this.view = view;
        init();
    }

    private void init() {
        JLabel zoomLabel = new JLabel(Messages.getString("ToolPanel.zoom")); //$NON-NLS-1$
        this.add(zoomLabel);

        JComboBox<Speed> speed = new JComboBox<>(Speed.values());
        speed.addActionListener(e -> view.setSpeed(((Speed) speed.getSelectedItem()).getValue()));
        speed.setSelectedItem(Speed.fromValue(view.getSpeed()));
        speed.setFocusable(false);
        this.add(speed);

        JComboBox<Amplitude> amplitude = new JComboBox<>(Amplitude.values());
        amplitude.addActionListener(e -> view.setAmplitude(((Amplitude) amplitude.getSelectedItem()).getValue()));
        amplitude.setSelectedItem(Amplitude.fromValue(view.getAmplitude()));
        amplitude.setFocusable(false);
        this.add(amplitude);
        
        if (view.getChannelNumber() >= 12) {
            addDisplayFormatComponent();
        }
    }

    private void addDisplayFormatComponent() {
        formatLabel = new JLabel(Messages.getString("ToolPanel.disp_format") + StringUtil.COLON); //$NON-NLS-1$
        this.add(formatLabel);

        formatCombo = new JComboBox<>(Format.values());
        formatCombo.setFocusable(false);
        formatCombo.setSelectedItem(view.getCurrentFormat());
        formatCombo.addActionListener(e -> view.setFormat((Format) formatCombo.getSelectedItem()));
        this.add(formatCombo);
    }
}
