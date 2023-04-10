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
import it.cnr.imaa.essi.lablib.gui.checkboxtree.CheckboxTree;
import it.cnr.imaa.essi.lablib.gui.checkboxtree.TreeCheckingEvent;
import it.cnr.imaa.essi.lablib.gui.checkboxtree.TreeCheckingModel;
import it.cnr.imaa.essi.lablib.gui.checkboxtree.TreeCheckingModel.CheckingMode;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Optional;
import javax.swing.JCheckBox;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTree;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import org.weasis.core.api.gui.Insertable;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.media.data.Series;
import org.weasis.core.api.util.ResourceUtil;
import org.weasis.core.api.util.ResourceUtil.OtherIcon;
import org.weasis.core.ui.docking.PluginTool;
import org.weasis.core.ui.docking.UIManager;
import org.weasis.core.ui.editor.SeriesViewerEvent;
import org.weasis.core.ui.editor.SeriesViewerEvent.EVENT;
import org.weasis.core.ui.editor.SeriesViewerListener;
import org.weasis.core.ui.editor.image.ImageViewerPlugin;
import org.weasis.core.ui.editor.image.ViewCanvas;
import org.weasis.core.ui.editor.image.ViewerPlugin;
import org.weasis.core.ui.model.layer.AbstractInfoLayer;
import org.weasis.core.ui.model.layer.LayerAnnotation;
import org.weasis.core.ui.util.CheckBoxTreeBuilder;
import org.weasis.core.util.LangUtil;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.viewer3d.ActionVol;
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
  private DefaultMutableTreeNode drawings;

  private DefaultMutableTreeNode crosshairMode;

  private DefaultMutableTreeNode crosshairCenter;
  private DefaultMutableTreeNode minAnnotations;
  private DefaultMutableTreeNode orientationCube;

  public DisplayTool(String pluginName) {
    super(BUTTON_NAME, pluginName, Insertable.Type.TOOL, 10);
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
    dicomInfo.add(new DefaultMutableTreeNode(LayerAnnotation.ANNOTATIONS, true));
    minAnnotations = new DefaultMutableTreeNode(LayerAnnotation.MIN_ANNOTATIONS, false);
    dicomInfo.add(minAnnotations);
    dicomInfo.add(new DefaultMutableTreeNode(LayerAnnotation.ANONYM_ANNOTATIONS, false));
    dicomInfo.add(new DefaultMutableTreeNode(LayerAnnotation.SCALE, true));
    dicomInfo.add(new DefaultMutableTreeNode(LayerAnnotation.LUT, true));
    dicomInfo.add(new DefaultMutableTreeNode(LayerAnnotation.IMAGE_ORIENTATION, true));
    dicomInfo.add(new DefaultMutableTreeNode(LayerAnnotation.WINDOW_LEVEL, true));
    dicomInfo.add(new DefaultMutableTreeNode(LayerAnnotation.ZOOM, true));
    dicomInfo.add(new DefaultMutableTreeNode(LayerAnnotation.ROTATION, true));
    dicomInfo.add(new DefaultMutableTreeNode(LayerAnnotation.PIXEL, true));
    rootNode.add(dicomInfo);
    drawings = new DefaultMutableTreeNode(ActionW.DRAWINGS, true);
    rootNode.add(drawings);

    orientationCube = new DefaultMutableTreeNode(ActionVol.ORIENTATION_CUBE, false);
    rootNode.add(orientationCube);
    crosshairMode = new DefaultMutableTreeNode(ActionVol.HIDE_CROSSHAIR_CENTER, false);
    rootNode.add(crosshairMode);
    crosshairCenter = new DefaultMutableTreeNode(ActionVol.RECENTERING_CROSSHAIR, false);
    rootNode.add(crosshairCenter);

    DefaultTreeModel model = new DefaultTreeModel(rootNode, false);
    tree.setModel(model);
    TreePath rootPath = new TreePath(rootNode.getPath());
    tree.addCheckingPath(rootPath);

    tree.setShowsRootHandles(true);
    tree.setRootVisible(false);
    tree.setExpandsSelectedPaths(true);
    tree.setCellRenderer(CheckBoxTreeBuilder.buildNoIconCheckboxTreeCellRenderer());
    tree.addTreeCheckingListener(this::treeValueChanged);

    JPanel panel = new JPanel();
    FlowLayout flowLayout = (FlowLayout) panel.getLayout();
    flowLayout.setAlignment(FlowLayout.LEFT);
    add(panel, BorderLayout.NORTH);
    applyAllViews.setSelected(AbstractInfoLayer.applyToAllView.get());
    applyAllViews.addActionListener(
        e -> {
          AbstractInfoLayer.applyToAllView.set(applyAllViews.isSelected());
          if (AbstractInfoLayer.applyToAllView.get()) {
            synchronized (UIManager.VIEWER_PLUGINS) {
              for (final ViewerPlugin<?> p : UIManager.VIEWER_PLUGINS) {
                if (p instanceof View3DContainer container) {
                  container.getImagePanels().forEach(v -> v.getJComponent().repaint());
                }
              }
            }
          } else {
            synchronized (UIManager.VIEWER_PLUGINS) {
              for (final ViewerPlugin<?> p : UIManager.VIEWER_PLUGINS) {
                if (p instanceof View3DContainer container) {
                  for (ViewCanvas<?> v : container.getImagePanels()) {
                    LayerAnnotation layer = v.getInfoLayer();
                    if (layer != null) {
                      layer.resetToDefault();
                    }
                  }
                }
              }
            }
          }
        });
    panel.add(applyAllViews);

    expandTree(tree, rootNode);
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

      List<ViewCanvas<DicomImageElement>> views = getViews();
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
          } else if (drawings.equals(selObject)) {
            for (ViewCanvas<DicomImageElement> v : views) {
              v.setDrawingsVisibility(selected);
            }
          } else if (crosshairMode.equals(selObject)) {
            for (ViewCanvas<DicomImageElement> v : views) {
              v.setActionsInView(ActionVol.HIDE_CROSSHAIR_CENTER.cmd(), selected, true);
            }
          } else if (crosshairCenter.equals(selObject)) {
            for (ViewCanvas<DicomImageElement> v : views) {
              v.setActionsInView(ActionVol.RECENTERING_CROSSHAIR.cmd(), selected, true);
            }
          } else if (orientationCube.equals(selObject)) {
            for (ViewCanvas<DicomImageElement> v : views) {
              v.setActionsInView(ActionVol.ORIENTATION_CUBE.cmd(), selected, true);
            }
          }
        } else if (dicomInfo.equals(parent)) {
          if (selObject != null) {
            for (ViewCanvas<DicomImageElement> v : views) {
              LayerAnnotation layer = v.getInfoLayer();
              if (layer != null
                  && Boolean.TRUE.equals(
                      layer.setDisplayPreferencesValue(selObject.toString(), selected))) {
                v.getJComponent().repaint();
              }
            }
            if (LayerAnnotation.ANONYM_ANNOTATIONS.equals(selObject.toString())) {
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

  private void initPathSelection(TreePath path, boolean selected) {
    if (selected) {
      tree.addCheckingPath(path);
    } else {
      tree.removeCheckingPath(path);
    }
  }

  public void iniTreeValues(ViewCanvas<?> view) {
    if (view != null) {
      initPathSelection = true;

      // Annotations node
      LayerAnnotation layer = view.getInfoLayer();
      if (layer != null) {
        initPathSelection(getTreePath(dicomInfo), layer.getVisible());
        Enumeration<?> en = dicomInfo.children();
        while (en.hasMoreElements()) {
          Object node = en.nextElement();
          if (node instanceof TreeNode checkNode) {
            boolean sel =
                applyAllViews.isSelected()
                    ? AbstractInfoLayer.defaultDisplayPreferences.getOrDefault(
                        node.toString(), Boolean.FALSE)
                    : layer.getDisplayPreferences(node.toString());
            initPathSelection(getTreePath(checkNode), sel);
          }
        }
      }

      initLayers(view);

      initPathSelection = false;
    }
  }

  private void initLayers(ViewCanvas<?> view) {
    initPathSelection(
        getTreePath(drawings),
        LangUtil.getNULLtoTrue((Boolean) view.getActionValue(ActionW.DRAWINGS.cmd())));

    // FIXME store in pref
    initPathSelection(
        getTreePath(crosshairMode),
        LangUtil.getNULLtoTrue(
            (Boolean) view.getActionValue(ActionVol.HIDE_CROSSHAIR_CENTER.cmd())));
    initPathSelection(
        getTreePath(crosshairCenter),
        LangUtil.getNULLtoFalse(
            (Boolean) view.getActionValue(ActionVol.RECENTERING_CROSSHAIR.cmd())));
    initPathSelection(
        getTreePath(orientationCube),
        LangUtil.getNULLtoFalse((Boolean) view.getActionValue(ActionVol.ORIENTATION_CUBE.cmd())));
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
              && selView.getInfoLayer().getDisplayPreferences(LayerAnnotation.MIN_ANNOTATIONS);

      if (checked && !minDisp) {
        for (ViewCanvas<?> v : getViews()) {
          LayerAnnotation layer = v.getInfoLayer();
          if (layer != null) {
            layer.setVisible(true);
            if (Boolean.TRUE.equals(
                layer.setDisplayPreferencesValue(LayerAnnotation.MIN_ANNOTATIONS, true))) {
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

  private List<ViewCanvas<DicomImageElement>> getViews() {
    ImageViewerPlugin<DicomImageElement> container =
        EventManager.getInstance().getSelectedView2dContainer();
    List<ViewCanvas<DicomImageElement>> views = null;
    if (container != null) {
      if (applyAllViews.isSelected()) {
        views = container.getImagePanels();
      } else {
        views = new ArrayList<>(1);
        Optional.ofNullable(container.getSelectedImagePane()).ifPresent(views::add);
      }
      return views;
    }
    return Collections.emptyList();
  }

  private static void expandTree(JTree tree, DefaultMutableTreeNode start) {
    Enumeration<?> children = start.children();
    while (children.hasMoreElements()) {
      Object child = children.nextElement();
      if (child instanceof DefaultMutableTreeNode dtm && !dtm.isLeaf()) {
        TreePath tp = new TreePath(dtm.getPath());
        tree.expandPath(tp);
        expandTree(tree, dtm);
      }
    }
  }
}
