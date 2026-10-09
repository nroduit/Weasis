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

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.GridLayout;
import java.awt.Rectangle;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.swing.DefaultListCellRenderer;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JColorChooser;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JSpinner;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.JViewport;
import javax.swing.Scrollable;
import javax.swing.SpinnerNumberModel;
import javax.swing.filechooser.FileNameExtensionFilter;
import net.miginfocom.swing.MigLayout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.Messages;
import org.weasis.core.api.gui.util.AbstractItemDialogPage;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.util.LayeredEntries.Origin;
import org.weasis.core.ui.editor.image.dockable.MeasureTool;
import org.weasis.core.ui.model.graphic.Graphic;
import org.weasis.core.ui.model.graphic.GraphicRegistry;
import org.weasis.core.ui.model.graphic.ToolCategory;
import org.weasis.core.ui.model.graphic.imp.area.SelectGraphic;
import org.weasis.core.ui.model.graphic.profile.MeasurementProfile;
import org.weasis.core.ui.model.graphic.profile.MeasurementProfile.Defaults;
import org.weasis.core.ui.model.graphic.profile.MeasurementProfileRegistry;
import org.weasis.core.ui.model.graphic.profile.MeasurementProfiles;
import org.weasis.core.ui.model.utils.ImageStatistics;
import org.weasis.core.ui.model.utils.MeasureFormat;
import org.weasis.core.ui.model.utils.bean.Measurement;
import org.weasis.core.util.StringUtil;

/**
 * Edits the user's tool profiles. The profile is chosen in the bar at the top; its name and
 * modalities come next, and three tabs hold the rest: the tools of the two palettes and their
 * order, the defaults of new graphics, the pixel statistics and the labels shown on the image.
 * Every section can be left to the current settings, which is how a profile only changes what it is
 * meant to change.
 */
public class ProfilePrefView extends AbstractItemDialogPage implements Scrollable {
  private static final Logger LOGGER = LoggerFactory.getLogger(ProfilePrefView.class);

  /** A section takes the width of the page even when its content would like more. */
  private static final String SHRINKABLE = "wmin 0"; // NON-NLS

  private final transient MeasurementProfileRegistry registry;
  private final transient GraphicRegistry graphics;

  private final JComboBox<MeasurementProfile> profileCombo = new JComboBox<>();
  private final JMenuItem deleteItem = new JMenuItem(Messages.getString("ProfilePrefView.delete"));
  private final JTextField nameField = new JTextField(16);
  private final JTextField modalitiesField = new JTextField(12);

  private final JCheckBox allTools = new JCheckBox(Messages.getString("ProfilePrefView.all_tools"));
  private final ToolCheckList measureTools =
      new ToolCheckList(Messages.getString("ProfilePrefView.measure_palette"));
  private final ToolCheckList drawTools =
      new ToolCheckList(Messages.getString("ProfilePrefView.draw_palette"));

  private final JCheckBox keepDefaults = new JCheckBox(Messages.getString("ProfilePrefView.keep"));
  private final JButton colorButton = new JButton();
  private final JSpinner widthSpinner = new JSpinner(new SpinnerNumberModel(1, 1, 8, 1));
  private final JCheckBox fillBox =
      new JCheckBox(Messages.getString("PropertiesDialog.fill_shape"));
  private final JSpinner opacitySpinner = new JSpinner(new SpinnerNumberModel(100, 0, 100, 5));
  private final JCheckBox uprightBox = new JCheckBox(Messages.getString("GraphicPrefView.upright"));
  private final JComboBox<Object> decimalsCombo = new JComboBox<>();

  private final JCheckBox keepStatistics =
      new JCheckBox(Messages.getString("ProfilePrefView.keep"));
  private final Map<JCheckBox, Measurement> statisticsBoxes = new LinkedHashMap<>();
  private final JComboBox<Graphic> labelToolCombo = new JComboBox<>();
  private final JCheckBox setLabels =
      new JCheckBox(Messages.getString("ProfilePrefView.set_labels"));
  private final JPanel labelBoxes = new JPanel(new GridLayout(0, 2));

  private Color color = ViewSetting.DEFAULT_LINE_COLOR;
  private final Map<String, List<String>> labels = new LinkedHashMap<>();
  private MeasurementProfile edited;
  private boolean editable;
  private boolean updating;
  private final Map<String, MeasurementProfile> pending = new LinkedHashMap<>();

