/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.editor.image.lut;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Rectangle;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.nio.file.Path;
import java.text.MessageFormat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import javax.swing.AbstractAction;
import javax.swing.AbstractCellEditor;
import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JColorChooser;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.WindowConstants;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellEditor;
import net.miginfocom.swing.MigLayout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.Messages;
import org.weasis.core.api.gui.util.CollapsiblePanel;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.gui.util.WinUtil;
import org.weasis.core.api.image.lut.ColorMapRegistry;
import org.weasis.core.api.util.ResourceUtil;
import org.weasis.core.api.util.ResourceUtil.ActionIcon;
import org.weasis.core.util.StringUtil;
import org.weasis.opencv.op.lut.ByteLut;
import org.weasis.opencv.op.lut.colormap.ColorMap;
import org.weasis.opencv.op.lut.colormap.ColorMapCompiler;
import org.weasis.opencv.op.lut.colormap.ColorMapDomain;
import org.weasis.opencv.op.lut.colormap.ColorMapEdits;
import org.weasis.opencv.op.lut.colormap.ColorMapType;
import org.weasis.opencv.op.lut.colormap.ColorStop;
import org.weasis.opencv.op.lut.colormap.DomainKind;
import org.weasis.opencv.op.lut.colormap.GradientOpacity;
import org.weasis.opencv.op.lut.colormap.Interpolation;
import org.weasis.opencv.op.lut.colormap.InterpolationSpace;
import org.weasis.opencv.op.lut.colormap.Lighting;
import org.weasis.opencv.op.lut.colormap.Material;
import org.weasis.opencv.op.lut.colormap.MaterialPreset;

/**
 * Creates and edits color maps of the {@link ColorMapRegistry}. Quick settings cover the usual
 * needs (palette, range, threshold, bands); the collapsed advanced section exposes the stops. Every
 * edit is undoable and previewed on the host view.
 */
public class ColorMapEditorDialog extends JDialog implements ColorMapCurvePanel.Listener {

  private static final Logger LOGGER = LoggerFactory.getLogger(ColorMapEditorDialog.class);

  private static final int PREVIEW_DELAY_MS = 90;
  private static final int STRIP_HEIGHT = 14;
  private static final String FAVORITE_MARK = " \u2605"; // NON-NLS
  private static final int SPINNER_COLUMNS = 7;
  private static final List<Integer> BITS = List.of(8, 12, 16);

  private final transient ColorMapEditorHost host;
  private final transient ColorMapRegistry registry;
  private final Timer previewTimer;
  private final transient Deque<ColorMap> undo = new ArrayDeque<>();
  private final transient Deque<ColorMap> redo = new ArrayDeque<>();
  private transient ColorMap current;
  private transient ColorMap savedMap;
  private transient ByteLut openedWith;
  private transient ColorMap dragStart;
  private boolean updating;

  private final String filterAll = Messages.getString("ColorMapEditor.all");
  private final JTextField filterField = new JTextField(12);
  private final JComboBox<String> modalityFilter = new JComboBox<>();
  private final JComboBox<String> sourceFilter = new JComboBox<>();
  private final JComboBox<String> categoryFilter = new JComboBox<>();
  private final JComboBox<String> dimensionFilter = new JComboBox<>();
  private final DefaultListModel<ColorMap> listModel = new DefaultListModel<>();
  private final JList<ColorMap> mapList = new JList<>(listModel);

  private final JTextField nameField = new JTextField(18);
  private final JComboBox<ColorMapType> typeCombo = new JComboBox<>(ColorMapType.values());
  private final JTextField modalityField = new JTextField(10);
  private final JCheckBox defaultCheck =
      new JCheckBox(Messages.getString("ColorMapEditor.default"));
  private final JCheckBox favoriteCheck =
      new JCheckBox(Messages.getString("ColorMapEditor.favorite"));
  private final JComboBox<DomainKind> kindCombo = new JComboBox<>(DomainKind.values());
  private final JTextField unitField = new JTextField(6);
  private final JTextField referenceField = new JTextField(8);
  private final JSpinner minSpinner = decimalSpinner(0);
  private final JSpinner maxSpinner = decimalSpinner(1);
  private final JComboBox<Integer> bitsCombo = new JComboBox<>(BITS.toArray(Integer[]::new));
  private final JComboBox<InterpolationSpace> spaceCombo =
      new JComboBox<>(InterpolationSpace.values());
  private final JComboBox<Interpolation> interpolationCombo =
      new JComboBox<>(Interpolation.values());
  private final JComboBox<ColorMap> paletteCombo = new JComboBox<>();
  private final JSpinner bandsSpinner = new JSpinner(new SpinnerNumberModel(8, 2, 256, 1));
  private final JSpinner belowSpinner = decimalSpinner(0);
  private final JSpinner opaqueSpinner = decimalSpinner(0);

  private final ColorMapCurvePanel curve = new ColorMapCurvePanel(this);
  private final StopTableModel stopModel = new StopTableModel();
  private final JTable stopTable = new JTable(stopModel);
  private final JPanel previewStrip = new PreviewStrip();
  private final JCheckBox liveCheck =
      new JCheckBox(Messages.getString("ColorMapEditor.livePreview"));
  private final JButton undoButton = new JButton(Messages.getString("ColorMapEditor.undo"));
  private final JButton redoButton = new JButton(Messages.getString("ColorMapEditor.redo"));
  private final JButton deleteButton = new JButton(Messages.getString("ColorMapEditor.delete"));
  private final JButton pickButton = new JButton(Messages.getString("ColorMapEditor.pick"));
  private final JCheckBox volumeCheck = new JCheckBox(Messages.getString("ColorMapEditor.volume"));
  private final JCheckBox shadeCheck = new JCheckBox(Messages.getString("ColorMapEditor.shade"));
  private final JSpinner specularPowerSpinner =
      new JSpinner(new SpinnerNumberModel(10.0, 1.0, 200.0, 1.0));
  private final JSpinner edgeSpinner = new JSpinner(new SpinnerNumberModel(0.0, 0.0, 1.0, 0.05));
  private final JComboBox<MaterialPreset> materialCombo = new JComboBox<>(MaterialPreset.values());
  private final JButton applyMaterialButton =
      new JButton(Messages.getString("ColorMapEditor.applyToStop"));
  private final JButton applyMaterialAllButton =
      new JButton(Messages.getString("ColorMapEditor.applyToAll"));
  private static final int HISTOGRAM_BINS = 128;
  private transient double[] histogram;
  private double histogramMin = Double.NaN;
  private double histogramMax = Double.NaN;
  private boolean lightingColumns;
  private int advancedGrowth;
  private final JLabel statusLabel = new JLabel(" ");

