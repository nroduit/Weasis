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
import java.awt.Rectangle;
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
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.JToggleButton;
import javax.swing.ScrollPaneConstants;
import javax.swing.Scrollable;
import javax.swing.SwingUtilities;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;
import javax.swing.table.TableModel;
import net.miginfocom.swing.MigLayout;
import org.weasis.core.Messages;
import org.weasis.core.api.gui.Insertable;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.gui.util.CollapsiblePanel;
import org.weasis.core.api.gui.util.ComboItemListener;
import org.weasis.core.api.gui.util.DecFormatter;
import org.weasis.core.api.gui.util.Feature;
import org.weasis.core.api.gui.util.Feature.ComboItemListenerValue;
import org.weasis.core.api.gui.util.GuiExecutor;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.gui.util.JToggleButtonGroup;
import org.weasis.core.api.gui.util.WinUtil;
import org.weasis.core.api.gui.util.WrapFlowLayout;
import org.weasis.core.api.gui.util.WrapGridLayout;
import org.weasis.core.api.image.util.MeasurableLayer;
import org.weasis.core.api.image.util.Unit;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.api.util.FontItem;
import org.weasis.core.api.util.ResourceUtil;
import org.weasis.core.api.util.ResourceUtil.ActionIcon;
import org.weasis.core.ui.docking.PluginTool;
import org.weasis.core.ui.editor.SeriesViewerEvent;
import org.weasis.core.ui.editor.SeriesViewerEvent.EVENT;
import org.weasis.core.ui.editor.SeriesViewerListener;
import org.weasis.core.ui.editor.image.CalibrationView;
import org.weasis.core.ui.editor.image.ImageViewerEventManager;
import org.weasis.core.ui.editor.image.ImageViewerPlugin;
import org.weasis.core.ui.editor.image.IntensityProfile;
import org.weasis.core.ui.editor.image.MeasureToolBar;
import org.weasis.core.ui.editor.image.MouseActions;
import org.weasis.core.ui.editor.image.ViewCanvas;
import org.weasis.core.ui.editor.image.ViewerPlugin;
import org.weasis.core.ui.editor.image.ViewerToolBar;
import org.weasis.core.ui.model.graphic.DragGraphic;
import org.weasis.core.ui.model.graphic.Graphic;
import org.weasis.core.ui.model.graphic.GraphicRegistry;
import org.weasis.core.ui.model.graphic.GraphicSelectionListener;
import org.weasis.core.ui.model.graphic.GraphicToolContext;
import org.weasis.core.ui.model.graphic.GraphicToolDescriptor;
import org.weasis.core.ui.model.graphic.ToolCategory;
import org.weasis.core.ui.model.graphic.ToolPanel;
import org.weasis.core.ui.model.graphic.imp.AnnotationGraphic;
import org.weasis.core.ui.model.graphic.imp.line.LineGraphic;
import org.weasis.core.ui.model.graphic.profile.MeasurementProfile;
import org.weasis.core.ui.model.graphic.profile.MeasurementProfileRegistry;
import org.weasis.core.ui.model.graphic.profile.MeasurementProfiles;
import org.weasis.core.ui.model.layer.LayerType;
import org.weasis.core.ui.model.utils.GraphicOutline;
import org.weasis.core.ui.model.utils.ImageStatistics;
import org.weasis.core.ui.model.utils.MeasureFormat;
import org.weasis.core.ui.model.utils.bean.MeasureItem;
import org.weasis.core.ui.model.utils.bean.Measurement;
import org.weasis.core.ui.pref.PreferenceDialog;
import org.weasis.core.ui.pref.ViewSetting;
import org.weasis.core.ui.util.ColorLayerUI;
import org.weasis.core.ui.util.MeasureTables;
import org.weasis.core.ui.util.SimpleTableModel;
import org.weasis.core.util.StringUtil;

