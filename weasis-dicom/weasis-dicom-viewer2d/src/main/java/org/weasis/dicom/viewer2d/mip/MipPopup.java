/*******************************************************************************
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.weasis.dicom.viewer2d.mip;

import java.awt.Component;
import java.awt.Container;
import java.awt.FlowLayout;
import java.awt.event.ActionListener;
import java.util.Comparator;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.DefaultBoundedRangeModel;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import javax.swing.event.ChangeListener;

import org.weasis.core.api.gui.util.ActionState;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.gui.util.DecFormater;
import org.weasis.core.api.gui.util.Filter;
import org.weasis.core.api.gui.util.JSliderW;
import org.weasis.core.api.gui.util.SliderChangeListener;
import org.weasis.core.api.gui.util.SliderCineListener;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.media.data.SeriesComparator;
import org.weasis.core.util.StringUtil;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.viewer2d.Messages;

public class MipPopup {

    public static MipDialog buildDialog(final MipView view) {
        if (view == null || view.isProcessRunning()) {
            return null;
        }
        return new MipDialog(view);
    }

    static JSliderW createSlider(String title, int labelDivision, boolean displayValueInTitle,
        DefaultBoundedRangeModel model) {
        final JPanel palenSlider1 = new JPanel();
        palenSlider1.setLayout(new BoxLayout(palenSlider1, BoxLayout.Y_AXIS));
        palenSlider1.setBorder(new TitledBorder(title));
        JSliderW slider = new JSliderW(model.getMinimum(), model.getMaximum() / 2 + 1, 1);
        slider.setLabelDivision(labelDivision);
        slider.setdisplayValueInTitle(displayValueInTitle);
        slider.setPaintTicks(true);
        palenSlider1.add(slider);
        if (labelDivision > 0) {
            slider.setPaintLabels(true);
            SliderChangeListener.setSliderLabelValues(slider, slider.getMinimum(), slider.getMaximum());
        }
        return slider;
    }

    static void updateSliderProoperties(JSliderW slider, String title) {
        JPanel panel = (JPanel) slider.getParent();
        if (slider.isdisplayValueInTitle() && panel != null && panel.getBorder() instanceof TitledBorder) {
            ((TitledBorder) panel.getBorder()).setTitle(title);
            panel.repaint();
        } else {
            slider.setToolTipText(title);
        }
    }

    public static class MipDialog extends JDialog {
        final MipView view;
        JSliderW frameSlider;
        JSliderW thickness;
        ChangeListener scrollListerner;

        public MipDialog(MipView view) {
            super(SwingUtilities.getWindowAncestor(view), Messages.getString("MipPopup.title"), //$NON-NLS-1$
                ModalityType.APPLICATION_MODAL);
            this.view = view;
            this.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
            this.setIconImage(MipView.MIP_ICON_SETTING.getImage());

            init();
        }

        private void init() {
            final Container panel_1 = getContentPane();
            panel_1.setLayout(new BoxLayout(panel_1, BoxLayout.Y_AXIS));

            final JPanel framePanel = new JPanel();
            framePanel.setBorder(new TitledBorder(null, Messages.getString("MipPopup.projection"), TitledBorder.LEADING, //$NON-NLS-1$
                TitledBorder.TOP, null, null));
            final ButtonGroup ratioGroup = new ButtonGroup();

            JRadioButton rdbtnMinProjection = new JRadioButton(Messages.getString("MipPopup.min")); //$NON-NLS-1$
            framePanel.add(rdbtnMinProjection);
            JRadioButton rdbtnMeanProjection = new JRadioButton(Messages.getString("MipPopup.mean")); //$NON-NLS-1$
            framePanel.add(rdbtnMeanProjection);
            JRadioButton rdbtnMaxProjection = new JRadioButton(Messages.getString("MipPopup.max")); //$NON-NLS-1$
            framePanel.add(rdbtnMaxProjection);
            panel_1.add(framePanel);
            ratioGroup.add(rdbtnMinProjection);
            ratioGroup.add(rdbtnMeanProjection);
            ratioGroup.add(rdbtnMaxProjection);
            MipView.Type type = (MipView.Type) view.getActionValue(MipView.MIP.cmd());
            if (MipView.Type.MIN.equals(type)) {
                rdbtnMinProjection.setSelected(true);
            } else if (MipView.Type.MEAN.equals(type)) {
                rdbtnMeanProjection.setSelected(true);
            } else {
                rdbtnMaxProjection.setSelected(true);
            }
            rdbtnMinProjection.addActionListener(e -> {
                if (e.getSource() instanceof JRadioButton) {
                    JRadioButton btn = (JRadioButton) e.getSource();
                    if (btn.isSelected()) {
                        view.setActionsInView(MipView.MIP.cmd(), MipView.Type.MIN);
                        MipView.buildMip(view, false);
                    }
                }
            });
            rdbtnMeanProjection.addActionListener(e -> {
                if (e.getSource() instanceof JRadioButton) {
                    JRadioButton btn = (JRadioButton) e.getSource();
                    if (btn.isSelected()) {
                        view.setActionsInView(MipView.MIP.cmd(), MipView.Type.MEAN);
                        MipView.buildMip(view, false);
                    }
                }
            });
            rdbtnMaxProjection.addActionListener(e -> {
                if (e.getSource() instanceof JRadioButton) {
                    JRadioButton btn = (JRadioButton) e.getSource();
                    if (btn.isSelected()) {
                        view.setActionsInView(MipView.MIP.cmd(), MipView.Type.MAX);
                        MipView.buildMip(view, false);
                    }
                }
            });

            ActionListener close = e -> dispose();

            ActionState sequence = view.getEventManager().getAction(ActionW.SCROLL_SERIES);
            if (sequence instanceof SliderCineListener) {
                SliderCineListener cineAction = (SliderCineListener) sequence;
                frameSlider = cineAction.createSlider(2, true);
                panel_1.add(frameSlider.getParent());
                final JSliderW sliderThickness =
                    createSlider(MipView.MIP_THICKNESS.getTitle(), 4, true, cineAction.getSliderModel());
                thickness = sliderThickness;
                panel_1.add(sliderThickness.getParent());
                Integer extend = (Integer) view.getActionValue(MipView.MIP_THICKNESS.cmd());
                sliderThickness.setValue(extend == null ? 2 : extend);
                updateSliderProoperties(sliderThickness,
                    MipView.MIP_THICKNESS.getTitle() + StringUtil.COLON_AND_SPACE + sliderThickness.getValue());

                scrollListerner = e -> {
                    JSliderW slider = (JSliderW) e.getSource();
                    getThickness(sliderThickness);
                    view.setActionsInView(ActionW.SCROLL_SERIES.cmd(), slider.getValue());
                    MipView.buildMip(view, false);
                };
                frameSlider.addChangeListener(scrollListerner);
                sliderThickness.addChangeListener(e -> {
                    JSliderW slider = (JSliderW) e.getSource();
                    getThickness(slider);
                    view.setActionsInView(MipView.MIP_THICKNESS.cmd(), slider.getValue());
                    MipView.buildMip(view, false);
                });
            }
            JPanel panel = new JPanel();
            FlowLayout flowLayout = (FlowLayout) panel.getLayout();
            flowLayout.setAlignment(FlowLayout.TRAILING);
            panel.setBorder(new EmptyBorder(20, 15, 10, 15));
            getContentPane().add(panel);

            JButton btnExitMipMode = new JButton(Messages.getString("MipPopup.rebuild_series")); //$NON-NLS-1$
            btnExitMipMode.addActionListener(e -> {
                MipView.buildMip(view, true);
                dispose();
            });
            panel.add(btnExitMipMode);

            Component horizontalStrut = Box.createHorizontalStrut(20);
            panel.add(horizontalStrut);

            JButton btnClose = new JButton(Messages.getString("MipPopup.close")); //$NON-NLS-1$
            btnClose.addActionListener(close);
            panel.add(btnClose);
        }

        private void getThickness(final JSliderW sliderThickness) {
            StringBuilder buf = new StringBuilder(MipView.MIP_THICKNESS.getTitle());
            buf.append(StringUtil.COLON_AND_SPACE);
            int val = sliderThickness.getValue();
            buf.append(val);
            MediaSeries<DicomImageElement> series = view.getSeries();
            if (series != null) {
                int slice = frameSlider.getValue() - 1;
                SeriesComparator sort = (SeriesComparator) view.getActionValue(ActionW.SORTSTACK.cmd());
                Boolean reverse = (Boolean) view.getActionValue(ActionW.INVERSESTACK.cmd());
                Comparator sortFilter = (reverse != null && reverse) ? sort.getReversOrderComparator() : sort;
                Filter filter = (Filter) view.getActionValue(ActionW.FILTERED_SERIES.cmd());
                int min = Math.max(0, slice - val);
                int max = Math.min(series.size(filter) - 1, slice + val);

                DicomImageElement fimg = series.getMedia(min, filter, sortFilter);
                DicomImageElement limg = series.getMedia(max, filter, sortFilter);

                if (fimg != null && limg != null) {
                    buf.append(" ("); //$NON-NLS-1$
                    buf.append(DecFormater.allNumber(SeriesBuilder.getThickness(fimg, limg)));
                    buf.append(" "); //$NON-NLS-1$
                    buf.append(fimg.getPixelSpacingUnit().getAbbreviation());
                    buf.append(")"); //$NON-NLS-1$
                }
            }
            updateSliderProoperties(sliderThickness, buf.toString());
        }

        public void updateThickness() {
            getThickness(thickness);
        }

        @Override
        public void dispose() {
            if (frameSlider != null) {
                frameSlider.removeChangeListener(scrollListerner);
                ActionState sequence = view.getEventManager().getAction(ActionW.SCROLL_SERIES);
                if (sequence instanceof SliderCineListener) {
                    SliderCineListener cineAction = (SliderCineListener) sequence;
                    cineAction.unregisterActionState(frameSlider);
                }
            }

            view.exitMipMode(view.getSeries(), null);
            super.dispose();
        }
    }
}
