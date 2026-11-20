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

import com.formdev.flatlaf.FlatClientProperties;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.DefaultListSelectionModel;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;
import javax.swing.ScrollPaneConstants;
import javax.swing.Scrollable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.gui.util.GuiExecutor;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.util.ResourceUtil;
import org.weasis.core.api.util.ResourceUtil.OtherIcon;
import org.weasis.core.ui.docking.PluginTool;
import org.weasis.core.ui.editor.Relevance;
import org.weasis.core.ui.editor.SeriesViewerEvent;
import org.weasis.core.ui.editor.SeriesViewerEvent.EVENT;
import org.weasis.core.ui.editor.SeriesViewerListener;
import org.weasis.core.ui.editor.ViewContext;
import org.weasis.core.ui.editor.image.ImageViewerPlugin;
import org.weasis.core.ui.editor.image.ViewCanvas;
import org.weasis.core.ui.editor.image.ViewerPlugin;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.codec.hp.HangingPlan.Alternative;
import org.weasis.dicom.codec.hp.HangingPlan.CellPlan;
import org.weasis.dicom.codec.hp.ReasonProvenance;
import org.weasis.dicom.codec.hp.SeriesFeatures;
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
  private final JList<String> ruleList = new TipList<>(reasons);
  private final JList<Choice> alternativeList = new TipList<>(alternatives);
  private final JLabel rulesTitle = section(Messages.getString("HangingWhyTool.rules"));
  private final JLabel alternativesTitle =
      section(Messages.getString("HangingWhyTool.alternatives"));
  private final JButton use = new JButton(Messages.getString("HangingWhyTool.use"));
  private final JLabel proposal = new JLabel();
  private final JButton applyRule = new JButton(Messages.getString("HangingWhyTool.apply"));
  private ViewerPlugin<?> viewer;
  private int cell = -1;
  private org.weasis.dicom.explorer.hp.Corrections.Correction lastCorrection;

  public HangingWhyTool() {
    super("hp-why", BUTTON_NAME, Type.TOOL_EXT, 40); // NON-NLS
    setIcon(ResourceUtil.getIcon(OtherIcon.HANGING_WHY));
    setDockableWidth(300);
    setLayout(new BorderLayout());
    JPanel content = new WidthTrackingPanel();
    content.setLayout(new BoxLayout(content, BoxLayout.PAGE_AXIS));
    content.setBorder(GuiUtils.getEmptyBorder(4, 6, 4, 6));
    header.putClientProperty(FlatClientProperties.STYLE_CLASS, "semibold"); // NON-NLS
    small(match);
    match.setForeground(GuiUtils.getMenuHintColor());
    content.add(left(header));
    content.add(left(match));
    // the rules are written in the notation of the protocols (field, operator, values),
    // untranslated like the commands: they read left to right in every language
    GuiUtils.fixOrientation(ruleList);
    ruleList.setFocusable(false);
    ruleList.setSelectionModel(new NoSelection());
    content.add(left(rulesTitle));
    content.add(left(small(ruleList)));
    alternativeList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    alternativeList.addListSelectionListener(
        e -> use.setEnabled(alternativeList.getSelectedValue() != null));
    alternativeList.addMouseListener(
        new MouseAdapter() {
          @Override
          public void mouseClicked(MouseEvent e) {
            if (e.getClickCount() == 2 && alternativeList.getSelectedValue() != null) {
              useAlternative();
            }
          }
        });
    content.add(left(alternativesTitle));
    content.add(left(small(alternativeList)));
    use.setEnabled(false);
    use.putClientProperty(FlatClientProperties.STYLE_CLASS, "small"); // NON-NLS
    use.addActionListener(e -> useAlternative());
    content.add(Box.createVerticalStrut(GuiUtils.getScaleLength(3)));
    content.add(left(use));
    content.add(Box.createVerticalStrut(GuiUtils.getScaleLength(4)));
    content.add(left(small(proposal)));
    applyRule.setEnabled(false);
    applyRule.setVisible(false);
    applyRule.putClientProperty(FlatClientProperties.STYLE_CLASS, "small"); // NON-NLS
    applyRule.addActionListener(e -> applyProposal());
    content.add(left(applyRule));
    JScrollPane scroll = new JScrollPane(content);
    scroll.setBorder(BorderFactory.createEmptyBorder());
    scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
    add(scroll, BorderLayout.CENTER);
    HangingPlanApplier.addListener(applied -> GuiExecutor.execute(this::refreshSelected));
    refresh(null, null);
  }

  private static <T extends JComponent> T left(T component) {
    component.setAlignmentX(Component.LEFT_ALIGNMENT);
    return component;
  }

  private static <T extends JComponent> T small(T component) {
    component.putClientProperty(FlatClientProperties.STYLE_CLASS, "small"); // NON-NLS
    return component;
  }

  /** The title of a group of lines: small and dimmed, as the sections of a menu. */
  private static JLabel section(String title) {
    JLabel label = small(new JLabel(title));
    label.setForeground(GuiUtils.getMenuHintColor());
    label.setBorder(GuiUtils.getEmptyBorder(6, 0, 1, 0));
    return label;
  }

  /** As wide as the tool: a long line is cut with an ellipsis instead of scrolling sideways. */
  private static final class WidthTrackingPanel extends JPanel implements Scrollable {
    @Override
    public Dimension getPreferredScrollableViewportSize() {
      return getPreferredSize();
    }

    @Override
    public int getScrollableUnitIncrement(Rectangle visible, int orientation, int direction) {
      return GuiUtils.getScaleLength(16);
    }

    @Override
    public int getScrollableBlockIncrement(Rectangle visible, int orientation, int direction) {
      return visible.height;
    }

    @Override
    public boolean getScrollableTracksViewportWidth() {
      return true;
    }

    @Override
    public boolean getScrollableTracksViewportHeight() {
      return false;
    }
  }

  /** A list without a frame whose lines say in a tooltip what the width cuts. */
  private static final class TipList<E> extends JList<E> {
    TipList(DefaultListModel<E> model) {
      super(model);
      setOpaque(false);
      ((DefaultListCellRenderer) getCellRenderer()).setOpaque(false);
      setToolTipText("");
    }

    @Override
    public String getToolTipText(MouseEvent event) {
      int index = locationToIndex(event.getPoint());
      return index >= 0 && getCellBounds(index, index).contains(event.getPoint())
          ? String.valueOf(getModel().getElementAt(index))
          : null;
    }

    @Override
    public Dimension getMaximumSize() {
      return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
    }
  }

  /** The rules are read, not picked. */
  private static final class NoSelection extends DefaultListSelectionModel {
    @Override
    public void setSelectionInterval(int index0, int index1) {
      // nothing is selected
    }

    @Override
    public void addSelectionInterval(int index0, int index1) {
      // nothing is selected
    }
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
    boolean hung = active.isPresent() && plan.isPresent();
    match.setVisible(hung);
    rulesTitle.setVisible(hung);
    ruleList.setVisible(hung);
    if (!hung) {
      viewer = null;
      cell = -1;
      header.setText(
          GuiUtils.HTML_START + Messages.getString("HangingWhyTool.none") + GuiUtils.HTML_END);
      alternativesTitle.setVisible(false);
      alternativeList.setVisible(false);
      use.setVisible(false);
      proposal.setText("");
      applyRule.setVisible(false);
      return;
    }
    viewer = container;
    cell = position;
    Applied applied = active.get();
    CellPlan c = plan.get();
    String name =
        applied.plan().name() == null ? applied.plan().protocolId() : applied.plan().name();
    header.setText(c.label() == null || c.label().isBlank() ? name : name + " \u00B7 " + c.label());
    match.setText(
        Messages.format(
            "HangingWhyTool.cell",
            position + 1,
            Messages.getString("InfoLayer.hp." + c.match().name().toLowerCase(Locale.ROOT)),
            c.score()));
    boolean choices = !c.alternatives().isEmpty();
    alternativesTitle.setVisible(choices);
    alternativeList.setVisible(choices);
    use.setVisible(choices);
    SeriesFeatures features = ActiveHanging.featuresOf(applied, c.series()).orElse(null);
    c.reasons().forEach(r -> reasons.addElement(ReasonProvenance.annotate(r, features)));
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
      applyRule.setVisible(false);
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
      applyRule.setVisible(applyRule.isEnabled());
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

  /** The panel explains a hanging: relevant once a protocol hung the viewer. */
  @Override
  public boolean isRelevant(ViewContext context) {
    return Relevance.PROTOCOL.test(context);
  }
}
