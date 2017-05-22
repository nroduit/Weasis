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

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import javax.swing.ImageIcon;
import javax.swing.JCheckBox;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTree;
import javax.swing.JViewport;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;

import org.dcm4che3.data.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.gui.util.JMVUtils;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.api.media.data.MediaElement;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.media.data.MediaSeriesGroup;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.api.util.StringUtil;
import org.weasis.core.ui.docking.PluginTool;
import org.weasis.core.ui.editor.SeriesViewerEvent;
import org.weasis.core.ui.editor.SeriesViewerListener;
import org.weasis.core.ui.editor.image.ImageViewerPlugin;
import org.weasis.core.ui.editor.image.ViewCanvas;
import org.weasis.core.ui.model.GraphicModel;
import org.weasis.core.ui.model.graphic.Graphic;
import org.weasis.core.ui.model.imp.XmlGraphicModel;
import org.weasis.core.ui.model.layer.LayerType;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.codec.DicomSeries;
import org.weasis.dicom.codec.TagD;
import org.weasis.dicom.codec.geometry.GeometryOfSlice;
import org.weasis.dicom.explorer.DicomModel;
import org.weasis.dicom.viewer2d.EventManager;

import bibliothek.gui.dock.common.CLocation;
import it.cnr.imaa.essi.lablib.gui.checkboxtree.CheckboxTree;
import it.cnr.imaa.essi.lablib.gui.checkboxtree.DefaultCheckboxTreeCellRenderer;
import it.cnr.imaa.essi.lablib.gui.checkboxtree.TreeCheckingEvent;
import it.cnr.imaa.essi.lablib.gui.checkboxtree.TreeCheckingModel;

public class RtDisplayTool extends PluginTool implements SeriesViewerListener {
    private static final Logger LOGGER = LoggerFactory.getLogger(RtDisplayTool.class);

    public static final String BUTTON_NAME = "RT Tool";
    public static final int DockableWidth = javax.swing.UIManager.getLookAndFeel() != null
        ? javax.swing.UIManager.getLookAndFeel().getClass().getName().startsWith("org.pushingpixels") ? 190 : 205 : 205; //$NON-NLS-1$

    private final JScrollPane rootPane;

    private final CheckboxTree tree;
    private boolean initPathSelection;
    private DefaultMutableTreeNode rootNode = new DefaultMutableTreeNode("rootNode", true); //$NON-NLS-1$
    private TreePath rootPath;
    private final JCheckBox applyAllViews = new JCheckBox("View All", true);
    private JPanel panel_foot;
    private DefaultMutableTreeNode nodeStructure;
    private RtSet rtSet;
    private RtSpecialElement selectedStructure;

    public RtDisplayTool() {
        super(BUTTON_NAME, BUTTON_NAME, PluginTool.Type.TOOL, 30);
        this.rootPane = new JScrollPane();
        dockable.setTitleIcon(new ImageIcon(RtDisplayTool.class.getResource("/icon/16x16/rtDose.png"))); //$NON-NLS-1$
        setDockableWidth(DockableWidth);

        this.tree = new CheckboxTree();
        setLayout(new BorderLayout(0, 0));
        this.initTree();
    }

    public void initTree() {
        this.tree.getCheckingModel().setCheckingMode(TreeCheckingModel.CheckingMode.SIMPLE);

        DefaultTreeModel model = new DefaultTreeModel(rootNode, false);
        tree.setModel(model);
        nodeStructure = new DefaultMutableTreeNode("Structure", true);
        rootNode.add(nodeStructure);
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

        panel_foot = new JPanel();
        panel_foot.setUI(new javax.swing.plaf.PanelUI() {
        });
        panel_foot.setOpaque(true);
        panel_foot.setBackground(JMVUtils.TREE_BACKROUND);
        add(panel_foot, BorderLayout.SOUTH);
    }

