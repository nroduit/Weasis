/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.pref;

import java.awt.GridLayout;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;
import javax.swing.BoxLayout;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import org.weasis.core.Messages;
import org.weasis.core.api.gui.util.AbstractItemDialogPage;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.util.FontItem;
import org.weasis.core.ui.editor.image.ImageViewerPlugin;
import org.weasis.core.ui.editor.image.ViewCanvas;
import org.weasis.core.ui.editor.image.ViewerPlugin;
import org.weasis.core.ui.editor.image.dockable.MeasureTool;
import org.weasis.core.ui.model.GraphicModel;
import org.weasis.core.ui.model.graphic.Graphic;
import org.weasis.core.ui.model.graphic.GraphicRegistry;
import org.weasis.core.ui.model.graphic.ToolCategory;
import org.weasis.core.ui.model.graphic.imp.area.SelectGraphic;
import org.weasis.core.ui.model.utils.ImageStatistics;
import org.weasis.core.ui.model.utils.MeasureFormat;
import org.weasis.core.ui.model.utils.bean.Measurement;
import org.weasis.core.util.StringUtil;

public class LabelsPrefView extends AbstractItemDialogPage {
  private final JPanel panelList = new JPanel();
  private final JComboBox<Graphic> comboBoxTool;
  private final JComboBox<FontItem> fontItemJComboBox;
  private final JComboBox<Object> decimalsCombo = new JComboBox<>();
  private final Map<JCheckBox, Measurement> map;

  public LabelsPrefView() {
    super(MeasureTool.LABEL_PREF_NAME, 710);
    this.map = HashMap.newHashMap(ImageStatistics.ALL_MEASUREMENTS.length);

    this.comboBoxTool = new JComboBox<>(measurementTools().toArray(Graphic[]::new));
    this.fontItemJComboBox = new JComboBox<>(FontItem.values());

    jbInit();
    initialize();
  }

  private void jbInit() {
    JLabel jLabelSize = new JLabel(Messages.getString("LabelPrefView.size") + StringUtil.COLON);
    JPanel panelFont =
        GuiUtils.getFlowLayoutPanel(
            ITEM_SEPARATOR_SMALL, ITEM_SEPARATOR_LARGE, jLabelSize, fontItemJComboBox);
    panelFont.setBorder(GuiUtils.getTitledBorder(Messages.getString("LabelPrefView.font")));
    add(panelFont);

    JLabel decimalsLabel =
        new JLabel(Messages.getString("LabelPrefView.decimals") + StringUtil.COLON);
    decimalsCombo.addItem(Messages.getString("LabelPrefView.decimals_auto"));
    for (int i = 0; i <= MeasureFormat.MAX_DECIMALS; i++) {
      decimalsCombo.addItem(i);
    }
    selectDecimals(MeasureTool.viewSetting.getDecimals());
    add(
        GuiUtils.getFlowLayoutPanel(
            ITEM_SEPARATOR_SMALL, ITEM_SEPARATOR_LARGE, decimalsLabel, decimalsCombo));

    JPanel panelShape = new JPanel();
    panelShape.setLayout(new BoxLayout(panelShape, BoxLayout.Y_AXIS));
    panelShape.setBorder(GuiUtils.getTitledBorder(Messages.getString("LabelsPrefView.geometric1")));
    JLabel shapeLabel =
        new JLabel(Messages.getString("LabelsPrefView.geometricshape") + StringUtil.COLON);
    JPanel panelCombo =
        GuiUtils.getFlowLayoutPanel(ITEM_SEPARATOR_SMALL, ITEM_SEPARATOR, shapeLabel, comboBoxTool);
    panelShape.add(panelCombo);
    add(GuiUtils.boxVerticalStrut(BLOCK_SEPARATOR));

    panelList.setLayout(new GridLayout(0, 2));
    panelShape.add(panelList);
    ItemListener toolsListener =
        e -> {
          if (e.getStateChange() == ItemEvent.SELECTED) {
            selectTool((Graphic) e.getItem());
          }
        };
    comboBoxTool.setMaximumRowCount(12);
    comboBoxTool.addItemListener(toolsListener);

    add(panelShape);
    add(GuiUtils.boxVerticalStrut(BLOCK_SEPARATOR));

    JPanel panel = new JPanel();
    panel.setBorder(GuiUtils.getTitledBorder(Messages.getString("MeasureTool.pix_stats")));
    panel.setLayout(new GridLayout(0, 2));

    for (Measurement m : ImageStatistics.ALL_MEASUREMENTS) {
      JCheckBox box = new JCheckBox(m.getName(), m.getGraphicLabel());
      panel.add(box);
      map.put(box, m);
      box.addActionListener(
          e -> {
            Object source = e.getSource();
            if (source instanceof JCheckBox checkBox) {
              Measurement measure = map.get(checkBox);
              if (measure != null) {
                measure.setGraphicLabel(((JCheckBox) source).isSelected());
              }
            }
          });
    }
    add(panel);

    add(GuiUtils.boxYLastElement(LAST_FILLER_HEIGHT));

    getProperties().setProperty(PreferenceDialog.KEY_SHOW_APPLY, Boolean.TRUE.toString());
    getProperties().setProperty(PreferenceDialog.KEY_SHOW_RESTORE, Boolean.TRUE.toString());
    getProperties().setProperty(PreferenceDialog.KEY_HELP, "draw-measure/#preferences"); // NON-NLS
  }

