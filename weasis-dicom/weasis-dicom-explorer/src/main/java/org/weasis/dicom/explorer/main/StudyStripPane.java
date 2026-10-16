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
import java.awt.Dimension;
import java.awt.event.InputEvent;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JToggleButton;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingConstants;
import net.miginfocom.swing.MigLayout;
import org.dcm4che3.data.Tag;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.media.data.IdentityMask;
import org.weasis.core.api.media.data.MediaSeriesGroup;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.api.util.FontItem;
import org.weasis.core.util.StringUtil;
import org.weasis.dicom.codec.TagD;
import org.weasis.dicom.explorer.DicomModel;
import org.weasis.dicom.explorer.Messages;
import org.weasis.dicom.explorer.items.SeriesItem;
import org.weasis.dicom.explorer.items.StudyContents;

/**
 * The timeline strip of the DICOM explorer: one card per study of the selected patient in time
 * order, with its date, modalities, series count and non-image objects, the year shown where it
 * changes. A click keeps that study only in the body (the {@code studies} facet of the filter), a
 * control-click adds or removes one for a comparison, a click on the only selected card shows all
 * again. The strip hides itself when the patient has fewer than two studies.
 */
public class StudyStripPane extends JPanel {

  private final DicomExplorer explorer;
  private final JPanel cards = new JPanel(new MigLayout("ins 2 4 2 4, gap 4 0, nogrid")); // NON-NLS
  private final JScrollPane scroll =
      new JScrollPane(
          cards,
          ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER,
          ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
  private final Set<MediaSeriesGroup> selected = new LinkedHashSet<>();

  public StudyStripPane(DicomExplorer explorer) {
    super(new MigLayout("fill, ins 0", "[grow,fill]", "[]")); // NON-NLS
    this.explorer = Objects.requireNonNull(explorer);
    scroll.setBorder(BorderFactory.createEmptyBorder());
    scroll.getHorizontalScrollBar().setUnitIncrement(GuiUtils.getScaleLength(24));
    // The wheel scrolls the strip sideways: there is nothing vertical in it
    scroll.addMouseWheelListener(
        e -> {
          var bar = scroll.getHorizontalScrollBar();
          bar.setValue(bar.getValue() + e.getUnitsToScroll() * bar.getUnitIncrement());
        });
    add(scroll, "growx"); // NON-NLS
  }

  /** The studies the strip selected, in selection order. */
  public Set<MediaSeriesGroup> getSelectedStudies() {
    return Set.copyOf(selected);
  }

  /** Rebuilds the cards from the model and the current filter; hides when one study or none. */
  public void refresh() {
    DicomModel model = explorer.getDataExplorerModel();
    MediaSeriesGroup patient = explorer.getSelectedPatient();
    selected.clear();
    selected.addAll(explorer.getSeriesFilter().getStudies());
    cards.removeAll();
    List<MediaSeriesGroup> studies =
        patient == null ? List.of() : chronological(StudyContents.studiesOf(model, patient));
    selected.retainAll(studies);
    boolean shown = studies.size() >= 2;
    setVisible(shown);
    if (shown) {
      int year = Integer.MIN_VALUE;
      for (MediaSeriesGroup study : studies) {
        LocalDate date = dateOf(study);
        if (date != null && date.getYear() != year) {
          year = date.getYear();
          JLabel label = new JLabel(String.valueOf(year));
          label.setFont(FontItem.SMALL_SEMIBOLD.getFont());
          label.setVerticalAlignment(SwingConstants.BOTTOM);
          cards.add(label, "aligny bottom, gapright 2"); // NON-NLS
        }
        cards.add(card(study, StudyContents.of(model, study)));
      }
    }
    cards.revalidate();
    cards.repaint();
    if (shown) {
      // A scroll pane in a MigLayout row gets no height of its own: state it from the cards
      int height = cards.getPreferredSize().height;
      height += scroll.getHorizontalScrollBar().getPreferredSize().height;
      Dimension size = new Dimension(GuiUtils.getScaleLength(40), height);
      scroll.setPreferredSize(size);
      scroll.setMinimumSize(size);
      setPreferredSize(size);
      setMinimumSize(size);
    }
    revalidate();
    repaint();
    if (shown && selected.isEmpty()) {
      // The most recent study is the one being read: start there, as the body does
      javax.swing.SwingUtilities.invokeLater(
          () -> {
            var bar = scroll.getHorizontalScrollBar();
            bar.setValue(bar.getMaximum());
          });
    }
  }

  private JToggleButton card(MediaSeriesGroup study, StudyContents contents) {
    List<SeriesItem> visible = contents.series();
    Set<String> modalities = new TreeSet<>();
    for (SeriesItem item : visible) {
      if (StringUtil.hasText(item.modality())) {
        modalities.add(item.modality());
      }
    }
    String date = masked(study, Tag.StudyDate);
    StringBuilder text = new StringBuilder("<html><b>"); // NON-NLS
    text.append(date.isEmpty() ? Messages.getString("DicomExplorer.col_study") : date);
    text.append("</b><br>"); // NON-NLS
    text.append(String.join(" ", modalities));
    if (!visible.isEmpty()) {
      text.append(modalities.isEmpty() ? "" : " · ").append(visible.size());
    }
    if (contents.hasObjects()) {
      text.append("<br><small>"); // NON-NLS
      List<String> parts = new ArrayList<>();
      contents.countByKind().forEach((k, n) -> parts.add(n + " " + k.getId().toUpperCase()));
      text.append(String.join(", ", parts)).append("</small>"); // NON-NLS
    }
    text.append("</html>"); // NON-NLS

    JToggleButton button = new JToggleButton(text.toString());
    button.setFont(FontItem.SMALL.getFont());
    button.setFocusable(false);
    button.setHorizontalAlignment(SwingConstants.LEFT);
    button.setMargin(new java.awt.Insets(2, 4, 2, 4));
    button.putClientProperty(FlatClientProperties.STYLE, "arc: 8"); // NON-NLS
    button.setSelected(selected.contains(study));
    String description = masked(study, Tag.StudyDescription);
    button.setToolTipText(description.isEmpty() ? null : description);
    button.addActionListener(
        e -> {
          boolean multi = (e.getModifiers() & InputEvent.CTRL_MASK) != 0;
          select(study, multi);
        });
    return button;
  }

  /** Applies a card click: one study, one more or one less with control, or all again. */
  void select(MediaSeriesGroup study, boolean multi) {
    if (multi) {
      if (!selected.remove(study)) {
        selected.add(study);
      }
    } else if (selected.size() == 1 && selected.contains(study)) {
      selected.clear();
    } else {
      selected.clear();
      selected.add(study);
    }
    explorer.getSeriesFilter().setStudies(selected);
    explorer.refreshFilter();
  }

  private static List<MediaSeriesGroup> chronological(List<MediaSeriesGroup> studies) {
    List<MediaSeriesGroup> list = new ArrayList<>(studies);
    list.sort(
        (a, b) -> {
          LocalDate da = dateOf(a);
          LocalDate db = dateOf(b);
          if (da == null || db == null) {
            return da == null ? (db == null ? 0 : 1) : -1;
          }
          return da.compareTo(db);
        });
    return list;
  }

  private static LocalDate dateOf(MediaSeriesGroup study) {
    return TagD.getTagValue(study, Tag.StudyDate, LocalDate.class);
  }

  /** Masked and formatted value of a tag, empty when absent. */
  private static String masked(MediaSeriesGroup group, int tagId) {
    TagW tag = TagD.get(tagId);
    Object value = IdentityMask.maskValue(tag, group.getTagValue(tag));
    String text = value == null ? null : tag.getFormattedTagValue(value, null);
    return StringUtil.hasText(text) ? text : "";
  }
}
