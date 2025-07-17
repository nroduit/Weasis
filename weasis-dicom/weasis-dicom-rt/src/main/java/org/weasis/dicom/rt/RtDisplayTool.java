/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.rt;

import bibliothek.gui.dock.common.CLocation;
import eu.essilab.lablib.checkboxtree.TreeCheckingEvent;
import eu.essilab.lablib.checkboxtree.TreeCheckingModel;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.*;
import javax.swing.*;
import javax.swing.SwingWorker.StateValue;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import net.miginfocom.swing.MigLayout;
import org.dcm4che3.data.Tag;
import org.knowm.xchart.XChartPanel;
import org.knowm.xchart.XYChart;
import org.knowm.xchart.XYChartBuilder;
import org.knowm.xchart.XYSeries;
import org.weasis.core.api.gui.Insertable;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.gui.util.DecFormatter;
import org.weasis.core.api.gui.util.Filter;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.gui.util.JSliderW;
import org.weasis.core.api.gui.util.SliderCineListener;
import org.weasis.core.api.gui.util.WinUtil;
import org.weasis.core.api.image.util.MeasurableLayer;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.util.ResourceUtil;
import org.weasis.core.api.util.ResourceUtil.OtherIcon;
import org.weasis.core.ui.dialog.PropertiesDialog;
import org.weasis.core.ui.docking.PluginTool;
import org.weasis.core.ui.editor.SeriesViewerEvent;
import org.weasis.core.ui.editor.SeriesViewerListener;
import org.weasis.core.ui.editor.image.ImageViewerPlugin;
import org.weasis.core.ui.editor.image.ViewCanvas;
import org.weasis.core.ui.model.graphic.imp.seg.GroupTreeNode;
import org.weasis.core.ui.model.graphic.imp.seg.SegContour;
import org.weasis.core.ui.model.graphic.imp.seg.SegRegion;
import org.weasis.core.ui.tp.raven.spinner.SpinnerProgress;
import org.weasis.core.ui.util.SegRegionTool;
import org.weasis.core.ui.util.SegRegionTree;
import org.weasis.core.ui.util.StructToolTipTreeNode;
import org.weasis.core.ui.util.TreeBuilder;
import org.weasis.core.util.SoftHashMap;
import org.weasis.core.util.StringUtil;
import org.weasis.dicom.codec.*;
import org.weasis.dicom.viewer2d.EventManager;
import org.weasis.dicom.viewer2d.View2d;
import org.weasis.opencv.data.PlanarImage;
import org.weasis.opencv.seg.RegionAttributes;

/**
 * @author Tomas Skripcak
 * @author Nicolas Roduit
 */
public class RtDisplayTool extends PluginTool implements SeriesViewerListener, SegRegionTool {

  public static final String BUTTON_NAME = Messages.getString("rt.tool");

  private static final String GRAPHIC_OPACITY = Messages.getString("graphic.opacity");
  private static final SoftHashMap<String, RtSet> RT_SET_CACHE = new SoftHashMap<>();

  // UI Components
  private final JScrollPane rootPane = new JScrollPane();
  private final JTabbedPane tabbedPane = new JTabbedPane();
  private final JButton btnLoad = new JButton(Messages.getString("load.rt"));
  private final JCheckBox cbDvhRecalculate = new JCheckBox(Messages.getString("dvh.recalculate"));
  private final JComboBox<StructureSet> comboRtStructureSet = new JComboBox<>();
  private final JComboBox<Plan> comboRtPlan = new JComboBox<>();
  private final JSliderW slider;
  private final SpinnerProgress progressBar = new SpinnerProgress();

  // Tree components
  private final StructRegionTree treeStructures;
  private final SegRegionTree treeIsodoses;
  private final DefaultMutableTreeNode rootNodeStructures =
      new DefaultMutableTreeNode("rootNode", true);
  private final DefaultMutableTreeNode rootNodeIsodoses =
      new DefaultMutableTreeNode("rootNode", true);
  private final GroupTreeNode nodeStructures;
  private final GroupTreeNode nodeIsodoses;

  // Labels
  private final JTextField txtRtPlanDoseValue = new JTextField();

  // State
  private RtSet rtSet;
  private boolean initPathSelection;

  // Store selection state for all GroupTreeNode items
  private final Map<String, Map<String, Boolean>> structureSetSelections = new HashMap<>();

  // Listeners
  private final transient ItemListener structureChangeListener;
  private final transient ItemListener planChangeListener;

