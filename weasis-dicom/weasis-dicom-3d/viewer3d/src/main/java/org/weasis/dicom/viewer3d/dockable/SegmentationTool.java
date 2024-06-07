/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.viewer3d.dockable;

import bibliothek.gui.dock.common.CLocation;
import eu.essilab.lablib.checkboxtree.TreeCheckingEvent;
import eu.essilab.lablib.checkboxtree.TreeCheckingModel;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import net.miginfocom.swing.MigLayout;
import org.dcm4che3.data.Tag;
import org.weasis.core.api.gui.Insertable;
import org.weasis.core.api.gui.util.ComboItemListener;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.image.util.MeasurableLayer;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.util.ResourceUtil;
import org.weasis.core.api.util.ResourceUtil.OtherIcon;
import org.weasis.core.ui.docking.PluginTool;
import org.weasis.core.ui.editor.SeriesViewerEvent;
import org.weasis.core.ui.editor.SeriesViewerEvent.EVENT;
import org.weasis.core.ui.editor.SeriesViewerListener;
import org.weasis.core.ui.editor.image.ImageViewerPlugin;
import org.weasis.core.ui.editor.image.ViewCanvas;
import org.weasis.core.ui.model.graphic.imp.seg.GroupTreeNode;
import org.weasis.core.ui.model.graphic.imp.seg.SegContour;
import org.weasis.core.ui.model.graphic.imp.seg.SegRegion;
import org.weasis.core.ui.util.*;
import org.weasis.core.util.StringUtil;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.codec.HiddenSeriesManager;
import org.weasis.dicom.codec.SegSpecialElement;
import org.weasis.dicom.codec.SpecialElementRegion;
import org.weasis.dicom.codec.TagD;
import org.weasis.dicom.viewer3d.ActionVol;
import org.weasis.dicom.viewer3d.EventManager;
import org.weasis.dicom.viewer3d.vr.Preset;
import org.weasis.dicom.viewer3d.vr.View3d;
import org.weasis.opencv.data.PlanarImage;
import org.weasis.opencv.seg.RegionAttributes;

/**
 * @author Nicolas Roduit
 */
public class SegmentationTool extends PluginTool implements SeriesViewerListener, SegRegionTool {

  public static final String BUTTON_NAME = "Segmentation";

  public enum Type {
    NONE("None"),
    SEG_ONLY("Segmentation only");

    private final String title;

    Type(String title) {
      this.title = title;
    }

    @Override
    public String toString() {
      return title;
    }
  }

  private final SegRegionTree tree;
  private boolean initPathSelection;
  private final DefaultMutableTreeNode rootNodeStructures =
      new DefaultMutableTreeNode("rootNode", true); // NON-NLS
  private final JComboBox<SpecialElementRegion> comboSeg = new JComboBox<>();

  private final GroupTreeNode nodeStructures;

  private final transient ItemListener structureChangeListener =
      e -> {
        if (e.getStateChange() == ItemEvent.SELECTED) {
          updateTree((SpecialElementRegion) e.getItem());
        }
      };

  public SegmentationTool() {
    super(BUTTON_NAME, Insertable.Type.TOOL, 30);
    this.setLayout(new BorderLayout(0, 0));
    this.dockable.setTitleIcon(ResourceUtil.getIcon(OtherIcon.SEGMENTATION));
    this.setDockableWidth(350);
    this.tree =
        new SegRegionTree(this) {
          @Override
          protected JMenuItem getStatisticMenuItem(DefaultMutableTreeNode node) {
            return null;
          }

          @Override
          protected JMenuItem getSelectionMenuItem(DefaultMutableTreeNode node) {
            return null;
          }
        };
    tree.setToolTipText(StringUtil.EMPTY_STRING);
    tree.setCellRenderer(TreeBuilder.buildNoIconCheckboxTreeCellRenderer());

    this.nodeStructures = new GroupTreeNode("List of regions", true);
    this.initData();

    initListeners();
  }

  private void initListeners() {
    comboSeg.addItemListener(structureChangeListener);
    tree.initListeners();
  }

  private DicomImageElement getImageElement(ViewCanvas<DicomImageElement> view) {
    if (view != null && view.getImage() instanceof DicomImageElement imageElement) {
      return imageElement;
    }
    return null;
  }

