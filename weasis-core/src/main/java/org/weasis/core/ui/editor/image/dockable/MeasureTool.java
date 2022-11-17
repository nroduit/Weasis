/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.editor.image.dockable;

import bibliothek.gui.dock.common.CLocation;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.Window;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JColorChooser;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.JToggleButton;
import javax.swing.JViewport;
import javax.swing.SwingUtilities;
import javax.swing.table.TableModel;
import org.weasis.core.api.gui.Insertable;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.gui.util.ComboItemListener;
import org.weasis.core.api.gui.util.Feature;
import org.weasis.core.api.gui.util.Feature.ComboItemListenerValue;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.gui.util.JToggleButtonGroup;
import org.weasis.core.api.image.util.MeasurableLayer;
import org.weasis.core.api.image.util.Unit;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.api.util.FontItem;
import org.weasis.core.api.util.ResourceUtil;
import org.weasis.core.api.util.ResourceUtil.ActionIcon;
import org.weasis.core.ui.Messages;
import org.weasis.core.ui.docking.PluginTool;
import org.weasis.core.ui.docking.UIManager;
import org.weasis.core.ui.editor.image.ImageViewerEventManager;
import org.weasis.core.ui.editor.image.ImageViewerPlugin;
import org.weasis.core.ui.editor.image.MeasureToolBar;
import org.weasis.core.ui.editor.image.MouseActions;
import org.weasis.core.ui.editor.image.ViewCanvas;
import org.weasis.core.ui.editor.image.ViewerPlugin;
import org.weasis.core.ui.editor.image.ViewerToolBar;
import org.weasis.core.ui.model.graphic.DragGraphic;
import org.weasis.core.ui.model.graphic.Graphic;
import org.weasis.core.ui.model.graphic.GraphicSelectionListener;
import org.weasis.core.ui.model.graphic.imp.AnnotationGraphic;
import org.weasis.core.ui.model.layer.LayerType;
import org.weasis.core.ui.model.utils.ImageStatistics;
import org.weasis.core.ui.model.utils.bean.MeasureItem;
import org.weasis.core.ui.model.utils.bean.Measurement;
import org.weasis.core.ui.pref.PreferenceDialog;
import org.weasis.core.ui.pref.ViewSetting;
import org.weasis.core.ui.util.ColorLayerUI;
import org.weasis.core.ui.util.SimpleTableModel;
import org.weasis.core.ui.util.TableColumnAdjuster;
import org.weasis.core.ui.util.TableNumberRenderer;
import org.weasis.core.util.StringUtil;

public class MeasureTool extends PluginTool implements GraphicSelectionListener {

  public static final String BUTTON_NAME = ActionW.DRAW + " & " + ActionW.MEASURE;
  public static final String LABEL_PREF_NAME = Messages.getString("MeasureTool.lab_img");
  public static final ViewSetting viewSetting = new ViewSetting();

  protected final ImageViewerEventManager<? extends ImageElement> eventManager;
  private final JScrollPane rootPane;
  private JPanel tableContainer;
  private JTable jtable;

  private List<DragGraphic> selectedGraphic;

  public MeasureTool(ImageViewerEventManager<? extends ImageElement> eventManager) {
    super(BUTTON_NAME, BUTTON_NAME, Insertable.Type.TOOL, 30);
    this.eventManager = eventManager;
    this.rootPane = new JScrollPane();
    dockable.setTitleIcon(ResourceUtil.getIcon(ActionIcon.MEASURE));
    setDockableWidth(220);
    jbInit();
  }

  private void jbInit() {
    rootPane.setBorder(BorderFactory.createEmptyBorder()); // remove default line
    setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
    add(getIconsPanel());
    add(getSelectedMeasurePanel());
  }