  public RtDisplayTool() {
    super(BUTTON_NAME, Insertable.Type.TOOL, 30);
    this.dockable.setTitleIcon(ResourceUtil.getIcon(OtherIcon.RADIOACTIVE));
    this.setDockableWidth(350);
    rootPane.setBorder(BorderFactory.createEmptyBorder()); // remove default line

    // Initialize trees
    this.treeStructures = new StructRegionTree(this);
    this.treeIsodoses = new SegRegionTree(this);
    this.nodeStructures = new GroupTreeNode(Messages.getString("structures"), true);
    this.nodeIsodoses = new GroupTreeNode(Messages.getString("isodoses"), true);

    // Initialize listeners after trees are created
    this.structureChangeListener =
        e -> {
          if (e.getStateChange() == ItemEvent.DESELECTED
              && e.getItem() instanceof StructureSet oldStructure) {
            saveTreeSelection(
                treeStructures, getSopInstanceUid(oldStructure), structureSetSelections);
          } else if (e.getStateChange() == ItemEvent.SELECTED
              && e.getItem() instanceof StructureSet newStructure) {
            updateTree(newStructure, null);
          }
        };
    this.planChangeListener =
        e -> {
          if (e.getStateChange() == ItemEvent.SELECTED && e.getItem() instanceof Plan newPlan) {
            updateTree(null, newPlan);
          }
        };

    // Initialize slider
    this.slider = PropertiesDialog.createOpacitySlider(GRAPHIC_OPACITY);
    slider.setValue(80);
    slider.addChangeListener(l -> updateSlider());

    initializeUI();
    initializeListeners();
  }

  private void initializeUI() {
    setLayout(new BorderLayout());

    // Header panel
    JPanel headerPanel = createHeaderPanel();
    add(headerPanel, BorderLayout.NORTH);

    // Tabbed pane for trees
    add(tabbedPane, BorderLayout.CENTER);

    // Bottom panel with slider
    JPanel bottomPanel =
        new JPanel(new MigLayout("fillx, ins 5lp", "[fill]", "[]10lp[]")); // NON-NLS
    bottomPanel.add(slider);
    add(bottomPanel, BorderLayout.SOUTH);

    initializeTrees();
    setInitialVisibility(false);
  }

  private JPanel createHeaderPanel() {
    JPanel headerPanel = new JPanel();
    headerPanel.setLayout(new BoxLayout(headerPanel, BoxLayout.Y_AXIS));

    // Load panel
    btnLoad.setToolTipText(Messages.getString("populate.rt.objects"));
    JPanel loadPanel = createFlowPanel(FlowLayout.LEFT);
    loadPanel.add(btnLoad);
    loadPanel.add(progressBar);
    progressBar.setVisible(false);
    headerPanel.add(loadPanel);

    // Structure set panel
    JPanel structPanel = createFlowPanel(FlowLayout.LEFT);
    structPanel.add(new JLabel(Messages.getString("structures") + StringUtil.COLON));
    GuiUtils.setPreferredWidth(comboRtStructureSet, 230, 150);
    structPanel.add(comboRtStructureSet);
    headerPanel.add(structPanel);

    // Plan panel
    JPanel planPanel = createFlowPanel(FlowLayout.LEFT);
    planPanel.add(new JLabel(Messages.getString("plan") + StringUtil.COLON));
    GuiUtils.setPreferredWidth(comboRtPlan, 240, 150);
    planPanel.add(comboRtPlan);
    headerPanel.add(planPanel);

    // Dose panel
    JPanel dosePanel = createFlowPanel(FlowLayout.LEFT);
    dosePanel.add(new JLabel(Messages.getString("dose") + StringUtil.COLON));
    dosePanel.add(txtRtPlanDoseValue);
    dosePanel.add(new JLabel("cGy"));
    headerPanel.add(dosePanel);

    // DVH panel
    JPanel dvhPanel = createFlowPanel(FlowLayout.LEFT);
    cbDvhRecalculate.setToolTipText(Messages.getString("when.enabled.recalculate"));
    dvhPanel.add(cbDvhRecalculate);
    JButton btnShowDvh = new JButton(Messages.getString("display.dvh.chart"));
    btnShowDvh.addActionListener(e -> showDvhChart());
    dvhPanel.add(btnShowDvh);
    headerPanel.add(dvhPanel);

    return headerPanel;
  }

  private JPanel createFlowPanel(int alignment) {
    JPanel panel = new JPanel(new FlowLayout(alignment));
    return panel;
  }

  private void initializeListeners() {
    btnLoad.addActionListener(e -> loadData());
    treeStructures.initListeners();
    treeIsodoses.initListeners();

    comboRtStructureSet.addItemListener(structureChangeListener);
    comboRtPlan.addItemListener(planChangeListener);
    tabbedPane.addChangeListener(_ -> initSlider());
  }

  private void initializeTrees() {
    setupTree(treeStructures, rootNodeStructures, nodeStructures);
    setupTree(treeIsodoses, rootNodeIsodoses, nodeIsodoses);
  }

