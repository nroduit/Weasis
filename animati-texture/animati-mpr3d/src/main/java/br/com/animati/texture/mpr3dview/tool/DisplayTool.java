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
package br.com.animati.texture.mpr3dview.tool;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Optional;

import javax.swing.ImageIcon;
import javax.swing.JCheckBox;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTree;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;

import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.gui.util.JMVUtils;
import org.weasis.core.api.media.data.Series;
import org.weasis.core.ui.docking.PluginTool;
import org.weasis.core.ui.editor.SeriesViewerEvent;
import org.weasis.core.ui.editor.SeriesViewerEvent.EVENT;
import org.weasis.core.ui.editor.SeriesViewerListener;
import org.weasis.core.ui.editor.image.ImageViewerPlugin;
import org.weasis.core.ui.editor.image.ViewCanvas;
import org.weasis.core.ui.model.layer.GraphicLayer;
import org.weasis.core.ui.model.layer.LayerAnnotation;
import org.weasis.core.ui.model.layer.LayerType;
import org.weasis.dicom.codec.DicomImageElement;

import bibliothek.gui.dock.common.CLocation;
import br.com.animati.texture.mpr3dview.GUIManager;
import br.com.animati.texture.mpr3dview.internal.Messages;
import it.cnr.imaa.essi.lablib.gui.checkboxtree.CheckboxTree;
import it.cnr.imaa.essi.lablib.gui.checkboxtree.DefaultCheckboxTreeCellRenderer;
import it.cnr.imaa.essi.lablib.gui.checkboxtree.TreeCheckingEvent;
import it.cnr.imaa.essi.lablib.gui.checkboxtree.TreeCheckingListener;
import it.cnr.imaa.essi.lablib.gui.checkboxtree.TreeCheckingModel;
import it.cnr.imaa.essi.lablib.gui.checkboxtree.TreeCheckingModel.CheckingMode;

public class DisplayTool extends PluginTool implements SeriesViewerListener {

    public static final String DICOM_ANNOTATIONS = Messages.getString("DisplayTool.dicom_ano"); //$NON-NLS-1$

    public static final String BUTTON_NAME = Messages.getString("DisplayTool.display"); //$NON-NLS-1$

    private final JCheckBox applyAllViews = new JCheckBox(Messages.getString("DisplayTool.btn_apply_all"), true); //$NON-NLS-1$
    private final CheckboxTree tree;
    private boolean initPathSelection;
    private DefaultMutableTreeNode rootNode = new DefaultMutableTreeNode("rootNode", true); //$NON-NLS-1$

    private DefaultMutableTreeNode dicomInfo;
    private DefaultMutableTreeNode drawings;
    private DefaultMutableTreeNode minAnnotations;
    private TreePath rootPath;
    private JPanel panelFoot;

