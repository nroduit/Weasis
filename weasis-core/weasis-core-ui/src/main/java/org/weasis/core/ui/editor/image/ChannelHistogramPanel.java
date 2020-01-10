/*******************************************************************************
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
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
import javax.swing.JPanel;
import javax.swing.border.TitledBorder;

import org.weasis.core.api.gui.util.JMVUtils;
import org.weasis.core.api.image.util.WindLevelParameters;
import org.weasis.core.api.util.FontTools;
import org.weasis.core.ui.Messages;

/**
 * @author Nicolas Roduit
 * @version 1.0
 */

public class ChannelHistogramPanel extends JPanel {

    private final BorderLayout borderLayout1 = new BorderLayout();
    private final HistogramPanel jPanelHistogram;
    private final JButton jButtonHistoMinus;
    private final JButton jButtonHistoPlus;

    private final JCheckBox jCheckAccumulate;
    private final JCheckBox jCheckLogarithmic;
    private final JCheckBox jCheckShowIntensity;
    private final JButton jButtonReset = new JButton(Messages.getString("ChannelHistogramPanel.reset")); //$NON-NLS-1$
  //  private final JButton jButtonSave = new JButton(Messages.getString("ChannelHistogramPanel.save")); //$NON-NLS-1$
    private final JPanel panel = new JPanel();

    public ChannelHistogramPanel(String name) {
        this(name, false, false, true);
    }

    public ChannelHistogramPanel(String name, boolean accumulate, boolean logarithmic, boolean showIntensity) {
        this.jCheckAccumulate = new JCheckBox(Messages.getString("ChannelHistogramPanel.accu"), accumulate); //$NON-NLS-1$
        this.jCheckLogarithmic = new JCheckBox(Messages.getString("ChannelHistogramPanel.log"), logarithmic); //$NON-NLS-1$
        this.jCheckShowIntensity = new JCheckBox(Messages.getString("ChannelHistogramPanel.ShowIntensity"), showIntensity); //$NON-NLS-1$
        this.jPanelHistogram = new HistogramPanel();
        this.jButtonHistoMinus = new JButton(new ImageIcon(getClass().getResource("/icon/16x16/minus.png"))); //$NON-NLS-1$
        this.jButtonHistoPlus = new JButton(new ImageIcon(getClass().getResource("/icon/16x16/plus.png"))); //$NON-NLS-1$
        init(name);
    }

    private void init(String name) {
        this.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createEmptyBorder(10, 3, 0, 3),
            new TitledBorder(null, name, TitledBorder.DEFAULT_JUSTIFICATION,
                TitledBorder.DEFAULT_POSITION, FontTools.getFont12Bold(), Color.GRAY)));
        this.setLayout(borderLayout1);
        this.add(jPanelHistogram, BorderLayout.CENTER);
        this.add(panel, BorderLayout.SOUTH);
        panel.setLayout(new FlowLayout(FlowLayout.LEADING, 5, 5));
        panel.add(jButtonHistoMinus);

        jButtonHistoMinus.setToolTipText(Messages.getString("ChannelHistogramPanel.shrink")); //$NON-NLS-1$
        jButtonHistoMinus.setPreferredSize(JMVUtils.getSmallIconButtonSize());
        panel.add(jButtonHistoPlus);
        jButtonHistoPlus.setToolTipText(Messages.getString("ChannelHistogramPanel.strech")); //$NON-NLS-1$
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
        // panel.add(jButtonSave);
        // jButtonSave.addActionListener(e -> save());
    }

    public boolean isAccumulate() {
        return jCheckAccumulate.isSelected();
    }

    public boolean isLogarithmic() {
        return jCheckLogarithmic.isSelected();
    }

    public boolean isShowIntensity() {
        return jCheckShowIntensity.isSelected();
    }

    public HistogramData getData() {
        return jPanelHistogram.getData();
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

    public void setHistogramBins(HistogramData data) {
        jPanelHistogram.setHistogram(data, jCheckAccumulate.isSelected(), jCheckLogarithmic.isSelected(),
            jCheckShowIntensity.isSelected());
    }

    public void setWindLevelParameters(WindLevelParameters p) {
        jPanelHistogram.setWindLevelParameters(p);
        jPanelHistogram.repaint();
    }

    public void setLut(DisplayByteLut lut) {
        jPanelHistogram.getData().setLut(lut);
        jPanelHistogram.repaint();
    }
}
