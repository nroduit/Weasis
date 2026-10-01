/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.viewer2d.pref;

import com.formdev.flatlaf.FlatClientProperties;
import java.awt.CardLayout;
import java.awt.Component;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.filechooser.FileNameExtensionFilter;
import net.miginfocom.swing.MigLayout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.gui.util.AbstractItemDialogPage;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.ui.pref.PreferenceDialog;
import org.weasis.core.util.StringUtil;
import org.weasis.dicom.codec.display.WindowPreset;
import org.weasis.dicom.codec.display.WindowPreset.Domain;
import org.weasis.dicom.codec.display.WindowPreset.DomainKind;
import org.weasis.dicom.codec.display.WindowPreset.When;
import org.weasis.dicom.codec.display.WindowPresetRegistry;
import org.weasis.dicom.codec.display.WindowPresetRegistry.Origin;
import org.weasis.dicom.viewer2d.Messages;
import org.weasis.opencv.op.lut.LutShape;

/**
 * Edits the window/level presets: the built-in and site ones are shown read-only, the user ones can
 * be created, changed, deleted, imported and exported.
 */
public class WindowPresetPrefView extends AbstractItemDialogPage {
  private static final Logger LOGGER = LoggerFactory.getLogger(WindowPresetPrefView.class);

  private static final String[] KEYS = {"", "3", "4", "5", "6", "7", "8", "9"}; // NON-NLS
  private static final String SHRINKABLE = "wmin 0"; // NON-NLS
  private static final String SECTION = "ins 5lp, fillx, wrap 4"; // NON-NLS
  private static final String SECTION_COLUMNS =
      "[right][grow 50,fill][right][grow 50,fill]"; // NON-NLS
  private static final String GAP = "gapleft 10lp"; // NON-NLS

  private final WindowPresetRegistry registry;
  private final Map<String, WindowPreset> pending = new LinkedHashMap<>();

  private final JComboBox<WindowPreset> presetCombo = new JComboBox<>();
  private final JTextField nameField = new JTextField(14);
  private final JTextField modalitiesField = new JTextField(10);
  private final JTextField windowField = new JTextField(6);
  private final JTextField levelField = new JTextField(6);
  private final JTextField unitField = new JTextField(6);
  private final JTextField bodyPartField = new JTextField(10);
  private final JTextField preferredField = new JTextField(10);
  private final JComboBox<LutShape> shapeCombo =
      new JComboBox<>(
          LutShape.getAllPredefined().stream()
              .sorted((a, b) -> a.toString().compareToIgnoreCase(b.toString()))
              .toArray(LutShape[]::new));
  private final JComboBox<String> keyCombo = new JComboBox<>(KEYS);
  private final JComboBox<DomainKind> domainCombo = new JComboBox<>(DomainKind.values());
  private final JComboBox<String> referenceCombo =
      new JComboBox<>(new String[] {WindowPreset.REFERENCE_IMAGE, WindowPreset.REFERENCE_SERIES});
  private final JSpinner bitsSpinner =
      new JSpinner(new SpinnerNumberModel(When.DEFAULT_MIN_BITS_STORED, 1, 32, 1));
  private final JCheckBox rescaleCheck =
      new JCheckBox(Messages.getString("WindowPresetPrefView.rescale"));
  private final JCheckBox defaultCheck =
      new JCheckBox(Messages.getString("WindowPresetPrefView.default"));
  private final List<JLabel> fieldLabels = new ArrayList<>();
  private final List<JLabel> firstColumnLabels = new ArrayList<>();
  private final JLabel rangeLabel = new JLabel();
  private final CardLayout rangeCards = new CardLayout();
  private final JPanel rangeSlot = new JPanel(rangeCards);
  private final JLabel readOnlyLabel = new JLabel();
  private final JLabel idLabel = new JLabel();
  private final JButton modalitiesPicker =
      CodePicker.modalities(modalitiesField, this::commitEditor);
  private final JButton bodyPartPicker = CodePicker.bodyParts(bodyPartField, this::commitEditor);
  private final JButton preferredPicker = CodePicker.bodyParts(preferredField, this::commitEditor);
  private final JMenuItem deleteItem =
      new JMenuItem(Messages.getString("WindowPresetPrefView.delete"));

