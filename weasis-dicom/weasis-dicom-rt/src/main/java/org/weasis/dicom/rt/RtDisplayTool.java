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
import it.cnr.imaa.essi.lablib.gui.checkboxtree.CheckboxTree;
import it.cnr.imaa.essi.lablib.gui.checkboxtree.TreeCheckingEvent;
import it.cnr.imaa.essi.lablib.gui.checkboxtree.TreeCheckingModel;
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
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.DefaultBoundedRangeModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.JTree;
import javax.swing.JViewport;
import javax.swing.SwingWorker;
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
import org.weasis.core.api.gui.Insertable;
import org.weasis.core.api.gui.task.CircularProgressBar;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.gui.util.JSliderW;
import org.weasis.core.api.gui.util.SliderChangeListener;
import org.weasis.core.api.gui.util.WinUtil;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.api.media.data.MediaElement;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.media.data.MediaSeriesGroup;
import org.weasis.core.api.media.data.SoftHashMap;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.api.util.FontItem;
import org.weasis.core.api.util.ResourceUtil;
import org.weasis.core.api.util.ResourceUtil.OtherIcon;
import org.weasis.core.ui.docking.PluginTool;
import org.weasis.core.ui.editor.SeriesViewerEvent;
import org.weasis.core.ui.editor.SeriesViewerListener;
import org.weasis.core.ui.editor.image.ImageViewerPlugin;
import org.weasis.core.ui.editor.image.ViewCanvas;
import org.weasis.core.ui.model.GraphicModel;
import org.weasis.core.ui.model.graphic.Graphic;
import org.weasis.core.ui.model.imp.XmlGraphicModel;
import org.weasis.core.ui.model.layer.LayerType;
import org.weasis.core.ui.util.CheckBoxTreeBuilder;
import org.weasis.core.util.StringUtil;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.codec.DicomSeries;
import org.weasis.dicom.codec.TagD;
import org.weasis.dicom.codec.geometry.GeometryOfSlice;
import org.weasis.dicom.explorer.DicomModel;
import org.weasis.dicom.viewer2d.EventManager;

/**
 * @author Tomas Skripcak
 * @author Nicolas Roduit
 */
public class RtDisplayTool extends PluginTool implements SeriesViewerListener {

  public static final String BUTTON_NAME = Messages.getString("rt.tool");

  private static final SoftHashMap<String, RtSet> RtSet_Cache = new SoftHashMap<>();

  private final JTabbedPane tabbedPane = new JTabbedPane();
  private final JScrollPane rootPane;
  private final JButton btnLoad = new JButton(Messages.getString("load.rt"));
  private final JCheckBox cbDvhRecalculate = new JCheckBox(Messages.getString("dvh.recalculate"));

  private final CheckboxTree treeStructures;
  private final CheckboxTree treeIsodoses;
  private boolean initPathSelection;
  private final DefaultMutableTreeNode rootNodeStructures =
      new DefaultMutableTreeNode("rootNode", true); // NON-NLS
  private final DefaultMutableTreeNode rootNodeIsodoses =
      new DefaultMutableTreeNode("rootNode", true); // NON-NLS
  private final JLabel lblRtStructureSet =
      new JLabel(Messages.getString("structure.set") + StringUtil.COLON);
  private final JComboBox<RtSpecialElement> comboRtStructureSet = new JComboBox<>();
  private final JLabel lblRtPlan = new JLabel(Messages.getString("plan") + StringUtil.COLON);
  private final JComboBox<RtSpecialElement> comboRtPlan = new JComboBox<>();
  private final JLabel lblRtPlanName = new JLabel();
  private final JLabel lblRtPlanDose = new JLabel(Messages.getString("dose") + StringUtil.COLON);
  private final JTextField txtRtPlanDoseValue = new JTextField();
  private final JLabel lblRtPlanDoseUnit = new JLabel("cGy"); // NON-NLS
  private final DefaultMutableTreeNode nodeStructures;
  private final DefaultMutableTreeNode nodeIsodoses;
  private final CircularProgressBar progressBar = new CircularProgressBar();
  private RtSet rtSet;
  private final transient ItemListener structureChangeListener =
      e -> {
        if (e.getStateChange() == ItemEvent.SELECTED) {
          updateTree((RtSpecialElement) e.getItem(), null);
        }
      };
  private final transient ItemListener planChangeListener =
      e -> {
        if (e.getStateChange() == ItemEvent.SELECTED) {
          updateTree(null, (RtSpecialElement) e.getItem());
        }
      };
  private final JPanel panelFoot = new JPanel();
  private final JSliderW slider;