  public final JPanel getIconsPanel() {
    final JPanel transform = new JPanel();
    transform.setLayout(new BoxLayout(transform, BoxLayout.Y_AXIS));
    transform.setBorder(GuiUtils.getEmptyBorder(5, 5, 5, 5));

    MeasureTool.buildIconPanel(transform, eventManager, ActionW.MEASURE, ActionW.DRAW_MEASURE, 5);
    MeasureTool.buildIconPanel(transform, eventManager, ActionW.DRAW, ActionW.DRAW_GRAPHICS, 5);
    transform.add(Box.createVerticalStrut(5));

    JLabel label = new JLabel(Messages.getString("MeasureToolBar.line") + StringUtil.COLON);
    JButton button = new JButton(ResourceUtil.getIcon(ActionIcon.PIPETTE));
    button.setToolTipText(Messages.getString("MeasureTool.pick"));
    button.addActionListener(
        e -> {
          Color newColor =
              JColorChooser.showDialog(
                  SwingUtilities.getWindowAncestor(MeasureTool.this),
                  Messages.getString("MeasureTool.pick_color"),
                  viewSetting.getLineColor());
          if (newColor != null) {
            viewSetting.setLineColor(newColor);
            updateMeasureProperties(viewSetting);
          }
        });

    JSpinner spinner = new JSpinner();
    GuiUtils.setNumberModel(spinner, viewSetting.getLineWidth(), 1, 8, 1);
    spinner.addChangeListener(
        e -> {
          Object val = ((JSpinner) e.getSource()).getValue();

          if (val instanceof Integer intVal) {
            viewSetting.setLineWidth(intVal);
            updateMeasureProperties(viewSetting);
          }
        });
    transform.add(GuiUtils.getFlowLayoutPanel(label, button, spinner));

    eventManager
        .getAction(ActionW.DRAW_ONLY_ONCE)
        .ifPresent(
            b -> {
              JCheckBox checkDraw = b.createCheckBox(ActionW.DRAW_ONLY_ONCE.getTitle());
              checkDraw.setSelected(viewSetting.isDrawOnlyOnce());
              transform.add(GuiUtils.getFlowLayoutPanel(checkDraw));
            });

    JCheckBox checkboxBasicImageStatistics =
        new JCheckBox(Messages.getString("MeasureTool.pix_stats"), viewSetting.isBasicStatistics());
    checkboxBasicImageStatistics.addActionListener(
        e -> {
          JCheckBox box = (JCheckBox) e.getSource();
          boolean sel = box.isSelected();
          viewSetting.setBasicStatistics(sel);
          // Force also advanced statistics
          viewSetting.setMoreStatistics(sel);
          for (Measurement m : ImageStatistics.ALL_MEASUREMENTS) {
            m.setComputed(sel);
          }
          synchronized (UIManager.VIEWER_PLUGINS) {
            for (int i = UIManager.VIEWER_PLUGINS.size() - 1; i >= 0; i--) {
              ViewerPlugin<?> p = UIManager.VIEWER_PLUGINS.get(i);
              if (p instanceof ImageViewerPlugin) {
                for (ViewCanvas<?> view : ((ImageViewerPlugin<?>) p).getImagePanels()) {
                  if (view != null) {
                    view.getGraphicManager().updateLabels(true, view);
                  }
                }
              }
            }
          }
        });
    transform.add(GuiUtils.getFlowLayoutPanel(checkboxBasicImageStatistics));

    eventManager
        .getAction(ActionW.SPATIAL_UNIT)
        .ifPresent(
            b -> {
              final JLabel lutLabel =
                  new JLabel(Messages.getString("MeasureTool.unit") + StringUtil.COLON);
              JComboBox<?> unitComboBox = b.createCombo(120);
              unitComboBox.setSelectedItem(Unit.PIXEL);
              transform.add(GuiUtils.getFlowLayoutPanel(lutLabel, unitComboBox));
            });

    final JButton btnGeneralOptions = new JButton(Messages.getString("MeasureTool.more_options"));
    btnGeneralOptions.addActionListener(
        e -> {
          Window win = SwingUtilities.getWindowAncestor(MeasureTool.this);
          ColorLayerUI layer = ColorLayerUI.createTransparentLayerUI(win.getParent());
          PreferenceDialog dialog = new PreferenceDialog(win);
          dialog.showPage(LABEL_PREF_NAME);
          ColorLayerUI.showCenterScreen(dialog, layer);
        });
    transform.add(GuiUtils.getFlowLayoutPanel(btnGeneralOptions));
    return transform;
  }

  private static void updateMeasureProperties(final ViewSetting setting) {
    if (setting != null) {
      MeasureToolBar.measureGraphicList.forEach(
          g -> MeasureToolBar.applyDefaultSetting(setting, g));
      MeasureToolBar.drawGraphicList.forEach(g -> MeasureToolBar.applyDefaultSetting(setting, g));
    }
  }