  private boolean filling;

  public WindowPresetPrefView() {
    this(WindowPresetRegistry.getInstance());
  }

  WindowPresetPrefView(WindowPresetRegistry registry) {
    super(Messages.getString("WindowPresetPrefView.title"), 502);
    this.registry = registry;
    jbInit();
    fillPresets(null);
    getProperties().setProperty(PreferenceDialog.KEY_SHOW_APPLY, Boolean.TRUE.toString());
    getProperties().setProperty(PreferenceDialog.KEY_HELP, "lut/#window-level-presets");
  }

  private void jbInit() {
    setLayout(new MigLayout("ins 0, fillx, wrap 1, hidemode 3", "[0:pref,grow,fill]")); // NON-NLS
    add(buildPresetBar(), SHRINKABLE);
    readOnlyLabel.putClientProperty(FlatClientProperties.STYLE_CLASS, "small"); // NON-NLS
    add(readOnlyLabel, "gaptop 3lp"); // NON-NLS
    add(buildIdentity(), SHRINKABLE);
    add(buildWindowLevel(), SHRINKABLE);
    add(buildConditions(), SHRINKABLE);
    idLabel.putClientProperty(FlatClientProperties.STYLE_CLASS, "small"); // NON-NLS
    idLabel.setEnabled(false);
    add(idLabel, "gaptop 5lp"); // NON-NLS
    alignFirstColumns();

    presetCombo.addActionListener(e -> showSelected());
    domainCombo.addActionListener(
        e -> {
          updateDomainState();
          commitEditor();
        });
    installTooltips();
    List.of(
            nameField,
            modalitiesField,
            windowField,
            levelField,
            unitField,
            bodyPartField,
            preferredField)
        .forEach(f -> f.getDocument().addDocumentListener(CodePicker.onEdit(this::commitEditor)));
    List.of(shapeCombo, keyCombo, referenceCombo)
        .forEach(c -> c.addActionListener(e -> commitEditor()));
    List.of(rescaleCheck, defaultCheck).forEach(c -> c.addActionListener(e -> commitEditor()));
    bitsSpinner.addChangeListener(e -> commitEditor());
  }

  private JPanel buildPresetBar() {
    presetCombo.setRenderer(
        new DefaultListCellRenderer() {
          @Override
          public Component getListCellRendererComponent(
              JList<?> list, Object value, int index, boolean selected, boolean focus) {
            super.getListCellRendererComponent(list, value, index, selected, focus);
            if (value instanceof WindowPreset preset) {
              setText(displayName(current(preset), registry.origin(preset.id())));
            }
            return this;
          }
        });

    JButton newButton = new JButton(Messages.getString("WindowPresetPrefView.new"));
    newButton.setToolTipText(Messages.getString("WindowPresetPrefView.new.tip"));
    newButton.addActionListener(e -> createPreset());
    JButton duplicateButton = new JButton(Messages.getString("WindowPresetPrefView.duplicate"));
    duplicateButton.setToolTipText(Messages.getString("WindowPresetPrefView.duplicate.tip"));
    duplicateButton.addActionListener(e -> duplicatePreset());

    JPopupMenu popup = new JPopupMenu();
    deleteItem.addActionListener(e -> deletePreset());
    popup.add(deleteItem);
    popup.addSeparator();
    JMenuItem importItem =
        new JMenuItem(
            Messages.getString("WindowPresetPrefView.import") + StringUtil.Suffix.THREE_PTS);
    importItem.addActionListener(e -> importPresets());
    popup.add(importItem);
    JMenuItem exportItem =
        new JMenuItem(
            Messages.getString("WindowPresetPrefView.export") + StringUtil.Suffix.THREE_PTS);
    exportItem.addActionListener(e -> exportPresets());
    popup.add(exportItem);
    JButton moreButton = new JButton("\u22EF"); // NON-NLS
    moreButton.setToolTipText(Messages.getString("WindowPresetPrefView.more.tip"));
    moreButton.addActionListener(e -> popup.show(moreButton, 0, moreButton.getHeight()));

    // The combo takes what the buttons leave and shortens a long name
    JPanel bar = new JPanel(new MigLayout("ins 0, fillx", "[][grow,fill][][][]")); // NON-NLS
    bar.add(label("WindowPresetPrefView.preset", presetCombo));
    bar.add(presetCombo, "wmin 80lp"); // NON-NLS
    bar.add(newButton);
    bar.add(duplicateButton);
    bar.add(moreButton);
    return bar;
  }

