/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.explorer.main;

import com.formdev.flatlaf.FlatClientProperties;
import com.formdev.flatlaf.icons.FlatClearIcon;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import javax.swing.JButton;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import org.dcm4che3.data.Tag;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.gui.util.WrapLayout;
import org.weasis.core.api.media.data.IdentityMask;
import org.weasis.core.api.media.data.MediaSeriesGroup;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.api.util.FontItem;
import org.weasis.core.ui.util.TitleMenuItem;
import org.weasis.core.util.StringUtil;
import org.weasis.dicom.codec.ObjectKind;
import org.weasis.dicom.codec.TagD;
import org.weasis.dicom.explorer.Messages;
import org.weasis.dicom.explorer.items.DownloadState;
import org.weasis.dicom.explorer.items.FacetCounts;
import org.weasis.dicom.explorer.items.InterestSource;

/**
 * The active facets of the series filter as chips under the filter field: one chip per value,
 * removed by a click, the count of the series shown first and a <i>Clear</i> chip last. Hidden when
 * no facet is active. The chips wrap when the panel is narrow, since its width belongs to the user.
 */
final class FacetChipBar extends JPanel {

  private final DicomExplorer explorer;
  private final JLabel countLabel = new JLabel();
  private boolean relayoutPending;

  FacetChipBar(DicomExplorer explorer) {
    super(
        new WrapLayout(FlowLayout.LEADING, GuiUtils.getScaleLength(3), GuiUtils.getScaleLength(2)));
    this.explorer = explorer;
    setOpaque(false);
    countLabel.setFont(FontItem.SMALL.getFont());
    setVisible(false);
  }

  /** Never drives the width of the panel: the chips wrap against the width it is given. */
  @Override
  public Dimension getPreferredSize() {
    Dimension size = super.getPreferredSize();
    size.width = GuiUtils.getScaleLength(30);
    return size;
  }

  @Override
  public Dimension getMinimumSize() {
    return getPreferredSize();
  }

  /** Once the width is known the rows may differ from the first guess: ask for a new pass. */
  @Override
  public void doLayout() {
    super.doLayout();
    if (!relayoutPending && super.getPreferredSize().height != getHeight()) {
      relayoutPending = true;
      SwingUtilities.invokeLater(
          () -> {
            relayoutPending = false;
            revalidate();
          });
    }
  }

  /** Rebuilds the chips from the filter; {@code counts} is {matching, total} or null. */
  void update(SeriesFilter filter, int[] counts) {
    removeAll();
    boolean active = filter.isActive();
    setVisible(active);
    if (!active) {
      revalidate();
      repaint();
      return;
    }
    if (counts != null && counts.length == 2) {
      countLabel.setText(Messages.format("DicomExplorer.filter_status", counts[0], counts[1]));
      add(countLabel);
    }
    String facetText = Messages.common("text");
    if (!filter.getText().isEmpty()) {
      add(chip("“" + filter.getText() + "”", facetText, () -> filter.setText(null)));
    }
    String facetStudy = Messages.common("study");
    MediaSeriesGroup study = filter.getStudy();
    if (study != null) {
      add(chip(studyLabel(study), facetStudy, () -> filter.setStudy(null)));
    }
    for (MediaSeriesGroup selected : filter.getStudies()) {
      add(
          chip(
              studyLabel(selected),
              facetStudy,
              () -> {
                Set<MediaSeriesGroup> studies = new HashSet<>(filter.getStudies());
                studies.remove(selected);
                filter.setStudies(studies);
              }));
    }
    String facetModality = Messages.common("modality");
    List<String> modalities = new ArrayList<>(filter.getModalities());
    modalities.sort(null);
    for (String modality : modalities) {
      add(
          chip(
              modality,
              facetModality,
              () -> {
                Set<String> values = new HashSet<>(filter.getModalities());
                values.remove(modality);
                filter.setModalities(values);
              }));
    }
    String facetObjects = Messages.getString("DicomExplorer.facet_objects");
    for (ObjectKind kind : filter.getObjectKinds()) {
      add(
          chip(
              kindLabel(kind),
              facetObjects,
              () -> {
                Set<ObjectKind> kinds = EnumSet.noneOf(ObjectKind.class);
                kinds.addAll(filter.getObjectKinds());
                kinds.remove(kind);
                filter.setObjectKinds(kinds);
              }));
    }
    String facetInterest = Messages.getString("DicomExplorer.facet_interest");
    if (filter.isInterestAny()) {
      add(
          chip(
              "★ " + Messages.getString("DicomExplorer.facet_any"),
              facetInterest,
              () -> filter.setInterest(false, filter.getInterestSources())));
    }
    for (InterestSource source : filter.getInterestSources()) {
      add(
          chip(
              "★ " + sourceLabel(source),
              facetInterest,
              () -> {
                Set<InterestSource> sources = EnumSet.noneOf(InterestSource.class);
                sources.addAll(filter.getInterestSources());
                sources.remove(source);
                filter.setInterest(filter.isInterestAny(), sources);
              }));
    }
    String facetState = Messages.getString("DicomExplorer.facet_state");
    for (DownloadState state : filter.getDownloadStates()) {
      add(
          chip(
              stateLabel(state),
              facetState,
              () -> {
                Set<DownloadState> states = EnumSet.noneOf(DownloadState.class);
                states.addAll(filter.getDownloadStates());
                states.remove(state);
                filter.setDownloadStates(states);
              }));
    }
    JButton clear = new JButton(Messages.common("clear"));
    style(clear);
    clear.setToolTipText(Messages.getString("DicomExplorer.filter_clear_tip"));
    clear.addActionListener(
        _ -> {
          filter.clear();
          explorer.refreshFilter();
        });
    add(clear);
    revalidate();
    repaint();
  }

