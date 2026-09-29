/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.acquire.explorer.gui.central.meta.panel;

import java.awt.Component;
import java.awt.Font;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.swing.ButtonGroup;
import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JRadioButton;
import net.miginfocom.swing.MigLayout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.acquire.explorer.Messages;
import org.weasis.core.api.gui.util.CheckBoxModel;
import org.weasis.core.api.gui.util.DropDownButton;
import org.weasis.core.api.gui.util.GroupCheckBoxMenu;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.util.FontItem;
import org.weasis.core.ui.util.SearchableComboBox;
import org.weasis.core.util.StringUtil;
import org.weasis.dicom.ref.AnatomicBuilder;
import org.weasis.dicom.ref.AnatomicBuilder.Category;
import org.weasis.dicom.ref.AnatomicBuilder.CategoryBuilder;
import org.weasis.dicom.ref.AnatomicItem;
import org.weasis.dicom.ref.AnatomicModifier;
import org.weasis.dicom.ref.AnatomicRegion;
import org.weasis.dicom.ref.RegionGroup;

public class AnatomicRegionView extends JPanel {

  private static final Logger LOGGER = LoggerFactory.getLogger(AnatomicRegionView.class);

  private JComboBox<CategoryBuilder> comboBox1;
  private JComboBox<AnatomicItem> comboBox2;
  // Filter of the regions by area; null = every area
  private final JComboBox<RegionGroup> areaCombo = new JComboBox<>();

  private final ButtonGroup ratioGroup = new ButtonGroup();
  private final JRadioButton radioButtonSeries =
      new JRadioButton(org.weasis.core.Messages.getString("CalibrationView.series"));
  private final JRadioButton radioButtonImage =
      new JRadioButton(org.weasis.core.Messages.getString("CalibrationView.current"));
  private final GroupCheckBoxMenu modifierGroup = new GroupCheckBoxMenu();
  private final DropDownButton modifiersDropdown =
      new DropDownButton(
          "search_mod", // NON-NLS
          Messages.getString("modifiers"),
          GuiUtils.getDownArrowIcon(),
          modifierGroup) {
        @Override
        protected JPopupMenu getPopupMenu() {
          JPopupMenu menu =
              (getMenuModel() == null) ? new JPopupMenu() : getMenuModel().createJPopupMenu();
          menu.setInvoker(this);
          return menu;
        }
      };

  public AnatomicRegionView(AnatomicRegion region, boolean selectSeries) {
    try {
      jbInit();
      radioButtonSeries.setSelected(selectSeries);
      if (!selectSeries) {
        radioButtonImage.setSelected(true);
      }
      initialize(region);
    } catch (Exception e) {
      LOGGER.error(e.getMessage(), e);
    }
  }

