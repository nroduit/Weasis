/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.viewer2d.mip;

import java.awt.Container;
import java.awt.FlowLayout;
import java.awt.event.ActionListener;
import java.util.Comparator;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.DefaultBoundedRangeModel;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import javax.swing.border.TitledBorder;
import javax.swing.event.ChangeListener;
import org.weasis.core.api.gui.util.ActionState;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.gui.util.DecFormater;
import org.weasis.core.api.gui.util.Filter;
import org.weasis.core.api.gui.util.GuiUtils;
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

  static JSliderW createSlider(String title, DefaultBoundedRangeModel model) {
    final JPanel sliderPanel = new JPanel();
    sliderPanel.setLayout(new BoxLayout(sliderPanel, BoxLayout.Y_AXIS));
    sliderPanel.setBorder(GuiUtils.getTitledBorder(title));
    JSliderW slider = new JSliderW(model.getMinimum(), model.getMaximum() / 2 + 1, 1);
    slider.setLabelDivision(4);
    slider.setdisplayValueInTitle(true);
    slider.setPaintTicks(true);
    sliderPanel.add(slider);
    slider.setPaintLabels(true);
    SliderChangeListener.setSliderLabelValues(slider, slider.getMinimum(), slider.getMaximum());
    return slider;
  }

  static void updateSliderProperties(JSliderW slider, String title) {
    if (slider.isdisplayValueInTitle() && slider.getBorder() instanceof TitledBorder titledBorder) {
      titledBorder.setTitle(title);
      slider.repaint();
    } else {
      slider.setToolTipText(title);
    }
  }

  public static class MipDialog extends JDialog {
    final MipView view;
    JSliderW frameSlider;
    JSliderW thickness;
    ChangeListener changeListener;

    public MipDialog(MipView view) {
      super(
          SwingUtilities.getWindowAncestor(view),
          Messages.getString("MipPopup.title"),
          ModalityType.APPLICATION_MODAL);
      this.view = view;
      this.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
      this.setIconImage(MipView.MIP_ICON_SETTING.getImage());

      init();
    }

    private void init() {
      final Container contentPane = getContentPane();
      contentPane.setLayout(new BoxLayout(contentPane, BoxLayout.Y_AXIS));

      final JPanel framePanel = new JPanel();
      framePanel.setBorder(GuiUtils.getTitledBorder(Messages.getString("MipPopup.projection")));
      final ButtonGroup ratioGroup = new ButtonGroup();

      JRadioButton rdbtnMinProjection = new JRadioButton(Messages.getString("MipPopup.min"));
      framePanel.add(rdbtnMinProjection);
      JRadioButton rdbtnMeanProjection = new JRadioButton(Messages.getString("MipPopup.mean"));
      framePanel.add(rdbtnMeanProjection);
      JRadioButton rdbtnMaxProjection = new JRadioButton(Messages.getString("MipPopup.max"));
      framePanel.add(rdbtnMaxProjection);
      contentPane.add(framePanel);
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
      rdbtnMinProjection.addActionListener(
          e -> {
            if (e.getSource() instanceof JRadioButton btn) {
              if (btn.isSelected()) {
                view.setActionsInView(MipView.MIP.cmd(), MipView.Type.MIN);
                MipView.buildMip(view, false);
              }
            }
          });
      rdbtnMeanProjection.addActionListener(
          e -> {
            if (e.getSource() instanceof JRadioButton btn) {
              if (btn.isSelected()) {
                view.setActionsInView(MipView.MIP.cmd(), MipView.Type.MEAN);
                MipView.buildMip(view, false);
              }
            }
          });
      rdbtnMaxProjection.addActionListener(
          e -> {
            if (e.getSource() instanceof JRadioButton btn) {
              if (btn.isSelected()) {
                view.setActionsInView(MipView.MIP.cmd(), MipView.Type.MAX);
                MipView.buildMip(view, false);
              }
            }
          });

      ActionListener close = e -> dispose();

      ActionState sequence = view.getEventManager().getAction(ActionW.SCROLL_SERIES);
      if (sequence instanceof SliderCineListener cineAction) {
        frameSlider = cineAction.createSlider(2, true);
        contentPane.add(frameSlider);
        final JSliderW sliderThickness =
            createSlider(MipView.MIP_THICKNESS.getTitle(), cineAction.getSliderModel());
        thickness = sliderThickness;
        contentPane.add(sliderThickness);
        Integer extend = (Integer) view.getActionValue(MipView.MIP_THICKNESS.cmd());
        sliderThickness.setValue(extend == null ? 2 : extend);
        updateSliderProperties(
            sliderThickness,
            MipView.MIP_THICKNESS.getTitle()
                + StringUtil.COLON_AND_SPACE
                + sliderThickness.getValue());

        changeListener =
            e -> {
              JSliderW slider = (JSliderW) e.getSource();
              getThickness(sliderThickness);
              view.setActionsInView(ActionW.SCROLL_SERIES.cmd(), slider.getValue());
              MipView.buildMip(view, false);
            };
        frameSlider.addChangeListener(changeListener);
        sliderThickness.addChangeListener(
            e -> {
              JSliderW slider = (JSliderW) e.getSource();
              getThickness(slider);
              view.setActionsInView(MipView.MIP_THICKNESS.cmd(), slider.getValue());
              MipView.buildMip(view, false);
            });
      }

      JButton btnExitMipMode = new JButton(Messages.getString("MipPopup.rebuild_series"));
      btnExitMipMode.addActionListener(
          e -> {
            MipView.buildMip(view, true);
            dispose();
          });

      JButton btnClose = new JButton(Messages.getString("MipPopup.close"));
      btnClose.addActionListener(close);

      JPanel panel =
          GuiUtils.getFlowLayoutPanel(
              FlowLayout.TRAILING, 5, 5, btnExitMipMode, GuiUtils.boxHorizontalStrut(20), btnClose);
      panel.setBorder(GuiUtils.getEmptyBorder(20, 15, 10, 15));
      contentPane.add(panel);
      contentPane.add(GuiUtils.boxYLastElement(1));
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
        Comparator sortFilter =
            (reverse != null && reverse) ? sort.getReversOrderComparator() : sort;
        Filter filter = (Filter) view.getActionValue(ActionW.FILTERED_SERIES.cmd());
        int min = Math.max(0, slice - val);
        int max = Math.min(series.size(filter) - 1, slice + val);

        DicomImageElement fimg = series.getMedia(min, filter, sortFilter);
        DicomImageElement limg = series.getMedia(max, filter, sortFilter);

        if (fimg != null && limg != null) {
          buf.append(" (");
          buf.append(DecFormater.allNumber(SeriesBuilder.getThickness(fimg, limg)));
          buf.append(" ");
          buf.append(fimg.getPixelSpacingUnit().getAbbreviation());
          buf.append(")");
        }
      }
      updateSliderProperties(sliderThickness, buf.toString());
    }

    public void updateThickness() {
      getThickness(thickness);
    }

    @Override
    public void dispose() {
      if (frameSlider != null) {
        frameSlider.removeChangeListener(changeListener);
        ActionState sequence = view.getEventManager().getAction(ActionW.SCROLL_SERIES);
        if (sequence instanceof SliderCineListener cineAction) {
          cineAction.unregisterActionState(frameSlider);
        }
      }

      view.exitMipMode(view.getSeries(), null);
      super.dispose();
    }
  }
}
