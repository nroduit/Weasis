/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.viewer2d;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Window;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.hp.HangingProtocolDocuments;
import org.weasis.core.api.media.data.MediaSeriesGroup;
import org.weasis.core.api.util.LayeredEntries.Origin;
import org.weasis.core.ui.editor.image.HangingLayout;
import org.weasis.core.ui.editor.image.ImageViewerPlugin;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.codec.TagD;
import org.weasis.dicom.codec.hp.FeatureSelector;
import org.weasis.dicom.codec.hp.HangingEngine.Candidate;
import org.weasis.dicom.codec.hp.ProtocolDeriver;
import org.weasis.dicom.codec.hp.ProtocolDeriver.CellRule;
import org.weasis.dicom.codec.hp.ProtocolDeriver.Derivation;
import org.weasis.dicom.codec.hp.ProtocolJson;
import org.weasis.dicom.codec.hp.StudySeries;
import org.weasis.dicom.codec.hp.ValidationReport;
import org.weasis.dicom.codec.hp.WeasisExtensions;
import org.weasis.dicom.explorer.DicomModel;
import org.weasis.dicom.explorer.hp.ActiveHanging;
import org.weasis.dicom.explorer.hp.HpStudies;

/**
 * <i>Save as protocol</i>: the review panel. The rules the deriver proposes for every cell, each
 * selector as a check box the user can drop to widen the rule, which series of the study the rule
 * keeps, the name of the protocol, and a dry run over the loaded studies before saving to the user
 * layer.
 */
public class SaveProtocolDialog extends JDialog {

  private static final Logger LOGGER = LoggerFactory.getLogger(SaveProtocolDialog.class);

  private final DicomModel model;
  private final HangingLayout layout;
  private final List<StudySeries> study;
  private final JTextField name = new JTextField(24);
  private final JLabel status = new JLabel(" ");
  private final JPanel rulesPanel = new JPanel();
  private final List<List<JCheckBox>> selectorBoxes = new ArrayList<>();
  private final List<CellRule> rules = new ArrayList<>();

  public SaveProtocolDialog(
      Window owner, DicomModel model, HangingLayout layout, List<StudySeries> study) {
    super(owner, Messages.getString("SaveProtocolDialog.title"), ModalityType.APPLICATION_MODAL);
    this.model = model;
    this.layout = layout;
    this.study = study;
    build();
    pack();
    setMinimumSize(new Dimension(520, 320));
    setLocationRelativeTo(owner);
  }

  private void build() {
    JPanel top = new JPanel(new FlowLayout(FlowLayout.LEADING));
    top.add(new JLabel(Messages.getString("SaveProtocolDialog.name")));
    top.add(name);
    add(top, BorderLayout.NORTH);

    rulesPanel.setLayout(new BoxLayout(rulesPanel, BoxLayout.PAGE_AXIS));
    Derivation initial =
        ProtocolDeriver.derive("user.draft", "draft", "SINGLE_USER", layout, study); // NON-NLS
    rules.addAll(initial.rules());
    for (CellRule rule : initial.rules()) {
      JPanel cell = new JPanel();
      cell.setLayout(new BoxLayout(cell, BoxLayout.PAGE_AXIS));
      cell.setBorder(
          BorderFactory.createTitledBorder(
              Messages.format("SaveProtocolDialog.cell", rule.cell() + 1, rule.label())));
      List<JCheckBox> boxes = new ArrayList<>();
      for (FeatureSelector selector : rule.selectors()) {
        JCheckBox box = new JCheckBox(selector.describe(), true);
        box.addActionListener(e -> updateStatus());
        boxes.add(box);
        cell.add(box);
      }
      if (rule.selectors().isEmpty()) {
        cell.add(new JLabel(Messages.getString("SaveProtocolDialog.noRule")));
      }
      cell.add(new JLabel(matchingText(rule)));
      selectorBoxes.add(boxes);
      rulesPanel.add(cell);
    }
    JScrollPane scroll = new JScrollPane(rulesPanel);
    scroll.setBorder(BorderFactory.createEmptyBorder());
    add(scroll, BorderLayout.CENTER);

    JPanel bottom = new JPanel(new BorderLayout());
    bottom.add(status, BorderLayout.NORTH);
    JPanel buttons = new JPanel(new FlowLayout(FlowLayout.TRAILING));
    JButton dryRun = new JButton(Messages.getString("SaveProtocolDialog.dryRun"));
    dryRun.addActionListener(e -> dryRun());
    JButton save = new JButton(Messages.common("save"));
    save.addActionListener(e -> save());
    JButton cancel = new JButton(Messages.common("cancel"));
    cancel.addActionListener(e -> dispose());
    buttons.add(dryRun);
    buttons.add(Box.createHorizontalStrut(10));
    buttons.add(save);
    buttons.add(cancel);
    bottom.add(buttons, BorderLayout.SOUTH);
    add(bottom, BorderLayout.SOUTH);
    getRootPane().setDefaultButton(save);
  }