  public RtDisplayTool() {
    super(BUTTON_NAME, BUTTON_NAME, Insertable.Type.TOOL, 30);
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
    this.slider = createTransparencySlider(5, true);

    this.treeStructures =
        new CheckboxTree() {

          @Override
          public String getToolTipText(MouseEvent evt) {
            if (getRowForLocation(evt.getX(), evt.getY()) == -1) {
              return null;
            }
            TreePath curPath = getPathForLocation(evt.getX(), evt.getY());
            if (curPath != null) {
              Object object = curPath.getLastPathComponent();
              if (object instanceof StructToolTipTreeNode treeNode) {
                return treeNode.getToolTipText();
              }
            }
            return null;
          }
        };
    treeStructures.setToolTipText(StringUtil.EMPTY_STRING);
    treeStructures.setCellRenderer(CheckBoxTreeBuilder.buildNoIconCheckboxTreeCellRenderer());

    this.treeIsodoses =
        new CheckboxTree() {

          @Override
          public String getToolTipText(MouseEvent evt) {
            if (getRowForLocation(evt.getX(), evt.getY()) == -1) {
              return null;
            }
            TreePath curPath = getPathForLocation(evt.getX(), evt.getY());
            if (curPath != null) {
              Object object = curPath.getLastPathComponent();
              if (object instanceof IsoToolTipTreeNode treeNode) {
                return treeNode.getToolTipText();
              }
            }
            return null;
          }
        };
    treeIsodoses.setToolTipText(StringUtil.EMPTY_STRING);
    treeIsodoses.setCellRenderer(CheckBoxTreeBuilder.buildNoIconCheckboxTreeCellRenderer());
    this.nodeStructures = new DefaultMutableTreeNode(Messages.getString("structures"), true);
    this.nodeIsodoses = new DefaultMutableTreeNode(Messages.getString("isodoses"), true);
    this.initData();
  }

