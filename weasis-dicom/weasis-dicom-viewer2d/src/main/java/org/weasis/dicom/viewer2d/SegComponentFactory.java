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

import eu.essilab.lablib.checkboxtree.CheckboxTree;
import eu.essilab.lablib.checkboxtree.TreeCheckingModel;
import java.awt.Component;
import java.awt.Dimension;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.swing.JButton;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import org.dcm4che3.data.Tag;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.media.data.MediaSeries.MEDIA_POSITION;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.api.util.ResourceUtil;
import org.weasis.core.api.util.ResourceUtil.OtherIcon;
import org.weasis.core.ui.editor.SeriesViewerEvent;
import org.weasis.core.ui.editor.SeriesViewerEvent.EVENT;
import org.weasis.core.ui.editor.image.ImageViewerPlugin;
import org.weasis.core.ui.editor.image.ViewButton;
import org.weasis.core.ui.editor.image.ViewCanvas;
import org.weasis.core.ui.util.TitleMenuItem;
import org.weasis.core.ui.util.TreeBuilder;
import org.weasis.core.util.StringUtil;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.codec.HiddenSeriesManager;
import org.weasis.dicom.codec.SpecialElementRegion;
import org.weasis.dicom.codec.TagD;
import org.weasis.dicom.codec.seg.SegSpecialElement;
import org.weasis.dicom.viewer2d.mpr.MprController;
import org.weasis.dicom.viewer2d.mpr.MprView;

/** Builds the segmentation selection button displayed in the corner of a 2D view. */
public final class SegComponentFactory {

  private SegComponentFactory() {}

  /** Returns every SEG element that can be rendered on the given view. */
  public static List<SegSpecialElement> getRelatedSegments(ViewCanvas<?> viewCanvas) {
    return getRelatedSegments(SegSpecialElement.class, viewCanvas);
  }

  /**
   * Returns every region element of the given type that can be rendered on the given view: the
   * elements referencing the displayed series plus the patient's elements that can be overlaid
   * spatially on it (same frame of reference) although they reference another series.
   */
  public static <E extends SpecialElementRegion> List<E> getRelatedSegments(
      Class<E> clazz, ViewCanvas<?> viewCanvas) {
    if (viewCanvas == null) {
      return List.of();
    }
    if (viewCanvas instanceof MprView mprView) {
      return getMprRelatedSegments(clazz, mprView);
    }
    return getRelatedSegments(clazz, viewCanvas.getSeries());
  }

  /**
   * Returns every region element of the given type that can be rendered on images of the given
   * series — see {@link #getRelatedSegments(Class, ViewCanvas)}.
   */
  public static <E extends SpecialElementRegion> List<E> getRelatedSegments(
      Class<E> clazz, MediaSeries<?> dcmSeries) {
    String seriesUID = TagD.getTagValue(dcmSeries, Tag.SeriesInstanceUID, String.class);
    if (!StringUtil.hasText(seriesUID)) {
      return List.of();
    }
    Set<E> segs = new LinkedHashSet<>();
    Set<String> list = HiddenSeriesManager.getInstance().reference2Series.get(seriesUID);
    if (list != null && !list.isEmpty()) {
      segs.addAll(
          HiddenSeriesManager.getHiddenElementsFromSeries(clazz, list.toArray(new String[0])));
    }
    Object media = dcmSeries.getMedia(MEDIA_POSITION.FIRST, null, null);
    if (media instanceof DicomImageElement img) {
      String patientPseudoUID = (String) img.getTagValue(TagW.PatientPseudoUID);
      if (StringUtil.hasText(patientPseudoUID)) {
        HiddenSeriesManager.getHiddenElementsFromPatient(clazz, patientPseudoUID).stream()
            .filter(seg -> seg.matchesSeries(dcmSeries))
            .forEach(segs::add);
      }
    }
    return List.copyOf(segs);
  }

  /**
   * For MPR views the current series is a synthetic MPR series whose UID is never registered in
   * reference2Series. If the async seg build has already completed, return those elements directly;
   * otherwise fall back to the original source series UID so that the reference2Series lookup still
   * works.
   */
  private static <E extends SpecialElementRegion> List<E> getMprRelatedSegments(
      Class<E> clazz, MprView mprView) {
    MprController ctrl = mprView.getMprController();
    if (ctrl == null) {
      return List.of();
    }
    List<E> elements =
        ctrl.getSegElements().stream().filter(clazz::isInstance).map(clazz::cast).toList();
    if (!elements.isEmpty()) {
      return elements;
    }
    var vol = ctrl.getVolume();
    if (vol != null) {
      String seriesUID =
          TagD.getTagValue(vol.getStack().getSeries(), Tag.SeriesInstanceUID, String.class);
      if (StringUtil.hasText(seriesUID)) {
        Set<String> list = HiddenSeriesManager.getInstance().reference2Series.get(seriesUID);
        if (list != null && !list.isEmpty()) {
          return HiddenSeriesManager.getHiddenElementsFromSeries(
              clazz, list.toArray(new String[0]));
        }
      }
    }
    return List.of();
  }

