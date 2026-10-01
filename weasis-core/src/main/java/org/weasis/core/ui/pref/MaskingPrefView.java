/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.pref;

import com.formdev.flatlaf.FlatClientProperties;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.LayoutManager;
import java.awt.Rectangle;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import javax.swing.BorderFactory;
import javax.swing.DefaultCellEditor;
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
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.JViewport;
import javax.swing.ListSelectionModel;
import javax.swing.Scrollable;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import net.miginfocom.swing.MigLayout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.Messages;
import org.weasis.core.api.gui.util.AbstractItemDialogPage;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.media.data.AnonymizationAction;
import org.weasis.core.api.media.data.MaskingModel;
import org.weasis.core.api.media.data.MaskingModel.TagRule;
import org.weasis.core.api.media.data.MaskingModelRegistry;
import org.weasis.core.api.media.data.MaskingModelRegistry.Origin;
import org.weasis.core.api.media.data.MaskingProfile;
import org.weasis.core.api.media.data.PixelMask;
import org.weasis.core.api.media.data.TagCategory;
import org.weasis.core.util.StringUtil;

/**
 * Edits the masking configuration: the profiles that decide how much identity is hidden in what
 * Weasis displays and renders, and the classification of the tags they act on. Built-in and site
 * entries are read-only; changes go to the user document and take effect on Apply.
 */
public class MaskingPrefView extends AbstractItemDialogPage implements Scrollable {
  private static final Logger LOGGER = LoggerFactory.getLogger(MaskingPrefView.class);

  private static final String SHRINKABLE = "wmin 0"; // NON-NLS
  private static final String USER_PREFIX = "user-"; // NON-NLS

  /** Categories a profile acts on; {@link TagCategory#OTHER} is never masked. */
  private static final List<TagCategory> CATEGORIES =
      Arrays.stream(TagCategory.values()).filter(c -> c != TagCategory.OTHER).toList();

  private final MaskingModelRegistry registry;
  private final Map<TagCategory, JComboBox<AnonymizationAction>> actionCombos =
      new EnumMap<>(TagCategory.class);

  private final JComboBox<MaskingProfile> profileCombo = new JComboBox<>();
  private final JTextField nameField = new JTextField(16);
  private final JCheckBox exportCheck = new JCheckBox(Messages.getString("MaskingPrefView.offer"));
  private final JCheckBox datesCheck = new JCheckBox(Messages.getString("MaskingPrefView.shift"));
  private final JComboBox<MaskingProfile> sessionCombo = new JComboBox<>();
  private final JComboBox<MaskingProfile> aiCombo = new JComboBox<>();
  private final JLabel readOnlyLabel = new JLabel();
  private final JLabel lockedLabel = new JLabel();
  private final JMenuItem deleteItem = new JMenuItem(Messages.getString("MaskingPrefView.delete"));
  private final JButton cloneButton = new JButton(Messages.getString("MaskingPrefView.clone"));

  private final TagTableModel tagTableModel = new TagTableModel();
  private final JTable tagTable = new JTable(tagTableModel);
  private final JTextField filterField = new JTextField(10);
  private final JTextField tagField = new JTextField(12);
  private final JComboBox<TagCategory> tagCategoryCombo =
      new JComboBox<>(CATEGORIES.toArray(TagCategory[]::new));
  private final JButton removeTagButton = new JButton(Messages.getString("MaskingPrefView.remove"));

  private MaskingModel pending;

  /** Pixel masks brought by an import; null while the ones on disk are the reference. */
  private List<PixelMask> importedMasks;

  private boolean filling;

  public MaskingPrefView() {
    this(MaskingModelRegistry.getInstance());
  }

  MaskingPrefView(MaskingModelRegistry registry) {
    super(Messages.getString("MaskingPrefView.title"), 112);
    this.registry = registry;
    this.pending = registry.userModel();
    jbInit();
    fillProfiles(null);
    fillTags();
    applyLock();
    getProperties().setProperty(PreferenceDialog.KEY_SHOW_APPLY, Boolean.TRUE.toString());
  }

