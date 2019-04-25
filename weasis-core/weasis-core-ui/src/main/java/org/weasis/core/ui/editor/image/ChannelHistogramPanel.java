package org.weasis.core.ui.editor.image;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.TitledBorder;

import org.weasis.core.api.gui.util.JMVUtils;
import org.weasis.core.api.image.op.ByteLut;
import org.weasis.core.api.image.util.WindLevelParameters;
import org.weasis.core.api.util.FontTools;
import org.weasis.core.api.util.StringUtil;

/**
 * @author Nicolas Roduit
 * @version 1.0
 */

public class ChannelHistogramPanel extends JPanel {

    private final BorderLayout borderLayout1 = new BorderLayout();
    private final HistogramPanel jPanelHistogram;
    private final JPanel jPanelSouth = new JPanel();
    private final JLabel jLabelResultVal = new JLabel();
    private final JButton jButtonHistoMinus;
    private final JButton jButtonHistoPlus;

    private final JCheckBox jCheckAccumulate = new JCheckBox("Acuumulate", false);
    private final JCheckBox jCheckLogarithmic = new JCheckBox("Logarithmic", false);
    private final JCheckBox jCheckShowIntensity = new JCheckBox("Show intensity color", true);
    private final JButton jButtonReset = new JButton("Reset");
    private final JButton jButtonSave = new JButton("Save");
    private final JPanel panel = new JPanel();

    public ChannelHistogramPanel(String name) {
        this.jPanelHistogram = new HistogramPanel();
        this.jButtonHistoMinus = new JButton(new ImageIcon(getClass().getResource("/icon/16x16/minus.png")));
        this.jButtonHistoPlus = new JButton(new ImageIcon(getClass().getResource("/icon/16x16/plus.png")));
        init(name);
    }

    private void init(String name) {
        this.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createEmptyBorder(10, 3, 0, 3),
            new TitledBorder(null, name + StringUtil.SPACE + "Intensity Histogram", TitledBorder.DEFAULT_JUSTIFICATION,
                TitledBorder.DEFAULT_POSITION, FontTools.getFont12Bold(), Color.GRAY)));
        this.setLayout(borderLayout1);
        this.add(jPanelHistogram, BorderLayout.CENTER);
        this.add(jPanelSouth, BorderLayout.SOUTH);
        jPanelSouth.setLayout(new BorderLayout(0, 0));
        jPanelSouth.add(panel, BorderLayout.NORTH);
        panel.setLayout(new FlowLayout(FlowLayout.LEADING, 5, 5));
        panel.add(jButtonHistoMinus);

        jButtonHistoMinus.setToolTipText("Shrink histogram vertically");
        jButtonHistoMinus.setPreferredSize(JMVUtils.getSmallIconButtonSize());
        panel.add(jButtonHistoPlus);
        jButtonHistoPlus.setToolTipText("Stretch histogram vertically");
        jButtonHistoPlus.setPreferredSize(JMVUtils.getSmallIconButtonSize());

        panel.add(Box.createHorizontalStrut(15));
        panel.add(jCheckAccumulate);
        jCheckAccumulate.addActionListener(e -> jPanelHistogram.setAccumulate(jCheckAccumulate.isSelected()));
        panel.add(jCheckLogarithmic);
        jCheckLogarithmic.addActionListener(e -> jPanelHistogram.setLogarithmic(jCheckLogarithmic.isSelected()));
        panel.add(jCheckShowIntensity);
        jCheckShowIntensity.addActionListener(e -> jPanelHistogram.setShowIntensity(jCheckShowIntensity.isSelected()));
        jButtonHistoPlus.addActionListener(e -> jPanelHistogram.updateZoom(true));
        jButtonHistoMinus.addActionListener(e -> jPanelHistogram.updateZoom(false));
        panel.add(jButtonReset);
        jButtonReset.addActionListener(e -> reset());
//        panel.add(jButtonSave);
//        jButtonSave.addActionListener(e -> save());
        jPanelSouth.add(jLabelResultVal, BorderLayout.CENTER);
    }

    private void save() {
        JFileChooser saveFC = new JFileChooser();
        int ret = saveFC.showSaveDialog(this);
        if (ret == JFileChooser.APPROVE_OPTION) {
            jPanelHistogram.saveHistogramInCSV(saveFC.getSelectedFile());
        }
    }

    private void reset() {
        jPanelHistogram.resetDisplay();
        jCheckLogarithmic.setSelected(jPanelHistogram.isLogarithmic());
        jCheckShowIntensity.setSelected(jPanelHistogram.isShowIntensity());
        jCheckAccumulate.setSelected(jPanelHistogram.isAccumulate());
    }

    public void setHistogramBins(float[] histValues, ByteLut lut, WindLevelParameters p) {
        jPanelHistogram.setHistogramBins(histValues, lut, jCheckAccumulate.isSelected(), jCheckLogarithmic.isSelected(),
            jCheckShowIntensity.isSelected());
        jPanelHistogram.setWindLevelParameters(p);
    }

    public void setDisplayMinMaxLabel(int min, int max) {
        String val = "Val:";
        if (jCheckLogarithmic.isSelected()) {
            val += max + "-" + min;
        } else {
            val += min + "-" + max;
        }
        jLabelResultVal.setText(val);
    }

    public void setWindLevelParameters(WindLevelParameters p) {
        jPanelHistogram.setWindLevelParameters(p);
        jPanelHistogram.repaint();
    }
}
