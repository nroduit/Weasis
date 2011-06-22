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
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Enumeration;

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
import org.weasis.core.api.gui.util.ActionState;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.gui.util.ToggleButtonListener;
import org.weasis.core.ui.docking.PluginTool;
import org.weasis.core.ui.editor.SeriesViewerEvent;
import org.weasis.core.ui.editor.SeriesViewerEvent.EVENT;
import org.weasis.core.ui.editor.SeriesViewerListener;
import org.weasis.core.ui.editor.image.AnnotationsLayer;
import org.weasis.core.ui.editor.image.DefaultView2d;
import org.weasis.core.ui.editor.image.ImageViewerPlugin;
import org.weasis.core.ui.graphic.model.AbstractLayer;
import org.weasis.core.ui.graphic.model.Tools;
import org.weasis.core.ui.util.CheckNode;
import org.weasis.core.ui.util.TreeLayer;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.viewer2d.EventManager;
import org.weasis.dicom.viewer2d.Messages;
import org.weasis.dicom.viewer2d.View2dContainer;

public class DisplayTool extends PluginTool implements SeriesViewerListener {

    public final static String IMAGE = Messages.getString("DisplayTool.image"); //$NON-NLS-1$
    public final static String DICOM_IMAGE_OVERLAY = Messages.getString("DisplayTool.dicom_overlay"); //$NON-NLS-1$
    public final static String DICOM_ANNOTATIONS = Messages.getString("DisplayTool.dicom_ano"); //$NON-NLS-1$

    public final static String BUTTON_NAME = Messages.getString("DisplayTool.display"); //$NON-NLS-1$

    private final JCheckBox applyAllViews = new JCheckBox(Messages.getString("DisplayTool.btn_apply_all"), true); //$NON-NLS-1$
    private final TreeLayer tree = new TreeLayer();
    private CheckNode image;
    private CheckNode dicomInfo;
    private CheckNode drawings;

    public DisplayTool(String pluginName) {
        super(BUTTON_NAME, pluginName, ToolWindowAnchor.RIGHT);
        setIcon(new ImageIcon(ImageTool.class.getResource("/icon/16x16/display.png")));
        setDockableWidth(210);
        jbInit();

    }

    private void jbInit() {
        setLayout(new BorderLayout(0, 0));
        iniTree();
    }

    public void iniTree() {
        DefaultMutableTreeNode root = new DefaultMutableTreeNode("root"); //$NON-NLS-1$
        root.add(image = new CheckNode(IMAGE, true));
        image.add(new CheckNode(DICOM_IMAGE_OVERLAY, true));
        dicomInfo = new CheckNode(DICOM_ANNOTATIONS, true);
        dicomInfo.add(new CheckNode(AnnotationsLayer.ANNOTATIONS, true));
        dicomInfo.add(new CheckNode(AnnotationsLayer.SCALE, true));
        dicomInfo.add(new CheckNode(AnnotationsLayer.LUT, true));
        dicomInfo.add(new CheckNode(AnnotationsLayer.IMAGE_ORIENTATION, true));
        dicomInfo.add(new CheckNode(AnnotationsLayer.WINDOW_LEVEL, true));
        dicomInfo.add(new CheckNode(AnnotationsLayer.ZOOM, true));
        dicomInfo.add(new CheckNode(AnnotationsLayer.ROTATION, true));
        dicomInfo.add(new CheckNode(AnnotationsLayer.FRAME, true));
        dicomInfo.add(new CheckNode(AnnotationsLayer.PIXEL, true));
        root.add(dicomInfo);
        drawings = new CheckNode(ActionW.DRAW, true);
        drawings.add(new CheckNode(Tools.MEASURE, true));
        drawings.add(new CheckNode(Tools.CROSSLINES, true));
        root.add(drawings);

        DefaultTreeModel model = new DefaultTreeModel(root, false);
        tree.constructTree(model);
        tree.addMouseListener(new MouseAdapter() {

            @Override
            public void mouseReleased(MouseEvent e) {
                TreeLayer layer = (TreeLayer) e.getSource();
                TreePath path = layer.getPathForLocation(e.getX(), e.getY());
                if (path != null) {
                    Object node = path.getLastPathComponent();
                    if (node instanceof CheckNode) {
                        CheckNode checkNode = (CheckNode) node;
                        checkNode.setSelected(!checkNode.isSelected());
                        if (checkNode.isUpdateChildren() && checkNode.getChildCount() > 0) {
                            TreeLayer.fireToChildren(checkNode.children(), checkNode.isSelected());
                        } else if (checkNode.isUpdateParent()) {
                            TreeLayer.fireParentChecked(checkNode);
                        }
                        tree.upadateNode(checkNode);
                        changeLayerSelection(checkNode);
                    }
                }
            }
        });

        JPanel panel = new JPanel();
        FlowLayout flowLayout = (FlowLayout) panel.getLayout();
        flowLayout.setAlignment(FlowLayout.LEFT);
        add(panel, BorderLayout.NORTH);
        panel.add(applyAllViews);

        expandTree(tree, root);
        JScrollPane scrollPane = new JScrollPane(tree);
        add(scrollPane, BorderLayout.CENTER);
    }