  private SegContour getContour(DicomImageElement imageElement, RegionAttributes attributes) {
    PlanarImage img = imageElement.getImage();
    if (img != null) {
      if (comboSeg.getSelectedItem() instanceof SpecialElementRegion seg) {
        Collection<SegContour> segments = seg.getContours(imageElement);
        for (SegContour c : segments) {
          if (c.getAttributes().equals(attributes)) {
            return c;
          }
        }
      }
    }
    return null;
  }

  public void show(SegRegion<?> region) {}

  public void computeStatistics(SegRegion<?> region) {
    ViewCanvas<DicomImageElement> view = EventManager.getInstance().getSelectedViewPane();
    DicomImageElement imageElement = getImageElement(view);
    if (imageElement != null) {
      SegContour c = getContour(imageElement, region);
      if (c != null) {
        MeasurableLayer layer = view.getMeasurableLayer();
        tree.showStatistics(c, layer);
      }
    }
  }

  public void initData() {
    int gabY = 7;
    MigLayout layout =
        new MigLayout("fillx, ins 5lp 3lp 0lp 3lp", "[grow,fill]", "[]10lp[]"); // NON-NLS
    JPanel panelMain = new JPanel(layout);
    panelMain.add(comboSeg, "width 50lp:min:320lp"); // NON-NLS
    add(panelMain, BorderLayout.NORTH);

    initStructureTree();
    Dimension minimumSize = GuiUtils.getDimension(150, 150);
    JScrollPane scrollPane = new JScrollPane(tree);
    scrollPane.setMinimumSize(minimumSize);
    scrollPane.setPreferredSize(minimumSize);
    add(scrollPane, BorderLayout.CENTER);

    JPanel panel = GuiUtils.getVerticalBoxLayoutPanel(GuiUtils.boxVerticalStrut(gabY));
    EventManager.getInstance()
        .getAction(ActionVol.SEG_TYPE)
        .ifPresent(
            comboItem -> {
              JLabel label = new JLabel(ActionVol.SEG_TYPE.getTitle() + StringUtil.COLON);
              JComboBox<?> combo = comboItem.createCombo(140);
              combo.setMaximumRowCount(10);
              panel.add(GuiUtils.getHorizontalBoxLayoutPanel(5, label, combo));
            });
    panel.add(GuiUtils.boxVerticalStrut(gabY));
    add(panel, BorderLayout.SOUTH);
  }

  public void initStructureTree() {
    this.tree.getCheckingModel().setCheckingMode(TreeCheckingModel.CheckingMode.SIMPLE);
    DefaultTreeModel model = new DefaultTreeModel(rootNodeStructures, false);
    tree.setModel(model);

    rootNodeStructures.add(nodeStructures);
    TreePath rootPath = new TreePath(rootNodeStructures.getPath());
    tree.addCheckingPath(rootPath);
    tree.setShowsRootHandles(true);
    tree.setRootVisible(false);
    tree.setExpandsSelectedPaths(true);
    tree.setCellRenderer(TreeBuilder.buildNoIconCheckboxTreeCellRenderer());
    tree.addTreeCheckingListener(this::treeValueChanged);

    TreeBuilder.expandTree(tree, rootNodeStructures, 2);
  }

  private void treeValueChanged(TreeCheckingEvent e) {
    if (!initPathSelection) {
      updateVisibleNode();
    }
  }

  public void updateVisibleNode() {
    boolean all = tree.getCheckingModel().isPathChecked(new TreePath(nodeStructures.getPath()));
    nodeStructures.setSelected(all);
    tree.updateVisibleNode(rootNodeStructures, nodeStructures);

    ImageViewerPlugin<DicomImageElement> container =
        EventManager.getInstance().getSelectedView2dContainer();
    List<ViewCanvas<DicomImageElement>> views = null;
    if (container != null) {
      views = container.getImagePanels();
    }
    if (views != null && !views.isEmpty()) {
      ComboItemListener<Type> segType =
          EventManager.getInstance().getAction(ActionVol.SEG_TYPE).orElse(null);
      if (segType != null && segType.getSelectedItem() == Type.SEG_ONLY) {
        Preset p = Preset.getSegmentationLut();
        for (ViewCanvas<DicomImageElement> v : views) {
          if (v instanceof View3d view3d) {
            view3d.setVolumePreset(p);
            view3d.updateSegmentation();
            view3d.repaint();
          }
        }
      }
    }
  }

  private void resetTree() {
    initPathSelection = true;
    nodeStructures.removeAllChildren();
    tree.setModel(new DefaultTreeModel(rootNodeStructures, false));
    initPathSelection = false;
  }