  public ProfilePrefView() {
    this(MeasurementProfileRegistry.getInstance(), GraphicRegistry.getInstance());
  }

  ProfilePrefView(MeasurementProfileRegistry registry, GraphicRegistry graphics) {
    super(Messages.getString("ProfilePrefView.title"), 704);
    getProperties()
        .setProperty(AbstractItemDialogPage.KEY_SCOPE, AbstractItemDialogPage.SCOPE_USER);
    this.registry = registry;
    this.graphics = graphics;
    jbInit();
    fillProfiles(registry.selectedId());
    getProperties().setProperty(PreferenceDialog.KEY_SHOW_APPLY, Boolean.TRUE.toString());
    getProperties()
        .setProperty(PreferenceDialog.KEY_HELP, "draw-measure/#tool-profiles"); // NON-NLS
  }

  private void jbInit() {
    setLayout(
        new MigLayout("ins 0, fill, wrap 1, hidemode 3", "[0:pref,grow,fill]", "[][][grow,fill]"));
    add(buildProfileBar(), SHRINKABLE);
    add(buildIdentity(), SHRINKABLE);
    JTabbedPane tabs = new JTabbedPane();
    tabs.addTab(Messages.getString("ProfilePrefView.tools"), buildToolsTab());
    tabs.addTab(Messages.getString("ProfilePrefView.defaults"), buildDefaultsTab());
    tabs.addTab(Messages.getString("ProfilePrefView.values"), buildValuesTab());
    add(tabs, SHRINKABLE);
  }

  // ---- Profile bar -------------------------------------------------------------------------

  private JPanel buildProfileBar() {
    profileCombo.setRenderer(
        new DefaultListCellRenderer() {
          @Override
          public Component getListCellRendererComponent(
              JList<?> list, Object value, int index, boolean selected, boolean focus) {
            super.getListCellRendererComponent(list, value, index, selected, focus);
            if (value instanceof MeasurementProfile profile) {
              setText(displayName(profile, registry.origin(profile.id())));
            }
            return this;
          }
        });
    profileCombo.addActionListener(
        _ -> {
          if (!updating) {
            commitEditor();
            showProfile((MeasurementProfile) profileCombo.getSelectedItem());
          }
        });

    JButton create = new JButton(Messages.getString("ProfilePrefView.new"));
    create.setToolTipText(Messages.getString("ProfilePrefView.new_tip"));
    create.addActionListener(_ -> createFromCurrent());
    JButton duplicate = new JButton(Messages.getString("ProfilePrefView.duplicate"));
    duplicate.addActionListener(_ -> duplicate());
    deleteItem.addActionListener(_ -> delete());
    JMenuItem importItem = new JMenuItem(Messages.getString("ProfilePrefView.import"));
    importItem.addActionListener(_ -> importProfiles());
    JMenuItem exportItem = new JMenuItem(Messages.getString("ProfilePrefView.export"));
    exportItem.addActionListener(_ -> exportProfile());
    JPopupMenu fileMenu = new JPopupMenu();
    fileMenu.add(deleteItem);
    fileMenu.addSeparator();
    fileMenu.add(importItem);
    fileMenu.add(exportItem);
    JButton more = new JButton("\u22EF"); // NON-NLS
    more.setToolTipText(Messages.getString("ProfilePrefView.more_tip"));
    more.addActionListener(_ -> fileMenu.show(more, 0, more.getHeight()));

    // One line: the combo takes what the buttons leave and shortens a long name
    JPanel bar = new JPanel(new MigLayout("ins 0, fillx", "[][grow,fill][][][]")); // NON-NLS
    bar.add(new JLabel(Messages.getString("MeasureTool.profile_label") + StringUtil.COLON));
    bar.add(profileCombo, "wmin 80lp"); // NON-NLS
    for (JButton button : List.of(create, duplicate, more)) {
      bar.add(button);
    }
    return bar;
  }

  static String displayName(MeasurementProfile profile) {
    return profile.builtIn()
        ? profile.name() + " (" + Messages.getString("ProfilePrefView.builtin") + ")"
        : profile.name();
  }