  private void jbInit() {
    setLayout(new MigLayout("ins 0, fillx, wrap 1, hidemode 3", "[0:pref,grow,fill]")); // NON-NLS
    lockedLabel.putClientProperty(FlatClientProperties.STYLE_CLASS, "small"); // NON-NLS
    add(lockedLabel, SHRINKABLE);
    JTabbedPane tabs = new JTabbedPane();
    tabs.addTab(Messages.getString("MaskingPrefView.tab.profiles"), scrolling(buildProfilesTab()));
    tabs.addTab(Messages.getString("MaskingPrefView.tab.tags"), buildTagsTab());
    // A preferred height the dialog can hold: the page then never scrolls as a whole, and a tab
    // taller than the dialog scrolls inside itself instead of adding a second bar next to its own.
    add(tabs, "height 200lp:300lp:, grow, push"); // NON-NLS
  }

  // Profiles tab

  private JPanel buildProfilesTab() {
    JPanel panel =
        new WidePanel(new MigLayout("ins 5lp, fillx, wrap 1, hidemode 3", "[0:pref,grow,fill]"));
    panel.add(buildProfileBar(), SHRINKABLE);
    readOnlyLabel.putClientProperty(FlatClientProperties.STYLE_CLASS, "small"); // NON-NLS
    panel.add(readOnlyLabel, "gaptop 3lp"); // NON-NLS
    panel.add(buildIdentity(), SHRINKABLE);
    panel.add(buildActions(), SHRINKABLE);
    panel.add(buildDefaults(), SHRINKABLE);

    profileCombo.addActionListener(e -> showSelected());
    nameField.getDocument().addDocumentListener(onEdit(this::commitProfile));
    exportCheck.addActionListener(e -> commitProfile());
    datesCheck.addActionListener(e -> commitProfile());
    sessionCombo.addActionListener(e -> commitDefaults());
    aiCombo.addActionListener(e -> commitDefaults());
    return panel;
  }

  private JPanel buildProfileBar() {
    profileCombo.setRenderer(
        renderer(v -> v instanceof MaskingProfile p ? displayName(p) : String.valueOf(v)));
    profileCombo.setToolTipText(Messages.getString("MaskingPrefView.profile.tip"));

    cloneButton.setToolTipText(Messages.getString("MaskingPrefView.clone.tip"));
    cloneButton.addActionListener(e -> askClone());

    JPopupMenu popup = new JPopupMenu();
    deleteItem.addActionListener(e -> deleteProfile());
    popup.add(deleteItem);
    popup.addSeparator();
    JMenuItem importItem =
        new JMenuItem(Messages.getString("MaskingPrefView.import") + StringUtil.Suffix.THREE_PTS);
    importItem.addActionListener(e -> importDocument());
    popup.add(importItem);
    JMenuItem exportItem =
        new JMenuItem(Messages.getString("MaskingPrefView.export") + StringUtil.Suffix.THREE_PTS);
    exportItem.addActionListener(e -> exportDocument());
    popup.add(exportItem);
    JButton moreButton = new JButton("⋯"); // NON-NLS
    moreButton.setToolTipText(Messages.getString("MaskingPrefView.more.tip"));
    moreButton.addActionListener(e -> popup.show(moreButton, 0, moreButton.getHeight()));

    JPanel bar = new JPanel(new MigLayout("ins 0, fillx", "[][grow,fill][][]")); // NON-NLS
    bar.add(label("MaskingPrefView.profile", profileCombo));
    bar.add(profileCombo, "wmin 80lp"); // NON-NLS
    bar.add(cloneButton);
    bar.add(moreButton);
    return bar;
  }

  private JPanel buildIdentity() {
    JPanel panel = section("MaskingPrefView.section.profile", "[right][grow,fill]");
    panel.add(label("MaskingPrefView.name", nameField));
    panel.add(nameField, SHRINKABLE);
    exportCheck.setToolTipText(Messages.getString("MaskingPrefView.offer.tip"));
    panel.add(exportCheck, "skip 1"); // NON-NLS
    datesCheck.setToolTipText(Messages.getString("MaskingPrefView.shift.tip"));
    panel.add(datesCheck, "skip 1"); // NON-NLS
    return panel;
  }