  private String matchingText(CellRule rule) {
    List<String> names = new ArrayList<>();
    for (String uid : rule.matching()) {
      study.stream()
          .filter(s -> s.uid().equals(uid))
          .findFirst()
          .ifPresent(
              s -> {
                String description =
                    TagD.getTagValue(
                        s.series(), org.dcm4che3.data.Tag.SeriesDescription, String.class);
                names.add(description == null || description.isBlank() ? uid : description);
              });
    }
    return Messages.format(
        rule.unique() ? "SaveProtocolDialog.matchesOne" : "SaveProtocolDialog.matchesMany",
        String.join(", ", names));
  }

  /** The protocol as the check boxes shape it: dropped selectors widen the rule of their cell. */
  private Derivation current(String id, String protocolName) {
    Derivation base =
        ActiveHanging.withUiProfile(
            ProtocolDeriver.derive(id, protocolName, "SINGLE_USER", layout, study));
    java.util.Map<Integer, WeasisExtensions.ImageSet> sets =
        new java.util.LinkedHashMap<>(base.document().weasis().imageSets());
    for (int i = 0; i < base.rules().size() && i < selectorBoxes.size(); i++) {
      CellRule rule = base.rules().get(i);
      List<JCheckBox> boxes = selectorBoxes.get(i);
      List<FeatureSelector> kept = new ArrayList<>();
      for (int j = 0; j < rule.selectors().size() && j < boxes.size(); j++) {
        if (boxes.get(j).isSelected()) {
          kept.add(rule.selectors().get(j));
        }
      }
      for (var entry : sets.entrySet()) {
        if (entry.getValue().selectors().equals(rule.selectors())
            && rule.label().equals(entry.getValue().label())) {
          sets.put(entry.getKey(), new WeasisExtensions.ImageSet(rule.label(), kept));
        }
      }
    }
    WeasisExtensions weasis = base.document().weasis();
    WeasisExtensions edited =
        new WeasisExtensions(
            sets,
            weasis.displaySets(),
            weasis.steps(),
            weasis.placement(),
            weasis.maskingProfile(),
            weasis.loading(),
            weasis.uiProfile(),
            weasis.extra());
    return new Derivation(
        new ProtocolJson.Document(id, base.document().protocol(), edited), base.rules());
  }

  private void updateStatus() {
    status.setText(" ");
  }

  private void dryRun() {
    String protocolName = name.getText().isBlank() ? "draft" : name.getText().trim(); // NON-NLS
    Derivation derivation = current(ProtocolDeriver.idOf(protocolName), protocolName);
    List<ValidationReport.Input> inputs = new ArrayList<>();
    for (MediaSeriesGroup patient :
        model.getChildren(org.weasis.core.api.media.data.MediaSeriesGroupNode.rootNode)) {
      for (MediaSeriesGroup s : model.getChildren(patient)) {
        String description =
            TagD.getTagValue(s, org.dcm4che3.data.Tag.StudyDescription, String.class);
        inputs.add(
            new ValidationReport.Input(
                String.valueOf(s.getTagValue(s.getTagID())),
                description == null ? String.valueOf(s.getTagValue(s.getTagID())) : description,
                HpStudies.studySeries(model, s)));
      }
    }
    ValidationReport report =
        ValidationReport.run(
            List.of(new Candidate(derivation.document(), Origin.USER, true)), inputs);
    long applies = report.studies().stream().filter(l -> l.protocol() != null).count();
    status.setText(Messages.format("SaveProtocolDialog.applies", applies, report.studies().size()));
  }

  private void save() {
    String protocolName = name.getText().trim();
    if (protocolName.isEmpty()) {
      status.setText(Messages.getString("SaveProtocolDialog.nameNeeded"));
      name.requestFocusInWindow();
      return;
    }
    String id = ProtocolDeriver.idOf(protocolName);
    Derivation derivation = current(id, protocolName);
    try {
      HangingProtocolDocuments.protocols().put(ProtocolJson.toJson(derivation.document()));
    } catch (IOException | RuntimeException e) {
      LOGGER.error("Cannot store the protocol {}", id, e);
      JOptionPane.showMessageDialog(this, e.getMessage(), getTitle(), JOptionPane.ERROR_MESSAGE);
      return;
    }
    dispose();
  }

  /** Opens the review panel on the display of a 2D viewer, when it shows a loaded study. */
  public static void open(ImageViewerPlugin<DicomImageElement> viewer) {
    DicomModel model = HpStudies.model().orElse(null);
    if (model == null || viewer == null || !viewer.supportsViewportPanes()) {
      return;
    }
    HangingLayout layout = viewer.captureLayout();
    MediaSeriesGroup study = HpStudies.studyOf(model, layout).orElse(null);
    if (study == null) {
      JOptionPane.showMessageDialog(
          viewer,
          Messages.getString("SaveProtocolDialog.noStudy"),
          Messages.getString("SaveProtocolDialog.title"),
          JOptionPane.INFORMATION_MESSAGE);
      return;
    }
    Window owner = javax.swing.SwingUtilities.getWindowAncestor(viewer);
    new SaveProtocolDialog(owner, model, layout, HpStudies.studySeries(model, study))
        .setVisible(true);
  }
}