  private JPanel buildIdentity() {
    JPanel panel = section("WindowPresetPrefView.section.preset");
    panel.add(firstLabel("WindowPresetPrefView.name", nameField));
    panel.add(nameField, SHRINKABLE);
    panel.add(label("WindowPresetPrefView.key", keyCombo), GAP);
    panel.add(keyCombo);
    panel.add(firstLabel("WindowPresetPrefView.modality", modalitiesField));
    panel.add(withPicker(modalitiesField, modalitiesPicker), "span 3, " + SHRINKABLE); // NON-NLS
    return panel;
  }

  // One slot shows the unit of absolute values or the range of percent values
  private JPanel buildWindowLevel() {
    rangeSlot.add(unitField, DomainKind.ABSOLUTE.name());
    rangeSlot.add(referenceCombo, DomainKind.PERCENT.name());
    rangeLabel.setLabelFor(rangeSlot);
    JPanel panel = section("WindowPresetPrefView.section.wl");
    panel.add(firstLabel("WindowPresetPrefView.domain", domainCombo));
    panel.add(domainCombo);
    panel.add(rangeLabel, GAP);
    panel.add(rangeSlot, SHRINKABLE);
    panel.add(firstLabel("WindowPresetPrefView.window", windowField));
    panel.add(windowField, SHRINKABLE);
    panel.add(label("WindowPresetPrefView.level", levelField), GAP);
    panel.add(levelField, SHRINKABLE);
    panel.add(firstLabel("WindowPresetPrefView.shape", shapeCombo));
    panel.add(shapeCombo, SHRINKABLE);
    return panel;
  }

  private JPanel buildConditions() {
    JPanel panel = section("WindowPresetPrefView.section.when");
    panel.add(firstLabel("WindowPresetPrefView.preferred", preferredField));
    panel.add(withPicker(preferredField, preferredPicker), "span 3, " + SHRINKABLE); // NON-NLS
    panel.add(firstLabel("WindowPresetPrefView.bodyPart", bodyPartField));
    panel.add(withPicker(bodyPartField, bodyPartPicker), "span 3, " + SHRINKABLE); // NON-NLS
    panel.add(firstLabel("WindowPresetPrefView.minBits", bitsSpinner));
    panel.add(bitsSpinner, "growx 0, wmin 50lp"); // NON-NLS
    panel.add(rescaleCheck, "span 2, alignx left, " + GAP); // NON-NLS
    panel.add(defaultCheck, "skip 1, span 3, alignx left"); // NON-NLS
    return panel;
  }

