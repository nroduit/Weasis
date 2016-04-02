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

import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.gui.util.JMVUtils;
import org.weasis.core.api.image.OpManager;
import org.weasis.core.api.image.WindowOp;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.api.media.data.Series;
import org.weasis.core.ui.docking.PluginTool;
import org.weasis.core.ui.editor.SeriesViewerEvent;
import org.weasis.core.ui.editor.SeriesViewerEvent.EVENT;
import org.weasis.core.ui.editor.SeriesViewerListener;
import org.weasis.core.ui.editor.image.AnnotationsLayer;
import org.weasis.core.ui.editor.image.ImageViewerPlugin;
import org.weasis.core.ui.editor.image.Panner;
import org.weasis.core.ui.editor.image.ViewCanvas;
import org.weasis.core.ui.graphic.model.AbstractLayer;
import org.weasis.core.ui.graphic.model.AbstractLayer.Identifier;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.codec.display.OverlayOp;
import org.weasis.dicom.codec.display.ShutterOp;
import org.weasis.dicom.viewer2d.EventManager;
import org.weasis.dicom.viewer2d.Messages;

import bibliothek.gui.dock.common.CLocation;
import it.cnr.imaa.essi.lablib.gui.checkboxtree.CheckboxTree;
import it.cnr.imaa.essi.lablib.gui.checkboxtree.DefaultCheckboxTreeCellRenderer;
import it.cnr.imaa.essi.lablib.gui.checkboxtree.TreeCheckingEvent;
import it.cnr.imaa.essi.lablib.gui.checkboxtree.TreeCheckingListener;
import it.cnr.imaa.essi.lablib.gui.checkboxtree.TreeCheckingModel;
import it.cnr.imaa.essi.lablib.gui.checkboxtree.TreeCheckingModel.CheckingMode;

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

    private DefaultMutableTreeNode imageNode;
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
        imageNode = new DefaultMutableTreeNode(IMAGE, true);
        imageNode.add(new DefaultMutableTreeNode(DICOM_IMAGE_OVERLAY, false));
        imageNode.add(new DefaultMutableTreeNode(DICOM_SHUTTER, false));
        imageNode.add(new DefaultMutableTreeNode(DICOM_PIXEL_PADDING, false));
        rootNode.add(imageNode);
        dicomInfo = new DefaultMutableTreeNode(DICOM_ANNOTATIONS, true);
        dicomInfo.add(new DefaultMutableTreeNode(AnnotationsLayer.ANNOTATIONS, true));
        minAnnotations = new DefaultMutableTreeNode(AnnotationsLayer.MIN_ANNOTATIONS, false);
        dicomInfo.add(minAnnotations);
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
        drawings.add(new DefaultMutableTreeNode(AbstractLayer.MEASURE, true));
        drawings.add(new DefaultMutableTreeNode(AbstractLayer.CROSSLINES, true));
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
                    List<ViewCanvas<DicomImageElement>> views = null;
                    if (container != null) {
                        if (applyAllViews.isSelected()) {
                            views = container.getImagePanels();
                        } else {
                            views = new ArrayList<ViewCanvas<DicomImageElement>>(1);
                            ViewCanvas<DicomImageElement> view = container.getSelectedImagePane();
                            if (view != null) {
                                views.add(view);
                            }
                        }
                    }
                    if (views != null) {
                        if (rootNode.equals(parent)) {
                            if (imageNode.equals(selObject)) {
                                for (ViewCanvas<DicomImageElement> v : views) {
                                    if (selected != v.getImageLayer().isVisible()) {
                                        v.getImageLayer().setVisible(selected);
                                        v.getJComponent().repaint();
                                    }
                                }
                            } else if (dicomInfo.equals(selObject)) {
                                for (ViewCanvas<DicomImageElement> v : views) {
                                    AnnotationsLayer layer = v.getInfoLayer();
                                    if (layer != null) {
                                        if (selected != v.getInfoLayer().isVisible()) {
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
                                for (ViewCanvas<DicomImageElement> v : views) {
                                    AnnotationsLayer layer = v.getInfoLayer();
                                    if (layer != null) {
                                        if (layer.setDisplayPreferencesValue(selObject.toString(), selected)) {
                                            v.getJComponent().repaint();
                                        }
                                    }
                                }
                                if (AnnotationsLayer.ANONYM_ANNOTATIONS.equals(selObject.toString())) {
                                    // Send message to listeners, only selected view
                                    ViewCanvas<DicomImageElement> v = container.getSelectedImagePane();
                                    Series series = (Series) v.getSeries();
                                    EventManager.getInstance().fireSeriesViewerListeners(
                                        new SeriesViewerEvent(container, series, v.getImage(), EVENT.ANONYM));

                                }
                            }
                        } else if (drawings.equals(parent)) {
                            if (selObject instanceof DefaultMutableTreeNode) {
                                if (((DefaultMutableTreeNode) selObject).getUserObject() instanceof Identifier) {
                                    Identifier layerID =
                                        (Identifier) ((DefaultMutableTreeNode) selObject).getUserObject();
                                    for (ViewCanvas<DicomImageElement> v : views) {
                                        AbstractLayer layer = v.getLayerModel().getLayer(layerID);
                                        if (layer != null) {
                                            if (layer.isVisible() != selected) {
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

    private void sendPropertyChangeEvent(List<ViewCanvas<DicomImageElement>> views, String cmd, boolean selected) {
        for (ViewCanvas<DicomImageElement> v : views) {
            v.propertyChange(new PropertyChangeEvent(EventManager.getInstance(), cmd, null, selected));
        }
    }

    private void iniDicomView(OpManager disOp, String op, String param, int index) {
        TreeNode treeNode = imageNode.getChildAt(index);
        if (treeNode != null) {
            Boolean val = (Boolean) disOp.getParamValue(op, param);
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

    public void iniTreeValues(ViewCanvas view) {
        if (view != null) {
            initPathSelection = true;
            // Image node
            OpManager disOp = view.getDisplayOpManager();
            initPathSelection(getTreePath(imageNode), view.getImageLayer().isVisible());
            iniDicomView(disOp, OverlayOp.OP_NAME, OverlayOp.P_SHOW, 0);
            iniDicomView(disOp, ShutterOp.OP_NAME, ShutterOp.P_SHOW, 1);
            iniDicomView(disOp, WindowOp.OP_NAME, ActionW.IMAGE_PIX_PADDING.cmd(), 2);

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
                    && ((DefaultMutableTreeNode) node).getUserObject() instanceof Identifier) {
                    DefaultMutableTreeNode checkNode = (DefaultMutableTreeNode) node;
                    AbstractLayer l = view.getLayerModel().getLayer((Identifier) checkNode.getUserObject());
                    if (l == null) {
                        // Remove from display if the layer does not exist any more
                        TreeNode parent = checkNode.getParent();
                        int index = parent.getIndex(checkNode);
                        checkNode.removeFromParent();
                        DefaultTreeModel dtm = (DefaultTreeModel) tree.getModel();
                        dtm.nodesWereRemoved(parent, new int[] { index }, new TreeNode[] { checkNode });
                    } else {
                        initPathSelection(getTreePath(checkNode), l.isVisible());
                    }
                }
            }
            ImageElement img = view.getImage();
            if (img != null) {
                Panner<?> panner = view.getPanner();
                if (panner != null) {

                    // int cps = panelFoot.getComponentCount();
                    // if (cps > 0) {
                    // Component cp = panelFoot.getComponent(0);
                    // if (cp != panner) {
                    // if (cp instanceof Thumbnail) {
                    // ((Thumbnail) cp).removeMouseAndKeyListener();
                    // }
                    // panner.registerListeners();
                    // panelFoot.removeAll();
                    // panelFoot.add(panner);
                    // panner.revalidate();
                    // panner.repaint();
                    // }
                    // } else {
                    // panner.registerListeners();
                    // panelFoot.add(panner);
                    // }
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
            ViewCanvas<DicomImageElement> selView = EventManager.getInstance().getSelectedViewPane();
            // Use an intermediate state of the minimal DICOM information. Triggered only from the shortcut SPACE or I
            boolean minDisp =
                selView != null && selView.getInfoLayer().getDisplayPreferences(AnnotationsLayer.MIN_ANNOTATIONS);

            if (checked && !minDisp) {
                ImageViewerPlugin<DicomImageElement> container =
                    EventManager.getInstance().getSelectedView2dContainer();
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
                        AnnotationsLayer layer = v.getInfoLayer();
                        if (layer != null) {
                            layer.setVisible(true);
                            if (layer.setDisplayPreferencesValue(AnnotationsLayer.MIN_ANNOTATIONS, true)) {
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
            if (obj instanceof Identifier) {
                DefaultTreeModel dtm = (DefaultTreeModel) tree.getModel();
                DefaultMutableTreeNode node = new DefaultMutableTreeNode(obj, true);
                drawings.add(node);
                dtm.nodesWereInserted(drawings, new int[] { drawings.getIndex(node) });
                if (event.getSeriesViewer() instanceof ImageViewerPlugin
                    && node.getUserObject() instanceof Identifier) {
                    ViewCanvas<?> pane = ((ImageViewerPlugin<?>) event.getSeriesViewer()).getSelectedImagePane();
                    if (pane != null) {
                        AbstractLayer l = pane.getLayerModel().getLayer((Identifier) node.getUserObject());
                        if (l != null && l.isVisible()) {
                            tree.addCheckingPath(getTreePath(node));
                        }
                    }
                }
            }
        } else if (EVENT.REMOVE_LAYER.equals(e)) {
            Object obj = event.getSharedObject();
            if (obj instanceof Identifier) {
                Identifier id = (Identifier) obj;
                Enumeration en = drawings.children();
                while (en.hasMoreElements()) {
                    Object node = en.nextElement();
                    if (node instanceof DefaultMutableTreeNode
                        && id.equals(((DefaultMutableTreeNode) node).getUserObject())) {
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
