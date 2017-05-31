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
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.swing.ImageIcon;
import javax.swing.JComboBox;
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
    private final JComboBox<RtSpecialElement> comboRtElements = new JComboBox<>();
    private JPanel panel_foot;
    private final DefaultMutableTreeNode nodeStructures;
    private final DefaultMutableTreeNode nodeIsodoses;
    private RtSet rtSet;
    private DicomImageElement selectedDosePlane;
    private final transient ItemListener structureChangeListener = e -> {
        if (e.getStateChange() == ItemEvent.SELECTED) {
            updateTree((RtSpecialElement) e.getItem());
        }
    };

    public RtDisplayTool() {
        super(BUTTON_NAME, BUTTON_NAME, PluginTool.Type.TOOL, 30);
        this.rootPane = new JScrollPane();
        dockable.setTitleIcon(new ImageIcon(RtDisplayTool.class.getResource("/icon/16x16/rtDose.png"))); //$NON-NLS-1$
        setDockableWidth(DockableWidth);

        this.tree = new CheckboxTree();
        setLayout(new BorderLayout(0, 0));
        nodeStructures = new DefaultMutableTreeNode("Structures", true);
        nodeIsodoses = new DefaultMutableTreeNode("Isodoses", true);
        this.initTree();
    }

    public void initTree() {
        this.tree.getCheckingModel().setCheckingMode(TreeCheckingModel.CheckingMode.SIMPLE);

        DefaultTreeModel model = new DefaultTreeModel(rootNode, false);
        tree.setModel(model);

        rootNode.add(nodeStructures);
        rootNode.add(nodeIsodoses);
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
        panel.add(comboRtElements);

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
                views = container.getImagePanels();
            }
            if (views != null) {
                RtSet rt = rtSet;
                if (rt != null && (selObject == nodeStructures || parent == nodeStructures)) {
                    for (ViewCanvas<DicomImageElement> v : views) {
                        showGraphic(rt, getStructureSelection(), v);
                    }
                }
            }
        }
    }

    private List<StructureLayer> getStructureSelection() {
        ArrayList<StructureLayer> list = new ArrayList<>();
        if (tree.getCheckingModel().isPathChecked(new TreePath(nodeStructures.getPath()))) {
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

                // List of detected contours from RtSet
                List<Contour> contours =
                    rt.getContourMap().get(TagD.getTagValue(dicom, Tag.SOPInstanceUID, String.class));

                // List of detected doses from RtSet
                Dose dose = rt.getFirstDose();

                // List of detected plans from RtSet
                Plan plan = rt.getFirstPlan();

                // Contours layer
                if (contours != null) {
                    GraphicModel modelList = (GraphicModel) dicom.getTagValue(TagW.PresentationModel);
                    // After getting a new image iterator, update the measurements
                    if (modelList == null) {
                        modelList = new XmlGraphicModel(dicom);
                        dicom.setTag(TagW.PresentationModel, modelList);
                    } else {
                        modelList.deleteByLayerType(LayerType.DICOM_RT);
                    }

                    // Check which contours should be rendered
                    for (Contour c : contours) {
                        StructureLayer structLayer = (StructureLayer) c.getLayer();
                        Structure struct = structLayer.getStructure();
                        if (containsStructure(list, struct)) {

                            // If dose is loaded
                            if (dose != null) {

                                // If DVH exists for the structure
                                Dvh structureDvh = dose.get(struct.getRoiNumber());
                                if (structureDvh != null) {

                                    // Absolute volume is defined in DVH (in cm^3) so use it
                                    if (structureDvh.getDvhVolumeUnit().equals("CM3")) {
                                        struct.setVolume(structureDvh.getDvhData()[0]);
                                    }
                                    // Otherwise recalculate structure volume
                                    else {
                                        struct.recalculateVolume();
                                    }

                                    // If plan is loaded with prescribed treatment dose calculate DVH statistics
                                    if (plan != null) {
                                        RtSet.calculatePercentualDvhDose(structureDvh.getDvhMinimumDose(), plan.getRxDose());
                                        RtSet.calculatePercentualDvhDose(structureDvh.getDvhMaximumDose(), plan.getRxDose());
                                        RtSet.calculatePercentualDvhDose(structureDvh.getDvhMeanDose(), plan.getRxDose());
                                    }
                                }
                            }

                            // Structure graphics
                            Graphic graphic = c.getGraphic(geometry);
                            if (graphic != null) {
                                
                                graphic.setLineThickness((float) struct.getThickness());
                                graphic.setPaint(struct.getColor());
                                graphic.setLayerType(LayerType.DICOM_RT);
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

    public void updateCanvas(ViewCanvas<?> viewCanvas) {
        RtSet rt = rtSet;
        if (rt == null) {
            this.nodeStructures.removeAllChildren();
            DefaultTreeModel model = new DefaultTreeModel(rootNode, false);
            tree.setModel(model);
            return;
        }

        comboRtElements.removeItemListener(structureChangeListener);
        RtSpecialElement oldStructure = (RtSpecialElement) comboRtElements.getSelectedItem();
        comboRtElements.removeAllItems();
        Set<RtSpecialElement> rtElements = rt.getStructures().keySet();
        rtElements.forEach(comboRtElements::addItem);

        boolean update = !rtElements.contains(oldStructure);
        if (update) {
            RtSpecialElement selectedStructure = rt.getFirstStructure();
            comboRtElements.setSelectedItem(selectedStructure);
            updateTree(selectedStructure);
        } else {
            comboRtElements.setSelectedItem(oldStructure);
        }
        comboRtElements.addItemListener(structureChangeListener);

        // Update selected dose plane
        ImageElement dicom = viewCanvas.getImage();
        if (dicom instanceof DicomImageElement) {
            GeometryOfSlice geometry = ((DicomImageElement) dicom).getDispSliceGeometry();

            // List of detected doses from RtSet
            Dose dose = rt.getFirstDose();

            if (dose != null) {
                MediaElement dosePlane = dose.getDosePlaneBySlice(geometry.getTLHC().getZ());
                if (dosePlane != null) {
                    selectedDosePlane = ((DicomImageElement) dosePlane);
                }
            }
        }

        showGraphic(rt, getStructureSelection(), viewCanvas);

    }

    public void updateTree(RtSpecialElement selectedStructure) {
        RtSet rt = rtSet;
        if (rt == null) {
            this.nodeStructures.removeAllChildren();
            DefaultTreeModel model = new DefaultTreeModel(rootNode, false);
            tree.setModel(model);
            return;
        }

        initPathSelection = true;
        try {

            nodeStructures.removeAllChildren();
            nodeIsodoses.removeAllChildren();

            Map<Integer, StructureLayer> structures = rt.getStructureSet(selectedStructure);
            // Prepare root node
            if (structures != null) {
                for (StructureLayer struct : structures.values()) {
                    DefaultMutableTreeNode node = new DefaultMutableTreeNode(struct, false);
                    this.nodeStructures.add(node);
                    initPathSelection(new TreePath(node.getPath()), true);
                }
            }
            DefaultTreeModel model = new DefaultTreeModel(rootNode, false);
            tree.setModel(model);
            initPathSelection(new TreePath(nodeStructures.getPath()), true);
            for (Enumeration<?> children = nodeStructures.children(); children.hasMoreElements();) {
                DefaultMutableTreeNode dtm = (DefaultMutableTreeNode) children.nextElement();
                initPathSelection(new TreePath(dtm.getPath()), true);
            }
            expandTree(tree, rootNode);

        } finally {
            initPathSelection = false;
        }

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
                        List<MediaElement> list = getRelatedSpecialElements(dicomModel, patient, frameOfReferenceUID);
                        if (!list.isEmpty() && (rtSet == null || !rtSet.getRtElements().equals(list))) {
                            rtSet = new RtSet(list);
                        }
                        updateCanvas(viewCanvas);
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

        // TODO: dose information for mouse pointing pixel
        // selectedDosePlane.getImage().getData().getPixel(100, 100, (double[]) null);
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