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
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import net.miginfocom.swing.MigLayout;
import org.dcm4che3.data.Tag;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.gui.util.DecFormatter;
import org.weasis.core.api.gui.util.Filter;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.gui.util.JSliderW;
import org.weasis.core.api.gui.util.SliderCineListener;
import org.weasis.core.api.image.measure.MeasurementsAdapter;
import org.weasis.core.api.image.util.MeasurableLayer;
import org.weasis.core.api.media.data.MediaSeries;
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
import org.weasis.core.ui.model.graphic.imp.seg.SegMeasurableLayer;
import org.weasis.core.ui.model.graphic.imp.seg.SegRegion;
import org.weasis.core.ui.util.*;
import org.weasis.core.util.StringUtil;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.codec.DicomSeries;
import org.weasis.dicom.codec.HiddenSeriesManager;
import org.weasis.dicom.codec.SegSpecialElement;
import org.weasis.dicom.codec.TagD;
import org.weasis.dicom.viewer2d.EventManager;
import org.weasis.dicom.viewer2d.Messages;
import org.weasis.dicom.viewer2d.View2d;
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
  private final JComboBox<SegSpecialElement> comboSeg = new JComboBox<>();

  private final GroupTreeNode nodeStructures;
  private final transient ItemListener structureChangeListener =
      e -> {
        if (e.getStateChange() == ItemEvent.SELECTED) {
          updateTree((SegSpecialElement) e.getItem());
        }
      };
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

    this.nodeStructures = new GroupTreeNode(Messages.getString("list.of.regions"), true);
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
      if (comboSeg.getSelectedItem() instanceof SegSpecialElement seg) {
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

    MigLayout layout2 = new MigLayout("fillx, ins 5lp", "[fill]", "[]10lp[]"); // NON-NLS
    JPanel panelBottom = new JPanel(layout2);
    panelBottom.add(slider);
    add(panelBottom, BorderLayout.SOUTH);

    initSlider();
  }

  private void initSlider() {
    SegSpecialElement item = (SegSpecialElement) comboSeg.getSelectedItem();
    float opacity = item == null ? 1.0f : item.getOpacity();
    slider.setValue((int) (opacity * 100));
    PropertiesDialog.updateSlider(slider, GRAPHIC_OPACITY);
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

  private void updateSlider() {
    float value = PropertiesDialog.updateSlider(slider, GRAPHIC_OPACITY);
    SegSpecialElement seg = (SegSpecialElement) comboSeg.getSelectedItem();
    if (seg != null) {
      seg.setOpacity(value);
      updateVisibleNode();
    }
  }

  private void treeValueChanged(TreeCheckingEvent e) {
    if (!initPathSelection) {
      updateVisibleNode();
    }
  }

  public void updateVisibleNode() {
    boolean all = tree.getCheckingModel().isPathChecked(new TreePath(nodeStructures.getPath()));
    SegSpecialElement seg = (SegSpecialElement) comboSeg.getSelectedItem();
    if (seg != null) {
      seg.setVisible(all);
    }

    nodeStructures.setSelected(all);
    tree.updateVisibleNode(rootNodeStructures, nodeStructures);

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
    nodeStructures.removeAllChildren();
    tree.setModel(new DefaultTreeModel(rootNodeStructures, false));
    initPathSelection = false;
  }

  public void updateCanvas(List<SegSpecialElement> list) {
    if (list == null || list.isEmpty()) {
      resetTree();
      comboSeg.removeAllItems();
      return;
    }

    comboSeg.removeItemListener(structureChangeListener);
    SegSpecialElement oldStructure = (SegSpecialElement) comboSeg.getSelectedItem();
    comboSeg.removeAllItems();
    list.forEach(comboSeg::addItem);

    boolean update = !list.contains(oldStructure);
    if (update) {
      comboSeg.setSelectedIndex(0);
      updateTree((SegSpecialElement) comboSeg.getSelectedItem());
    } else {
      comboSeg.setSelectedItem(oldStructure);
    }

    comboSeg.addItemListener(structureChangeListener);
  }

  public void updateTree(SegSpecialElement specialElement) {
    // Empty tree when no RtSet
    if (specialElement == null) {
      resetTree();
      return;
    }

    initPathSelection = true;
    specialElement.setOpacity(slider.getValue() / 100f);
    try {
      // Prepare root tree model
      tree.setModel(new DefaultTreeModel(rootNodeStructures, false));

      // Prepare parent node for structures
      nodeStructures.removeAllChildren();
      Map<String, List<SegRegion<DicomImageElement>>> map =
          SegRegion.groupRegions(specialElement.getSegAttributes().values());
      for (List<SegRegion<DicomImageElement>> list : map.values()) {
        if (list.size() == 1) {
          SegRegion<DicomImageElement> region = list.getFirst();
          DefaultMutableTreeNode node = buildStructRegionNode(region);
          nodeStructures.add(node);
          tree.setPathSelection(new TreePath(node.getPath()), region.isSelected());
        } else {
          GroupTreeNode node = new GroupTreeNode(list.getFirst().getPrefix(), true);
          nodeStructures.add(node);
          for (SegRegion<DicomImageElement> structRegion : list) {
            DefaultMutableTreeNode childNode = buildStructRegionNode(structRegion);
            node.add(childNode);
            tree.setPathSelection(new TreePath(childNode.getPath()), structRegion.isSelected());
          }
          tree.setPathSelection(new TreePath(node.getPath()), true);
        }
      }
      tree.setPathSelection(new TreePath(nodeStructures.getPath()), specialElement.isVisible());

      // Expand
      TreeBuilder.expandTree(tree, rootNodeStructures, 2);
    } finally {
      initPathSelection = false;
    }
  }

  private DefaultMutableTreeNode buildStructRegionNode(SegRegion<DicomImageElement> contour) {
    return new StructToolTipTreeNode(contour, false) {
      @Override
      public String getToolTipText() {
        SegRegion<?> seg = (SegRegion) getUserObject();
        StringBuilder buf = new StringBuilder();
        buf.append(GuiUtils.HTML_START);
        buf.append("<b>");
        buf.append(seg.getLabel());
        buf.append("</b>");
        buf.append(GuiUtils.HTML_BR);
        buf.append("Algorithm type"); // NON-NLS
        buf.append(StringUtil.COLON_AND_SPACE);
        buf.append(seg.getType());
        buf.append(GuiUtils.HTML_BR);
        buf.append("Voxel count"); // NON-NLS
        buf.append(StringUtil.COLON_AND_SPACE);
        buf.append(DecFormatter.allNumber(seg.getNumberOfPixels()));
        buf.append(GuiUtils.HTML_BR);
        SegMeasurableLayer<?> layer = seg.getMeasurableLayer();
        if (layer != null) {
          MeasurementsAdapter adapter =
              layer.getMeasurementAdapter(layer.getSourceImage().getPixelSpacingUnit());
          buf.append("Volume (%s3)".formatted(adapter.getUnit())); // NON-NLS
          buf.append(StringUtil.COLON_AND_SPACE);
          double ratio = adapter.getCalibRatio();
          buf.append(
              DecFormatter.twoDecimal(
                  seg.getNumberOfPixels() * ratio * ratio * layer.getThickness()));
          buf.append(GuiUtils.HTML_BR);
        }
        buf.append(GuiUtils.HTML_END);
        return buf.toString();
      }
    };
  }

  public void initTreeValues(ViewCanvas<?> viewCanvas) {
    List<SegSpecialElement> segList = null;
    if (viewCanvas != null) {
      MediaSeries<?> dcmSeries = viewCanvas.getSeries();
      String seriesUID = TagD.getTagValue(dcmSeries, Tag.SeriesInstanceUID, String.class);
      if (StringUtil.hasText(seriesUID)) {
        Set<String> list = HiddenSeriesManager.getInstance().reference2Series.get(seriesUID);
        if (list != null && !list.isEmpty()) {
          segList =
              HiddenSeriesManager.getHiddenElementsFromSeries(
                  SegSpecialElement.class, list.toArray(new String[0]));
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
      initTreeValues(((ImageViewerPlugin<?>) event.getSeriesViewer()).getSelectedImagePane());
    }
  }

  @Override
  protected void changeToolWindowAnchor(CLocation clocation) {
    // TODO Auto-generated method stub
  }
}
