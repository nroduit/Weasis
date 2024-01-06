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
import it.cnr.imaa.essi.lablib.gui.checkboxtree.CheckboxTree;
import it.cnr.imaa.essi.lablib.gui.checkboxtree.TreeCheckingEvent;
import it.cnr.imaa.essi.lablib.gui.checkboxtree.TreeCheckingModel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Collection;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javax.swing.BorderFactory;
import javax.swing.JComboBox;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import net.miginfocom.swing.MigLayout;
import org.dcm4che3.data.Tag;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.gui.util.JSliderW;
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
import org.weasis.core.ui.util.CheckBoxTreeBuilder;
import org.weasis.core.util.StringUtil;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.codec.DicomSeries;
import org.weasis.dicom.codec.HiddenSeriesManager;
import org.weasis.dicom.codec.HiddenSpecialElement;
import org.weasis.dicom.codec.SegSpecialElement;
import org.weasis.dicom.codec.TagD;
import org.weasis.dicom.codec.seg.EditableContour;
import org.weasis.dicom.viewer2d.EventManager;
import org.weasis.dicom.viewer2d.View2d;
import org.weasis.opencv.data.PlanarImage;
import org.weasis.opencv.op.ImageProcessor;

/**
 * @author Nicolas Roduit
 */
public class SegmentationTool extends PluginTool implements SeriesViewerListener {

  public static final String BUTTON_NAME = "Segmentation";
  private static final String GRAPHIC_OPACITY = "Graphic Opacity";
  ;
  private final JScrollPane rootPane;

  private final CheckboxTree tree;
  private boolean initPathSelection;
  private final DefaultMutableTreeNode rootNodeStructures =
      new DefaultMutableTreeNode("rootNode", true); // NON-NLS
  private final JComboBox<SegSpecialElement> comboSeg = new JComboBox<>();

  private final DefaultMutableTreeNode nodeStructures;
  private final transient ItemListener structureChangeListener =
      e -> {
        if (e.getStateChange() == ItemEvent.SELECTED) {
          updateTree((SegSpecialElement) e.getItem());
        }
      };
  private final JSliderW slider;
  private final JPopupMenu popupMenu = new JPopupMenu();

  public SegmentationTool() {
    super(BUTTON_NAME, Type.TOOL, 30);
    this.setLayout(new BorderLayout(0, 0));
    this.rootPane = new JScrollPane();
    this.dockable.setTitleIcon(ResourceUtil.getIcon(OtherIcon.IMAGE_PRESENTATION));
    this.setDockableWidth(350);
    rootPane.setBorder(BorderFactory.createEmptyBorder()); // remove default line
    this.slider = PropertiesDialog.createOpacitySlider(GRAPHIC_OPACITY);
    slider.setValue(80);
    PropertiesDialog.updateSlider(slider, GRAPHIC_OPACITY);
    slider.addChangeListener(
        l -> {
          updateSlider();
        });
    this.tree =
        new CheckboxTree() {

          @Override
          public String getToolTipText(MouseEvent evt) {
            TreePath curPath = getPathForLocation(evt.getX(), evt.getY());
            return getSegItemToolTipText(curPath);
          }
        };
    tree.setToolTipText(StringUtil.EMPTY_STRING);
    tree.setCellRenderer(CheckBoxTreeBuilder.buildNoIconCheckboxTreeCellRenderer());

    this.nodeStructures = new DefaultMutableTreeNode("List of regions", true);
    this.initData();

    initListeners();
  }

  private void initListeners() {
    comboSeg.addItemListener(structureChangeListener);
    tree.addMouseListener(
        new MouseAdapter() {
          @Override
          public void mousePressed(MouseEvent e) {
            popupMenu.removeAll();
            if (SwingUtilities.isRightMouseButton(e)) {
              DefaultMutableTreeNode node = getTreeNode(e.getPoint());
              boolean visible = node != null && !node.isLeaf();
              if (visible) {
                popupMenu.add(getCheckAllMenuItem(node, true));
                popupMenu.add(getCheckAllMenuItem(node, false));
              }
              popupMenu.add(getOpacityMenuItem(node, e.getPoint()));
              popupMenu.add(getStatisticMenuItem(node));
              popupMenu.show(tree, e.getX(), e.getY());
            }
          }
        });
  }

  private JMenuItem getOpacityMenuItem(DefaultMutableTreeNode node, Point pt) {
    JMenuItem jMenuItem = new JMenuItem(PropertiesDialog.FILL_OPACITY);
    jMenuItem.addActionListener(_ -> showSliderInPopup(node, pt));
    return jMenuItem;
  }

