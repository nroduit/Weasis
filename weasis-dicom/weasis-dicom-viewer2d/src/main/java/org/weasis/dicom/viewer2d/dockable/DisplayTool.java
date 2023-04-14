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
import it.cnr.imaa.essi.lablib.gui.checkboxtree.TreeCheckingModel.CheckingMode;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.beans.PropertyChangeEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
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
import org.weasis.core.api.image.OpManager;
import org.weasis.core.api.image.WindowOp;
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
import org.weasis.core.ui.model.layer.LayerType;
import org.weasis.core.ui.util.CheckBoxTreeBuilder;
import org.weasis.core.util.LangUtil;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.codec.display.OverlayOp;
import org.weasis.dicom.codec.display.ShutterOp;
import org.weasis.dicom.viewer2d.EventManager;
import org.weasis.dicom.viewer2d.Messages;

public class DisplayTool extends PluginTool implements SeriesViewerListener {

  public static final String IMAGE = Messages.getString("DisplayTool.image");
  public static final String DICOM_IMAGE_OVERLAY = Messages.getString("DisplayTool.dicom_overlay");
  public static final String DICOM_PIXEL_PADDING = Messages.getString("DisplayTool.pixpad");
  public static final String DICOM_SHUTTER = Messages.getString("DisplayTool.shutter");
  public static final String DICOM_ANNOTATIONS = Messages.getString("DisplayTool.dicom_ano");

  public static final String BUTTON_NAME = Messages.getString("DisplayTool.display");

  private final JCheckBox applyAllViews =
      new JCheckBox(Messages.getString("DisplayTool.btn_apply_all"), true);
  private final CheckboxTree tree;
  private boolean initPathSelection;
  private final DefaultMutableTreeNode rootNode = new DefaultMutableTreeNode("rootNode", true);