  private void setupTree(
      SegRegionTree tree, DefaultMutableTreeNode rootNode, GroupTreeNode groupNode) {
    tree.getCheckingModel().setCheckingMode(TreeCheckingModel.CheckingMode.SIMPLE);
    tree.setVisible(false);
    tree.setToolTipText(StringUtil.EMPTY_STRING);
    tree.setCellRenderer(TreeBuilder.buildNoIconCheckboxTreeCellRenderer());
    tree.addTreeCheckingListener(this::treeValueChanged);

    DefaultTreeModel model = new DefaultTreeModel(rootNode, false);
    tree.setModel(model);
    rootNode.add(groupNode);

    TreePath rootPath = new TreePath(rootNode.getPath());
    tree.addCheckingPath(rootPath);
    tree.setShowsRootHandles(true);
    tree.setRootVisible(false);
    tree.setExpandsSelectedPaths(true);

    if (tree == treeStructures) {
      // Structures tree: default checked = true
      tree.setPathSelection(new TreePath(groupNode.getPath()), true);
    } else if (tree == treeIsodoses) {
      // Isodoses tree: default checked = false
      tree.setPathSelection(new TreePath(groupNode.getPath()), false);
      tree.getCheckingModel().removeCheckingPath(new TreePath(groupNode.getPath()));
    }

    TreeBuilder.expandTree(tree, rootNode, 2);
    Dimension minimumSize = GuiUtils.getDimension(150, 150);
    JScrollPane scrollPane = new JScrollPane(tree);
    scrollPane.setMinimumSize(minimumSize);
    scrollPane.setPreferredSize(minimumSize);
    tabbedPane.add(scrollPane, groupNode.toString());
  }

  private void setInitialVisibility(boolean visible) {
    comboRtStructureSet.setVisible(visible);
    comboRtPlan.setVisible(visible);
    txtRtPlanDoseValue.setVisible(visible);
    treeStructures.setVisible(visible);
    treeIsodoses.setVisible(visible);
  }

  private void saveTreeSelection(
      SegRegionTree tree, String key, Map<String, Map<String, Boolean>> selectionMap) {
    if (key == null || tree == null) return;

    Map<String, Boolean> nodeSelections = new HashMap<>();

    // Save root node selection
    GroupTreeNode rootNode = (tree == treeStructures) ? nodeStructures : nodeIsodoses;
    TreePath rootPath = new TreePath(rootNode.getPath());
    boolean isRootChecked = tree.getCheckingModel().isPathChecked(rootPath);
    nodeSelections.put("ROOT", isRootChecked);

    // Save all GroupTreeNode selections
    saveGroupNodeSelections(tree, rootNode, nodeSelections);

    selectionMap.put(key, nodeSelections);
  }

  private void saveGroupNodeSelections(
      SegRegionTree tree, DefaultMutableTreeNode parentNode, Map<String, Boolean> nodeSelections) {
    for (int i = 0; i < parentNode.getChildCount(); i++) {
      DefaultMutableTreeNode childNode = (DefaultMutableTreeNode) parentNode.getChildAt(i);

      if (childNode instanceof GroupTreeNode groupNode) {
        TreePath groupPath = new TreePath(groupNode.getPath());
        boolean isGroupChecked = tree.getCheckingModel().isPathChecked(groupPath);

        // Use group node's toString() as unique key
        String groupKey = "GROUP_" + groupNode; // NON-NLS
        nodeSelections.put(groupKey, isGroupChecked);

        // Recursively save nested group nodes if any
        saveGroupNodeSelections(tree, groupNode, nodeSelections);
      }
    }
  }

  private String getSopInstanceUid(RtSpecialElement specialElement) {
    if (specialElement == null) return null;
    return TagD.getTagValue(specialElement, Tag.SOPInstanceUID, String.class);
  }

  private void restoreTreeSelection(
      SegRegionTree tree, String key, Map<String, Map<String, Boolean>> selectionMap) {
    if (key == null || tree == null) return;

    Map<String, Boolean> savedSelections = selectionMap.get(key);
    if (savedSelections != null && !savedSelections.isEmpty()) {
      // Restore saved selections
      restoreSavedSelections(tree, savedSelections);
    } else {
      // Apply default selection
      applyDefaultTreeSelection(tree);
    }
  }

  private void restoreSavedSelections(SegRegionTree tree, Map<String, Boolean> savedSelections) {
    initPathSelection = true;
    try {
      // Restore root node selection
      GroupTreeNode rootNode = (tree == treeStructures) ? nodeStructures : nodeIsodoses;
      TreePath rootPath = new TreePath(rootNode.getPath());
      Boolean rootSelection = savedSelections.get("ROOT");
      if (rootSelection != null) {
        tree.setPathSelection(rootPath, rootSelection);
      }

      // Restore GroupTreeNode selections
      restoreGroupNodeSelections(tree, rootNode, savedSelections);

    } finally {
      initPathSelection = false;
    }
  }