  private void installTooltips() {
    tip("WindowPresetPrefView.name.tip", nameField);
    tip("WindowPresetPrefView.modality.tip", modalitiesField);
    tip("WindowPresetPrefView.key.tip", keyCombo);
    tip("WindowPresetPrefView.domain.tip", domainCombo);
    tip("WindowPresetPrefView.unit.tip", unitField);
    tip("WindowPresetPrefView.reference.tip", referenceCombo);
    tip("WindowPresetPrefView.window.tip", windowField);
    tip("WindowPresetPrefView.level.tip", levelField);
    tip("WindowPresetPrefView.shape.tip", shapeCombo);
    tip("WindowPresetPrefView.bodyPart.tip", bodyPartField);
    tip("WindowPresetPrefView.preferred.tip", preferredField);
    tip("WindowPresetPrefView.minBits.tip", bitsSpinner);
    tip("WindowPresetPrefView.rescale.tip", rescaleCheck);
    tip("WindowPresetPrefView.default.tip", defaultCheck);
    tip("WindowPresetPrefView.preset.tip", presetCombo);
    for (JLabel l : fieldLabels) {
      l.setToolTipText(((JComponent) l.getLabelFor()).getToolTipText());
    }

    domainCombo.setRenderer(
        textRenderer(
            v ->
                Messages.getString(
                    v == DomainKind.PERCENT
                        ? "WindowPresetPrefView.domain.percent"
                        : "WindowPresetPrefView.domain.absolute")));
    referenceCombo.setRenderer(
        textRenderer(
            v ->
                Messages.getString(
                    WindowPreset.REFERENCE_SERIES.equals(v)
                        ? "WindowPresetPrefView.reference.series"
                        : "WindowPresetPrefView.reference.image")));
    keyCombo.setRenderer(
        textRenderer(
            v ->
                StringUtil.hasText((String) v)
                    ? (String) v
                    : Messages.getString("WindowPresetPrefView.key.none")));
  }

  private static void tip(String key, JComponent field) {
    field.setToolTipText(Messages.getString(key));
  }

  private static DefaultListCellRenderer textRenderer(Function<Object, String> text) {
    return new DefaultListCellRenderer() {
      @Override
      public Component getListCellRendererComponent(
          JList<?> list, Object value, int index, boolean selected, boolean focus) {
        super.getListCellRendererComponent(list, value, index, selected, focus);
        setText(text.apply(value));
        return this;
      }
    };
  }

  private static JPanel section(String titleKey) {
    JPanel panel = new JPanel(new MigLayout(SECTION, SECTION_COLUMNS));
    panel.setBorder(GuiUtils.getTitledBorder(Messages.getString(titleKey)));
    return panel;
  }

  private static JPanel withPicker(JTextField field, JButton picker) {
    JPanel panel = new JPanel(new MigLayout("ins 0, fillx", "[grow,fill][]")); // NON-NLS
    panel.add(field, SHRINKABLE);
    panel.add(picker);
    return panel;
  }

  private JLabel firstLabel(String key, JComponent field) {
    JLabel label = label(key, field);
    firstColumnLabels.add(label);
    return label;
  }

  // The sections are separate panels: one width for their first column aligns them
  private void alignFirstColumns() {
    int width =
        firstColumnLabels.stream().mapToInt(l -> l.getPreferredSize().width).max().orElse(0);
    for (JLabel label : firstColumnLabels) {
      label.setPreferredSize(new java.awt.Dimension(width, label.getPreferredSize().height));
      label.setHorizontalAlignment(JLabel.TRAILING);
    }
  }

  private JLabel label(String key, JComponent field) {
    JLabel label = new JLabel(Messages.getString(key) + StringUtil.COLON);
    label.setLabelFor(field);
    fieldLabels.add(label);
    return label;
  }

  static String displayName(WindowPreset preset, Origin origin) {
    String modalities =
        preset.modalities().isEmpty()
            ? Messages.getString("WindowPresetPrefView.all")
            : String.join(", ", new TreeSet<>(preset.modalities()));
    String text = preset.name() + " \u2014 " + modalities; // NON-NLS
    return origin == Origin.USER ? text : text + " (" + originName(origin) + ")";
  }

  private static String originName(Origin origin) {
    return Messages.getString(
        "WindowPresetPrefView.origin." + origin.name().toLowerCase(Locale.ROOT));
  }

  /** The preset with its pending edits. */
  private WindowPreset current(WindowPreset preset) {
    return preset == null ? null : pending.getOrDefault(preset.id(), preset);
  }

