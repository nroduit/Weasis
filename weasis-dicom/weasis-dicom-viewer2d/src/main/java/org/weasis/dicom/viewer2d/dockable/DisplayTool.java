/*******************************************************************************
 * Copyright (c) 2010 Nicolas Roduit.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     Nicolas Roduit - initial API and implementation
 ******************************************************************************/
package org.weasis.dicom.viewer2d.dockable;

import it.cnr.imaa.essi.lablib.gui.checkboxtree.CheckboxTree;
import it.cnr.imaa.essi.lablib.gui.checkboxtree.DefaultCheckboxTreeCellRenderer;
import it.cnr.imaa.essi.lablib.gui.checkboxtree.TreeCheckingEvent;
import it.cnr.imaa.essi.lablib.gui.checkboxtree.TreeCheckingListener;
import it.cnr.imaa.essi.lablib.gui.checkboxtree.TreeCheckingModel.CheckingMode;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.beans.PropertyChangeEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

import javax.swing.ImageIcon;
import javax.swing.JCheckBox;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTree;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;

import org.noos.xing.mydoggy.ToolWindowAnchor;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.media.data.Series;
import org.weasis.core.ui.docking.PluginTool;
import org.weasis.core.ui.editor.SeriesViewerEvent;
import org.weasis.core.ui.editor.SeriesViewerEvent.EVENT;
import org.weasis.core.ui.editor.SeriesViewerListener;
import org.weasis.core.ui.editor.image.AnnotationsLayer;
import org.weasis.core.ui.editor.image.DefaultView2d;
import org.weasis.core.ui.editor.image.ImageViewerPlugin;
import org.weasis.core.ui.graphic.model.AbstractLayer;
import org.weasis.core.ui.graphic.model.Tools;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.viewer2d.EventManager;
import org.weasis.dicom.viewer2d.Messages;
import org.weasis.dicom.viewer2d.View2dContainer;

public class DisplayTool extends PluginTool implements SeriesViewerListener {

    public static final String IMAGE = Messages.getString("DisplayTool.image"); //$NON-NLS-1$
    public static final String DICOM_IMAGE_OVERLAY = Messages.getString("DisplayTool.dicom_overlay"); //$NON-NLS-1$
    public static final String DICOM_PIXEL_PADDING = Messages.getString("DisplayTool.pixpad"); //$NON-NLS-1$
    public static final String DICOM_SHUTTER = Messages.getString("DisplayTool.shutter"); //$NON-NLS-1$
    public static final String DICOM_ANNOTATIONS = Messages.getString("DisplayTool.dicom_ano"); //$NON-NLS-1$

    public static final String BUTTON_NAME = Messages.getString("DisplayTool.display"); //$NON-NLS-1$

    private final JCheckBox applyAllViews = new JCheckBox(Messages.getString("DisplayTool.btn_apply_all"), true); //$NON-NLS-1$
    private final CheckboxTree tree;
    private boolean initPathSelection;
    private DefaultMutableTreeNode rootNode = new DefaultMutableTreeNode("rootNode", true); //$NON-NLS-1$

    private DefaultMutableTreeNode image;
    private DefaultMutableTreeNode dicomInfo;
    private DefaultMutableTreeNode drawings;
    private TreePath rootPath;

    public DisplayTool(String pluginName) {
        super(BUTTON_NAME, pluginName, ToolWindowAnchor.RIGHT, PluginTool.TYPE.mainTool);
        setIcon(new ImageIcon(ImageTool.class.getResource("/icon/16x16/display.png"))); //$NON-NLS-1$
        setDockableWidth(210);

        tree = new CheckboxTree();
        initPathSelection = false;
        setLayout(new BorderLayout(0, 0));
        iniTree();

    }

