/*
 * Copyright (c) 2023 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.viewer3d.vr;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsEnvironment;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Path2D;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import javax.swing.AbstractAction;
import javax.swing.AbstractCellEditor;
import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JColorChooser;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
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
import javax.swing.ListCellRenderer;
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
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.util.ResourceUtil;
import org.weasis.core.api.util.ResourceUtil.ActionIcon;
import org.weasis.core.util.StringUtil;
import org.weasis.dicom.codec.display.Modality;
import org.weasis.dicom.viewer3d.ActionVol;
import org.weasis.dicom.viewer3d.EventManager;
import org.weasis.dicom.viewer3d.Messages;
import org.weasis.dicom.viewer3d.vr.lut.PresetGroup;
import org.weasis.dicom.viewer3d.vr.lut.PresetPoint;

/**
 * Dialog for creating, editing, and deleting custom volume LUT presets. Built-in presets are
 * read-only; to modify them the user must copy them first. The transfer-function panel offers
 * interactive editing (drag to move points, double-click to add or recolor) with a live 3D preview.
 */
public class VolumeLutEditorDialog extends JDialog {
  private static final Logger LOGGER = LoggerFactory.getLogger(VolumeLutEditorDialog.class);

  /** Debounce delay before a heavy 3D rebuild is pushed to the view during interactive editing. */
  private static final int PREVIEW_DELAY_MS = 90;

  /**
   * Modalities offered in the combo: {@link Modality#DEFAULT} (applies to all) plus the image
   * modalities that can be reconstructed into a 3D volume. A read-only preset carrying an off-list
   * modality is added on the fly by {@link #ensureModalityPresent(String)}.
   */
  private static final List<Modality> RELEVANT_MODALITIES =
      List.of(
          Modality.DEFAULT,
          Modality.CT,
          Modality.MR,
          Modality.PT,
          Modality.NM,
          Modality.US,
          Modality.XA,
          Modality.OT);

  private final View3d view3d;
  private final Preset originalPreset;

  // Left panel – filter bar + preset list
  private final JTextField searchField = new JTextField();
  private final JComboBox<String> modalityFilter = new JComboBox<>();
  private final JComboBox<String> editableFilter = new JComboBox<>();
  private final String filterAll = Messages.getString("filter.all");
  private final String filterEditable = Messages.getString("filter.editable");
  private final String filterBuiltin = Messages.getString("filter.builtin");
  private boolean updatingFilters;
  private final DefaultListModel<Preset> presetListModel = new DefaultListModel<>();
  private final JList<Preset> presetList = new JList<>(presetListModel);

  // Right panel – editor fields
  private final JTextField nameField = new JTextField(20);
  private final JComboBox<String> modalityCombo = new JComboBox<>();
  private final JCheckBox shadeCheck = new JCheckBox(Messages.getString("preset.shade"));
  private final JCheckBox defaultCheck = new JCheckBox(Messages.getString("preset.default"));
  private final JSpinner specularPowerSpinner =
      new JSpinner(new SpinnerNumberModel(10.0, 1.0, 100.0, 1.0));

  // Group management
  private final DefaultListModel<PresetGroup> groupListModel = new DefaultListModel<>();
  private final JList<PresetGroup> groupList = new JList<>(groupListModel);

  // Point table (precise numeric entry)
  private PointTableModel pointTableModel;
  private JTable pointTable;
  private JButton removePointBtn;

  // Interactive transfer function editor + final LUT strip
  private final TransferFunctionPanel tfPanel = new TransferFunctionPanel();
  private final LutPreviewPanel lutPreview = new LutPreviewPanel();

  // Coalesces interactive edits into a throttled 3D refresh
  private final Timer previewTimer;

  public VolumeLutEditorDialog(View3d view3d) {
    super(
        SwingUtilities.getWindowAncestor(view3d),
        Messages.getString("edit.volume.lut"),
        ModalityType.MODELESS);
    this.view3d = Objects.requireNonNull(view3d);
    this.originalPreset = view3d.getVolumePreset();
    this.previewTimer = new Timer(PREVIEW_DELAY_MS, e -> doLivePreview());
    previewTimer.setRepeats(false);
    setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
    setIconImage(ResourceUtil.getIcon(ActionIcon.LUT).getImage());
    initComponents();
    loadPresetList();
    pack();
    fitToUsableScreen(1280, 760);
  }

  /**
   * Clamps the preferred minimum size and the packed size to the usable screen area (excluding
   * taskbar/menu-bar insets) so the dialog never opens larger than the screen.
   */
  private void fitToUsableScreen(int preferredMinWidth, int preferredMinHeight) {
    Rectangle avail = getUsableScreenBounds();
    setMinimumSize(
        new Dimension(
            Math.min(preferredMinWidth, avail.width), Math.min(preferredMinHeight, avail.height)));
    setSize(Math.min(getWidth(), avail.width), Math.min(getHeight(), avail.height));
  }

  private Rectangle getUsableScreenBounds() {
    GraphicsConfiguration gc = getGraphicsConfiguration();
    if (gc == null) {
      Window owner = getOwner();
      gc = owner != null ? owner.getGraphicsConfiguration() : null;
    }
    if (gc == null) {
      gc =
          GraphicsEnvironment.getLocalGraphicsEnvironment()
              .getDefaultScreenDevice()
              .getDefaultConfiguration();
    }
    Rectangle b = gc.getBounds();
    Insets in = getToolkit().getScreenInsets(gc);
    return new Rectangle(
        b.x + in.left, b.y + in.top, b.width - in.left - in.right, b.height - in.top - in.bottom);
  }

  @Override
  public void dispose() {
    previewTimer.stop();
    super.dispose();
  }

  // ───────────────────── UI construction ─────────────────────

  private void initComponents() {
    JPanel content =
        new JPanel(new MigLayout("insets 10lp, fill", "[grow]", "[grow][]")); // NON-NLS

    JSplitPane split =
        new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, buildPresetListPanel(), buildEditorPanel());
    split.setDividerLocation(360);
    content.add(split, "grow, wrap"); // NON-NLS
    content.add(buildBottomPanel(), "growx"); // NON-NLS