  private void restoreGroupNodeSelections(
      SegRegionTree tree, DefaultMutableTreeNode parentNode, Map<String, Boolean> savedSelections) {
    for (int i = 0; i < parentNode.getChildCount(); i++) {
      DefaultMutableTreeNode childNode = (DefaultMutableTreeNode) parentNode.getChildAt(i);

      if (childNode instanceof GroupTreeNode groupNode) {
        String groupKey = "GROUP_" + groupNode; // NON-NLS
        Boolean groupSelection = savedSelections.get(groupKey);

        if (groupSelection != null) {
          TreePath groupPath = new TreePath(groupNode.getPath());
          tree.setPathSelection(groupPath, groupSelection);
        }

        // Recursively restore nested group nodes if any
        restoreGroupNodeSelections(tree, groupNode, savedSelections);
      }
    }
  }

  private void applyDefaultTreeSelection(SegRegionTree tree) {
    initPathSelection = true;
    try {
      GroupTreeNode rootNode = (tree == treeStructures) ? nodeStructures : nodeIsodoses;
      TreePath rootPath = new TreePath(rootNode.getPath());

      if (tree == treeStructures) {
        // Default: structures tree root node checked (true)
        tree.setPathSelection(rootPath, true);
        setAllGroupNodesSelection(tree, rootNode, true);
      } else if (tree == treeIsodoses) {
        // Default: isodoses tree root node unchecked (false)
        tree.setPathSelection(rootPath, false);
        setAllGroupNodesSelection(tree, rootNode, true);
      }
    } finally {
      initPathSelection = false;
    }
  }

  private void setAllGroupNodesSelection(
      SegRegionTree tree, DefaultMutableTreeNode parentNode, boolean selected) {
    for (int i = 0; i < parentNode.getChildCount(); i++) {
      DefaultMutableTreeNode childNode = (DefaultMutableTreeNode) parentNode.getChildAt(i);

      if (childNode instanceof GroupTreeNode groupNode) {
        TreePath groupPath = new TreePath(groupNode.getPath());
        tree.setPathSelection(groupPath, selected);

        // Recursively apply to nested group nodes
        setAllGroupNodesSelection(tree, groupNode, selected);
      }
    }
  }

  private void loadData() {
    if (rtSet == null || rtSet.getPatientImage() == null) return;

    SwingWorker<Boolean, Void> loadTask =
        new SwingWorker<>() {
          @Override
          protected Boolean doInBackground() throws Exception {
            rtSet.reloadRtCase(cbDvhRecalculate.isSelected());
            return true;
          }
        };

    loadTask.addPropertyChangeListener(
        evt -> {
          String propertyName = evt.getPropertyName();
          if ("state".equals(propertyName)) {
            handleLoadTaskStateChange((StateValue) evt.getNewValue());
          }
        });

    new Thread(loadTask).start();
  }

  private void handleLoadTaskStateChange(StateValue state) {
    if (StateValue.STARTED == state) {
      btnLoad.setEnabled(false);
      progressBar.setVisible(true);
      progressBar.setIndeterminate(true);

    } else if (StateValue.DONE == state) {
      progressBar.setVisible(false);
      btnLoad.setEnabled(false);
      btnLoad.setToolTipText(Messages.getString("rt.objects.from.loaded"));
      cbDvhRecalculate.setEnabled(false);
      cbDvhRecalculate.setToolTipText(Messages.getString("dvh.calculation"));
      setInitialVisibility(true);
      initSlider();

      // Update GUI
      updateCanvas(EventManager.getInstance().getSelectedViewPane());
      updateCurrentContainer();
    }
  }

  private void showDvhChart() {
    if (rtSet == null) return;

    List<StructRegion> structs = getCheckedStructRegions();
    if (structs.isEmpty()) return;

    XYChart chart =
        new XYChartBuilder()
            .width(800)
            .height(500)
            .title("DVH")
            .xAxisTitle(Messages.getString("dose.cgy"))
            .yAxisTitle(Messages.getString("volume") + " (%)")
            .build();

    chart.getStyler().setDefaultSeriesRenderStyle(XYSeries.XYSeriesRenderStyle.Line);

    for (StructRegion region : structs) {
      Dvh dvh = region.getDvh();
      if (dvh != null) {
        dvh.appendChart(region, chart);
      }
    }

    showChartDialog(chart);
  }

  private void showChartDialog(XYChart chart) {
    JDialog dialog = new JDialog(WinUtil.getParentWindow(this), Messages.getString("dvh.chart"));
    XChartPanel<XYChart> chartPanel = new XChartPanel<>(chart);
    dialog.getContentPane().add(chartPanel, BorderLayout.CENTER);
    dialog.pack();
    GuiUtils.showCenterScreen(dialog);
  }

  private List<StructRegion> getCheckedStructRegions() {
    List<StructRegion> list = new ArrayList<>();
    for (TreePath path : treeStructures.getCheckingModel().getCheckingPaths()) {
      if (treeStructures.hasAllParentsChecked(path)) {
        DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
        if (node.getUserObject() instanceof StructRegion region && region.getDvh() != null) {
          list.add(region);
        }
      }
    }
    return list;
  }

