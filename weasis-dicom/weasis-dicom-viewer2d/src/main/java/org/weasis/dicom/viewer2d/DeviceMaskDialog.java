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
import java.awt.Shape;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import net.miginfocom.swing.MigLayout;
import org.dcm4che3.data.Tag;
import org.weasis.core.api.media.data.MaskRegion;
import org.weasis.core.api.media.data.MaskingModelRegistry;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.media.data.PixelMask;
import org.weasis.core.api.media.data.PixelMask.DeviceKey;
import org.weasis.core.api.media.data.PixelMask.Reference;
import org.weasis.core.api.media.data.TagCategory;
import org.weasis.core.api.media.data.TagReadable;
import org.weasis.core.util.StringUtil;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.codec.TagD;

/**
 * Asks what a drawn region says about the machine that produced the image, and turns it into a
 * {@link PixelMask} of the masking document: which attributes of the device the entry is keyed on,
 * and what each region carries, which is what decides the profiles that hide it.
 */
final class DeviceMaskDialog {

  /** The device attributes an entry can be keyed on, in the order they are offered. */
  private static final int[] KEY_TAGS = {
    Tag.Modality, Tag.StationName, Tag.Manufacturer, Tag.ManufacturerModelName, Tag.InstitutionName
  };

  private DeviceMaskDialog() {}

  /**
   * @param shapes the regions in image coordinates
   * @return the entry to save, or empty when the dialog was cancelled or nothing can be keyed
   */
  static Optional<PixelMask> ask(
      Component parent,
      DicomImageElement image,
      MediaSeries<DicomImageElement> series,
      List<Shape> shapes) {
    Integer columns = TagD.getTagValue(image, Tag.Columns, Integer.class);
    Integer rows = TagD.getTagValue(image, Tag.Rows, Integer.class);
    if (shapes.isEmpty() || columns == null || rows == null || columns <= 0 || rows <= 0) {
      return Optional.empty();
    }
    Map<Integer, String> values = deviceValues(image, series);
    if (values.isEmpty()) {
      JOptionPane.showMessageDialog(
          parent,
          Messages.getString("DeviceMaskDialog.no.device"),
          Messages.getString("DeviceMaskDialog.title"),
          JOptionPane.WARNING_MESSAGE);
      return Optional.empty();
    }

    JTextField nameField = new JTextField(defaultName(values), 18);
    Map<Integer, JCheckBox> checks = new LinkedHashMap<>();
    List<JComboBox<TagCategory>> categories = new ArrayList<>();
    JPanel panel = buildPanel(values, columns, rows, shapes, nameField, checks, categories);

    if (JOptionPane.showConfirmDialog(
            parent,
            panel,
            Messages.getString("DeviceMaskDialog.title"),
            JOptionPane.OK_CANCEL_OPTION,
            JOptionPane.PLAIN_MESSAGE)
        != JOptionPane.OK_OPTION) {
      return Optional.empty();
    }

    String name =
        StringUtil.hasText(nameField.getText()) ? nameField.getText().trim() : defaultName(values);
    List<MaskRegion> regions = new ArrayList<>();
    for (int i = 0; i < shapes.size(); i++) {
      TagCategory category = (TagCategory) categories.get(i).getSelectedItem();
      regions.add(MaskRegion.of(shapes.get(i), columns, rows, category));
    }
    return Optional.of(
        new PixelMask(
            uniqueId(name),
            name,
            key(values, checks),
            new Reference(columns, rows),
            regions,
            List.of(),
            true));
  }

