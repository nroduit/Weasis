/*******************************************************************************
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.weasis.dicom.rt;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.MouseEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import javax.swing.*;
import javax.swing.SwingWorker.StateValue;
import javax.swing.border.TitledBorder;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;

import org.dcm4che3.data.Tag;
import org.knowm.xchart.XChartPanel;
import org.knowm.xchart.XYChart;
import org.knowm.xchart.XYChartBuilder;
import org.knowm.xchart.XYSeries;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.gui.task.CircularProgressBar;
import org.weasis.core.api.gui.util.JMVUtils;
import org.weasis.core.api.gui.util.JSliderW;
import org.weasis.core.api.gui.util.SliderChangeListener;
import org.weasis.core.api.gui.util.WinUtil;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.api.media.data.MediaElement;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.media.data.MediaSeriesGroup;
import org.weasis.core.api.media.data.SoftHashMap;
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

/**
 * 
 * @author Tomas Skripcak
 * @author Nicolas Roduit
 */
public class RtDisplayTool extends PluginTool implements SeriesViewerListener {
    private static final Logger LOGGER = LoggerFactory.getLogger(RtDisplayTool.class);

    public static final String BUTTON_NAME = "RT Tool";

    private static final SoftHashMap<String, RtSet> RtSet_Cache = new SoftHashMap<>();

    private final JTabbedPane tabbedPane = new JTabbedPane();
    private final JScrollPane rootPane;
    private final JButton btnLoad = new JButton("Load RT");
    private final JCheckBox cbDvhRecalculate = new JCheckBox("DVH recalculate");

    private final CheckboxTree treeStructures;
    private final CheckboxTree treeIsodoses;
    private boolean initPathSelection;
    private DefaultMutableTreeNode rootNodeStructures = new DefaultMutableTreeNode("rootNode", true); //$NON-NLS-1$
    private DefaultMutableTreeNode rootNodeIsodoses = new DefaultMutableTreeNode("rootNode", true); //$NON-NLS-1$
    private final JLabel lblRtStructureSet = new JLabel("Structure Set:");
    private final JComboBox<RtSpecialElement> comboRtStructureSet = new JComboBox<>();
    private final JLabel lblRtPlan = new JLabel("Plan:");
    private final JComboBox<RtSpecialElement> comboRtPlan = new JComboBox<>();
    private final JLabel lblRtPlanName = new JLabel();
    private final JLabel lblRtPlanDose = new JLabel("Dose:");
    private final JTextField txtRtPlanDoseValue = new JTextField();
    private final JLabel lblRtPlanDoseUnit = new JLabel("cGy");
    private final DefaultMutableTreeNode nodeStructures;
    private final DefaultMutableTreeNode nodeIsodoses;
    private final CircularProgressBar progressBar = new CircularProgressBar();
    private RtSet rtSet;
    private final transient ItemListener structureChangeListener = e -> {
        if (e.getStateChange() == ItemEvent.SELECTED) {
            updateTree((RtSpecialElement) e.getItem(), null);
        }
    };
    private final transient ItemListener planChangeListener = e -> {
        if (e.getStateChange() == ItemEvent.SELECTED) {
            updateTree(null, (RtSpecialElement) e.getItem());
        }
    };
    private final JPanel panelFoot = new JPanel();
    private final JSliderW slider;
    private JPanel panelHead;
    private JPanel panelDvh;
    private JButton btnShowDvh;