  /** Fills the combo from the registry and the pending edits, keeping the selection. */
  void fillPresets(String selectedId) {
    filling = true;
    try {
      List<WindowPreset> presets = new ArrayList<>(registry.presets());
      pending.values().stream()
          .filter(p -> presets.stream().noneMatch(e -> e.id().equals(p.id())))
          .forEach(presets::add);
      presetCombo.setModel(new DefaultComboBoxModel<>(presets.toArray(WindowPreset[]::new)));
      presets.stream()
          .filter(p -> p.id().equals(selectedId))
          .findFirst()
          .ifPresent(presetCombo::setSelectedItem);
    } finally {
      filling = false;
    }
    showSelected();
  }

  private void showSelected() {
    if (filling) {
      return;
    }
    WindowPreset preset = current(selected());
    boolean editable = preset != null && registry.origin(preset.id()) == Origin.USER;
    filling = true;
    try {
      show(preset);
    } finally {
      filling = false;
    }
    enableEditor(editable);
    readOnlyLabel.setVisible(preset != null && !editable);
    if (preset != null && !editable) {
      readOnlyLabel.setText(
          Messages.getString("WindowPresetPrefView.readonly")
              .formatted(originName(registry.origin(preset.id()))));
    }
    deleteItem.setEnabled(editable);
    validateEditor();
  }

  /** Selects the preset of that id, for the tests. */
  void selectPreset(String id) {
    for (int i = 0; i < presetCombo.getItemCount(); i++) {
      if (presetCombo.getItemAt(i).id().equals(id)) {
        presetCombo.setSelectedIndex(i);
        return;
      }
    }
  }

  /** Whether the shown preset can be edited (a user preset). */
  boolean editorEnabled() {
    return nameField.isEnabled();
  }

  /** Types values in the editor, for the tests. */
  void typeName(String name) {
    nameField.setText(name);
  }

  /** The selected preset with its pending edits. */
  WindowPreset selected() {
    return current((WindowPreset) presetCombo.getSelectedItem());
  }

  /** Writes the preset into the fields. */
  void show(WindowPreset preset) {
    if (preset == null) {
      List.of(
              nameField,
              modalitiesField,
              windowField,
              levelField,
              unitField,
              bodyPartField,
              preferredField)
          .forEach(f -> f.setText(""));
      idLabel.setText("");
      return;
    }
    nameField.setText(preset.name());
    modalitiesField.setText(String.join(", ", new TreeSet<>(preset.modalities())));
    windowField.setText(format(preset.window()));
    levelField.setText(format(preset.level()));
    unitField.setText(preset.domain().unit() == null ? "" : preset.domain().unit());
    domainCombo.setSelectedItem(preset.domain().kind());
    referenceCombo.setSelectedItem(
        preset.domain().reference() == null
            ? WindowPreset.REFERENCE_IMAGE
            : preset.domain().reference());
    shapeCombo.setSelectedItem(preset.shape());
    keyCombo.setSelectedItem(preset.key() == null ? "" : String.valueOf(preset.key()));
    bitsSpinner.setValue(preset.when().minBitsStored());
    rescaleCheck.setSelected(preset.when().requiresRescale());
    bodyPartField.setText(String.join(", ", new TreeSet<>(preset.when().bodyParts())));
    preferredField.setText(String.join(", ", new TreeSet<>(preset.when().preferredBodyParts())));
    defaultCheck.setSelected(preset.defaultPreset());
    idLabel.setText(preset.id());
    updateDomainState();
  }

  /** The preset described by the fields, with the id of the shown one. */
  WindowPreset editorPreset() {
    WindowPreset preset = selected();
    if (preset == null) {
      return null;
    }
    String key = (String) keyCombo.getSelectedItem();
    DomainKind kind = (DomainKind) domainCombo.getSelectedItem();
    String unit =
        kind != DomainKind.PERCENT && StringUtil.hasText(unitField.getText())
            ? unitField.getText().trim()
            : null;
    String reference =
        kind == DomainKind.PERCENT ? (String) referenceCombo.getSelectedItem() : null;
    return new WindowPreset(
        preset.id(),
        nameField.getText().trim(),
        WindowPreset.parseCodes(modalitiesField.getText()),
        preset.category(),
        preset.tags(),
        preset.hidden(),
        parseDouble(windowField.getText()),
        parseDouble(levelField.getText()),
        new Domain(kind, unit, reference),
        (LutShape) shapeCombo.getSelectedItem(),
        StringUtil.hasText(key) ? key.charAt(0) : null,
        new When(
            (Integer) bitsSpinner.getValue(),
            rescaleCheck.isSelected(),
            WindowPreset.parseCodes(bodyPartField.getText()),
            WindowPreset.parseCodes(preferredField.getText())),
        defaultCheck.isSelected());
  }