  private void initSlider() {
    if (rtSet != null) {
      SpecialElementRegion region = getSelectedRegion();
      float opacity = region != null ? region.getOpacity() : 1.0f;
      slider.setValue((int) (opacity * 100));
      PropertiesDialog.updateSlider(slider, GRAPHIC_OPACITY);
    }
  }

  private void updateSlider() {
    float value = PropertiesDialog.updateSlider(slider, GRAPHIC_OPACITY);
    if (rtSet != null) {
      SpecialElementRegion region = getSelectedRegion();
      if (region != null) {
        region.setOpacity(value);
        updateVisibleNode();
      }
    }
  }

  private SpecialElementRegion getSelectedRegion() {
    boolean isDoseSelected = tabbedPane.getSelectedIndex() == 1;

    if (isDoseSelected) {
      Plan plan = (Plan) comboRtPlan.getSelectedItem();
      return plan != null ? plan.getFirstDose() : null;
    } else {
      return (StructureSet) comboRtStructureSet.getSelectedItem();
    }
  }

  private void treeValueChanged(TreeCheckingEvent e) {
    if (!initPathSelection) {
      updateVisibleNode();
    }
  }

  public void updateVisibleNode() {
    SpecialElementRegion region = getSelectedRegion();
    if (region instanceof StructureSet) {
      updateTreeVisibility(treeStructures, nodeStructures, region);
    } else if (region instanceof Dose) {
      updateTreeVisibility(treeIsodoses, nodeIsodoses, region);
    }
    updateCurrentContainer();
  }

  private void updateTreeVisibility(
      SegRegionTree tree, GroupTreeNode node, SpecialElementRegion region) {
    boolean isChecked = tree.getCheckingModel().isPathChecked(new TreePath(node.getPath()));
    region.setVisible(isChecked);
    node.setSelected(isChecked);
    tree.updateVisibleNode(tree == treeStructures ? rootNodeStructures : rootNodeIsodoses, node);
  }

  public void updateCurrentContainer() {
    ImageViewerPlugin<DicomImageElement> container =
        EventManager.getInstance().getSelectedView2dContainer();
    if (container != null) {
      List<ViewCanvas<DicomImageElement>> views = container.getImagePanels();
      if (views != null) {
        views.forEach(this::updateGraphics);
      }
    }
  }

  private void updateGraphics(ViewCanvas<?> view) {
    if (view instanceof View2d view2d) {
      view2d.updateSegmentation();
      view2d.repaint();
    }
  }

  public void updateCanvas(ViewCanvas<?> viewCanvas) {
    if (rtSet == null || rtSet.getStructures().isEmpty()) {
      clearTrees();
      return;
    }

    updateComboBoxes();
    updateGraphics(viewCanvas);
  }

  private void clearSelectionMemory() {
    structureSetSelections.clear();
  }

  private void clearTrees() {
    // Save current selections before clearing
    StructureSet currentStructure = (StructureSet) comboRtStructureSet.getSelectedItem();
    Plan currentPlan = (Plan) comboRtPlan.getSelectedItem();

    if (currentStructure != null) {
      saveTreeSelection(
          treeStructures, getSopInstanceUid(currentStructure), structureSetSelections);
    }

    initPathSelection = true;
    nodeStructures.removeAllChildren();
    nodeIsodoses.removeAllChildren();

    treeStructures.setModel(new DefaultTreeModel(rootNodeStructures, false));
    treeIsodoses.setModel(new DefaultTreeModel(rootNodeIsodoses, false));

    comboRtStructureSet.removeAllItems();
    comboRtPlan.removeAllItems();

    // Apply default selections to empty trees
    applyDefaultTreeSelection(treeStructures);
    applyDefaultTreeSelection(treeIsodoses);

    initPathSelection = false;
  }

  private void updateComboBoxes() {
    // Save current selections
    StructureSet oldStructure = (StructureSet) comboRtStructureSet.getSelectedItem();
    Plan oldPlan = (Plan) comboRtPlan.getSelectedItem();

    // Update combo boxes
    comboRtStructureSet.removeItemListener(structureChangeListener);
    comboRtPlan.removeItemListener(planChangeListener);
    comboRtStructureSet.removeAllItems();
    comboRtPlan.removeAllItems();

    rtSet.getStructures().forEach(comboRtStructureSet::addItem);
    rtSet.getPlans().forEach(comboRtPlan::addItem);

    // Restore or set default selections
    selectDefaultItems(oldStructure, oldPlan);

    comboRtStructureSet.addItemListener(structureChangeListener);
    comboRtPlan.addItemListener(planChangeListener);
  }