  /** The name with where the profile comes from, and whether the site locked it. */
  static String displayName(MeasurementProfile profile, Origin origin) {
    if (origin == Origin.USER) {
      return profile.name();
    }
    String suffix = origin.displayName();
    if (profile.locked()) {
      suffix += ", " + Origin.lockedName();
    }
    return profile.name() + " (" + suffix + ")";
  }

  // ---- Name and modalities -----------------------------------------------------------------

  private JPanel buildIdentity() {
    String tip = Messages.getString("ProfilePrefView.modalities_tip");
    JLabel modalitiesLabel =
        new JLabel(Messages.getString("ProfilePrefView.modalities") + StringUtil.COLON);
    modalitiesLabel.setToolTipText(tip);
    modalitiesField.setToolTipText(tip);
    JPanel identity =
        new JPanel(
            new MigLayout("ins 5lp 0 5lp 0, fillx", "[][grow 60,fill][][grow 40,fill]")); // NON-NLS
    identity.add(new JLabel(Messages.getString("ProfilePrefView.name") + StringUtil.COLON));
    identity.add(nameField, "wmin 60lp"); // NON-NLS
    identity.add(modalitiesLabel, "gapleft 10lp"); // NON-NLS
    identity.add(modalitiesField, "wmin 60lp"); // NON-NLS
    return identity;
  }

  // ---- Tabs --------------------------------------------------------------------------------

  private JPanel buildToolsTab() {
    JPanel tab =
        new JPanel(
            new MigLayout(
                "ins 5lp, fill, wrap 2", "[0:0,grow,fill,sg][0:0,grow,fill,sg]", "[][grow,fill]"));
    allTools.setToolTipText(Messages.getString("ProfilePrefView.all_tools_tip"));
    allTools.addActionListener(_ -> enableEditor());
    tab.add(allTools, "span 2"); // NON-NLS
    tab.add(measureTools);
    tab.add(drawTools);
    return tab;
  }

  private JPanel buildDefaultsTab() {
    colorButton.setToolTipText(Messages.getString("MeasureTool.pick"));
    colorButton.addActionListener(
        _ -> {
          Color chosen =
              JColorChooser.showDialog(this, Messages.getString("MeasureTool.pick_color"), color);
          if (chosen != null) {
            setColor(chosen);
          }
        });
    uprightBox.setToolTipText(Messages.getString("GraphicPrefView.upright_tip"));
    decimalsCombo.addItem(Messages.getString("LabelPrefView.decimals_auto"));
    for (int i = 0; i <= MeasureFormat.MAX_DECIMALS; i++) {
      decimalsCombo.addItem(i);
    }
    keepDefaults.addActionListener(_ -> enableEditor());

    // Same blocks as the Drawings page, each as wide as the tab, fields aligned on two columns
    String columns = "[][left,grow]"; // NON-NLS
    JPanel line = new JPanel(new MigLayout("ins 5lp, fillx", "[][][][left,grow]")); // NON-NLS
    line.setBorder(GuiUtils.getTitledBorder(Messages.getString("MeasureToolBar.line")));
    line.add(new JLabel(Messages.getString("ColorMapEditor.color") + StringUtil.COLON));
    line.add(colorButton);
    line.add(
        new JLabel(Messages.getString("measure.width") + StringUtil.COLON),
        "gapleft 15lp"); // NON-NLS
    line.add(widthSpinner);

    JPanel shape = new JPanel(new MigLayout("ins 5lp, fillx, wrap 2", columns)); // NON-NLS
    shape.setBorder(GuiUtils.getTitledBorder(Messages.getString("closed.shape")));
    shape.add(fillBox, "span 2"); // NON-NLS
    shape.add(new JLabel(Messages.getString("fill.opacity") + StringUtil.COLON), "gapleft 20lp");
    shape.add(opacitySpinner, "split 2"); // NON-NLS
    shape.add(new JLabel("%"));
    shape.add(uprightBox, "span 2"); // NON-NLS

    JPanel values = new JPanel(new MigLayout("ins 5lp, fillx, wrap 2", columns)); // NON-NLS
    values.setBorder(GuiUtils.getTitledBorder(Messages.getString("ProfilePrefView.measured")));
    values.add(new JLabel(Messages.getString("ProfilePrefView.decimals") + StringUtil.COLON));
    values.add(decimalsCombo);

    fillBox.addActionListener(_ -> enableEditor());
    JPanel tab =
        new JPanel(new MigLayout("ins 5lp, fillx, wrap 1", "[0:pref,grow,fill]")); // NON-NLS
    tab.add(keepDefaults);
    tab.add(line);
    tab.add(shape);
    tab.add(values);
    return tab;
  }

