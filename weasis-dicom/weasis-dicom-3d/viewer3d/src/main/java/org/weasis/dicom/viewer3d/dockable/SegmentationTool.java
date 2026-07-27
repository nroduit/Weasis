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
import java.awt.Dimension;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import org.weasis.core.api.gui.Insertable;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.image.util.MeasurableLayer;
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
import org.weasis.dicom.codec.SpecialElementRegion;
import org.weasis.dicom.codec.seg.LazyContourLoader;
import org.weasis.dicom.codec.seg.SegSpecialElement;
import org.weasis.dicom.viewer2d.SegComponentFactory;
import org.weasis.dicom.viewer3d.ActionVol;
import org.weasis.dicom.viewer3d.EventManager;
import org.weasis.dicom.viewer3d.Messages;
import org.weasis.dicom.viewer3d.vr.Preset;
import org.weasis.dicom.viewer3d.vr.View3d;
import org.weasis.opencv.data.PlanarImage;
import org.weasis.opencv.seg.RegionAttributes;

/**
 * @author Nicolas Roduit
 */
public class SegmentationTool extends PluginTool implements SeriesViewerListener, SegRegionTool {

  public static final String BUTTON_NAME =
      org.weasis.dicom.viewer2d.Messages.getString("segmentation");

  public enum Type {
    NONE(Messages.getString("segmentation.none")),
    SEG_ONLY(Messages.getString("segmentation.only")),
    SEG_OVERLAY(Messages.getString("segmentation.overlay")),
    SEG_MASK_INCLUDE(Messages.getString("segmentation.mask.include")),
    SEG_MASK_EXCLUDE(Messages.getString("segmentation.mask.exclude"));

    private final String title;

    Type(String title) {
      this.title = title;
    }

    /** Returns {@code true} when this mode needs the segmentation texture to render. */
    public boolean requiresSegTexture() {
      return this != NONE;
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
  private final Map<GroupTreeNode, SpecialElementRegion> segNodeMap = new LinkedHashMap<>();

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
    tree.setCellRenderer(TreeBuilder.buildSegRegionCellRenderer());

    this.initData();

    initListeners();
  }

