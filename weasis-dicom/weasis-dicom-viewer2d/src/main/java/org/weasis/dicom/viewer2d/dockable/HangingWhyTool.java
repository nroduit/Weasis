/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.viewer2d.dockable;

import java.awt.BorderLayout;
import java.awt.Component;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.gui.util.GuiExecutor;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.util.ResourceUtil;
import org.weasis.core.api.util.ResourceUtil.OtherIcon;
import org.weasis.core.ui.docking.PluginTool;
import org.weasis.core.ui.editor.SeriesViewerEvent;
import org.weasis.core.ui.editor.SeriesViewerEvent.EVENT;
import org.weasis.core.ui.editor.SeriesViewerListener;
import org.weasis.core.ui.editor.image.ImageViewerPlugin;
import org.weasis.core.ui.editor.image.ViewCanvas;
import org.weasis.core.ui.editor.image.ViewerPlugin;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.codec.hp.HangingPlan.Alternative;
import org.weasis.dicom.codec.hp.HangingPlan.CellPlan;
import org.weasis.dicom.explorer.hp.ActiveHanging;
import org.weasis.dicom.explorer.hp.HangingPlanApplier;
import org.weasis.dicom.explorer.hp.HangingPlanApplier.Applied;
import org.weasis.dicom.viewer2d.EventManager;
import org.weasis.dicom.viewer2d.Messages;

/**
 * The <i>why</i> panel: for the selected cell of a hung viewer, the protocol and the display set,
 * how the series matched, the rules that selected it and the alternatives of its image set. Picking
 * an alternative puts it in the cell and records the correction; the rule the correction proposes
 * is shown, and can be written into the protocol when it is a user one. A detail tool: it opens in
 * the bottom slot of the tools side.
 */
public class HangingWhyTool extends PluginTool implements SeriesViewerListener {

  private static final Logger LOGGER = LoggerFactory.getLogger(HangingWhyTool.class);
  public static final String BUTTON_NAME = Messages.getString("HangingWhyTool.title");

  private record Choice(String series, String label) {
    @Override
    public String toString() {
      return label;
    }
  }

  private final JLabel header = new JLabel();
  private final JLabel match = new JLabel();
  private final DefaultListModel<String> reasons = new DefaultListModel<>();
  private final DefaultListModel<Choice> alternatives = new DefaultListModel<>();
  private final JList<Choice> alternativeList = new JList<>(alternatives);
  private final JButton use = new JButton(Messages.getString("HangingWhyTool.use"));
  private final JLabel proposal = new JLabel();
  private final JButton applyRule = new JButton(Messages.getString("HangingWhyTool.apply"));
  private ViewerPlugin<?> viewer;
  private int cell = -1;
  private org.weasis.dicom.explorer.hp.Corrections.Correction lastCorrection;

  public HangingWhyTool() {
    super("hp-why", BUTTON_NAME, Type.TOOL_EXT, 40); // NON-NLS
    setIcon(ResourceUtil.getIcon(OtherIcon.VIEW_SETTING));
    setDockableWidth(300);
    setLayout(new BorderLayout());
    JPanel content = new JPanel();
    content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
    content.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
    header.setAlignmentX(Component.LEFT_ALIGNMENT);
    match.setAlignmentX(Component.LEFT_ALIGNMENT);
    content.add(header);
    content.add(match);
    content.add(Box.createVerticalStrut(5));
    content.add(titled(Messages.getString("HangingWhyTool.rules"), new JList<>(reasons)));
    alternativeList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    alternativeList.addListSelectionListener(
        e -> use.setEnabled(alternativeList.getSelectedValue() != null));
    content.add(titled(Messages.getString("HangingWhyTool.alternatives"), alternativeList));
    use.setEnabled(false);
    use.setAlignmentX(Component.LEFT_ALIGNMENT);
    use.addActionListener(e -> useAlternative());
    content.add(use);
    content.add(Box.createVerticalStrut(8));
    proposal.setAlignmentX(Component.LEFT_ALIGNMENT);
    content.add(proposal);
    applyRule.setAlignmentX(Component.LEFT_ALIGNMENT);
    applyRule.setEnabled(false);
    applyRule.addActionListener(e -> applyProposal());
    content.add(applyRule);
    add(new JScrollPane(content), BorderLayout.CENTER);
    HangingPlanApplier.addListener(applied -> GuiExecutor.execute(this::refreshSelected));
    refresh(null, null);
  }