  private JPanel buildValuesTab() {
    JPanel statistics = new JPanel(new MigLayout("ins 5lp, wrap 1", "[grow,fill]")); // NON-NLS
    statistics.setBorder(
        GuiUtils.getTitledBorder(Messages.getString("ProfilePrefView.statistics")));
    keepStatistics.addActionListener(_ -> enableEditor());
    statistics.add(keepStatistics);
    JPanel grid = new JPanel(new GridLayout(0, 2));
    for (Measurement m : ImageStatistics.ALL_MEASUREMENTS) {
      JCheckBox box = new JCheckBox(m.getName());
      statisticsBoxes.put(box, m);
      grid.add(box);
    }
    statistics.add(grid);

    JPanel labelPanel = new JPanel(new MigLayout("ins 5lp, wrap 1", "[grow,fill]")); // NON-NLS
    labelPanel.setBorder(GuiUtils.getTitledBorder(Messages.getString("ProfilePrefView.labels")));
    labelToolCombo.setRenderer(
        new DefaultListCellRenderer() {
          @Override
          public Component getListCellRendererComponent(
              JList<?> list, Object value, int index, boolean selected, boolean focus) {
            super.getListCellRendererComponent(list, value, index, selected, focus);
            if (value instanceof Graphic graphic) {
              setText(graphic.getUIName());
              setIcon(graphic.getIcon());
            }
            return this;
          }
        });
    labelToolCombo.addActionListener(_ -> showLabelsOfSelectedTool());
    setLabels.setToolTipText(Messages.getString("ProfilePrefView.set_labels_tip"));
    setLabels.addActionListener(_ -> storeLabelsOfSelectedTool());
    JButton takeLabels = new JButton(Messages.getString("ProfilePrefView.take_labels"));
    takeLabels.setToolTipText(Messages.getString("ProfilePrefView.take_labels_tip"));
    takeLabels.addActionListener(_ -> takeCurrentLabels());
    labelPanel.add(labelToolCombo);
    labelPanel.add(setLabels);
    labelPanel.add(labelBoxes);
    labelPanel.add(takeLabels, "growx 0, gaptop 8lp"); // NON-NLS

    JPanel tab =
        new JPanel(
            new MigLayout(
                "ins 5lp, fill", "[0:0,grow,fill,sg][0:0,grow,fill,sg]", "[top]")); // NON-NLS
    tab.add(statistics);
    tab.add(labelPanel);
    return tab;
  }

  // ---- Showing a profile -------------------------------------------------------------------

  private void fillProfiles(String selectId) {
    updating = true;
    try {
      profileCombo.removeAllItems();
      MeasurementProfile target = null;
      for (MeasurementProfile profile : registry.profiles()) {
        MeasurementProfile shown = pending.getOrDefault(profile.id(), profile);
        profileCombo.addItem(shown);
        if (shown.id().equals(selectId)) {
          target = shown;
        }
      }
      if (target == null && profileCombo.getItemCount() > 0) {
        target = profileCombo.getItemAt(0);
      }
      profileCombo.setSelectedItem(target);
      showProfile(target);
    } finally {
      updating = false;
    }
  }

  /** The tools of a palette: those of the profile first, in its order, then the others. */
  private List<ToolCheckList.Item> items(MeasurementProfile profile, ToolCategory category) {
    boolean all = profile == null || profile.tools() == null;
    List<String> chosen =
        MeasurementProfiles.keysOf(MeasurementProfiles.tools(profile, graphics, category));
    List<String> keys = new ArrayList<>(chosen);
    for (String key : MeasurementProfiles.keysOf(graphics.prototypes(category))) {
      if (!keys.contains(key)) {
        keys.add(key);
      }
    }
    List<ToolCheckList.Item> items = new ArrayList<>();
    for (String key : keys) {
      Graphic prototype = graphics.prototype(key).orElse(null);
      if (prototype != null && !(prototype instanceof SelectGraphic)) {
        items.add(
            new ToolCheckList.Item(
                key, prototype.getUIName(), prototype.getIcon(), all || chosen.contains(key)));
      }
    }
    return items;
  }