  private void selectDefaultItems(StructureSet oldStructure, Plan oldPlan) {
    boolean updateTree = false;

    if (rtSet.getStructures().contains(oldStructure)) {
      comboRtStructureSet.setSelectedItem(oldStructure);
    } else {
      Set<StructureSet> structures = rtSet.getStructures();
      if (!structures.isEmpty()) {
        StructureSet mostRecentStructure =
            structures.stream()
                .filter(structure -> structure.getTagValue(TagD.get(Tag.StructureSetDate)) != null)
                .max(
                    Comparator.comparing(
                        structure -> (Date) structure.getTagValue(TagD.get(Tag.StructureSetDate))))
                .orElse(structures.iterator().next());

        comboRtStructureSet.setSelectedItem(mostRecentStructure);
        updateTree = true;
      }
    }

    if (rtSet.getPlans().contains(oldPlan)) {
      comboRtPlan.setSelectedItem(oldPlan);
    } else {
      RtSpecialElement firstPlan = rtSet.getFirstPlan();
      if (firstPlan != null) {
        comboRtPlan.setSelectedItem(firstPlan);
        updateTree = true;
      }
    }

    if (updateTree) {
      updateTree(
          (StructureSet) comboRtStructureSet.getSelectedItem(),
          (Plan) comboRtPlan.getSelectedItem());
    }
  }

  public void updateTree(StructureSet selectedStructure, Plan selectedPlan) {
    initPathSelection = true;
    try {
      // Empty tree when no RtSet
      if (rtSet == null) {
        clearTrees();
        return;
      }

      updateStructuresTree(selectedStructure);
      updateIsodosesTree(selectedPlan);
      expandTrees();
    } finally {
      initPathSelection = false;
    }
  }

  private void updateStructuresTree(StructureSet selectedStructure) {
    treeStructures.setModel(new DefaultTreeModel(rootNodeStructures, false));

    if (selectedStructure != null) {
      nodeStructures.removeAllChildren();
      Map<String, List<StructRegion>> regionMap =
          SegRegion.groupRegions(selectedStructure.getSegAttributes().values());

      for (List<StructRegion> regionList : StructRegion.sort(regionMap.values())) {
        addRegionsToTree(regionList);
      }

      // Restore saved selection or apply default (checked = true)
      String key = getSopInstanceUid(selectedStructure);
      restoreTreeSelection(treeStructures, key, structureSetSelections);
    }
  }

  private void addRegionsToTree(List<StructRegion> regionList) {
    if (regionList.size() == 1) {
      StructRegion region = regionList.getFirst();
      DefaultMutableTreeNode node = buildStructRegionNode(region);
      nodeStructures.add(node);
      treeStructures.setPathSelection(new TreePath(node.getPath()), region.isSelected());
    } else {
      GroupTreeNode groupNode = new GroupTreeNode(regionList.getFirst().getPrefix(), true);
      nodeStructures.add(groupNode);

      for (StructRegion region : regionList) {
        DefaultMutableTreeNode childNode = buildStructRegionNode(region);
        groupNode.add(childNode);
        treeStructures.setPathSelection(new TreePath(childNode.getPath()), region.isSelected());
      }

      treeStructures.addCheckingPath(new TreePath(groupNode.getPath()));
    }
  }

  private void updateIsodosesTree(Plan selectedPlan) {
    if (selectedPlan != null) {
      treeIsodoses.setModel(new DefaultTreeModel(rootNodeIsodoses, false));
      nodeIsodoses.removeAllChildren();
      updatePlanInfo(selectedPlan);

      Dose planDose = selectedPlan.getFirstDose();
      if (planDose != null) {
        addIsodosesToTree(planDose);
      }
    }
  }

  private void updatePlanInfo(Plan selectedPlan) {
    txtRtPlanDoseValue.setText(String.format("%.0f", selectedPlan.getRxDose())); // NON-NLS
  }

  private void addIsodosesToTree(Dose planDose) {
    Map<Integer, IsoDoseRegion> isodoses = planDose.getIsoDoseSet();
    if (isodoses != null) {
      for (IsoDoseRegion isoDoseRegion : isodoses.values()) {
        DefaultMutableTreeNode node = createIsodoseNode(isoDoseRegion);
        nodeIsodoses.add(node);
        treeIsodoses.addCheckingPath(new TreePath(node.getPath()));
      }
    }
    treeIsodoses.removeCheckingPath(new TreePath(nodeIsodoses.getPath()));
  }

  private DefaultMutableTreeNode createIsodoseNode(IsoDoseRegion isoDoseRegion) {
    return new StructToolTipTreeNode(isoDoseRegion, false) {
      @Override
      public String getToolTipText() {
        IsoDoseRegion layer = (IsoDoseRegion) getUserObject();
        return buildIsodoseTooltip(layer);
      }
    };
  }

