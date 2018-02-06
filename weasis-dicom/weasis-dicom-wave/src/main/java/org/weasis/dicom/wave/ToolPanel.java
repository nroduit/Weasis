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
                return "auto mm/s";
            }
            return DecFormater.oneDecimal(value) + " mm/s";
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
                return "auto mm/mV";
            }
            return String.format("%d mm/mV", value);
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
        JLabel zoomLabel = new JLabel("Zoom");
        this.add(zoomLabel);

        JComboBox<Speed> speed = new JComboBox<>(Speed.values());
        speed.addActionListener(e -> view.setSpeed(((Speed) speed.getSelectedItem()).getValue()));
        speed.setSelectedItem(WaveLayoutManager.AUTO_SPEED);
        speed.setFocusable(false);
        this.add(speed);

        JComboBox<Amplitude> amplitude = new JComboBox<>(Amplitude.values());
        amplitude.addActionListener(e -> view.setAmplitude(((Amplitude) amplitude.getSelectedItem()).getValue()));
        amplitude.setSelectedItem(WaveLayoutManager.AUTO_AMPLITUDE);
        amplitude.setFocusable(false);
        this.add(amplitude);
        
        if (view.getChannelNumber() == 12) {
            addDisplayFormatComponent();
        }
    }

    private void addDisplayFormatComponent() {
        formatLabel = new JLabel("Display format" + StringUtil.COLON);
        this.add(formatLabel);
        
        formatCombo = new JComboBox<>(Format.values());
        formatCombo.setFocusable(false);
        formatCombo
            .addActionListener(e -> view.setFormat((Format) formatCombo.getSelectedItem()));
        this.add(formatCombo);
    }
}