  private static JPanel buildPanel(
      Map<Integer, String> values,
      int columns,
      int rows,
      List<Shape> shapes,
      JTextField nameField,
      Map<Integer, JCheckBox> checks,
      List<JComboBox<TagCategory>> categories) {
    JPanel panel = new JPanel(new MigLayout("ins 5lp, wrap 2", "[right][grow,fill]")); // NON-NLS
    panel.add(new JLabel(Messages.getString("DeviceMaskDialog.name") + StringUtil.COLON));
    panel.add(nameField);

    panel.add(new JLabel(Messages.getString("DeviceMaskDialog.match") + StringUtil.COLON), "top");
    JPanel keys = new JPanel(new MigLayout("ins 0, wrap 1")); // NON-NLS
    values.forEach(
        (tag, value) -> {
          JCheckBox check = new JCheckBox(TagD.get(tag).getDisplayedName() + " = " + value);
          // The station identifies the machine; the others widen or narrow the rule
          check.setSelected(tag == Tag.StationName || tag == Tag.Modality);
          checks.put(tag, check);
          keys.add(check);
        });
    panel.add(keys);

    panel.add(new JLabel(Messages.getString("DeviceMaskDialog.reference") + StringUtil.COLON));
    panel.add(new JLabel(columns + " x " + rows)); // NON-NLS

    for (int i = 0; i < shapes.size(); i++) {
      JComboBox<TagCategory> combo = categoryCombo();
      categories.add(combo);
      panel.add(
          new JLabel(
              Messages.getString("DeviceMaskDialog.region").formatted(i + 1) + StringUtil.COLON));
      panel.add(combo);
    }
    JLabel note = new JLabel(Messages.getString("DeviceMaskDialog.note"));
    panel.add(note, "span 2, gaptop 5lp"); // NON-NLS
    return panel;
  }

  private static JComboBox<TagCategory> categoryCombo() {
    JComboBox<TagCategory> combo =
        new JComboBox<>(
            new TagCategory[] {
              TagCategory.DIRECT_ID,
              TagCategory.BIRTH_DATE,
              TagCategory.DATE,
              TagCategory.INSTITUTION,
              TagCategory.DEVICE,
              TagCategory.DESCRIPTOR,
              TagCategory.FREE_TEXT
            });
    combo.setRenderer(
        new javax.swing.DefaultListCellRenderer() {
          @Override
          public Component getListCellRendererComponent(
              javax.swing.JList<?> list, Object value, int index, boolean selected, boolean focus) {
            super.getListCellRendererComponent(list, value, index, selected, focus);
            if (value instanceof TagCategory category) {
              setText(category.displayName());
            }
            return this;
          }
        });
    combo.setToolTipText(Messages.getString("DeviceMaskDialog.region.tip"));
    return combo;
  }

  /** The device attributes the series carries, in the offered order. */
  private static Map<Integer, String> deviceValues(TagReadable image, TagReadable series) {
    Map<Integer, String> values = new LinkedHashMap<>();
    for (int tag : KEY_TAGS) {
      String value = TagD.getTagValue(image, tag, String.class);
      if (!StringUtil.hasText(value)) {
        value = TagD.getTagValue(series, tag, String.class);
      }
      if (StringUtil.hasText(value)) {
        values.put(tag, value.trim());
      }
    }
    return values;
  }

  private static DeviceKey key(Map<Integer, String> values, Map<Integer, JCheckBox> checks) {
    return new DeviceKey(
        selected(values, checks, Tag.Modality),
        selected(values, checks, Tag.StationName),
        selected(values, checks, Tag.Manufacturer),
        selected(values, checks, Tag.ManufacturerModelName),
        selected(values, checks, Tag.InstitutionName));
  }

  private static String selected(
      Map<Integer, String> values, Map<Integer, JCheckBox> checks, int tag) {
    JCheckBox check = checks.get(tag);
    return check != null && check.isSelected() ? values.get(tag) : null;
  }

  private static String defaultName(Map<Integer, String> values) {
    String station = values.get(Tag.StationName);
    String model = values.get(Tag.ManufacturerModelName);
    if (StringUtil.hasText(station)) {
      return StringUtil.hasText(model) ? model + " (" + station + ")" : station;
    }
    return StringUtil.hasText(model) ? model : Messages.getString("DeviceMaskDialog.title");
  }

  /** An id that no entry of the library holds yet, derived from the name. */
  static String uniqueId(String name) {
    MaskingModelRegistry registry = MaskingModelRegistry.getInstance();
    String base = "user-" + slug(name); // NON-NLS
    String id = base;
    int i = 2;
    while (registry.pixelMask(id).isPresent()) {
      id = base + "-" + i++;
    }
    return id;
  }

  private static String slug(String text) {
    String slug = text.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-");
    slug = slug.replaceAll("(?:^-+)|(?:-+$)", "");
    return slug.isEmpty() ? "mask" : slug; // NON-NLS
  }
}