  private void showSliderInPopup(DefaultMutableTreeNode node, Point pt) {
    if (node != null && node.isLeaf() && node.getUserObject() instanceof EditableContour contour) {
      // Create a popup menu
      JPopupMenu menu = new JPopupMenu();
      JSliderW jSlider = PropertiesDialog.createOpacitySlider(PropertiesDialog.FILL_OPACITY);
      GuiUtils.setPreferredWidth(jSlider, 250);
      jSlider.setValue((int) (contour.getAttributes().getInteriorOpacity() * 100f));
      PropertiesDialog.updateSlider(jSlider, PropertiesDialog.FILL_OPACITY);
      jSlider.addChangeListener(
          l -> {
            float value = PropertiesDialog.updateSlider(jSlider, PropertiesDialog.FILL_OPACITY);
            contour.getAttributes().setInteriorOpacity(value);
            updateVisibleNode();
          });
      menu.add(jSlider);
      menu.show(tree, pt.x, pt.y);
    }
  }

  private JMenuItem getStatisticMenuItem(DefaultMutableTreeNode node) {
    JMenuItem selectAllMenuItem = new JMenuItem("Compute statistics");
    selectAllMenuItem.addActionListener(
        e -> {
          if (node != null) {
            if (node.isLeaf() && node.getUserObject() instanceof EditableContour contour) {
              ViewCanvas<DicomImageElement> view = EventManager.getInstance().getSelectedViewPane();
              if (view != null && view.getImage() instanceof DicomImageElement imageElement) {
                PlanarImage img = imageElement.getImage();
                if (img != null) {
                  String seriesUID =
                      TagD.getTagValue(imageElement, Tag.SeriesInstanceUID, String.class);
                  List<String> seriesList =
                      HiddenSeriesManager.getInstance().reference2Series.get(seriesUID);
                  for (String s : seriesList) {
                    List<HiddenSpecialElement> els =
                        HiddenSeriesManager.getInstance().series2Elements.get(s);
                    for (HiddenSpecialElement el : els) {
                      if (el instanceof SegSpecialElement seg) {
                        Collection<EditableContour> segments = seg.getContours(imageElement);
                        if (segments != null) {
                          for (EditableContour c : segments) {
                            if (c.getCategory().equals(contour.getCategory())) {
                              double[][] result =
                                  ImageProcessor.meanStdDev(
                                      img.toMat(), c.getNonEditableGraphic().getShape());
                              System.out.println(
                                  "Mean: " + result[0][0] + " StdDev: " + result[1][0]);
                              break;
                            }
                          }
                        }
                      }
                    }
                  }
                }
              }
            }
          }
        });
    return selectAllMenuItem;
  }

  private JMenuItem getCheckAllMenuItem(DefaultMutableTreeNode node, boolean selected) {
    JMenuItem selectAllMenuItem =
        new JMenuItem(selected ? "Select all the child nodes" : "Unselect all the child nodes");
    selectAllMenuItem.addActionListener(
        e -> {
          if (node != null) {
            Enumeration<?> children = node.children();
            while (children.hasMoreElements()) {
              Object child = children.nextElement();
              if (child instanceof DefaultMutableTreeNode dtm) {
                TreePath tp = new TreePath(dtm.getPath());
                if (selected) {
                  tree.getCheckingModel().addCheckingPath(tp);
                } else {
                  tree.getCheckingModel().removeCheckingPath(tp);
                }
              }
            }
          }
        });
    return selectAllMenuItem;
  }

  private DefaultMutableTreeNode getTreeNode(Point mousePosition) {
    TreePath treePath = tree.getPathForLocation(mousePosition.x, mousePosition.y);
    if (treePath != null) {
      Object userObject = treePath.getLastPathComponent();
      if (userObject instanceof DefaultMutableTreeNode) {
        return (DefaultMutableTreeNode) userObject;
      }
    }
    return null;
  }

