/*
 * Copyright (c) 2010 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.viewer3d.dockable;

import bibliothek.gui.dock.common.CLocation;
import eu.essilab.lablib.checkboxtree.CheckboxTree;
import eu.essilab.lablib.checkboxtree.TreeCheckingEvent;
import eu.essilab.lablib.checkboxtree.TreeCheckingModel;
import eu.essilab.lablib.checkboxtree.TreeCheckingModel.CheckingMode;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import javax.swing.JCheckBox;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import org.weasis.core.api.gui.Insertable;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.media.data.Series;
import org.weasis.core.api.util.ResourceUtil;
import org.weasis.core.api.util.ResourceUtil.OtherIcon;
import org.weasis.core.ui.docking.PluginTool;
import org.weasis.core.ui.editor.SeriesViewerEvent;
import org.weasis.core.ui.editor.SeriesViewerEvent.EVENT;
import org.weasis.core.ui.editor.SeriesViewerListener;
import org.weasis.core.ui.editor.image.ImageViewerPlugin;
import org.weasis.core.ui.editor.image.ViewCanvas;
import org.weasis.core.ui.editor.image.ViewerPlugin;
import org.weasis.core.ui.model.layer.AbstractInfoLayer;
import org.weasis.core.ui.model.layer.LayerAnnotation;
import org.weasis.core.ui.model.layer.LayerItem;
import org.weasis.core.ui.util.TreeBuilder;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.viewer3d.EventManager;
import org.weasis.dicom.viewer3d.Messages;
import org.weasis.dicom.viewer3d.View3DContainer;

public class DisplayTool extends PluginTool implements SeriesViewerListener {

  public static final String DICOM_ANNOTATIONS = Messages.getString("dicom.annotations");

  public static final String BUTTON_NAME = Messages.getString("display");

  private final JCheckBox applyAllViews =
      new JCheckBox(Messages.getString("apply.all.views"), true);
  private final CheckboxTree tree;
  private boolean initPathSelection;
  private final DefaultMutableTreeNode rootNode = new DefaultMutableTreeNode("rootNode", true);

  private DefaultMutableTreeNode dicomInfo;
  //  private DefaultMutableTreeNode drawings;

  private DefaultMutableTreeNode minAnnotations;

  //  private DefaultMutableTreeNode orientationCube;

  public DisplayTool(String pluginName) {
    super(pluginName, Insertable.Type.TOOL, 10);
    dockable.setTitleIcon(ResourceUtil.getIcon(OtherIcon.VIEW_SETTING));
    setDockableWidth(230);

    tree = new CheckboxTree();
    initPathSelection = false;
    setLayout(new BorderLayout(0, 0));
    iniTree();
  }

  public void iniTree() {
    tree.getCheckingModel().setCheckingMode(CheckingMode.SIMPLE);

    dicomInfo = new DefaultMutableTreeNode(DICOM_ANNOTATIONS, true);
    dicomInfo.add(new DefaultMutableTreeNode(LayerItem.ANNOTATIONS, true));
    minAnnotations = new DefaultMutableTreeNode(LayerItem.MIN_ANNOTATIONS, false);
    dicomInfo.add(minAnnotations);
    dicomInfo.add(new DefaultMutableTreeNode(LayerItem.ANONYM_ANNOTATIONS, false));
    //    dicomInfo.add(new DefaultMutableTreeNode(LayerItem.SCALE, true));
    //    dicomInfo.add(new DefaultMutableTreeNode(LayerItem.LUT, true));
    //    dicomInfo.add(new DefaultMutableTreeNode(LayerItem.IMAGE_ORIENTATION, true));
    dicomInfo.add(new DefaultMutableTreeNode(LayerItem.WINDOW_LEVEL, true));
    dicomInfo.add(new DefaultMutableTreeNode(LayerItem.ZOOM, true));
    //    dicomInfo.add(new DefaultMutableTreeNode(LayerItem.ROTATION, true));
    //    dicomInfo.add(new DefaultMutableTreeNode(LayerItem.PIXEL, true));
    rootNode.add(dicomInfo);
    //    drawings = new DefaultMutableTreeNode(ActionW.DRAWINGS, true);
    //    rootNode.add(drawings);

    //    orientationCube = new DefaultMutableTreeNode(ActionVol.ORIENTATION_CUBE, false);
    //    rootNode.add(orientationCube);

    DefaultTreeModel model = new DefaultTreeModel(rootNode, false);
    tree.setModel(model);
    TreePath rootPath = new TreePath(rootNode.getPath());
    tree.addCheckingPath(rootPath);

    tree.setShowsRootHandles(true);
    tree.setRootVisible(false);
    tree.setExpandsSelectedPaths(true);
    tree.setCellRenderer(TreeBuilder.buildNoIconCheckboxTreeCellRenderer());
    tree.addTreeCheckingListener(this::treeValueChanged);

    JPanel panel = new JPanel();
    FlowLayout flowLayout = (FlowLayout) panel.getLayout();
    flowLayout.setAlignment(FlowLayout.LEFT);
    add(panel, BorderLayout.NORTH);
    applyAllViews.setSelected(true);
    applyAllViews.addActionListener(
        e -> {
          List<ViewCanvas<?>> views = getViews(true);
          if (applyAllViews.isSelected()) {
            views.forEach(v -> v.getJComponent().repaint());
          } else {
            views.forEach(
                v -> {
                  LayerAnnotation layer = v.getInfoLayer();
                  if (layer != null) {
                    layer.resetToDefault();
                  }
                });
          }
        });
    panel.add(applyAllViews);

    TreeBuilder.expandTree(tree, rootNode);
    add(new JScrollPane(tree), BorderLayout.CENTER);
  }

  private void treeValueChanged(TreeCheckingEvent e) {
    if (!initPathSelection) {
      TreePath path = e.getPath();
      boolean selected = e.isCheckedPath();
      Object selObject = path.getLastPathComponent();
      Object parent = null;
      if (path.getParentPath() != null) {
        parent = path.getParentPath().getLastPathComponent();
      }

      List<ViewCanvas<?>> views = getViews(applyAllViews.isSelected());
      if (!views.isEmpty()) {
        if (rootNode.equals(parent)) {
          if (dicomInfo.equals(selObject)) {
            for (ViewCanvas<?> v : views) {
              LayerAnnotation layer = v.getInfoLayer();
              if (layer != null && selected != Boolean.TRUE.equals(v.getInfoLayer().getVisible())) {
                v.getInfoLayer().setVisible(selected);
                v.getJComponent().repaint();
              }
            }
            //          } else if (drawings.equals(selObject)) {
            //            for (ViewCanvas<DicomImageElement> v : views) {
            //              v.setDrawingsVisibility(selected);
            //            }
            //          } else if (orientationCube.equals(selObject)) {
            //            for (ViewCanvas<DicomImageElement> v : views) {
            //              v.setActionsInView(ActionVol.ORIENTATION_CUBE.cmd(), selected, true);
            //            }
          }
        } else if (dicomInfo.equals(parent)) {
          if (selObject instanceof DefaultMutableTreeNode node
              && node.getUserObject() instanceof LayerItem item) {
            for (ViewCanvas<?> v : views) {
              LayerAnnotation layer = v.getInfoLayer();
              if (layer != null && layer.setDisplayPreferencesValue(item, selected)) {
                v.getJComponent().repaint();
              }
            }
            if (LayerItem.ANONYM_ANNOTATIONS.equals(item)) {
              // Send message to listeners, only selected view
              ImageViewerPlugin<DicomImageElement> container =
                  EventManager.getInstance().getSelectedView2dContainer();
              ViewCanvas<DicomImageElement> v = container.getSelectedImagePane();
              Series<?> series = (Series<?>) v.getSeries();
              EventManager.getInstance()
                  .fireSeriesViewerListeners(
                      new SeriesViewerEvent(container, series, v.getImage(), EVENT.ANONYM));
            }
          }
        }
      }
    }
  }

  public void iniTreeValues(ViewCanvas<?> view) {
    if (view != null) {
      initPathSelection = true;

      // Annotations node
      LayerAnnotation layer = view.getInfoLayer();
      if (layer != null) {
        TreeBuilder.setPathSelection(tree, getTreePath(dicomInfo), layer.getVisible());
        Enumeration<?> en = dicomInfo.children();
        while (en.hasMoreElements()) {
          Object node = en.nextElement();
          if (node instanceof DefaultMutableTreeNode checkNode
              && checkNode.getUserObject() instanceof LayerItem item) {
            boolean sel =
                applyAllViews.isSelected()
                    ? AbstractInfoLayer.getDefaultDisplayPreferences()
                        .getOrDefault(item, Boolean.FALSE)
                    : layer.getDisplayPreferences(item);
            TreeBuilder.setPathSelection(tree, getTreePath(checkNode), sel);
          }
        }
        // Minimal annotation is the default mode
        TreeBuilder.setPathSelection(tree, getTreePath(minAnnotations), true);
      }

      initLayers(view);

      initPathSelection = false;
    }
  }

  private void initLayers(ViewCanvas<?> view) {
    //    TreeBuilder.setPathSelection(tree,
    //        getTreePath(drawings),
    //        LangUtil.getNULLtoTrue((Boolean) view.getActionValue(ActionW.DRAWINGS.cmd())));
    //
    //    // FIXME store in pref
    //    TreeBuilder.setPathSelection(tree,
    //        getTreePath(orientationCube),
    //        LangUtil.getNULLtoFalse((Boolean)
    // view.getActionValue(ActionVol.ORIENTATION_CUBE.cmd())));
  }

  private static TreePath getTreePath(TreeNode node) {
    List<TreeNode> list = new ArrayList<>();
    list.add(node);
    TreeNode parent = node;
    while (parent.getParent() != null) {
      parent = parent.getParent();
      list.add(parent);
    }
    Collections.reverse(list);
    return new TreePath(list.toArray(new TreeNode[0]));
  }

  @Override
  public Component getToolComponent() {
    return this;
  }

  @Override
  protected void changeToolWindowAnchor(CLocation clocation) {
    // Do nothing
  }

  @Override
  public void changingViewContentEvent(SeriesViewerEvent event) {
    // TODO should recieved layer changes
    EVENT e = event.getEventType();
    if (EVENT.SELECT_VIEW.equals(e) && event.getSeriesViewer() instanceof ImageViewerPlugin) {
      iniTreeValues(((ImageViewerPlugin<?>) event.getSeriesViewer()).getSelectedImagePane());
    } else if (EVENT.TOGGLE_INFO.equals(e)) {
      TreeCheckingModel model = tree.getCheckingModel();
      TreePath path = new TreePath(dicomInfo.getPath());

      boolean checked = model.isPathChecked(path);
      ViewCanvas<DicomImageElement> selView = EventManager.getInstance().getSelectedViewPane();
      // Use an intermediate state of the minimal DICOM information. Triggered only from the
      // shortcut SPACE or I
      boolean minDisp =
          selView != null
              && selView.getInfoLayer().getDisplayPreferences(LayerItem.MIN_ANNOTATIONS);

      if (checked && !minDisp) {
        for (ViewCanvas<?> v : getViews(applyAllViews.isSelected())) {
          LayerAnnotation layer = v.getInfoLayer();
          if (layer != null) {
            layer.setVisible(true);
            if (layer.setDisplayPreferencesValue(LayerItem.MIN_ANNOTATIONS, true)) {
              v.getJComponent().repaint();
            }
          }
        }
        model.addCheckingPath(new TreePath(minAnnotations.getPath()));
      } else if (checked) {
        model.removeCheckingPath(path);
        model.removeCheckingPath(new TreePath(minAnnotations.getPath()));
      } else {
        model.addCheckingPath(path);
        model.removeCheckingPath(new TreePath(minAnnotations.getPath()));
      }
    }
  }

  private List<ViewCanvas<?>> getViews(boolean allVisible) {
    List<ViewCanvas<?>> views;
    if (allVisible) {
      views = new ArrayList<>();
      List<ViewerPlugin<?>> viewerPlugins = GuiUtils.getUICore().getViewerPlugins();
      synchronized (viewerPlugins) {
        for (final ViewerPlugin<?> p : viewerPlugins) {
          if (p instanceof View3DContainer plugin && plugin.getDockable().isShowing()) {
            views.addAll(plugin.getImagePanels());
          }
        }
      }
    } else {
      views = new ArrayList<>(1);
      ViewCanvas<?> view = EventManager.getInstance().getSelectedViewPane();
      if (view != null) {
        views.add(view);
      }
    }
    return views;
  }
}