    public void iniTree() {
        tree.getCheckingModel().setCheckingMode(CheckingMode.SIMPLE);

        image = new DefaultMutableTreeNode(IMAGE, true);
        image.add(new DefaultMutableTreeNode(DICOM_IMAGE_OVERLAY, false));
        image.add(new DefaultMutableTreeNode(DICOM_SHUTTER, false));
        image.add(new DefaultMutableTreeNode(DICOM_PIXEL_PADDING, false));
        rootNode.add(image);
        dicomInfo = new DefaultMutableTreeNode(DICOM_ANNOTATIONS, true);
        dicomInfo.add(new DefaultMutableTreeNode(AnnotationsLayer.ANNOTATIONS, true));
        dicomInfo.add(new DefaultMutableTreeNode(AnnotationsLayer.ANONYM_ANNOTATIONS, false));
        dicomInfo.add(new DefaultMutableTreeNode(AnnotationsLayer.SCALE, true));
        dicomInfo.add(new DefaultMutableTreeNode(AnnotationsLayer.LUT, true));
        dicomInfo.add(new DefaultMutableTreeNode(AnnotationsLayer.IMAGE_ORIENTATION, true));
        dicomInfo.add(new DefaultMutableTreeNode(AnnotationsLayer.WINDOW_LEVEL, true));
        dicomInfo.add(new DefaultMutableTreeNode(AnnotationsLayer.ZOOM, true));
        dicomInfo.add(new DefaultMutableTreeNode(AnnotationsLayer.ROTATION, true));
        dicomInfo.add(new DefaultMutableTreeNode(AnnotationsLayer.FRAME, true));
        dicomInfo.add(new DefaultMutableTreeNode(AnnotationsLayer.PIXEL, true));
        rootNode.add(dicomInfo);
        drawings = new DefaultMutableTreeNode(ActionW.DRAW, true);
        drawings.add(new DefaultMutableTreeNode(Tools.MEASURE, true));
        drawings.add(new DefaultMutableTreeNode(Tools.CROSSLINES, true));
        rootNode.add(drawings);

        DefaultTreeModel model = new DefaultTreeModel(rootNode, false);
        tree.setModel(model);
        rootPath = new TreePath(rootNode.getPath());
        tree.addCheckingPath(rootPath);

        tree.setShowsRootHandles(true);
        tree.setRootVisible(false);
        tree.setExpandsSelectedPaths(true);
        DefaultCheckboxTreeCellRenderer renderer = new DefaultCheckboxTreeCellRenderer();
        renderer.setOpenIcon(null);
        renderer.setClosedIcon(null);
        renderer.setLeafIcon(null);
        tree.setCellRenderer(renderer);
        tree.addTreeCheckingListener(new TreeCheckingListener() {

            @Override
            public void valueChanged(TreeCheckingEvent e) {
                if (!initPathSelection) {
                    TreePath path = e.getPath();
                    Object source = e.getSource();
                    boolean selected = e.isCheckedPath();
                    Object selObject = path.getLastPathComponent();
                    Object parent = null;
                    if (path.getParentPath() != null) {
                        parent = path.getParentPath().getLastPathComponent();
                    }

                    ImageViewerPlugin<DicomImageElement> container =
                        EventManager.getInstance().getSelectedView2dContainer();
                    ArrayList<DefaultView2d<DicomImageElement>> views = null;
                    if (container != null) {
                        if (applyAllViews.isSelected()) {
                            views = container.getImagePanels();
                        } else {
                            views = new ArrayList<DefaultView2d<DicomImageElement>>(1);
                            views.add(container.getSelectedImagePane());
                        }
                    }
                    if (views != null) {
                        if (rootNode.equals(parent)) {
                            if (image.equals(selObject)) {
                                for (DefaultView2d<DicomImageElement> v : views) {
                                    if (selected != v.getImageLayer().isVisible()) {
                                        v.getImageLayer().setVisible(selected);
                                        v.repaint();
                                    }
                                }
                            } else if (dicomInfo.equals(selObject)) {
                                for (DefaultView2d<DicomImageElement> v : views) {
                                    if (selected != v.getInfoLayer().isVisible()) {
                                        v.getInfoLayer().setVisible(selected);
                                        v.repaint();
                                    }
                                }
                            } else if (drawings.equals(selObject)) {
                                for (DefaultView2d<DicomImageElement> v : views) {
                                    v.setDrawingsVisibility(selected);
                                }
                            }
                        } else if (image.equals(parent)) {
                            if (selObject != null) {
                                if (DICOM_IMAGE_OVERLAY.equals(selObject.toString())) {
                                    sendPropertyChangeEvent(views, ActionW.IMAGE_OVERLAY.cmd(), selected);
                                } else if (DICOM_SHUTTER.equals(selObject.toString())) {
                                    sendPropertyChangeEvent(views, ActionW.IMAGE_SCHUTTER.cmd(), selected);
                                } else if (DICOM_PIXEL_PADDING.equals(selObject.toString())) {
                                    sendPropertyChangeEvent(views, ActionW.IMAGE_PIX_PADDING.cmd(), selected);
                                }
                            }
                        } else if (dicomInfo.equals(parent)) {
                            if (selObject != null) {
                                for (DefaultView2d<DicomImageElement> v : views) {
                                    AnnotationsLayer layer = v.getInfoLayer();
                                    if (layer != null) {
                                        if (layer.setDisplayPreferencesValue(selObject.toString(), selected)) {
                                            v.repaint();
                                        }
                                    }
                                }
                                if (AnnotationsLayer.ANONYM_ANNOTATIONS.equals(selObject.toString())) {
                                    // Send message to listeners, only selected view
                                    DefaultView2d<DicomImageElement> v = container.getSelectedImagePane();
                                    Series series = (Series) v.getSeries();
                                    EventManager.getInstance().fireSeriesViewerListeners(
                                        new SeriesViewerEvent(container, series, series.getMedia(v.getFrameIndex()),
                                            EVENT.ANONYM));

                                }
                            }
                        } else if (drawings.equals(parent)) {
                            if (selObject instanceof DefaultMutableTreeNode) {
                                if (((DefaultMutableTreeNode) selObject).getUserObject() instanceof Tools) {
                                    Tools tool = (Tools) ((DefaultMutableTreeNode) selObject).getUserObject();
                                    for (DefaultView2d<DicomImageElement> v : views) {
                                        AbstractLayer layer = v.getLayerModel().getLayer(tool);
                                        if (layer != null) {
                                            if (layer.isVisible() != selected) {
                                                layer.setVisible(selected);
                                                v.repaint();
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

        JPanel panel = new JPanel();
        FlowLayout flowLayout = (FlowLayout) panel.getLayout();
        flowLayout.setAlignment(FlowLayout.LEFT);
        add(panel, BorderLayout.NORTH);
        panel.add(applyAllViews);

        expandTree(tree, rootNode);
        add(new JScrollPane(tree), BorderLayout.CENTER);
    }

    private void sendPropertyChangeEvent(ArrayList<DefaultView2d<DicomImageElement>> views, String cmd, boolean selected) {
        for (DefaultView2d<DicomImageElement> v : views) {
            Boolean overlay = (Boolean) v.getActionValue(cmd);
            if (overlay != null && selected != overlay) {
                v.propertyChange(new PropertyChangeEvent(EventManager.getInstance(), cmd, null, selected));
            }
        }
    }

    private void iniDicomView(DefaultView2d view, String cmd, int index) {
        TreeNode treeNode = image.getChildAt(index);
        if (treeNode != null) {
            Boolean val = (Boolean) view.getActionValue(cmd);
            initPathSelection(getTreePath(treeNode), val == null ? false : val);
        }
    }

    private void initPathSelection(TreePath path, boolean selected) {
        if (selected) {
            tree.addCheckingPath(path);
        } else {
            tree.removeCheckingPath(path);
        }
    }

    public void iniTreeValues(DefaultView2d view) {
        if (view != null) {
            initPathSelection = true;
            // Image node
            initPathSelection(getTreePath(image), view.getImageLayer().isVisible());
            iniDicomView(view, ActionW.IMAGE_OVERLAY.cmd(), 0);
            iniDicomView(view, ActionW.IMAGE_SCHUTTER.cmd(), 1);
            iniDicomView(view, ActionW.IMAGE_PIX_PADDING.cmd(), 2);

            // Annotations node
            AnnotationsLayer layer = view.getInfoLayer();
            if (layer != null) {
                initPathSelection(getTreePath(dicomInfo), layer.isVisible());
                Enumeration en = dicomInfo.children();
                while (en.hasMoreElements()) {
                    Object node = en.nextElement();
                    if (node instanceof TreeNode) {
                        TreeNode checkNode = (TreeNode) node;
                        initPathSelection(getTreePath(checkNode), layer.getDisplayPreferences(node.toString()));
                    }
                }
            }

            // Drawings node
            Boolean draw = (Boolean) view.getActionValue(ActionW.DRAW.cmd());
            initPathSelection(getTreePath(drawings), draw == null ? true : draw);
            Enumeration en = drawings.children();
            while (en.hasMoreElements()) {
                Object node = en.nextElement();
                if (node instanceof DefaultMutableTreeNode
                    && ((DefaultMutableTreeNode) node).getUserObject() instanceof Tools) {
                    DefaultMutableTreeNode checkNode = (DefaultMutableTreeNode) node;
                    AbstractLayer l = view.getLayerModel().getLayer((Tools) checkNode.getUserObject());
                    if (layer != null) {
                        initPathSelection(getTreePath(checkNode), l.isVisible());
                    }
                }
            }
            initPathSelection = false;
        }
    }

    private static TreePath getTreePath(TreeNode node) {
        List<TreeNode> list = new ArrayList<TreeNode>();
        list.add(node);
        TreeNode parent = node;
        while (parent.getParent() != null) {
            parent = parent.getParent();
            list.add(parent);
        }
        Collections.reverse(list);
        return new TreePath(list.toArray(new TreeNode[list.size()]));
    }

    @Override
    public Component getToolComponent() {
        return this;
    }

    public void expandAllTree() {
        tree.expandRow(4);
    }

    @Override
    protected void changeToolWindowAnchor(ToolWindowAnchor anchor) {
        // TODO Auto-generated method stub

    }

    @Override
    public void changingViewContentEvent(SeriesViewerEvent event) {
        if (event.getEventType().equals(EVENT.SELECT_VIEW) && event.getSeriesViewer() instanceof View2dContainer) {
            iniTreeValues(((View2dContainer) event.getSeriesViewer()).getSelectedImagePane());
        }
    }

    private static void expandTree(JTree tree, DefaultMutableTreeNode start) {
        for (Enumeration children = start.children(); children.hasMoreElements();) {
            DefaultMutableTreeNode dtm = (DefaultMutableTreeNode) children.nextElement();
            if (!dtm.isLeaf()) {
                //
                TreePath tp = new TreePath(dtm.getPath());
                tree.expandPath(tp);
                //
                expandTree(tree, dtm);
            }
        }
        return;
    }

}