  public JPanel getSelectedMeasurePanel() {
    jtable = createMultipleRenderingTable(new SimpleTableModel(new String[] {}, new Object[][] {}));
    jtable.setFont(FontItem.MINI.getFont());

    jtable.getTableHeader().setReorderingAllowed(false);

    tableContainer = new JPanel(new BorderLayout());
    tableContainer.setPreferredSize(GuiUtils.getDimension(50, 50));
    tableContainer.setBorder(
        BorderFactory.createCompoundBorder(
            GuiUtils.getEmptyBorder(10, 3, 0, 3),
            GuiUtils.getTitledBorder(Messages.getString("MeasureTool.sel"))));
    return tableContainer;
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
  protected void changeToolWindowAnchor(CLocation clocation) {
    // Do nothing
  }

  public static JTable createMultipleRenderingTable(TableModel model) {
    JTable table = new JTable(model);
    table.getTableHeader().setReorderingAllowed(false);
    table.setShowHorizontalLines(true);
    table.setShowVerticalLines(true);
    table.getColumnModel().setColumnMargin(GuiUtils.getScaleLength(3));
    return table;
  }

  public void setSelectedGraphic(Graphic graph, MeasurableLayer layer) {
    List<MeasureItem> measList = null;

    if (graph != null && layer != null && graph.getLayerType() == LayerType.MEASURE) {
      Unit unit =
          eventManager
              .getAction(ActionW.SPATIAL_UNIT)
              .map(c -> (Unit) c.getSelectedItem())
              .orElse(null);
      measList = graph.computeMeasurements(layer, true, unit);
    }
    updateMeasuredItems(measList);
  }

  @Override
  public void updateMeasuredItems(List<MeasureItem> measList) {
    tableContainer.removeAll();

    // just clear tableContainer if measList is null
    if (measList != null) {
      String[] headers = {
        Messages.getString("MeasureTool.param"), Messages.getString("MeasureTool.val")
      };
      jtable.setModel(new SimpleTableModel(headers, getLabels(measList)));
      jtable.getColumnModel().getColumn(1).setCellRenderer(new TableNumberRenderer());
      int height =
          (jtable.getRowHeight() + jtable.getRowMargin()) * jtable.getRowCount()
              + jtable.getTableHeader().getHeight()
              + 5;
      tableContainer.setPreferredSize(
          new Dimension(jtable.getColumnModel().getTotalColumnWidth(), height));
      tableContainer.add(jtable.getTableHeader(), BorderLayout.PAGE_START);
      tableContainer.add(jtable, BorderLayout.CENTER);
      TableColumnAdjuster.pack(jtable);
    } else {
      tableContainer.setPreferredSize(GuiUtils.getDimension(50, 50));
    }
    tableContainer.revalidate();
    tableContainer.repaint();
  }

  public static Object[][] getLabels(List<MeasureItem> measList) {
    if (measList != null) {
      Object[][] labels = new Object[measList.size()][];
      for (int i = 0; i < labels.length; i++) {
        MeasureItem m = measList.get(i);
        Object[] row = new Object[2];
        StringBuilder buffer = new StringBuilder(m.getMeasurement().getName());
        if (m.getLabelExtension() != null) {
          buffer.append(m.getLabelExtension());
        }
        if (m.getUnit() != null) {
          buffer.append(" [");
          buffer.append(m.getUnit());
          buffer.append("]");
        }
        row[0] = buffer.toString();
        row[1] = m.getValue();
        labels[i] = row;
      }
      return labels;
    }
    return null;
  }

  public static int getNumberOfMeasures(boolean[] select) {
    int k = 0;
    for (boolean b : select) {
      if (b) {
        k++;
      }
    }
    return k;
  }

  @Override
  public void handle(List<Graphic> selectedGraphicList, MeasurableLayer layer) {
    Graphic g = null;
    List<DragGraphic> list = null;

    if (selectedGraphicList != null) {
      if (selectedGraphicList.size() == 1) {
        g = selectedGraphicList.get(0);
      }

      list = new ArrayList<>();

      for (Graphic graphic : selectedGraphicList) {
        if (graphic instanceof DragGraphic dragGraphic) {
          list.add(dragGraphic);
        }
      }
    }

    boolean computeAllMeasures = true;
    if (selectedGraphic != null) {
      if (g != null && selectedGraphic.size() == 1) {
        // Warning only comparing if it is the same instance, cannot compare handle points.
        // Update of the list of measures is performed in the drag sequence (move, complete). Here
        // only the change of selection will compute the measurements
        if (g == selectedGraphic.get(0) && !(g instanceof AnnotationGraphic)) {
          computeAllMeasures = false;
        }
      }
      selectedGraphic.clear();
    }

    this.selectedGraphic = list;
    if (computeAllMeasures) {
      // if g equals null means graphic is not single or no graphic is selected
      setSelectedGraphic(g, layer);
    }
  }

  public static void buildIconPanel(
      JPanel rootPanel,
      ImageViewerEventManager<?> eventManager,
      Feature<?> action,
      ComboItemListenerValue<Graphic> graphicAction,
      int lineLength) {
    Optional<ComboItemListener<Graphic>> actionState = eventManager.getAction(graphicAction);
    if (actionState.isEmpty()) {
      return;
    }

    final JPanel pIcons = new JPanel();
    pIcons.setBorder(
        BorderFactory.createCompoundBorder(
            GuiUtils.getEmptyBorder(10, 5, 0, 5),
            GuiUtils.getTitledBorder(graphicAction.getTitle())));

    JToggleButtonGroup<?> measures = actionState.get().createButtonGroup();
    JToggleButton[] items = measures.getJToggleButtonList();

    pIcons.setLayout(new GridLayout(0, lineLength));
    for (JToggleButton item : items) {
      item.addActionListener(
          e -> {
            ImageViewerPlugin<? extends ImageElement> view =
                eventManager.getSelectedView2dContainer();
            if (view != null) {
              final ViewerToolBar<?> toolBar = view.getViewerToolBar();
              if (toolBar != null) {
                String cmd = action.cmd();
                if (!toolBar.isCommandActive(cmd)) {
                  MouseActions mouseActions = eventManager.getMouseActions();
                  mouseActions.setAction(MouseActions.T_LEFT, cmd);
                  view.setMouseActions(mouseActions);
                  toolBar.changeButtonState(MouseActions.T_LEFT, cmd);
                }
              }
            }
          });
      pIcons.add(item);
    }
    rootPanel.add(GuiUtils.getFlowLayoutPanel(FlowLayout.LEADING, 0, 0, pIcons));
  }
}