  void showProfile(MeasurementProfile profile) {
    boolean wasUpdating = updating;
    updating = true;
    try {
      edited = profile;
      editable = profile != null && !profile.builtIn();
      nameField.setText(profile == null ? "" : profile.name());
      modalitiesField.setText(profile == null ? "" : String.join(", ", profile.modalities()));

      allTools.setSelected(profile == null || profile.tools() == null);
      measureTools.setItems(items(profile, ToolCategory.MEASURE));
      drawTools.setItems(items(profile, ToolCategory.DRAW));

      showDefaults(profile == null ? null : profile.defaults());

      keepStatistics.setSelected(profile == null || profile.statistics() == null);
      statisticsBoxes.forEach(
          (box, m) ->
              box.setSelected(
                  profile == null || profile.statistics() == null
                      ? Boolean.TRUE.equals(m.getComputed())
                      : profile.statistics().contains(m.getKey())));

      labels.clear();
      if (profile != null) {
        labels.putAll(profile.labels());
      }
      Object selectedTool = labelToolCombo.getSelectedItem();
      labelToolCombo.removeAllItems();
      for (ToolCategory category : List.of(ToolCategory.MEASURE, ToolCategory.ADVANCED)) {
        for (Graphic g : graphics.prototypes(category)) {
          boolean measured = g.getMeasurementList() != null && !g.getMeasurementList().isEmpty();
          if (measured && !(g instanceof SelectGraphic)) {
            labelToolCombo.addItem(g);
          }
        }
      }
      if (selectedTool != null) {
        labelToolCombo.setSelectedItem(selectedTool);
      }
    } finally {
      updating = wasUpdating;
    }
    refreshLabels();
  }

  private void showDefaults(Defaults d) {
    ViewSetting current = MeasureTool.viewSetting;
    keepDefaults.setSelected(d == null);
    setColor(d == null || d.color() == null ? current.getLineColor() : d.color());
    widthSpinner.setValue(
        d == null || d.lineWidth() == null ? current.getLineWidth() : d.lineWidth());
    fillBox.setSelected(d == null || d.fill() == null ? current.isFilled() : d.fill());
    opacitySpinner.setValue(
        Math.round(
            100
                * (d == null || d.fillOpacity() == null
                    ? current.getFillOpacity()
                    : d.fillOpacity())));
    uprightBox.setSelected(
        d == null || d.uprightByDrag() == null ? current.isUprightByDrag() : d.uprightByDrag());
    int decimals = d == null || d.decimals() == null ? current.getDecimals() : d.decimals();
    decimalsCombo.setSelectedIndex(
        decimals < 0 ? 0 : Math.min(decimals, MeasureFormat.MAX_DECIMALS) + 1);
  }

  private void setColor(Color chosen) {
    color = chosen;
    colorButton.setIcon(new ColorIcon(chosen));
  }

  /** A section left to the current settings is greyed; a built-in profile is read only. */
  private void enableEditor() {
    nameField.setEnabled(editable);
    modalitiesField.setEnabled(editable);
    deleteItem.setEnabled(editable);
    // A user profile over a site or built-in id: deleting it resets the profile to that definition
    deleteItem.setText(
        edited == null
            ? Messages.getString("ProfilePrefView.delete")
            : registry
                .below(edited.id())
                .map(o -> Messages.getString("ProfilePrefView.reset").formatted(o.displayName()))
                .orElse(Messages.getString("ProfilePrefView.delete")));

    allTools.setEnabled(editable);
    measureTools.setEnabled(editable && !allTools.isSelected());
    drawTools.setEnabled(editable && !allTools.isSelected());

    keepDefaults.setEnabled(editable);
    boolean defaults = editable && !keepDefaults.isSelected();
    for (Component c : List.of(colorButton, widthSpinner, fillBox, uprightBox, decimalsCombo)) {
      c.setEnabled(defaults);
    }
    opacitySpinner.setEnabled(defaults && fillBox.isSelected());

    keepStatistics.setEnabled(editable);
    statisticsBoxes.keySet().forEach(b -> b.setEnabled(editable && !keepStatistics.isSelected()));

    setLabels.setEnabled(editable && labelToolCombo.getSelectedItem() != null);
    for (Component c : labelBoxes.getComponents()) {
      c.setEnabled(editable && setLabels.isSelected());
    }
  }

