/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.base.viewer2d.dockable;

import bibliothek.gui.dock.common.CLocation;
import eu.essilab.lablib.checkboxtree.CheckboxTree;
import eu.essilab.lablib.checkboxtree.TreeCheckingEvent;
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
import org.weasis.base.viewer2d.EventManager;
import org.weasis.base.viewer2d.Messages;
import org.weasis.base.viewer2d.View2dContainer;
import org.weasis.core.api.gui.Insertable;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.api.media.data.Thumbnailable;
import org.weasis.core.api.util.ResourceUtil;
import org.weasis.core.api.util.ResourceUtil.OtherIcon;
import org.weasis.core.ui.docking.PluginTool;
import org.weasis.core.ui.editor.SeriesViewerEvent;
import org.weasis.core.ui.editor.SeriesViewerEvent.EVENT;
import org.weasis.core.ui.editor.SeriesViewerListener;
import org.weasis.core.ui.editor.image.ImageViewerPlugin;
import org.weasis.core.ui.editor.image.Panner;
import org.weasis.core.ui.editor.image.ViewCanvas;
import org.weasis.core.ui.editor.image.ViewerPlugin;
import org.weasis.core.ui.model.layer.LayerAnnotation;
import org.weasis.core.ui.model.layer.LayerItem;
import org.weasis.core.ui.util.TreeBuilder;

public class DisplayTool extends PluginTool implements SeriesViewerListener {

  public static final String BUTTON_NAME = Messages.getString("DisplayTool.display");

  private final JCheckBox applyAllViews =
      new JCheckBox(Messages.getString("apply.to.all.views"), true);
  private final CheckboxTree tree;
  private boolean initPathSelection;
  private final DefaultMutableTreeNode rootNode = new DefaultMutableTreeNode("rootNode", true);

  private DefaultMutableTreeNode image;
  private DefaultMutableTreeNode info;
  private DefaultMutableTreeNode drawings;
  private JPanel panelFoot;

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

    image = new DefaultMutableTreeNode(Messages.getString("DisplayTool.img"), true);
    rootNode.add(image);
    info = new DefaultMutableTreeNode(Messages.getString("DisplayTool.annotations"), true);
    info.add(new DefaultMutableTreeNode(LayerItem.ANNOTATIONS, true));
    info.add(new DefaultMutableTreeNode(LayerItem.SCALE, true));
    info.add(new DefaultMutableTreeNode(LayerItem.LUT, true));
    info.add(new DefaultMutableTreeNode(LayerItem.IMAGE_ORIENTATION, true));
    info.add(new DefaultMutableTreeNode(LayerItem.WINDOW_LEVEL, true));
    info.add(new DefaultMutableTreeNode(LayerItem.ZOOM, true));
    info.add(new DefaultMutableTreeNode(LayerItem.ROTATION, true));
    info.add(new DefaultMutableTreeNode(LayerItem.FRAME, true));
    info.add(new DefaultMutableTreeNode(LayerItem.PIXEL, true));
    rootNode.add(info);
    drawings = new DefaultMutableTreeNode(ActionW.DRAWINGS, true);
    rootNode.add(drawings);

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

    panelFoot = new JPanel();
    add(panelFoot, BorderLayout.SOUTH);
  }

  public void iniTreeValues(ViewCanvas<?> view) {
    if (view != null) {
      initPathSelection = true;
      // Image node
      TreeBuilder.setPathSelection(tree, getTreePath(image), view.getImageLayer().getVisible());

      // Annotations node
      LayerAnnotation layer = view.getInfoLayer();
      if (layer != null) {
        TreeBuilder.setPathSelection(tree, getTreePath(info), layer.getVisible());
        Enumeration<?> en = info.children();
        while (en.hasMoreElements()) {
          Object node = en.nextElement();
          if (node instanceof DefaultMutableTreeNode checkNode
              && checkNode.getUserObject() instanceof LayerItem item) {
            TreeBuilder.setPathSelection(
                tree, getTreePath(checkNode), layer.getDisplayPreferences(item));
          }
        }
      }

      ImageElement img = view.getImage();
      if (img != null) {
        Panner<?> panner = view.getPanner();
        if (panner != null) {
          int cps = panelFoot.getComponentCount();
          if (cps > 0) {
            Component cp = panelFoot.getComponent(0);
            if (cp != panner) {
              if (cp instanceof Thumbnailable thumbnailable) {
                thumbnailable.removeMouseAndKeyListener();
              }
              panner.registerListeners();
              panelFoot.removeAll();
              panelFoot.add(panner);
              panner.revalidate();
              panner.repaint();
            }
          } else {
            panner.registerListeners();
            panelFoot.add(panner);
          }
        }
      }

      initPathSelection = false;
    }
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
      if (views.isEmpty()) {
        return;
      }

      if (rootNode.equals(parent)) {
        if (image.equals(selObject)) {
          for (ViewCanvas<?> v : views) {
            if (selected != v.getImageLayer().getVisible()) {
              v.getImageLayer().setVisible(selected);
              v.getJComponent().repaint();
            }
          }
        } else if (info.equals(selObject)) {
          for (ViewCanvas<?> v : views) {
            if (selected != v.getInfoLayer().getVisible()) {
              v.getInfoLayer().setVisible(selected);
              v.getJComponent().repaint();
            }
          }
        } else if (drawings.equals(selObject)) {
          for (ViewCanvas<?> v : views) {
            v.setDrawingsVisibility(selected);
          }
        }

      } else if (info.equals(parent)
          && selObject instanceof DefaultMutableTreeNode node
          && node.getUserObject() instanceof LayerItem item) {
        for (ViewCanvas<?> v : views) {
          LayerAnnotation layer = v.getInfoLayer();
          if (layer != null && layer.setDisplayPreferencesValue(item, selected)) {
            v.getJComponent().repaint();
          }
        }
      }
    }
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
    // Do noting
  }

  @Override
  public void changingViewContentEvent(SeriesViewerEvent event) {
    EVENT e = event.getEventType();
    if (EVENT.SELECT_VIEW.equals(e)
        && event.getSeriesViewer() instanceof View2dContainer container) {
      iniTreeValues(container.getSelectedImagePane());
    }
  }

  private List<ViewCanvas<?>> getViews(boolean allVisible) {
    List<ViewCanvas<?>> views;
    if (allVisible) {
      views = new ArrayList<>();
      List<ViewerPlugin<?>> viewerPlugins = GuiUtils.getUICore().getViewerPlugins();
      synchronized (viewerPlugins) {
        for (final ViewerPlugin<?> p : viewerPlugins) {
          if (p instanceof ImageViewerPlugin<?> plugin && plugin.getDockable().isShowing()) {
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
