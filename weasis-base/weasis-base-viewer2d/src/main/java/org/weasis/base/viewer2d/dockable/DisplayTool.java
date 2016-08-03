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

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Objects;

import javax.swing.ImageIcon;
import javax.swing.JCheckBox;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTree;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;

import org.weasis.base.viewer2d.EventManager;
import org.weasis.base.viewer2d.Messages;
import org.weasis.base.viewer2d.View2dContainer;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.gui.util.JMVUtils;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.api.media.data.Thumbnail;
import org.weasis.core.ui.docking.PluginTool;
import org.weasis.core.ui.editor.SeriesViewerEvent;
import org.weasis.core.ui.editor.SeriesViewerEvent.EVENT;
import org.weasis.core.ui.editor.SeriesViewerListener;
import org.weasis.core.ui.editor.image.ImageViewerPlugin;
import org.weasis.core.ui.editor.image.Panner;
import org.weasis.core.ui.editor.image.ViewCanvas;
import org.weasis.core.ui.model.layer.GraphicLayer;
import org.weasis.core.ui.model.layer.Layer;
import org.weasis.core.ui.model.layer.LayerAnnotation;
import org.weasis.core.ui.model.layer.LayerType;

import bibliothek.gui.dock.common.CLocation;
import it.cnr.imaa.essi.lablib.gui.checkboxtree.CheckboxTree;
import it.cnr.imaa.essi.lablib.gui.checkboxtree.DefaultCheckboxTreeCellRenderer;
import it.cnr.imaa.essi.lablib.gui.checkboxtree.TreeCheckingEvent;
import it.cnr.imaa.essi.lablib.gui.checkboxtree.TreeCheckingModel.CheckingMode;

public class DisplayTool extends PluginTool implements SeriesViewerListener {

    public static final String BUTTON_NAME = Messages.getString("DisplayTool.display"); //$NON-NLS-1$

    private final JCheckBox applyAllViews = new JCheckBox("Apply to all views", true); //$NON-NLS-1$
    private final CheckboxTree tree;
    private boolean initPathSelection;
    private DefaultMutableTreeNode rootNode = new DefaultMutableTreeNode("rootNode", true); //$NON-NLS-1$

    private DefaultMutableTreeNode image;
    private DefaultMutableTreeNode info;
    private DefaultMutableTreeNode drawings;
    private TreePath rootPath;
    private JPanel panelFoot;