  /**
   * Builds the facet menu: the modalities, kinds of referencing objects, sources of images of
   * interest and download states present for the selected patient, each with the number of series
   * it keeps, checked when active. {@code apply} runs after a change; built on each click so the
   * counts are current.
   */
  static JPopupMenu menu(FacetCounts counts, SeriesFilter filter, Runnable apply) {
    JPopupMenu popup = new JPopupMenu();
    if (!counts.modalities().isEmpty()) {
      popup.add(new TitleMenuItem(Messages.common("modality")));
      counts
          .modalities()
          .forEach(
              (modality, n) ->
                  popup.add(
                      item(
                          apply,
                          modality,
                          n,
                          filter.getModalities().contains(modality),
                          () -> {
                            Set<String> values = new TreeSet<>(filter.getModalities());
                            if (!values.remove(modality)) {
                              values.add(modality);
                            }
                            filter.setModalities(values);
                          })));
    }
    if (!counts.objectKinds().isEmpty()) {
      popup.addSeparator();
      popup.add(new TitleMenuItem(Messages.getString("DicomExplorer.facet_objects")));
      counts
          .objectKinds()
          .forEach(
              (kind, n) ->
                  popup.add(
                      item(
                          apply,
                          kindLabel(kind),
                          n,
                          filter.getObjectKinds().contains(kind),
                          () -> {
                            Set<ObjectKind> kinds = EnumSet.noneOf(ObjectKind.class);
                            kinds.addAll(filter.getObjectKinds());
                            if (!kinds.remove(kind)) {
                              kinds.add(kind);
                            }
                            filter.setObjectKinds(kinds);
                          })));
    }
    if (counts.interestAny() > 0) {
      popup.addSeparator();
      popup.add(new TitleMenuItem(Messages.getString("DicomExplorer.facet_interest")));
      popup.add(
          item(
              apply,
              "\u2605 " + Messages.getString("DicomExplorer.facet_any"),
              counts.interestAny(),
              filter.isInterestAny(),
              () -> filter.setInterest(!filter.isInterestAny(), filter.getInterestSources())));
      counts
          .interestSources()
          .forEach(
              (source, n) ->
                  popup.add(
                      item(
                          apply,
                          "\u2605 " + sourceLabel(source),
                          n,
                          filter.getInterestSources().contains(source),
                          () -> {
                            Set<InterestSource> sources = EnumSet.noneOf(InterestSource.class);
                            sources.addAll(filter.getInterestSources());
                            if (!sources.remove(source)) {
                              sources.add(source);
                            }
                            filter.setInterest(filter.isInterestAny(), sources);
                          })));
    }
    if (counts.hasTransfers()) {
      popup.addSeparator();
      popup.add(new TitleMenuItem(Messages.getString("DicomExplorer.facet_state")));
      counts
          .downloadStates()
          .forEach(
              (state, n) ->
                  popup.add(
                      item(
                          apply,
                          stateLabel(state),
                          n,
                          filter.getDownloadStates().contains(state),
                          () -> {
                            Set<DownloadState> states = EnumSet.noneOf(DownloadState.class);
                            states.addAll(filter.getDownloadStates());
                            if (!states.remove(state)) {
                              states.add(state);
                            }
                            filter.setDownloadStates(states);
                          })));
    }
    if (popup.getComponentCount() > 0) {
      popup.addSeparator();
    }
    JMenuItem clear = new JMenuItem(Messages.common("clear"));
    clear.setEnabled(filter.isActive());
    clear.addActionListener(
        _ -> {
          filter.clear();
          apply.run();
        });
    popup.add(clear);
    return popup;
  }