    public RtDisplayTool() {
        super(BUTTON_NAME, BUTTON_NAME, PluginTool.Type.TOOL, 30);
        this.setLayout(new BorderLayout(0, 0));
        this.rootPane = new JScrollPane();
        this.dockable.setTitleIcon(new ImageIcon(RtDisplayTool.class.getResource("/icon/16x16/rtDose.png"))); //$NON-NLS-1$
        this.setDockableWidth(350);
        this.btnLoad.setToolTipText("Populate RT objects from loaded DICOM study");
        // By default recalculate DVH only when it is missing for structure
        this.cbDvhRecalculate.setSelected(false);
        this.cbDvhRecalculate
            .setToolTipText("When enabled recalculate DVH for all structures, otherwise recalculate only missing DVH");
        this.lblRtStructureSet.setVisible(false);
        this.comboRtStructureSet.setVisible(false);
        this.lblRtPlan.setVisible(false);
        this.comboRtPlan.setVisible(false);
        this.lblRtPlanName.setVisible(false);
        this.lblRtPlanDose.setVisible(false);
        this.txtRtPlanDoseValue.setVisible(false);
        this.lblRtPlanDoseUnit.setVisible(false);
        //this.btnShowDvh.setVisible(false);
        this.slider = createTransparencySlider(5, true);

        this.treeStructures = new CheckboxTree() {
            private static final long serialVersionUID = 778188275507301929L;

            @Override
            public String getToolTipText(MouseEvent evt) {
                if (getRowForLocation(evt.getX(), evt.getY()) == -1) {
                    return null;
                }
                TreePath curPath = getPathForLocation(evt.getX(), evt.getY());
                if (curPath != null) {
                    Object object = curPath.getLastPathComponent();
                    if (object instanceof StructToolTipTreeNode) {
                        return ((StructToolTipTreeNode) object).getToolTipText();
                    }
                }
                return null;
            }
        };
        treeStructures.setToolTipText(StringUtil.EMPTY_STRING);

        this.treeIsodoses = new CheckboxTree() {
            private static final long serialVersionUID = 9185787644491574319L;

            @Override
            public String getToolTipText(MouseEvent evt) {
                if (getRowForLocation(evt.getX(), evt.getY()) == -1) {
                    return null;
                }
                TreePath curPath = getPathForLocation(evt.getX(), evt.getY());
                if (curPath != null) {
                    Object object = curPath.getLastPathComponent();
                    if (object instanceof IsoToolTipTreeNode) {
                        return ((IsoToolTipTreeNode) object).getToolTipText();
                    }
                }
                return null;
            }
        };
        treeIsodoses.setToolTipText(StringUtil.EMPTY_STRING);
        this.nodeStructures = new DefaultMutableTreeNode("Structures", true);
        this.nodeIsodoses = new DefaultMutableTreeNode("Isodoses", true);
        this.initData();
    }

    private void loadData() {
        final RtSet rt = this.rtSet;
        SwingWorker<Boolean, Boolean> loadTask = new SwingWorker<Boolean, Boolean>() {

            @Override
            protected Boolean doInBackground() throws Exception {
                // Reload RT case data objects for GUI
                rt.reloadRtCase(cbDvhRecalculate.isSelected());
                return true;
            }
        };

        loadTask.addPropertyChangeListener(evt -> {
            if ("progress".equals(evt.getPropertyName())) { //$NON-NLS-1$
                int progress = (Integer) evt.getNewValue();
                progressBar.setValue(progress);

            } else if ("state".equals(evt.getPropertyName())) { //$NON-NLS-1$
                if (StateValue.STARTED == evt.getNewValue()) {
                    btnLoad.setEnabled(false);
                    progressBar.setVisible(true);
                    progressBar.setIndeterminate(true);

                } else if (StateValue.DONE == evt.getNewValue()) {
                    progressBar.setIndeterminate(false);
                    progressBar.setVisible(false);
                    btnLoad.setEnabled(false);
                    btnLoad.setToolTipText("RT objects from loaded DICOM study have been already created");
                    cbDvhRecalculate.setEnabled(false);
                    cbDvhRecalculate.setToolTipText("DVH calculation cannot be modified after the RT objects creation");
                    lblRtStructureSet.setVisible(true);
                    comboRtStructureSet.setVisible(true);
                    lblRtPlan.setVisible(true);
                    comboRtPlan.setVisible(true);
                    lblRtPlanName.setVisible(true);
                    lblRtPlanDose.setVisible(true);
                    txtRtPlanDoseValue.setVisible(true);
                    lblRtPlanDoseUnit.setVisible(true);
                    treeStructures.setVisible(true);
                    treeIsodoses.setVisible(true);
                    //btnShowDvh.setVisible(true);

                    initSlider();

                    // Update GUI
                    ImageViewerPlugin<DicomImageElement> container = EventManager.getInstance().getSelectedView2dContainer();
                    List<ViewCanvas<DicomImageElement>> views = null;
                    if (container != null) {
                        views = container.getImagePanels();
                    }
                    if (views != null) {
                        for (ViewCanvas<DicomImageElement> v : views) {
                            updateCanvas(v);
                        }
                    }
                }
            }
        });
        
        new Thread(loadTask).start();
    }

