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
import org.weasis.core.api.gui.task.CircularProgressBar;
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
  public static final String FORMAT = "%.3f %%"; // NON-NLS
  private static final SoftHashMap<String, RtSet> RtSet_Cache = new SoftHashMap<>();

  private final JTabbedPane tabbedPane = new JTabbedPane();
  private final JScrollPane rootPane;
  private final JButton btnLoad = new JButton(Messages.getString("load.rt"));
  private final JCheckBox cbDvhRecalculate = new JCheckBox(Messages.getString("dvh.recalculate"));

  private final StructRegionTree treeStructures;
  private final SegRegionTree treeIsodoses;
  private boolean initPathSelection;
  private final DefaultMutableTreeNode rootNodeStructures =
      new DefaultMutableTreeNode("rootNode", true); // NON-NLS
  private final DefaultMutableTreeNode rootNodeIsodoses =
      new DefaultMutableTreeNode("rootNode", true); // NON-NLS
  private final JLabel lblRtStructureSet =
      new JLabel(Messages.getString("structure.set") + StringUtil.COLON);
  private final JComboBox<StructureSet> comboRtStructureSet = new JComboBox<>();
  private final JLabel lblRtPlan = new JLabel(Messages.getString("plan") + StringUtil.COLON);
  private final JComboBox<Plan> comboRtPlan = new JComboBox<>();
  private final JLabel lblRtPlanName = new JLabel();
  private final JLabel lblRtPlanDose = new JLabel(Messages.getString("dose") + StringUtil.COLON);
  private final JTextField txtRtPlanDoseValue = new JTextField();
  private final JLabel lblRtPlanDoseUnit = new JLabel("cGy"); // NON-NLS
  private final GroupTreeNode nodeStructures;
  private final GroupTreeNode nodeIsodoses;
  private final CircularProgressBar progressBar = new CircularProgressBar();
  private RtSet rtSet;
  private final transient ItemListener structureChangeListener =
      e -> {
        if (e.getStateChange() == ItemEvent.SELECTED) {
          updateTree((StructureSet) e.getItem(), null);
        }
      };
  private final transient ItemListener planChangeListener =
      e -> {
        if (e.getStateChange() == ItemEvent.SELECTED) {
          updateTree(null, (Plan) e.getItem());
        }
      };
  private final JSliderW slider;

  public RtDisplayTool() {
    super(BUTTON_NAME, Insertable.Type.TOOL, 30);
    this.setLayout(new BorderLayout(0, 0));
    this.rootPane = new JScrollPane();
    this.dockable.setTitleIcon(ResourceUtil.getIcon(OtherIcon.RADIOACTIVE));
    this.setDockableWidth(350);
    rootPane.setBorder(BorderFactory.createEmptyBorder()); // remove default line
    this.btnLoad.setToolTipText(Messages.getString("populate.rt.objects"));
    // By default, recalculate DVH only when it is missing for structure
    this.cbDvhRecalculate.setSelected(false);
    this.cbDvhRecalculate.setToolTipText(Messages.getString("when.enabled.recalculate"));
    this.lblRtStructureSet.setVisible(false);
    this.comboRtStructureSet.setVisible(false);
    this.lblRtPlan.setVisible(false);
    this.comboRtPlan.setVisible(false);
    this.lblRtPlanName.setVisible(false);
    this.lblRtPlanDose.setVisible(false);
    this.txtRtPlanDoseValue.setVisible(false);
    this.lblRtPlanDoseUnit.setVisible(false);
    // this.btnShowDvh.setVisible(false);
    this.slider = PropertiesDialog.createOpacitySlider(GRAPHIC_OPACITY);
    slider.setValue(80);
    PropertiesDialog.updateSlider(slider, GRAPHIC_OPACITY);
    slider.addChangeListener(
        l -> {
          updateSlider();
        });

    this.treeStructures = new StructRegionTree(this);
    treeStructures.setToolTipText(StringUtil.EMPTY_STRING);
    treeStructures.setCellRenderer(TreeBuilder.buildNoIconCheckboxTreeCellRenderer());

    this.treeIsodoses = new SegRegionTree(this);
    treeIsodoses.setToolTipText(StringUtil.EMPTY_STRING);
    treeIsodoses.setCellRenderer(TreeBuilder.buildNoIconCheckboxTreeCellRenderer());

    this.nodeStructures = new GroupTreeNode(Messages.getString("structures"), true);
    this.nodeIsodoses = new GroupTreeNode(Messages.getString("isodoses"), true);

    initData();
    initListeners();
  }

  private void initListeners() {
    treeStructures.initListeners();
    treeIsodoses.initListeners();
  }

  private void loadData() {
    final RtSet rt = this.rtSet;
    if (rt.getPatientImage() == null) {
      return;
    }
    SwingWorker<Boolean, Boolean> loadTask =
        new SwingWorker<>() {

          @Override
          protected Boolean doInBackground() {
            // Reload RT case data objects for GUI
            rt.reloadRtCase(cbDvhRecalculate.isSelected());
            return true;
          }
        };

    loadTask.addPropertyChangeListener(
        evt -> {
          if ("progress".equals(evt.getPropertyName())) {
            int progress = (Integer) evt.getNewValue();
            progressBar.setValue(progress);

          } else if ("state".equals(evt.getPropertyName())) {
            if (StateValue.STARTED == evt.getNewValue()) {
              btnLoad.setEnabled(false);
              progressBar.setVisible(true);
              progressBar.setIndeterminate(true);

            } else if (StateValue.DONE == evt.getNewValue()) {
              progressBar.setIndeterminate(false);
              progressBar.setVisible(false);
              btnLoad.setEnabled(false);
              btnLoad.setToolTipText(Messages.getString("rt.objects.from.loaded"));
              cbDvhRecalculate.setEnabled(false);
              cbDvhRecalculate.setToolTipText(Messages.getString("dvh.calculation"));
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
              // btnShowDvh.setVisible(true);

              initSlider();

              // Update GUI
              ImageViewerPlugin<DicomImageElement> container =
                  EventManager.getInstance().getSelectedView2dContainer();
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

  private boolean isDoseSelected() {
    return tabbedPane.getSelectedIndex() == 1;
  }

  private DicomImageElement getImageElement(ViewCanvas<DicomImageElement> view) {
    if (view != null && view.getImage() instanceof DicomImageElement imageElement) {
      return imageElement;
    }
    return null;
  }

  private DicomSeries getSeries(ViewCanvas<DicomImageElement> view) {
    if (view != null && view.getSeries() instanceof DicomSeries series) {
      return series;
    }
    return null;
  }

  private SegContour getContour(DicomImageElement imageElement, RegionAttributes attributes) {
    PlanarImage img = imageElement.getImage();
    if (img != null) {
      SpecialElementRegion region = getSelectedRegion();
      if (region != null) {
        Collection<SegContour> segments = region.getContours(imageElement);
        for (SegContour c : segments) {
          if (c.getAttributes().equals(attributes)) {
            return c;
          }
        }
      }
    }
    return null;
  }

  public void show(SegRegion<?> region) {
    ViewCanvas<DicomImageElement> view = EventManager.getInstance().getSelectedViewPane();
    DicomSeries series = (DicomSeries) view.getSeries();
    if (series != null) {
      long max = Long.MIN_VALUE;
      DicomImageElement bestImage = null;
      for (DicomImageElement dcm : series.getMedias(null, null)) {
        SegContour c = getContour(dcm, region);
        if (c != null) {
          if (c.getNumberOfPixels() > max) {
            max = c.getNumberOfPixels();
            bestImage = dcm;
          }
        }
      }
      if (bestImage != null) {
        Optional<SliderCineListener> action =
            EventManager.getInstance().getAction(ActionW.SCROLL_SERIES);
        if (action.isPresent()) {
          Filter<DicomImageElement> filter =
              (Filter<DicomImageElement>) view.getActionValue(ActionW.FILTERED_SERIES.cmd());
          int imgIndex = series.getImageIndex(bestImage, filter, view.getCurrentSortComparator());
          action.get().setSliderValue(imgIndex + 1);
        }
      }
    }
  }

  public void computeStatistics(SegRegion<?> region) {
    ViewCanvas<DicomImageElement> view = EventManager.getInstance().getSelectedViewPane();
    DicomImageElement imageElement = getImageElement(view);
    if (imageElement != null) {
      SegContour c = getContour(imageElement, region);
      if (c != null) {
        MeasurableLayer layer = view.getMeasurableLayer();
        if (region instanceof IsoDoseRegion) {
          treeIsodoses.showStatistics(c, layer);
        } else {
          treeStructures.showStatistics(c, layer);
        }
      }
    }
  }

  public void initData() {
    JPanel panelHead = new JPanel();
    add(panelHead, BorderLayout.NORTH);
    panelHead.setLayout(new BoxLayout(panelHead, BoxLayout.Y_AXIS));
    this.btnLoad.setToolTipText(Messages.getString("populate.rt.objects"));
    this.comboRtStructureSet.setVisible(false);
    this.comboRtPlan.setVisible(false);

    // RT data load panel
    JPanel panelLoad = new JPanel();
    panelHead.add(panelLoad);
    FlowLayout flPanelLoad = (FlowLayout) panelLoad.getLayout();
    flPanelLoad.setAlignment(FlowLayout.LEFT);
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
    JPanel panelDvh = new JPanel();
    FlowLayout flDvh = (FlowLayout) panelDvh.getLayout();
    flDvh.setAlignment(FlowLayout.LEFT);
    panelHead.add(panelDvh);
    // By default, recalculate DVH only when it is missing for structure
    panelDvh.add(cbDvhRecalculate);
    this.cbDvhRecalculate.setSelected(false);
    this.cbDvhRecalculate.setToolTipText(Messages.getString("when.enabled.recalculate"));

    JButton btnShowDvh = new JButton(Messages.getString("display.dvh.chart"));
    btnShowDvh.addActionListener(e -> showDvhChart());
    panelDvh.add(btnShowDvh);

    this.btnLoad.addActionListener(e -> loadData());

    add(tabbedPane, BorderLayout.CENTER);

    MigLayout layout2 = new MigLayout("fillx, ins 5lp", "[fill]", "[]10lp[]"); // NON-NLS
    JPanel panelBottom = new JPanel(layout2);
    panelBottom.add(slider);
    add(panelBottom, BorderLayout.SOUTH);

    initStructureTree();
    initIsodosesTree();

    initSlider();
    tabbedPane.addChangeListener(e -> initSlider());
  }

  private void showDvhChart() {
    RtSet rt = rtSet;
    if (rt != null) {
      List<StructRegion> structs = getStructureSelection();
      if (!structs.isEmpty()) {
        XYChart dvhChart =
            new XYChartBuilder()
                .width(800)
                .height(500)
                .title("DVH")
                .xAxisTitle(Messages.getString("dose.cgy"))
                .yAxisTitle(Messages.getString("volume") + " (%)")
                .build();
        dvhChart.getStyler().setDefaultSeriesRenderStyle(XYSeries.XYSeriesRenderStyle.Line);
        for (StructRegion region : structs) {
          Dvh structureDvh = region.getDvh();
          structureDvh.appendChart(region, dvhChart);
        }

        JDialog d = new JDialog(WinUtil.getParentWindow(this), Messages.getString("dvh.chart"));
        XChartPanel<XYChart> chartPanel = new XChartPanel<>(dvhChart);
        d.getContentPane().add(chartPanel, BorderLayout.CENTER);
        d.pack();
        GuiUtils.showCenterScreen(d);
      }
    }
  }

  private void initSlider() {
    RtSet rt = rtSet;
    if (rt != null) {
      float opacity = 1.0f;
      SpecialElementRegion region = getSelectedRegion();
      if (region != null) {
        opacity = region.getOpacity();
      }
      slider.setValue((int) (opacity * 100));
      PropertiesDialog.updateSlider(slider, GRAPHIC_OPACITY);
    }
  }

  private SpecialElementRegion getSelectedRegion() {
    SpecialElementRegion region = null;
    if (isDoseSelected()) {
      Plan plan = (Plan) comboRtPlan.getSelectedItem();
      if (plan != null) {
        Dose dose = plan.getFirstDose();
        if (dose != null) {
          region = dose;
        }
      }
    } else {
      StructureSet structureSet = (StructureSet) comboRtStructureSet.getSelectedItem();
      if (structureSet != null) {
        region = structureSet;
      }
    }
    return region;
  }

  public void initStructureTree() {
    this.treeStructures.getCheckingModel().setCheckingMode(TreeCheckingModel.CheckingMode.SIMPLE);
    this.treeStructures.setVisible(false);
    buildTreeModel(rootNodeStructures, treeStructures, nodeStructures);
  }

  public void initIsodosesTree() {
    this.treeIsodoses.getCheckingModel().setCheckingMode(TreeCheckingModel.CheckingMode.SIMPLE);
    this.treeIsodoses.setVisible(false);
    buildTreeModel(rootNodeIsodoses, treeIsodoses, nodeIsodoses);
  }

  private void buildTreeModel(
      DefaultMutableTreeNode rootNode, SegRegionTree tree, GroupTreeNode groupTreeNode) {
    DefaultTreeModel model = new DefaultTreeModel(rootNode, false);
    tree.setModel(model);

    rootNode.add(groupTreeNode);
    TreePath rootPath = new TreePath(rootNode.getPath());
    tree.addCheckingPath(rootPath);
    tree.setShowsRootHandles(true);
    tree.setRootVisible(false);
    tree.setExpandsSelectedPaths(true);
    tree.setCellRenderer(TreeBuilder.buildNoIconCheckboxTreeCellRenderer());
    tree.addTreeCheckingListener(this::treeValueChanged);

    TreeBuilder.expandTree(tree, rootNode, 2);
    Dimension minimumSize = GuiUtils.getDimension(150, 150);
    JScrollPane scrollPane = new JScrollPane(tree);
    scrollPane.setMinimumSize(minimumSize);
    scrollPane.setPreferredSize(minimumSize);
    tabbedPane.add(scrollPane, groupTreeNode.toString());
  }

  private void updateSlider() {
    float value = PropertiesDialog.updateSlider(slider, GRAPHIC_OPACITY);
    RtSet rt = rtSet;
    if (rt != null) {
      SpecialElementRegion region = getSelectedRegion();
      if (region != null) {
        region.setOpacity(value);
      }
      updateVisibleNode();
    }
  }

  private void treeValueChanged(TreeCheckingEvent e) {
    if (!initPathSelection) {
      updateVisibleNode();
    }
  }

  private List<StructRegion> getStructureSelection() {
    ArrayList<StructRegion> list = new ArrayList<>();
    if (treeStructures.getCheckingModel().isPathChecked(new TreePath(nodeStructures.getPath()))) {
      TreePath[] paths = treeStructures.getCheckingModel().getCheckingPaths();
      for (TreePath treePath : paths) {
        DefaultMutableTreeNode node = (DefaultMutableTreeNode) treePath.getLastPathComponent();
        if (node.getUserObject() instanceof StructRegion region && region.getDvh() != null) {
          list.add(region);
        }
      }
      return list;
    }
    return Collections.emptyList();
  }

  public void updateVisibleNode() {
    SpecialElementRegion region = getSelectedRegion();
    if (region instanceof StructureSet) {
      boolean all =
          treeStructures.getCheckingModel().isPathChecked(new TreePath(nodeStructures.getPath()));
      region.setVisible(all);
      nodeStructures.setSelected(all);
      treeStructures.updateVisibleNode(rootNodeStructures, nodeStructures);
    } else if (region instanceof Dose) {
      boolean all =
          treeIsodoses.getCheckingModel().isPathChecked(new TreePath(nodeIsodoses.getPath()));
      region.setVisible(all);
      nodeIsodoses.setSelected(all);
      treeIsodoses.updateVisibleNode(rootNodeIsodoses, nodeIsodoses);
    }

    updateCurrentContainer();
  }

  public void updateCurrentContainer() {
    ImageViewerPlugin<DicomImageElement> container =
        EventManager.getInstance().getSelectedView2dContainer();
    List<ViewCanvas<DicomImageElement>> views = null;
    if (container != null) {
      views = container.getImagePanels();
    }
    if (views != null) {
      for (ViewCanvas<DicomImageElement> v : views) {
        if (v instanceof View2d view2d) {
          view2d.updateSegmentation();
          view2d.repaint();
        }
      }
    }
  }

  public void updateCanvas(ViewCanvas<?> viewCanvas) {
    RtSet rt = rtSet;
    if (rt == null || rt.getStructures().isEmpty()) {
      initPathSelection = true;
      nodeStructures.removeAllChildren();
      nodeIsodoses.removeAllChildren();

      treeStructures.setModel(new DefaultTreeModel(rootNodeStructures, false));
      treeIsodoses.setModel(new DefaultTreeModel(rootNodeIsodoses, false));

      comboRtStructureSet.removeAllItems();
      comboRtPlan.removeAllItems();
      initPathSelection = false;
      return;
    }

    comboRtStructureSet.removeItemListener(structureChangeListener);
    comboRtPlan.removeItemListener(planChangeListener);

    StructureSet oldStructure = (StructureSet) comboRtStructureSet.getSelectedItem();
    Plan oldPlan = (Plan) comboRtPlan.getSelectedItem();

    comboRtStructureSet.removeAllItems();
    comboRtPlan.removeAllItems();

    Set<StructureSet> rtStructElements = rt.getStructures();
    Set<Plan> rtPlanElements = rt.getPlans();

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
      RtSpecialElement selectedPlan = rt.getFirstPlan();
      if (selectedPlan != null) {
        comboRtPlan.setSelectedItem(selectedPlan);
        updateTree = true;
      }
    } else {
      comboRtPlan.setSelectedItem(oldPlan);
    }

    if (updateTree) {
      updateTree(
          (StructureSet) comboRtStructureSet.getSelectedItem(),
          (Plan) comboRtPlan.getSelectedItem());
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

    updateCurrentContainer();
  }

  public void updateTree(StructureSet selectedStructure, Plan selectedPlan) {
    initPathSelection = true;
    // Empty tree when no RtSet
    if (rtSet == null) {
      nodeStructures.removeAllChildren();
      nodeIsodoses.removeAllChildren();
      treeStructures.setModel(new DefaultTreeModel(rootNodeStructures, false));
      treeIsodoses.setModel(new DefaultTreeModel(rootNodeIsodoses, false));
      initPathSelection = false;
      return;
    }

    try {
      // Prepare root tree model
      treeStructures.setModel(new DefaultTreeModel(rootNodeStructures, false));
      treeIsodoses.setModel(new DefaultTreeModel(rootNodeIsodoses, false));

      // Prepare parent node for structures
      if (selectedStructure != null) {
        nodeStructures.removeAllChildren();
        Map<String, List<StructRegion>> map =
            SegRegion.groupRegions(selectedStructure.getSegAttributes().values());
        for (List<StructRegion> list : StructRegion.sort(map.values())) {
          if (list.size() == 1) {
            StructRegion region = list.getFirst();
            DefaultMutableTreeNode node = buildStructRegionNode(region);
            nodeStructures.add(node);
            treeStructures.setPathSelection(new TreePath(node.getPath()), region.isSelected());
          } else {
            GroupTreeNode node = new GroupTreeNode(list.getFirst().getPrefix(), true);
            nodeStructures.add(node);
            for (StructRegion structRegion : list) {
              DefaultMutableTreeNode childNode = buildStructRegionNode(structRegion);
              node.add(childNode);
              treeStructures.setPathSelection(
                  new TreePath(childNode.getPath()), structRegion.isSelected());
            }
            treeStructures.addCheckingPath(new TreePath(node.getPath()));
          }
        }
        treeStructures.setPathSelection(new TreePath(nodeStructures.getPath()), true);
      }

      // Prepare parent node for isodoses
      if (selectedPlan != null) {
        nodeIsodoses.removeAllChildren();

        lblRtPlanName.setText(selectedPlan.getName());
        txtRtPlanDoseValue.setText(String.format("%.0f", selectedPlan.getRxDose())); // NON-NLS

        Dose planDose = selectedPlan.getFirstDose();
        if (planDose != null) {
          Map<Integer, IsoDoseRegion> isodoses = planDose.getIsoDoseSet();
          if (isodoses != null) {
            for (IsoDoseRegion isoDoseLayer : isodoses.values()) {
              DefaultMutableTreeNode node =
                  new StructToolTipTreeNode(isoDoseLayer, false) {
                    @Override
                    public String getToolTipText() {
                      IsoDoseRegion layer = (IsoDoseRegion) getUserObject();

                      StringBuilder buf = new StringBuilder();
                      buf.append(GuiUtils.HTML_START);
                      buf.append("<b>");
                      buf.append(layer.getLabel());
                      buf.append("</b>");
                      buf.append(GuiUtils.HTML_BR);
                      buf.append(Messages.getString("level"));
                      buf.append(StringUtil.COLON_AND_SPACE);
                      buf.append(String.format("%d%%", layer.getLevel())); // NON-NLS
                      buf.append(GuiUtils.HTML_BR);
                      buf.append(Messages.getString("thickness"));
                      buf.append(StringUtil.COLON_AND_SPACE);
                      buf.append(DecFormatter.twoDecimal(layer.getThickness()));
                      buf.append(GuiUtils.HTML_BR);

                      buf.append(GuiUtils.HTML_END);

                      return buf.toString();
                    }
                  };
              this.nodeIsodoses.add(node);
              treeIsodoses.addCheckingPath(new TreePath(node.getPath()));
            }
          }
          treeIsodoses.removeCheckingPath(new TreePath(nodeIsodoses.getPath()));
        }
      }

      // Expand
      TreeBuilder.expandTree(treeStructures, rootNodeStructures, 2);
      TreeBuilder.expandTree(treeIsodoses, rootNodeIsodoses, 2);
    } finally {
      initPathSelection = false;
    }
  }

  private DefaultMutableTreeNode buildStructRegionNode(StructRegion structRegion) {
    return new StructToolTipTreeNode(structRegion, false) {
      @Override
      public String getToolTipText() {
        StructRegion region = (StructRegion) getUserObject();

        double volume = region.getVolume();
        Dvh structureDvh = region.getDvh();

        StringBuilder buf = new StringBuilder();
        buf.append(GuiUtils.HTML_START);
        buf.append("<b>");
        buf.append(region.getLabel());
        buf.append("</b>");
        buf.append(GuiUtils.HTML_BR);
        if (StringUtil.hasText(region.getRoiObservationLabel())) {
          buf.append(region.getRoiObservationLabel());
          buf.append(GuiUtils.HTML_BR);
        }
        buf.append(Messages.getString("thickness"));
        buf.append(StringUtil.COLON_AND_SPACE);
        buf.append(
            String.format("%s mm", DecFormatter.twoDecimal(region.getThickness()))); // NON-NLS
        buf.append(GuiUtils.HTML_BR);
        buf.append(Messages.getString("volume"));
        buf.append(StringUtil.COLON_AND_SPACE);
        buf.append(String.format("%s cm³", DecFormatter.fourDecimal(volume))); // NON-NLS
        buf.append(GuiUtils.HTML_BR);

        if (structureDvh != null) {
          buf.append(structureDvh.getDvhSource().toString());
          buf.append(" ");
          buf.append(Messages.getString("min.dose"));
          buf.append(StringUtil.COLON_AND_SPACE);
          buf.append(
              DecFormatter.percentTwoDecimal(
                  Dose.calculateRelativeDose(
                          structureDvh.getDvhMinimumDoseCGy(), structureDvh.getPlan().getRxDose())
                      / 100.0));
          buf.append(GuiUtils.HTML_BR);
          buf.append(structureDvh.getDvhSource().toString());
          buf.append(" ");
          buf.append(Messages.getString("max.dose"));
          buf.append(StringUtil.COLON_AND_SPACE);
          buf.append(
              DecFormatter.percentTwoDecimal(
                  Dose.calculateRelativeDose(
                          structureDvh.getDvhMaximumDoseCGy(), structureDvh.getPlan().getRxDose())
                      / 100.0));
          buf.append(GuiUtils.HTML_BR);
          buf.append(structureDvh.getDvhSource().toString());
          buf.append(" ");
          buf.append(Messages.getString("mean.dose"));
          buf.append(StringUtil.COLON_AND_SPACE);
          buf.append(
              DecFormatter.percentTwoDecimal(
                  Dose.calculateRelativeDose(
                          structureDvh.getDvhMeanDoseCGy(), structureDvh.getPlan().getRxDose())
                      / 100.0));
          buf.append(GuiUtils.HTML_BR);
        }
        buf.append(GuiUtils.HTML_END);

        return buf.toString();
      }

      @Override
      public String toString() {
        StructRegion layer = (StructRegion) getUserObject();
        String resultLabel = layer.getLabel();

        String type = layer.getRtRoiInterpretedType();
        if (StringUtil.hasText(type)) {
          resultLabel += " [" + type + "]";
        }

        return getColorBullet(layer.getColor(), resultLabel);
      }
    };
  }

  public void initTreeValues(ViewCanvas<?> viewCanvas) {

    List<RtSpecialElement> list;
    if (viewCanvas != null) {
      MediaSeries<?> dcmSeries = viewCanvas.getSeries();
      boolean compatible = RtDisplayTool.isCtLinkedRT(dcmSeries);
      if (compatible && dcmSeries instanceof DicomSeries series) {
        String seriesUID = TagD.getTagValue(dcmSeries, Tag.SeriesInstanceUID, String.class);
        if (StringUtil.hasText(seriesUID)) {
          list = getRTSpecialElements(seriesUID);
          RtSet set = RtSet_Cache.get(seriesUID);
          boolean reload = set == null || (!list.isEmpty() && !set.getRtElements().equals(list));
          if (reload) {
            set = new RtSet(series, list);
            RtSet_Cache.put(seriesUID, set);
          }
          boolean empty = set.getStructures().isEmpty();
          btnLoad.setEnabled(empty || reload);
          this.rtSet = set;
          updateCanvas(viewCanvas);
          return;
        }
      }
    }
    this.rtSet = null;
    updateCanvas(viewCanvas);
  }

  private List<RtSpecialElement> getRTSpecialElements(String seriesUID) {
    if (StringUtil.hasText(seriesUID)) {
      Set<String> list = HiddenSeriesManager.getInstance().reference2Series.get(seriesUID);
      if (list != null && !list.isEmpty()) {
        return HiddenSeriesManager.getHiddenElementsFromSeries(
            RtSpecialElement.class, list.toArray(new String[0]));
      }
    }
    return Collections.emptyList();
  }

  @Override
  public Component getToolComponent() {
    return getToolComponentFromJScrollPane(rootPane);
  }

  @Override
  public void changingViewContentEvent(SeriesViewerEvent event) {
    SeriesViewerEvent.EVENT e = event.getEventType();
    if (SeriesViewerEvent.EVENT.SELECT.equals(e)
        && event.getSeriesViewer() instanceof ImageViewerPlugin) {
      initTreeValues(((ImageViewerPlugin<?>) event.getSeriesViewer()).getSelectedImagePane());
    }
  }

  @Override
  protected void changeToolWindowAnchor(CLocation clocation) {
    // TODO Auto-generated method stub
  }

  public static boolean isCtLinkedRT(MediaSeries<?> dcmSeries) {
    if (dcmSeries != null) {
      String seriesUID = TagD.getTagValue(dcmSeries, Tag.SeriesInstanceUID, String.class);
      if (StringUtil.hasText(seriesUID)) {
        Set<String> list = HiddenSeriesManager.getInstance().reference2Series.get(seriesUID);
        if (list != null && !list.isEmpty()) {
          return HiddenSeriesManager.hasHiddenElementsFromSeries(
              RtSpecialElement.class, list.toArray(new String[0]));
        }
      }
    }
    return false;
  }
}