  private String buildIsodoseTooltip(IsoDoseRegion layer) {
    StringBuilder buf = new StringBuilder();
    buf.append(GuiUtils.HTML_START);
    buf.append("<b>").append(layer.getLabel()).append("</b>");
    buf.append(GuiUtils.HTML_BR);
    buf.append(Messages.getString("level")).append(StringUtil.COLON_AND_SPACE);
    buf.append(String.format("%d%%", layer.getLevel())); // NON-NLS
    buf.append(GuiUtils.HTML_BR);
    buf.append(Messages.getString("thickness")).append(StringUtil.COLON_AND_SPACE);
    buf.append(DecFormatter.twoDecimal(layer.getThickness()));
    buf.append(GuiUtils.HTML_BR);

    buf.append(GuiUtils.HTML_END);

    return buf.toString();
  }

  private void expandTrees() {
    // Expand
    TreeBuilder.expandTree(treeStructures, rootNodeStructures, 2);
    TreeBuilder.expandTree(treeIsodoses, rootNodeIsodoses, 2);
  }

  private DefaultMutableTreeNode buildStructRegionNode(StructRegion structRegion) {
    return new StructToolTipTreeNode(structRegion, false) {
      @Override
      public String getToolTipText() {
        return buildStructRegionTooltip((StructRegion) getUserObject());
      }

      @Override
      public String toString() {
        StructRegion layer = (StructRegion) getUserObject();
        String label = layer.getLabel();
        String type = layer.getRtRoiInterpretedType();
        if (StringUtil.hasText(type)) {
          label += " [" + type + "]";
        }
        return getColorBullet(layer.getColor(), label);
      }
    };
  }

  private String buildStructRegionTooltip(StructRegion region) {
    StringBuilder buf = new StringBuilder();
    buf.append(GuiUtils.HTML_START);
    buf.append("<b>").append(region.getLabel()).append("</b>");
    buf.append(GuiUtils.HTML_BR);

    if (StringUtil.hasText(region.getRoiObservationLabel())) {
      buf.append(region.getRoiObservationLabel()).append(GuiUtils.HTML_BR);
    }

    buf.append(Messages.getString("thickness")).append(StringUtil.COLON_AND_SPACE);
    buf.append(String.format("%s mm", DecFormatter.twoDecimal(region.getThickness()))); // NON-NLS
    buf.append(GuiUtils.HTML_BR);

    buf.append(Messages.getString("volume")).append(StringUtil.COLON_AND_SPACE);
    buf.append(String.format("%s cm³", DecFormatter.fourDecimal(region.getVolume()))); // NON-NLS
    buf.append(GuiUtils.HTML_BR);

    Dvh dvh = region.getDvh();
    if (dvh != null) {
      appendDvhInfo(buf, dvh);
    }

    buf.append(GuiUtils.HTML_END);
    return buf.toString();
  }

  private void appendDvhInfo(StringBuilder buf, Dvh dvh) {
    String source = dvh.getDvhSource().toString();
    double rxDose = dvh.getPlan().getRxDose();

    buf.append(source).append(" ").append(Messages.getString("min.dose"));
    buf.append(StringUtil.COLON_AND_SPACE);
    buf.append(
        DecFormatter.percentTwoDecimal(
            Dose.calculateRelativeDose(dvh.getDvhMinimumDoseCGy(), rxDose) / 100.0));
    buf.append(GuiUtils.HTML_BR);

    buf.append(source).append(" ").append(Messages.getString("max.dose"));
    buf.append(StringUtil.COLON_AND_SPACE);
    buf.append(
        DecFormatter.percentTwoDecimal(
            Dose.calculateRelativeDose(dvh.getDvhMaximumDoseCGy(), rxDose) / 100.0));
    buf.append(GuiUtils.HTML_BR);

    buf.append(source).append(" ").append(Messages.getString("mean.dose"));
    buf.append(StringUtil.COLON_AND_SPACE);
    buf.append(
        DecFormatter.percentTwoDecimal(
            Dose.calculateRelativeDose(dvh.getDvhMeanDoseCGy(), rxDose) / 100.0));
    buf.append(GuiUtils.HTML_BR);
  }

  // Simplified utility methods for SegRegionTool interface
  public void show(SegRegion<?> region) {
    ViewCanvas<DicomImageElement> view = EventManager.getInstance().getSelectedViewPane();
    if (view == null || !(view.getSeries() instanceof DicomSeries series)) return;

    DicomImageElement bestImage = findBestImageForRegion(series, region);
    if (bestImage != null) {
      navigateToImage(view, series, bestImage);
    }
  }

  private DicomImageElement findBestImageForRegion(DicomSeries series, SegRegion<?> region) {
    long maxPixels = Long.MIN_VALUE;
    DicomImageElement bestImage = null;

    for (DicomImageElement dcm : series.getMedias(null, null)) {
      SegContour contour = getContour(dcm, region);
      if (contour != null && contour.getNumberOfPixels() > maxPixels) {
        maxPixels = contour.getNumberOfPixels();
        bestImage = dcm;
      }
    }
    return bestImage;
  }