    public void initData() {

        add(tabbedPane, BorderLayout.CENTER);

        add(panelFoot, BorderLayout.SOUTH);

        panelFoot.add(slider.getParent());

        panelHead = new JPanel();
        add(panelHead, BorderLayout.NORTH);
        panelHead.setLayout(new BoxLayout(panelHead, BoxLayout.Y_AXIS));
        this.btnLoad.setToolTipText("Populate RT objects from loaded DICOM study");
        this.comboRtStructureSet.setVisible(false);
        this.comboRtPlan.setVisible(false);

        // RT data load panel
        JPanel panelLoad = new JPanel();
        panelHead.add(panelLoad);
        FlowLayout fl_panelLoad = (FlowLayout) panelLoad.getLayout();
        fl_panelLoad.setAlignment(FlowLayout.LEFT);
        panelLoad.add(this.btnLoad);
        panelLoad.add(progressBar);
        progressBar.setVisible(false);
        
        // RTStruct panel
        JPanel panelStruct = new JPanel();
        FlowLayout flStruct = (FlowLayout) panelStruct.getLayout();
        flStruct.setAlignment(FlowLayout.LEFT);
        panelHead.add(panelStruct);
        panelStruct.add(this.lblRtStructureSet);
        panelStruct.add(this.comboRtStructureSet);

        // RTPlan panel
        JPanel panelPlan = new JPanel();
        FlowLayout flPlan = (FlowLayout) panelPlan.getLayout();
        flPlan.setAlignment(FlowLayout.LEFT);
        panelHead.add(panelPlan);
        panelPlan.add(this.lblRtPlan);
        // TODO: multi-select data table (with check box selection) -> with info about each plan dose
        panelPlan.add(this.comboRtPlan);
        panelPlan.add(this.lblRtPlanName);

        // RTDose panel
        JPanel panelDose = new JPanel();
        FlowLayout flDose = (FlowLayout) panelDose.getLayout();
        flDose.setAlignment(FlowLayout.LEFT);
        panelHead.add(panelDose);
        panelDose.add(this.lblRtPlanDose);
        panelDose.add(this.txtRtPlanDoseValue);
        panelDose.add(this.lblRtPlanDoseUnit);

        // DVH panel
        panelDvh = new JPanel();
        FlowLayout flDvh = (FlowLayout) panelDvh.getLayout();
        flDvh.setAlignment(FlowLayout.LEFT);
        panelHead.add(panelDvh);
        // By default recalculate DVH only when it is missing for structure
        panelDvh.add(cbDvhRecalculate);
        this.cbDvhRecalculate.setSelected(false);
        this.cbDvhRecalculate
            .setToolTipText("When enabled recalculate DVH for all structures, otherwise recalculate only missing DVH");

        btnShowDvh = new JButton("Display DVH chart");
        btnShowDvh.addActionListener(e -> showDvhChart());
        panelDvh.add(btnShowDvh);

        this.btnLoad.addActionListener(e -> loadData());

        initStructureTree();
        initIsodosesTree();

        tabbedPane.addChangeListener(e -> initSlider());
    }

    private void showDvhChart() {
        RtSet rt = rtSet;
        if (rt != null) {
            List<StructureLayer> structs = getStructureSelection();
            if (!structs.isEmpty()) {
                XYChart dvhChart = new XYChartBuilder().width(800).height(500).title("DVH").xAxisTitle("Dose (cGy)")
                    .yAxisTitle("Volume (%)").build();
                dvhChart.getStyler().setDefaultSeriesRenderStyle(XYSeries.XYSeriesRenderStyle.Line);
                for (StructureLayer structureLayer : structs) {
                    Structure structure = structureLayer.getStructure();
                    Dvh structureDvh = structure.getDvh();
                    structureDvh.appendChart(structure, dvhChart);
                }

                JDialog d = new JDialog(WinUtil.getParentWindow(this), "DVH Chart");
                XChartPanel<XYChart> chartPanel = new XChartPanel<>(dvhChart);
                d.getContentPane().add(chartPanel, BorderLayout.CENTER);
                d.pack();
                JMVUtils.showCenterScreen(d);
            }
        }
    }

