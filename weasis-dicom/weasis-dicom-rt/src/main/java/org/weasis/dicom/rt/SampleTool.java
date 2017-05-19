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
package org.weasis.dicom.rt;

import java.awt.*;
import java.beans.PropertyChangeEvent;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Map;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;

import it.cnr.imaa.essi.lablib.gui.checkboxtree.*;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.gui.util.JMVUtils;
import org.weasis.core.api.image.OpManager;
import org.weasis.core.api.image.WindowOp;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.api.media.data.Series;
import org.weasis.core.ui.docking.PluginTool;

import bibliothek.gui.dock.common.CLocation;
import org.weasis.core.ui.editor.SeriesViewerEvent;
import org.weasis.core.ui.editor.image.*;
import org.weasis.core.ui.graphic.model.AbstractLayer;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.codec.display.OverlayOp;
import org.weasis.dicom.codec.display.ShutterOp;
import org.weasis.core.ui.editor.SeriesViewerListener;

public class SampleTool extends PluginTool implements SeriesViewerListener {

    //region Finals

    public static final String BUTTON_NAME = "RT Tool";
    public static final int DockableWidth = javax.swing.UIManager.getLookAndFeel() != null ? javax.swing.UIManager
            .getLookAndFeel().getClass().getName().startsWith("org.pushingpixels") ? 190 : 205 : 205; //$NON-NLS-1$
    protected final ImageViewerEventManager eventManager;
    private final JScrollPane rootPane;

    //endregion

    //region Members

    private final CheckboxTree tree;
    private DefaultMutableTreeNode rootNode = new DefaultMutableTreeNode("rootNode", true); //$NON-NLS-1$
    private TreePath rootPath;
    private boolean initPathSelection;
    private final JCheckBox applyAllViews = new JCheckBox(Messages.getString("RTTool.btn_apply_all"), true);
    private JPanel panel_foot;

    //endregion

    //region Constructors

    public SampleTool(ImageViewerEventManager eventManager) {
        super(BUTTON_NAME, BUTTON_NAME, PluginTool.Type.TOOL, 30);
        this.eventManager = eventManager;
        this.rootPane = new JScrollPane();
        dockable.setTitleIcon(new ImageIcon(SampleTool.class.getResource("/icon/16x16/display.png"))); //$NON-NLS-1$
        setDockableWidth(DockableWidth);

        this.tree = new CheckboxTree();
        setLayout(new BorderLayout(0, 0));
        this.initTree();
    }

    //endregion

    //region Methods