  private DefaultMutableTreeNode imageNode;
  private DefaultMutableTreeNode dicomInfo;
  private DefaultMutableTreeNode drawings;
  private DefaultMutableTreeNode crosslines;
  private DefaultMutableTreeNode minAnnotations;

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
    imageNode = new DefaultMutableTreeNode(IMAGE, true);
    imageNode.add(new DefaultMutableTreeNode(DICOM_IMAGE_OVERLAY, false));
    imageNode.add(new DefaultMutableTreeNode(DICOM_SHUTTER, false));
    imageNode.add(new DefaultMutableTreeNode(DICOM_PIXEL_PADDING, false));
    rootNode.add(imageNode);
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
    dicomInfo.add(new DefaultMutableTreeNode(LayerAnnotation.FRAME, true));
    dicomInfo.add(new DefaultMutableTreeNode(LayerAnnotation.PIXEL, true));
    rootNode.add(dicomInfo);
    drawings = new DefaultMutableTreeNode(ActionW.DRAWINGS, true);
    rootNode.add(drawings);
    crosslines = new DefaultMutableTreeNode(LayerType.CROSSLINES, false);
    drawings.add(crosslines);

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
                if (p instanceof ImageViewerPlugin) {
                  ((ImageViewerPlugin<?>) p)
                      .getImagePanels()
                      .forEach(v -> v.getJComponent().repaint());
                }
              }
            }
          } else {
            synchronized (UIManager.VIEWER_PLUGINS) {
              for (final ViewerPlugin<?> p : UIManager.VIEWER_PLUGINS) {
                if (p instanceof ImageViewerPlugin) {
                  for (ViewCanvas<?> v : ((ImageViewerPlugin<?>) p).getImagePanels()) {
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

      ImageViewerPlugin<?> container = EventManager.getInstance().getSelectedView2dContainer();
      List<ViewCanvas<?>> views = getViews();

      if (rootNode.equals(parent)) {
        if (imageNode.equals(selObject)) {
          for (ViewCanvas<?> v : views) {
            if (selected != Boolean.TRUE.equals(v.getImageLayer().getVisible())) {
              v.getImageLayer().setVisible(selected);
              v.getJComponent().repaint();
            }
          }
        } else if (dicomInfo.equals(selObject)) {
          for (ViewCanvas<?> v : views) {
            LayerAnnotation layer = v.getInfoLayer();
            if (layer != null && selected != Boolean.TRUE.equals(v.getInfoLayer().getVisible())) {
              v.getInfoLayer().setVisible(selected);
              v.getJComponent().repaint();
            }
          }
        } else if (drawings.equals(selObject)) {
          for (ViewCanvas<?> v : views) {
            v.setDrawingsVisibility(selected);
          }
        }
      } else if (imageNode.equals(parent)) {
        if (selObject != null) {
          if (DICOM_IMAGE_OVERLAY.equals(selObject.toString())) {
            sendPropertyChangeEvent(views, ActionW.IMAGE_OVERLAY.cmd(), selected);
          } else if (DICOM_SHUTTER.equals(selObject.toString())) {
            sendPropertyChangeEvent(views, ActionW.IMAGE_SHUTTER.cmd(), selected);
          } else if (DICOM_PIXEL_PADDING.equals(selObject.toString())) {
            sendPropertyChangeEvent(views, ActionW.IMAGE_PIX_PADDING.cmd(), selected);
          }
        }
      } else if (dicomInfo.equals(parent)) {
        if (selObject != null) {
          if (applyAllViews.isSelected()) {
            if (Boolean.TRUE.equals(
                AbstractInfoLayer.setDefaultDisplayPreferencesValue(
                    selObject.toString(), selected))) {
              views.forEach(v -> v.getJComponent().repaint());
            }
          } else {
            for (ViewCanvas<?> v : views) {
              LayerAnnotation layer = v.getInfoLayer();
              if (layer != null
                  && Boolean.TRUE.equals(
                      layer.setDisplayPreferencesValue(selObject.toString(), selected))) {
                v.getJComponent().repaint();
              }
            }
          }
          if (LayerAnnotation.ANONYM_ANNOTATIONS.equals(selObject.toString())) {
            // Send message to listeners, only selected view
            ViewCanvas<?> v = container.getSelectedImagePane();
            Series<?> series = (Series<?>) v.getSeries();
            EventManager.getInstance()
                .fireSeriesViewerListeners(
                    new SeriesViewerEvent(container, series, v.getImage(), EVENT.ANONYM));
          }
        }
      } else if (drawings.equals(parent) && selObject == crosslines) {
        for (ViewCanvas<?> v : views) {
          if (Boolean.TRUE.equals(v.getActionValue(LayerType.CROSSLINES.name())) != selected) {
            v.setActionsInView(LayerType.CROSSLINES.name(), selected);
            if (!selected) {
              v.getGraphicManager().deleteByLayerType(LayerType.CROSSLINES);
              v.getJComponent().repaint();
            } else {
              // Force redrawing crosslines
              v.getEventManager()
                  .getAction(ActionW.SCROLL_SERIES)
                  .ifPresent(a -> a.stateChanged(a.getSliderModel()));
            }
          }
        }
      }
    }
  }

  private static void sendPropertyChangeEvent(
      List<ViewCanvas<?>> views, String cmd, boolean selected) {
    for (ViewCanvas<?> v : views) {
      v.propertyChange(new PropertyChangeEvent(EventManager.getInstance(), cmd, null, selected));
    }
  }

  private void iniDicomView(OpManager disOp, String op, String param, int index) {
    TreeNode treeNode = imageNode.getChildAt(index);
    if (treeNode != null) {
      Boolean val = (Boolean) disOp.getParamValue(op, param);
      initPathSelection(getTreePath(treeNode), val != null && val);
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
      // Image node
      OpManager disOp = view.getDisplayOpManager();
      initPathSelection(getTreePath(imageNode), view.getImageLayer().getVisible());
      iniDicomView(disOp, OverlayOp.OP_NAME, OverlayOp.P_SHOW, 0);
      iniDicomView(disOp, ShutterOp.OP_NAME, ShutterOp.P_SHOW, 1);
      iniDicomView(disOp, WindowOp.OP_NAME, ActionW.IMAGE_PIX_PADDING.cmd(), 2);

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
    initPathSelection(
        getTreePath(crosslines),
        LangUtil.getNULLtoTrue((Boolean) view.getActionValue(LayerType.CROSSLINES.name())));
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
    // TODO should received layer changes
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

  private List<ViewCanvas<?>> getViews() {
    List<ViewCanvas<?>> views;
    if (applyAllViews.isSelected()) {
      views = new ArrayList<>();
      synchronized (UIManager.VIEWER_PLUGINS) {
        for (final ViewerPlugin<?> p : UIManager.VIEWER_PLUGINS) {
          if (p instanceof ImageViewerPlugin) {
            views.addAll(((ImageViewerPlugin<?>) p).getImagePanels());
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