  private void initListeners() {
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
      for (SpecialElementRegion seg : segNodeMap.values()) {
        Set<LazyContourLoader> loaders = seg.getContours(imageElement);
        if (loaders == null || loaders.isEmpty()) {
          continue;
        }
        for (LazyContourLoader loader : loaders) {
          Collection<SegContour> segments = loader.getLazyContours();
          for (SegContour c : segments) {
            if (c.getAttributes().equals(attributes)) {
              return c;
            }
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
    JButton showAll =
        new JButton(org.weasis.dicom.viewer2d.Messages.getString("show.all")); // NON-NLS
    showAll.addActionListener(_ -> setAllSegVisible(true));
    JButton hideAll =
        new JButton(org.weasis.dicom.viewer2d.Messages.getString("hide.all")); // NON-NLS
    hideAll.addActionListener(_ -> setAllSegVisible(false));
    panel.add(GuiUtils.getFlowLayoutPanel(showAll, hideAll));
    panel.add(GuiUtils.boxVerticalStrut(gabY));
    add(panel, BorderLayout.SOUTH);
  }

  /** Checks or unchecks every segmentation node at once and refreshes the views. */
  private void setAllSegVisible(boolean visible) {
    initPathSelection = true;
    try {
      for (GroupTreeNode node : segNodeMap.keySet()) {
        tree.setPathSelection(new TreePath(node.getPath()), visible);
      }
    } finally {
      initPathSelection = false;
    }
    updateVisibleNode();
  }

  public void initStructureTree() {
    this.tree.getCheckingModel().setCheckingMode(TreeCheckingModel.CheckingMode.SIMPLE);
    DefaultTreeModel model = new DefaultTreeModel(rootNodeStructures, false);
    tree.setModel(model);

    tree.setShowsRootHandles(true);
    tree.setRootVisible(false);
    tree.setExpandsSelectedPaths(true);
    tree.setCellRenderer(TreeBuilder.buildSegRegionCellRenderer());
    tree.addTreeCheckingListener(this::treeValueChanged);

    TreeBuilder.expandTree(tree, rootNodeStructures, 3);
  }

  private void treeValueChanged(TreeCheckingEvent e) {
    if (!initPathSelection) {
      updateVisibleNode();
    }
  }

  public void updateVisibleNode() {
    for (Map.Entry<GroupTreeNode, SpecialElementRegion> entry : segNodeMap.entrySet()) {
      GroupTreeNode segNode = entry.getKey();
      SpecialElementRegion seg = entry.getValue();
      boolean checked = tree.getCheckingModel().isPathChecked(new TreePath(segNode.getPath()));
      seg.setVisible(checked);
      segNode.setSelected(checked);
      tree.updateVisibleNode(segNode, segNode);
    }

    // Update the container's region map so that Preset.getRegionMap() reflects the tree state.
    refreshContainerRegionMap();

    ImageViewerPlugin<DicomImageElement> container =
        EventManager.getInstance().getSelectedView2dContainer();
    List<ViewCanvas<DicomImageElement>> views = null;
    if (container != null) {
      views = container.getImagePanels();
    }
    if (views != null && !views.isEmpty()) {
      for (ViewCanvas<DicomImageElement> v : views) {
        if (v instanceof View3d view3d && view3d.getSegType().requiresSegTexture()) {
          view3d.refreshSegColorLUT();
        }
      }
    }
  }

  private void resetTree() {
    initPathSelection = true;
    segNodeMap.clear();
    rootNodeStructures.removeAllChildren();
    tree.setModel(new DefaultTreeModel(rootNodeStructures, false));
    Map<String, List<SegRegion<?>>> map = Preset.getRegionMap();
    if (map != null) {
      map.clear();
    }
    initPathSelection = false;
  }

  public void updateCanvas(List<SpecialElementRegion> list) {
    if (list == null || list.isEmpty()) {
      resetTree();
      return;
    }

    // Keep the existing tree (and its checkbox state) when the same segmentations are already shown
    if (segNodeMap.size() == list.size() && segNodeMap.values().containsAll(list)) {
      // Same segmentations already shown — keep the tree and its checkbox state, but refresh the
      // per-region voxel counts: the SegmentationVolume decodes in the background, so the counts
      // may have become available only after the tree was first built.
      syncRegionPixelCounts();
      return;
    }

    initPathSelection = true;
    try {
      segNodeMap.clear();
      rootNodeStructures.removeAllChildren();
      tree.setModel(new DefaultTreeModel(rootNodeStructures, false));

      for (SpecialElementRegion seg : list) {
        String label = seg instanceof SegSpecialElement sse ? sse.getLabel() : seg.toString();
        GroupTreeNode segNode = new GroupTreeNode(label, true);
        segNodeMap.put(segNode, seg);
        rootNodeStructures.add(segNode);

        addRegionsToNode(segNode, seg);
        tree.setPathSelection(new TreePath(segNode.getPath()), seg.isVisible());
      }

      refreshContainerRegionMap();

      TreeBuilder.expandTree(tree, rootNodeStructures, 3);
    } finally {
      initPathSelection = false;
    }
  }

  /**
   * Refreshes the voxel count of every region node from the live {@link SegRegion} held by the
   * segmentation, without rebuilding the tree. The counts are computed in the background once the
   * {@code SegmentationVolume} is decoded, so a tree built earlier keeps the tooltips up to date.
   */
  private void syncRegionPixelCounts() {
    for (Map.Entry<GroupTreeNode, SpecialElementRegion> entry : segNodeMap.entrySet()) {
      Map<Integer, ? extends RegionAttributes> live = entry.getValue().getSegAttributes();
      if (live == null) {
        continue;
      }
      for (SegRegion<?> region : collectRegions(entry.getKey())) {
        RegionAttributes src = live.get(region.getId());
        if (src != null) {
          region.setNumberOfPixels(src.getNumberOfPixels());
        }
      }
    }
  }

  private void addRegionsToNode(GroupTreeNode parentNode, SpecialElementRegion seg) {
    Collection<SegRegion<?>> regions = (Collection<SegRegion<?>>) seg.getSegAttributes().values();
    List<SegRegion<?>> regionList = new ArrayList<>();
    for (SegRegion<?> region : regions) {
      SegRegion<?> copy = region.copy();
      copy.setInteriorOpacity(1.0f);
      regionList.add(copy);
    }
    Map<String, List<SegRegion<?>>> map = SegRegion.groupRegions(regionList);
    for (List<SegRegion<?>> groupedList : map.values()) {
      if (groupedList.size() == 1) {
        SegRegion<?> region = groupedList.getFirst();
        DefaultMutableTreeNode node = SegSpecialElement.buildStructRegionNode(region);
        parentNode.add(node);
        tree.setPathSelection(new TreePath(node.getPath()), region.isSelected());
      } else {
        SegRegion<?> first = groupedList.getFirst();
        if (first.getLabel().equals(first.getPrefix())) {
          // Labels are identical: skip parent node, add regions directly
          for (SegRegion<?> structRegion : groupedList) {
            DefaultMutableTreeNode childNode =
                SegSpecialElement.buildStructRegionNode(structRegion);
            parentNode.add(childNode);
            tree.setPathSelection(new TreePath(childNode.getPath()), structRegion.isSelected());
          }
        } else {
          GroupTreeNode groupNode = new GroupTreeNode(first.getPrefix(), true);
          parentNode.add(groupNode);
          for (SegRegion<?> structRegion : groupedList) {
            DefaultMutableTreeNode childNode =
                SegSpecialElement.buildStructRegionNode(structRegion);
            groupNode.add(childNode);
            tree.setPathSelection(new TreePath(childNode.getPath()), structRegion.isSelected());
          }
          tree.setPathSelection(new TreePath(groupNode.getPath()), true);
        }
      }
    }
  }

  /**
   * Rebuilds the container's region map (consumed by {@link Preset#getRegionMap()}) by aggregating
   * the regions of every segmentation currently shown in the tree. Keys are namespaced by {@link
   * SpecialElementRegion#getRegionUID()} — NOT by label, which may be identical for several SEG
   * objects of the same series — so per-file state never collides across multiple SEG files.
   */
  private void refreshContainerRegionMap() {
    Map<String, List<SegRegion<?>>> map = Preset.getRegionMap();
    if (map == null) {
      return;
    }
    map.clear();
    for (Map.Entry<GroupTreeNode, SpecialElementRegion> entry : segNodeMap.entrySet()) {
      String prefix = entry.getValue().getRegionUID();
      List<SegRegion<?>> regions = collectRegions(entry.getKey());
      Map<String, List<SegRegion<?>>> grouped = SegRegion.groupRegions(regions);
      for (Map.Entry<String, List<SegRegion<?>>> e : grouped.entrySet()) {
        map.put(prefix + "::" + e.getKey(), e.getValue()); // NON-NLS
      }
    }
  }

  private static List<SegRegion<?>> collectRegions(DefaultMutableTreeNode node) {
    List<SegRegion<?>> regions = new ArrayList<>();
    java.util.Enumeration<?> children = node.depthFirstEnumeration();
    while (children.hasMoreElements()) {
      Object child = children.nextElement();
      if (child instanceof DefaultMutableTreeNode dtm
          && dtm.getUserObject() instanceof SegRegion<?> region) {
        regions.add(region);
      }
    }
    return regions;
  }

  public void initTreeValues(ViewCanvas<?> viewCanvas) {
    updateCanvas(
        viewCanvas == null
            ? null
            : SegComponentFactory.getRelatedSegments(SpecialElementRegion.class, viewCanvas));
  }

  @Override
  public void changingViewContentEvent(SeriesViewerEvent event) {
    EVENT e = event.getEventType();
    if (EVENT.SELECT_VIEW.equals(e) && event.getSeriesViewer() instanceof ImageViewerPlugin) {
      initTreeValues(((ImageViewerPlugin<?>) event.getSeriesViewer()).getSelectedViewCanvas());
    }
  }

  @Override
  protected void changeToolWindowAnchor(CLocation clocation) {
    // TODO Auto-generated method stub
  }
}
