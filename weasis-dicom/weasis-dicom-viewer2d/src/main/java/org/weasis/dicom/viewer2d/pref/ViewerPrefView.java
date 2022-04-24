/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.viewer2d.pref;

import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.event.ItemEvent;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Vector;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JColorChooser;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.SwingUtilities;
import org.weasis.core.api.gui.util.AbstractItemDialogPage;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.gui.util.MouseActionAdapter;
import org.weasis.core.api.image.OpManager;
import org.weasis.core.api.image.WindowOp;
import org.weasis.core.api.image.ZoomOp;
import org.weasis.core.api.image.ZoomOp.Interpolation;
import org.weasis.core.api.service.BundleTools;
import org.weasis.core.api.util.ResourceUtil;
import org.weasis.core.api.util.ResourceUtil.ActionIcon;
import org.weasis.core.ui.docking.UIManager;
import org.weasis.core.ui.editor.image.ImageViewerPlugin;
import org.weasis.core.ui.editor.image.ViewCanvas;
import org.weasis.core.ui.editor.image.ViewerPlugin;
import org.weasis.core.ui.pref.PreferenceDialog;
import org.weasis.core.util.StringUtil;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.codec.display.OverlayOp;
import org.weasis.dicom.viewer2d.EventManager;
import org.weasis.dicom.viewer2d.Messages;
import org.weasis.dicom.viewer2d.PRManager;
import org.weasis.dicom.viewer2d.View2dContainer;
import org.weasis.dicom.viewer2d.View2dFactory;

public class ViewerPrefView extends AbstractItemDialogPage {
  private final Hashtable<Integer, JLabel> labels = new Hashtable<>();
  private final List<ActionW> actions =
      List.of(ActionW.WINDOW, ActionW.LEVEL, ActionW.ZOOM, ActionW.ROTATION, ActionW.SCROLL_SERIES);
  private final Map<ActionW, Integer> map = new HashMap<>();
  private final JComboBox<ActionW> comboBox = new JComboBox<>();
  private final JSlider slider = new JSlider(-100, 100, 0);
  private JComboBox<ZoomOp.Interpolation> comboBoxInterpolation;
  private JCheckBox checkBoxWLcolor;
  private JCheckBox checkBoxLevelInverse;
  private JCheckBox checkBoxApplyPR;
  private final JButton overlayColor;

  public ViewerPrefView() {
    super(View2dFactory.NAME, 501);
    this.overlayColor = new JButton(ResourceUtil.getIcon(ActionIcon.PIPETTE));
    initGUI();
  }

  private void initGUI() {
    labels.put(-100, new JLabel(Messages.getString("ViewerPrefView.low")));
    labels.put(0, new JLabel(Messages.getString("ViewerPrefView.mid")));
    labels.put(100, new JLabel(Messages.getString("ViewerPrefView.high")));

    EventManager eventManager = EventManager.getInstance();
    formatSlider(slider);

    for (ActionW a : actions) {
      if (eventManager.getAction(a) instanceof MouseActionAdapter action) {
        map.put(a, realValueToSlider(action.getMouseSensitivity()));
      }
    }

    comboBox.setModel(new DefaultComboBoxModel<>(new Vector<>(actions)));
    comboBox.addItemListener(
        e -> {
          if (e.getStateChange() == ItemEvent.SELECTED) {
            Object item = e.getItem();
            if (item instanceof ActionW a) {
              slider.setValue(map.get(a));
            }
          }
        });
    slider.setValue(map.get(actions.get(0)));
    slider.addChangeListener(
        e -> {
          Object item = comboBox.getSelectedItem();
          if (item instanceof ActionW a) {
            map.put(a, slider.getValue());
          }
        });

    overlayColor.setToolTipText(org.weasis.core.ui.Messages.getString("MeasureTool.pick"));
    overlayColor.addActionListener(
        e -> {
          Color newColor =
              JColorChooser.showDialog(
                  SwingUtilities.getWindowAncestor(this),
                  org.weasis.core.ui.Messages.getString("MeasureTool.pick_color"),
                  getOverlayColor());
          if (newColor != null) {
            BundleTools.SYSTEM_PREFERENCES.putColorProperty(OverlayOp.OVERLAY_COLOR_KEY, newColor);
          }
        });

    JPanel panel = GuiUtils.getVerticalBoxLayoutPanel();
    panel.setBorder(GuiUtils.getTitledBorder(Messages.getString("ViewerPrefView.mouse_sens")));
    panel.add(GuiUtils.getFlowLayoutPanel(comboBox));
    panel.add(GuiUtils.getHorizontalBoxLayoutPanel(ITEM_SEPARATOR, slider));
    panel.add(GuiUtils.boxVerticalStrut(ITEM_SEPARATOR));
    add(panel);
    add(GuiUtils.boxVerticalStrut(BLOCK_SEPARATOR));

    JLabel lblInterpolation =
        new JLabel(Messages.getString("ViewerPrefView.interp") + StringUtil.COLON);
    comboBoxInterpolation = new JComboBox<>(ZoomOp.Interpolation.values());
    comboBoxInterpolation.setSelectedIndex(eventManager.getZoomSetting().getInterpolation());

    int shiftX = ITEM_SEPARATOR - ITEM_SEPARATOR_SMALL;
    JPanel panel1 =
        GuiUtils.getFlowLayoutPanel(
            FlowLayout.LEADING,
            ITEM_SEPARATOR_SMALL,
            ITEM_SEPARATOR,
            GuiUtils.boxHorizontalStrut(shiftX),
            lblInterpolation,
            comboBoxInterpolation);
    panel1.setBorder(GuiUtils.getTitledBorder(Messages.getString("ViewerPrefView.zoom")));
    add(panel1);
    add(GuiUtils.boxVerticalStrut(BLOCK_SEPARATOR));

    checkBoxWLcolor =
        new JCheckBox(
            Messages.getString("ViewerPrefView.wl_color"),
            eventManager.getOptions().getBooleanProperty(WindowOp.P_APPLY_WL_COLOR, true));
    checkBoxLevelInverse =
        new JCheckBox(
            Messages.getString("ViewerPrefView.inverse_wl"),
            eventManager.getOptions().getBooleanProperty(WindowOp.P_INVERSE_LEVEL, true));
    checkBoxApplyPR =
        new JCheckBox(
            Messages.getString("ViewerPrefView.apply_pr"),
            eventManager.getOptions().getBooleanProperty(PRManager.PR_APPLY, false));

    final JPanel otherPanel = GuiUtils.getVerticalBoxLayoutPanel();
    otherPanel.setBorder(GuiUtils.getTitledBorder(Messages.getString("ViewerPrefView.other")));
    otherPanel.add(GuiUtils.getFlowLayoutPanel(checkBoxWLcolor));
    otherPanel.add(GuiUtils.getFlowLayoutPanel(checkBoxLevelInverse));
    otherPanel.add(GuiUtils.getFlowLayoutPanel(checkBoxApplyPR));
    otherPanel.add(
        GuiUtils.getFlowLayoutPanel(new JLabel(Messages.getString("overlay.color")), overlayColor));
    add(otherPanel);

    add(GuiUtils.boxYLastElement(LAST_FILLER_HEIGHT));
    getProperties().setProperty(PreferenceDialog.KEY_SHOW_RESTORE, Boolean.TRUE.toString());
  }