    private void initSlider() {
        RtSet rt = rtSet;
        if (rt != null) {
            if (tabbedPane.getSelectedIndex() == 0) {
                slider.setValue(rt.getStructureFillTransparency() * 100 / 255);
            } else if (tabbedPane.getSelectedIndex() == 1) {
                slider.setValue(rt.getIsoFillTransparency() * 100 / 255);
            }
        }
    }

    public void initStructureTree() {
        this.treeStructures.getCheckingModel().setCheckingMode(TreeCheckingModel.CheckingMode.SIMPLE);
        this.treeStructures.setVisible(false);
        DefaultTreeModel model = new DefaultTreeModel(rootNodeStructures, false);
        treeStructures.setModel(model);

        rootNodeStructures.add(nodeStructures);
        TreePath rootPath = new TreePath(rootNodeStructures.getPath());
        treeStructures.addCheckingPath(rootPath);
        treeStructures.setShowsRootHandles(true);
        treeStructures.setRootVisible(false);
        treeStructures.setExpandsSelectedPaths(true);
        DefaultCheckboxTreeCellRenderer renderer = new DefaultCheckboxTreeCellRenderer();
        renderer.setOpenIcon(null);
        renderer.setClosedIcon(null);
        renderer.setLeafIcon(null);
        treeStructures.setCellRenderer(renderer);
        treeStructures.addTreeCheckingListener(this::treeValueChanged);

        expandTree(treeStructures, rootNodeStructures);
        tabbedPane.add(new JScrollPane(treeStructures), nodeStructures.toString());
    }

    public void initIsodosesTree() {
        this.treeIsodoses.getCheckingModel().setCheckingMode(TreeCheckingModel.CheckingMode.SIMPLE);
        this.treeIsodoses.setVisible(false);
        DefaultTreeModel model = new DefaultTreeModel(rootNodeIsodoses, false);
        treeIsodoses.setModel(model);

        rootNodeIsodoses.add(nodeIsodoses);
        TreePath rootPath = new TreePath(rootNodeIsodoses.getPath());
        treeIsodoses.addCheckingPath(rootPath);
        treeIsodoses.setShowsRootHandles(true);
        treeIsodoses.setRootVisible(false);
        treeIsodoses.setExpandsSelectedPaths(true);
        DefaultCheckboxTreeCellRenderer renderer = new DefaultCheckboxTreeCellRenderer();
        renderer.setOpenIcon(null);
        renderer.setClosedIcon(null);
        renderer.setLeafIcon(null);
        treeIsodoses.setCellRenderer(renderer);
        treeIsodoses.addTreeCheckingListener(this::treeValueChanged);

        expandTree(treeIsodoses, rootNodeIsodoses);
        tabbedPane.add(new JScrollPane(treeIsodoses), nodeIsodoses.toString());
    }

    public JSliderW createTransparencySlider(int labelDivision, boolean displayValueInTitle) {
        final JPanel panelSlider1 = new JPanel();
        panelSlider1.setLayout(new BoxLayout(panelSlider1, BoxLayout.Y_AXIS));
        panelSlider1.setBorder(new TitledBorder("Graphic Opacity"));
        DefaultBoundedRangeModel model = new DefaultBoundedRangeModel(50, 0, 0, 100);
        JSliderW s = new JSliderW(model);
        s.setLabelDivision(labelDivision);
        s.setdisplayValueInTitle(displayValueInTitle);
        s.setPaintTicks(true);
        s.setShowLabels(labelDivision > 0);
        panelSlider1.add(s);
        if (s.isShowLabels()) {
            s.setPaintLabels(true);
            SliderChangeListener.setSliderLabelValues(s, model.getMinimum(), model.getMaximum(), 0.0, 100.0);
        }
        s.addChangeListener(l -> {
            if (!model.getValueIsAdjusting()) {
                RtSet rt = rtSet;
                if (rt != null) {
                    if (tabbedPane.getSelectedIndex() == 0) {
                        rt.setStructureFillTransparency(model.getValue() * 255 / 100);
                    } else if (tabbedPane.getSelectedIndex() == 1) {
                        rt.setIsoFillTransparency(model.getValue() * 255 / 100);
                    }

                    ImageViewerPlugin<DicomImageElement> container =
                        EventManager.getInstance().getSelectedView2dContainer();
                    List<ViewCanvas<DicomImageElement>> views = null;
                    if (container != null) {
                        views = container.getImagePanels();
                    }
                    if (views != null) {
                        for (ViewCanvas<DicomImageElement> v : views) {
                            showGraphic(rt, getStructureSelection(), getIsoDoseSelection(), v);
                        }
                    }
                }
            }
        });
        return s;
    }

