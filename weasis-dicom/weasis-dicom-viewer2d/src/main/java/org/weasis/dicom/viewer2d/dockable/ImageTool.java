/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.viewer2d.dockable;

import com.formdev.flatlaf.extras.FlatSVGIcon;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.text.DecimalFormat;
import java.util.List;
import java.util.Optional;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JToggleButton;
import javax.swing.border.Border;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;
import javax.swing.text.NumberFormatter;
import org.dcm4che3.data.Tag;
import org.weasis.core.api.gui.Insertable;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.gui.util.CollapsiblePanel;
import org.weasis.core.api.gui.util.ComboItemListener;
import org.weasis.core.api.gui.util.Feature.SliderChangeListenerValue;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.gui.util.JSliderW;
import org.weasis.core.api.gui.util.SliderChangeListener;
import org.weasis.core.api.util.ResourceUtil;
import org.weasis.core.api.util.ResourceUtil.ActionIcon;
import org.weasis.core.api.util.ResourceUtil.OtherIcon;
import org.weasis.core.ui.docking.PluginTool;
import org.weasis.core.ui.editor.image.ImageViewerEventManager;
import org.weasis.core.ui.editor.image.ViewCanvas;
import org.weasis.core.util.StringUtil;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.codec.TagD;
import org.weasis.dicom.viewer2d.EventManager;
import org.weasis.dicom.viewer2d.Messages;
import org.weasis.dicom.viewer2d.ResetTools;
import org.weasis.dicom.viewer2d.WindowPresetActions;
import org.weasis.dicom.viewer2d.fusion.FusionAction;
import org.weasis.dicom.viewer2d.subtraction.FrameMask;
import org.weasis.dicom.viewer2d.subtraction.SubtractionAction;
import org.weasis.dicom.viewer2d.subtraction.SubtractionController;

public class ImageTool extends PluginTool {

  public static final String BUTTON_NAME = Messages.getString("ImageTool.img_tool");

  // Local-persistence keys for the expanded/collapsed state of each collapsible section.
  private static final String PREF_SECTION = "weasis.image.tool.section."; // NON-NLS
  private static final Border SECTION_CONTENT_BORDER = GuiUtils.getEmptyBorder(2, 6, 6, 6);

  private final JScrollPane rootPane = new JScrollPane();
  private final Border spaceY = GuiUtils.getEmptyBorder(15, 3, 0, 3);

  public ImageTool(String pluginName) {
    super("image", pluginName, Insertable.Type.TOOL, 20); // NON-NLS
    setIcon(ResourceUtil.getIcon(OtherIcon.IMAGE_EDIT));
    setDockableWidth(290);
    init();
  }

  private void init() {
    setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
    addSection(
        Messages.getString("ImageTool.wl"),
        getWindowLevelPanel(EventManager.getInstance(), spaceY, true),
        true,
        "wl"); // NON-NLS
    addSection(
        Messages.getString("ImageTool.transform"), getTransformPanel(spaceY), true, "transform");
    addSection(Messages.common("cine"), getSlicePanel(spaceY), true, "slice"); // NON-NLS
    // Advanced image operations are collapsed by default to keep the tool compact.
    addSection(Messages.getString("FusionTool.title"), getFusionPanel(spaceY), false, "fusion");
    addSubtractionSection();
    addSection(ActionW.RESET.getTitle(), getResetPanel(spaceY), true, "reset"); // NON-NLS
    add(GuiUtils.boxYLastElement(3));
    rootPane.setBorder(BorderFactory.createEmptyBorder()); // remove default line
  }

  /**
   * Wraps a section panel in a {@link CollapsiblePanel}: the panel's own titled border is dropped
   * (the collapsible header carries the title) and its expanded state is persisted under {@code
   * name}.
   */
  private CollapsiblePanel addSection(
      String title, JComponent content, boolean expandedByDefault, String name) {
    content.setBorder(SECTION_CONTENT_BORDER);
    CollapsiblePanel section =
        new CollapsiblePanel(title, content, expandedByDefault, PREF_SECTION + name);
    add(section);
    return section;
  }