  private void enableEditor(boolean editable) {
    List.of(
            nameField,
            modalitiesField,
            modalitiesPicker,
            windowField,
            levelField,
            unitField,
            bodyPartField,
            bodyPartPicker,
            preferredField,
            preferredPicker,
            shapeCombo,
            keyCombo,
            domainCombo,
            referenceCombo,
            bitsSpinner,
            rescaleCheck,
            defaultCheck)
        .forEach(c -> c.setEnabled(editable));
    updateDomainState();
  }

  // Percent presets have a reference range, absolute ones an informative unit
  private void updateDomainState() {
    DomainKind kind =
        domainCombo.getSelectedItem() == DomainKind.PERCENT
            ? DomainKind.PERCENT
            : DomainKind.ABSOLUTE;
    boolean percent = kind == DomainKind.PERCENT;
    rangeCards.show(rangeSlot, kind.name());
    rangeLabel.setText(
        Messages.getString(percent ? "WindowPresetPrefView.reference" : "WindowPresetPrefView.unit")
            + StringUtil.COLON);
    rangeLabel.setToolTipText(
        Messages.getString(
            percent ? "WindowPresetPrefView.reference.tip" : "WindowPresetPrefView.unit.tip"));
  }

  /** Keeps the edits of the shown preset until Apply. */
  void commitEditor() {
    if (filling || !validateEditor()) {
      return;
    }
    WindowPreset preset = selected();
    if (preset == null || registry.origin(preset.id()) != Origin.USER) {
      return;
    }
    WindowPreset edited = editorPreset();
    if (registry.find(edited.id()).filter(edited::equals).isPresent()) {
      pending.remove(edited.id());
    } else {
      pending.put(edited.id(), edited);
    }
    presetCombo.repaint();
  }

  /** Outlines the invalid fields; whether the editor holds a valid preset. */
  private boolean validateEditor() {
    boolean editable = editorEnabled();
    boolean name = !editable || StringUtil.hasText(nameField.getText());
    double w = parseDouble(windowField.getText());
    boolean window = !editable || (Double.isFinite(w) && w > 0);
    boolean level = !editable || Double.isFinite(parseDouble(levelField.getText()));
    outline(nameField, name);
    outline(windowField, window);
    outline(levelField, level);
    return name && window && level;
  }

  private static void outline(JComponent field, boolean valid) {
    field.putClientProperty(
        FlatClientProperties.OUTLINE, valid ? null : FlatClientProperties.OUTLINE_ERROR);
  }

  // A new preset starts from a soft-tissue window for the modalities of the selected one
  private void createPreset() {
    String name =
        JOptionPane.showInputDialog(
            this,
            Messages.getString("WindowPresetPrefView.new.name"),
            Messages.getString("WindowPresetPrefView.new"),
            JOptionPane.PLAIN_MESSAGE);
    if (!StringUtil.hasText(name)) {
      return;
    }
    WindowPreset source = selected();
    save(
        new WindowPreset(
            uniqueId(name),
            name.trim(),
            source == null ? Set.of() : source.modalities(),
            null,
            List.of(),
            false,
            400,
            40,
            null,
            null,
            null,
            null,
            false),
        name.trim());
  }