  private void navigateToImage(
      ViewCanvas<DicomImageElement> view, DicomSeries series, DicomImageElement image) {
    Optional<SliderCineListener> action =
        EventManager.getInstance().getAction(ActionW.SCROLL_SERIES);
    if (action.isPresent()) {
      Filter<DicomImageElement> filter =
          (Filter<DicomImageElement>) view.getActionValue(ActionW.FILTERED_SERIES.cmd());
      int imgIndex = series.getImageIndex(image, filter, view.getCurrentSortComparator());
      action.get().setSliderValue(imgIndex + 1);
    }
  }

  private SegContour getContour(DicomImageElement imageElement, RegionAttributes attributes) {
    PlanarImage img = imageElement.getImage();
    if (img == null) return null;

    SpecialElementRegion region = getSelectedRegion();
    if (region == null) return null;

    Set<LazyContourLoader> loaders = region.getContours(imageElement);
    if (loaders == null || loaders.isEmpty()) return null;

    return loaders.stream()
        .flatMap(loader -> loader.getLazyContours().stream())
        .filter(c -> c.getAttributes().equals(attributes))
        .findFirst()
        .orElse(null);
  }

  public void computeStatistics(SegRegion<?> region) {
    ViewCanvas<DicomImageElement> view = EventManager.getInstance().getSelectedViewPane();
    DicomImageElement imageElement = getImageElement(view);
    if (imageElement == null) return;

    SegContour contour = getContour(imageElement, region);
    if (contour != null) {
      MeasurableLayer layer = view.getMeasurableLayer();
      if (region instanceof IsoDoseRegion) {
        treeIsodoses.showStatistics(contour, layer);
      } else {
        treeStructures.showStatistics(contour, layer);
      }
    }
  }

  private DicomImageElement getImageElement(ViewCanvas<DicomImageElement> view) {
    return (view != null && view.getImage() instanceof DicomImageElement element) ? element : null;
  }

  public void initTreeValues(ViewCanvas<?> viewCanvas) {
    if (viewCanvas == null) {
      this.rtSet = null;
      updateCanvas(null);
      return;
    }

    MediaSeries<?> series = viewCanvas.getSeries();
    if (!isCtLinkedRT(series) || !(series instanceof DicomSeries dicomSeries)) {
      this.rtSet = null;
      updateCanvas(viewCanvas);
      return;
    }

    String seriesUID = TagD.getTagValue(series, Tag.SeriesInstanceUID, String.class);
    if (!StringUtil.hasText(seriesUID)) {
      this.rtSet = null;
      updateCanvas(viewCanvas);
      return;
    }

    List<RtSpecialElement> rtElements = getRTSpecialElements(seriesUID);
    RtSet set = RT_SET_CACHE.get(seriesUID);
    boolean reload =
        set == null || (!rtElements.isEmpty() && !set.getRtElements().equals(rtElements));

    if (reload) {
      set = new RtSet(dicomSeries, rtElements);
      RT_SET_CACHE.put(seriesUID, set);
    }

    boolean empty = set.getStructures().isEmpty();
    btnLoad.setEnabled(empty || reload);
    this.rtSet = set;
    updateCanvas(viewCanvas);
  }

  private List<RtSpecialElement> getRTSpecialElements(String seriesUID) {
    Set<String> hiddenSeries = HiddenSeriesManager.getInstance().reference2Series.get(seriesUID);
    if (hiddenSeries != null && !hiddenSeries.isEmpty()) {
      return HiddenSeriesManager.getHiddenElementsFromSeries(
          RtSpecialElement.class, hiddenSeries.toArray(new String[0]));
    }
    return Collections.emptyList();
  }

  public static boolean isCtLinkedRT(MediaSeries<?> dcmSeries) {
    if (dcmSeries == null) return false;

    String seriesUID = TagD.getTagValue(dcmSeries, Tag.SeriesInstanceUID, String.class);
    if (!StringUtil.hasText(seriesUID)) return false;

    Set<String> hiddenSeries = HiddenSeriesManager.getInstance().reference2Series.get(seriesUID);
    return hiddenSeries != null
        && !hiddenSeries.isEmpty()
        && HiddenSeriesManager.hasHiddenElementsFromSeries(
            RtSpecialElement.class, hiddenSeries.toArray(new String[0]));
  }

  // Required overrides
  @Override
  public Component getToolComponent() {
    return getToolComponentFromJScrollPane(rootPane);
  }

  @Override
  public void changingViewContentEvent(SeriesViewerEvent event) {
    if (SeriesViewerEvent.EVENT.SELECT.equals(event.getEventType())
        && event.getSeriesViewer() instanceof ImageViewerPlugin<?> plugin) {
      initTreeValues(plugin.getSelectedImagePane());
    }
  }

  @Override
  protected void changeToolWindowAnchor(CLocation clocation) {
    // Implementation can be added if needed
  }
}