  /**
   * The subtraction section is contextual: it shows only while the selected series has a series of
   * its study it can subtract, which the mask combo lists.
   */
  private void addSubtractionSection() {
    EventManager manager = EventManager.getInstance();
    Optional<ComboItemListener<Object>> mask = manager.getAction(SubtractionAction.MASK);
    if (mask.isEmpty()) {
      return;
    }
    JComboBox<?> maskCombo = mask.get().createCombo(160);
    CollapsiblePanel section =
        addSection(
            Messages.getString("SubtractionTool.title"),
            getSubtractionPanel(manager, maskCombo, spaceY),
            false,
            "subtraction"); // NON-NLS
    ListDataListener contextual =
        new ListDataListener() {
          @Override
          public void intervalAdded(ListDataEvent e) {
            update();
          }

          @Override
          public void intervalRemoved(ListDataEvent e) {
            update();
          }

          @Override
          public void contentsChanged(ListDataEvent e) {
            update();
          }

          private void update() {
            boolean visible = maskCombo.getModel().getSize() > 0;
            if (section.isVisible() != visible) {
              section.setVisible(visible);
              revalidate();
            }
          }
        };
    maskCombo.getModel().addListDataListener(contextual);
    section.setVisible(maskCombo.getModel().getSize() > 0);
  }

  @Override
  public Component getToolComponent() {
    return getToolComponentFromJScrollPane(rootPane);
  }

  public static JPanel getResetPanel(Border hspace) {
    JComboBox<ResetTools> resetComboBox = new JComboBox<>(ResetTools.values());
    JButton resetButton = new JButton(ActionW.RESET.getTitle());
    resetButton.addActionListener(
        e -> {
          ResetTools reset = (ResetTools) resetComboBox.getSelectedItem();
          if (reset != null) {
            EventManager.getInstance().reset(reset);
          }
        });
    EventManager.getInstance()
        .getAction(ActionW.RESET)
        .ifPresent(
            a -> {
              a.registerActionState(resetComboBox);
              a.registerActionState(resetButton);
            });

    JPanel panel = GuiUtils.getFlowLayoutPanel(resetComboBox, resetButton);
    panel.setBorder(
        BorderFactory.createCompoundBorder(
            hspace, GuiUtils.getTitledBorder(ActionW.RESET.getTitle())));
    return panel;
  }

  public static JPanel getSlicePanel(Border hspace) {
    JPanel framePanel = GuiUtils.getVerticalBoxLayoutPanel();
    framePanel.setBorder(
        BorderFactory.createCompoundBorder(
            hspace, GuiUtils.getTitledBorder(Messages.common("cine"))));

    EventManager.getInstance()
        .getAction(ActionW.SCROLL_SERIES)
        .ifPresent(
            sliderItem -> {
              JSliderW frameSlider = sliderItem.createSlider(2, true);
              framePanel.add(frameSlider);

              JLabel speedLabel = new JLabel();
              speedLabel.setText(Messages.getString("speed.fps") + StringUtil.COLON);

              JSpinner speedSpinner = new JSpinner(sliderItem.getSpeedModel());
              NumberFormatter formatter = new NumberFormatter(new DecimalFormat("##"));
              formatter.setValueClass(Double.class);
              GuiUtils.formatCheckAction(speedSpinner, formatter);
              GuiUtils.setSpinnerWidth(speedSpinner, 2);

              JButton startButton = new JButton();
              startButton.setActionCommand(ActionW.CINESTART.cmd());
              startButton.setToolTipText(Messages.getString("ImageTool.cine_start"));
              startButton.setIcon(ResourceUtil.getIcon(ActionIcon.EXECUTE));
              startButton.addActionListener(EventManager.getInstance());
              sliderItem.registerActionState(startButton);

              JButton stopButton = new JButton();
              stopButton.setActionCommand(ActionW.CINESTOP.cmd());
              stopButton.setToolTipText(Messages.getString("ImageTool.cine_stop"));
              stopButton.setIcon(ResourceUtil.getIcon(ActionIcon.SUSPEND));
              stopButton.addActionListener(EventManager.getInstance());
              sliderItem.registerActionState(stopButton);

              JPanel cinePanel =
                  GuiUtils.getFlowLayoutPanel(
                      3, 3, speedLabel, speedSpinner, startButton, stopButton);
              EventManager.getInstance()
                  .getAction(ActionW.CINE_SWEEP)
                  .ifPresent(
                      toggleButton -> {
                        FlatSVGIcon icon = ResourceUtil.getIcon(ActionIcon.LOOP);
                        JToggleButton checkBox = toggleButton.createJToggleButton(icon);
                        checkBox.setPreferredSize(GuiUtils.getBigIconButtonSize(checkBox));
                        checkBox.setToolTipText(toggleButton.getActionW().getTitle());
                        cinePanel.add(checkBox);
                      });

              framePanel.add(cinePanel);
            });
    return framePanel;
  }

