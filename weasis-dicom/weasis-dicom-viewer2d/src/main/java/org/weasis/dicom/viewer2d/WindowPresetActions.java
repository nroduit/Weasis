/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.viewer2d;

import java.awt.Component;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import org.dcm4che3.data.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.gui.util.ComboItemListener;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.gui.util.SliderChangeListener;
import org.weasis.core.api.util.ResourceUtil;
import org.weasis.core.api.util.ResourceUtil.ActionIcon;
import org.weasis.core.ui.editor.image.ImageViewerEventManager;
import org.weasis.core.ui.editor.image.ViewCanvas;
import org.weasis.core.util.StringUtil;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.codec.TagD;
import org.weasis.dicom.codec.display.WindowPreset;
import org.weasis.dicom.codec.display.WindowPresetRegistry;
import org.weasis.opencv.op.lut.LutShape;

/** Saves the window/level of the selected view as a user preset. */
public final class WindowPresetActions {
  private static final Logger LOGGER = LoggerFactory.getLogger(WindowPresetActions.class);

  /** Offered shortcut keys, the digits left by auto level and the two DICOM presets. */
  private static final String[] KEYS = {"", "3", "4", "5", "6", "7", "8", "9"}; // NON-NLS

  private WindowPresetActions() {}

  /** Menu entry for the preset menu and the toolbar drop-down. */
  public static JMenuItem saveCurrentItem(
      ImageViewerEventManager<DicomImageElement> manager, Component parent) {
    JMenuItem item =
        new JMenuItem(Messages.getString("WindowPresetActions.save") + StringUtil.Suffix.THREE_PTS);
    item.addActionListener(e -> saveCurrent(manager, parent));
    return item;
  }

  /** Small button for the <i>Windowing and Rendering</i> panel. */
  public static JButton saveCurrentButton(ImageViewerEventManager<DicomImageElement> manager) {
    JButton button = new JButton(ResourceUtil.getIcon(ActionIcon.PLUS));
    button.setToolTipText(Messages.getString("WindowPresetActions.save"));
    button.setPreferredSize(GuiUtils.getBigIconButtonSize(button));
    button.addActionListener(e -> saveCurrent(manager, button));
    return button;
  }

  static void saveCurrent(ImageViewerEventManager<DicomImageElement> manager, Component parent) {
    Optional<SliderChangeListener> window = manager.getAction(ActionW.WINDOW);
    Optional<SliderChangeListener> level = manager.getAction(ActionW.LEVEL);
    if (window.isEmpty() || level.isEmpty()) {
      return;
    }
    ViewCanvas<DicomImageElement> view = manager.getSelectedViewPane();
    DicomImageElement image = view == null ? null : view.getImage();
    String modality =
        image == null ? null : TagD.getTagValue(image, Tag.Modality, String.class); // NON-NLS
    LutShape shape =
        manager
            .getAction(ActionW.LUT_SHAPE)
            .map(ComboItemListener::getSelectedItem)
            .filter(LutShape.class::isInstance)
            .map(LutShape.class::cast)
            .orElse(LutShape.LINEAR);

    JTextField nameField = new JTextField(20);
    JTextField modalityField = new JTextField(modality == null ? "" : modality, 8);
    JComboBox<String> keyCombo = new JComboBox<>(KEYS);
    keyCombo.setToolTipText(Messages.getString("WindowPresetPrefView.key.tip"));
    JPanel panel = GuiUtils.getVerticalBoxLayoutPanel();
    panel.add(
        GuiUtils.getHorizontalBoxLayoutPanel(
            5,
            new JLabel(Messages.getString("WindowPresetActions.name") + StringUtil.COLON),
            nameField));
    panel.add(GuiUtils.boxVerticalStrut(5));
    panel.add(
        GuiUtils.getHorizontalBoxLayoutPanel(
            5,
            new JLabel(Messages.getString("WindowPresetActions.modality") + StringUtil.COLON),
            modalityField));
    panel.add(GuiUtils.boxVerticalStrut(5));
    panel.add(
        GuiUtils.getHorizontalBoxLayoutPanel(
            5,
            new JLabel(Messages.getString("WindowPresetActions.key") + StringUtil.COLON),
            keyCombo));

    int result =
        JOptionPane.showConfirmDialog(
            parent,
            panel,
            Messages.getString("WindowPresetActions.save"),
            JOptionPane.OK_CANCEL_OPTION,
            JOptionPane.PLAIN_MESSAGE);
    if (result != JOptionPane.OK_OPTION) {
      return;
    }

    String name = nameField.getText();
    if (!StringUtil.hasText(name)) {
      return;
    }
    String selectedKey = (String) keyCombo.getSelectedItem();
    WindowPreset preset =
        buildPreset(
            name,
            modalityField.getText(),
            window.get().getRealValue(),
            level.get().getRealValue(),
            shape,
            StringUtil.hasText(selectedKey) ? selectedKey.charAt(0) : null);

    WindowPresetRegistry registry = WindowPresetRegistry.getInstance();
    if (registry.find(preset.id()).isPresent()
        && JOptionPane.showConfirmDialog(
                parent,
                Messages.getString("WindowPresetActions.exists"),
                Messages.getString("WindowPresetActions.save"),
                JOptionPane.YES_NO_OPTION)
            != JOptionPane.YES_OPTION) {
      return;
    }
    try {
      registry.saveUser(preset);
    } catch (IOException e) {
      LOGGER.error("Cannot save the window preset {}", preset.id(), e);
      JOptionPane.showMessageDialog(
          parent,
          Messages.getString("WindowPresetActions.error"),
          Messages.getString("WindowPresetActions.save"),
          JOptionPane.ERROR_MESSAGE);
    }
  }

  /**
   * The user preset of the given values, with the id {@code user.<modality>.<name>}; an empty
   * modality gives a preset offered for every modality.
   */
  static WindowPreset buildPreset(
      String name, String modality, double window, double level, LutShape shape, Character key) {
    Set<String> codes = WindowPreset.parseCodes(modality);
    String prefix = codes.isEmpty() ? "" : slug(String.join("-", new TreeSet<>(codes))) + ".";
    return new WindowPreset(
        WindowPreset.USER_PREFIX + prefix + slug(name),
        name.trim(),
        codes,
        null,
        List.of(),
        false,
        window,
        level,
        null,
        shape,
        key,
        null,
        false);
  }

  static String slug(String text) {
    String slug = text.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-");
    slug = slug.replaceAll("(?:^-+)|(?:-+$)", "");
    return slug.isEmpty() ? "preset" : slug; // NON-NLS
  }
}
