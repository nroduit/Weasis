package org.weasis.dicom.viewer2d.mip;

import java.awt.Component;
import java.awt.Container;
import java.awt.Dialog.ModalityType;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.DefaultBoundedRangeModel;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.gui.util.ActionState;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.gui.util.JMVUtils;
import org.weasis.core.api.gui.util.JSliderW;
import org.weasis.core.api.gui.util.SliderChangeListener;
import org.weasis.core.api.gui.util.SliderCineListener;
import org.weasis.core.api.util.StringUtil;
import org.weasis.core.ui.editor.image.ShowPopup;
import org.weasis.dicom.viewer2d.Messages;

public class MipPopup implements ShowPopup {
    private final Logger LOGGER = LoggerFactory.getLogger(MipPopup.class);

    public static JDialog buildDialog(final MipView view) {
        if (view == null) {
            return null;
        }
        final JDialog dialog =
            new JDialog(SwingUtilities.getWindowAncestor(view),
                Messages.getString("MipPopup.title"), ModalityType.APPLICATION_MODAL); //$NON-NLS-1$
        dialog.setIconImage(MipView.MIP_ICON_SETTING.getImage());
        final Container panel_1 = dialog.getContentPane();
        panel_1.setLayout(new BoxLayout(panel_1, BoxLayout.Y_AXIS));

        final JPanel framePanel = new JPanel();
        framePanel.setBorder(new TitledBorder(null,
            Messages.getString("MipPopup.projection"), TitledBorder.LEADING, TitledBorder.TOP, null, null)); //$NON-NLS-1$
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
        rdbtnMinProjection.addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent e) {
                if (e.getSource() instanceof JRadioButton) {
                    JRadioButton btn = (JRadioButton) e.getSource();
                    if (btn.isSelected()) {
                        view.setActionsInView(MipView.MIP.cmd(), MipView.Type.MIN);
                        view.buildMip(false);
                    }
                }
            }
        });
        rdbtnMeanProjection.addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent e) {
                if (e.getSource() instanceof JRadioButton) {
                    JRadioButton btn = (JRadioButton) e.getSource();
                    if (btn.isSelected()) {
                        view.setActionsInView(MipView.MIP.cmd(), MipView.Type.MEAN);
                        view.buildMip(false);
                    }
                }
            }
        });
        rdbtnMaxProjection.addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent e) {
                if (e.getSource() instanceof JRadioButton) {
                    JRadioButton btn = (JRadioButton) e.getSource();
                    if (btn.isSelected()) {
                        view.setActionsInView(MipView.MIP.cmd(), MipView.Type.MAX);
                        view.buildMip(false);
                    }
                }
            }
        });

        ActionState sequence = view.getEventManager().getAction(ActionW.SCROLL_SERIES);
        if (sequence instanceof SliderCineListener) {
            SliderCineListener cineAction = (SliderCineListener) sequence;
            final JSliderW frameSlider = cineAction.createSlider(4, true);
            panel_1.add(frameSlider.getParent());
            final JSliderW sliderThickness =
                createSlider(MipView.MIP_THICKNESS.getTitle(), 4, true, cineAction.getModel());
            panel_1.add(sliderThickness.getParent());
            Integer extend = (Integer) view.getActionValue(MipView.MIP_THICKNESS.cmd());
            sliderThickness.setValue(extend == null ? 2 : extend);
            updateSliderProoperties(sliderThickness, MipView.MIP_THICKNESS.getTitle() + StringUtil.COLON_AND_SPACE
                + sliderThickness.getValue());

            frameSlider.addChangeListener(new ChangeListener() {

                @Override
                public void stateChanged(ChangeEvent e) {
                    JSliderW slider = (JSliderW) e.getSource();
                    // updateSliderProoperties(slider, ActionW.SCROLL_SERIES.getTitle() + StringUtil.COLON_AND_SPACE
                    // + slider.getValue());
                    if (!slider.getValueIsAdjusting()) {
                        view.setActionsInView(ActionW.SCROLL_SERIES.cmd(), slider.getValue());
                        view.buildMip(false);
                    }
                }
            });
            sliderThickness.addChangeListener(new ChangeListener() {

                @Override
                public void stateChanged(ChangeEvent e) {
                    JSliderW slider = (JSliderW) e.getSource();
                    updateSliderProoperties(slider, MipView.MIP_THICKNESS.getTitle() + StringUtil.COLON_AND_SPACE
                        + slider.getValue());
                    if (!slider.getValueIsAdjusting()) {
                        view.setActionsInView(MipView.MIP_THICKNESS.cmd(), slider.getValue());
                        view.buildMip(false);
                    }
                }
            });
        }
        JPanel panel = new JPanel();
        FlowLayout flowLayout = (FlowLayout) panel.getLayout();
        flowLayout.setAlignment(FlowLayout.TRAILING);
        panel.setBorder(new EmptyBorder(20, 15, 10, 15));
        dialog.getContentPane().add(panel);

        JButton btnExitMipMode = new JButton("Rebuild Series");
        btnExitMipMode.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                view.buildMip(true);
                dialog.dispose();
            }
        });
        panel.add(btnExitMipMode);

        Component horizontalStrut = Box.createHorizontalStrut(20);
        panel.add(horizontalStrut);

        JButton btnClose = new JButton(Messages.getString("MipPopup.close")); //$NON-NLS-1$
        btnClose.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                view.exitMipMode(view.getSeries(), null);
                dialog.dispose();
            }
        });
        panel.add(btnClose);
        return dialog;

    }

    static JSliderW createSlider(String title, int labelDivision, boolean displayValueInTitle,
        DefaultBoundedRangeModel model) {
        final JPanel palenSlider1 = new JPanel();
        palenSlider1.setLayout(new BoxLayout(palenSlider1, BoxLayout.Y_AXIS));
        palenSlider1.setBorder(new TitledBorder(title));
        JSliderW slider = new JSliderW(model.getMinimum(), model.getMaximum() / 2 + 1, 2);
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

    @Override
    public void showPopup(Component invoker, int x, int y) {
        if (invoker instanceof MipView) {
            JDialog dialog = buildDialog((MipView) invoker);
            dialog.pack();
            JMVUtils.showCenterScreen(dialog, invoker);
        }
    }
}