  public ColorMapEditorDialog(Component parent, ColorMapEditorHost host) {
    super(
        parent == null ? null : SwingUtilities.getWindowAncestor(parent),
        Messages.getString("ColorMapEditor.title"),
        ModalityType.MODELESS);
    this.host = Objects.requireNonNull(host);
    this.registry = ColorMapRegistry.getInstance();
    this.previewTimer = new Timer(PREVIEW_DELAY_MS, e -> pushPreview());
    previewTimer.setRepeats(false);
    this.openedWith = host.currentLut();
    setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
    setIconImage(ResourceUtil.getIcon(ActionIcon.LUT).getImage());
    initComponents();
    reloadList();
    loadMap(initialMap());
    pack();
    setMinimumSize(new Dimension(GuiUtils.getScaleLength(720), GuiUtils.getScaleLength(540)));
  }

  /** Opens the editor centered on {@code parent}. */
  public static ColorMapEditorDialog open(Component parent, ColorMapEditorHost host) {
    ColorMapEditorDialog dialog = new ColorMapEditorDialog(parent, host);
    if (parent == null || parent instanceof Window) {
      GuiUtils.showCenterScreen(dialog);
    } else {
      GuiUtils.showCenterScreen(dialog, parent);
    }
    return dialog;
  }

  private ColorMap initialMap() {
    return mapOf(openedWith);
  }

  // The map behind a LUT, else the first map offered for the host's modality.
  private ColorMap mapOf(ByteLut lut) {
    if (lut != null && lut.source() != null) {
      return lut.source();
    }
    if (lut != null && lut.lutTable() != null) {
      return ColorMap.fromByteLut(lut);
    }
    List<ColorMap> maps = registry.mapsFor(host.currentModality());
    return maps.isEmpty() ? registry.maps().getFirst() : maps.getFirst();
  }

  // Closing shows the last saved map, else the LUT the editor opened with: previews are dropped.
  @Override
  public void dispose() {
    previewTimer.stop();
    if (savedMap != null) {
      host.preview(registry.byteLut(savedMap));
    } else if (openedWith != null) {
      host.preview(openedWith);
    }
    super.dispose();
  }

  // ───────────────────── UI construction ─────────────────────