  /**
   * NM images get Lower and Upper Window Level sliders instead of Window and Level (IHE RAD TF-2
   * 4.16.4.2.2.3.3); the panel follows the modality of the selected image.
   */
  private static void addWindowBounds(
      ImageViewerEventManager<DicomImageElement> manager,
      JPanel parent,
      JPanel wlSliders,
      int gap) {
    Optional<SliderChangeListener> window = manager.getAction(ActionW.WINDOW);
    Optional<SliderChangeListener> level = manager.getAction(ActionW.LEVEL);
    if (window.isEmpty() || level.isEmpty()) {
      return;
    }
    WindowBoundsPanel bounds = new WindowBoundsPanel(window.get(), level.get(), gap);
    parent.add(bounds);
    Runnable switchMode =
        () -> {
          boolean nm = isNuclearMedicine(manager.getSelectedViewPane());
          if (bounds.isVisible() != nm || wlSliders.isVisible() == nm) {
            bounds.setVisible(nm);
            wlSliders.setVisible(!nm);
            parent.revalidate();
          }
        };
    // The Window and Level models change whenever another image is selected
    window.get().getSliderModel().addChangeListener(_ -> switchMode.run());
    level.get().getSliderModel().addChangeListener(_ -> switchMode.run());
    switchMode.run();
  }

  static boolean isNuclearMedicine(ViewCanvas<DicomImageElement> view) {
    DicomImageElement image = view == null ? null : view.getImage();
    return image != null && "NM".equals(TagD.getTagValue(image, Tag.Modality, String.class));
  }

  public static JPanel getWindowLevelPanel(
      ImageViewerEventManager<DicomImageElement> manager, Border hspace, boolean all) {
    int gabY = 7;
    JPanel winLevelPanel = GuiUtils.getVerticalBoxLayoutPanel();
    winLevelPanel.setBorder(
        BorderFactory.createCompoundBorder(
            hspace, GuiUtils.getTitledBorder(Messages.getString("ImageTool.wl"))));
    JPanel wlSliders = GuiUtils.getVerticalBoxLayoutPanel();
    manager
        .getAction(ActionW.WINDOW)
        .ifPresent(
            sliderItem -> {
              JSliderW windowSlider = sliderItem.createSlider(0, true);
              GuiUtils.setPreferredWidth(windowSlider, 100);
              wlSliders.add(windowSlider);
              wlSliders.add(GuiUtils.boxVerticalStrut(gabY));
            });

    manager
        .getAction(ActionW.LEVEL)
        .ifPresent(
            sliderItem -> {
              JSliderW levelSlider = sliderItem.createSlider(0, true);
              GuiUtils.setPreferredWidth(levelSlider, 100);
              wlSliders.add(levelSlider);
              wlSliders.add(GuiUtils.boxVerticalStrut(gabY));
            });
    winLevelPanel.add(wlSliders);
    addWindowBounds(manager, winLevelPanel, wlSliders, gabY);

    manager
        .getAction(ActionW.PRESET)
        .ifPresent(
            comboItem -> {
              JLabel presetsLabel = new JLabel(ActionW.PRESET.getTitle() + StringUtil.COLON);
              JComboBox<?> presetComboBox = comboItem.createCombo(160);
              presetComboBox.setMaximumRowCount(10);
              winLevelPanel.add(
                  GuiUtils.getHorizontalBoxLayoutPanel(
                      5,
                      presetsLabel,
                      presetComboBox,
                      WindowPresetActions.saveCurrentButton(manager)));
              winLevelPanel.add(GuiUtils.boxVerticalStrut(gabY));
            });

    manager
        .getAction(ActionW.LUT_SHAPE)
        .ifPresent(
            comboItem -> {
              JLabel label = new JLabel(ActionW.LUT_SHAPE.getTitle() + StringUtil.COLON);
              JComboBox<?> combo = comboItem.createCombo(140);
              combo.setMaximumRowCount(10);
              winLevelPanel.add(GuiUtils.getHorizontalBoxLayoutPanel(5, label, combo));
              winLevelPanel.add(GuiUtils.boxVerticalStrut(gabY));
            });

    if (all) {
      manager
          .getAction(ActionW.LUT)
          .ifPresent(
              comboItem -> {
                JLabel lutLabel = new JLabel(ActionW.LUT.getTitle() + StringUtil.COLON);
                JComboBox<?> lutComboBox = comboItem.createCombo(140);
                JPanel lutPanel = GuiUtils.getHorizontalBoxLayoutPanel(5, lutLabel, lutComboBox);
                manager
                    .getAction(ActionW.INVERT_LUT)
                    .ifPresent(
                        toggleButton -> {
                          FlatSVGIcon icon = ResourceUtil.getIcon(ActionIcon.INVERSE_LUT);
                          JToggleButton checkBox = toggleButton.createJToggleButton(icon);
                          checkBox.setPreferredSize(GuiUtils.getBigIconButtonSize(checkBox));
                          checkBox.setToolTipText(toggleButton.getActionW().getTitle());
                          lutPanel.add(checkBox);
                        });
                winLevelPanel.add(lutPanel);
                winLevelPanel.add(GuiUtils.boxVerticalStrut(gabY));
              });

      manager
          .getAction(ActionW.FILTER)
          .ifPresent(
              comboItem -> {
                JLabel label = new JLabel(Messages.common("filter") + StringUtil.COLON);
                JComboBox<?> combo = comboItem.createCombo(160);
                winLevelPanel.add(GuiUtils.getHorizontalBoxLayoutPanel(5, label, combo));
                winLevelPanel.add(GuiUtils.boxVerticalStrut(5));
              });
    }
    return winLevelPanel;
  }