  // ---- Labels of a tool --------------------------------------------------------------------

  private String selectedLabelTool() {
    return labelToolCombo.getSelectedItem() instanceof Graphic graphic
        ? graphics.keyOf(graphic).orElse(null)
        : null;
  }

  /** Follows the tool combo, but not while the page fills it. */
  private void showLabelsOfSelectedTool() {
    if (!updating) {
      refreshLabels();
    }
  }

  /** Shows the labels of the selected tool for the profile in the editor. */
  private void refreshLabels() {
    labelBoxes.removeAll();
    String tool = selectedLabelTool();
    if (tool != null && labelToolCombo.getSelectedItem() instanceof Graphic graphic) {
      List<String> shown = labels.get(tool);
      boolean wasUpdating = updating;
      updating = true;
      setLabels.setSelected(shown != null);
      updating = wasUpdating;
      for (Measurement m : graphic.getMeasurementList()) {
        JCheckBox box = new JCheckBox(m.getName());
        box.setName(m.getKey());
        box.setSelected(
            shown == null ? Boolean.TRUE.equals(m.getGraphicLabel()) : shown.contains(m.getKey()));
        box.addActionListener(_ -> storeLabelsOfSelectedTool());
        labelBoxes.add(box);
      }
    }
    labelBoxes.revalidate();
    labelBoxes.repaint();
    enableEditor();
  }

  /** Keeps the choice of the check boxes, or forgets the tool when the profile does not set it. */
  private void storeLabelsOfSelectedTool() {
    String tool = selectedLabelTool();
    if (updating || tool == null) {
      return;
    }
    if (setLabels.isSelected()) {
      List<String> shown = new ArrayList<>();
      for (Component c : labelBoxes.getComponents()) {
        if (c instanceof JCheckBox box && box.isSelected()) {
          shown.add(box.getName());
        }
      }
      labels.put(tool, List.copyOf(shown));
    } else {
      labels.remove(tool);
    }
    enableEditor();
  }

  private void takeCurrentLabels() {
    labels.clear();
    labels.putAll(MeasurementProfiles.capture("x", "x", List.of(), null).labels()); // NON-NLS
    refreshLabels();
  }

  // ---- Reading the editor ------------------------------------------------------------------

  /** The profile the editor describes; the one shown when it cannot be edited. */
  MeasurementProfile editedProfile() {
    if (edited == null || !editable) {
      return edited;
    }
    List<String> tools = null;
    if (!allTools.isSelected()) {
      tools = new ArrayList<>(measureTools.checkedKeys());
      tools.addAll(drawTools.checkedKeys());
    }
    Defaults defaults =
        keepDefaults.isSelected()
            ? null
            : new Defaults(
                color,
                (Integer) widthSpinner.getValue(),
                fillBox.isSelected(),
                ((Integer) opacitySpinner.getValue()) / 100f,
                uprightBox.isSelected(),
                decimalsCombo.getSelectedItem() instanceof Integer n ? n : MeasureFormat.AUTO);
    List<String> statistics =
        keepStatistics.isSelected()
            ? null
            : statisticsBoxes.entrySet().stream()
                .filter(e -> e.getKey().isSelected())
                .map(e -> e.getValue().getKey())
                .toList();
    String name = nameField.getText().trim();
    return new MeasurementProfile(
        edited.id(),
        name.isEmpty() ? edited.name() : name,
        modalities(),
        tools,
        defaults,
        new LinkedHashMap<>(labels),
        statistics,
        false);
  }

  /** Keeps the editor's content aside until the page is applied, when it changed something. */
  private void commitEditor() {
    MeasurementProfile profile = editedProfile();
    if (profile != null && editable && !profile.equals(edited)) {
      pending.put(profile.id(), profile);
      edited = profile;
    }
  }

  private List<String> modalities() {
    return parseModalities(modalitiesField.getText());
  }

  /** Modalities typed with commas, semicolons or spaces between them, in upper case, once each. */
  static List<String> parseModalities(String text) {
    return Arrays.stream(text.split("[,;\\s]+"))
        .filter(StringUtil::hasText)
        .map(String::toUpperCase)
        .distinct()
        .toList();
  }

  // ---- Actions of the bar ------------------------------------------------------------------