    public DisplayTool(String pluginName) {
        super(BUTTON_NAME, pluginName, PluginTool.Type.TOOL, 10);
        dockable.setTitleIcon(new ImageIcon(DisplayTool.class.getResource("/icon/16x16/display.png"))); //$NON-NLS-1$
        setDockableWidth(210);

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
        dicomInfo.add(new DefaultMutableTreeNode(LayerAnnotation.IMAGE_ORIENTATION, true));
        dicomInfo.add(new DefaultMutableTreeNode(LayerAnnotation.WINDOW_LEVEL, true));
        dicomInfo.add(new DefaultMutableTreeNode(LayerAnnotation.ZOOM, true));
        dicomInfo.add(new DefaultMutableTreeNode(LayerAnnotation.ROTATION, true));
        dicomInfo.add(new DefaultMutableTreeNode(LayerAnnotation.FRAME, true));
        dicomInfo.add(new DefaultMutableTreeNode(LayerAnnotation.PIXEL, true));
        rootNode.add(dicomInfo);
        drawings = new DefaultMutableTreeNode(ActionW.DRAW, true);
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
                        GUIManager.getInstance().getSelectedView2dContainer();
                    List<ViewCanvas<DicomImageElement>> views = null;
                    if (container != null) {
                        if (applyAllViews.isSelected()) {
                            views = container.getImagePanels();
                        } else {
                            views = new ArrayList<>(1);
                            ViewCanvas<DicomImageElement> view = container.getSelectedImagePane();
                            if (view != null) {
                                views.add(view);
                            }
                        }
                    }
                    if (views != null) {
                        if (rootNode.equals(parent)) {
                            if (dicomInfo.equals(selObject)) {
                                for (ViewCanvas<DicomImageElement> v : views) {
                                    LayerAnnotation layer = v.getInfoLayer();
                                    if (layer != null) {
                                        if (layer.setDisplayPreferencesValue(LayerAnnotation.MIN_ANNOTATIONS, false)) {
                                            v.getJComponent().repaint();
                                        }
                                        if (selected != v.getInfoLayer().getVisible()) {
                                            v.getInfoLayer().setVisible(selected);
                                            v.getJComponent().repaint();
                                        }
                                    }

                                }
                            } else if (drawings.equals(selObject)) {
                                for (ViewCanvas<DicomImageElement> v : views) {
                                    v.setDrawingsVisibility(selected);
                                }
                            }
                        } else if (dicomInfo.equals(parent)) {
                            if (selObject != null) {
                                for (ViewCanvas<DicomImageElement> v : views) {
                                    LayerAnnotation layer = v.getInfoLayer();
                                    if (layer != null) {
                                        if (layer.setDisplayPreferencesValue(selObject.toString(), selected)) {
                                            v.getJComponent().repaint();
                                        }
                                    }
                                }
                                if (LayerAnnotation.ANONYM_ANNOTATIONS.equals(selObject.toString())) {
                                    // Send message to listeners, only selected view
                                    ViewCanvas<DicomImageElement> v = container.getSelectedImagePane();
                                    Series series = (Series) v.getSeries();
                                    GUIManager.getInstance().fireSeriesViewerListeners(
                                        new SeriesViewerEvent(container, series, v.getImage(), EVENT.ANONYM));

                                }
                            }
                        } else if (drawings.equals(parent)) {
                            for (ViewCanvas<DicomImageElement> v : views) {
                                v.setDrawingsVisibility(selected);
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

    public void iniTreeValues(ViewCanvas view) {
        if (view != null) {
            initPathSelection = true;

            // Annotations node
            LayerAnnotation layer = view.getInfoLayer();
            if (layer != null) {
                initPathSelection(getTreePath(dicomInfo), layer.getVisible());
                Enumeration en = dicomInfo.children();
                while (en.hasMoreElements()) {
                    Object node = en.nextElement();
                    if (node instanceof TreeNode) {
                        TreeNode checkNode = (TreeNode) node;
                        initPathSelection(getTreePath(checkNode), layer.getDisplayPreferences(node.toString()));
                    }
                }
            }

            initLayers(view);

            initPathSelection = false;
        }
    }

    private void initLayers(ViewCanvas<?> view) {
        // Drawings node
        drawings.removeAllChildren();
        Boolean draw = (Boolean) view.getActionValue(ActionW.DRAW.cmd());
        TreePath drawingsPath = getTreePath(drawings);
        initPathSelection(getTreePath(drawings), draw == null ? true : draw);

        List<GraphicLayer> layers = view.getGraphicManager().getLayers();
        for (GraphicLayer layer : layers) {
            DefaultMutableTreeNode treeNode = new DefaultMutableTreeNode(layer.getType(), true);
            drawings.add(treeNode);
            initPathSelection(getTreePath(treeNode), layer.getVisible());
        }

        // Reload tree node as model does not fire any events to UI
        DefaultTreeModel dtm = (DefaultTreeModel) tree.getModel();
        dtm.reload(drawings);
        tree.expandPath(drawingsPath);
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
        // TODO Auto-generated method stub

    }

    @Override
    public void changingViewContentEvent(SeriesViewerEvent event) {
        EVENT e = event.getEventType();
        if (EVENT.SELECT_VIEW.equals(e) && event.getSeriesViewer() instanceof ImageViewerPlugin) {
            iniTreeValues(((ImageViewerPlugin<?>) event.getSeriesViewer()).getSelectedImagePane());
        } else if (EVENT.TOOGLE_INFO.equals(e)) {
            TreeCheckingModel model = tree.getCheckingModel();
            TreePath path = new TreePath(dicomInfo.getPath());

            boolean checked = model.isPathChecked(path);
            ViewCanvas<DicomImageElement> selView = GUIManager.getInstance().getSelectedViewPane();
            // Use an intermediate state of the minimal DICOM information. Triggered only from the shortcut SPACE or I
            boolean minDisp =
                selView != null && selView.getInfoLayer().getDisplayPreferences(LayerAnnotation.MIN_ANNOTATIONS);

            if (checked && !minDisp) {
                ImageViewerPlugin<DicomImageElement> container = GUIManager.getInstance().getSelectedView2dContainer();
                List<ViewCanvas<DicomImageElement>> views = null;
                if (container != null) {
                    if (applyAllViews.isSelected()) {
                        views = container.getImagePanels();
                    } else {
                        views = new ArrayList<>(1);
                        ViewCanvas<DicomImageElement> view = container.getSelectedImagePane();
                        if (view != null) {
                            views.add(view);
                        }
                    }
                }
                if (views != null) {
                    for (ViewCanvas<DicomImageElement> v : views) {
                        LayerAnnotation layer = v.getInfoLayer();
                        if (layer != null) {
                            layer.setVisible(true);
                            if (layer.setDisplayPreferencesValue(LayerAnnotation.MIN_ANNOTATIONS, true)) {
                                v.getJComponent().repaint();
                            }
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
        } else if (EVENT.ADD_LAYER.equals(e)) {
            Object obj = event.getSharedObject();
            if (obj instanceof String) {
                DefaultTreeModel dtm = (DefaultTreeModel) tree.getModel();
                DefaultMutableTreeNode node = new DefaultMutableTreeNode(obj, true);
                drawings.add(node);
                dtm.nodesWereInserted(drawings, new int[] { drawings.getIndex(node) });
                if (event.getSeriesViewer() instanceof ImageViewerPlugin && node.getUserObject() instanceof LayerType) {
                    ViewCanvas<?> pane = ((ImageViewerPlugin<?>) event.getSeriesViewer()).getSelectedImagePane();
                    if (pane != null) {
                        Optional<GraphicLayer> layer =
                            pane.getGraphicManager().findLayerByType((LayerType) node.getUserObject());
                        layer.filter(l -> l.getVisible()).ifPresent(l -> tree.addCheckingPath(getTreePath(node)));
                    }
                }
            }
        } else if (EVENT.REMOVE_LAYER.equals(e)) {
            Object obj = event.getSharedObject();
            if (obj instanceof String) {
                String uid = (String) obj;
                Enumeration<?> en = drawings.children();
                while (en.hasMoreElements()) {
                    Object node = en.nextElement();
                    if (node instanceof DefaultMutableTreeNode
                        && uid.equals(((DefaultMutableTreeNode) node).getUserObject())) {
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