  private static JCheckBoxMenuItem item(
      Runnable apply, String label, int count, boolean selected, Runnable toggle) {
    JCheckBoxMenuItem item = new JCheckBoxMenuItem(label + " (" + count + ")");
    item.setSelected(selected);
    item.addActionListener(
        _ -> {
          toggle.run();
          apply.run();
        });
    return item;
  }

  private JButton chip(String text, String facet, Runnable remove) {
    JButton chip = new JButton(text, new FlatClearIcon());
    style(chip);
    chip.setHorizontalTextPosition(SwingConstants.LEFT);
    chip.setIconTextGap(GuiUtils.getScaleLength(3));
    chip.setToolTipText(Messages.format("DicomExplorer.chip_remove", facet, text));
    chip.addActionListener(
        _ -> {
          remove.run();
          explorer.refreshFilter();
        });
    return chip;
  }

  private static void style(JButton chip) {
    chip.setFont(FontItem.SMALL.getFont());
    chip.setFocusable(false);
    chip.putClientProperty(
        FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_ROUND_RECT);
    chip.putClientProperty(FlatClientProperties.STYLE, "margin: 1,6,1,5"); // NON-NLS
  }

  /** The study date as the panes show it (masked under the identity policy), else its ID. */
  static String studyLabel(MediaSeriesGroup study) {
    String date = formatted(study, Tag.StudyDate);
    if (!date.isEmpty()) {
      return date;
    }
    String id = formatted(study, Tag.StudyID);
    return id.isEmpty() ? Messages.common("study") : id;
  }

  static String kindLabel(ObjectKind kind) {
    return kind == ObjectKind.REJECTION_NOTE
        ? Messages.getString("DicomExplorer.facet_rejected")
        : kind.getId().toUpperCase();
  }

  static String sourceLabel(InterestSource source) {
    return switch (source) {
      case KEY_OBJECT -> "KO"; // NON-NLS
      case GRAPHICS -> Messages.getString("DicomExplorer.facet_graphics");
      case PRESENTATION_STATE -> "PR"; // NON-NLS
      case SEGMENTATION -> "SEG"; // NON-NLS
    };
  }

  static String stateLabel(DownloadState state) {
    return switch (state) {
      case COMPLETE -> Messages.getString("DicomExplorer.state_complete");
      case PENDING -> Messages.getString("DicomExplorer.state_pending");
      case DOWNLOADING -> Messages.getString("DicomExplorer.state_downloading");
    };
  }

  private static String formatted(MediaSeriesGroup group, int tagId) {
    TagW tag = TagD.get(tagId);
    Object masked = IdentityMask.maskValue(tag, group.getTagValue(tag));
    String text = masked == null ? null : tag.getFormattedTagValue(masked, null);
    return StringUtil.hasText(text) ? text : "";
  }
}