    private void initPathSelection(TreePath path, boolean selected) {
        if (selected) {
            tree.addCheckingPath(path);
        } else {
            tree.removeCheckingPath(path);
        }
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

            ImageViewerPlugin<DicomImageElement> container = EventManager.getInstance().getSelectedView2dContainer();
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
                RtSet rt = rtSet;
                if (rt != null && (selObject == nodeStructure || parent == nodeStructure)) {
                    for (ViewCanvas<DicomImageElement> v : views) {
                        showGraphic(rt, getStructureSelection(), v);
                    }
                }
            }
        }
    }

    private List<StructureLayer> getStructureSelection() {
        ArrayList<StructureLayer> list = new ArrayList<>();
        if (tree.getCheckingModel().isPathChecked(new TreePath(nodeStructure.getPath()))) {
            TreePath[] paths = tree.getCheckingModel().getCheckingPaths();
            for (TreePath treePath : paths) {
                DefaultMutableTreeNode node = (DefaultMutableTreeNode) treePath.getLastPathComponent();
                if (node.getUserObject() instanceof StructureLayer) {
                    list.add((StructureLayer) node.getUserObject());
                }
            }
        }
        return list;
    }

    private static boolean containsStructure(List<StructureLayer> list, Structure s) {
        for (StructureLayer structure : list) {
            if (structure.getStructure().getRoiNumber() == s.getRoiNumber()
                && structure.getStructure().getRoiName().equals(s.getRoiName())) {
                return true;
            }
        }
        return false;
    }

    private static void showGraphic(RtSet rt, List<StructureLayer> list, ViewCanvas<?> v) {
        if (rt != null) {
            ImageElement dicom = v.getImage();
            if (dicom instanceof DicomImageElement) {
                GeometryOfSlice geometry = ((DicomImageElement) dicom).getDispSliceGeometry();
                List<Contour> contours =
                    rt.getContourMap().get(TagD.getTagValue(dicom, Tag.SOPInstanceUID, String.class));
                if (contours != null) {
                    GraphicModel modelList = (GraphicModel) dicom.getTagValue(TagW.PresentationModel);
                    // After getting a new image iterator, update the measurements
                    if (modelList == null) {
                        modelList = new XmlGraphicModel(dicom);
                        dicom.setTag(TagW.PresentationModel, modelList);
                    } else {
                        modelList.deleteByLayerType(LayerType.DICOM_RT);
                    }

                    for (Contour c : contours) {
                        StructureLayer structLayer = c.getStructure();
                        Structure struct = structLayer.getStructure();
                        if (containsStructure(list, struct)) {
                            Graphic graphic = c.getGraphic(geometry);
                            if (graphic != null) {
                                graphic.setLineThickness((float) struct.getThickness());
                                graphic.setPaint(struct.getColor());
                                graphic.setLayerType(LayerType.DICOM_RT);
                                // graphic.setLabelVisible(labelVisible);
                                // graphic.setClassID(classID);
                                // graphic.setFilled(filled);
                                graphic.setLayer(structLayer.getLayer());
                                for (PropertyChangeListener listener : modelList.getGraphicsListeners()) {
                                    graphic.addPropertyChangeListener(listener);
                                }
                                modelList.addGraphic(graphic);
                            }
                        }
                    }
                    v.getJComponent().repaint();
                }
            }
        }
    }

    public void updateTree(ViewCanvas<?> viewCanvas) {
        RtSet rt = rtSet;
        if (rt == null) {
            this.nodeStructure.removeAllChildren();
            DefaultTreeModel model = new DefaultTreeModel(rootNode, false);
            tree.setModel(model);
            return;
        }

        initPathSelection = true;
        try {
            // TODO show combo to select if more than one
            RtSpecialElement oldStructure = selectedStructure;
            selectedStructure = rt.getFirstStructure();
            boolean update = !Objects.equals(oldStructure, selectedStructure);
            if (update) {
                this.nodeStructure.removeAllChildren();

                Map<Integer, StructureLayer> structures = rt.getStructureSet(selectedStructure);
                // Prepare root node
                for (StructureLayer struct : structures.values()) {
                    DefaultMutableTreeNode node = new DefaultMutableTreeNode(struct, false);
                    this.nodeStructure.add(node);
                    initPathSelection(new TreePath(node.getPath()), true);
                }
                DefaultTreeModel model = new DefaultTreeModel(rootNode, false);
                tree.setModel(model);
                initPathSelection(new TreePath(nodeStructure.getPath()), true);
                for (Enumeration children = nodeStructure.children(); children.hasMoreElements();) {
                    DefaultMutableTreeNode dtm = (DefaultMutableTreeNode) children.nextElement();
                    initPathSelection(new TreePath(dtm.getPath()), true);
                }
                expandTree(tree, rootNode);
            }
        } finally {
            initPathSelection = false;
        }
        showGraphic(rt, getStructureSelection(), viewCanvas);

    }

    public void initTreeValues(ViewCanvas<?> viewCanvas) {
        if (viewCanvas != null) {
            MediaSeries<?> dcmSeries = viewCanvas.getSeries();
            if (dcmSeries != null) {
                DicomModel dicomModel = (DicomModel) dcmSeries.getTagValue(TagW.ExplorerModel);
                if (dicomModel != null) {
                    MediaSeriesGroup patient = dicomModel.getParent(dcmSeries, DicomModel.patient);
                    if (patient != null) {
                        String frameOfReferenceUID = TagD.getTagValue(dcmSeries, Tag.FrameOfReferenceUID, String.class);
                        List<MediaElement> list =
                            getRelatedSpecialElements(dicomModel, patient, frameOfReferenceUID);
                        if (!list.isEmpty() && (rtSet == null || !rtSet.getRtElements().equals(list))) {
                            rtSet = new RtSet(list);
                        }
                        updateTree(viewCanvas);
                    }
                }
            }
        }
    }

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
        if (SeriesViewerEvent.EVENT.SELECT.equals(e) && event.getSeriesViewer() instanceof ImageViewerPlugin) {
            initTreeValues(((ImageViewerPlugin<?>) event.getSeriesViewer()).getSelectedImagePane());
        }
    }

    @Override
    protected void changeToolWindowAnchor(CLocation clocation) {
        // TODO Auto-generated method stub
    }

    private static void expandTree(JTree tree, DefaultMutableTreeNode start) {
        for (Enumeration children = start.children(); children.hasMoreElements();) {
            DefaultMutableTreeNode dtm = (DefaultMutableTreeNode) children.nextElement();
            if (!dtm.isLeaf()) {
                TreePath tp = new TreePath(dtm.getPath());
                tree.expandPath(tp);
                expandTree(tree, dtm);
            }
        }
        return;
    }

    private static List<MediaElement> getRelatedSpecialElements(DicomModel model, MediaSeriesGroup patient,
        String frameOfReferenceUID) {
        List<MediaElement> specialElementList = new ArrayList<>();
        if (StringUtil.hasText(frameOfReferenceUID)) {
            for (MediaSeriesGroup st : model.getChildren(patient)) {
                for (MediaSeriesGroup s : model.getChildren(st)) {
                    String frameUID = TagD.getTagValue(s, Tag.FrameOfReferenceUID, String.class);
                    String modality = TagD.getTagValue(s, Tag.Modality, String.class);
                    if (frameOfReferenceUID.equals(frameUID) || "RTSTRUCT".equals(modality)) {
                        List<RtSpecialElement> list = DicomModel.getSpecialElements(s, RtSpecialElement.class);
                        if (!list.isEmpty()) {
                            specialElementList.addAll(list);
                        }

                        if ("RTDOSE".equals(modality) && s instanceof DicomSeries) {
                            for (DicomImageElement media : ((DicomSeries) s).getMedias(null, null)) {
                                if ("RTDOSE".equals(TagD.getTagValue(media, Tag.Modality))) {
                                    specialElementList.add(media);
                                }
                            }
                        }
                    }
                }
            }
        }
        return specialElementList;
    }
}