  private static JPanel titled(String title, Component component) {
    JPanel panel = new JPanel(new BorderLayout());
    panel.setBorder(BorderFactory.createTitledBorder(title));
    panel.add(component, BorderLayout.CENTER);
    panel.setAlignmentX(Component.LEFT_ALIGNMENT);
    return panel;
  }

  @Override
  public void changingViewContentEvent(SeriesViewerEvent event) {
    EVENT e = event.getEventType();
    if ((EVENT.SELECT_VIEW.equals(e) || EVENT.SELECT.equals(e) || EVENT.LAYOUT.equals(e))
        && event.getSeriesViewer() instanceof ImageViewerPlugin<?> plugin) {
      refreshSelected();
    }
  }

  private void refreshSelected() {
    ImageViewerPlugin<DicomImageElement> container =
        EventManager.getInstance().getSelectedView2dContainer();
    refresh(container, container == null ? null : container.getSelectedViewCanvas());
  }

  /** Shows the cell of a canvas, or nothing when its viewer was not hung by a protocol. */
  public void refresh(
      ImageViewerPlugin<DicomImageElement> container, ViewCanvas<DicomImageElement> canvas) {
    reasons.clear();
    alternatives.clear();
    use.setEnabled(false);
    Optional<Applied> active = container == null ? Optional.empty() : ActiveHanging.of(container);
    int position = container == null ? -1 : container.cellPositionOf(canvas);
    Optional<CellPlan> plan = active.flatMap(a -> a.cellAt(container, position));
    if (active.isEmpty() || plan.isEmpty()) {
      viewer = null;
      cell = -1;
      header.setText(Messages.getString("HangingWhyTool.none"));
      match.setText("");
      return;
    }
    viewer = container;
    cell = position;
    Applied applied = active.get();
    CellPlan c = plan.get();
    String name =
        applied.plan().name() == null ? applied.plan().protocolId() : applied.plan().name();
    header.setText(
        String.format(
            Messages.getString("HangingWhyTool.header"),
            name,
            position + 1,
            c.label() == null ? "" : c.label()));
    match.setText(
        String.format(
            Messages.getString("HangingWhyTool.match"),
            c.match().name().toLowerCase(Locale.ROOT),
            c.score()));
    c.reasons().forEach(reasons::addElement);
    for (Alternative a : c.alternatives()) {
      alternatives.addElement(
          new Choice(
              a.series(),
              (a.description() == null ? a.series() : a.description())
                  + String.format(Locale.ROOT, "  (%.2f)", a.score())));
    }
    if (lastCorrection == null || !lastCorrection.protocol().equals(applied.plan().protocolId())) {
      proposal.setText("");
      applyRule.setEnabled(false);
    }
  }

  private void useAlternative() {
    Choice choice = alternativeList.getSelectedValue();
    if (choice == null || viewer == null || cell < 0) {
      return;
    }
    Optional<Applied> active = ActiveHanging.of(viewer);
    if (active.isEmpty()) {
      return;
    }
    try {
      ActiveHanging.Corrected corrected =
          ActiveHanging.correct(active.get(), cell, choice.series());
      lastCorrection = corrected.correction();
      List<String> rule =
          corrected.correction().proposal().stream().map(s -> s.describe()).toList();
      proposal.setText(
          "<html>" // NON-NLS
              + Messages.getString("HangingWhyTool.proposal")
              + "<br>" // NON-NLS
              + String.join("<br>", rule) // NON-NLS
              + "</html>"); // NON-NLS
      applyRule.setEnabled(corrected.userProtocol() && !rule.isEmpty());
    } catch (RuntimeException e) {
      LOGGER.warn("Cannot correct the cell", e);
      proposal.setText(e.getMessage());
    }
  }

  private void applyProposal() {
    if (lastCorrection != null && ActiveHanging.applyProposal(lastCorrection)) {
      applyRule.setEnabled(false);
      proposal.setText(Messages.getString("HangingWhyTool.applied"));
    }
  }

  /** Whether the tool is of use: a 2D viewer hung by a protocol is selected. */
  public static boolean isRelevant() {
    return GuiUtils.getUICore().getSelectedPlugin().flatMap(ActiveHanging::of).isPresent();
  }
}