    public void initTree() {
        this.tree.getCheckingModel().setCheckingMode(TreeCheckingModel.CheckingMode.SIMPLE);

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
        // Register event handler
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
                                 org.weasis.dicom.viewer2d.EventManager.getInstance().getSelectedView2dContainer();
                         ArrayList<DefaultView2d<DicomImageElement>> views = null;
                         if (container != null) {
                             if (applyAllViews.isSelected()) {
                                 views = container.getImagePanels();
                             } else {
                                 views = new ArrayList<DefaultView2d<DicomImageElement>>(1);
                                 DefaultView2d<DicomImageElement> view = container.getSelectedImagePane();
                                 if (view != null) {
                                     views.add(view);
                                 }
                             }
                         }
                         if (views != null) {
                             if (rootNode.equals(parent)) {
                                 sendPropertyChangeEvent(views, ActionW.IMAGE_PIX_PADDING.cmd(), selected);
//                                 if (image.equals(parent)) {
//                                     if (selObject != null) {
//                                         if (DICOM_IMAGE_OVERLAY.equals(selObject.toString())) {
//                                             sendPropertyChangeEvent(views, ActionW.IMAGE_OVERLAY.cmd(), selected);
//                                         } else if (DICOM_SHUTTER.equals(selObject.toString())) {
//                                             sendPropertyChangeEvent(views, ActionW.IMAGE_SHUTTER.cmd(), selected);
//                                         } else if (DICOM_PIXEL_PADDING.equals(selObject.toString())) {
//                                             sendPropertyChangeEvent(views, ActionW.IMAGE_PIX_PADDING.cmd(), selected);
//                                         }
//                                     }
                             }
                         }
                     }
                 }
             }
        );

        JPanel panel = new JPanel();
        FlowLayout flowLayout = (FlowLayout) panel.getLayout();
        flowLayout.setAlignment(FlowLayout.LEFT);
        add(panel, BorderLayout.NORTH);
        panel.add(applyAllViews);

        expandTree(tree, rootNode);
        add(new JScrollPane(tree), BorderLayout.CENTER);

        panel_foot = new JPanel();
        panel_foot.setUI(new javax.swing.plaf.PanelUI() { });
        panel_foot.setOpaque(true);
        panel_foot.setBackground(JMVUtils.TREE_BACKROUND);
        add(panel_foot, BorderLayout.SOUTH);
    }

    public void setModel(Map<Integer, Structure> structures) {

        // Prepare root node
        for (Structure struct : structures.values()) {
            this.rootNode.add(new DefaultMutableTreeNode(struct.getRoiName(), false));
        }

        // Setup the tree view model
        DefaultTreeModel model = new DefaultTreeModel(rootNode, false);
        this.tree.setModel(model);


    }


    public void initTreeValues(DefaultView2d view) {
        if (view != null) {
            initPathSelection = true;
            // Image node
            OpManager disOp = view.getDisplayOpManager();
//            initPathSelection(getTreePath(image), view.getImageLayer().isVisible());
//            iniDicomView(disOp, OverlayOp.OP_NAME, OverlayOp.P_SHOW, 0);
//            iniDicomView(disOp, ShutterOp.OP_NAME, ShutterOp.P_SHOW, 1);
//            iniDicomView(disOp, WindowOp.OP_NAME, ActionW.IMAGE_PIX_PADDING.cmd(), 2);
//
//            // Annotations node
//            AnnotationsLayer layer = view.getInfoLayer();
//            if (layer != null) {
//                initPathSelection(getTreePath(dicomInfo), layer.isVisible());
//                Enumeration en = dicomInfo.children();
//                while (en.hasMoreElements()) {
//                    Object node = en.nextElement();
//                    if (node instanceof TreeNode) {
//                        TreeNode checkNode = (TreeNode) node;
//                        initPathSelection(getTreePath(checkNode), layer.getDisplayPreferences(node.toString()));
//                    }
//                }
//            }
//
//            // Drawings node
//            Boolean draw = (Boolean) view.getActionValue(ActionW.DRAW.cmd());
//            initPathSelection(getTreePath(drawings), draw == null ? true : draw);
//            Enumeration en = drawings.children();
//            while (en.hasMoreElements()) {
//                Object node = en.nextElement();
//                if (node instanceof DefaultMutableTreeNode
//                        && ((DefaultMutableTreeNode) node).getUserObject() instanceof AbstractLayer.Identifier) {
//                    DefaultMutableTreeNode checkNode = (DefaultMutableTreeNode) node;
//                    AbstractLayer l = view.getLayerModel().getLayer((AbstractLayer.Identifier) checkNode.getUserObject());
//                    if (l == null) {
//                        // Remove from display if the layer does not exist any more
//                        TreeNode parent = checkNode.getParent();
//                        int index = parent.getIndex(checkNode);
//                        checkNode.removeFromParent();
//                        DefaultTreeModel dtm = (DefaultTreeModel) tree.getModel();
//                        dtm.nodesWereRemoved(parent, new int[] { index }, new TreeNode[] { checkNode });
//                    } else {
//                        initPathSelection(getTreePath(checkNode), l.isVisible());
//                    }
//                }
//            }
//            ImageElement img = view.getImage();
//            if (img != null) {
//                Panner<?> panner = view.getPanner();
//                if (panner != null) {
//
//                    // int cps = panel_foot.getComponentCount();
//                    // if (cps > 0) {
//                    // Component cp = panel_foot.getComponent(0);
//                    // if (cp != panner) {
//                    // if (cp instanceof Thumbnail) {
//                    // ((Thumbnail) cp).removeMouseAndKeyListener();
//                    // }
//                    // panner.registerListeners();
//                    // panel_foot.removeAll();
//                    // panel_foot.add(panner);
//                    // panner.revalidate();
//                    // panner.repaint();
//                    // }
//                    // } else {
//                    // panner.registerListeners();
//                    // panel_foot.add(panner);
//                    // }
//                }
//            }

            initPathSelection = false;
        }
    }

    //endregion

    //region Overrides

    @Override
    public Component getToolComponent() {
        JViewport viewPort = rootPane.getViewport();
        if (viewPort == null) {
            viewPort = new JViewport();
            rootPane.setViewport(viewPort);
        }
        if (viewPort.getView() != this) {
            viewPort.setView(this);
        }
        return rootPane;
    }

    @Override
    public void changingViewContentEvent(SeriesViewerEvent event) {
        SeriesViewerEvent.EVENT e = event.getEventType();
        if (SeriesViewerEvent.EVENT.SELECT_VIEW.equals(e) && event.getSeriesViewer() instanceof ImageViewerPlugin) {
            initTreeValues(((ImageViewerPlugin<?>) event.getSeriesViewer()).getSelectedImagePane());
        } else if (SeriesViewerEvent.EVENT.TOOGLE_INFO.equals(e)) {
            TreeCheckingModel model = tree.getCheckingModel();
            //model.toggleCheckingPath(new TreePath(dicomInfo.getPath()));
        } else if (SeriesViewerEvent.EVENT.ADD_LAYER.equals(e)) {
            Object obj = event.getSharedObject();
            if (obj instanceof AbstractLayer.Identifier) {
                DefaultTreeModel dtm = (DefaultTreeModel) tree.getModel();
                DefaultMutableTreeNode node = new DefaultMutableTreeNode(obj, true);
//                drawings.add(node);
//                dtm.nodesWereInserted(drawings, new int[] { drawings.getIndex(node) });
//                if (event.getSeriesViewer() instanceof ImageViewerPlugin && node.getUserObject() instanceof AbstractLayer.Identifier) {
//                    DefaultView2d<?> pane = ((ImageViewerPlugin<?>) event.getSeriesViewer()).getSelectedImagePane();
//                    if (pane != null) {
//                        AbstractLayer l = pane.getLayerModel().getLayer((AbstractLayer.Identifier) node.getUserObject());
//                        if (l != null && l.isVisible()) {
//                            tree.addCheckingPath(getTreePath(node));
//                        }
//                    }
//                }
            }
        } else if (SeriesViewerEvent.EVENT.REMOVE_LAYER.equals(e)) {
            Object obj = event.getSharedObject();
            if (obj instanceof AbstractLayer.Identifier) {
                AbstractLayer.Identifier id = (AbstractLayer.Identifier) obj;
//                Enumeration en = drawings.children();
//                while (en.hasMoreElements()) {
//                    Object node = en.nextElement();
//                    if (node instanceof DefaultMutableTreeNode
//                            && id.equals(((DefaultMutableTreeNode) node).getUserObject())) {
//                        DefaultMutableTreeNode n = (DefaultMutableTreeNode) node;
//                        TreeNode parent = n.getParent();
//                        int index = parent.getIndex(n);
//                        n.removeFromParent();
//                        DefaultTreeModel dtm = (DefaultTreeModel) tree.getModel();
//                        dtm.nodesWereRemoved(parent, new int[] { index }, new TreeNode[] { n });
//                    }
//                }
            }
        }
    }

    @Override
    protected void changeToolWindowAnchor(CLocation clocation) {
        // TODO Auto-generated method stub
    }

    //endregion

    //region Private Methods

    private void sendPropertyChangeEvent(ArrayList<DefaultView2d<DicomImageElement>> views, String cmd, boolean selected) {
        for (DefaultView2d<DicomImageElement> v : views) {
            v.propertyChange(new PropertyChangeEvent(EventManager.getInstance(), cmd, null, selected));
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

    //endregion

}