  public static ViewButton buildSegSelectionButton(View2d view) {
    return new ViewButton(
        (invoker, x, y) -> new SegSelectionPopup(view).show(invoker, x, y),
        ResourceUtil.getIcon(OtherIcon.SEGMENTATION).derive(24, 24),
        Messages.getString("segmentation"));
  }

  /** Popup letting the user check the segmentations to display on the view. */
  private static final class SegSelectionPopup {
    private final View2d view;
    private final CheckboxTree tree = new CheckboxTree();
    private final Map<DefaultMutableTreeNode, SegSpecialElement> nodeMap = new LinkedHashMap<>();
    private boolean updating;

    SegSelectionPopup(View2d view) {
      this.view = view;
    }

    void show(Component invoker, int x, int y) {
      List<SegSpecialElement> segs = getRelatedSegments(view);
      if (segs.isEmpty()) {
        return;
      }
      buildTree(segs);

      JPopupMenu popupMenu = new JPopupMenu();
      popupMenu.add(new TitleMenuItem(Messages.getString("segmentation")));
      popupMenu.addSeparator();

      JScrollPane scrollPane = new JScrollPane(tree);
      Dimension treeSize = tree.getPreferredSize();
      int width = Math.clamp(treeSize.width + GuiUtils.getScaleLength(30), 250, 500);
      int height = Math.min(treeSize.height + GuiUtils.getScaleLength(10), 300);
      scrollPane.setPreferredSize(new Dimension(width, height));
      popupMenu.add(scrollPane);

      JButton showAll = new JButton(Messages.getString("show.all"));
      showAll.addActionListener(_ -> setAllChecked(true));
      JButton hideAll = new JButton(Messages.getString("hide.all"));
      hideAll.addActionListener(_ -> setAllChecked(false));
      popupMenu.add(GuiUtils.getFlowLayoutPanel(showAll, hideAll));
      popupMenu.show(invoker, x, y);
    }

    private void buildTree(List<SegSpecialElement> segs) {
      DefaultMutableTreeNode root = new DefaultMutableTreeNode("rootNode", true); // NON-NLS
      for (SegSpecialElement seg : segs) {
        DefaultMutableTreeNode node = new DefaultMutableTreeNode(seg.getShortLabel(), false);
        nodeMap.put(node, seg);
        root.add(node);
      }
      tree.getCheckingModel().setCheckingMode(TreeCheckingModel.CheckingMode.SIMPLE);
      tree.setModel(new DefaultTreeModel(root, false));
      tree.setRootVisible(false);
      tree.setShowsRootHandles(true);
      tree.setCellRenderer(TreeBuilder.buildNoIconCheckboxTreeCellRenderer());
      updating = true;
      try {
        nodeMap.forEach(
            (node, seg) ->
                TreeBuilder.setPathSelection(tree, new TreePath(node.getPath()), seg.isVisible()));
      } finally {
        updating = false;
      }
      tree.addTreeCheckingListener(_ -> applySelection());
    }

    private void setAllChecked(boolean checked) {
      updating = true;
      try {
        for (DefaultMutableTreeNode node : nodeMap.keySet()) {
          TreeBuilder.setPathSelection(tree, new TreePath(node.getPath()), checked);
        }
      } finally {
        updating = false;
      }
      applySelection();
    }

    private void applySelection() {
      if (updating) {
        return;
      }
      nodeMap.forEach(
          (node, seg) ->
              seg.setVisible(tree.getCheckingModel().isPathChecked(new TreePath(node.getPath()))));
      refreshViews();
    }

    private void refreshViews() {
      ImageViewerPlugin<DicomImageElement> container =
          EventManager.getInstance().getSelectedView2dContainer();
      if (container == null) {
        return;
      }
      for (ViewCanvas<DicomImageElement> v : container.getImagePanels()) {
        if (v instanceof View2d view2d) {
          view2d.updateSegmentation();
          view2d.repaint();
        }
      }
      // Keep the Segmentation tool tree in sync with the new visibility states
      EventManager.getInstance()
          .fireSeriesViewerListeners(
              new SeriesViewerEvent(
                  container, view.getSeries(), view.getImage(), EVENT.SELECT_VIEW));
    }
  }
}