  // Two category columns: the eight rows fit the dialog without scrolling
  private JPanel buildActions() {
    JPanel panel =
        section("MaskingPrefView.section.actions", "[right][grow 50,fill][right][grow 50,fill]");
    for (TagCategory category : CATEGORIES) {
      JComboBox<AnonymizationAction> combo = new JComboBox<>(actionsFor(category));
      combo.setRenderer(
          renderer(v -> v instanceof AnonymizationAction a ? actionName(a) : String.valueOf(v)));
      combo.addActionListener(e -> commitProfile());
      actionCombos.put(category, combo);
      JLabel label = new JLabel(categoryName(category) + StringUtil.COLON);
      label.setLabelFor(combo);
      label.setToolTipText(categoryTip(category));
      panel.add(label);
      panel.add(combo, SHRINKABLE);
    }
    return panel;
  }

  private JPanel buildDefaults() {
    JPanel panel = section("MaskingPrefView.section.defaults", "[right][grow,fill]");
    sessionCombo.setRenderer(
        renderer(v -> v instanceof MaskingProfile p ? displayName(p) : String.valueOf(v)));
    aiCombo.setRenderer(
        renderer(v -> v instanceof MaskingProfile p ? displayName(p) : String.valueOf(v)));
    sessionCombo.setToolTipText(Messages.getString("MaskingPrefView.session.tip"));
    aiCombo.setToolTipText(Messages.getString("MaskingPrefView.ai.tip"));
    panel.add(label("MaskingPrefView.session", sessionCombo));
    panel.add(sessionCombo, SHRINKABLE);
    panel.add(label("MaskingPrefView.ai", aiCombo));
    panel.add(aiCombo, SHRINKABLE);
    return panel;
  }

  /** Actions that mean something for that category; a profile never keeps direct identifiers. */
  private static AnonymizationAction[] actionsFor(TagCategory category) {
    return switch (category) {
      case DIRECT_ID ->
          new AnonymizationAction[] {
            AnonymizationAction.PSEUDONYMIZE, AnonymizationAction.CLEAR, AnonymizationAction.REMOVE
          };
      case DATE, BIRTH_DATE ->
          new AnonymizationAction[] {
            AnonymizationAction.KEEP,
            AnonymizationAction.SHIFT,
            AnonymizationAction.CLEAR,
            AnonymizationAction.REMOVE
          };
      case INSTITUTION, DEVICE ->
          new AnonymizationAction[] {
            AnonymizationAction.KEEP,
            AnonymizationAction.PSEUDONYMIZE,
            AnonymizationAction.CLEAR,
            AnonymizationAction.REMOVE
          };
      default ->
          new AnonymizationAction[] {
            AnonymizationAction.KEEP, AnonymizationAction.CLEAR, AnonymizationAction.REMOVE
          };
    };
  }

  /** The merged profiles, overridden and extended by the edited user document. */
  private List<MaskingProfile> profiles() {
    Map<String, MaskingProfile> byId = new LinkedHashMap<>();
    registry.profiles().forEach(p -> byId.put(p.id(), p));
    pending.profiles().forEach(p -> byId.put(p.id(), p));
    return List.copyOf(byId.values());
  }

  private boolean isUserProfile(String id) {
    return pending.profiles().stream().anyMatch(p -> p.id().equals(id));
  }

  private void fillProfiles(String selectedId) {
    filling = true;
    try {
      List<MaskingProfile> profiles = profiles();
      MaskingProfile[] array = profiles.toArray(MaskingProfile[]::new);
      profileCombo.setModel(new DefaultComboBoxModel<>(array));
      sessionCombo.setModel(new DefaultComboBoxModel<>(array.clone()));
      aiCombo.setModel(new DefaultComboBoxModel<>(array.clone()));
      select(profileCombo, selectedId);
      select(
          sessionCombo,
          pending.sessionProfile() == null
              ? registry.sessionProfile().id()
              : pending.sessionProfile());
      select(
          aiCombo, pending.aiProfile() == null ? registry.aiProfile().id() : pending.aiProfile());
    } finally {
      filling = false;
    }
    showSelected();
  }