    setContentPane(content);
  }

  private JPanel buildPresetListPanel() {
    JPanel panel = new JPanel(new MigLayout("insets 5lp, fill", "[grow]", "[][grow][]")); // NON-NLS
    panel.setBorder(GuiUtils.getTitledBorder(Messages.getString("volume.lut.selection")));

    panel.add(buildFilterPanel(), "growx, wrap"); // NON-NLS

    presetList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    presetList.setCellRenderer(new PresetListRenderer());
    presetList.addListSelectionListener(
        e -> {
          if (!e.getValueIsAdjusting()) {
            onPresetSelected();
          }
        });
    panel.add(new JScrollPane(presetList), "grow, wrap"); // NON-NLS

    JButton newBtn = new JButton(Messages.getString("new.preset"));
    JButton copyBtn = new JButton(Messages.getString("copy.preset"));
    JButton deleteBtn = new JButton(Messages.getString("delete.preset"));

    newBtn.addActionListener(e -> onNewPreset());
    copyBtn.addActionListener(e -> onCopyPreset());
    deleteBtn.addActionListener(e -> onDeletePreset());

    JPanel btnPanel = new JPanel(new MigLayout("insets 0", "[][][]", "[]")); // NON-NLS
    btnPanel.add(newBtn);
    btnPanel.add(copyBtn);
    btnPanel.add(deleteBtn);
    panel.add(btnPanel, "center"); // NON-NLS
    return panel;
  }

  /** Builds the filter bar: a name search field plus modality and editable-state combos. */
  private JPanel buildFilterPanel() {
    JPanel filter = new JPanel(new MigLayout("insets 0, fillx", "[grow][grow]", "[][]")); // NON-NLS

    searchField.putClientProperty("JTextField.placeholderText", Messages.getString("search"));
    searchField.setToolTipText(Messages.getString("search"));

    modalityFilter.setToolTipText(Messages.getString("preset.modality"));
    editableFilter.setToolTipText(Messages.getString("filter.editable"));
    editableFilter.addItem(filterAll);
    editableFilter.addItem(filterEditable);
    editableFilter.addItem(filterBuiltin);

    // Attach listeners after items are populated to avoid spurious early filtering
    searchField
        .getDocument()
        .addDocumentListener(
            new DocumentListener() {
              @Override
              public void insertUpdate(DocumentEvent e) {
                onFilterChanged();
              }

              @Override
              public void removeUpdate(DocumentEvent e) {
                onFilterChanged();
              }

              @Override
              public void changedUpdate(DocumentEvent e) {
                onFilterChanged();
              }
            });
    modalityFilter.addActionListener(e -> onFilterChanged());
    editableFilter.addActionListener(e -> onFilterChanged());

    filter.add(searchField, "span 2, growx, wrap"); // NON-NLS
    filter.add(modalityFilter, "growx"); // NON-NLS
    filter.add(editableFilter, "growx"); // NON-NLS
    return filter;
  }

  private JPanel buildEditorPanel() {
    // 4-column grid: label | field | label | field
    JPanel editor =
        new JPanel(
            new MigLayout( // NON-NLS
                "insets 5lp, fill", // NON-NLS
                "[][grow][][grow]", // NON-NLS
                "[][]4lp[grow]4lp[40lp]")); // NON-NLS
    editor.setBorder(GuiUtils.getEmptyBorder(0, 5, 0, 0));

    // Populate modality combo once with the volume-relevant image modalities
    for (Modality m : RELEVANT_MODALITIES) {
      modalityCombo.addItem(m.name());
    }

    // Row 0 – name + modality
    editor.add(new JLabel(Messages.getString("preset.name")));
    editor.add(nameField, "growx"); // NON-NLS
    editor.add(new JLabel(Messages.getString("preset.modality")));
    editor.add(modalityCombo, "growx, wrap"); // NON-NLS

    // Row 1 – checkboxes + specular power
    editor.add(shadeCheck);
    editor.add(defaultCheck);
    editor.add(new JLabel(Messages.getString("preset.specular.power")));
    editor.add(specularPowerSpinner, "growx, wrap"); // NON-NLS

    // Row 2 – groups + interactive graph + point table (fills remaining height)
    editor.add(buildGroupPointPanel(), "span 4, grow, wrap"); // NON-NLS

    // Row 3 – final LUT color strip (fixed height)
    editor.add(lutPreview, "span 4, growx, h 40lp"); // NON-NLS

    return editor;
  }

  /** Builds the group list, interactive transfer-function graph, and the numeric point table. */
  private JPanel buildGroupPointPanel() {
    // 2-column grid: [fixed group list] | [graph over table]
    JPanel panel =
        new JPanel(new MigLayout("insets 5lp, fill", "[120lp][grow]", "[grow][]")); // NON-NLS
    panel.setBorder(GuiUtils.getTitledBorder(Messages.getString("add.group")));

    // ── Group list (left column) ──
    groupList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    groupList.setCellRenderer(
        new DefaultListCellRenderer() {
          @Override
          public Component getListCellRendererComponent(
              JList<?> list, Object value, int index, boolean sel, boolean focus) {
            super.getListCellRendererComponent(list, value, index, sel, focus);
            if (value instanceof PresetGroup g) {
              setText(g.getName());
            }
            return this;
          }
        });
    groupList.addListSelectionListener(
        e -> {
          if (!e.getValueIsAdjusting()) {
            onGroupSelected();
          }
        });
    panel.add(new JScrollPane(groupList), "grow"); // NON-NLS

    // ── Graph (top) + point table (bottom) stacked in the right column ──
    pointTableModel = new PointTableModel();
    pointTable = new JTable(pointTableModel);
    pointTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    pointTable.setRowHeight(GuiUtils.getScaleLength(22));
    pointTable.getTableHeader().setReorderingAllowed(false);
    pointTable
        .getSelectionModel()
        .addListSelectionListener(
            e -> {
              if (!e.getValueIsAdjusting()) {
                tfPanel.repaint();
                updateRemovePointEnabled();
              }
            });
    // Color column – render as filled swatch, edit via JColorChooser
    pointTable.getColumnModel().getColumn(2).setCellRenderer(new ColorCellRenderer());
    pointTable.getColumnModel().getColumn(2).setCellEditor(new ColorCellEditor());
    pointTable.getColumnModel().getColumn(2).setPreferredWidth(GuiUtils.getScaleLength(60));

    JScrollPane tableScroll = new JScrollPane(pointTable);
    tableScroll.setPreferredSize(
        new Dimension(GuiUtils.getScaleLength(360), GuiUtils.getScaleLength(120)));

    JPanel rightPanel =
        new JPanel(new MigLayout("insets 0, fill", "[grow]", "[grow][120lp]")); // NON-NLS
    rightPanel.add(tfPanel, "grow, wrap"); // NON-NLS
    rightPanel.add(tableScroll, "grow"); // NON-NLS
    panel.add(rightPanel, "grow, wrap"); // NON-NLS

    // ── Group action buttons (left column, row 1) ──
    JButton addGroupBtn = new JButton("+");
    addGroupBtn.setToolTipText(Messages.getString("add.group"));
    JButton renameGroupBtn = new JButton("✎");
    renameGroupBtn.setToolTipText(Messages.getString("rename.group"));
    JButton removeGroupBtn = new JButton("−");
    removeGroupBtn.setToolTipText(Messages.getString("remove.group"));
    addGroupBtn.addActionListener(e -> onAddGroup());
    renameGroupBtn.addActionListener(e -> onRenameGroup());
    removeGroupBtn.addActionListener(e -> onRemoveGroup());

    JPanel groupBtnPanel = new JPanel(new MigLayout("insets 0", "[][][]", "[]")); // NON-NLS
    groupBtnPanel.add(addGroupBtn);
    groupBtnPanel.add(renameGroupBtn);
    groupBtnPanel.add(removeGroupBtn);
    panel.add(groupBtnPanel, "center"); // NON-NLS

    // ── Point action buttons (right column, row 1) ──
    JButton addPointBtn = new JButton(Messages.getString("add.point"));
    removePointBtn = new JButton(Messages.getString("remove.point"));
    addPointBtn.addActionListener(e -> onAddPoint());
    removePointBtn.addActionListener(e -> onRemovePoint());
    updateRemovePointEnabled();

    JPanel pointBtnPanel = new JPanel(new MigLayout("insets 0", "[][]", "[]")); // NON-NLS
    pointBtnPanel.add(addPointBtn);
    pointBtnPanel.add(removePointBtn);
    panel.add(pointBtnPanel); // NON-NLS

    return panel;
  }

  private JPanel buildBottomPanel() {
    JButton helpBtn = GuiUtils.createHelpButton("dicom-3d-viewer/#lut-editor"); // NON-NLS
    JButton applyBtn = new JButton(Messages.getString("apply.preview"));
    JButton saveBtn = new JButton(Messages.getString("save"));
    JButton cancelBtn = new JButton(Messages.getString("cancel"));

    applyBtn.addActionListener(e -> applyPreview());
    saveBtn.addActionListener(e -> onSaveAndClose());
    cancelBtn.addActionListener(e -> onCancel());

    // "push" right-aligns Help, Save and Cancel, leaving Apply Preview on the left
    JPanel panel = new JPanel(new MigLayout("insets 5lp 0 5lp 0", "[]push[][][]", "[]")); // NON-NLS
    panel.add(applyBtn);
    panel.add(helpBtn);
    panel.add(saveBtn);
    panel.add(cancelBtn);
    return panel;
  }

  // ───────────────────── Data loading ─────────────────────

  private void loadPresetList() {
    rebuildModalityFilter();
    applyPresetFilter();
    // Select the currently active preset if it passes the filters
    if (originalPreset != null && presetListModel.contains(originalPreset)) {
      presetList.setSelectedValue(originalPreset, true);
    }
  }

  private void onFilterChanged() {
    if (!updatingFilters) {
      applyPresetFilter();
    }
  }

  /** Rebuilds the preset list from all presets, keeping only those matching the active filters. */
  private void applyPresetFilter() {
    Preset previous = presetList.getSelectedValue();
    String query = searchField.getText().trim().toLowerCase();
    Object modality = modalityFilter.getSelectedItem();
    Object editable = editableFilter.getSelectedItem();
    boolean anyModality = modality == null || filterAll.equals(modality);

    presetListModel.clear();
    for (Preset p : Preset.getAllPresets()) {
      if (!query.isEmpty() && !p.getName().toLowerCase().contains(query)) {
        continue;
      }
      if (!anyModality && !p.getModality().name().equals(modality)) {
        continue;
      }
      if (filterEditable.equals(editable) && !p.isCustom()) {
        continue;
      }
      if (filterBuiltin.equals(editable) && p.isCustom()) {
        continue;
      }
      presetListModel.addElement(p);
    }

    if (previous != null && presetListModel.contains(previous)) {
      presetList.setSelectedValue(previous, true);
    } else if (!presetListModel.isEmpty()) {
      presetList.setSelectedIndex(0);
    } else {
      onPresetSelected(); // Nothing matches: disable the editor
    }
  }

  /** Repopulates the modality filter with the modalities present among all presets. */
  private void rebuildModalityFilter() {
    updatingFilters = true;
    Object previous = modalityFilter.getSelectedItem();
    modalityFilter.removeAllItems();
    modalityFilter.addItem(filterAll);
    Preset.getAllPresets().stream()
        .map(p -> p.getModality().name())
        .distinct()
        .sorted()
        .forEach(modalityFilter::addItem);
    modalityFilter.setSelectedItem(previous == null ? filterAll : previous);
    if (modalityFilter.getSelectedIndex() < 0) {
      modalityFilter.setSelectedItem(filterAll);
    }
    updatingFilters = false;
  }

  private void resetFilters() {
    updatingFilters = true;
    searchField.setText("");
    modalityFilter.setSelectedItem(filterAll);
    editableFilter.setSelectedItem(filterAll);
    updatingFilters = false;
  }

  private void onPresetSelected() {
    Preset selected = presetList.getSelectedValue();
    if (selected == null) {
      setEditorEnabled(false);
      return;
    }
    boolean editable = selected.isCustom();
    setEditorEnabled(editable);

    nameField.setText(selected.getName());
    ensureModalityPresent(selected.getModality().name());
    modalityCombo.setSelectedItem(selected.getModality().name());
    shadeCheck.setSelected(selected.isShade());
    defaultCheck.setSelected(selected.isDefaultElement());
    specularPowerSpinner.setValue((double) selected.getSpecularPower());

    groupListModel.clear();
    for (PresetGroup g : selected.getGroups()) {
      groupListModel.addElement(g);
    }
    if (!groupListModel.isEmpty()) {
      groupList.setSelectedIndex(0);
    }

    updateLutPreview(selected);
  }

  /** Adds a modality name to the combo if the curated list does not already contain it. */
  private void ensureModalityPresent(String modalityName) {
    for (int i = 0; i < modalityCombo.getItemCount(); i++) {
      if (modalityCombo.getItemAt(i).equals(modalityName)) {
        return;
      }
    }
    modalityCombo.addItem(modalityName);
  }

  private void onGroupSelected() {
    PresetGroup group = groupList.getSelectedValue();
    if (group == null) {
      pointTableModel.setPoints(new ArrayList<>());
      updateGraphDomain();
      tfPanel.repaint();
      return;
    }
    List<PresetPoint> pts = new ArrayList<>(Arrays.asList(group.getPoints()));
    sortPoints(pts);
    pointTableModel.setPoints(pts);
    updateGraphDomain();
    tfPanel.repaint();
    updateRemovePointEnabled();
  }

  private void setEditorEnabled(boolean enabled) {
    nameField.setEnabled(enabled);
    modalityCombo.setEnabled(enabled);
    shadeCheck.setEnabled(enabled);
    defaultCheck.setEnabled(enabled);
    specularPowerSpinner.setEnabled(enabled);
    groupList.setEnabled(enabled);
    pointTable.setEnabled(enabled);
    tfPanel.setEditable(enabled);
    updateRemovePointEnabled();
  }

  /** Enables Remove Point only for an editable preset with a selected, removable point. */
  private void updateRemovePointEnabled() {
    if (removePointBtn != null) {
      removePointBtn.setEnabled(
          pointTable.isEnabled()
              && pointTable.getSelectedRow() >= 0
              && pointTableModel.getRowCount() > 2);
    }
  }

  // ───────────────────── Preset actions ─────────────────────

  private void onNewPreset() {
    PresetPoint defaultStart = new PresetPoint(0, 0f, 0f, 0f, 0f, 0.2f, 0.1f, 0.9f);
    PresetPoint defaultEnd = new PresetPoint(512, 1f, 1f, 1f, 1f, 0.2f, 0.9f, 0.2f);
    PresetGroup group = new PresetGroup("Default", new PresetPoint[] {defaultStart, defaultEnd});
    List<PresetGroup> groups = new ArrayList<>();
    groups.add(group);

    Preset newPreset = new Preset("New Preset", "ALL", false, true, 10f, groups, true);
    Preset.customPresets.add(newPreset);
    Preset.saveCustomPresets();
    refreshPresetListAndCombo(newPreset);
  }

  private void onCopyPreset() {
    Preset selected = presetList.getSelectedValue();
    if (selected == null) {
      return;
    }
    // Deep-copy groups
    List<PresetGroup> copiedGroups = selected.getGroups().stream().map(PresetGroup::copy).toList();

    Preset copy =
        new Preset(
            selected.getName() + " (" + Messages.getString("copy.preset") + ")",
            selected.getModality().name(),
            false,
            selected.isShade(),
            selected.getSpecularPower(),
            new ArrayList<>(copiedGroups),
            true);

    Preset.customPresets.add(copy);
    Preset.saveCustomPresets();
    refreshPresetListAndCombo(copy);
  }

  private void onDeletePreset() {
    Preset selected = presetList.getSelectedValue();
    if (selected == null || !selected.isCustom()) {
      return;
    }
    int result =
        JOptionPane.showConfirmDialog(
            this,
            String.format(Messages.getString("confirm.delete"), selected.getName()),
            Messages.getString("confirm.delete.title"),
            JOptionPane.YES_NO_OPTION);
    if (result != JOptionPane.YES_OPTION) {
      return;
    }
    Preset.customPresets.remove(selected);
    Preset.saveCustomPresets();

    // If the deleted preset was the active one on the view, revert to a default
    if (selected == view3d.getVolumePreset()) {
      Preset def = Preset.getDefaultPreset(view3d.getVolTexture().getModality());
      if (def != null) {
        view3d.setVolumePreset(def);
      }
    }
    refreshPresetListAndCombo(null);
  }

  // ───────────────────── Group actions ─────────────────────

  private void onAddGroup() {
    Preset selected = presetList.getSelectedValue();
    if (selected == null || !selected.isCustom()) {
      return;
    }
    String name =
        JOptionPane.showInputDialog(
            this,
            Messages.getString("enter.group.name") + StringUtil.COLON,
            Messages.getString("add.group"),
            JOptionPane.PLAIN_MESSAGE);
    if (name == null || name.isBlank()) {
      return;
    }
    // Get the last point intensity to compute a new range
    int startIntensity = 0;
    if (!groupListModel.isEmpty()) {
      PresetGroup lastGroup = groupListModel.getElementAt(groupListModel.size() - 1);
      PresetPoint[] pts = lastGroup.getPoints();
      if (pts.length > 0) {
        startIntensity = pts[pts.length - 1].getIntensity() + 1;
      }
    }
    PresetPoint p1 = new PresetPoint(startIntensity, 0f, 0f, 0f, 0f, 0.2f, 0.1f, 0.9f);
    PresetPoint p2 = new PresetPoint(startIntensity + 100, 1f, 1f, 1f, 1f, 0.2f, 0.9f, 0.2f);
    PresetGroup newGroup = new PresetGroup(name, new PresetPoint[] {p1, p2});
    groupListModel.addElement(newGroup);
    groupList.setSelectedValue(newGroup, true);
    schedulePreview();
  }

  private void onRenameGroup() {
    PresetGroup group = groupList.getSelectedValue();
    Preset selected = presetList.getSelectedValue();
    if (group == null || selected == null || !selected.isCustom()) {
      return;
    }
    String name =
        JOptionPane.showInputDialog(
            this, Messages.getString("enter.group.name") + StringUtil.COLON, group.getName());
    if (name == null || name.isBlank()) {
      return;
    }
    group.setName(name);
    groupList.repaint();
  }

  private void onRemoveGroup() {
    PresetGroup group = groupList.getSelectedValue();
    Preset selected = presetList.getSelectedValue();
    if (group == null || selected == null || !selected.isCustom()) {
      return;
    }
    if (groupListModel.size() <= 1) {
      return; // Must keep at least one group
    }
    groupListModel.removeElement(group);
    if (!groupListModel.isEmpty()) {
      groupList.setSelectedIndex(0);
    }
    schedulePreview();
  }

  // ───────────────────── Point actions ─────────────────────

  private void onAddPoint() {
    Preset selected = presetList.getSelectedValue();
    if (selected == null || !selected.isCustom() || groupList.getSelectedValue() == null) {
      return;
    }
    List<PresetPoint> pts = pointTableModel.getPoints();
    int lastIntensity = pts.isEmpty() ? 0 : pts.getLast().getIntensity() + 100;
    Color c = pts.isEmpty() ? Color.WHITE : sampleColor(pts, lastIntensity);
    pts.add(
        new PresetPoint(
            lastIntensity,
            0.5f,
            c.getRed() / 255f,
            c.getGreen() / 255f,
            c.getBlue() / 255f,
            0.2f,
            0.9f,
            0.2f));
    refreshAfterStructuralChange();
  }

  private void onRemovePoint() {
    Preset selected = presetList.getSelectedValue();
    if (selected == null || !selected.isCustom()) {
      return;
    }
    int row = pointTable.getSelectedRow();
    if (row < 0) {
      return;
    }
    List<PresetPoint> pts = pointTableModel.getPoints();
    if (pts.size() <= 2) {
      return; // Keep at least two points so the curve stays meaningful
    }
    pts.remove(row);
    refreshAfterStructuralChange();
  }

  /** Writes the current table data back to the selected group. */
  private void syncPointsToGroup() {
    PresetGroup group = groupList.getSelectedValue();
    if (group != null) {
      group.setPoints(pointTableModel.getPoints().toArray(new PresetPoint[0]));
    }
  }

  private static void sortPoints(List<PresetPoint> pts) {
    pts.sort(Comparator.comparingInt(PresetPoint::getIntensity));
  }

  private void updateGraphDomain() {
    double dMin = 0;
    double dMax = 4095;
    DicomVolTexture vt = view3d.getVolTexture();
    if (vt != null) {
      dMin = vt.getLevelMin();
      dMax = vt.getLevelMax();
    }
    List<PresetPoint> pts = pointTableModel.getPoints();
    if (!pts.isEmpty()) {
      dMin = Math.min(dMin, pts.getFirst().getIntensity());
      dMax = Math.max(dMax, pts.getLast().getIntensity());
    }
    double pad = Math.max(1, (dMax - dMin) * 0.02);
    tfPanel.setDomain(dMin - pad, dMax + pad);
  }

  /**
   * Re-sorts, rescales the graph, mirrors the change to the table, and schedules a live preview.
   */
  private void refreshAfterStructuralChange() {
    sortPoints(pointTableModel.getPoints());
    syncPointsToGroup();
    updateGraphDomain(); // A new/edited intensity may fall outside the current axis range
    pointTableModel.fireTableDataChanged();
    tfPanel.repaint();
    lutPreview.repaint();
    updateRemovePointEnabled();
    schedulePreview();
  }

  /** Lightweight refresh used during a drag: no structural change, keeps table selection. */
  private void refreshAfterDrag(int row) {
    syncPointsToGroup();
    if (row >= 0 && row < pointTableModel.getRowCount()) {
      pointTableModel.fireTableRowsUpdated(row, row);
    }
    lutPreview.repaint();
    schedulePreview();
  }

  // ───────────────────── Preview & Save ─────────────────────

  private void schedulePreview() {
    Preset selected = presetList.getSelectedValue();
    if (selected != null && selected.isCustom()) {
      previewTimer.restart();
    }
  }

  private void doLivePreview() {
    Preset selected = presetList.getSelectedValue();
    if (selected == null || !selected.isCustom()) {
      return;
    }
    Preset preview = buildPreviewPreset();
    if (preview != null) {
      view3d.setVolumePreset(preview);
      updateLutPreview(preview);
    }
  }

  private void applyPreview() {
    Preset selected = presetList.getSelectedValue();
    if (selected == null) {
      return;
    }
    Preset previewPreset = selected.isCustom() ? buildPreviewPreset() : selected;
    if (previewPreset != null) {
      view3d.setVolumePreset(previewPreset);
      updateLutPreview(previewPreset);
    }
  }

  /** Reloads the presets from disk (discarding unsaved edits), restores the view, and closes. */
  private void onCancel() {
    Preset.reloadCustomPresets();
    Preset restore = resolveRestorePreset();
    if (restore != null) {
      view3d.setVolumePreset(restore);
    }
    EventManager eventManager = EventManager.getInstance();
    eventManager
        .getAction(ActionVol.VOL_PRESET)
        .ifPresent(
            a -> {
              a.setDataListWithoutTriggerAction(Preset.getAllPresets().toArray(new Preset[0]));
              if (restore != null) {
                a.setSelectedItemWithoutTriggerAction(restore);
              }
            });
    dispose();
  }

  /**
   * Resolves which preset to reapply after a reload: the original built-in as-is, the reloaded
   * equivalent of the original custom preset, or the modality default when it no longer exists.
   */
  private Preset resolveRestorePreset() {
    Modality modality =
        view3d.getVolTexture() != null ? view3d.getVolTexture().getModality() : null;
    if (originalPreset == null) {
      return Preset.getDefaultPreset(modality);
    }
    if (!originalPreset.isCustom()) {
      return originalPreset; // Built-in preset objects are stable across a reload
    }
    for (Preset p : Preset.customPresets) {
      if (p.getName().equals(originalPreset.getName())
          && p.getModality() == originalPreset.getModality()) {
        return p;
      }
    }
    Preset def = Preset.getDefaultPreset(modality);
    return def != null ? def : originalPreset;
  }

  /** Persists the current custom preset if editable, then closes the dialog. */
  private void onSaveAndClose() {
    Preset selected = presetList.getSelectedValue();
    if (selected != null && selected.isCustom() && !saveCurrentPreset()) {
      return; // Validation failed – keep the dialog open
    }
    dispose();
  }

  /**
   * Writes the current custom preset to disk. Returns {@code false} (keeping the dialog open) when
   * the name is missing or the preset cannot be built.
   */
  private boolean saveCurrentPreset() {
    Preset selected = presetList.getSelectedValue();
    if (selected == null || !selected.isCustom()) {
      return false;
    }
    String name = nameField.getText().trim();
    if (name.isEmpty()) {
      JOptionPane.showMessageDialog(
          this,
          Messages.getString("preset.name.required"),
          Messages.getString("edit.volume.lut"),
          JOptionPane.WARNING_MESSAGE);
      return false;
    }
    Preset newPreset = buildPresetFromEditor();
    if (newPreset == null) {
      return false;
    }
    int idx = Preset.customPresets.indexOf(selected);
    if (idx >= 0) {
      Preset.customPresets.set(idx, newPreset);
    } else {
      Preset.customPresets.add(newPreset);
    }
    Preset.saveCustomPresets();

    view3d.setVolumePreset(newPreset);
    refreshPresetListAndCombo(newPreset);
    return true;
  }

  /** Builds a preset from the editor form, surfacing a dialog on failure (used by Save). */
  private Preset buildPresetFromEditor() {
    Preset preset = buildPreviewPreset();
    if (preset == null) {
      JOptionPane.showMessageDialog(
          this,
          Messages.getString("invalid.preset"),
          Messages.getString("edit.volume.lut"),
          JOptionPane.ERROR_MESSAGE);
    }
    return preset;
  }

  /** Builds a preset from the editor form, returning {@code null} silently on failure. */
  private Preset buildPreviewPreset() {
    try {
      syncPointsToGroup();
      String name = nameField.getText().trim();
      if (name.isEmpty()) {
        name = "Custom";
      }
      String modality = (String) modalityCombo.getSelectedItem();
      boolean shade = shadeCheck.isSelected();
      boolean defaultEl = defaultCheck.isSelected();
      float specPow = ((Number) specularPowerSpinner.getValue()).floatValue();

      List<PresetGroup> groups = new ArrayList<>();
      for (int i = 0; i < groupListModel.size(); i++) {
        groups.add(groupListModel.getElementAt(i).copy());
      }
      return new Preset(name, modality, defaultEl, shade, specPow, groups, true);
    } catch (Exception e) {
      LOGGER.debug("Cannot build preset from editor", e);
      return null;
    }
  }

  private void refreshPresetListAndCombo(Preset selectAfter) {
    rebuildModalityFilter();
    applyPresetFilter();
    // If the active filters hide the target preset, clear them so it stays visible
    if (selectAfter != null && !presetListModel.contains(selectAfter)) {
      resetFilters();
      applyPresetFilter();
    }
    if (selectAfter != null && presetListModel.contains(selectAfter)) {
      presetList.setSelectedValue(selectAfter, true);
    }
    // Refresh the global combo model so the toolbar stays in sync
    EventManager eventManager = EventManager.getInstance();
    eventManager
        .getAction(ActionVol.VOL_PRESET)
        .ifPresent(
            a -> a.setDataListWithoutTriggerAction(Preset.getAllPresets().toArray(new Preset[0])));
  }

  private void updateLutPreview(Preset preset) {
    lutPreview.setPreset(preset);
    lutPreview.repaint();
  }

  // ───────────────────── Color sampling ─────────────────────

  private static float cf(Float v) {
    return v == null ? 0f : v;
  }

  private static Color colorOf(PresetPoint p) {
    return new Color(
        Math.clamp(cf(p.getRed()), 0f, 1f),
        Math.clamp(cf(p.getGreen()), 0f, 1f),
        Math.clamp(cf(p.getBlue()), 0f, 1f));
  }

  /** Linearly interpolates the RGB color at the given intensity across the sorted point list. */
  private static Color sampleColor(List<PresetPoint> pts, double intensity) {
    if (pts.isEmpty()) {
      return Color.GRAY;
    }
    if (intensity <= pts.getFirst().getIntensity()) {
      return colorOf(pts.getFirst());
    }
    if (intensity >= pts.getLast().getIntensity()) {
      return colorOf(pts.getLast());
    }
    for (int i = 0; i < pts.size() - 1; i++) {
      PresetPoint a = pts.get(i);
      PresetPoint b = pts.get(i + 1);
      if (intensity >= a.getIntensity() && intensity <= b.getIntensity()) {
        double n = (double) b.getIntensity() - a.getIntensity();
        double f = n <= 0 ? 0 : (intensity - a.getIntensity()) / n;
        return new Color(
            Math.clamp((float) (cf(a.getRed()) + (cf(b.getRed()) - cf(a.getRed())) * f), 0f, 1f),
            Math.clamp(
                (float) (cf(a.getGreen()) + (cf(b.getGreen()) - cf(a.getGreen())) * f), 0f, 1f),
            Math.clamp(
                (float) (cf(a.getBlue()) + (cf(b.getBlue()) - cf(a.getBlue())) * f), 0f, 1f));
      }
    }
    return colorOf(pts.getLast());
  }

  // ═══════════════════════════════════════════════════════════
  // Interactive transfer-function panel
  // ═══════════════════════════════════════════════════════════

  /**
   * Draws the opacity transfer function of the selected group over the intensity axis, with a color
   * gradient bar at the base. Points can be dragged, added (double-click), recolored (double-click
   * a node), and removed (Delete / right-click). Edits update the 3D view live.
   */
  private class TransferFunctionPanel extends JPanel {
    private static final int PAD_LEFT = 40;
    private static final int PAD_RIGHT = 12;
    private static final int PAD_TOP = 12;
    private static final int PAD_BOTTOM = 24;
    private static final int COLOR_BAR_H = 14;
    private static final int NODE_R = 5;
    private static final int HIT_R = 7;

    private double domainMin = 0;
    private double domainMax = 4095;
    private boolean editable = true;
    private int hoverIndex = -1;
    private int dragIndex = -1;

    TransferFunctionPanel() {
      setOpaque(true);
      setBorder(GuiUtils.getTitledBorder(Messages.getString("transfer.function")));
      setToolTipText(Messages.getString("tf.help"));
      setPreferredSize(new Dimension(GuiUtils.getScaleLength(420), GuiUtils.getScaleLength(220)));

      MouseAdapter ma =
          new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
              onPressed(e);
            }

            @Override
            public void mouseDragged(MouseEvent e) {
              onDragged(e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
              if (dragIndex >= 0) {
                dragIndex = -1;
                schedulePreview();
              }
            }

            @Override
            public void mouseMoved(MouseEvent e) {
              int idx = hitTest(e.getPoint());
              if (idx != hoverIndex) {
                hoverIndex = idx;
                repaint();
              }
            }

            @Override
            public void mouseExited(MouseEvent e) {
              if (hoverIndex != -1) {
                hoverIndex = -1;
                repaint();
              }
            }
          };
      addMouseListener(ma);
      addMouseMotionListener(ma);

      getInputMap(JComponent.WHEN_FOCUSED)
          .put(KeyStroke.getKeyStroke("DELETE"), "removePoint"); // NON-NLS
      getInputMap(JComponent.WHEN_FOCUSED)
          .put(KeyStroke.getKeyStroke("BACK_SPACE"), "removePoint"); // NON-NLS
      getActionMap()
          .put(
              "removePoint", // NON-NLS
              new AbstractAction() {
                @Override
                public void actionPerformed(ActionEvent e) {
                  int sel = pointTable.getSelectedRow();
                  if (editable && sel >= 0) {
                    onRemovePoint();
                  }
                }
              });
    }

    void setDomain(double min, double max) {
      this.domainMin = min;
      this.domainMax = max <= min ? min + 1 : max;
    }

    void setEditable(boolean editable) {
      this.editable = editable;
    }

    private List<PresetPoint> pts() {
      return pointTableModel.getPoints();
    }

    // ── coordinate mapping ──
    private int plotX() {
      return PAD_LEFT;
    }

    private int plotY() {
      return PAD_TOP;
    }

    private int plotW() {
      return Math.max(1, getWidth() - PAD_LEFT - PAD_RIGHT);
    }

    private int plotH() {
      return Math.max(1, getHeight() - PAD_TOP - PAD_BOTTOM - COLOR_BAR_H);
    }

    private int xFor(double intensity) {
      double f = (intensity - domainMin) / (domainMax - domainMin);
      return (int) Math.round(plotX() + f * plotW());
    }

    private int yFor(double opacity) {
      return (int) Math.round(plotY() + (1 - Math.clamp(opacity, 0.0, 1.0)) * plotH());
    }

    private double intensityForX(int x) {
      double f = (x - plotX()) / (double) plotW();
      return domainMin + f * (domainMax - domainMin);
    }

    private double opacityForY(int y) {
      return Math.clamp(1 - (y - plotY()) / (double) plotH(), 0.0, 1.0);
    }

    private int hitTest(Point p) {
      List<PresetPoint> pts = pts();
      for (int i = 0; i < pts.size(); i++) {
        PresetPoint pt = pts.get(i);
        int nx = xFor(pt.getIntensity());
        int ny = yFor(pt.getOpacity());
        if (Math.abs(p.x - nx) <= HIT_R && Math.abs(p.y - ny) <= HIT_R) {
          return i;
        }
      }
      return -1;
    }

    // ── interaction ──
    private void onPressed(MouseEvent e) {
      requestFocusInWindow();
      if (!editable) {
        return;
      }
      int idx = hitTest(e.getPoint());
      if (idx >= 0) {
        pointTable.setRowSelectionInterval(idx, idx);
        if (SwingUtilities.isRightMouseButton(e)) {
          if (pts().size() > 2) {
            onRemovePoint();
          }
          return;
        }
        if (e.getClickCount() == 2) {
          editColor(idx);
          return;
        }
        dragIndex = idx;
      } else if (e.getClickCount() == 2 && !SwingUtilities.isRightMouseButton(e)) {
        addPointAt(e.getPoint());
      }
    }

    private void onDragged(MouseEvent e) {
      if (!editable || dragIndex < 0) {
        return;
      }
      List<PresetPoint> pts = pts();
      PresetPoint p = pts.get(dragIndex);
      // Clamp intensity strictly between neighbors so the list stays ordered without reindexing
      int lower =
          dragIndex > 0 ? pts.get(dragIndex - 1).getIntensity() + 1 : (int) Math.floor(domainMin);
      int upper =
          dragIndex < pts.size() - 1
              ? pts.get(dragIndex + 1).getIntensity() - 1
              : (int) Math.ceil(domainMax);
      if (upper < lower) {
        upper = lower;
      }
      int newIntensity = (int) Math.round(Math.clamp(intensityForX(e.getX()), lower, upper));
      p.setIntensity(newIntensity);
      p.setOpacity((float) opacityForY(e.getY()));
      repaint();
      refreshAfterDrag(dragIndex);
    }

    private void addPointAt(Point pt) {
      List<PresetPoint> pts = pts();
      int intensity = (int) Math.round(Math.clamp(intensityForX(pt.x), domainMin, domainMax));
      float opacity = (float) opacityForY(pt.y);
      Color c = sampleColor(pts, intensity);
      PresetPoint added =
          new PresetPoint(
              intensity,
              opacity,
              c.getRed() / 255f,
              c.getGreen() / 255f,
              c.getBlue() / 255f,
              0.2f,
              0.9f,
              0.2f);
      pts.add(added);
      sortPoints(pts);
      refreshAfterStructuralChange();
      int newRow = pts.indexOf(added);
      if (newRow >= 0) {
        pointTable.setRowSelectionInterval(newRow, newRow);
      }
    }

    private void editColor(int idx) {
      PresetPoint p = pts().get(idx);
      Color chosen = JColorChooser.showDialog(this, Messages.getString("color"), colorOf(p));
      if (chosen != null) {
        p.setRed(chosen.getRed() / 255f);
        p.setGreen(chosen.getGreen() / 255f);
        p.setBlue(chosen.getBlue() / 255f);
        refreshAfterStructuralChange();
      }
    }

    // ── painting ──
    @Override
    protected void paintComponent(Graphics g) {
      super.paintComponent(g);
      Graphics2D g2 = (Graphics2D) g.create();
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

      int px = plotX();
      int py = plotY();
      int pw = plotW();
      int ph = plotH();
      List<PresetPoint> pts = pts();

      paintGrid(g2, px, py, pw, ph);
      paintColorBar(g2, px, py + ph, pw, pts);
      if (!pts.isEmpty()) {
        paintCurve(g2, pts, py + ph);
        paintNodes(g2, pts);
      }
      paintAxisLabels(g2, px, py, pw, ph);
      g2.dispose();
    }

    private void paintGrid(Graphics2D g2, int px, int py, int pw, int ph) {
      g2.setColor(getBackground().darker());
      g2.setStroke(new BasicStroke(1f));
      for (int i = 0; i <= 4; i++) {
        int y = py + i * ph / 4;
        g2.drawLine(px, y, px + pw, y);
      }
    }

    private void paintColorBar(Graphics2D g2, int px, int barTop, int pw, List<PresetPoint> pts) {
      for (int i = 0; i < pw; i++) {
        double intensity = intensityForX(px + i);
        g2.setColor(pts.isEmpty() ? Color.DARK_GRAY : sampleColor(pts, intensity));
        g2.drawLine(px + i, barTop, px + i, barTop + COLOR_BAR_H);
      }
      g2.setColor(getForeground().darker());
      g2.drawRect(px, barTop, pw, COLOR_BAR_H);
    }

    private void paintCurve(Graphics2D g2, List<PresetPoint> pts, int baseline) {
      Path2D fill = new Path2D.Float();
      Path2D line = new Path2D.Float();
      for (int i = 0; i < pts.size(); i++) {
        int x = xFor(pts.get(i).getIntensity());
        int y = yFor(pts.get(i).getOpacity());
        if (i == 0) {
          fill.moveTo(x, baseline);
          fill.lineTo(x, y);
          line.moveTo(x, y);
        } else {
          fill.lineTo(x, y);
          line.lineTo(x, y);
        }
        if (i == pts.size() - 1) {
          fill.lineTo(x, baseline);
          fill.closePath();
        }
      }
      Color accent = getForeground();
      g2.setColor(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 48));
      g2.fill(fill);
      g2.setColor(accent);
      g2.setStroke(new BasicStroke(1.8f));
      g2.draw(line);
    }

    private void paintNodes(Graphics2D g2, List<PresetPoint> pts) {
      int selected = pointTable.getSelectedRow();
      for (int i = 0; i < pts.size(); i++) {
        PresetPoint p = pts.get(i);
        int x = xFor(p.getIntensity());
        int y = yFor(p.getOpacity());
        int r = (i == hoverIndex || i == selected) ? NODE_R + 2 : NODE_R;
        g2.setColor(colorOf(p));
        g2.fillOval(x - r, y - r, r * 2, r * 2);
        g2.setStroke(new BasicStroke(i == selected ? 2f : 1f));
        g2.setColor(i == selected ? getForeground() : Color.DARK_GRAY);
        g2.drawOval(x - r, y - r, r * 2, r * 2);
      }
    }

    private void paintAxisLabels(Graphics2D g2, int px, int py, int pw, int ph) {
      g2.setColor(getForeground());
      Font small = getFont().deriveFont(Font.PLAIN, GuiUtils.getScaleLength(10));
      g2.setFont(small);
      int ascent = g2.getFontMetrics().getAscent();
      // Opacity axis (left)
      g2.drawString("1", px - GuiUtils.getScaleLength(14), py + ascent); // NON-NLS
      g2.drawString("0", px - GuiUtils.getScaleLength(14), py + ph); // NON-NLS
      // Intensity axis (bottom)
      String minLbl = Integer.toString((int) Math.round(domainMin));
      String maxLbl = Integer.toString((int) Math.round(domainMax));
      int baseY = py + ph + COLOR_BAR_H + ascent + GuiUtils.getScaleLength(2);
      g2.drawString(minLbl, px, baseY);
      int maxW = g2.getFontMetrics().stringWidth(maxLbl);
      g2.drawString(maxLbl, px + pw - maxW, baseY);
    }
  }

  // ═══════════════════════════════════════════════════════════
  // Preset list / preview renderers
  // ═══════════════════════════════════════════════════════════

  /**
   * Renders a preset in the list with two lines: the preset name on the first line and a full-width
   * LUT color strip on the second line.
   */
  private static class PresetListRenderer implements ListCellRenderer<Preset> {
    private final JPanel cell =
        new JPanel(new MigLayout("insets 4lp, fill", "[grow]", "[][5lp]")); // NON-NLS
    private final JLabel nameLabel = new JLabel();
    private final LutStripPanel stripPanel = new LutStripPanel();

    PresetListRenderer() {
      nameLabel.setOpaque(false);
      cell.add(nameLabel, "growx, wrap"); // NON-NLS
      cell.add(stripPanel, "growx, h 5lp"); // NON-NLS
    }

    @Override
    public Component getListCellRendererComponent(
        JList<? extends Preset> list,
        Preset value,
        int index,
        boolean isSelected,
        boolean cellHasFocus) {
      if (value != null) {
        String label = value.toString();
        if (value.isCustom()) {
          label = "★ " + label;
        }
        nameLabel.setText(label);
        nameLabel.setFont(list.getFont());
        stripPanel.setPreset(value);
      }
      Color bg = isSelected ? list.getSelectionBackground() : list.getBackground();
      Color fg = isSelected ? list.getSelectionForeground() : list.getForeground();
      cell.setBackground(bg);
      cell.setOpaque(true);
      nameLabel.setForeground(fg);
      return cell;
    }

    private static class LutStripPanel extends JPanel {
      private Preset preset;

      LutStripPanel() {
        setOpaque(false);
      }

      void setPreset(Preset p) {
        this.preset = p;
      }

      @Override
      protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (preset == null) {
          return;
        }
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setStroke(new BasicStroke(1.0f));
        final int w = getWidth();
        final int h = getHeight();
        // Provide a dynamically sized icon so drawLutIcon scales to the available cell width
        Icon scaledIcon =
            new Icon() {
              @Override
              public void paintIcon(Component c, Graphics g, int x, int y) {}

              @Override
              public int getIconWidth() {
                return w;
              }

              @Override
              public int getIconHeight() {
                return h;
              }
            };
        preset.drawLutIcon(g2, scaledIcon, 0, 0, 1);
        g2.dispose();
      }
    }
  }

  /**
   * Draws the full LUT color gradient across the whole panel width, with a control-point marker
   * (contrast guide line + handle) at every point so the user can see where each point lands in the
   * final LUT construction.
   */
  private static class LutPreviewPanel extends JPanel {
    private static final int BORDER = 2;
    private Preset preset;

    void setPreset(Preset preset) {
      this.preset = preset;
    }

    @Override
    protected void paintComponent(Graphics g) {
      super.paintComponent(g);
      if (preset == null) {
        return;
      }
      Graphics2D g2 = (Graphics2D) g.create();
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      final int w = getWidth();
      final int h = getHeight();
      // Dynamically sized icon so the gradient spans the whole panel width
      Icon icon =
          new Icon() {
            @Override
            public void paintIcon(Component c, Graphics g, int x, int y) {}

            @Override
            public int getIconWidth() {
              return w;
            }

            @Override
            public int getIconHeight() {
              return h;
            }
          };
      g2.setStroke(new BasicStroke(1f));
      preset.drawLutIcon(g2, icon, 0, 0, BORDER);
      drawPointMarkers(g2, w, h);
      g2.dispose();
    }

    private void drawPointMarkers(Graphics2D g2, int w, int h) {
      int iconWidth = w - 2 * BORDER;
      int lutWidth = preset.getColorMax() - preset.getColorMin();
      if (iconWidth <= 0 || lutWidth <= 0) {
        return;
      }
      int colorMin = preset.getColorMin();
      int top = BORDER;
      int bottom = h - BORDER;
      int handle = GuiUtils.getScaleLength(5);

      List<PresetPoint> points = new ArrayList<>();
      preset.getGroups().forEach(gr -> points.addAll(Arrays.asList(gr.getPoints())));

      for (PresetPoint p : points) {
        int px =
            BORDER
                + (int) Math.round((p.getIntensity() - colorMin) * (double) iconWidth / lutWidth);
        px = Math.clamp(px, BORDER, BORDER + iconWidth);
        Color contrast = colorOf(p);
        contrast =
            (0.299 * contrast.getRed() + 0.587 * contrast.getGreen() + 0.114 * contrast.getBlue())
                    < 128
                ? Color.WHITE
                : Color.BLACK;
        // Vertical guide line
        g2.setColor(contrast);
        g2.setStroke(new BasicStroke(1f));
        g2.drawLine(px, top + handle, px, bottom);
        // Downward triangle handle filled with the point color
        Path2D tri = new Path2D.Float();
        tri.moveTo((float) px - handle, top);
        tri.lineTo((float) px + handle, top);
        tri.lineTo(px, (float) top + handle);
        tri.closePath();
        g2.setColor(colorOf(p));
        g2.fill(tri);
        g2.setColor(contrast);
        g2.draw(tri);
      }
    }
  }

  // ═══════════════════════════════════════════════════════════
  // Point table model
  // ═══════════════════════════════════════════════════════════

  /** Table model for editing the selected group's PresetPoint list. */
  private class PointTableModel extends AbstractTableModel {
    private final String[] columns = {
      Messages.getString("intensity"),
      Messages.getString("opacity"),
      Messages.getString("color"),
      Messages.getString("specular"),
      Messages.getString("ambient"),
      Messages.getString("diffuse")
    };

    private List<PresetPoint> points = new ArrayList<>();

    void setPoints(List<PresetPoint> points) {
      this.points = points;
      fireTableDataChanged();
    }

    List<PresetPoint> getPoints() {
      return points;
    }

    @Override
    public int getRowCount() {
      return points.size();
    }

    @Override
    public int getColumnCount() {
      return columns.length;
    }

    @Override
    public String getColumnName(int col) {
      return columns[col];
    }

    @Override
    public Class<?> getColumnClass(int col) {
      return switch (col) {
        case 0 -> Integer.class;
        case 2 -> Color.class;
        default -> Float.class;
      };
    }

    @Override
    public boolean isCellEditable(int row, int col) {
      return pointTable.isEnabled();
    }

    @Override
    public Object getValueAt(int row, int col) {
      PresetPoint p = points.get(row);
      return switch (col) {
        case 0 -> p.getIntensity();
        case 1 -> p.getOpacity();
        case 2 -> colorOf(p);
        case 3 -> p.getSpecular();
        case 4 -> p.getAmbient();
        case 5 -> p.getDiffuse();
        default -> null;
      };
    }

    @Override
    public void setValueAt(Object value, int row, int col) {
      if (value == null) {
        return;
      }
      PresetPoint p = points.get(row);
      try {
        switch (col) {
          case 0 -> p.setIntensity(((Number) value).intValue());
          case 1 -> p.setOpacity(Math.clamp(((Number) value).floatValue(), 0f, 1f));
          case 2 -> {
            Color c = (Color) value;
            p.setRed(c.getRed() / 255f);
            p.setGreen(c.getGreen() / 255f);
            p.setBlue(c.getBlue() / 255f);
          }
          case 3 -> p.setSpecular(Math.clamp(((Number) value).floatValue(), 0f, 1f));
          case 4 -> p.setAmbient(Math.clamp(((Number) value).floatValue(), 0f, 1f));
          case 5 -> p.setDiffuse(Math.clamp(((Number) value).floatValue(), 0f, 1f));
        }
      } catch (Exception e) {
        return; // Ignore invalid input
      }
      // Intensity edits may reorder the list; refresh structurally, otherwise a lightweight update
      if (col == 0) {
        refreshAfterStructuralChange();
      } else {
        fireTableRowsUpdated(row, row);
        tfPanel.repaint();
        lutPreview.repaint();
        schedulePreview();
      }
    }
  }

  // ═══════════════════════════════════════════════════════════
  // Color cell renderer / editor
  // ═══════════════════════════════════════════════════════════

  /** Renders a Color value as a filled color swatch in the table cell. */
  private static class ColorCellRenderer extends DefaultTableCellRenderer {
    @Override
    public Component getTableCellRendererComponent(
        JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
      super.getTableCellRendererComponent(table, "", isSelected, hasFocus, row, column);
      if (value instanceof Color c) {
        setBackground(c);
        setBorder(BorderFactory.createLineBorder(c.darker(), 1));
      }
      setOpaque(true);
      return this;
    }
  }

  /** Opens a {@link JColorChooser} to pick the point's RGB color when cell editing starts. */
  private static class ColorCellEditor extends AbstractCellEditor implements TableCellEditor {
    private Color currentColor = Color.WHITE;
    private final JPanel swatch = new JPanel();

    @Override
    public Object getCellEditorValue() {
      return currentColor;
    }

    @Override
    public Component getTableCellEditorComponent(
        JTable table, Object value, boolean isSelected, int row, int column) {
      currentColor = value instanceof Color c ? c : Color.WHITE;
      swatch.setBackground(currentColor);
      SwingUtilities.invokeLater(
          () -> {
            Color chosen =
                JColorChooser.showDialog(table, Messages.getString("color"), currentColor);
            if (chosen != null) {
              currentColor = chosen;
              stopCellEditing();
            } else {
              fireEditingCanceled();
            }
          });
      return swatch;
    }
  }
}
