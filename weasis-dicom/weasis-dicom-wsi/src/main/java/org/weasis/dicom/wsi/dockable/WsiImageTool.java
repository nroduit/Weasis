/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.wsi.dockable;

import java.awt.Component;
import java.util.Optional;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.border.Border;
import org.weasis.core.api.gui.Insertable;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.gui.util.CollapsiblePanel;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.gui.util.JSliderW;
import org.weasis.core.api.gui.util.WrapFlowLayout;
import org.weasis.core.api.setting.Settings;
import org.weasis.core.api.util.ResourceUtil;
import org.weasis.core.api.util.ResourceUtil.OtherIcon;
import org.weasis.core.ui.docking.PluginTool;
import org.weasis.dicom.viewer2d.EventManager;
import org.weasis.dicom.viewer2d.ResetTools;
import org.weasis.dicom.wsi.Messages;
import org.weasis.dicom.wsi.WsiSettings;
import org.weasis.dicom.wsi.WsiView2d;

/**
 * The image tool of the slide viewer. The tiles of a slide are shown as stored: the window, the
 * lookup tables, the filters, the cine, the fusion and the subtraction of the 2D viewer do not
 * apply. What remains is where the slide is looked at from: the magnification, the rotation and the
 * flip, and their reset. The tool keeps the state of its sections apart from the 2D viewer.
 */
public class WsiImageTool extends PluginTool {

  // Apart from the sections of the image tool of the 2D viewer
  private static final String PREF_SECTION = "weasis.wsi.image.tool.section."; // NON-NLS
  private static final Border SECTION_CONTENT_BORDER = GuiUtils.getEmptyBorder(2, 6, 6, 6);

  private final JScrollPane rootPane = new JScrollPane();
  private final JPanel presets = new JPanel(new WrapFlowLayout(3, 3));

  public WsiImageTool(String pluginName) {
    super("image", pluginName, Insertable.Type.TOOL, 20); // NON-NLS
    setIcon(ResourceUtil.getIcon(OtherIcon.IMAGE_EDIT));
    setDockableWidth(290);
    setLayout(new BoxLayout(this, BoxLayout.PAGE_AXIS));
    addSection(
        Messages.getString("WsiImageTool.magnification"), magnificationPanel(), "magnification");
    addSection(Messages.getString("WsiImageTool.transform"), transformPanel(), "transform");
    addSection(ActionW.RESET.getTitle(), resetPanel(), "reset"); // NON-NLS
    add(GuiUtils.boxYLastElement(3));
    rootPane.setBorder(BorderFactory.createEmptyBorder());
    // The tool lives as long as the application: the listener is never removed
    Settings.addListener(WsiSettings.ZOOM_PRESETS, _ -> fillPresets());
  }

  private void addSection(String title, JComponent content, String name) {
    content.setBorder(SECTION_CONTENT_BORDER);
    add(new CollapsiblePanel(title, content, true, PREF_SECTION + name));
  }

  @Override
  public Component getToolComponent() {
    return getToolComponentFromJScrollPane(rootPane);
  }

  // The fit, the base level and the presets of the preferences. The zoom slider of the 2D viewer
  // is left out: its range stops far above the scale at which a slide is seen whole
  private JPanel magnificationPanel() {
    JPanel panel = GuiUtils.getVerticalBoxLayoutPanel();
    fillPresets();
    panel.add(presets);
    return panel;
  }

  private void fillPresets() {
    presets.removeAll();
    presets.add(
        button(
            Messages.getString("WsiImageTool.fit"),
            Messages.getString("WsiToolBar.fit"),
            WsiView2d::fitToView));
    presets.add(
        button(
            Messages.getString("WsiImageTool.one_to_one"),
            Messages.getString("WsiToolBar.one_to_one"),
            WsiView2d::oneToOne));
    for (double magnification : WsiSettings.presets()) {
      presets.add(
          button(
              Messages.format("WsiView.magnification", magnification),
              null,
              v -> v.zoomToMagnification(magnification)));
    }
    presets.revalidate();
    presets.repaint();
  }

  private static JPanel transformPanel() {
    JPanel panel = GuiUtils.getVerticalBoxLayoutPanel();
    EventManager.getInstance()
        .getAction(ActionW.ROTATION)
        .ifPresent(
            rotation -> {
              JSliderW slider = rotation.createSlider(5, true);
              GuiUtils.setPreferredWidth(slider, 100);
              panel.add(slider);
            });
    JPanel turns = new JPanel(new WrapFlowLayout(3, 3));
    turns.add(
        button(
            Messages.getString("WsiImageTool.rotate_left"),
            Messages.getString("WsiToolBar.rotate_left"),
            v -> v.rotate(-1)));
    turns.add(
        button(
            Messages.getString("WsiImageTool.rotate_right"),
            Messages.getString("WsiToolBar.rotate_right"),
            v -> v.rotate(1)));
    panel.add(turns);
    EventManager.getInstance()
        .getAction(ActionW.FLIP)
        .ifPresent(
            flip -> {
              JCheckBox checkBox =
                  GuiUtils.shrinkable(flip.createCheckBox(Messages.getString("WsiImageTool.flip")));
              panel.add(GuiUtils.getHorizontalBoxLayoutPanel(5, checkBox));
            });
    return panel;
  }

  // The resets that mean something for a slide: no window to reset
  private static JPanel resetPanel() {
    JPanel panel = new JPanel(new WrapFlowLayout(3, 3));
    for (ResetTools reset :
        new ResetTools[] {ResetTools.ALL, ResetTools.ZOOM, ResetTools.ROTATION, ResetTools.PAN}) {
      JButton button = new JButton(reset.toString());
      button.addActionListener(_ -> EventManager.getInstance().reset(reset));
      EventManager.getInstance()
          .getAction(ActionW.RESET)
          .ifPresent(a -> a.registerActionState(button));
      panel.add(button);
    }
    return panel;
  }

  private static JButton button(String text, String tooltip, Consumer<WsiView2d> action) {
    JButton button = new JButton(text);
    button.setToolTipText(tooltip);
    button.addActionListener(_ -> selectedView().ifPresent(action));
    return button;
  }

  private static Optional<WsiView2d> selectedView() {
    return EventManager.getInstance().getSelectedViewPane() instanceof WsiView2d view
        ? Optional.of(view)
        : Optional.empty();
  }
}