  private static void select(JComboBox<MaskingProfile> combo, String id) {
    for (int i = 0; i < combo.getItemCount(); i++) {
      if (combo.getItemAt(i).id().equals(id)) {
        combo.setSelectedIndex(i);
        return;
      }
    }
  }

  private void showSelected() {
    MaskingProfile profile = (MaskingProfile) profileCombo.getSelectedItem();
    boolean editable = profile != null && isUserProfile(profile.id()) && !registry.isLocked();
    filling = true;
    try {
      show(profile);
    } finally {
      filling = false;
    }
    enableEditor(editable);
    boolean readOnly = profile != null && !editable && !registry.isLocked();
    readOnlyLabel.setVisible(readOnly);
    if (readOnly) {
      readOnlyLabel.setText(
          Messages.getString("MaskingPrefView.readonly")
              .formatted(originName(registry.origin(profile.id()))));
    }
    deleteItem.setEnabled(editable);
  }

  private void show(MaskingProfile profile) {
    nameField.setText(profile == null ? "" : profile.name());
    exportCheck.setSelected(profile != null && profile.offerInExport());
    datesCheck.setSelected(profile != null && profile.shiftsDates());
    actionCombos.forEach(
        (category, combo) ->
            combo.setSelectedItem(
                profile == null ? AnonymizationAction.KEEP : profile.actionFor(category)));
  }

  private void enableEditor(boolean editable) {
    nameField.setEnabled(editable);
    exportCheck.setEnabled(editable);
    datesCheck.setEnabled(editable);
    actionCombos.values().forEach(c -> c.setEnabled(editable));
  }

  /** Keeps the edits of the shown profile in the user document until Apply. */
  private void commitProfile() {
    MaskingProfile profile = (MaskingProfile) profileCombo.getSelectedItem();
    if (filling || profile == null || !isUserProfile(profile.id())) {
      return;
    }
    Map<TagCategory, AnonymizationAction> actions = new EnumMap<>(TagCategory.class);
    actionCombos.forEach(
        (category, combo) -> {
          AnonymizationAction action = (AnonymizationAction) combo.getSelectedItem();
          // A missing category is KEEP: leaving it out keeps the document readable
          if (action != null && action != AnonymizationAction.KEEP) {
            actions.put(category, action);
          }
        });
    String name = nameField.getText().trim();
    replaceProfile(
        new MaskingProfile(
            profile.id(),
            name.isEmpty() ? profile.id() : name,
            profile.labels(),
            actions,
            profile.tagActions(),
            datesCheck.isSelected(),
            exportCheck.isSelected()));
    profileCombo.repaint();
  }

  private void commitDefaults() {
    if (filling) {
      return;
    }
    MaskingProfile session = (MaskingProfile) sessionCombo.getSelectedItem();
    MaskingProfile ai = (MaskingProfile) aiCombo.getSelectedItem();
    pending =
        pending.withDefaults(
            session == null ? pending.sessionProfile() : session.id(),
            ai == null ? pending.aiProfile() : ai.id());
  }

  private void replaceProfile(MaskingProfile profile) {
    List<MaskingProfile> profiles = new ArrayList<>(pending.profiles());
    profiles.removeIf(p -> p.id().equals(profile.id()));
    profiles.add(profile);
    pending = pending.withProfiles(profiles);
  }

  /** A copy of the selected profile, which is how a built-in or site profile is changed. */
  private void askClone() {
    String name =
        JOptionPane.showInputDialog(
            this,
            Messages.getString("MaskingPrefView.new.name"),
            Messages.getString("MaskingPrefView.clone"),
            JOptionPane.PLAIN_MESSAGE);
    if (StringUtil.hasText(name)) {
      cloneProfile(name);
      nameField.requestFocusInWindow();
    }
  }