    private void treeValueChanged(TreeCheckingEvent e) {
        if (!initPathSelection) {
            TreePath path = e.getPath();
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
                if (rt != null && ((selObject == nodeStructures || parent == nodeStructures)
                    || (selObject == nodeIsodoses || parent == nodeIsodoses))) {
                    for (ViewCanvas<DicomImageElement> v : views) {
                        showGraphic(rt, getStructureSelection(), getIsoDoseSelection(), v);
                    }
                }
            }
        }
    }

    private List<StructureLayer> getStructureSelection() {
        ArrayList<StructureLayer> list = new ArrayList<>();
        if (treeStructures.getCheckingModel().isPathChecked(new TreePath(nodeStructures.getPath()))) {
            TreePath[] paths = treeStructures.getCheckingModel().getCheckingPaths();
            for (TreePath treePath : paths) {
                DefaultMutableTreeNode node = (DefaultMutableTreeNode) treePath.getLastPathComponent();
                if (node.getUserObject() instanceof StructureLayer) {
                    list.add((StructureLayer) node.getUserObject());
                }
            }
        }
        return list;
    }

    private List<IsoDoseLayer> getIsoDoseSelection() {
        ArrayList<IsoDoseLayer> list = new ArrayList<>();
        if (treeIsodoses.getCheckingModel().isPathChecked(new TreePath(nodeIsodoses.getPath()))) {
            TreePath[] paths = treeIsodoses.getCheckingModel().getCheckingPaths();
            for (TreePath treePath : paths) {
                DefaultMutableTreeNode node = (DefaultMutableTreeNode) treePath.getLastPathComponent();
                if (node.getUserObject() instanceof IsoDoseLayer) {
                    list.add((IsoDoseLayer) node.getUserObject());
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

    private static boolean containsIsoDose(List<IsoDoseLayer> list, IsoDose i) {
        for (IsoDoseLayer isoDoseLayer : list) {
            if (isoDoseLayer.getIsoDose().getLevel() == i.getLevel()
                && isoDoseLayer.getIsoDose().getLabel().equals(i.getLabel())) {
                return true;
            }
        }
        return false;
    }

    private static void showGraphic(RtSet rt, List<StructureLayer> listStructure, List<IsoDoseLayer> listIsoDose,
        ViewCanvas<?> v) {
        if (rt != null) {
            ImageElement dicom = v.getImage();
            if (dicom instanceof DicomImageElement) {
                GeometryOfSlice geometry = ((DicomImageElement) dicom).getDispSliceGeometry();

                // Key for contour lookup
                String imageUID = TagD.getTagValue(dicom, Tag.SOPInstanceUID, String.class);

                // List of detected contours from RtSet
                List<Contour> contours = rt.getContourMap().get(imageUID);

                // List of detected plans from RtSet
                Plan plan = rt.getFirstPlan();
                Dose dose = null;
                if (plan != null) {
                    dose = plan.getFirstDose();
                }

                // Any RT layer is available
                if (contours != null || dose != null) {
                    GraphicModel modelList = (GraphicModel) dicom.getTagValue(TagW.PresentationModel);
                    // After getting a new image iterator, update the measurements
                    if (modelList == null) {
                        modelList = new XmlGraphicModel(dicom);
                        dicom.setTag(TagW.PresentationModel, modelList);
                    } else {
                        modelList.deleteByLayerType(LayerType.DICOM_RT);
                    }

                    // Contours layer
                    if (contours != null) {
                        // Check which contours should be rendered
                        for (Contour c : contours) {
                            StructureLayer structLayer = (StructureLayer) c.getLayer();
                            Structure structure = structLayer.getStructure();
                            if (containsStructure(listStructure, structure)) {
                                // Structure graphics
                                Graphic graphic = c.getGraphic(geometry);
                                if (graphic != null) {

                                    graphic.setLineThickness((float) structure.getThickness());
                                    graphic.setPaint(structure.getColor());
                                    graphic.setLayerType(LayerType.DICOM_RT);
                                    graphic.setLayer(structLayer.getLayer());

                                    // External body contour -> do not fill
                                    boolean filled = !"EXTERNAL".equals(structure.getRtRoiInterpretedType());
                                    graphic.setFilled(filled);

                                    for (PropertyChangeListener listener : modelList.getGraphicsListeners()) {
                                        graphic.addPropertyChangeListener(listener);
                                    }

                                    modelList.addGraphic(graphic);
                                }
                            }
                        }
                    }

                    // Iso dose contour layer
                    if (dose != null) {

                        List<Contour> isoContours = dose.getIsoContourMap().get(imageUID);
                        if (isoContours != null) {
                            // Check which iso contours should be rendered
                            for (int i = isoContours.size() - 1; i >= 0; i--) {
                                Contour isoContour = isoContours.get(i);
                                IsoDoseLayer isoDoseLayer = (IsoDoseLayer) isoContour.getLayer();
                                IsoDose isoDose = isoDoseLayer.getIsoDose();

                                // Only selected
                                if (containsIsoDose(listIsoDose, isoDose)) {

                                    // Iso dose graphics
                                    Graphic graphic = isoContour.getGraphic(geometry);
                                    if (graphic != null) {

                                        graphic.setLineThickness((float) isoDose.getThickness());
                                        graphic.setPaint(isoDose.getColor());
                                        graphic.setLayerType(LayerType.DICOM_RT);
                                        graphic.setLayer(isoDoseLayer.getLayer());
                                        graphic.setFilled(true);

                                        for (PropertyChangeListener listener : modelList.getGraphicsListeners()) {
                                            graphic.addPropertyChangeListener(listener);
                                        }
                                        modelList.addGraphic(graphic);
                                    }
                                }
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
        if (rt == null || rt.getStructures().isEmpty()) {
            this.nodeStructures.removeAllChildren();
            this.nodeIsodoses.removeAllChildren();

            treeStructures.setModel(new DefaultTreeModel(rootNodeStructures, false));
            treeIsodoses.setModel(new DefaultTreeModel(rootNodeIsodoses, false));

            comboRtStructureSet.removeAllItems();
            comboRtPlan.removeAllItems();
            return;
        }

        comboRtStructureSet.removeItemListener(structureChangeListener);
        comboRtPlan.removeItemListener(planChangeListener);

        RtSpecialElement oldStructure = (RtSpecialElement) comboRtStructureSet.getSelectedItem();
        RtSpecialElement oldPlan = (RtSpecialElement) comboRtPlan.getSelectedItem();

        comboRtStructureSet.removeAllItems();
        comboRtPlan.removeAllItems();

        Set<RtSpecialElement> rtStructElements = rt.getStructures().keySet();
        Set<RtSpecialElement> rtPlanElements = rt.getPlans().keySet();

        rtStructElements.forEach(comboRtStructureSet::addItem);
        rtPlanElements.forEach(comboRtPlan::addItem);

        boolean update = !rtStructElements.contains(oldStructure);
        boolean update1 = !rtPlanElements.contains(oldPlan);

        boolean updateTree = false;
        if (update) {
            RtSpecialElement selectedStructure = rt.getFirstStructure();
            if (selectedStructure != null) {
                updateTree = true;
            }
        } else {
            comboRtStructureSet.setSelectedItem(oldStructure);
        }

        if (update1 || !nodeIsodoses.children().hasMoreElements()) {
            RtSpecialElement selectedPlan = rt.getFirstPlanKey();
            if (selectedPlan != null) {
                comboRtPlan.setSelectedItem(selectedPlan);
                updateTree = true;
            }
        } else {
            comboRtPlan.setSelectedItem(oldPlan);
        }

        if (updateTree) {
            updateTree((RtSpecialElement) comboRtStructureSet.getSelectedItem(),
                (RtSpecialElement) comboRtPlan.getSelectedItem());
        }

        comboRtStructureSet.addItemListener(structureChangeListener);
        comboRtPlan.addItemListener(planChangeListener);

        // Update selected dose plane
        // ImageElement dicom = viewCanvas.getImage();
        // if (dicom instanceof DicomImageElement) {
        // GeometryOfSlice geometry = ((DicomImageElement) dicom).getDispSliceGeometry();
        //
        // if (rt.getFirstDose() != null) {
        // rt.getDoseValueForPixel(247, 263, geometry.getTLHC().getZ());
        // }
        // }

        showGraphic(rt, getStructureSelection(), getIsoDoseSelection(), viewCanvas);
    }

    public void updateTree(RtSpecialElement selectedStructure, RtSpecialElement selectedPlan) {
        // Empty tree when no RtSet
        if (rtSet == null) {
            nodeStructures.removeAllChildren();
            nodeIsodoses.removeAllChildren();
            treeStructures.setModel(new DefaultTreeModel(rootNodeStructures, false));
            treeIsodoses.setModel(new DefaultTreeModel(rootNodeIsodoses, false));
            return;
        }

        initPathSelection = true;
        try {
            // Prepare root tree model
            treeStructures.setModel(new DefaultTreeModel(rootNodeStructures, false));
            treeIsodoses.setModel(new DefaultTreeModel(rootNodeIsodoses, false));

            // Prepare parent node for structures
            if (selectedStructure != null) {
                nodeStructures.removeAllChildren();
                Map<Integer, StructureLayer> structures = rtSet.getStructureSet(selectedStructure);
                if (structures != null) {
                    for (StructureLayer structureLayer : structures.values()) {
                        DefaultMutableTreeNode node = new StructToolTipTreeNode(structureLayer, false);
                        nodeStructures.add(node);
                        treeStructures.addCheckingPath(new TreePath(node.getPath()));
                    }
                }
                treeStructures.addCheckingPath(new TreePath(nodeStructures.getPath()));
            }

            // Prepare parent node for isodoses
            if (selectedPlan != null) {
                nodeIsodoses.removeAllChildren();

                Plan plan = rtSet.getPlan(selectedPlan);
                this.lblRtPlanName.setText(plan.getName());
                this.txtRtPlanDoseValue.setText(String.format("%.0f", plan.getRxDose()));

                Dose planDose = plan.getFirstDose();
                if (planDose != null) {
                    Map<Integer, IsoDoseLayer> isodoses = planDose.getIsoDoseSet();
                    if (isodoses != null) {
                        for (IsoDoseLayer isoDoseLayer : isodoses.values()) {
                            DefaultMutableTreeNode node = new IsoToolTipTreeNode(isoDoseLayer, false);
                            this.nodeIsodoses.add(node);
                            treeIsodoses.addCheckingPath(new TreePath(node.getPath()));
                        }
                    }
                    treeIsodoses.removeCheckingPath(new TreePath(nodeIsodoses.getPath()));
                }
            }

            // Expand
            expandTree(treeStructures, rootNodeStructures);
            expandTree(treeIsodoses, rootNodeIsodoses);
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
                        RtSet set = RtSet_Cache.get(frameOfReferenceUID);
                        boolean reload = set == null || (!list.isEmpty() && !set.getRtElements().equals(list));
                        if (reload) {
                            set = new RtSet(frameOfReferenceUID, list);
                            RtSet_Cache.put(frameOfReferenceUID, set);
                        }
                        boolean empty = set.getStructures().isEmpty();
                        btnLoad.setEnabled(empty || reload);
                        this.rtSet = set;

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
                            synchronized (s) {
                                for (DicomImageElement media : ((DicomSeries) s).getMedias(null, null)) {
                                    if ("RTDOSE".equals(TagD.getTagValue(media, Tag.Modality))) {
                                        specialElementList.add(media);
                                    }
                                }
                            }
                        }
                        if ("CT".equals(modality) && s instanceof DicomSeries) {
                            synchronized (s) {
                                for (DicomImageElement media : ((DicomSeries) s).getMedias(null, null)) {
                                    if ("CT".equals(TagD.getTagValue(media, Tag.Modality))) {
                                        specialElementList.add(media);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        return specialElementList;
    }

    private static String getColorBullet(Color c, String label) {
        StringBuilder buf = new StringBuilder("<html><font color='rgb("); //$NON-NLS-1$
        buf.append(c.getRed());
        buf.append(",");
        buf.append(c.getGreen());
        buf.append(",");
        buf.append(c.getBlue());
        // Other square: u2B1B
        buf.append(")'> \u2588 </font>");
        buf.append(label);
        buf.append("</html>"); //$NON-NLS-1$
        return buf.toString();
    }

    static class StructToolTipTreeNode extends DefaultMutableTreeNode {

        public StructToolTipTreeNode(StructureLayer userObject, boolean allowsChildren) {
            super(Objects.requireNonNull(userObject), allowsChildren);
        }

        public String getToolTipText() {
            StructureLayer layer = (StructureLayer) getUserObject();

            double volume = layer.getStructure().getVolume();
            String source = layer.getStructure().getVolumeSource().toString();
            Dvh structureDvh = layer.getStructure().getDvh();

            StringBuilder buf = new StringBuilder();
            buf.append("<html>");
            buf.append("Structure Information:<br>");
            if (StringUtil.hasText(layer.getStructure().getRoiObservationLabel())) {
                buf.append("Observation Label: ");
                buf.append(layer.getStructure().getRoiObservationLabel());
                buf.append("<br>");
            }
            buf.append(String.format("Thickness: %.2f<br>", layer.getStructure().getThickness()));
            buf.append(String.format(source + " Volume: %.4f cm^3<br>", volume));

            if (structureDvh != null) {
                buf.append(String.format(structureDvh.getDvhSource().toString() + " Min Dose: %.3f %%<br>", RtSet
                    .calculateRelativeDose(structureDvh.getDvhMinimumDoseCGy(), structureDvh.getPlan().getRxDose())));
                buf.append(String.format(structureDvh.getDvhSource().toString() + " Max Dose: %.3f %%<br>", RtSet
                    .calculateRelativeDose(structureDvh.getDvhMaximumDoseCGy(), structureDvh.getPlan().getRxDose())));
                buf.append(String.format(structureDvh.getDvhSource().toString() + " Mean Dose: %.3f %%<br>",
                    RtSet.calculateRelativeDose(structureDvh.getDvhMeanDoseCGy(), structureDvh.getPlan().getRxDose())));
            }
            buf.append("</html>");

            return buf.toString();
        }

        @Override
        public String toString() {
            StructureLayer layer = (StructureLayer) getUserObject();
            return getColorBullet(layer.getStructure().getColor(), layer.toString());
        }
    }

    static class IsoToolTipTreeNode extends DefaultMutableTreeNode {

        public IsoToolTipTreeNode(IsoDoseLayer userObject, boolean allowsChildren) {
            super(Objects.requireNonNull(userObject), allowsChildren);
        }

        public String getToolTipText() {
            IsoDoseLayer layer = (IsoDoseLayer) getUserObject();

            StringBuilder buf = new StringBuilder();
            buf.append("<html>");
            buf.append("Isodose Information:<br>");
            if (layer.getIsoDose() != null) {
                buf.append(String.format("Level: %d %%<br>", layer.getIsoDose().getLevel()));
                buf.append(String.format("Thickness: %.2f<br>", layer.getIsoDose().getThickness()));
            }
            buf.append("</html>");

            return buf.toString();
        }

        @Override
        public String toString() {
            IsoDoseLayer layer = (IsoDoseLayer) getUserObject();
            return getColorBullet(layer.getIsoDose().getColor(), layer.toString());
        }
    }

}
