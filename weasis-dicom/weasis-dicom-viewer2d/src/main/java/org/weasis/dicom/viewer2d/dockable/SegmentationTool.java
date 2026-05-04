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

import bibliothek.gui.dock.common.CLocation;
import eu.essilab.lablib.checkboxtree.TreeCheckingEvent;
import eu.essilab.lablib.checkboxtree.TreeCheckingModel;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import net.miginfocom.swing.MigLayout;
import org.dcm4che3.data.Tag;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.gui.util.Filter;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.gui.util.JSliderW;
import org.weasis.core.api.gui.util.SliderCineListener;
import org.weasis.core.api.image.util.MeasurableLayer;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.api.util.ResourceUtil;
import org.weasis.core.api.util.ResourceUtil.OtherIcon;
import org.weasis.core.ui.dialog.PropertiesDialog;
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
import org.weasis.dicom.codec.DicomSeries;
import org.weasis.dicom.codec.HiddenSeriesManager;
import org.weasis.dicom.codec.TagD;
import org.weasis.dicom.codec.seg.LazyContourLoader;
import org.weasis.dicom.codec.seg.SegSpecialElement;
import org.weasis.dicom.viewer2d.EventManager;
import org.weasis.dicom.viewer2d.Messages;
import org.weasis.dicom.viewer2d.View2d;
import org.weasis.dicom.viewer2d.mpr.MprController;
import org.weasis.dicom.viewer2d.mpr.MprView;
import org.weasis.opencv.data.PlanarImage;
import org.weasis.opencv.seg.RegionAttributes;

/**
 * @author Nicolas Roduit
 */
public class SegmentationTool extends PluginTool implements SeriesViewerListener, SegRegionTool {

  public static final String BUTTON_NAME = Messages.getString("segmentation");
  private static final String GRAPHIC_OPACITY = Messages.getString("graphic.opacity");

  private final SegRegionTree tree;
  private boolean initPathSelection;
  private final DefaultMutableTreeNode rootNodeStructures =
      new DefaultMutableTreeNode("rootNode", true); // NON-NLS
  private final Map<GroupTreeNode, SegSpecialElement> segNodeMap = new LinkedHashMap<>();
  private final JSliderW slider;