  /** Copies the selected profile under a new id; the clone is editable, its source is not. */
  MaskingProfile cloneProfile(String name) {
    MaskingProfile source = (MaskingProfile) profileCombo.getSelectedItem();
    if (source == null) {
      return null;
    }
    MaskingProfile copy =
        new MaskingProfile(
            uniqueId(name),
            name.trim(),
            Map.of(),
            source.actions(),
            source.tagActions(),
            source.shiftsDates(),
            source.offerInExport());
    replaceProfile(copy);
    fillProfiles(copy.id());
    return copy;
  }

  private void deleteProfile() {
    MaskingProfile profile = (MaskingProfile) profileCombo.getSelectedItem();
    if (profile == null || !isUserProfile(profile.id())) {
      return;
    }
    if (JOptionPane.showConfirmDialog(
            this,
            Messages.getString("MaskingPrefView.delete.q").formatted(profile.name()),
            getTitle(),
            JOptionPane.YES_NO_OPTION)
        != JOptionPane.YES_OPTION) {
      return;
    }
    removeProfile(profile.id());
  }

  /** Removes a user profile, and takes any default that named it off it. */
  void removeProfile(String id) {
    List<MaskingProfile> profiles = new ArrayList<>(pending.profiles());
    profiles.removeIf(p -> p.id().equals(id));
    pending = pending.withProfiles(profiles);
    // A default left on a deleted profile is resolved to another one at merge, while this page
    // would go on showing the deleted one
    pending =
        pending.withDefaults(
            defaultAfterDelete(pending.sessionProfile(), id, MaskingProfile.DISPLAY_ID),
            defaultAfterDelete(pending.aiProfile(), id, MaskingProfile.AI_REQUEST_ID));
    fillProfiles(null);
  }

  /** The standard profile when {@code current} is the deleted one, or the first one offered. */
  private String defaultAfterDelete(String current, String deletedId, String standardId) {
    if (!deletedId.equals(current)) {
      return current;
    }
    List<MaskingProfile> offered = profiles();
    return offered.stream().anyMatch(p -> p.id().equals(standardId))
        ? standardId
        : offered.stream().findFirst().map(MaskingProfile::id).orElse(null);
  }

  private String uniqueId(String name) {
    String base = USER_PREFIX + slug(name);
    String id = base;
    int i = 2;
    while (exists(id)) {
      id = base + "-" + i++;
    }
    return id;
  }

  private boolean exists(String id) {
    return profiles().stream().anyMatch(p -> p.id().equals(id));
  }

  static String slug(String text) {
    String slug = text.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-");
    slug = slug.replaceAll("(?:^-+)|(?:-+$)", "");
    return slug.isEmpty() ? "profile" : slug; // NON-NLS
  }

  // Tag classification tab

  private JPanel buildTagsTab() {
    JPanel panel =
        new JPanel(new MigLayout("ins 5lp, fillx, wrap 1, hidemode 3", "[0:pref,grow,fill]"));
    tagTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    tagTable.setAutoCreateRowSorter(true);
    tagTable
        .getColumnModel()
        .getColumn(1)
        .setCellEditor(
            new DefaultCellEditor(new JComboBox<>(CATEGORIES.toArray(TagCategory[]::new))));
    tagTable.setDefaultRenderer(
        TagCategory.class, cellRenderer(v -> categoryName((TagCategory) v)));
    tagTable.setDefaultRenderer(Origin.class, cellRenderer(v -> originName((Origin) v)));
    tagTable
        .getSelectionModel()
        .addListSelectionListener(e -> removeTagButton.setEnabled(selectedUserRow() != null));

    JPanel filter = new JPanel(new MigLayout("ins 0, fillx", "[][grow,fill]")); // NON-NLS
    filter.add(label("MaskingPrefView.filter", filterField));
    filter.add(filterField, SHRINKABLE);
    filterField.getDocument().addDocumentListener(onEdit(this::fillTags));
    panel.add(filter, SHRINKABLE);
    panel.add(new JScrollPane(tagTable), "height 120lp:200lp:, grow, push"); // NON-NLS
    panel.add(buildTagEditor(), SHRINKABLE);
    return panel;
  }