  // The copy drops the shortcut key, which belongs to the source
  private void duplicatePreset() {
    WindowPreset source = selected();
    if (source != null) {
      String name = source.name() + " (2)"; // NON-NLS
      WindowPreset copy = source.withId(uniqueId(name));
      save(
          new WindowPreset(
              copy.id(),
              name,
              copy.modalities(),
              copy.category(),
              copy.tags(),
              copy.hidden(),
              copy.window(),
              copy.level(),
              copy.domain(),
              copy.shape(),
              null,
              copy.when(),
              false),
          name);
      nameField.requestFocusInWindow();
      nameField.selectAll();
    }
  }

  private void save(WindowPreset preset, String name) {
    WindowPreset renamed =
        new WindowPreset(
            preset.id(),
            name,
            preset.modalities(),
            preset.category(),
            preset.tags(),
            preset.hidden(),
            preset.window(),
            preset.level(),
            preset.domain(),
            preset.shape(),
            preset.key(),
            preset.when(),
            preset.defaultPreset());
    try {
      registry.saveUser(renamed);
      fillPresets(renamed.id());
    } catch (IOException e) {
      error(e, "Cannot save the window preset");
    }
  }

  private void deletePreset() {
    WindowPreset preset = selected();
    if (preset == null || registry.origin(preset.id()) != Origin.USER) {
      return;
    }
    if (JOptionPane.showConfirmDialog(
            this,
            Messages.getString("WindowPresetPrefView.delete.q"),
            getTitle(),
            JOptionPane.YES_NO_OPTION)
        == JOptionPane.YES_OPTION) {
      try {
        pending.remove(preset.id());
        registry.deleteUser(preset.id());
        fillPresets(null);
      } catch (IOException e) {
        error(e, "Cannot delete the window preset");
      }
    }
  }

  private void importPresets() {
    JFileChooser chooser = new JFileChooser();
    chooser.setFileFilter(
        new FileNameExtensionFilter(
            Messages.getString("WindowPresetPrefView.files"), "json")); // NON-NLS
    if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
      return;
    }
    File file = chooser.getSelectedFile();
    try {
      List<WindowPreset> presets = WindowPresetRegistry.readImport(file.toPath());
      registry.saveUser(presets);
      fillPresets(presets.getFirst().id());
    } catch (IOException e) {
      error(e, "Cannot import window presets");
    }
  }

  private void exportPresets() {
    JFileChooser chooser = new JFileChooser();
    chooser.setSelectedFile(new File(WindowPresetRegistry.USER_FILE));
    if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
      return;
    }
    try {
      Path file = chooser.getSelectedFile().toPath();
      WindowPresetRegistry.export(file, registry.userPresets());
    } catch (IOException e) {
      error(e, "Cannot export window presets");
    }
  }

  private void error(Exception e, String message) {
    LOGGER.error(message, e);
    JOptionPane.showMessageDialog(this, e.getMessage(), getTitle(), JOptionPane.ERROR_MESSAGE);
  }

  private String uniqueId(String name) {
    String base = WindowPreset.USER_PREFIX + slug(name);
    String id = base;
    int i = 2;
    while (registry.find(id).isPresent() || pending.containsKey(id)) {
      id = base + "-" + i++;
    }
    return id;
  }

  static String slug(String text) {
    String slug = text.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-");
    slug = slug.replaceAll("(?:^-+)|(?:-+$)", "");
    return slug.isEmpty() ? "preset" : slug; // NON-NLS
  }

  private static double parseDouble(String text) {
    try {
      return Double.parseDouble(text.trim());
    } catch (NumberFormatException e) {
      return Double.NaN;
    }
  }

  private static String format(double value) {
    return value == Math.rint(value) ? String.valueOf((long) value) : String.valueOf(value);
  }

  @Override
  public void closeAdditionalWindow() {
    commitEditor();
    try {
      registry.saveUser(List.copyOf(pending.values()));
    } catch (IOException e) {
      error(e, "Cannot save the window preset");
    }
    pending.clear();
  }

  @Override
  public void resetToDefaultValues() {
    pending.clear();
    WindowPreset preset = selected();
    fillPresets(preset == null ? null : preset.id());
  }
}
