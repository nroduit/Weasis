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
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
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
import org.dcm4che3.data.Tag;
import org.weasis.core.api.gui.Insertable;
import org.weasis.core.api.gui.util.ComboItemListener;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.image.util.MeasurableLayer;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.media.data.TagW;
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
import org.weasis.dicom.codec.SpecialElementRegion;
import org.weasis.dicom.codec.TagD;
import org.weasis.dicom.codec.seg.LazyContourLoader;
import org.weasis.dicom.codec.seg.SegSpecialElement;
import org.weasis.dicom.viewer2d.mpr.MprController;
import org.weasis.dicom.viewer2d.mpr.MprView;
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
    SEG_ONLY("Segmentation only"),
    SEG_OVERLAY("Segmentation overlay");

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
    tree.setCellRenderer(TreeBuilder.buildNoIconCheckboxTreeCellRenderer());

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
    panel.add(GuiUtils.boxVerticalStrut(gabY));
    add(panel, BorderLayout.SOUTH);
  }

  public void initStructureTree() {
    this.tree.getCheckingModel().setCheckingMode(TreeCheckingModel.CheckingMode.SIMPLE);
    DefaultTreeModel model = new DefaultTreeModel(rootNodeStructures, false);
    tree.setModel(model);

    tree.setShowsRootHandles(true);
    tree.setRootVisible(false);
    tree.setExpandsSelectedPaths(true);
    tree.setCellRenderer(TreeBuilder.buildNoIconCheckboxTreeCellRenderer());
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
      ComboItemListener<Type> segType =
          EventManager.getInstance().getAction(ActionVol.SEG_TYPE).orElse(null);
      if (segType != null) {
        Type selectedType = (Type) segType.getSelectedItem();
        if (selectedType == Type.SEG_ONLY) {
          Preset p = Preset.getSegmentationLut();
          for (ViewCanvas<DicomImageElement> v : views) {
            if (v instanceof View3d view3d) {
              view3d.setVolumePreset(p);
              view3d.updateSegmentation();
              view3d.repaint();
            }
          }
        } else if (selectedType == Type.SEG_OVERLAY) {
          for (ViewCanvas<DicomImageElement> v : views) {
            if (v instanceof View3d view3d) {
              view3d.refreshSegColorLUT();
            }
          }
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
   * the regions of every segmentation currently shown in the tree. Keys are namespaced by
   * segmentation label to avoid collisions across multiple SEG files.
   */
  private void refreshContainerRegionMap() {
    Map<String, List<SegRegion<?>>> map = Preset.getRegionMap();
    if (map == null) {
      return;
    }
    map.clear();
    for (Map.Entry<GroupTreeNode, SpecialElementRegion> entry : segNodeMap.entrySet()) {
      GroupTreeNode segNode = entry.getKey();
      String prefix = String.valueOf(segNode.getUserObject());
      List<SegRegion<?>> regions = collectRegions(segNode);
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
    List<SpecialElementRegion> segList = null;
    if (viewCanvas != null) {
      // For MPR views the current series is a synthetic MPR series whose UID is never registered
      // in reference2Series. If the async seg build has already completed, return those elements
      // directly; otherwise fall back to the original source series UID.
      if (viewCanvas instanceof MprView mprView) {
        MprController ctrl = mprView.getMprController();
        if (ctrl != null) {
          List<SegSpecialElement> elements = ctrl.getSegElements();
          if (!elements.isEmpty()) {
            updateCanvas(new java.util.ArrayList<>(elements));
            return;
          }
          var vol = ctrl.getVolume();
          if (vol != null) {
            String seriesUID =
                TagD.getTagValue(vol.getStack().getSeries(), Tag.SeriesInstanceUID, String.class);
            if (StringUtil.hasText(seriesUID)) {
              Set<String> list = HiddenSeriesManager.getInstance().reference2Series.get(seriesUID);
              if (list != null && !list.isEmpty()) {
                segList =
                    HiddenSeriesManager.getHiddenElementsFromSeries(
                        SpecialElementRegion.class, list.toArray(new String[0]));
              }
            }
          }
        }
        updateCanvas(segList);
        return;
      }

      MediaSeries<?> dcmSeries = viewCanvas.getSeries();
      String seriesUID = TagD.getTagValue(dcmSeries, Tag.SeriesInstanceUID, String.class);
      if (StringUtil.hasText(seriesUID)) {
        Set<String> list = HiddenSeriesManager.getInstance().reference2Series.get(seriesUID);
        if (list != null && !list.isEmpty()) {
          segList =
              HiddenSeriesManager.getHiddenElementsFromSeries(
                  SpecialElementRegion.class, list.toArray(new String[0]));
        }

        // Fallback: use patient-level lookup when reference2Series has no entries.
        if ((segList == null || segList.isEmpty())
            && viewCanvas.getImage() instanceof DicomImageElement img) {
          String patientPseudoUID = (String) img.getTagValue(TagW.PatientPseudoUID);
          if (StringUtil.hasText(patientPseudoUID)) {
            List<SpecialElementRegion> patientSegs =
                HiddenSeriesManager.getHiddenElementsFromPatient(
                    SpecialElementRegion.class, patientPseudoUID);
            if (!patientSegs.isEmpty()) {
              segList =
                  patientSegs.stream()
                      .filter(seg -> seg.containsSopInstanceUIDReference(img))
                      .toList();
            }
          }
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
      initTreeValues(((ImageViewerPlugin<?>) event.getSeriesViewer()).getSelectedViewCanvas());
    }
  }

  @Override
  protected void changeToolWindowAnchor(CLocation clocation) {
    // TODO Auto-generated method stub
  }
}