  private void loadData() {
    final RtSet rt = this.rtSet;
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

  public void initData() {

    add(tabbedPane, BorderLayout.CENTER);

    add(panelFoot, BorderLayout.SOUTH);

    panelFoot.add(slider);

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

    initStructureTree();
    initIsodosesTree();

    tabbedPane.addChangeListener(e -> initSlider());
  }

  private void showDvhChart() {
    RtSet rt = rtSet;
    if (rt != null) {
      List<StructureLayer> structs = getStructureSelection();
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
        for (StructureLayer structureLayer : structs) {
          Structure structure = structureLayer.getStructure();
          Dvh structureDvh = structure.getDvh();
          structureDvh.appendChart(structure, dvhChart);
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
    treeStructures.setCellRenderer(CheckBoxTreeBuilder.buildNoIconCheckboxTreeCellRenderer());
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
    treeIsodoses.setCellRenderer(CheckBoxTreeBuilder.buildNoIconCheckboxTreeCellRenderer());
    treeIsodoses.addTreeCheckingListener(this::treeValueChanged);

    expandTree(treeIsodoses, rootNodeIsodoses);
    tabbedPane.add(new JScrollPane(treeIsodoses), nodeIsodoses.toString());
  }

  public JSliderW createTransparencySlider(int labelDivision, boolean displayValueInTitle) {
    String title = Messages.getString("graphic.opacity");
    DefaultBoundedRangeModel model = new DefaultBoundedRangeModel(50, 0, 0, 100);
    TitledBorder titledBorder =
        new TitledBorder(
            BorderFactory.createEmptyBorder(),
            title + StringUtil.COLON_AND_SPACE + model.getValue(),
            TitledBorder.LEADING,
            TitledBorder.DEFAULT_POSITION,
            FontItem.MEDIUM.getFont(),
            null);
    JSliderW s = new JSliderW(model);
    s.setLabelDivision(labelDivision);
    s.setDisplayValueInTitle(displayValueInTitle);
    s.setPaintTicks(true);
    s.setShowLabels(labelDivision > 0);
    s.setBorder(titledBorder);
    if (s.isShowLabels()) {
      s.setPaintLabels(true);
      SliderChangeListener.setSliderLabelValues(
          s, model.getMinimum(), model.getMaximum(), 0.0, 100.0);
    }
    s.addChangeListener(
        l -> {
          String result = title + StringUtil.COLON_AND_SPACE + model.getValue();
          SliderChangeListener.updateSliderProperties(slider, result);
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

      ImageViewerPlugin<DicomImageElement> container =
          EventManager.getInstance().getSelectedView2dContainer();
      List<ViewCanvas<DicomImageElement>> views = null;
      if (container != null) {
        views = container.getImagePanels();
      }
      if (views != null) {
        RtSet rt = rtSet;
        if (rt != null
            && ((selObject == nodeStructures || parent == nodeStructures)
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
        if (node.getUserObject() instanceof StructureLayer layer) {
          list.add(layer);
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
        if (node.getUserObject() instanceof IsoDoseLayer layer) {
          list.add(layer);
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

  private static void showGraphic(
      RtSet rt,
      List<StructureLayer> listStructure,
      List<IsoDoseLayer> listIsoDose,
      ViewCanvas<?> v) {
    if (rt != null) {
      ImageElement dicom = v.getImage();
      if (dicom instanceof DicomImageElement dicomImageElement) {
        GeometryOfSlice geometry = dicomImageElement.getDispSliceGeometry();

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
      updateTree(
          (RtSpecialElement) comboRtStructureSet.getSelectedItem(),
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
        this.txtRtPlanDoseValue.setText(String.format("%.0f", plan.getRxDose())); // NON-NLS

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
            String frameOfReferenceUID =
                TagD.getTagValue(dcmSeries, Tag.FrameOfReferenceUID, String.class);
            if (frameOfReferenceUID == null) {
              frameOfReferenceUID = "";
            }
            List<MediaElement> list =
                getRelatedSpecialElements(dicomModel, patient, frameOfReferenceUID);
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
    if (SeriesViewerEvent.EVENT.SELECT.equals(e)
        && event.getSeriesViewer() instanceof ImageViewerPlugin) {
      initTreeValues(((ImageViewerPlugin<?>) event.getSeriesViewer()).getSelectedImagePane());
    }
  }

  @Override
  protected void changeToolWindowAnchor(CLocation clocation) {
    // TODO Auto-generated method stub
  }

  private static void expandTree(JTree tree, DefaultMutableTreeNode start) {
    for (Enumeration children = start.children(); children.hasMoreElements(); ) {
      DefaultMutableTreeNode dtm = (DefaultMutableTreeNode) children.nextElement();
      if (!dtm.isLeaf()) {
        TreePath tp = new TreePath(dtm.getPath());
        tree.expandPath(tp);
        expandTree(tree, dtm);
      }
    }
  }

  private static List<MediaElement> getRelatedSpecialElements(
      DicomModel model, MediaSeriesGroup patient, String frameOfReferenceUID) {
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
            if ("RTDOSE".equals(modality) && s instanceof DicomSeries dicomSeries) {
              synchronized (s) {
                for (DicomImageElement media : dicomSeries.getMedias(null, null)) {
                  if ("RTDOSE".equals(TagD.getTagValue(media, Tag.Modality))) {
                    specialElementList.add(media);
                  }
                }
              }
            }
            if ("CT".equals(modality) && s instanceof DicomSeries dicomSeries) {
              synchronized (s) {
                for (DicomImageElement media : dicomSeries.getMedias(null, null)) {
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
    StringBuilder buf = new StringBuilder("<html><font color='rgb("); // NON-NLS
    buf.append(c.getRed());
    buf.append(",");
    buf.append(c.getGreen());
    buf.append(",");
    buf.append(c.getBlue());
    // Other square: u2B1B (unicode)
    buf.append(")'> █ </font>"); // NON-NLS
    buf.append(label);
    buf.append(GuiUtils.HTML_END);
    return buf.toString();
  }

  static class StructToolTipTreeNode extends DefaultMutableTreeNode {

    public static final String FORMAT = "%.3f %%"; // NON-NLS

    public StructToolTipTreeNode(StructureLayer userObject, boolean allowsChildren) {
      super(Objects.requireNonNull(userObject), allowsChildren);
    }

    public String getToolTipText() {
      StructureLayer layer = (StructureLayer) getUserObject();

      double volume = layer.getStructure().getVolume();
      // String source = layer.getStructure().getVolumeSource().toString();
      Dvh structureDvh = layer.getStructure().getDvh();

      StringBuilder buf = new StringBuilder();
      buf.append(GuiUtils.HTML_START);
      buf.append(Messages.getString("structure.information"));
      buf.append(StringUtil.COLON);
      buf.append(GuiUtils.HTML_BR);
      if (StringUtil.hasText(layer.getStructure().getRoiObservationLabel())) {
        buf.append(Messages.getString("observation.label"));
        buf.append(StringUtil.COLON_AND_SPACE);
        buf.append(layer.getStructure().getRoiObservationLabel());
        buf.append(GuiUtils.HTML_BR);
      }
      buf.append(Messages.getString("thickness"));
      buf.append(StringUtil.COLON_AND_SPACE);
      buf.append(String.format("%.2f", layer.getStructure().getThickness())); // NON-NLS
      buf.append(GuiUtils.HTML_BR);
      buf.append(Messages.getString("volume"));
      buf.append(StringUtil.COLON_AND_SPACE);
      buf.append(String.format("%.4f cm^3", volume)); // NON-NLS
      buf.append(GuiUtils.HTML_BR);

      if (structureDvh != null) {
        buf.append(structureDvh.getDvhSource().toString());
        buf.append(" ");
        buf.append(Messages.getString("min.dose"));
        buf.append(StringUtil.COLON_AND_SPACE);
        buf.append(
            String.format(
                FORMAT,
                RtSet.calculateRelativeDose(
                    structureDvh.getDvhMinimumDoseCGy(), structureDvh.getPlan().getRxDose())));
        buf.append(GuiUtils.HTML_BR);
        buf.append(structureDvh.getDvhSource().toString());
        buf.append(" ");
        buf.append(Messages.getString("max.dose"));
        buf.append(StringUtil.COLON_AND_SPACE);
        buf.append(
            String.format(
                FORMAT,
                RtSet.calculateRelativeDose(
                    structureDvh.getDvhMaximumDoseCGy(), structureDvh.getPlan().getRxDose())));
        buf.append(GuiUtils.HTML_BR);
        buf.append(structureDvh.getDvhSource().toString());
        buf.append(" ");
        buf.append(Messages.getString("mean.dose"));
        buf.append(StringUtil.COLON_AND_SPACE);
        buf.append(
            String.format(
                FORMAT,
                RtSet.calculateRelativeDose(
                    structureDvh.getDvhMeanDoseCGy(), structureDvh.getPlan().getRxDose())));
        buf.append(GuiUtils.HTML_BR);
      }
      buf.append(GuiUtils.HTML_END);

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
      buf.append(GuiUtils.HTML_START);
      buf.append(Messages.getString("isodose.information"));
      buf.append(StringUtil.COLON);
      buf.append(GuiUtils.HTML_BR);
      if (layer.getIsoDose() != null) {
        buf.append(Messages.getString("level"));
        buf.append(StringUtil.COLON_AND_SPACE);
        buf.append(String.format("%d %%", layer.getIsoDose().getLevel())); // NON-NLS
        buf.append(GuiUtils.HTML_BR);
        buf.append(Messages.getString("thickness"));
        buf.append(StringUtil.COLON_AND_SPACE);
        buf.append(String.format("%.2f", layer.getIsoDose().getThickness())); // NON-NLS
        buf.append(GuiUtils.HTML_BR);
      }
      buf.append(GuiUtils.HTML_END);

      return buf.toString();
    }

    @Override
    public String toString() {
      IsoDoseLayer layer = (IsoDoseLayer) getUserObject();
      return getColorBullet(layer.getIsoDose().getColor(), layer.toString());
    }
  }
}
