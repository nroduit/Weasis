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
package org.weasis.base.viewer2d.dockable;

import it.cnr.imaa.essi.lablib.gui.checkboxtree.CheckboxTree;
import it.cnr.imaa.essi.lablib.gui.checkboxtree.DefaultCheckboxTreeCellRenderer;
import it.cnr.imaa.essi.lablib.gui.checkboxtree.TreeCheckingEvent;
import it.cnr.imaa.essi.lablib.gui.checkboxtree.TreeCheckingListener;
import it.cnr.imaa.essi.lablib.gui.checkboxtree.TreeCheckingModel.CheckingMode;

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
import javax.swing.JTree;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;

import org.noos.xing.mydoggy.ToolWindowAnchor;
import org.weasis.base.viewer2d.EventManager;
import org.weasis.base.viewer2d.View2dContainer;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.ui.docking.PluginTool;
import org.weasis.core.ui.editor.SeriesViewerEvent;
import org.weasis.core.ui.editor.SeriesViewerEvent.EVENT;
import org.weasis.core.ui.editor.SeriesViewerListener;
import org.weasis.core.ui.editor.image.AnnotationsLayer;
import org.weasis.core.ui.editor.image.DefaultView2d;
import org.weasis.core.ui.editor.image.ImageViewerPlugin;
import org.weasis.core.ui.graphic.model.AbstractLayer;
import org.weasis.core.ui.graphic.model.Tools;

public class DisplayTool extends PluginTool implements SeriesViewerListener {

    public static final String IMAGE = "Image";
    public static final String ANNOTATIONS = "Annotations";

    public static final String BUTTON_NAME = "Display";

    private final JCheckBox applyAllViews = new JCheckBox("Apply to all views", true); //$NON-NLS-1$
    private final CheckboxTree tree;
    private boolean initPathSelection;
    private DefaultMutableTreeNode rootNode = new DefaultMutableTreeNode("rootNode", true); //$NON-NLS-1$

    private DefaultMutableTreeNode image;
    private DefaultMutableTreeNode info;
    private DefaultMutableTreeNode drawings;
    private TreePath rootPath;

    public DisplayTool(String pluginName) {
        super(BUTTON_NAME, pluginName, ToolWindowAnchor.RIGHT, PluginTool.TYPE.mainTool);
        //        setIcon(new ImageIcon(ImageTool.class.getResource("/icon/16x16/display.png"))); //$NON-NLS-1$
        setDockableWidth(210);

        tree = new CheckboxTree();
        initPathSelection = false;
        setLayout(new BorderLayout(0, 0));
        iniTree();

    }

    public void iniTree() {
        tree.getCheckingModel().setCheckingMode(CheckingMode.SIMPLE);

        image = new DefaultMutableTreeNode(IMAGE, true);
        rootNode.add(image);
        info = new DefaultMutableTreeNode(ANNOTATIONS, true);
        info.add(new DefaultMutableTreeNode(AnnotationsLayer.ANNOTATIONS, true));
        info.add(new DefaultMutableTreeNode(AnnotationsLayer.SCALE, true));
        info.add(new DefaultMutableTreeNode(AnnotationsLayer.LUT, true));
        info.add(new DefaultMutableTreeNode(AnnotationsLayer.IMAGE_ORIENTATION, true));
        info.add(new DefaultMutableTreeNode(AnnotationsLayer.WINDOW_LEVEL, true));
        info.add(new DefaultMutableTreeNode(AnnotationsLayer.ZOOM, true));
        info.add(new DefaultMutableTreeNode(AnnotationsLayer.ROTATION, true));
        info.add(new DefaultMutableTreeNode(AnnotationsLayer.FRAME, true));
        info.add(new DefaultMutableTreeNode(AnnotationsLayer.PIXEL, true));
        rootNode.add(info);
        drawings = new DefaultMutableTreeNode(ActionW.DRAW, true);
        drawings.add(new DefaultMutableTreeNode(Tools.MEASURE, true));
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

                    ImageViewerPlugin<ImageElement> container = EventManager.getInstance().getSelectedView2dContainer();
                    ArrayList<DefaultView2d<ImageElement>> views = null;
                    if (container != null) {
                        if (applyAllViews.isSelected()) {
                            views = container.getImagePanels();
                        } else {
                            views = new ArrayList<DefaultView2d<ImageElement>>(1);
                            views.add(container.getSelectedImagePane());
                        }
                    }
                    if (views != null) {
                        if (rootNode.equals(parent)) {
                            if (image.equals(selObject)) {
                                for (DefaultView2d<ImageElement> v : views) {
                                    if (selected != v.getImageLayer().isVisible()) {
                                        v.getImageLayer().setVisible(selected);
                                        v.repaint();
                                    }
                                }
                            } else if (info.equals(selObject)) {
                                for (DefaultView2d<ImageElement> v : views) {
                                    if (selected != v.getInfoLayer().isVisible()) {
                                        v.getInfoLayer().setVisible(selected);
                                        v.repaint();
                                    }
                                }
                            } else if (drawings.equals(selObject)) {
                                for (DefaultView2d<ImageElement> v : views) {
                                    v.setDrawingsVisibility(selected);
                                }
                            }

                        } else if (info.equals(parent)) {
                            if (selObject != null) {
                                for (DefaultView2d<ImageElement> v : views) {
                                    AnnotationsLayer layer = v.getInfoLayer();
                                    if (layer != null) {
                                        if (layer.setDisplayPreferencesValue(selObject.toString(), selected)) {
                                            v.repaint();
                                        }
                                    }
                                }
                            }
                        } else if (drawings.equals(parent)) {
                            if (selObject instanceof DefaultMutableTreeNode) {
                                if (((DefaultMutableTreeNode) selObject).getUserObject() instanceof Tools) {
                                    Tools tool = (Tools) ((DefaultMutableTreeNode) selObject).getUserObject();
                                    for (DefaultView2d<ImageElement> v : views) {
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

            // Annotations node
            AnnotationsLayer layer = view.getInfoLayer();
            if (layer != null) {
                initPathSelection(getTreePath(info), layer.isVisible());
                Enumeration en = info.children();
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