  private JPanel buildTagEditor() {
    JPanel panel = new JPanel(new MigLayout("ins 0, fillx", "[][grow,fill][][][]")); // NON-NLS
    tagField.setToolTipText(Messages.getString("MaskingPrefView.tag.tip"));
    tagCategoryCombo.setRenderer(
        renderer(v -> v instanceof TagCategory c ? categoryName(c) : String.valueOf(v)));
    JButton addButton = new JButton(Messages.getString("MaskingPrefView.add"));
    addButton.addActionListener(e -> addTag());
    removeTagButton.addActionListener(e -> removeTag());
    removeTagButton.setEnabled(false);
    panel.add(label("MaskingPrefView.tag", tagField));
    panel.add(tagField, SHRINKABLE);
    panel.add(tagCategoryCombo);
    panel.add(addButton);
    panel.add(removeTagButton);
    return panel;
  }

  private void fillTags() {
    Map<String, TagRow> byKey = new TreeMap<>();
    for (TagRule rule : registry.tagRules()) {
      byKey.put(
          rule.key(),
          new TagRow(rule.key(), rule.category(), registry.tagOrigin(rule.key()), false));
    }
    for (TagRule rule : pending.tags()) {
      byKey.put(rule.key(), new TagRow(rule.key(), rule.category(), Origin.USER, true));
    }
    String filter = filterField.getText().trim().toUpperCase(Locale.ROOT);
    tagTableModel.setRows(
        byKey.values().stream()
            .filter(row -> filter.isEmpty() || row.key().toUpperCase(Locale.ROOT).contains(filter))
            .toList());
    removeTagButton.setEnabled(false);
  }

  void addTag() {
    String key = MaskingModel.normalizeKey(tagField.getText(), null);
    boolean valid = key != null && !registry.isLocked();
    tagField.putClientProperty(
        FlatClientProperties.OUTLINE, valid ? null : FlatClientProperties.OUTLINE_ERROR);
    if (!valid) {
      return;
    }
    classify(key, (TagCategory) tagCategoryCombo.getSelectedItem());
    tagField.setText("");
    fillTags();
  }

  private void removeTag() {
    TagRow row = selectedUserRow();
    if (row == null) {
      return;
    }
    List<TagRule> tags = new ArrayList<>(pending.tags());
    tags.removeIf(rule -> rule.key().equals(row.key()));
    pending = pending.withTags(tags);
    fillTags();
  }

  private TagRow selectedUserRow() {
    int index = tagTable.getSelectedRow();
    if (index < 0) {
      return null;
    }
    TagRow row = tagTableModel.row(tagTable.convertRowIndexToModel(index));
    return row != null && row.user() ? row : null;
  }

  private void classify(String key, TagCategory category) {
    List<TagRule> tags = new ArrayList<>(pending.tags());
    tags.removeIf(rule -> rule.key().equals(key));
    tags.add(new TagRule(key, category));
    pending = pending.withTags(tags);
  }

  private record TagRow(String key, TagCategory category, Origin origin, boolean user) {}

  private class TagTableModel extends AbstractTableModel {
    private transient List<TagRow> rows = List.of();

    void setRows(List<TagRow> rows) {
      this.rows = rows;
      fireTableDataChanged();
    }

    TagRow row(int index) {
      return index >= 0 && index < rows.size() ? rows.get(index) : null;
    }

    @Override
    public int getRowCount() {
      return rows.size();
    }

    @Override
    public int getColumnCount() {
      return 3;
    }

    @Override
    public String getColumnName(int column) {
      return switch (column) {
        case 0 -> Messages.getString("MaskingPrefView.column.tag");
        case 1 -> Messages.getString("MaskingPrefView.column.category");
        default -> Messages.getString("MaskingPrefView.column.source");
      };
    }

    @Override
    public Class<?> getColumnClass(int column) {
      return switch (column) {
        case 0 -> String.class;
        case 1 -> TagCategory.class;
        default -> Origin.class;
      };
    }

