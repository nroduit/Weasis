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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import javax.swing.BoxLayout;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import org.weasis.core.api.gui.util.AbstractItemDialogPage;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.util.FontItem;
import org.weasis.core.ui.Messages;
import org.weasis.core.ui.docking.UIManager;
import org.weasis.core.ui.editor.image.ImageViewerPlugin;
import org.weasis.core.ui.editor.image.MeasureToolBar;
import org.weasis.core.ui.editor.image.ViewCanvas;
import org.weasis.core.ui.editor.image.ViewerPlugin;
import org.weasis.core.ui.editor.image.dockable.MeasureTool;
import org.weasis.core.ui.model.GraphicModel;
import org.weasis.core.ui.model.graphic.Graphic;
import org.weasis.core.ui.model.utils.ImageStatistics;
import org.weasis.core.ui.model.utils.bean.Measurement;
import org.weasis.core.util.StringUtil;

public class LabelsPrefView extends AbstractItemDialogPage {
  private final JPanel panelList = new JPanel();
  private final JComboBox<Graphic> comboBoxTool;
  private final JComboBox<FontItem> fontItemJComboBox;
  private final ViewSetting viewSetting;
  private final Map<JCheckBox, Measurement> map;

  public LabelsPrefView() {
    super(MeasureTool.LABEL_PREF_NAME, 510);
    this.map = new HashMap<>(ImageStatistics.ALL_MEASUREMENTS.length);
    this.viewSetting = Objects.requireNonNull(MeasureTool.viewSetting);

    ArrayList<Graphic> tools = new ArrayList<>(MeasureToolBar.measureGraphicList);
    tools.remove(0);
    this.comboBoxTool = new JComboBox<>(tools.toArray(Graphic[]::new));
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
    String key = viewSetting.getFontKey();
    if (StringUtil.hasText(key)) {
      fontItemJComboBox.setSelectedItem(FontItem.getFontItem(key));
    } else {
      fontItemJComboBox.setSelectedItem(FontItem.SMALL_SEMIBOLD);
    }

    selectTool((Graphic) comboBoxTool.getSelectedItem());
  }

  @Override
  public void closeAdditionalWindow() {
    viewSetting.setFontKey(
        ((FontItem) Objects.requireNonNull(fontItemJComboBox.getSelectedItem())).getKey());
    MeasureToolBar.measureGraphicList.forEach(
        g -> MeasureToolBar.applyDefaultSetting(viewSetting, g));

    synchronized (UIManager.VIEWER_PLUGINS) {
      for (int i = UIManager.VIEWER_PLUGINS.size() - 1; i >= 0; i--) {
        ViewerPlugin<?> p = UIManager.VIEWER_PLUGINS.get(i);
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
    viewSetting.setFontKey(FontItem.SMALL_SEMIBOLD.getKey());
    initialize();
    MeasureToolBar.measureGraphicList.forEach(
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