  private static List<Graphic> measurementTools() {
    GraphicRegistry registry = GraphicRegistry.getInstance();
    return Stream.concat(
            registry.prototypes(ToolCategory.MEASURE).stream(),
            registry.prototypes(ToolCategory.ADVANCED).stream())
        .filter(g -> !(g instanceof SelectGraphic) && !g.getMeasurementList().isEmpty())
        .toList();
  }

  private void selectTool(Graphic graph) {
    if (graph != null) {
      panelList.removeAll();
      List<Measurement> list = graph.getMeasurementList();
      if (list != null) {
        for (final Measurement m : list) {
          JCheckBox box = new JCheckBox(m.getName(), m.getGraphicLabel());
          box.addActionListener(
              e -> {
                Object source = e.getSource();
                if (source instanceof JCheckBox checkBox) {
                  m.setGraphicLabel(checkBox.isSelected());
                }
              });
          panelList.add(box);
        }
      }
      panelList.revalidate();
      panelList.repaint();
    }
  }

  private void initialize() {
    FontItem item = MeasureTool.viewSetting.getFontItem();
    fontItemJComboBox.setSelectedItem(item == null ? ViewSetting.DEFAULT_FONT : item);

    selectTool((Graphic) comboBoxTool.getSelectedItem());
  }

  private void selectDecimals(int decimals) {
    decimalsCombo.setSelectedIndex(
        decimals < 0 ? 0 : Math.min(decimals, MeasureFormat.MAX_DECIMALS) + 1);
  }

  @Override
  public void closeAdditionalWindow() {
    ViewSetting settings = MeasureTool.viewSetting;
    settings.setFontItem((FontItem) Objects.requireNonNull(fontItemJComboBox.getSelectedItem()));
    settings.setDecimals(
        decimalsCombo.getSelectedItem() instanceof Integer decimals
            ? decimals
            : MeasureFormat.AUTO);

    List<ViewerPlugin<?>> viewerPlugins = GuiUtils.getUICore().getViewerPlugins();
    synchronized (viewerPlugins) {
      for (int i = viewerPlugins.size() - 1; i >= 0; i--) {
        ViewerPlugin<?> p = viewerPlugins.get(i);
        if (p instanceof ImageViewerPlugin viewerPlugin) {
          for (Object v : viewerPlugin.getImagePanels()) {
            if (v instanceof ViewCanvas<?> view) {
              GraphicModel graphicList = view.getGraphicManager();
              graphicList.updateLabels(true, view);
            }
          }
        }
      }
    }
  }

  @Override
  public void resetToDefaultValues() {
    MeasureTool.viewSetting.setFontItem(ViewSetting.DEFAULT_FONT);
    MeasureTool.viewSetting.setDecimals(ViewSetting.DEFAULT_DECIMALS);
    selectDecimals(ViewSetting.DEFAULT_DECIMALS);
    initialize();
    measurementTools()
        .forEach(
            g -> {
              List<Measurement> list = g.getMeasurementList();
              Optional.ofNullable(list)
                  .ifPresent(l -> l.forEach(Measurement::resetToGraphicLabelValue));
            });

    selectTool((Graphic) comboBoxTool.getSelectedItem());

    Arrays.stream(ImageStatistics.ALL_MEASUREMENTS).forEach(Measurement::resetToGraphicLabelValue);
    map.forEach((key, value) -> key.setSelected(value.getGraphicLabel()));
  }
}