    @Override
    public boolean isCellEditable(int row, int column) {
      return column == 1 && !registry.isLocked() && rows.get(row).user();
    }

    @Override
    public Object getValueAt(int row, int column) {
      TagRow entry = rows.get(row);
      return switch (column) {
        case 0 -> entry.key();
        case 1 -> entry.category();
        default -> entry.origin();
      };
    }

    @Override
    public void setValueAt(Object value, int row, int column) {
      if (column == 1 && value instanceof TagCategory category) {
        classify(rows.get(row).key(), category);
        fillTags();
      }
    }
  }

  // Whole document

  private void importDocument() {
    JFileChooser chooser = new JFileChooser();
    chooser.setFileFilter(
        new FileNameExtensionFilter(
            Messages.getString("MaskingPrefView.files"), "json")); // NON-NLS
    if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
      return;
    }
    try {
      MaskingModel imported = MaskingModelRegistry.readImport(chooser.getSelectedFile().toPath());
      // A document without masks leaves the device library alone rather than emptying it
      importedMasks = imported.masks().isEmpty() ? null : imported.masks();
      pending = imported;
      fillProfiles(null);
      fillTags();
    } catch (IOException | RuntimeException e) {
      error(e, "Cannot import the masking configuration");
    }
  }

  private void exportDocument() {
    JFileChooser chooser = new JFileChooser();
    chooser.setSelectedFile(new File(MaskingModelRegistry.USER_FILE));
    if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
      return;
    }
    try {
      edited().write(chooser.getSelectedFile().toPath());
    } catch (IOException e) {
      error(e, "Cannot export the masking configuration");
    }
  }

  private void applyLock() {
    boolean locked = registry.isLocked();
    lockedLabel.setVisible(locked);
    if (locked) {
      lockedLabel.setText(
          Messages.getString("MaskingPrefView.locked").formatted(registry.siteLocation()));
      cloneButton.setEnabled(false);
      filterField.setEnabled(true);
      tagField.setEnabled(false);
      tagCategoryCombo.setEnabled(false);
    }
  }

  private void error(Exception e, String message) {
    LOGGER.error(message, e);
    JOptionPane.showMessageDialog(this, e.getMessage(), getTitle(), JOptionPane.ERROR_MESSAGE);
  }

  // Display names

  private static String displayName(MaskingProfile profile) {
    return profile.displayName(Locale.getDefault());
  }

  static String categoryName(TagCategory category) {
    return category.displayName();
  }

  private static String categoryTip(TagCategory category) {
    return Messages.getString("MaskingPrefView.tip." + category.name());
  }

  static String actionName(AnonymizationAction action) {
    return Messages.getString("MaskingPrefView.action." + action.name());
  }

  private static String originName(Origin origin) {
    return Messages.getString("MaskingPrefView.origin." + origin.name());
  }

  private JLabel label(String key, JComponent field) {
    JLabel label = new JLabel(Messages.getString(key) + StringUtil.COLON);
    label.setLabelFor(field);
    return label;
  }

  private static JPanel section(String titleKey, String columns) {
    JPanel panel = new JPanel(new MigLayout("ins 5lp, fillx, wrap 2", columns)); // NON-NLS
    panel.setBorder(GuiUtils.getTitledBorder(Messages.getString(titleKey)));
    return panel;
  }

  private static DefaultListCellRenderer renderer(Function<Object, String> text) {
    return new DefaultListCellRenderer() {
      @Override
      public Component getListCellRendererComponent(
          JList<?> list, Object value, int index, boolean selected, boolean focus) {
        super.getListCellRendererComponent(list, value, index, selected, focus);
        setText(value == null ? "" : text.apply(value));
        return this;
      }
    };
  }

  private static DefaultTableCellRenderer cellRenderer(Function<Object, String> text) {
    return new DefaultTableCellRenderer() {
      @Override
      protected void setValue(Object value) {
        setText(value == null ? "" : text.apply(value));
      }
    };
  }

  private static DocumentListener onEdit(Runnable action) {
    return new DocumentListener() {
      @Override
      public void insertUpdate(DocumentEvent e) {
        action.run();
      }

      @Override
      public void removeUpdate(DocumentEvent e) {
        action.run();
      }

      @Override
      public void changedUpdate(DocumentEvent e) {
        action.run();
      }
    };
  }

  /** The edited document, for the tests. */
  MaskingModel pendingModel() {
    return pending;
  }

  /** Whether the shown profile can be edited (a user profile, no lock), for the tests. */
  boolean editorEnabled() {
    return nameField.isEnabled();
  }

  /** Selects the profile of that id, for the tests. */
  void selectProfile(String id) {
    select(profileCombo, id);
  }

  /** Selects the profile used by session masking, for the tests. */
  void selectSessionProfile(String id) {
    select(sessionCombo, id);
  }

  /** Sets the action of a category in the editor, for the tests. */
  void setAction(TagCategory category, AnonymizationAction action) {
    actionCombos.get(category).setSelectedItem(action);
  }

  /** Types a tag in the classification editor, for the tests. */
  void typeTag(String tag) {
    tagField.setText(tag);
  }

  /** Rows of the classification table, for the tests. */
  int tagRowCount() {
    return tagTableModel.getRowCount();
  }

  /**
   * The document to write: this page never edits the pixel masks, so the ones on disk win. The
   * device-mask page writes them straight away while this one is open, and rewriting the snapshot
   * taken when this page was built would take those changes back.
   */
  private MaskingModel edited() {
    return pending.withMasks(importedMasks == null ? registry.userModel().masks() : importedMasks);
  }

  @Override
  public void closeAdditionalWindow() {
    MaskingModel model = edited();
    if (registry.isLocked() || model.equals(registry.userModel())) {
      return;
    }
    try {
      registry.saveUser(model);
      pending = registry.userModel();
      importedMasks = null;
      fillProfiles(null);
      fillTags();
    } catch (IOException | RuntimeException e) {
      error(e, "Cannot save the masking configuration");
    }
  }

  @Override
  public void resetToDefaultValues() {
    pending = registry.userModel();
    importedMasks = null;
    fillProfiles(null);
    fillTags();
  }

  // ---- Scrolling -------------------------------------------------------------------------

  /** A tab that scrolls its own content, with no border of its own to double the tab one. */
  private static JScrollPane scrolling(JComponent content) {
    JScrollPane scroll = new JScrollPane(content);
    scroll.setBorder(BorderFactory.createEmptyBorder());
    scroll.getVerticalScrollBar().setUnitIncrement(GuiUtils.getScaleLength(16));
    return scroll;
  }

  /**
   * The page takes the width of the dialog instead of asking for its own: its content adapts, and a
   * preferred width that depends on the current one cannot make the page grow while scrolling.
   */
  @Override
  public boolean getScrollableTracksViewportWidth() {
    return true;
  }

  /** Fills the dialog when it is taller than the page, scrolls vertically otherwise. */
  @Override
  public boolean getScrollableTracksViewportHeight() {
    return getParent() instanceof JViewport viewport
        && viewport.getHeight() > getPreferredSize().height;
  }

  @Override
  public Dimension getPreferredScrollableViewportSize() {
    return getPreferredSize();
  }

  @Override
  public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
    return GuiUtils.getScaleLength(16);
  }

  @Override
  public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
    return Math.max(GuiUtils.getScaleLength(16), visibleRect.height / 2);
  }

  /** Content of a scrolling tab: it takes the width it is given, and scrolls vertically only. */
  private static class WidePanel extends JPanel implements Scrollable {

    WidePanel(LayoutManager layout) {
      super(layout);
    }

    @Override
    public Dimension getPreferredScrollableViewportSize() {
      return getPreferredSize();
    }

    @Override
    public boolean getScrollableTracksViewportWidth() {
      return true;
    }

    @Override
    public boolean getScrollableTracksViewportHeight() {
      return false;
    }

    @Override
    public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
      return GuiUtils.getScaleLength(16);
    }

    @Override
    public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
      return Math.max(GuiUtils.getScaleLength(16), visibleRect.height / 2);
    }
  }
}