    public void changeLayerSelection(CheckNode userObject) {
        String selection = userObject.toString();
        boolean selected = userObject.isSelected();
        boolean allViews = applyAllViews.isSelected();
        ImageViewerPlugin<DicomImageElement> container = EventManager.getInstance().getSelectedView2dContainer();
        ArrayList<DefaultView2d<DicomImageElement>> views = null;
        if (container != null) {
            if (applyAllViews.isSelected()) {
                views = container.getImagePanels();
            } else {
                views = new ArrayList<DefaultView2d<DicomImageElement>>(1);
                views.add(container.getSelectedImagePane());
            }
        }
        if (IMAGE.equals(selection) && views != null) {
            for (DefaultView2d<DicomImageElement> v : views) {
                if (selected != v.getImageLayer().isVisible()) {
                    v.getImageLayer().setVisible(selected);
                    v.repaint();
                }
            }
        } else if (DICOM_IMAGE_OVERLAY.equals(selection)) {
            // TODO need to update all views !
            ActionState overlay = EventManager.getInstance().getAction(ActionW.IMAGE_OVERLAY);
            if (overlay instanceof ToggleButtonListener) {
                ((ToggleButtonListener) overlay).setSelected(userObject.isSelected());
            }
        } else if (DICOM_ANNOTATIONS.equals(selection) && views != null) {
            for (DefaultView2d<DicomImageElement> v : views) {
                if (selected != v.getInfoLayer().isVisible()) {
                    v.getInfoLayer().setVisible(selected);
                    v.repaint();
                }
            }

        } else if (dicomInfo.equals(userObject.getParent()) && views != null) {
            for (DefaultView2d<DicomImageElement> v : views) {
                AnnotationsLayer layer = v.getInfoLayer();
                if (layer != null) {
                    if (layer.setDisplayPreferencesValue(selection, selected)) {
                        v.repaint();
                    }
                }
            }
        } else if (ActionW.DRAW.toString().equals(selection) && views != null) {
            for (DefaultView2d<DicomImageElement> v : views) {
                v.setDrawingsVisibility(selected);
            }
        } else if (drawings.equals(userObject.getParent()) && views != null) {
            if (userObject.getUserObject() instanceof Tools) {
                Tools tool = (Tools) userObject.getUserObject();
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

    public void iniTreeValues(DefaultView2d view) {
        if (view != null) {
            image.setSelected(view.getImageLayer().isVisible());
            TreeNode treeNode = image.getChildAt(0);
            if (treeNode instanceof CheckNode) {
                CheckNode dicom_overlay = (CheckNode) treeNode;
                ActionState overlay = EventManager.getInstance().getAction(ActionW.IMAGE_OVERLAY);
                if (overlay instanceof ToggleButtonListener) {
                    dicom_overlay.setSelected(((ToggleButtonListener) overlay).isSelected());
                }
            }
            tree.upadateNode(image);
            AnnotationsLayer layer = view.getInfoLayer();
            if (layer != null) {
                dicomInfo.setSelected(layer.isVisible());
                Enumeration en = dicomInfo.children();
                while (en.hasMoreElements()) {
                    Object node = en.nextElement();
                    if (node instanceof CheckNode) {
                        CheckNode checkNode = (CheckNode) node;
                        checkNode.setSelected(layer.getDisplayPreferences(node.toString()));
                    }
                }
                tree.upadateNode(dicomInfo);
            }
            Boolean draw = (Boolean) view.getActionValue(ActionW.DRAW.cmd());
            drawings.setSelected(draw == null ? true : draw);
            Enumeration en = drawings.children();
            while (en.hasMoreElements()) {
                Object node = en.nextElement();
                if (node instanceof CheckNode && ((CheckNode) node).getUserObject() instanceof Tools) {
                    CheckNode checkNode = (CheckNode) node;
                    AbstractLayer l = view.getLayerModel().getLayer((Tools) ((CheckNode) node).getUserObject());
                    if (layer != null) {
                        checkNode.setSelected(l.isVisible());
                    }
                }
            }
        }
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
        if (event.getEventType().equals(EVENT.SELECT) && event.getSeriesViewer() instanceof View2dContainer) {
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
