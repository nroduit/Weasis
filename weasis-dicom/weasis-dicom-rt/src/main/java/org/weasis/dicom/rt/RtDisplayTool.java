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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.SwingWorker;
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
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.codec.DicomSeries;
import org.weasis.dicom.codec.HiddenSeriesManager;
import org.weasis.dicom.codec.SpecialElementRegion;
import org.weasis.dicom.codec.TagD;
import org.weasis.dicom.codec.seg.LazyContourLoader;
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

  /**
   * System property (configurable in {@code base.json}) enabling the experimental DVH recalculation
   * feature. The DVH calculation algorithm (derived from dicompyler) has not been clinically
   * validated; it is therefore disabled by default.
   */
  public static final String P_DVH_RECALCULATE = "weasis.rt.dvh.recalculate.enable";

  private static final String GRAPHIC_OPACITY = Messages.getString("graphic.opacity");
  private static final SoftHashMap<String, RtSet> RT_SET_CACHE = new SoftHashMap<>();

  private static final String ROOT_KEY = "ROOT"; // NON-NLS
  private static final String GROUP_PREFIX = "GROUP_"; // NON-NLS
  private static final String REGION_PREFIX = "REGION_"; // NON-NLS

  private final boolean dvhRecalculateEnabled =
      GuiUtils.getUICore().getSystemPreferences().getBooleanProperty(P_DVH_RECALCULATE, false);

  private final JScrollPane rootPane = new JScrollPane();
  private final JTabbedPane tabbedPane = new JTabbedPane();
  private final JButton btnLoad = new JButton(Messages.getString("load.rt"));
  private final JComboBox<StructureSet> comboRtStructureSet = new JComboBox<>();
  private final JComboBox<Plan> comboRtPlan = new JComboBox<>();
  private final JSliderW slider;
  private final SpinnerProgress progressBar = new SpinnerProgress();

  private final StructRegionTree treeStructures;
  private final SegRegionTree treeIsodoses;
  private final DefaultMutableTreeNode rootNodeStructures =
      new DefaultMutableTreeNode("rootNode", true);
  private final DefaultMutableTreeNode rootNodeIsodoses =
      new DefaultMutableTreeNode("rootNode", true);
  private final GroupTreeNode nodeStructures;
  private final GroupTreeNode nodeIsodoses;

  /** Map a tree to its root group node. */
  private final Map<SegRegionTree, GroupTreeNode> rootGroupOf = new HashMap<>(2);

  private final JLabel txtRtPlanDoseValue = new JLabel();

  private RtSet rtSet;
  private boolean initPathSelection;

  private final Map<String, Map<String, Boolean>> structureSetSelections = new HashMap<>();
  private final Map<String, Map<String, Boolean>> planSelections = new HashMap<>();

  /**
   * Tracks the keys that the user has explicitly toggled, per structure/plan SOPInstanceUID. Only
   * these keys are persisted in {@link #structureSetSelections} / {@link #planSelections};
   * everything else falls back to defaults on restore.
   */
  private final Map<String, Set<String>> userToggledKeys = new HashMap<>();

  /**
   * Remembers, per {@link RtSet}, the SOPInstanceUID of the last structure/plan the user explicitly
   * selected in the combo. Used by {@link #selectDefaultItems} to restore that choice when the user
   * navigates back to a previously visited RT exam, so the saved tree state is applied to the right
   * element.
   */
  private final Map<RtSet, String> lastStructureUidByRtSet = new HashMap<>();

  private final Map<RtSet, String> lastPlanUidByRtSet = new HashMap<>();

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
    this.rootGroupOf.put(treeStructures, nodeStructures);
    this.rootGroupOf.put(treeIsodoses, nodeIsodoses);

    // Initialize listeners after trees are created
    this.structureChangeListener =
        e -> {
          if (e.getStateChange() == ItemEvent.DESELECTED
              && e.getItem() instanceof StructureSet oldStructure) {
            saveTreeSelection(
                treeStructures, getSopInstanceUid(oldStructure), structureSetSelections);
          } else if (e.getStateChange() == ItemEvent.SELECTED
              && e.getItem() instanceof StructureSet newStructure) {
            rememberLastSelection(lastStructureUidByRtSet, newStructure);
            updateTree(newStructure, null);
          }
        };
    this.planChangeListener =
        e -> {
          if (e.getStateChange() == ItemEvent.DESELECTED && e.getItem() instanceof Plan oldPlan) {
            saveTreeSelection(treeIsodoses, getSopInstanceUid(oldPlan), planSelections);
          } else if (e.getStateChange() == ItemEvent.SELECTED
              && e.getItem() instanceof Plan newPlan) {
            rememberLastSelection(lastPlanUidByRtSet, newPlan);
            updateTree(null, newPlan);
          }
        };

    // Initialize slider
    this.slider = PropertiesDialog.createOpacitySlider(GRAPHIC_OPACITY);
    slider.setValue(100);
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
    JButton btnShowDvh = new JButton(Messages.getString("display.dvh.chart"));
    btnShowDvh.addActionListener(e -> showDvhChart());
    dvhPanel.add(btnShowDvh);
    headerPanel.add(dvhPanel);

    return headerPanel;
  }

  private JPanel createFlowPanel(int alignment) {
    return new JPanel(new FlowLayout(alignment));
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
    tree.addTreeCheckingListener(e -> handleTreeChecking(tree, e));

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

    Set<String> toggled = userToggledKeys.get(key);
    if (toggled == null || toggled.isEmpty()) {
      // No user interaction: drop any previous save so the default state applies on next load.
      selectionMap.remove(key);
      return;
    }

    Map<String, Boolean> nodeSelections = new HashMap<>();
    GroupTreeNode rootNode = rootGroupOf.get(tree);
    TreeCheckingModel checking = tree.getCheckingModel();
    if (toggled.contains(ROOT_KEY)) {
      nodeSelections.put(ROOT_KEY, checking.isPathChecked(new TreePath(rootNode.getPath())));
    }
    walkAllNodes(
        rootNode,
        node -> {
          String nodeKey = nodeToKey(tree, node);
          if (nodeKey != null && toggled.contains(nodeKey)) {
            nodeSelections.put(nodeKey, checking.isPathChecked(new TreePath(node.getPath())));
          }
        });

    if (nodeSelections.isEmpty()) {
      selectionMap.remove(key);
    } else {
      selectionMap.put(key, nodeSelections);
    }
  }

  /** Visits every {@link GroupTreeNode} descendant (depth-first) of {@code parentNode}. */
  private static void walkGroupNodes(
      DefaultMutableTreeNode parentNode, java.util.function.Consumer<GroupTreeNode> action) {
    for (int i = 0; i < parentNode.getChildCount(); i++) {
      if (parentNode.getChildAt(i) instanceof GroupTreeNode groupNode) {
        action.accept(groupNode);
        walkGroupNodes(groupNode, action);
      }
    }
  }

  /** Visits every {@link DefaultMutableTreeNode} descendant (depth-first) of {@code parentNode}. */
  private static void walkAllNodes(
      DefaultMutableTreeNode parentNode,
      java.util.function.Consumer<DefaultMutableTreeNode> action) {
    for (int i = 0; i < parentNode.getChildCount(); i++) {
      if (parentNode.getChildAt(i) instanceof DefaultMutableTreeNode node) {
        action.accept(node);
        walkAllNodes(node, action);
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

    // Always start from the defaults: this guarantees that nodes not explicitly toggled by the
    // user fall back to their default checked state on every reload (e.g. the structures root
    // node remains checked when no user action was taken).
    applyDefaultTreeSelection(tree);

    Map<String, Boolean> savedSelections = selectionMap.get(key);
    if (savedSelections != null && !savedSelections.isEmpty()) {
      overlaySavedSelections(tree, savedSelections);
    }
  }

  private void overlaySavedSelections(SegRegionTree tree, Map<String, Boolean> savedSelections) {
    GroupTreeNode rootNode = rootGroupOf.get(tree);
    Boolean rootSelection = savedSelections.get(ROOT_KEY);
    if (rootSelection != null) {
      tree.setPathSelection(new TreePath(rootNode.getPath()), rootSelection);
    }
    walkAllNodes(
        rootNode,
        node -> {
          String nodeKey = nodeToKey(tree, node);
          if (nodeKey == null) {
            return;
          }
          Boolean sel = savedSelections.get(nodeKey);
          if (sel != null) {
            tree.setPathSelection(new TreePath(node.getPath()), sel);
          }
        });
  }

  private void applyDefaultTreeSelection(SegRegionTree tree) {
    // initPathSelection is always managed by the caller
    GroupTreeNode rootNode = rootGroupOf.get(tree);
    // Structures tree defaults to checked, isodoses tree defaults to unchecked
    tree.setPathSelection(new TreePath(rootNode.getPath()), tree == treeStructures);
    walkGroupNodes(rootNode, group -> tree.setPathSelection(new TreePath(group.getPath()), true));
  }

  private void loadData() {
    if (rtSet == null || rtSet.getPatientImage() == null) return;

    SwingWorker<Boolean, Void> loadTask =
        new SwingWorker<>() {
          @Override
          protected Boolean doInBackground() {
            rtSet.reloadRtCase();
            return true;
          }
        };

    loadTask.addPropertyChangeListener(
        evt -> {
          if ("state".equals(evt.getPropertyName())) {
            handleLoadTaskStateChange((StateValue) evt.getNewValue());
          }
        });

    loadTask.execute();
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
      setInitialVisibility(true);

      // Update GUI (initSlider is called inside updateCanvas)
      updateCanvas(EventManager.getInstance().getSelectedViewPane());
      updateCurrentContainer();
    }
  }

  /** Build and show the DVH chart for the currently selected structures. */
  private void showDvhChart() {
    if (rtSet == null) return;

    List<StructRegion> selected = getCheckedStructRegions();
    if (selected.isEmpty()) return;

    List<StructRegion> withDvh = new ArrayList<>();
    List<StructRegion> withoutDvh = new ArrayList<>();
    for (StructRegion region : selected) {
      if (region.getDvh() != null) {
        withDvh.add(region);
      } else {
        withoutDvh.add(region);
      }
    }

    if (!withoutDvh.isEmpty() && dvhRecalculateEnabled) {
      promptAndComputeMissingDvh(withDvh, withoutDvh);
    } else {
      buildAndShowChart(withDvh);
    }
  }

  private void promptAndComputeMissingDvh(
      List<StructRegion> withDvh, List<StructRegion> withoutDvh) {
    String message =
        String.format(
            Messages.getString("dvh.compute.missing.confirm"),
            withoutDvh.size(),
            withoutDvh.stream().map(StructRegion::getLabel).collect(Collectors.joining(", ")));
    int answer =
        JOptionPane.showConfirmDialog(
            WinUtil.getParentWindow(this),
            message,
            Messages.getString("dvh.experimental.warning"),
            JOptionPane.YES_NO_OPTION,
            JOptionPane.WARNING_MESSAGE);
    if (answer != JOptionPane.YES_OPTION) {
      buildAndShowChart(withDvh);
      return;
    }

    SwingWorker<List<StructRegion>, Void> task = getListVoidSwingWorker(withDvh, withoutDvh);
    progressBar.setVisible(true);
    progressBar.setIndeterminate(true);
    task.execute();
  }

  private SwingWorker<List<StructRegion>, Void> getListVoidSwingWorker(
      List<StructRegion> withDvh, List<StructRegion> withoutDvh) {
    final List<StructRegion> all = new ArrayList<>(withDvh);
    return new SwingWorker<>() {
      @Override
      protected List<StructRegion> doInBackground() {
        for (StructRegion region : withoutDvh) {
          if (rtSet.computeDvhOnDemand(region) && region.getDvh() != null) {
            all.add(region);
          }
        }
        return all;
      }

      @Override
      protected void done() {
        progressBar.setVisible(false);
        buildAndShowChart(all);
      }
    };
  }

  private void buildAndShowChart(List<StructRegion> regions) {
    if (regions.isEmpty()) {
      return;
    }
    XYChart chart =
        new XYChartBuilder()
            .width(800)
            .height(500)
            .title("DVH")
            .xAxisTitle(Messages.getString("dose.cgy"))
            .yAxisTitle(Messages.getString("volume") + " (%)")
            .build();

    chart.getStyler().setDefaultSeriesRenderStyle(XYSeries.XYSeriesRenderStyle.Line);

    for (StructRegion region : regions) {
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

  /**
   * Return all currently checked structure regions. The DVH may be {@code null} for some of them
   * (see {@link #showDvhChart()} for the handling of missing DVHs).
   */
  private List<StructRegion> getCheckedStructRegions() {
    return Arrays.stream(treeStructures.getCheckingModel().getCheckingPaths())
        .filter(treeStructures::hasAllParentsChecked)
        .<StructRegion>mapMulti(
            (p, sink) -> {
              if (((DefaultMutableTreeNode) p.getLastPathComponent()).getUserObject()
                  instanceof StructRegion r) {
                sink.accept(r);
              }
            })
        .toList();
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

  /**
   * Routes a tree checking event: records the user-driven toggle (so the choice persists across
   * loads) and then refreshes the visible nodes. The selection is also saved immediately so that
   * the state survives intermediate operations such as tab switches or programmatic refreshes that
   * might rebuild the tree without going through the combo-box change listener.
   */
  private void handleTreeChecking(SegRegionTree tree, TreeCheckingEvent e) {
    if (initPathSelection) {
      return;
    }
    recordUserToggle(tree, e.getPath());
    persistCurrentSelection(tree);
    updateVisibleNode();
  }

  /** Persists the current selection state of {@code tree} into the appropriate selection map. */
  private void persistCurrentSelection(SegRegionTree tree) {
    String uid = currentSelectionUid(tree);
    if (uid == null) {
      return;
    }
    Map<String, Map<String, Boolean>> map =
        (tree == treeIsodoses) ? planSelections : structureSetSelections;
    saveTreeSelection(tree, uid, map);
  }

  /**
   * Marks the key associated with {@code path} as user-modified for the currently selected
   * structure (for the structures tree) or plan (for the isodoses tree), so that it is persisted by
   * {@link #saveTreeSelection}.
   */
  private void recordUserToggle(SegRegionTree tree, TreePath path) {
    if (path == null) {
      return;
    }
    String uid = currentSelectionUid(tree);
    if (uid == null) {
      return;
    }
    String key = pathToKey(tree, path);
    if (key != null) {
      userToggledKeys.computeIfAbsent(uid, k -> new HashSet<>()).add(key);
    }
  }

  /** Returns the SOPInstanceUID of the combo selection that backs {@code tree}. */
  private String currentSelectionUid(SegRegionTree tree) {
    if (tree == treeStructures) {
      return getSopInstanceUid((StructureSet) comboRtStructureSet.getSelectedItem());
    }
    if (tree == treeIsodoses) {
      return getSopInstanceUid((Plan) comboRtPlan.getSelectedItem());
    }
    return null;
  }

  /**
   * Returns the persistence key associated with {@code path} or {@code null} if the last component
   * is not a node we know how to identify.
   */
  private String pathToKey(SegRegionTree tree, TreePath path) {
    if (path.getLastPathComponent() instanceof DefaultMutableTreeNode node) {
      return nodeToKey(tree, node);
    }
    return null;
  }

  /**
   * Returns a stable persistence key for {@code node}: {@link #ROOT_KEY} for the visible root
   * group, {@code GROUP_<label>} for inner {@link GroupTreeNode}s, and {@code REGION_<id>} for leaf
   * nodes whose user object is a {@link SegRegion} (struct regions, isodose regions). Returns
   * {@code null} for nodes that should not be persisted.
   */
  private String nodeToKey(SegRegionTree tree, DefaultMutableTreeNode node) {
    GroupTreeNode rootGroup = rootGroupOf.get(tree);
    if (node == rootGroup) {
      return ROOT_KEY;
    }
    if (node instanceof GroupTreeNode group) {
      return GROUP_PREFIX + group;
    }
    Object userObject = node.getUserObject();
    if (userObject instanceof SegRegion<?> region) {
      return REGION_PREFIX + region.getId();
    }
    return null;
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
    DefaultMutableTreeNode root = tree == treeStructures ? rootNodeStructures : rootNodeIsodoses;
    tree.updateVisibleNode(root, node);
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
    initSlider();
    updateGraphics(viewCanvas);
  }

  private void clearTrees() {
    // Save current selections before clearing
    StructureSet currentStructure = (StructureSet) comboRtStructureSet.getSelectedItem();
    Plan currentPlan = (Plan) comboRtPlan.getSelectedItem();

    if (currentStructure != null) {
      saveTreeSelection(
          treeStructures, getSopInstanceUid(currentStructure), structureSetSelections);
    }
    if (currentPlan != null) {
      saveTreeSelection(treeIsodoses, getSopInstanceUid(currentPlan), planSelections);
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

    // Discard any tracked user toggles: defaults must apply on next load.
    userToggledKeys.clear();

    initPathSelection = false;
  }

  private void updateComboBoxes() {
    // Save current selections
    StructureSet oldStructure = (StructureSet) comboRtStructureSet.getSelectedItem();
    Plan oldPlan = (Plan) comboRtPlan.getSelectedItem();

    // Persist the tree selection for the current structure/plan before switching away,
    // because removing the item listener prevents the DESELECTED event from firing.
    saveTreeSelection(treeStructures, getSopInstanceUid(oldStructure), structureSetSelections);
    saveTreeSelection(treeIsodoses, getSopInstanceUid(oldPlan), planSelections);

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

    StructureSet structureToSelect = pickStructureToSelect(oldStructure);
    if (structureToSelect != null) {
      comboRtStructureSet.setSelectedItem(structureToSelect);
      updateTree |= structureToSelect != oldStructure;
    }

    Plan planToSelect = pickPlanToSelect(oldPlan);
    if (planToSelect != null) {
      comboRtPlan.setSelectedItem(planToSelect);
      updateTree |= planToSelect != oldPlan;
    }

    if (updateTree) {
      updateTree(
          (StructureSet) comboRtStructureSet.getSelectedItem(),
          (Plan) comboRtPlan.getSelectedItem());
    }
  }

  /**
   * Returns the {@link StructureSet} that should be selected: prefer the outgoing one if still
   * present, otherwise the last user-chosen structure for this {@link RtSet}, otherwise the most
   * recent.
   */
  private StructureSet pickStructureToSelect(StructureSet oldStructure) {
    Set<StructureSet> structures = rtSet.getStructures();
    if (structures.contains(oldStructure)) {
      return oldStructure;
    }
    StructureSet remembered =
        findByUid(structures, lastStructureUidByRtSet.get(rtSet), this::getSopInstanceUid);
    if (remembered != null) {
      return remembered;
    }
    return pickMostRecentStructure(structures).orElse(null);
  }

  /**
   * Returns the {@link Plan} that should be selected: prefer the outgoing one if still present,
   * otherwise the last user-chosen plan for this {@link RtSet}, otherwise the first plan.
   */
  private Plan pickPlanToSelect(Plan oldPlan) {
    Set<Plan> plans = rtSet.getPlans();
    if (plans.contains(oldPlan)) {
      return oldPlan;
    }
    Plan remembered = findByUid(plans, lastPlanUidByRtSet.get(rtSet), this::getSopInstanceUid);
    if (remembered != null) {
      return remembered;
    }
    return rtSet.getFirstPlan() instanceof Plan p ? p : null;
  }

  private static <T> T findByUid(
      Set<T> elements, String uid, java.util.function.Function<T, String> uidExtractor) {
    if (uid == null || elements == null || elements.isEmpty()) {
      return null;
    }
    for (T e : elements) {
      if (uid.equals(uidExtractor.apply(e))) {
        return e;
      }
    }
    return null;
  }

  /**
   * Records the SOPInstanceUID of {@code element} as the last user choice for the current RtSet.
   */
  private void rememberLastSelection(Map<RtSet, String> map, RtSpecialElement element) {
    if (rtSet == null || element == null) {
      return;
    }
    String uid = getSopInstanceUid(element);
    if (uid != null) {
      map.put(rtSet, uid);
    }
  }

  private static Optional<StructureSet> pickMostRecentStructure(Set<StructureSet> structures) {
    if (structures.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(
        structures.stream()
            .filter(s -> s.getTagValue(TagD.get(Tag.StructureSetDate)) != null)
            .max(Comparator.comparing(s -> (Date) s.getTagValue(TagD.get(Tag.StructureSetDate))))
            .orElse(structures.iterator().next()));
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
    // Make sure the displayed graphics reflect the freshly restored tree state. The check events
    // fired during the rebuild were ignored (initPathSelection was true), so region visibility
    // would otherwise stay stale until the user manually toggled or moved the slider.
    syncAllRegionsFromTrees();
  }

  /**
   * Reads the current checking state of both trees and pushes it down to each region's {@code
   * visible} flag, then asks the viewer to repaint. Safe to call after any rebuild that temporarily
   * sets {@link #initPathSelection} to {@code true}.
   */
  private void syncAllRegionsFromTrees() {
    StructureSet structure = (StructureSet) comboRtStructureSet.getSelectedItem();
    if (structure != null) {
      updateTreeVisibility(treeStructures, nodeStructures, structure);
    }
    Plan plan = (Plan) comboRtPlan.getSelectedItem();
    if (plan != null) {
      Dose dose = plan.getFirstDose();
      if (dose != null) {
        updateTreeVisibility(treeIsodoses, nodeIsodoses, dose);
      }
    }
    updateCurrentContainer();
  }

  private void updateStructuresTree(StructureSet selectedStructure) {
    if (selectedStructure == null) {
      // Nothing to update: preserve the current struct tree state as-is.
      return;
    }
    nodeStructures.removeAllChildren();
    treeStructures.setModel(new DefaultTreeModel(rootNodeStructures, false));

    Map<String, List<StructRegion>> regionMap =
        SegRegion.groupRegions(selectedStructure.getSegAttributes().values());

    for (List<StructRegion> regionList : StructRegion.sort(regionMap.values())) {
      addRegionsToTree(regionList);
    }

    // Restore saved selection or apply default (checked = true)
    String key = getSopInstanceUid(selectedStructure);
    restoreTreeSelection(treeStructures, key, structureSetSelections);
  }

  private void addRegionsToTree(List<StructRegion> regionList) {
    if (regionList.size() == 1) {
      addRegionNode(regionList.getFirst(), nodeStructures);
      return;
    }
    StructRegion first = regionList.getFirst();
    if (first.getLabel().equals(first.getPrefix())) {
      // Labels are identical: skip parent node, add regions directly
      regionList.forEach(r -> addRegionNode(r, nodeStructures));
      return;
    }
    GroupTreeNode groupNode = new GroupTreeNode(first.getPrefix(), true);
    nodeStructures.add(groupNode);
    regionList.forEach(r -> addRegionNode(r, groupNode));
    treeStructures.addCheckingPath(new TreePath(groupNode.getPath()));
  }

  private void addRegionNode(StructRegion region, DefaultMutableTreeNode parent) {
    DefaultMutableTreeNode node = buildStructRegionNode(region);
    parent.add(node);
    treeStructures.setPathSelection(new TreePath(node.getPath()), region.isSelected());
  }

  private void updateIsodosesTree(Plan selectedPlan) {
    if (selectedPlan != null) {
      nodeIsodoses.removeAllChildren();
      treeIsodoses.setModel(new DefaultTreeModel(rootNodeIsodoses, false));
      updatePlanInfo(selectedPlan);

      Dose planDose = selectedPlan.getFirstDose();
      if (planDose != null) {
        addIsodosesToTree(planDose);
      }
      // Restore saved selection or apply default (root unchecked).
      restoreTreeSelection(treeIsodoses, getSopInstanceUid(selectedPlan), planSelections);
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
    return GuiUtils.HTML_START
        + "<b>"
        + layer.getLabel()
        + "</b>"
        + GuiUtils.HTML_BR
        + Messages.getString("level")
        + StringUtil.COLON_AND_SPACE
        + "%d%%".formatted(layer.getLevel())
        + GuiUtils.HTML_BR // NON-NLS
        + Messages.getString("thickness")
        + StringUtil.COLON_AND_SPACE
        + DecFormatter.twoDecimal(layer.getThickness())
        + GuiUtils.HTML_BR
        + GuiUtils.HTML_END;
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
    appendDoseLine(buf, source, "min.dose", dvh.getDvhMinimumDoseCGy(), rxDose);
    appendDoseLine(buf, source, "max.dose", dvh.getDvhMaximumDoseCGy(), rxDose);
    appendDoseLine(buf, source, "mean.dose", dvh.getDvhMeanDoseCGy(), rxDose);
  }

  private static void appendDoseLine(
      StringBuilder buf, String source, String i18nKey, double doseCGy, double rxDose) {
    buf.append(source).append(' ').append(Messages.getString(i18nKey));
    buf.append(StringUtil.COLON_AND_SPACE);
    buf.append(DecFormatter.percentTwoDecimal(Dose.calculateRelativeDose(doseCGy, rxDose) / 100.0));
    buf.append(GuiUtils.HTML_BR);
  }

  // Simplified utility methods for SegRegionTool interface
  /** Scrolls the active viewer to the slice that contains the largest part of {@code region}. */
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

  /**
   * Reset the tool to reflect the RT objects associated with {@code viewCanvas}; falls back to an
   * empty state if no RT element is linked or if the series is not RT-compatible.
   */
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
    if (hiddenSeries == null || hiddenSeries.isEmpty()) {
      return List.of();
    }
    return HiddenSeriesManager.getHiddenElementsFromSeries(
        RtSpecialElement.class, hiddenSeries.toArray(new String[0]));
  }

  /** {@code true} if {@code dcmSeries} is a CT/MR series referenced by at least one RT object. */
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
      initTreeValues(plugin.getSelectedViewCanvas());
    }
  }

  @Override
  protected void changeToolWindowAnchor(CLocation clocation) {
    // Implementation can be added if needed
  }
}