  public static JPanel getTransformPanel(Border hspace) {
    JPanel transform = GuiUtils.getVerticalBoxLayoutPanel();
    transform.setBorder(
        BorderFactory.createCompoundBorder(
            hspace, GuiUtils.getTitledBorder(Messages.getString("ImageTool.transform"))));

    EventManager.getInstance()
        .getAction(ActionW.ZOOM)
        .ifPresent(
            sliderItem -> {
              JSliderW zoomSlider = sliderItem.createSlider(0, true);
              GuiUtils.setPreferredWidth(zoomSlider, 100);
              transform.add(zoomSlider);
            });

    EventManager.getInstance()
        .getAction(ActionW.ROTATION)
        .ifPresent(
            sliderItem -> {
              JSliderW rotationSlider = sliderItem.createSlider(5, true);
              GuiUtils.setPreferredWidth(rotationSlider, 100);
              transform.add(rotationSlider);
            });

    EventManager.getInstance()
        .getAction(ActionW.FLIP)
        .ifPresent(
            toggleButton -> {
              JPanel pane = GuiUtils.getFlowLayoutPanel();
              pane.add(toggleButton.createCheckBox(Messages.getString("View2dContainer.flip_h")));
              transform.add(pane);
            });
    return transform;
  }

  /**
   * Mask series and weight of the mask, then a bottom row with the preview toggle on the left and
   * the button that builds the series on the right.
   */
  public static JPanel getSubtractionPanel(
      EventManager manager, JComboBox<?> maskCombo, Border hspace) {
    int gabY = 7;
    JPanel panel = GuiUtils.getVerticalBoxLayoutPanel();
    panel.setBorder(
        BorderFactory.createCompoundBorder(
            hspace, GuiUtils.getTitledBorder(Messages.getString("SubtractionTool.title"))));

    JLabel label = new JLabel(Messages.getString("SubtractionTool.mask") + StringUtil.COLON);
    panel.add(GuiUtils.getHorizontalBoxLayoutPanel(5, label, maskCombo));
    panel.add(GuiUtils.boxVerticalStrut(gabY));

    // The mask of an X-ray run: the frame shown as mask, and the sub-pixel shift of the mask
    JPanel run = GuiUtils.getVerticalBoxLayoutPanel();
    JButton current = new JButton(Messages.getString("SubtractionTool.current_frame"));
    current.setToolTipText(Messages.getString("SubtractionTool.current_frame.tip"));
    current.addActionListener(e -> SubtractionController.useCurrentFrameAsMask());
    run.add(GuiUtils.getFlowLayoutPanel(current));
    for (SliderChangeListenerValue shift :
        List.of(SubtractionAction.SHIFT_ROWS, SubtractionAction.SHIFT_COLUMNS)) {
      manager
          .getAction(shift)
          .ifPresent(
              a -> {
                JSliderW slider = a.createSlider(0, true);
                GuiUtils.setPreferredWidth(slider, 100);
                run.add(slider);
                run.add(GuiUtils.boxVerticalStrut(gabY));
              });
    }
    run.setVisible(false);
    maskCombo
        .getModel()
        .addListDataListener(
            new ListDataListener() {
              @Override
              public void intervalAdded(ListDataEvent e) {
                update();
              }

              @Override
              public void intervalRemoved(ListDataEvent e) {
                update();
              }

              @Override
              public void contentsChanged(ListDataEvent e) {
                update();
              }

              private void update() {
                boolean shown =
                    maskCombo.getModel().getSize() > 0
                        && maskCombo.getModel().getElementAt(0) instanceof FrameMask;
                if (run.isVisible() != shown) {
                  run.setVisible(shown);
                  panel.revalidate();
                }
              }
            });
    panel.add(run);

    manager
        .getAction(SubtractionAction.FACTOR)
        .ifPresent(
            a -> {
              JSliderW slider = a.createSlider(0, true);
              GuiUtils.setPreferredWidth(slider, 100);
              panel.add(slider);
              panel.add(GuiUtils.boxVerticalStrut(gabY));
            });

    manager
        .getAction(SubtractionAction.MOTION)
        .ifPresent(
            a -> {
              JCheckBox motion = a.createCheckBox(SubtractionAction.MOTION.getTitle());
              motion.setToolTipText(Messages.getString("SubtractionTool.motion.tip"));
              panel.add(GuiUtils.getFlowLayoutPanel(motion));
            });

    JPanel bottom = new JPanel(new BorderLayout());
    bottom.setAlignmentX(Component.LEFT_ALIGNMENT);
    manager
        .getAction(SubtractionAction.PREVIEW)
        .ifPresent(
            a ->
                bottom.add(
                    a.createCheckBox(SubtractionAction.PREVIEW.getTitle()), BorderLayout.WEST));
    JButton build = new JButton(Messages.getString("SubtractionTool.subtract"));
    build.setToolTipText(Messages.getString("SubtractionTool.subtract.tip"));
    build.addActionListener(e -> SubtractionController.subtractSelected());
    manager.getAction(SubtractionAction.MASK).ifPresent(a -> a.registerActionState(build));
    bottom.add(build, BorderLayout.EAST);
    bottom.setMaximumSize(new Dimension(Integer.MAX_VALUE, bottom.getPreferredSize().height));
    panel.add(bottom);
    return panel;
  }