  public SegmentationTool() {
    super(BUTTON_NAME, Type.TOOL, 30);
    this.setLayout(new BorderLayout(0, 0));
    this.dockable.setTitleIcon(ResourceUtil.getIcon(OtherIcon.SEGMENTATION));
    this.setDockableWidth(350);
    this.slider = PropertiesDialog.createOpacitySlider(GRAPHIC_OPACITY);
    slider.setValue(80);
    PropertiesDialog.updateSlider(slider, GRAPHIC_OPACITY);
    slider.addChangeListener(
        l -> {
          updateSlider();
        });
    this.tree = new SegRegionTree(this);
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
      for (SegSpecialElement seg : segNodeMap.values()) {
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

  public void show(SegRegion<?> region) {
    ViewCanvas<DicomImageElement> view = EventManager.getInstance().getSelectedViewPane();
    DicomSeries series = (DicomSeries) view.getSeries();
    if (series != null) {
      long max = Long.MIN_VALUE;
      DicomImageElement bestImage = null;
      for (DicomImageElement dcm : series.getMedias(null, null)) {
        SegContour c = getContour(dcm, region);
        if (c != null) {
          if (c.getNumberOfPixels() > max) {
            max = c.getNumberOfPixels();
            bestImage = dcm;
          }
        }
      }
      if (bestImage != null) {
        Optional<SliderCineListener> action =
            EventManager.getInstance().getAction(ActionW.SCROLL_SERIES);
        if (action.isPresent()) {
          Filter<DicomImageElement> filter =
              (Filter<DicomImageElement>) view.getActionValue(ActionW.FILTERED_SERIES.cmd());
          int imgIndex = series.getImageIndex(bestImage, filter, view.getCurrentSortComparator());
          action.get().setSliderValue(imgIndex + 1);
        }
      }
    }
  }

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
    initStructureTree();
    Dimension minimumSize = GuiUtils.getDimension(150, 150);
    JScrollPane scrollPane = new JScrollPane(tree);
    scrollPane.setMinimumSize(minimumSize);
    scrollPane.setPreferredSize(minimumSize);
    add(scrollPane, BorderLayout.CENTER);

    MigLayout layout2 = new MigLayout("fillx, ins 5lp", "[fill]", "[]10lp[]"); // NON-NLS
    JPanel panelBottom = new JPanel(layout2);
    panelBottom.add(slider);
    add(panelBottom, BorderLayout.SOUTH);
  }

  public void initStructureTree() {
    this.tree.getCheckingModel().setCheckingMode(TreeCheckingModel.CheckingMode.SIMPLE);
    DefaultTreeModel model = new DefaultTreeModel(rootNodeStructures, false);
    tree.setModel(model);

    TreePath rootPath = new TreePath(rootNodeStructures.getPath());
    tree.addCheckingPath(rootPath);
    tree.setShowsRootHandles(true);
    tree.setRootVisible(false);
    tree.setExpandsSelectedPaths(true);
    tree.setCellRenderer(TreeBuilder.buildNoIconCheckboxTreeCellRenderer());
    tree.addTreeCheckingListener(this::treeValueChanged);

    TreeBuilder.expandTree(tree, rootNodeStructures, 3);
  }

  private void updateSlider() {
    float value = PropertiesDialog.updateSlider(slider, GRAPHIC_OPACITY);
    for (SegSpecialElement seg : segNodeMap.values()) {
      seg.setOpacity(value);
    }
    if (!segNodeMap.isEmpty()) {
      updateVisibleNode();
    }
  }

  private void treeValueChanged(TreeCheckingEvent e) {
    if (!initPathSelection) {
      updateVisibleNode();
    }
  }

  public void updateVisibleNode() {
    for (Map.Entry<GroupTreeNode, SegSpecialElement> entry : segNodeMap.entrySet()) {
      GroupTreeNode segNode = entry.getKey();
      SegSpecialElement seg = entry.getValue();
      boolean checked = tree.getCheckingModel().isPathChecked(new TreePath(segNode.getPath()));
      seg.setVisible(checked);
      segNode.setSelected(checked);
      tree.updateVisibleNode(segNode, segNode);
    }

    ImageViewerPlugin<DicomImageElement> container =
        EventManager.getInstance().getSelectedView2dContainer();
    List<ViewCanvas<DicomImageElement>> views = null;
    if (container != null) {
      views = container.getImagePanels();
    }
    if (views != null) {
      for (ViewCanvas<DicomImageElement> v : views) {
        if (v instanceof View2d view2d) {
          view2d.updateSegmentation();
          view2d.repaint();
        }
      }
    }
  }

  private void resetTree() {
    initPathSelection = true;
    segNodeMap.clear();
    rootNodeStructures.removeAllChildren();
    tree.setModel(new DefaultTreeModel(rootNodeStructures, false));
    initPathSelection = false;
  }

  public void updateCanvas(List<SegSpecialElement> list) {
    if (list == null || list.isEmpty()) {
      resetTree();
      return;
    }

    initPathSelection = true;
    float opacityValue = slider.getValue() / 100f;
    try {
      segNodeMap.clear();
      rootNodeStructures.removeAllChildren();
      tree.setModel(new DefaultTreeModel(rootNodeStructures, false));

      for (SegSpecialElement seg : list) {
        seg.setOpacity(opacityValue);
        GroupTreeNode segNode = new GroupTreeNode(seg.getLabel(), true);
        segNodeMap.put(segNode, seg);
        rootNodeStructures.add(segNode);

        addRegionsToNode(segNode, seg);
        tree.setPathSelection(new TreePath(segNode.getPath()), seg.isVisible());
      }

      TreeBuilder.expandTree(tree, rootNodeStructures, 3);
    } finally {
      initPathSelection = false;
    }
  }

  private void addRegionsToNode(GroupTreeNode parentNode, SegSpecialElement seg) {
    Map<String, List<SegRegion<DicomImageElement>>> map =
        SegRegion.groupRegions(seg.getSegAttributes().values());
    for (List<SegRegion<DicomImageElement>> regionList : map.values()) {
      if (regionList.size() == 1) {
        SegRegion<DicomImageElement> region = regionList.getFirst();
        DefaultMutableTreeNode node = SegSpecialElement.buildStructRegionNode(region);
        parentNode.add(node);
        tree.setPathSelection(new TreePath(node.getPath()), region.isSelected());
      } else {
        SegRegion<DicomImageElement> first = regionList.getFirst();
        if (first.getLabel().equals(first.getPrefix())) {
          // Labels are identical: skip parent node, add regions directly
          for (SegRegion<DicomImageElement> structRegion : regionList) {
            DefaultMutableTreeNode childNode =
                SegSpecialElement.buildStructRegionNode(structRegion);
            parentNode.add(childNode);
            tree.setPathSelection(new TreePath(childNode.getPath()), structRegion.isSelected());
          }
        } else {
          GroupTreeNode groupNode = new GroupTreeNode(first.getPrefix(), true);
          parentNode.add(groupNode);
          for (SegRegion<DicomImageElement> structRegion : regionList) {
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

  public void initTreeValues(ViewCanvas<?> viewCanvas) {
    List<SegSpecialElement> segList = null;
    if (viewCanvas != null) {
      // For MPR views the current series is a synthetic MPR series whose UID is never registered
      // in reference2Series. If the async seg build has already completed, return those elements
      // directly; otherwise fall back to the original source series UID so that the
      // reference2Series lookup still works.
      if (viewCanvas instanceof MprView mprView) {
        MprController ctrl = mprView.getMprController();
        if (ctrl != null) {
          List<SegSpecialElement> elements = ctrl.getSegElements();
          if (!elements.isEmpty()) {
            updateCanvas(elements);
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
                        SegSpecialElement.class, list.toArray(new String[0]));
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
                  SegSpecialElement.class, list.toArray(new String[0]));
        }

        // Fallback: use patient-level lookup when reference2Series has no entries.
        if ((segList == null || segList.isEmpty())
            && viewCanvas.getImage() instanceof DicomImageElement img) {
          String patientPseudoUID = (String) img.getTagValue(TagW.PatientPseudoUID);
          if (StringUtil.hasText(patientPseudoUID)) {
            List<SegSpecialElement> patientSegs =
                HiddenSeriesManager.getHiddenElementsFromPatient(
                    SegSpecialElement.class, patientPseudoUID);
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
    SeriesViewerEvent.EVENT e = event.getEventType();
    if (EVENT.SELECT_VIEW.equals(e) && event.getSeriesViewer() instanceof ImageViewerPlugin) {
      initTreeValues(((ImageViewerPlugin<?>) event.getSeriesViewer()).getSelectedViewCanvas());
    }
  }

  @Override
  protected void changeToolWindowAnchor(CLocation clocation) {
    // TODO Auto-generated method stub
  }
}