  public void updateCanvas(List<SpecialElementRegion> list) {
    if (list == null || list.isEmpty()) {
      resetTree();
      comboSeg.removeAllItems();
      return;
    }

    comboSeg.removeItemListener(structureChangeListener);
    SpecialElementRegion oldStructure = (SpecialElementRegion) comboSeg.getSelectedItem();
    comboSeg.removeAllItems();
    list.forEach(comboSeg::addItem);

    boolean update = !list.contains(oldStructure);
    if (update) {
      comboSeg.setSelectedIndex(0);
      updateTree((SpecialElementRegion) comboSeg.getSelectedItem());
    } else {
      comboSeg.setSelectedItem(oldStructure);
    }

    comboSeg.addItemListener(structureChangeListener);
  }

  public void updateTree(SpecialElementRegion specialElement) {
    // Empty tree when no RtSet
    if (specialElement == null) {
      resetTree();
      return;
    }

    initPathSelection = true;
    try {
      // Prepare root tree model
      tree.setModel(new DefaultTreeModel(rootNodeStructures, false));

      // Prepare parent node for structures
      nodeStructures.removeAllChildren();
      Collection<SegRegion<?>> regions =
          (Collection<SegRegion<?>>) specialElement.getSegAttributes().values();
      List<SegRegion<?>> regionList = new ArrayList<>();
      for (SegRegion<?> region : regions) {
        SegRegion<?> copy = region.copy();
        copy.setInteriorOpacity(1.0f);
        regionList.add(copy);
      }
      Map<String, List<SegRegion<?>>> regionMap = SegRegion.groupRegions(regionList);
      Map<String, List<SegRegion<?>>> map = Preset.getRegionMap();
      if (map != null) {
        map.clear();
        map.putAll(regionMap);
        initTreeSelection(specialElement, map);
      }
    } finally {
      initPathSelection = false;
    }
  }

  private void initTreeSelection(
      SpecialElementRegion specialElement, Map<String, List<SegRegion<?>>> regionMap) {
    for (List<SegRegion<?>> list : regionMap.values()) {
      if (list.size() == 1) {
        SegRegion<?> region = list.getFirst();
        DefaultMutableTreeNode node = SegSpecialElement.buildStructRegionNode(region);
        nodeStructures.add(node);
        tree.setPathSelection(new TreePath(node.getPath()), region.isSelected());
      } else {
        GroupTreeNode node = new GroupTreeNode(list.getFirst().getPrefix(), true);
        nodeStructures.add(node);
        for (SegRegion<?> structRegion : list) {
          DefaultMutableTreeNode childNode = SegSpecialElement.buildStructRegionNode(structRegion);
          node.add(childNode);
          tree.setPathSelection(new TreePath(childNode.getPath()), structRegion.isSelected());
        }
        tree.setPathSelection(new TreePath(node.getPath()), true);
      }
    }
    tree.setPathSelection(new TreePath(nodeStructures.getPath()), specialElement.isVisible());

    // Expand
    TreeBuilder.expandTree(tree, rootNodeStructures, 2);
  }

  public void initTreeValues(ViewCanvas<?> viewCanvas) {
    List<SpecialElementRegion> segList = null;
    if (viewCanvas != null) {
      MediaSeries<?> dcmSeries = viewCanvas.getSeries();
      String seriesUID = TagD.getTagValue(dcmSeries, Tag.SeriesInstanceUID, String.class);
      if (StringUtil.hasText(seriesUID)) {
        Set<String> list = HiddenSeriesManager.getInstance().reference2Series.get(seriesUID);
        if (list != null && !list.isEmpty()) {
          segList =
              HiddenSeriesManager.getHiddenElementsFromSeries(
                  SpecialElementRegion.class, list.toArray(new String[0]));
        }
      }
    }
    updateCanvas(segList);
  }

  @Override
  public Component getToolComponent() {
    return this;
  }

  @Override
  public void changingViewContentEvent(SeriesViewerEvent event) {
    EVENT e = event.getEventType();
    if (EVENT.SELECT_VIEW.equals(e) && event.getSeriesViewer() instanceof ImageViewerPlugin) {
      initTreeValues(((ImageViewerPlugin<?>) event.getSeriesViewer()).getSelectedImagePane());
    }
  }

  @Override
  protected void changeToolWindowAnchor(CLocation clocation) {
    // TODO Auto-generated method stub
  }
}