  public static JPanel getFusionPanel(Border hspace) {
    int gabY = 7;
    EventManager manager = EventManager.getInstance();
    JPanel fusionPanel = GuiUtils.getVerticalBoxLayoutPanel();
    fusionPanel.setBorder(
        BorderFactory.createCompoundBorder(
            hspace, GuiUtils.getTitledBorder(Messages.getString("FusionTool.title"))));

    manager
        .getAction(FusionAction.ENABLE)
        .ifPresent(
            a -> {
              fusionPanel.add(
                  GuiUtils.getFlowLayoutPanel(a.createCheckBox(FusionAction.ENABLE.getTitle())));
              fusionPanel.add(GuiUtils.boxVerticalStrut(gabY));
            });

    manager
        .getAction(FusionAction.SERIES)
        .ifPresent(
            a -> {
              JLabel label = new JLabel(Messages.common("series") + StringUtil.COLON);
              fusionPanel.add(GuiUtils.getHorizontalBoxLayoutPanel(5, label, a.createCombo(160)));
              fusionPanel.add(GuiUtils.boxVerticalStrut(gabY));
            });

    manager
        .getAction(FusionAction.LUT)
        .ifPresent(
            a -> {
              JLabel label = new JLabel(Messages.getString("FusionTool.lut") + StringUtil.COLON);
              fusionPanel.add(GuiUtils.getHorizontalBoxLayoutPanel(5, label, a.createCombo(160)));
              fusionPanel.add(GuiUtils.boxVerticalStrut(gabY));
            });

    manager
        .getAction(FusionAction.BASE_OPACITY)
        .ifPresent(
            a -> {
              JSliderW slider = a.createSlider(0, true);
              GuiUtils.setPreferredWidth(slider, 100);
              fusionPanel.add(slider);
              fusionPanel.add(GuiUtils.boxVerticalStrut(gabY));
            });

    manager
        .getAction(FusionAction.OVERLAY_OPACITY)
        .ifPresent(
            a -> {
              JSliderW slider = a.createSlider(0, true);
              GuiUtils.setPreferredWidth(slider, 100);
              fusionPanel.add(slider);
              fusionPanel.add(GuiUtils.boxVerticalStrut(gabY));
            });

    manager
        .getAction(FusionAction.WINDOW_TOP)
        .ifPresent(
            a -> {
              JSliderW slider = a.createSlider(0, true);
              GuiUtils.setPreferredWidth(slider, 100);
              fusionPanel.add(slider);
              fusionPanel.add(GuiUtils.boxVerticalStrut(5));
            });
    return fusionPanel;
  }
}