  private Color getOverlayColor() {
    return BundleTools.SYSTEM_PREFERENCES.getColorProperty(
        OverlayOp.OVERLAY_COLOR_KEY, Color.WHITE);
  }

  @Override
  public void closeAdditionalWindow() {
    EventManager eventManager = EventManager.getInstance();
    for (ActionW a : actions) {
      if (eventManager.getAction(a) instanceof MouseActionAdapter action) {
        action.setMouseSensitivity(sliderToRealValue(map.get(a)));
      }
    }

    int interpolationPosition = comboBoxInterpolation.getSelectedIndex();
    eventManager.getZoomSetting().setInterpolation(interpolationPosition);
    boolean applyWLcolor = checkBoxWLcolor.isSelected();
    eventManager.getOptions().putBooleanProperty(WindowOp.P_APPLY_WL_COLOR, applyWLcolor);

    eventManager.getOptions().putBooleanProperty(PRManager.PR_APPLY, checkBoxApplyPR.isSelected());
    eventManager
        .getOptions()
        .putBooleanProperty(WindowOp.P_INVERSE_LEVEL, checkBoxLevelInverse.isSelected());
    ImageViewerPlugin<DicomImageElement> view = eventManager.getSelectedView2dContainer();
    if (view != null) {
      view.setMouseActions(eventManager.getMouseActions());
    }

    Interpolation inter = Interpolation.getInterpolation(interpolationPosition);
    synchronized (UIManager.VIEWER_PLUGINS) {
      for (final ViewerPlugin<?> p : UIManager.VIEWER_PLUGINS) {
        if (p instanceof View2dContainer viewer) {
          for (ViewCanvas<DicomImageElement> v : viewer.getImagePanels()) {
            OpManager disOp = v.getDisplayOpManager();
            disOp.setParamValue(WindowOp.OP_NAME, WindowOp.P_APPLY_WL_COLOR, applyWLcolor);
            v.changeZoomInterpolation(inter);
          }
        }
      }
    }

    BundleTools.saveSystemPreferences();
  }

  @Override
  public void resetToDefaultValues() {
    map.put(ActionW.WINDOW, realValueToSlider(1.25));
    map.put(ActionW.LEVEL, realValueToSlider(1.25));
    map.put(ActionW.SCROLL_SERIES, realValueToSlider(0.1));
    map.put(ActionW.ROTATION, realValueToSlider(0.25));
    map.put(ActionW.ZOOM, realValueToSlider(0.1));
    slider.setValue(map.get(comboBox.getSelectedItem()));

    comboBoxInterpolation.setSelectedItem(Interpolation.BILINEAR);

    // Get the default server configuration and if no value take the default value in parameter.
    EventManager eventManager = EventManager.getInstance();
    eventManager.getOptions().resetProperty(WindowOp.P_APPLY_WL_COLOR, Boolean.TRUE.toString());

    checkBoxWLcolor.setSelected(
        eventManager.getOptions().getBooleanProperty(WindowOp.P_APPLY_WL_COLOR, true));
    checkBoxLevelInverse.setSelected(
        eventManager.getOptions().getBooleanProperty(WindowOp.P_INVERSE_LEVEL, true));
    checkBoxApplyPR.setSelected(
        eventManager.getOptions().getBooleanProperty(PRManager.PR_APPLY, false));
    BundleTools.SYSTEM_PREFERENCES.putColorProperty(OverlayOp.OVERLAY_COLOR_KEY, Color.WHITE);
  }

  private void formatSlider(JSlider slider) {
    slider.setPaintTicks(true);
    slider.setMajorTickSpacing(100);
    slider.setMinorTickSpacing(5);
    slider.setLabelTable(labels);
    slider.setPaintLabels(true);
  }

  private static double sliderToRealValue(int value) {
    return Math.pow(10, value * 3.0 / 100.0);
  }

  private static int realValueToSlider(double value) {
    return (int) (Math.log10(value) * 100.0 / 3.0);
  }
}