    public DisplayTool(String pluginName) {
        super(BUTTON_NAME, pluginName, PluginTool.Type.TOOL, 10);
        dockable.setTitleIcon(new ImageIcon(ImageTool.class.getResource("/icon/16x16/display.png"))); //$NON-NLS-1$
        setDockableWidth(210);

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
        info.add(new DefaultMutableTreeNode(LayerAnnotation.ANNOTATIONS, true));
        info.add(new DefaultMutableTreeNode(LayerAnnotation.SCALE, true));
        info.add(new DefaultMutableTreeNode(LayerAnnotation.LUT, true));
        info.add(new DefaultMutableTreeNode(LayerAnnotation.IMAGE_ORIENTATION, true));
        info.add(new DefaultMutableTreeNode(LayerAnnotation.WINDOW_LEVEL, true));
        info.add(new DefaultMutableTreeNode(LayerAnnotation.ZOOM, true));
        info.add(new DefaultMutableTreeNode(LayerAnnotation.ROTATION, true));
        info.add(new DefaultMutableTreeNode(LayerAnnotation.FRAME, true));
        info.add(new DefaultMutableTreeNode(LayerAnnotation.PIXEL, true));
        rootNode.add(info);
        drawings = new DefaultMutableTreeNode(ActionW.DRAWINGS, true);
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
        tree.addTreeCheckingListener(this::treeValueChanged);

        JPanel panel = new JPanel();
        FlowLayout flowLayout = (FlowLayout) panel.getLayout();
        flowLayout.setAlignment(FlowLayout.LEFT);
        add(panel, BorderLayout.NORTH);
        panel.add(applyAllViews);

        expandTree(tree, rootNode);
        add(new JScrollPane(tree), BorderLayout.CENTER);

        panelFoot = new JPanel();
        // To handle selection color with all L&Fs
        panelFoot.setUI(new javax.swing.plaf.PanelUI() {
        });
        panelFoot.setOpaque(true);
        panelFoot.setBackground(JMVUtils.TREE_BACKROUND);
        add(panelFoot, BorderLayout.SOUTH);
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
            initPathSelection(getTreePath(image), view.getImageLayer().getVisible());

            // Annotations node
            LayerAnnotation layer = view.getInfoLayer();
            if (layer != null) {
                initPathSelection(getTreePath(info), layer.getVisible());
                Enumeration en = info.children();
                while (en.hasMoreElements()) {
                    Object node = en.nextElement();
                    if (node instanceof TreeNode) {
                        TreeNode checkNode = (TreeNode) node;
                        initPathSelection(getTreePath(checkNode), layer.getDisplayPreferences(node.toString()));
                    }
                }
            }

            initLayers(view);

            ImageElement img = view.getImage();
            if (img != null) {
                Panner<?> panner = view.getPanner();
                if (panner != null) {
                    int cps = panelFoot.getComponentCount();
                    if (cps > 0) {
                        Component cp = panelFoot.getComponent(0);
                        if (cp != panner) {
                            if (cp instanceof Thumbnail) {
                                ((Thumbnail) cp).removeMouseAndKeyListener();
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

    private void initLayers(ViewCanvas<?> view) {
        // Drawings node
        drawings.removeAllChildren();
        Boolean draw = (Boolean) view.getActionValue(ActionW.DRAWINGS.cmd());
        TreePath drawingsPath = getTreePath(drawings);
        initPathSelection(getTreePath(drawings), draw == null ? true : draw);

        List<GraphicLayer> layers = view.getGraphicManager().getLayers();
        layers.removeIf(l -> l.getType() == LayerType.TEMP_DRAW);
        for (GraphicLayer layer : layers) {
            DefaultMutableTreeNode treeNode = new DefaultMutableTreeNode(layer, true);
            drawings.add(treeNode);
            initPathSelection(getTreePath(treeNode), layer.getVisible());
        }

        // Reload tree node as model does not fire any events to UI
        DefaultTreeModel dtm = (DefaultTreeModel) tree.getModel();
        dtm.reload(drawings);
        tree.expandPath(drawingsPath);
    }

    private void treeValueChanged(TreeCheckingEvent e) {
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
            List<ViewCanvas<ImageElement>> views = null;
            if (container != null) {
                if (applyAllViews.isSelected()) {
                    views = container.getImagePanels();
                } else {
                    views = new ArrayList<>(1);
                    ViewCanvas<ImageElement> view = container.getSelectedImagePane();
                    if (view != null) {
                        views.add(view);
                    }
                }
            }
            if (views != null) {
                if (rootNode.equals(parent)) {
                    if (image.equals(selObject)) {
                        for (ViewCanvas<ImageElement> v : views) {
                            if (selected != v.getImageLayer().getVisible()) {
                                v.getImageLayer().setVisible(selected);
                                v.getJComponent().repaint();
                            }
                        }
                    } else if (info.equals(selObject)) {
                        for (ViewCanvas<ImageElement> v : views) {
                            if (selected != v.getInfoLayer().getVisible()) {
                                v.getInfoLayer().setVisible(selected);
                                v.getJComponent().repaint();
                            }
                        }
                    } else if (drawings.equals(selObject)) {
                        for (ViewCanvas<ImageElement> v : views) {
                            v.setDrawingsVisibility(selected);
                        }
                    }

                } else if (info.equals(parent)) {
                    if (selObject != null) {
                        for (ViewCanvas<ImageElement> v : views) {
                            LayerAnnotation layer = v.getInfoLayer();
                            if (layer != null) {
                                if (layer.setDisplayPreferencesValue(selObject.toString(), selected)) {
                                    v.getJComponent().repaint();
                                }
                            }
                        }
                    }
                } else if (drawings.equals(parent)) {
                    if (selObject instanceof DefaultMutableTreeNode) {
                        if (((DefaultMutableTreeNode) selObject).getUserObject() instanceof LayerType) {
                            Layer layer = (Layer) ((DefaultMutableTreeNode) selObject).getUserObject();
                            for (ViewCanvas<ImageElement> v : views) {
                                if (!Objects.equals(layer.getVisible(), selected)) {
                                    layer.setVisible(selected);
                                    v.getJComponent().repaint();
                                }
                            }
                        }
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
    protected void changeToolWindowAnchor(CLocation clocation) {
        // Do noting
    }

    @Override
    public void changingViewContentEvent(SeriesViewerEvent event) {
        EVENT e = event.getEventType();
        if (EVENT.SELECT_VIEW.equals(e) && event.getSeriesViewer() instanceof View2dContainer) {
            iniTreeValues(((View2dContainer) event.getSeriesViewer()).getSelectedImagePane());
        } else if (EVENT.ADD_LAYER.equals(e)) {
            Object obj = event.getSharedObject();
            if (obj instanceof Layer) {
                DefaultTreeModel dtm = (DefaultTreeModel) tree.getModel();
                DefaultMutableTreeNode node = new DefaultMutableTreeNode(obj, true);
                drawings.add(node);
                dtm.nodesWereInserted(drawings, new int[] { drawings.getIndex(node) });
                if (event.getSeriesViewer() instanceof ImageViewerPlugin) {
                    ViewCanvas<?> pane = ((ImageViewerPlugin<?>) event.getSeriesViewer()).getSelectedImagePane();
                    if (pane != null && ((Layer) obj).getVisible()) {
                        tree.addCheckingPath(getTreePath(node));
                    }
                }
            }
        } else if (EVENT.REMOVE_LAYER.equals(e)) {
            Object obj = event.getSharedObject();
            if (obj instanceof Layer) {
                Layer layer = (Layer) obj;
                Enumeration<?> en = drawings.children();
                while (en.hasMoreElements()) {
                    Object node = en.nextElement();
                    if (node instanceof DefaultMutableTreeNode
                        && layer.equals(((DefaultMutableTreeNode) node).getUserObject())) {
                        DefaultMutableTreeNode n = (DefaultMutableTreeNode) node;
                        TreeNode parent = n.getParent();
                        int index = parent.getIndex(n);
                        n.removeFromParent();
                        DefaultTreeModel dtm = (DefaultTreeModel) tree.getModel();
                        dtm.nodesWereRemoved(parent, new int[] { index }, new TreeNode[] { n });
                    }
                }
            }
        }
    }

    private static void expandTree(JTree tree, DefaultMutableTreeNode start) {
        for (Enumeration<?> children = start.children(); children.hasMoreElements();) {
            DefaultMutableTreeNode dtm = (DefaultMutableTreeNode) children.nextElement();
            if (!dtm.isLeaf()) {
                TreePath tp = new TreePath(dtm.getPath());
                tree.expandPath(tp);
                expandTree(tree, dtm);
            }
        }
        return;
    }

}