public class MeasureTool extends PluginTool
    implements GraphicSelectionListener, SeriesViewerListener, Scrollable {

  public static final String BUTTON_NAME = ActionW.DRAW + " & " + ActionW.MEASURE;
  public static final String LABEL_PREF_NAME = Messages.getString("MeasureTool.lab_img");
  public static final ViewSetting viewSetting = new ViewSetting();
  public static final String TOOL_WIDTH_KEY = "weasis.measure.tool.width"; // NON-NLS
  private static final int DEFAULT_TOOL_WIDTH = 220;
  private static final int SCROLL_UNIT = 16;
  private static final int MIN_PROFILE_COMBO_WIDTH = 90;

  protected final ImageViewerEventManager<? extends ImageElement> eventManager;
  private final JScrollPane rootPane;
  private JPanel tableContainer;
  private JTable jtable;
  private final JLabel acquisitionLabel = new JLabel();
  private final JPanel toolPanelHost = new JPanel(new BorderLayout());
  private final IntensityProfilePanel profilePanel = new IntensityProfilePanel();
  private final CollapsiblePanel profileSection =
      new CollapsiblePanel(
          Messages.getString("MeasureTool.profile"),
          profilePanel,
          true,
          "weasis.measure.panel.profile"); // NON-NLS
  private String currentToolKey;
  private JComponent currentToolPanel;
  private final JComboBox<Object> profileCombo = new JComboBox<>();
  private final JCheckBox checkboxBasicImageStatistics =
      new JCheckBox(Messages.getString("MeasureTool.pix_stats"));
  private MeasurementProfile appliedProfile;
  private boolean updatingProfileCombo;

  private List<DragGraphic> selectedGraphic;

  public MeasureTool(ImageViewerEventManager<? extends ImageElement> eventManager) {
    super(BUTTON_NAME, Insertable.Type.TOOL, 30);
    this.eventManager = eventManager;
    this.rootPane = new JScrollPane();
    dockable.setTitleIcon(ResourceUtil.getIcon(ActionIcon.MEASURE));
    setDockableWidth(
        GuiUtils.getUICore()
            .getLocalPersistence()
            .getIntProperty(TOOL_WIDTH_KEY, DEFAULT_TOOL_WIDTH));
    jbInit();
  }

  private void jbInit() {
    rootPane.setBorder(BorderFactory.createEmptyBorder()); // remove default line
    rootPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
    setLayout(sectionLayout());
    add(getIconsPanel());
    add(toolPanelHost, FOLLOW_WIDTH);
    profileSection.setVisible(false);
    add(profileSection, FOLLOW_WIDTH);
    add(getSelectedMeasurePanel());
    eventManager
        .getAction(ActionW.DRAW_MEASURE)
        .ifPresent(
            a ->
                a.getModel()
                    .addListDataListener(
                        new ListDataListener() {
                          @Override
                          public void intervalAdded(ListDataEvent e) {
                            // Nothing to show
                          }

                          @Override
                          public void intervalRemoved(ListDataEvent e) {
                            // Nothing to show
                          }

                          @Override
                          public void contentsChanged(ListDataEvent e) {
                            showActiveToolPanel();
                          }
                        }));
    showActiveToolPanel();
  }

  /**
   * Constraint of a section that takes the width the tool has and never asks for more: the width of
   * the tool comes from its palette and its table, not from a chart or a text.
   */
  static final String FOLLOW_WIDTH = "width 0:0:"; // NON-NLS

  /**
   * One column that every section fills, whatever its alignment, and that may get narrower than its
   * content; a hidden section takes no room.
   */
  static MigLayout sectionLayout() {
    return new MigLayout(
        "fillx, ins 0, gap 0, wrap 1, hidemode 3", "[0:pref,grow,fill]"); // NON-NLS
  }

  private JPanel buildProfileSelector() {
    fillProfileCombo();
    profileCombo.addActionListener(
        e -> {
          if (!updatingProfileCombo) {
            Object item = profileCombo.getSelectedItem();
            MeasurementProfileRegistry.getInstance()
                .select(item instanceof MeasurementProfile p ? p.id() : null);
          }
        });
    MeasurementProfileRegistry registry = MeasurementProfileRegistry.getInstance();
    registry.addListener(() -> GuiExecutor.execute(this::onProfilesChanged));
    eventManager.addSeriesViewerListener(this);
    applyActiveProfile();
    JLabel label = new JLabel(Messages.getString("MeasureTool.profile_label") + StringUtil.COLON);
    return profileRow(label, profileCombo);
  }

  /**
   * The combo takes what is left of the row beside its label and shortens a long profile name; when
   * not even its minimum width fits there (its arrow and the first characters of the name), it
   * drops under the label and takes the whole width.
   */
  static JPanel profileRow(JLabel label, JComboBox<?> combo) {
    int gap = GuiUtils.getScaleLength(5);
    combo.setMinimumSize(
        new Dimension(
            GuiUtils.getScaleLength(MIN_PROFILE_COMBO_WIDTH), combo.getPreferredSize().height));
    JPanel row = new JPanel(new WrapFlowLayout(gap, gap));
    row.setBorder(GuiUtils.getEmptyBorder(5, 5, 5, 5));
    row.add(label);
    row.add(combo, WrapFlowLayout.FILL);
    return row;
  }

  /** The tool is as wide as its dock: it scrolls vertically only. */
  @Override
  public boolean getScrollableTracksViewportWidth() {
    return true;
  }

  @Override
  public boolean getScrollableTracksViewportHeight() {
    return false;
  }

  @Override
  public Dimension getPreferredScrollableViewportSize() {
    return getPreferredSize();
  }

  @Override
  public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
    return GuiUtils.getScaleLength(SCROLL_UNIT);
  }

  @Override
  public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
    return Math.max(GuiUtils.getScaleLength(SCROLL_UNIT), visibleRect.height - SCROLL_UNIT);
  }

  private void fillProfileCombo() {
    updatingProfileCombo = true;
    try {
      MeasurementProfileRegistry registry = MeasurementProfileRegistry.getInstance();
      profileCombo.removeAllItems();
      profileCombo.addItem(Messages.getString("MeasureTool.profile_auto"));
      registry.profiles().forEach(profileCombo::addItem);
      String selected = registry.selectedId();
      profileCombo.setSelectedItem(
          selected == null ? profileCombo.getItemAt(0) : registry.profile(selected).orElse(null));
    } finally {
      updatingProfileCombo = false;
    }
  }

  private void onProfilesChanged() {
    fillProfileCombo();
    applyActiveProfile();
  }

  /** Applies the registry's active profile to this viewer type when it changed. */
  private void applyActiveProfile() {
    MeasurementProfile active = MeasurementProfileRegistry.getInstance().active();
    // Compared as a whole: a profile edited in the preferences is applied again
    if (active != null && !active.equals(appliedProfile)) {
      appliedProfile = active;
      MeasurementProfiles.apply(active, eventManager);
      checkboxBasicImageStatistics.setSelected(viewSetting.isBasicStatistics());
    }
  }

  @Override
  public void changingViewContentEvent(SeriesViewerEvent event) {
    EVENT type = event.getEventType();
    if (type == EVENT.SELECT || type == EVENT.SELECT_VIEW || type == EVENT.LAYOUT) {
      MediaSeries<?> series = event.getSeries();
      TagW modalityTag = TagW.get("Modality"); // NON-NLS
      Object modality =
          series == null || modalityTag == null ? null : series.getTagValue(modalityTag);
      MeasurementProfileRegistry.getInstance()
          .setModality(modality == null ? null : modality.toString());
    }
  }

  /** Recomputes the labels and repaints every open view, after a display preference change. */
  public static void refreshViewLabels() {
    List<ViewerPlugin<?>> viewerPlugins = GuiUtils.getUICore().getViewerPlugins();
    synchronized (viewerPlugins) {
      for (int i = viewerPlugins.size() - 1; i >= 0; i--) {
        if (viewerPlugins.get(i) instanceof ImageViewerPlugin<?> plugin) {
          for (ViewCanvas<?> view : plugin.getImagePanels()) {
            if (view != null) {
              view.getGraphicManager().updateLabels(true, view);
              view.getJComponent().repaint();
            }
          }
        }
      }
    }
  }

  /** Shows the panel of the tool selected in the measurement palette, if it has one. */
  private void showActiveToolPanel() {
    Graphic active =
        eventManager
            .getAction(ActionW.DRAW_MEASURE)
            .map(a -> a.getSelectedItem() instanceof Graphic g ? g : null)
            .orElse(null);
    showToolPanel(GraphicRegistry.getInstance().keyOf(active).orElse(null), null);
  }

  /** Shows or refreshes the panel of a tool for the given graphic of that tool. */
  private void showToolPanel(String key, Graphic graphic) {
    GraphicRegistry registry = GraphicRegistry.getInstance();
    GraphicToolDescriptor descriptor =
        key == null
            ? null
            : registry.descriptor(key).filter(GraphicToolDescriptor::hasPanel).orElse(null);
    if (descriptor == null) {
      if (currentToolPanel != null) {
        currentToolKey = null;
        currentToolPanel = null;
        toolPanelHost.removeAll();
        toolPanelHost.revalidate();
        toolPanelHost.repaint();
      }
      return;
    }
    GraphicToolContext context =
        new GraphicToolContext(eventManager, eventManager.getSelectedViewPane(), graphic);
    if (key.equals(currentToolKey) && currentToolPanel instanceof ToolPanel panel) {
      panel.update(context);
      return;
    }
    currentToolKey = key;
    currentToolPanel = descriptor.panel().apply(context);
    String title = registry.prototype(key).map(Graphic::getUIName).orElse(key);
    toolPanelHost.removeAll();
    toolPanelHost.add(
        new CollapsiblePanel(
            title, currentToolPanel, true, "weasis.measure.panel." + key), // NON-NLS
        BorderLayout.CENTER);
    toolPanelHost.revalidate();
    toolPanelHost.repaint();
  }

  private void updateToolPanel(Graphic graphic) {
    GraphicToolContext context =
        new GraphicToolContext(eventManager, eventManager.getSelectedViewPane(), graphic);
    if (currentToolPanel instanceof ToolPanel panel && currentToolKey != null) {
      panel.update(context);
    }
    if (profileSection.isVisible()) {
      profilePanel.update(context);
    }
  }

  /** The intensity profile applies to any open measurement, whatever tool drew it. */
  private void showProfile(Graphic graphic) {
    boolean open = graphic != null && IntensityProfile.isOpenPath(graphic);
    if (open) {
      profilePanel.update(
          new GraphicToolContext(eventManager, eventManager.getSelectedViewPane(), graphic));
    }
    if (!open) {
      profilePanel.clear();
    }
    if (profileSection.isVisible() != open) {
      profileSection.setVisible(open);
      revalidate();
      repaint();
    }
  }

  public final JPanel getIconsPanel() {
    final JPanel transform = new JPanel();
    transform.setLayout(new BoxLayout(transform, BoxLayout.Y_AXIS));
    transform.setBorder(GuiUtils.getEmptyBorder(5, 5, 5, 5));

    transform.add(buildProfileSelector());
    transform.add(Box.createVerticalStrut(5));
    MeasureTool.buildIconPanel(transform, eventManager, ActionW.MEASURE, ActionW.DRAW_MEASURE, 5);
    MeasureTool.buildIconPanel(transform, eventManager, ActionW.DRAW, ActionW.DRAW_GRAPHICS, 5);
    transform.add(Box.createVerticalStrut(5));

    JLabel label = new JLabel(Messages.getString("MeasureToolBar.line") + StringUtil.COLON);
    JButton button = buildLineColorButton(this);

    JSpinner spinner = new JSpinner();
    viewSetting.initLineWidthSpinner(spinner);
    // One flow for every option: side by side in a wide dock, wrapped in a narrow one
    int gap = GuiUtils.getScaleLength(5);
    JPanel options = new JPanel(new WrapFlowLayout(2 * gap, gap));
    options.setBorder(GuiUtils.getEmptyBorder(5, 5, 5, 5));
    options.add(label, WrapFlowLayout.KEEP_WITH_NEXT);
    options.add(button, WrapFlowLayout.KEEP_WITH_NEXT);
    options.add(spinner);

    eventManager
        .getAction(ActionW.DRAW_ONLY_ONCE)
        .ifPresent(
            b -> {
              JCheckBox checkDraw = b.createCheckBox(ActionW.DRAW_ONLY_ONCE.getTitle());
              checkDraw.setSelected(viewSetting.isDrawOnlyOnce());
              options.add(checkDraw);
            });

    checkboxBasicImageStatistics.setSelected(viewSetting.isBasicStatistics());
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
          refreshViewLabels();
        });
    options.add(checkboxBasicImageStatistics);

    eventManager
        .getAction(ActionW.SPATIAL_UNIT)
        .ifPresent(
            b -> {
              final JLabel labelUnit =
                  new JLabel(Messages.getString("MeasureTool.unit") + StringUtil.COLON);
              JComboBox<?> unitComboBox = b.createCombo(120);
              unitComboBox.setSelectedItem(Unit.PIXEL);
              options.add(labelUnit, WrapFlowLayout.KEEP_WITH_NEXT);
              options.add(unitComboBox);
            });
    transform.add(options);

    final JButton btnGeneralOptions = new JButton(Messages.getString("MeasureTool.more_options"));
    btnGeneralOptions.addActionListener(
        e -> {
          Window win = SwingUtilities.getWindowAncestor(MeasureTool.this);
          ColorLayerUI layer = ColorLayerUI.createTransparentLayerUI(win.getParent());
          PreferenceDialog dialog = new PreferenceDialog(win);
          dialog.showPage(BUTTON_NAME);
          ColorLayerUI.showCenterScreen(dialog, layer);
        });
    transform.add(GuiUtils.getFlowLayoutPanel(btnGeneralOptions));
    return transform;
  }

  public static void updateMeasureProperties() {
    GraphicRegistry registry = GraphicRegistry.getInstance();
    for (ToolCategory category : ToolCategory.values()) {
      registry
          .prototypes(category)
          .forEach(g -> MeasureToolBar.applyDefaultSetting(viewSetting, g));
    }
  }

  public JPanel getSelectedMeasurePanel() {
    jtable = createMultipleRenderingTable(new SimpleTableModel(new String[] {}, new Object[][] {}));
    jtable.setFont(FontItem.MINI.getFont());

    jtable.getTableHeader().setReorderingAllowed(false);
    MeasureTables.installCopyMenu(jtable);

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
    return getToolComponentFromJScrollPane(rootPane);
  }

  @Override
  protected void changeToolWindowAnchor(CLocation clocation) {
    // Do nothing
  }

  public static JButton buildLineColorButton(Component c) {
    JButton button = new JButton(ResourceUtil.getIcon(ActionIcon.PIPETTE));
    button.setToolTipText(Messages.getString("MeasureTool.pick"));
    button.addActionListener(
        e -> {
          Color newColor =
              JColorChooser.showDialog(
                  WinUtil.getValidComponent(c),
                  Messages.getString("MeasureTool.pick_color"),
                  viewSetting.getLineColor());
          if (newColor != null) {
            viewSetting.setLineColor(newColor);
            updateMeasureProperties();
            if (GraphicOutline.relativeLuminance(newColor) < GraphicOutline.MIN_LINE_LUMINANCE) {
              JOptionPane.showMessageDialog(
                  WinUtil.getValidComponent(c),
                  Messages.getString("MeasureTool.dark_color"),
                  Messages.getString("MeasureTool.pick_color"),
                  JOptionPane.INFORMATION_MESSAGE);
            }
          }
        });
    return button;
  }

  /** Puts the current buttons of the group in the panel, each selecting the drawing action. */
  static void populate(
      JPanel pIcons,
      JToggleButtonGroup<?> group,
      ImageViewerEventManager<?> eventManager,
      Feature<?> action) {
    pIcons.removeAll();
    for (JToggleButton item : group.getJToggleButtonList()) {
      item.addActionListener(
          _ -> {
            ImageViewerPlugin<? extends ImageElement> view =
                eventManager.getSelectedView2dContainer();
            final ViewerToolBar<?> toolBar = view == null ? null : view.getViewerToolBar();
            String cmd = action.cmd();
            if (toolBar != null && !toolBar.isCommandActive(cmd)) {
              MouseActions mouseActions = eventManager.getMouseActions();
              mouseActions.setAction(MouseActions.T_LEFT, cmd);
              view.setMouseActions(mouseActions);
              toolBar.changeButtonState(MouseActions.T_LEFT, cmd);
            }
          });
      pIcons.add(item);
    }
    pIcons.revalidate();
    pIcons.repaint();
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
    if (selectedGraphic != null && selectedGraphic.size() == 1) {
      updateToolPanel(selectedGraphic.getFirst());
    }

    // just clear tableContainer if measList is null
    if (measList != null) {
      String[] headers = {
        Messages.getString("MeasureTool.param"), Messages.getString("MeasureTool.val")
      };
      jtable.setModel(new SimpleTableModel(headers, getLabels(measList)));
      jtable
          .getColumnModel()
          .getColumn(1)
          .setCellRenderer(MeasureTables.valueRenderer(measList, valueFormat()));
      MeasureTables.fit(jtable, tableContainer);
      showAcquisitionInfo();
    } else {
      tableContainer.setPreferredSize(GuiUtils.getDimension(50, 50));
    }
    tableContainer.revalidate();
    tableContainer.repaint();
  }

  private MeasureFormat valueFormat() {
    ViewCanvas<?> view = eventManager.getSelectedViewPane();
    Unit unit =
        eventManager
            .getAction(ActionW.SPATIAL_UNIT)
            .map(c -> (Unit) c.getSelectedItem())
            .orElse(null);
    return new MeasureFormat(
        view == null ? null : view.getMeasurableLayer(), unit, viewSetting.getDecimals());
  }

  /** Under the values: where the pixel size comes from and the slice thickness, when known. */
  private void showAcquisitionInfo() {
    ViewCanvas<?> view = eventManager.getSelectedViewPane();
    List<String> lines = acquisitionInfo(view);
    Graphic selected =
        selectedGraphic != null && selectedGraphic.size() == 1 ? selectedGraphic.getFirst() : null;
    boolean calibrate = view != null && CalibrationView.accepts(selected);
    if (lines.isEmpty() && !calibrate) {
      return;
    }
    JPanel footer = new JPanel(new BorderLayout());
    if (!lines.isEmpty()) {
      acquisitionLabel.setFont(FontItem.MINI.getFont());
      acquisitionLabel.setText("<html>" + String.join("<br>", lines) + "</html>"); // NON-NLS
      footer.add(acquisitionLabel, BorderLayout.CENTER);
    }
    if (calibrate) {
      JButton button = new JButton(Messages.getString("MeasureTool.calibrate"));
      button.setFont(FontItem.MINI.getFont());
      button.setToolTipText(Messages.getString("MeasureTool.calibrate_tip"));
      button.addActionListener(_ -> CalibrationView.showDialog((LineGraphic) selected, view));
      footer.add(GuiUtils.getFlowLayoutPanel(button), BorderLayout.PAGE_END);
    }
    tableContainer.add(footer, BorderLayout.PAGE_END);
    Dimension size = tableContainer.getPreferredSize();
    size.height += footer.getPreferredSize().height;
    tableContainer.setPreferredSize(size);
  }

  static List<String> acquisitionInfo(ViewCanvas<?> view) {
    ImageElement image = view == null ? null : view.getImage();
    if (image == null) {
      return List.of();
    }
    List<String> lines = new ArrayList<>(2);
    String calibration = image.getPixelSizeCalibrationDescription();
    if (StringUtil.hasText(calibration)) {
      lines.add(
          Messages.getString("MeasureTool.calibration") + StringUtil.COLON + " " + calibration);
    }
    TagW thicknessTag = TagW.get("SliceThickness"); // NON-NLS
    if (thicknessTag != null && image.getTagValue(thicknessTag) instanceof Number thickness) {
      lines.add(
          Messages.getString("MeasureTool.thickness")
              + StringUtil.COLON
              + " "
              + DecFormatter.twoDecimal(thickness)
              + " "
              + Unit.MILLIMETER.getAbbreviation());
    }
    return lines;
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
    String key = GraphicRegistry.getInstance().keyOf(g).orElse(null);
    boolean graphicHasPanel =
        key != null
            && GraphicRegistry.getInstance()
                .descriptor(key)
                .map(GraphicToolDescriptor::hasPanel)
                .orElse(false);
    if (graphicHasPanel) {
      showToolPanel(key, g);
    } else {
      showActiveToolPanel();
    }
    showProfile(g);
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

    // As many columns as the width allows; lineLength only matters before the panel has a width
    pIcons.setLayout(new WrapGridLayout(lineLength));
    JToggleButtonGroup<?> measures = actionState.get().createButtonGroup();
    Runnable populate = () -> populate(pIcons, measures, eventManager, action);
    populate.run();
    // A profile or a plugin changes the tools: the group then builds new buttons, which the panel
    // has to take
    measures.setRebuildListener(() -> SwingUtilities.invokeLater(populate));
    rootPanel.add(pIcons);
  }
}