  void jbInit() {
    setLayout(new MigLayout("wrap 2, insets 0", "[right][grow]", "[]10[]10[]10[]20[]")); // NON-NLS
    CategoryBuilder[] sortedCategories =
        AnatomicBuilder.getCategoryMap().keySet().stream()
            .sorted(Comparator.comparing(CategoryBuilder::toString))
            .toArray(CategoryBuilder[]::new);
    comboBox1 = new JComboBox<>(sortedCategories);
    comboBox1.setSelectedItem(Category.SURFACE);
    comboBox1.addActionListener(_ -> updateComboBox2());

    areaCombo.addItem(null);
    Arrays.stream(RegionGroup.values()).forEach(areaCombo::addItem);
    areaCombo.setRenderer(
        new DefaultListCellRenderer() {
          @Override
          public Component getListCellRendererComponent(
              JList<?> list, Object value, int index, boolean selected, boolean focus) {
            super.getListCellRendererComponent(list, value, index, selected, focus);
            setText(
                value instanceof RegionGroup g ? g.getLabel() : Messages.getString("all.areas"));
            return this;
          }
        });
    areaCombo.setToolTipText(Messages.getString("area.tip"));
    areaCombo.addActionListener(_ -> updateComboBox2());

    // Create the second combo box
    comboBox2 = new SearchableComboBox<>();

    Font font = comboBox2.getFont();
    if (font == null) {
      font = FontItem.DEFAULT.getFont();
    }
    int maxWidth = 0;
    Collection<List<AnatomicItem>> categories = AnatomicBuilder.getCategoryMap().values();
    for (List<AnatomicItem> list : categories) {
      for (AnatomicItem item : list) {
        int width = comboBox2.getFontMetrics(font).stringWidth(item.getCodeMeaning());
        if (width > maxWidth) {
          maxWidth = width;
        }
      }
    }
    Insets insets = comboBox2.getInsets();
    updateComboBox2();
    GuiUtils.setPreferredWidth(comboBox2, maxWidth + insets.left + insets.right);

    JLabel lblApplyTo =
        new JLabel(org.weasis.core.Messages.getString("CalibrationView.apply") + StringUtil.COLON);
    ratioGroup.add(radioButtonSeries);
    ratioGroup.add(radioButtonImage);

    add(new JLabel("Category" + StringUtil.COLON), "right"); // NON-NLS
    add(comboBox1, "growx"); // NON-NLS

    add(new JLabel(Messages.getString("area") + StringUtil.COLON), "right"); // NON-NLS
    add(areaCombo, "growx"); // NON-NLS

    add(new JLabel("Region" + StringUtil.COLON), "right"); // NON-NLS
    add(comboBox2, "growx 500"); // NON-NLS

    List<Object> list = Stream.of(AnatomicModifier.values()).collect(Collectors.toList());
    modifierGroup.setModel(list, false, false);
    modifiersDropdown.setToolTipText(Messages.getString("select.modifiers"));
    add(modifiersDropdown, "cell 1 3, span"); // NON-NLS

    add(lblApplyTo, "span, split 3, right, gaptop 20"); // NON-NLS
    add(radioButtonSeries);
    add(radioButtonImage);
  }

  public AnatomicItem getSelectedAnatomicItem() {
    Object item = comboBox2.getSelectedItem();
    return item instanceof AnatomicItem val ? val : null;
  }

  public CategoryBuilder getSelectedCategory() {
    return (CategoryBuilder) comboBox1.getSelectedItem();
  }

  public Set<AnatomicModifier> getModifiers() {
    return modifierGroup.getModelList().stream()
        .filter(CheckBoxModel::isSelected)
        .map(c -> (AnatomicModifier) c.getObject())
        .collect(Collectors.toCollection(HashSet::new));
  }

  void updateComboBox2() {
    RegionGroup area = (RegionGroup) areaCombo.getSelectedItem();
    Object selected = comboBox2.getSelectedItem();
    List<AnatomicItem> list =
        AnatomicBuilder.getCategoryItems(getSelectedCategory()).stream()
            .filter(item -> area == null || new AnatomicRegion(item).isIn(area))
            .toList();
    comboBox2.setModel(new DefaultComboBoxModel<>(list.toArray(new AnatomicItem[0])));
    if (selected != null && list.contains(selected)) {
      comboBox2.setSelectedItem(selected);
    }
  }

  /** Selects the area filter, null for every area; for the tests. */
  void selectArea(RegionGroup area) {
    areaCombo.setSelectedItem(area);
  }

  /** The regions offered by the Region combo; for the tests. */
  List<AnatomicItem> offeredRegions() {
    List<AnatomicItem> items = new ArrayList<>();
    for (int i = 0; i < comboBox2.getItemCount(); i++) {
      items.add(comboBox2.getItemAt(i));
    }
    return items;
  }

  public boolean isApplyingToSeries() {
    return radioButtonSeries.isSelected();
  }

  private void initialize(AnatomicRegion region) {
    if (region != null) {
      CategoryBuilder category = region.getCategory();
      if (category != null) {
        comboBox1.setSelectedItem(category);
      }
      comboBox2.setSelectedItem(region.getRegion());
      Set<AnatomicModifier> modifiers = region.getModifiers();
      modifierGroup.getModelList().forEach(c -> c.setSelected(modifiers.contains(c.getObject())));
    }
  }
}