  private String getSegItemToolTipText(TreePath curPath) {
    if (curPath != null) {
      Object object = curPath.getLastPathComponent();
      if (object instanceof StructToolTipTreeNode treeNode) {
        return treeNode.getToolTipText();
      }
    }
    return null;
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
    tree.setCellRenderer(CheckBoxTreeBuilder.buildNoIconCheckboxTreeCellRenderer());
    tree.addTreeCheckingListener(this::treeValueChanged);

    expandTree(tree, rootNodeStructures);
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

  private void updateVisibleNode(DefaultMutableTreeNode start, boolean all) {
    for (Enumeration<TreeNode> children = start.children(); children.hasMoreElements(); ) {
      DefaultMutableTreeNode dtm = (DefaultMutableTreeNode) children.nextElement();
      if (dtm.isLeaf()) {
        TreePath tp = new TreePath(dtm.getPath());
        DefaultMutableTreeNode node = (DefaultMutableTreeNode) tp.getLastPathComponent();
        if (node.getUserObject() instanceof EditableContour contour) {
          contour.getAttributes().setVisible(tree.getCheckingModel().isPathChecked(tp));
        }
      } else {
        updateVisibleNode(dtm, all);
      }
    }
  }

  private void updateVisibleNode() {
    boolean all = tree.getCheckingModel().isPathChecked(new TreePath(nodeStructures.getPath()));
    SegSpecialElement seg = (SegSpecialElement) comboSeg.getSelectedItem();
    if (seg != null) {
      seg.setVisible(all);
    }
    updateVisibleNode(rootNodeStructures, all);

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
      Map<Integer, EditableContour> segments = specialElement.getSegAttributes();
      if (segments != null) {
        for (EditableContour contour : segments.values()) {
          DefaultMutableTreeNode node = new StructToolTipTreeNode(contour, false);
          nodeStructures.add(node);
          initPathSelection(new TreePath(node.getPath()), contour.getAttributes().isVisible());
        }
      }
      initPathSelection(new TreePath(nodeStructures.getPath()), specialElement.isVisible());

      // Expand
      expandTree(tree, rootNodeStructures);
    } finally {
      initPathSelection = false;
    }
  }

  private void initPathSelection(TreePath path, boolean selected) {
    if (selected) {
      tree.addCheckingPath(path);
    } else {
      tree.removeCheckingPath(path);
    }
  }

  public void initTreeValues(ViewCanvas<?> viewCanvas) {
    List<SegSpecialElement> segList = null;
    if (viewCanvas != null) {
      MediaSeries<?> dcmSeries = viewCanvas.getSeries();
      String seriesUID = TagD.getTagValue(dcmSeries, Tag.SeriesInstanceUID, String.class);
      if (StringUtil.hasText(seriesUID)) {
        List<String> list = HiddenSeriesManager.getInstance().reference2Series.get(seriesUID);
        if (list != null && !list.isEmpty()) {
          segList =
              DicomSeries.getHiddenElementsFromSeries(
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

  private static void expandTree(JTree tree, DefaultMutableTreeNode start) {
    for (Enumeration children = start.children(); children.hasMoreElements(); ) {
      DefaultMutableTreeNode dtm = (DefaultMutableTreeNode) children.nextElement();
      if (!dtm.isLeaf()) {
        TreePath tp = new TreePath(dtm.getPath());
        tree.expandPath(tp);
        expandTree(tree, dtm);
      }
    }
  }

  private static String getColorBullet(Color c, String label) {
    StringBuilder buf = new StringBuilder("<html><font color='rgb("); // NON-NLS
    buf.append(c.getRed());
    buf.append(",");
    buf.append(c.getGreen());
    buf.append(",");
    buf.append(c.getBlue());
    // Other square: u2B1B (unicode)
    buf.append(")'> █ </font>"); // NON-NLS
    buf.append(label);
    buf.append(GuiUtils.HTML_END);
    return buf.toString();
  }

  static class StructToolTipTreeNode extends DefaultMutableTreeNode {

    public StructToolTipTreeNode(EditableContour userObject, boolean allowsChildren) {
      super(Objects.requireNonNull(userObject), allowsChildren);
    }

    public String getToolTipText() {
      EditableContour seg = (EditableContour) getUserObject();
      StringBuilder buf = new StringBuilder();
      buf.append(GuiUtils.HTML_START);
      buf.append("Label");
      buf.append(StringUtil.COLON_AND_SPACE);
      buf.append(seg.getCategory().label());
      buf.append(GuiUtils.HTML_BR);
      buf.append("Algorithm type");
      buf.append(StringUtil.COLON_AND_SPACE);
      buf.append(seg.getCategory().type());
      buf.append(GuiUtils.HTML_BR);
      buf.append(GuiUtils.HTML_END);
      return buf.toString();
    }

    @Override
    public String toString() {
      EditableContour layer = (EditableContour) getUserObject();
      return getColorBullet(layer.getAttributes().getColor(), layer.getCategory().label());
    }
  }
}