  private void initComponents() {
    JPanel content =
        new JPanel(new MigLayout("insets 10lp, fill", "[grow]", "[grow][]")); // NON-NLS
    JSplitPane split =
        new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, buildListPanel(), buildEditorPanel());
    split.setDividerLocation(GuiUtils.getScaleLength(280));
    content.add(split, "grow, wrap"); // NON-NLS
    content.add(buildBottomPanel(), "growx"); // NON-NLS
    setContentPane(content);
    bindKey(KeyStroke.getKeyStroke(KeyEvent.VK_Z, KeyEvent.CTRL_DOWN_MASK), "undo", this::undo);
    bindKey(KeyStroke.getKeyStroke(KeyEvent.VK_Y, KeyEvent.CTRL_DOWN_MASK), "redo", this::redo);
  }

  private void bindKey(KeyStroke stroke, String name, Runnable action) {
    JComponent root = getRootPane();
    root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(stroke, name);
    root.getActionMap()
        .put(
            name,
            new AbstractAction() {
              @Override
              public void actionPerformed(ActionEvent e) {
                action.run();
              }
            });
  }

  private JPanel buildListPanel() {
    JPanel panel = new JPanel(new MigLayout("insets 0, fill", "[grow]", "[][][grow]")); // NON-NLS
    JPanel filters = new JPanel(new MigLayout("insets 0", "[][grow]", "[]")); // NON-NLS
    filters.add(new JLabel(Messages.getString("ColorMapEditor.filter")));
    filters.add(filterField, "growx, wrap"); // NON-NLS
    sourceFilter.addItem(filterAll);
    sourceFilter.addItem(Messages.getString("ColorMapEditor.builtin"));
    sourceFilter.addItem(Messages.getString("ColorMapEditor.imported"));
    sourceFilter.addItem(Messages.getString("ColorMapEditor.user"));
    dimensionFilter.addItem(filterAll);
    dimensionFilter.addItem(Messages.getString("ColorMapEditor.maps2d"));
    dimensionFilter.addItem(Messages.getString("ColorMapEditor.maps3d"));
    dimensionFilter.setSelectedIndex(host.isVolume() ? 2 : 1);
    filters.add(modalityFilter);
    filters.add(sourceFilter, "growx, wrap"); // NON-NLS
    filters.add(categoryFilter);
    filters.add(dimensionFilter, "growx"); // NON-NLS
    panel.add(filters, "growx, wrap"); // NON-NLS
    panel.add(new JLabel(), "wrap"); // NON-NLS

    mapList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    mapList.setCellRenderer(new MapRenderer());
    mapList.addListSelectionListener(
        e -> {
          if (!e.getValueIsAdjusting() && !updating && mapList.getSelectedValue() != null) {
            loadMap(mapList.getSelectedValue());
          }
        });
    JScrollPane listScroll = new JScrollPane(mapList);
    listScroll.setPreferredSize(
        new Dimension(GuiUtils.getScaleLength(260), GuiUtils.getScaleLength(360)));
    panel.add(listScroll, "grow"); // NON-NLS

    filterField
        .getDocument()
        .addDocumentListener(
            new DocumentListener() {
              @Override
              public void insertUpdate(DocumentEvent e) {
                reloadList();
              }

              @Override
              public void removeUpdate(DocumentEvent e) {
                reloadList();
              }

              @Override
              public void changedUpdate(DocumentEvent e) {
                reloadList();
              }
            });
    modalityFilter.addActionListener(e -> reloadList());
    sourceFilter.addActionListener(e -> reloadList());
    categoryFilter.addActionListener(e -> reloadList());
    dimensionFilter.addActionListener(e -> reloadList());
    return panel;
  }

  private JPanel buildEditorPanel() {
    MigLayout layout = new MigLayout("insets 0 8lp 0 0, fillx", "[grow]", "[][][][]"); // NON-NLS
    JPanel panel = new JPanel(layout);
    panel.add(buildQuickPanel(), "growx, wrap"); // NON-NLS
    panel.add(previewStrip, "growx, h 28lp!, wrap"); // NON-NLS
    JPanel advancedContent = buildAdvancedPanel();
    CollapsiblePanel advanced =
        new CollapsiblePanel(Messages.getString("ColorMapEditor.advanced"), advancedContent, false);
    panel.add(advanced, "growx, wrap"); // NON-NLS
    panel.add(statusLabel, "growx"); // NON-NLS
    // The curve only gets room when the section is open: let its row take the spare height then,
    // and enlarge the dialog if the packed height cannot show the graph and the stop table.
    advancedContent.addComponentListener(
        new ComponentAdapter() {
          @Override
          public void componentShown(ComponentEvent e) {
            layout.setRowConstraints("[][][grow][]"); // NON-NLS
            layout.setComponentConstraints(advanced, "grow, hmax 100%, wrap"); // NON-NLS
            growToFitContent();
          }

          @Override
          public void componentHidden(ComponentEvent e) {
            layout.setRowConstraints("[][][][]"); // NON-NLS
            layout.setComponentConstraints(advanced, "growx, wrap"); // NON-NLS
            giveBackAdvancedHeight();
          }
        });
    return panel;
  }

  /** Grows the dialog to its preferred height, within the screen it is on. */
  private void growToFitContent() {
    int needed = getPreferredSize().height;
    if (needed <= getHeight()) {
      return;
    }
    Rectangle screen = WinUtil.getClosedScreenBound(getBounds());
    int height = screen == null ? needed : Math.min(needed, screen.height);
    int y =
        screen == null
            ? getY()
            : Math.max(screen.y, Math.min(getY(), screen.y + screen.height - height));
    advancedGrowth = height - getHeight();
    setBounds(getX(), y, getWidth(), height);
  }

  // Undo the growth of the last expansion, but never below what the collapsed content needs.
  private void giveBackAdvancedHeight() {
    if (advancedGrowth > 0) {
      setSize(getWidth(), Math.max(getPreferredSize().height, getHeight() - advancedGrowth));
      advancedGrowth = 0;
    }
  }

  private JPanel buildQuickPanel() {
    JPanel panel =
        new JPanel(new MigLayout("insets 0, wrap 4", "[][grow][][grow]", "[]4lp[]")); // NON-NLS
    panel.setBorder(GuiUtils.getTitledBorder(Messages.getString("ColorMapEditor.quick")));

    panel.add(label("ColorMapEditor.name"));
    panel.add(nameField, "growx"); // NON-NLS
    panel.add(label("ColorMapEditor.type"));
    panel.add(typeCombo, "growx"); // NON-NLS

    panel.add(label("ColorMapEditor.modalities"));
    panel.add(modalityField, "growx"); // NON-NLS
    panel.add(defaultCheck);
    panel.add(favoriteCheck);

    panel.add(label("ColorMapEditor.domain"));
    JPanel domain = new JPanel(new MigLayout("insets 0", "[][][][]", "[]")); // NON-NLS
    domain.add(kindCombo);
    domain.add(new JLabel(Messages.getString("ColorMapEditor.unit")));
    domain.add(unitField);
    domain.add(referenceField);
    panel.add(domain, "span 3, growx"); // NON-NLS

    panel.add(label("ColorMapEditor.range"));
    JPanel range = new JPanel(new MigLayout("insets 0", "[][]", "[]")); // NON-NLS
    range.add(minSpinner);
    range.add(maxSpinner);
    panel.add(range);
    panel.add(label("ColorMapEditor.bits"));
    panel.add(bitsCombo);

    panel.add(label("ColorMapEditor.space"));
    panel.add(spaceCombo, "growx"); // NON-NLS
    panel.add(label("ColorMapEditor.interpolation"));
    panel.add(interpolationCombo, "growx"); // NON-NLS

    panel.add(label("ColorMapEditor.palette"));
    paletteCombo.setRenderer(new MapRenderer());
    panel.add(paletteCombo, "growx"); // NON-NLS
    JButton applyPalette = new JButton(Messages.getString("ColorMapEditor.apply"));
    applyPalette.addActionListener(
        e -> {
          if (paletteCombo.getSelectedItem() instanceof ColorMap palette) {
            pushEdit(ColorMapEdits.withPalette(current, palette));
          }
        });
    JButton reverse = new JButton(Messages.getString("ColorMapEditor.reverse"));
    reverse.addActionListener(e -> pushEdit(current.reversed()));
    panel.add(GuiUtils.getFlowLayoutPanel(applyPalette, reverse), "span 2"); // NON-NLS

    panel.add(label("ColorMapEditor.bands"));
    JButton discretize = new JButton(Messages.getString("ColorMapEditor.apply"));
    discretize.addActionListener(
        e -> pushEdit(ColorMapEdits.discretized(current, (Integer) bandsSpinner.getValue())));
    panel.add(GuiUtils.getFlowLayoutPanel(bandsSpinner, discretize));
    panel.add(label("ColorMapEditor.transparentBelow"));
    JButton threshold = new JButton(Messages.getString("ColorMapEditor.apply"));
    threshold.addActionListener(e -> applyThreshold());
    JPanel thresholdPanel = new JPanel(new MigLayout("insets 0", "[][][][]", "[]")); // NON-NLS
    thresholdPanel.add(belowSpinner);
    thresholdPanel.add(new JLabel(Messages.getString("ColorMapEditor.opaqueFrom")));
    thresholdPanel.add(opaqueSpinner);
    thresholdPanel.add(threshold);
    panel.add(thresholdPanel);

    panel.add(label("ColorMapEditor.lighting"));
    JPanel lightingPanel = new JPanel(new MigLayout("insets 0", "[][][][]", "[]")); // NON-NLS
    lightingPanel.add(volumeCheck);
    lightingPanel.add(shadeCheck);
    lightingPanel.add(new JLabel(Messages.getString("ColorMapEditor.specularPower")));
    lightingPanel.add(specularPowerSpinner);
    lightingPanel.add(new JLabel(Messages.getString("ColorMapEditor.edgeEmphasis")));
    lightingPanel.add(edgeSpinner);
    panel.add(lightingPanel, "span 3, growx"); // NON-NLS
    volumeCheck.addActionListener(e -> toggleVolume());
    shadeCheck.addActionListener(e -> commitLighting());
    bindSpinner(specularPowerSpinner, this::commitLighting);
    bindSpinner(edgeSpinner, this::commitLighting);

    bindText(nameField, text -> commit(b -> b.name(text)));
    bindCombo(typeCombo, () -> commit(b -> b.type((ColorMapType) typeCombo.getSelectedItem())));
    bindText(modalityField, text -> commit(b -> b.modalities(parseModalities(text))));
    defaultCheck.addActionListener(
        e -> commit(b -> b.defaultForModality(defaultCheck.isSelected())));
    favoriteCheck.addActionListener(e -> toggleFavorite());
    bindCombo(kindCombo, this::applyDomainKind);
    bindText(unitField, text -> commitDomain(d -> withUnit(d, text)));
    bindText(referenceField, text -> commitDomain(d -> withReference(d, text)));
    bindSpinner(minSpinner, this::applyRange);
    bindSpinner(maxSpinner, this::applyRange);
    bindCombo(bitsCombo, () -> commit(b -> b.bits((Integer) bitsCombo.getSelectedItem())));
    bindCombo(
        spaceCombo, () -> commit(b -> b.space((InterpolationSpace) spaceCombo.getSelectedItem())));
    bindCombo(
        interpolationCombo,
        () -> commit(b -> b.interpolation((Interpolation) interpolationCombo.getSelectedItem())));
    return panel;
  }

  private JPanel buildAdvancedPanel() {
    JPanel panel = new JPanel(new MigLayout("insets 0, fill", "[grow][]", "[grow][]")); // NON-NLS
    curve.setToolTipText(Messages.getString("ColorMapEditor.curveHelp"));
    curve
        .getInputMap(JComponent.WHEN_FOCUSED)
        .put(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), "removeStop"); // NON-NLS
    curve
        .getActionMap()
        .put(
            "removeStop", // NON-NLS
            new AbstractAction() {
              @Override
              public void actionPerformed(ActionEvent e) {
                curve.removeSelected();
              }
            });
    panel.add(curve, "grow, span 2, wrap"); // NON-NLS

    stopTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    stopTable.setDefaultRenderer(Color.class, new ColorCellRenderer());
    stopTable.setDefaultEditor(Color.class, new ColorCellEditor());
    stopTable.setPreferredScrollableViewportSize(
        new Dimension(GuiUtils.getScaleLength(360), GuiUtils.getScaleLength(120)));
    stopTable
        .getSelectionModel()
        .addListSelectionListener(
            e -> {
              if (!e.getValueIsAdjusting()) {
                curve.setSelectedStop(stopTable.getSelectedRow());
              }
            });
    panel.add(new JScrollPane(stopTable), "grow"); // NON-NLS

    JButton add = new JButton(Messages.getString("ColorMapEditor.add"));
    add.addActionListener(e -> addStop());
    JButton remove = new JButton(Messages.getString("ColorMapEditor.remove"));
    remove.addActionListener(e -> curve.removeSelected());
    pickButton.addActionListener(e -> host.pickValue(this::addStopAtValue));
    panel.add(GuiUtils.getVerticalBoxLayoutPanel(add, remove, pickButton), "top, wrap"); // NON-NLS

    applyMaterialButton.addActionListener(e -> applyMaterial(false));
    applyMaterialAllButton.addActionListener(e -> applyMaterial(true));
    panel.add(
        GuiUtils.getFlowLayoutPanel(
            new JLabel(Messages.getString("ColorMapEditor.material") + StringUtil.COLON),
            materialCombo,
            applyMaterialButton,
            applyMaterialAllButton),
        "span 2"); // NON-NLS
    return panel;
  }

  private JPanel buildBottomPanel() {
    JPanel panel = new JPanel(new MigLayout("insets 0", "[]push[]", "[]")); // NON-NLS
    undoButton.addActionListener(e -> undo());
    redoButton.addActionListener(e -> redo());
    JButton duplicate = new JButton(Messages.getString("ColorMapEditor.duplicate"));
    duplicate.addActionListener(e -> duplicate());
    JButton save = new JButton(Messages.getString("ColorMapEditor.save"));
    save.addActionListener(e -> save());
    deleteButton.addActionListener(e -> delete());
    JButton importButton = new JButton(Messages.getString("ColorMapEditor.import"));
    importButton.addActionListener(e -> importTable());
    JButton exportButton = new JButton(Messages.getString("ColorMapEditor.export"));
    exportButton.addActionListener(e -> exportJson());
    liveCheck.setSelected(true);
    liveCheck.addActionListener(e -> schedulePreview());
    JButton close = new JButton(Messages.getString("close"));
    close.addActionListener(e -> dispose());
    panel.add(
        GuiUtils.getFlowLayoutPanel(
            undoButton, redoButton, duplicate, save, deleteButton, importButton, exportButton));
    panel.add(GuiUtils.getFlowLayoutPanel(liveCheck, close));
    return panel;
  }

  private static JLabel label(String key) {
    return new JLabel(Messages.getString(key) + StringUtil.COLON);
  }

  // Unbounded on purpose: a ±Double.MAX_VALUE bound makes Swing size the field for 300 digits.
  private static JSpinner decimalSpinner(double value) {
    JSpinner spinner = new JSpinner(new SpinnerNumberModel(Double.valueOf(value), null, null, 1.0));
    JSpinner.NumberEditor editor = new JSpinner.NumberEditor(spinner, "0.###"); // NON-NLS
    editor.getTextField().setColumns(SPINNER_COLUMNS);
    spinner.setEditor(editor);
    return spinner;
  }

  private void bindText(JTextField field, Consumer<String> commit) {
    field.addActionListener(e -> commit.accept(field.getText()));
    field.addFocusListener(
        new FocusAdapter() {
          @Override
          public void focusLost(FocusEvent e) {
            commit.accept(field.getText());
          }
        });
  }

  private void bindCombo(JComboBox<?> combo, Runnable commit) {
    combo.addActionListener(
        e -> {
          if (!updating) {
            commit.run();
          }
        });
  }

  private void bindSpinner(JSpinner spinner, Runnable commit) {
    spinner.addChangeListener(
        e -> {
          if (!updating) {
            commit.run();
          }
        });
  }

  // ───────────────────── model ─────────────────────

  private void loadMap(ColorMap map) {
    current = map;
    undo.clear();
    redo.clear();
    refreshFromModel();
    schedulePreview();
  }

  private void pushEdit(ColorMap next) {
    if (next == null || next.equals(current)) {
      return;
    }
    undo.push(current);
    redo.clear();
    current = next;
    refreshFromModel();
    schedulePreview();
  }

  // A volume preset needs physical values: a relative map is spread over the displayed data.
  private void toggleVolume() {
    if (!volumeCheck.isSelected()) {
      commit(b -> b.lighting(null));
      return;
    }
    double[] range = host.valueRange().orElse(new double[] {0, 4095});
    ColorMap anchored =
        ColorMapEdits.anchored(current, current.domain().unit(), range[0], range[1]);
    pushEdit(anchored.toBuilder().lighting(Lighting.DEFAULT).build());
  }

  // A favorite is a registry fact keyed on the id, not a property of the map: it applies at once.
  private void toggleFavorite() {
    try {
      registry.setFavorite(current.id(), favoriteCheck.isSelected());
      mapList.repaint();
    } catch (IOException e) {
      LOGGER.error("Cannot save favorite color maps", e);
      statusLabel.setText(e.getMessage());
    }
  }

  private void commit(Consumer<ColorMap.Builder> change) {
    if (updating) {
      return;
    }
    try {
      ColorMap.Builder builder = current.toBuilder();
      change.accept(builder);
      pushEdit(builder.build());
      statusLabel.setText(" ");
    } catch (IllegalArgumentException e) {
      statusLabel.setText(e.getMessage());
    }
  }

  private void commitDomain(java.util.function.UnaryOperator<ColorMapDomain> change) {
    if (updating || current.domain().isRelative()) {
      return;
    }
    commit(b -> b.domain(change.apply(current.domain())));
  }

  private static ColorMapDomain withUnit(ColorMapDomain d, String unit) {
    String value = StringUtil.hasText(unit) ? unit.trim() : null;
    return new ColorMapDomain(d.kind(), value, d.min(), d.max(), d.reference());
  }

  private static ColorMapDomain withReference(ColorMapDomain d, String reference) {
    if (d.kind() != DomainKind.PERCENT) {
      return d;
    }
    String value = StringUtil.hasText(reference) ? reference.trim() : ColorMapDomain.REFERENCE_MAX;
    return new ColorMapDomain(d.kind(), d.unit(), d.min(), d.max(), value);
  }

  private static Set<String> parseModalities(String text) {
    var codes = new TreeSet<String>();
    for (String code : text.split("[,;\\s]+")) {
      if (!code.isBlank()) {
        codes.add(code.trim().toUpperCase(Locale.ROOT));
      }
    }
    return codes;
  }

  private void applyDomainKind() {
    DomainKind kind = (DomainKind) kindCombo.getSelectedItem();
    ColorMapDomain d = current.domain();
    if (kind == null || kind == d.kind()) {
      return;
    }
    try {
      ColorMap next;
      if (kind == DomainKind.RELATIVE) {
        next =
            ColorMapEdits.rescaled(current, 0, 1).toBuilder()
                .domain(ColorMapDomain.RELATIVE)
                .build();
      } else {
        double min = current.firstPosition();
        double max = current.lastPosition() > min ? current.lastPosition() : min + 1;
        String reference = kind == DomainKind.PERCENT ? ColorMapDomain.REFERENCE_MAX : null;
        next =
            current.toBuilder()
                .domain(new ColorMapDomain(kind, d.unit(), min, max, reference))
                .build();
      }
      pushEdit(next);
      statusLabel.setText(" ");
    } catch (IllegalArgumentException e) {
      statusLabel.setText(e.getMessage());
      refreshFromModel();
    }
  }

  private void applyRange() {
    double min = ((Number) minSpinner.getValue()).doubleValue();
    double max = ((Number) maxSpinner.getValue()).doubleValue();
    if (!(max > min)) {
      return;
    }
    try {
      pushEdit(ColorMapEdits.rescaled(current, min, max));
      statusLabel.setText(" ");
    } catch (IllegalArgumentException e) {
      statusLabel.setText(e.getMessage());
    }
  }

  private void applyThreshold() {
    double below = ((Number) belowSpinner.getValue()).doubleValue();
    double opaque = ((Number) opaqueSpinner.getValue()).doubleValue();
    try {
      pushEdit(ColorMapEdits.withThreshold(current, below, Math.max(below, opaque)));
      statusLabel.setText(" ");
    } catch (IllegalArgumentException e) {
      statusLabel.setText(e.getMessage());
    }
  }

  // Eyedropper: a stop at the value read on the view, with the color the map shows there.
  private void addStopAtValue(double value) {
    if (!Double.isFinite(value)) {
      return;
    }
    Color color = current.sample(value).withAlpha(1f).toColor();
    Float alpha = current.hasAlpha() ? current.sample(value).alpha() : null;
    ColorStop stop = new ColorStop(value, color, alpha, null, null);
    ColorMap next = ColorMapEdits.withStop(current, stop);
    pushEdit(next);
    stopSelected(next.stops().indexOf(stop));
  }

  private void commitLighting() {
    if (current.lighting() == null) {
      return;
    }
    float power = ((Number) specularPowerSpinner.getValue()).floatValue();
    float emphasis = ((Number) edgeSpinner.getValue()).floatValue();
    GradientOpacity gradient = emphasis > 0f ? GradientOpacity.edgeEmphasis(emphasis) : null;
    commit(b -> b.lighting(new Lighting(shadeCheck.isSelected(), power, gradient)));
  }

  // Sets the chosen material on the selected stop or on every stop; the map becomes a volume
  // preset.
  private void applyMaterial(boolean all) {
    MaterialPreset preset = (MaterialPreset) materialCombo.getSelectedItem();
    if (preset == null) {
      return;
    }
    var stops = new ArrayList<ColorStop>(current.stops().size());
    int selected = curve.getSelectedStop();
    for (int i = 0; i < current.stops().size(); i++) {
      ColorStop stop = current.stops().get(i);
      stops.add(all || i == selected ? stop.withMaterial(preset.material()) : stop);
    }
    Lighting lighting =
        current.lighting() == null
            ? new Lighting(true, preset.specularPower())
            : all
                ? new Lighting(current.lighting().shade(), preset.specularPower())
                : current.lighting();
    pushEdit(current.toBuilder().stops(stops).lighting(lighting).build());
  }

  private void refreshHistogram(ColorMapDomain d) {
    if (d.min() != histogramMin || d.max() != histogramMax) {
      histogram = host.histogram(d.min(), d.max(), HISTOGRAM_BINS);
      histogramMin = d.min();
      histogramMax = d.max();
      curve.setHistogram(histogram);
    }
  }

  // Share of the histogram the alpha curve lets through: what a volume preset really shows.
  private String visibleFraction() {
    if (histogram == null || current.lighting() == null) {
      return " ";
    }
    double total = 0;
    double visible = 0;
    var sampler = current.sampler();
    ColorMapDomain d = current.domain();
    for (int i = 0; i < histogram.length; i++) {
      double value = d.denormalize((i + 0.5) / histogram.length);
      total += histogram[i];
      visible += histogram[i] * sampler.sample(value).alpha();
    }
    return total <= 0
        ? " "
        : MessageFormat.format(
            Messages.getString("ColorMapEditor.visible"), Math.round(100.0 * visible / total));
  }

  private void addStop() {
    ColorMapDomain d = current.domain();
    double position = d.denormalize(0.5);
    Color color = current.sample(position).withAlpha(1f).toColor();
    Float alpha = current.hasAlpha() ? current.sample(position).alpha() : null;
    ColorStop stop = new ColorStop(position, color, alpha, null, null);
    ColorMap next = ColorMapEdits.withStop(current, stop);
    pushEdit(next);
    stopSelected(next.stops().indexOf(stop));
  }

  private void undo() {
    if (!undo.isEmpty()) {
      redo.push(current);
      current = undo.pop();
      refreshFromModel();
      schedulePreview();
    }
  }

  private void redo() {
    if (!redo.isEmpty()) {
      undo.push(current);
      current = redo.pop();
      refreshFromModel();
      schedulePreview();
    }
  }

  private void duplicate() {
    String name = current.name() + " " + Messages.getString("ColorMapEditor.copy");
    pushEdit(current.toBuilder().name(name).id("user." + ColorMap.slug(name)).build()); // NON-NLS
    nameField.requestFocusInWindow();
    nameField.selectAll();
  }

  private void save() {
    try {
      registry.saveUserMap(current);
      savedMap = current;
      host.mapsChanged();
      reloadList();
      refreshFromModel();
      statusLabel.setText(" ");
    } catch (IOException e) {
      LOGGER.error("Cannot save color map", e);
      JOptionPane.showMessageDialog(
          this,
          Messages.getString("ColorMapEditor.saveError")
              + StringUtil.COLON_AND_SPACE
              + e.getMessage(),
          getTitle(),
          JOptionPane.ERROR_MESSAGE);
    }
  }

  private void delete() {
    String message =
        MessageFormat.format(Messages.getString("ColorMapEditor.deleteConfirm"), current.name());
    if (JOptionPane.showConfirmDialog(this, message, getTitle(), JOptionPane.YES_NO_OPTION)
        != JOptionPane.YES_OPTION) {
      return;
    }
    try {
      String id = current.id();
      registry.deleteUserMap(id);
      if (savedMap != null && savedMap.id().equals(id)) {
        savedMap = null;
      }
      host.mapsChanged();
      if (openedWith != null
          && openedWith.source() != null
          && openedWith.source().id().equals(id)) {
        openedWith = host.currentLut(); // the host fell back to its default: restore that on close
      }
      reloadList();
      loadMap(registry.findById(id).orElseGet(() -> mapOf(host.currentLut())));
    } catch (IOException e) {
      LOGGER.error("Cannot delete color map", e);
    }
  }

  private JFileChooser chooser(boolean write) {
    JFileChooser chooser = new JFileChooser();
    chooser.setAcceptAllFileFilterUsed(false);
    for (ColorMapFormat format : host.formats()) {
      if (!write || format.canWrite()) {
        chooser.addChoosableFileFilter(new FormatFilter(format));
      }
    }
    return chooser;
  }

  private void importTable() {
    JFileChooser chooser = chooser(false);
    if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
      return;
    }
    Path file = chooser.getSelectedFile().toPath();
    ColorMapFormat format =
        chooser.getFileFilter() instanceof FormatFilter f && f.format.matches(file)
            ? f.format
            : host.formats().stream()
                .filter(x -> x.matches(file))
                .findFirst()
                .orElse(ColorMapFormats.JSON);
    try {
      List<ColorMap> maps = format.read(file);
      if (!maps.isEmpty()) {
        pushEdit(maps.getFirst());
      }
    } catch (IOException | RuntimeException e) {
      LOGGER.error("Cannot import color map: {}", file, e);
      statusLabel.setText(e.getMessage());
    }
  }

  private void exportJson() {
    JFileChooser chooser = chooser(true);
    chooser.setSelectedFile(new java.io.File(ColorMapFormats.JSON.fileName(current.name())));
    if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
      return;
    }
    ColorMapFormat format =
        chooser.getFileFilter() instanceof FormatFilter f ? f.format : ColorMapFormats.JSON;
    Path file = chooser.getSelectedFile().toPath();
    if (!format.matches(file)) {
      file = file.resolveSibling(format.fileName(file.getFileName().toString()));
    }
    try {
      format.write(file, current);
    } catch (IOException | RuntimeException e) {
      LOGGER.error("Cannot export color map", e);
      statusLabel.setText(e.getMessage());
    }
  }

  private static final class FormatFilter extends javax.swing.filechooser.FileFilter {
    private final ColorMapFormat format;

    FormatFilter(ColorMapFormat format) {
      this.format = format;
    }

    @Override
    public boolean accept(java.io.File f) {
      return f.isDirectory() || format.matches(f.toPath());
    }

    @Override
    public String getDescription() {
      return format.description() + " (" + String.join(", ", format.extensions()) + ")";
    }
  }

  // ───────────────────── view refresh ─────────────────────

  private static final List<Set<ColorMapRegistry.Origin>> SOURCE_FILTERS =
      List.of(
          EnumSet.allOf(ColorMapRegistry.Origin.class),
          EnumSet.of(
              ColorMapRegistry.Origin.BUNDLED,
              ColorMapRegistry.Origin.CONTRIBUTED,
              ColorMapRegistry.Origin.SITE),
          EnumSet.of(ColorMapRegistry.Origin.IMPORTED),
          EnumSet.of(ColorMapRegistry.Origin.USER));

  private void reloadList() {
    updating = true;
    try {
      String previousModality =
          modalityFilter.getSelectedItem() instanceof String s ? s : host.currentModality();
      refreshFilter(
          modalityFilter,
          registry.maps().stream()
              .flatMap(m -> m.modalities().stream())
              .collect(Collectors.toCollection(TreeSet::new)),
          previousModality);
      String previousCategory = categoryFilter.getSelectedItem() instanceof String s ? s : null;
      refreshFilter(categoryFilter, new TreeSet<>(registry.categories()), previousCategory);

      String modality =
          filterAll.equals(modalityFilter.getSelectedItem())
              ? null
              : (String) modalityFilter.getSelectedItem();
      String category =
          filterAll.equals(categoryFilter.getSelectedItem())
              ? null
              : (String) categoryFilter.getSelectedItem();
      ColorMapRegistry.Query query =
          new ColorMapRegistry.Query(
              modality,
              switch (dimensionFilter.getSelectedIndex()) {
                case 1 -> Boolean.FALSE;
                case 2 -> Boolean.TRUE;
                default -> null;
              },
              SOURCE_FILTERS.get(Math.max(0, sourceFilter.getSelectedIndex())),
              category,
              filterField.getText().trim(),
              true);
      listModel.clear();
      registry.query(query).forEach(listModel::addElement);
      paletteCombo.removeAllItems();
      registry.query(ColorMapRegistry.Query.ALL.withHidden(true)).forEach(paletteCombo::addItem);
      if (current != null) {
        mapList.setSelectedValue(current, true);
      }
    } finally {
      updating = false;
    }
  }

  private void refreshFilter(JComboBox<String> combo, Set<String> values, String previous) {
    combo.removeAllItems();
    combo.addItem(filterAll);
    values.forEach(combo::addItem);
    combo.setSelectedItem(previous != null && values.contains(previous) ? previous : filterAll);
  }

  private void refreshFromModel() {
    updating = true;
    try {
      ColorMapDomain d = current.domain();
      nameField.setText(current.name());
      typeCombo.setSelectedItem(current.type());
      modalityField.setText(String.join(", ", new TreeSet<>(current.modalities())));
      defaultCheck.setSelected(current.defaultForModality());
      kindCombo.setSelectedItem(d.kind());
      unitField.setText(d.unit() != null ? d.unit() : "");
      unitField.setEnabled(!d.isRelative());
      referenceField.setText(d.reference() != null ? d.reference() : "");
      referenceField.setVisible(d.kind() == DomainKind.PERCENT);
      double min = d.isRelative() ? current.firstPosition() : d.min();
      double max = d.isRelative() ? current.lastPosition() : d.max();
      minSpinner.setValue(min);
      maxSpinner.setValue(max);
      bitsCombo.setSelectedItem(BITS.contains(current.bits()) ? current.bits() : 8);
      spaceCombo.setSelectedItem(current.space());
      interpolationCombo.setSelectedItem(current.interpolation());
      belowSpinner.setValue(min);
      opaqueSpinner.setValue(min);
      boolean lighting = current.lighting() != null;
      volumeCheck.setSelected(lighting);
      shadeCheck.setEnabled(lighting);
      specularPowerSpinner.setEnabled(lighting);
      edgeSpinner.setEnabled(lighting);
      if (lighting) {
        shadeCheck.setSelected(current.lighting().shade());
        specularPowerSpinner.setValue((double) current.lighting().specularPower());
        GradientOpacity gradient = current.lighting().gradientOpacity();
        edgeSpinner.setValue(gradient == null ? 0.0 : (double) gradient.emphasis());
      }
      refreshHistogram(d);
      curve.setMap(current);
      if (lighting != lightingColumns) {
        lightingColumns = lighting;
        stopModel.fireTableStructureChanged();
        stopTable.setDefaultRenderer(Color.class, new ColorCellRenderer());
        stopTable.setDefaultEditor(Color.class, new ColorCellEditor());
      }
      stopModel.fireTableDataChanged();
      int selected = curve.getSelectedStop();
      if (selected >= 0 && selected < current.stops().size()) {
        stopTable.setRowSelectionInterval(selected, selected);
      }
      previewStrip.repaint();
      undoButton.setEnabled(!undo.isEmpty());
      redoButton.setEnabled(!redo.isEmpty());
      deleteButton.setEnabled(registry.isUserMap(current));
      // Picked values are physical: they only make sense on a map anchored to physical values.
      pickButton.setEnabled(host.canPickValue() && !d.isRelative());
      ColorMapRegistry.Origin origin = registry.origin(current);
      favoriteCheck.setEnabled(registry.findById(current.id()).isPresent());
      favoriteCheck.setSelected(registry.isFavorite(current));
      setTitle(
          Messages.getString("ColorMapEditor.title")
              + " - "
              + current.name()
              + (origin == null ? "" : " [" + origin.name().toLowerCase(Locale.ROOT) + "]"));
      statusLabel.setText(visibleFraction());
    } finally {
      updating = false;
    }
  }

  private void schedulePreview() {
    if (liveCheck.isSelected()) {
      previewTimer.restart();
    }
  }

  private void pushPreview() {
    if (liveCheck.isSelected() && current != null) {
      host.preview(ColorMapCompiler.toByteLut(current));
    }
  }

  // ───────────────────── curve listener ─────────────────────

  // A drag streams uncommitted maps; the commit records one undo step from the pre-drag map.
  @Override
  public void mapEdited(ColorMap map, boolean commit) {
    if (commit) {
      ColorMap before = dragStart != null ? dragStart : current;
      dragStart = null;
      if (!map.equals(before)) {
        undo.push(before);
        redo.clear();
      }
      current = map;
      refreshFromModel();
      schedulePreview();
    } else {
      if (dragStart == null) {
        dragStart = current;
      }
      current = map;
      stopModel.fireTableDataChanged();
      previewStrip.repaint();
      schedulePreview();
    }
  }

  @Override
  public void stopSelected(int index) {
    if (index >= 0 && index < stopModel.getRowCount()) {
      stopTable.setRowSelectionInterval(index, index);
    } else {
      stopTable.clearSelection();
    }
  }

  // ───────────────────── inner components ─────────────────────

  private class PreviewStrip extends JPanel {
    @Override
    protected void paintComponent(Graphics g) {
      super.paintComponent(g);
      if (current != null) {
        ColorMapCompiler.toByteLut(current)
            .getIcon(Math.max(1, getWidth()), Math.max(1, getHeight()))
            .paintIcon(this, g, 0, 0);
      }
    }
  }

  private class MapRenderer extends DefaultListCellRenderer {
    @Override
    public Component getListCellRendererComponent(
        JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
      super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
      if (value instanceof ColorMap map) {
        setText(registry.isFavorite(map) ? map.name() + FAVORITE_MARK : map.name());
        setIcon(
            registry
                .byteLut(map)
                .getIcon(GuiUtils.getScaleLength(64), GuiUtils.getScaleLength(STRIP_HEIGHT)));
      }
      return this;
    }
  }

  private class StopTableModel extends AbstractTableModel {
    private final String[] columns = {
      Messages.getString("ColorMapEditor.position"),
      Messages.getString("ColorMapEditor.color"),
      Messages.getString("ColorMapEditor.alpha"),
      Messages.getString("ColorMapEditor.group")
    };

    @Override
    public int getRowCount() {
      return current == null ? 0 : current.stops().size();
    }

    private final String[] materialColumns = {
      Messages.getString("ColorMapEditor.ambient"),
      Messages.getString("ColorMapEditor.diffuse"),
      Messages.getString("ColorMapEditor.specular")
    };

    @Override
    public int getColumnCount() {
      return columns.length + (current != null && current.lighting() != null ? 3 : 0);
    }

    @Override
    public String getColumnName(int column) {
      return column < columns.length ? columns[column] : materialColumns[column - columns.length];
    }

    @Override
    public Class<?> getColumnClass(int column) {
      return switch (column) {
        case 0 -> Double.class;
        case 1 -> Color.class;
        case 2 -> Float.class;
        case 3 -> String.class;
        default -> Float.class;
      };
    }

    @Override
    public boolean isCellEditable(int row, int column) {
      return true;
    }

    @Override
    public Object getValueAt(int row, int column) {
      ColorStop stop = current.stops().get(row);
      Material m = stop.material();
      return switch (column) {
        case 0 -> stop.position();
        case 1 -> stop.color();
        case 2 -> stop.alpha();
        case 3 -> stop.group();
        case 4 -> m == null ? null : m.ambient();
        case 5 -> m == null ? null : m.diffuse();
        default -> m == null ? null : m.specular();
      };
    }

    private static ColorStop withMaterialComponent(ColorStop stop, int column, Object value) {
      Material base = stop.material() != null ? stop.material() : Material.DEFAULT;
      float v = value instanceof Number n ? n.floatValue() : 0f;
      Material m =
          switch (column) {
            case 4 -> new Material(v, base.diffuse(), base.specular());
            case 5 -> new Material(base.ambient(), v, base.specular());
            default -> new Material(base.ambient(), base.diffuse(), v);
          };
      return stop.withMaterial(m);
    }

    @Override
    public void setValueAt(Object value, int row, int column) {
      ColorStop stop = current.stops().get(row);
      try {
        ColorStop edited =
            switch (column) {
              case 0 -> stop.withPosition(((Number) value).doubleValue());
              case 1 -> stop.withColor((Color) value);
              case 2 -> stop.withAlpha(value instanceof Number n ? n.floatValue() : null);
              case 3 -> stop.withGroup(value instanceof String s && !s.isBlank() ? s : null);
              default -> withMaterialComponent(stop, column, value);
            };
        ColorMap next = ColorMapEdits.withStop(current, row, edited);
        pushEdit(next);
        stopSelected(next.stops().indexOf(edited));
      } catch (IllegalArgumentException | ClassCastException e) {
        statusLabel.setText(e.getMessage());
      }
    }
  }

  private static class ColorCellRenderer extends DefaultTableCellRenderer {
    @Override
    public Component getTableCellRendererComponent(
        JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
      super.getTableCellRendererComponent(table, "", isSelected, hasFocus, row, column);
      setOpaque(true);
      if (value instanceof Color color) {
        setBackground(color);
        setBorder(BorderFactory.createLineBorder(Color.DARK_GRAY));
      }
      return this;
    }
  }

  private static class ColorCellEditor extends AbstractCellEditor implements TableCellEditor {
    private final JLabel swatch = new JLabel();
    private Color chosen;

    @Override
    public Object getCellEditorValue() {
      return chosen;
    }

    @Override
    public Component getTableCellEditorComponent(
        JTable table, Object value, boolean isSelected, int row, int column) {
      chosen = value instanceof Color color ? color : Color.WHITE;
      swatch.setOpaque(true);
      swatch.setBackground(chosen);
      SwingUtilities.invokeLater(
          () -> {
            Color picked =
                JColorChooser.showDialog(table, Messages.getString("ColorMapEditor.color"), chosen);
            if (picked != null) {
              chosen = picked;
              stopCellEditing();
            } else {
              cancelCellEditing();
            }
          });
      return swatch;
    }
  }
}