  private void createFromCurrent() {
    String name =
        JOptionPane.showInputDialog(
            this,
            Messages.getString("ProfilePrefView.name"),
            Messages.getString("ProfilePrefView.new_name"));
    if (!StringUtil.hasText(name)) {
      return;
    }
    commitEditor();
    MeasurementProfile profile =
        MeasurementProfiles.capture(
            uniqueId(MeasurementProfile.idFromName(name)), name.trim(), List.of(), null);
    registry.saveUser(profile);
    fillProfiles(profile.id());
  }

  private void duplicate() {
    commitEditor();
    MeasurementProfile source = (MeasurementProfile) profileCombo.getSelectedItem();
    if (source == null) {
      return;
    }
    String name = source.name() + " 2";
    MeasurementProfile copy =
        pending
            .getOrDefault(source.id(), source)
            .withIdAndName(uniqueId(MeasurementProfile.idFromName(name)), name);
    registry.saveUser(copy);
    fillProfiles(copy.id());
  }

  private void delete() {
    MeasurementProfile profile = (MeasurementProfile) profileCombo.getSelectedItem();
    if (profile == null || profile.builtIn()) {
      return;
    }
    int answer =
        JOptionPane.showConfirmDialog(
            this,
            Messages.getString("ProfilePrefView.delete_confirm").formatted(profile.name()),
            getTitle(),
            JOptionPane.YES_NO_OPTION);
    if (answer == JOptionPane.YES_OPTION) {
      pending.remove(profile.id());
      registry.deleteUser(profile.id());
      fillProfiles(profile.id());
    }
  }

  private String uniqueId(String base) {
    String id = base;
    int n = 2;
    while (registry.profile(id).isPresent() || pending.containsKey(id)) {
      id = base + "-" + n++;
    }
    return id;
  }

  private JFileChooser chooser() {
    JFileChooser chooser = new JFileChooser();
    chooser.setFileFilter(
        new FileNameExtensionFilter(Messages.getString("ProfilePrefView.json"), "json")); // NON-NLS
    return chooser;
  }

  private void importProfiles() {
    JFileChooser chooser = chooser();
    if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
      try {
        commitEditor();
        List<MeasurementProfile> imported = registry.importFrom(chooser.getSelectedFile().toPath());
        fillProfiles(imported.isEmpty() ? null : imported.getFirst().id());
      } catch (IOException | RuntimeException e) {
        LOGGER.error("Cannot import measurement profiles", e);
        JOptionPane.showMessageDialog(this, e.getMessage(), getTitle(), JOptionPane.ERROR_MESSAGE);
      }
    }
  }

  private void exportProfile() {
    commitEditor();
    MeasurementProfile profile = (MeasurementProfile) profileCombo.getSelectedItem();
    if (profile == null) {
      return;
    }
    JFileChooser chooser = chooser();
    chooser.setSelectedFile(Path.of(profile.id() + ".json").toFile()); // NON-NLS
    if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
      try {
        registry.exportTo(
            chooser.getSelectedFile().toPath(),
            List.of(pending.getOrDefault(profile.id(), profile)));
      } catch (IOException e) {
        LOGGER.error("Cannot export the measurement profile", e);
        JOptionPane.showMessageDialog(this, e.getMessage(), getTitle(), JOptionPane.ERROR_MESSAGE);
      }
    }
  }

  @Override
  public void closeAdditionalWindow() {
    commitEditor();
    pending.values().forEach(registry::saveUser);
    pending.clear();
  }

  @Override
  public void resetToDefaultValues() {
    pending.clear();
    MeasurementProfile selected = (MeasurementProfile) profileCombo.getSelectedItem();
    fillProfiles(selected == null ? null : selected.id());
  }

  // ---- Scrolling -------------------------------------------------------------------------

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

  /** A square of the line color, on the button that changes it. */
  private record ColorIcon(Color color) implements Icon {
    private static final int SIZE = 16;

    @Override
    public void paintIcon(Component c, Graphics g, int x, int y) {
      g.setColor(color);
      g.fillRect(x, y, getIconWidth(), getIconHeight());
      g.setColor(Color.GRAY);
      g.drawRect(x, y, getIconWidth() - 1, getIconHeight() - 1);
    }

    @Override
    public int getIconWidth() {
      return GuiUtils.getScaleLength(2 * SIZE);
    }

    @Override
    public int getIconHeight() {
      return GuiUtils.getScaleLength(SIZE);
    }
  }
}
