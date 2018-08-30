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
import org.weasis.core.api.gui.util.SliderChangeListener;
import org.weasis.core.api.media.data.Series;
import org.weasis.core.api.util.LangUtil;
import org.weasis.core.ui.docking.PluginTool;
import org.weasis.core.ui.editor.SeriesViewerEvent;
import org.weasis.core.ui.editor.SeriesViewerEvent.EVENT;
import org.weasis.core.ui.editor.SeriesViewerListener;
import org.weasis.core.ui.editor.image.ImageViewerPlugin;
import org.weasis.core.ui.editor.image.ViewCanvas;
import org.weasis.core.ui.model.layer.LayerAnnotation;
import org.weasis.core.ui.model.layer.LayerType;
import org.weasis.dicom.codec.DicomImageElement;

import bibliothek.gui.dock.common.CLocation;
import br.com.animati.texture.mpr3dview.GUIManager;
import br.com.animati.texture.mpr3dview.internal.Messages;
import it.cnr.imaa.essi.lablib.gui.checkboxtree.CheckboxTree;
import it.cnr.imaa.essi.lablib.gui.checkboxtree.DefaultCheckboxTreeCellRenderer;
import it.cnr.imaa.essi.lablib.gui.checkboxtree.TreeCheckingEvent;
import it.cnr.imaa.essi.lablib.gui.checkboxtree.TreeCheckingModel;
import it.cnr.imaa.essi.lablib.gui.checkboxtree.TreeCheckingModel.CheckingMode;

@SuppressWarnings("serial")
public class DisplayTool extends PluginTool implements SeriesViewerListener {

    public static final String DICOM_ANNOTATIONS = Messages.getString("DisplayTool.dicom_ano"); //$NON-NLS-1$

    public static final String BUTTON_NAME = Messages.getString("DisplayTool.display"); //$NON-NLS-1$

    private final JCheckBox applyAllViews = new JCheckBox(Messages.getString("DisplayTool.btn_apply_all"), true); //$NON-NLS-1$
    private final CheckboxTree tree;
    private boolean initPathSelection;
    private DefaultMutableTreeNode rootNode = new DefaultMutableTreeNode("rootNode", true); //$NON-NLS-1$

    private DefaultMutableTreeNode dicomInfo;
    private DefaultMutableTreeNode drawings;
    private DefaultMutableTreeNode crosslines;
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
        drawings = new DefaultMutableTreeNode(ActionW.DRAWINGS, true);
        rootNode.add(drawings);
        crosslines = new DefaultMutableTreeNode(LayerType.CROSSLINES, false);
        drawings.add(crosslines);

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

    private void treeValueChanged(TreeCheckingEvent e) {
        if (!initPathSelection) {
            TreePath path = e.getPath();
            boolean selected = e.isCheckedPath();
            Object selObject = path.getLastPathComponent();
            Object parent = null;
            if (path.getParentPath() != null) {
                parent = path.getParentPath().getLastPathComponent();
            }

            ImageViewerPlugin<DicomImageElement> container = GUIManager.getInstance().getSelectedView2dContainer();
            List<ViewCanvas<DicomImageElement>> views = null;
            if (container != null) {
                if (applyAllViews.isSelected()) {
                    views = container.getImagePanels();
                } else {
                    views = new ArrayList<>(1);
                    Optional.ofNullable(container.getSelectedImagePane()).ifPresent(views::add);
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
                            Series<?> series = (Series<?>) v.getSeries();
                            GUIManager.getInstance().fireSeriesViewerListeners(
                                new SeriesViewerEvent(container, series, v.getImage(), EVENT.ANONYM));

                        }
                    }
                } else if (drawings.equals(parent)) {
                    if (selObject == crosslines) {
                        for (ViewCanvas<DicomImageElement> v : views) {
                            if ((Boolean) v.getActionValue(LayerType.CROSSLINES.name()) != selected) {
                                v.setActionsInView(LayerType.CROSSLINES.name(), selected);
                                if (!selected) {
                                    v.getGraphicManager().deleteByLayerType(LayerType.CROSSLINES);
                                    v.getJComponent().repaint();
                                } else {
                                    // Force to redraw crosslines
                                    Optional
                                        .ofNullable(
                                            (SliderChangeListener) v.getEventManager().getAction(ActionW.SCROLL_SERIES))
                                        .ifPresent(a -> a.stateChanged(a.getSliderModel()));
                                }
                            }
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
        initPathSelection(getTreePath(drawings),
            LangUtil.getNULLtoTrue((Boolean) view.getActionValue(ActionW.DRAWINGS.cmd())));
        initPathSelection(getTreePath(crosslines),
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
        return new TreePath(list.toArray(new TreeNode[list.size()]));
    }

    @Override
    public Component getToolComponent() {
        return this;
    }

    @Override
    protected void changeToolWindowAnchor(CLocation clocation) {
        // TODO Auto-generated method stub

    }

    @Override
    public void changingViewContentEvent(SeriesViewerEvent event) {
        // TODO should recieved layer changes
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
