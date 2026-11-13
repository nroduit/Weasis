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
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.InputEvent;
import java.awt.geom.RoundRectangle2D;
import java.time.LocalDate;
import java.time.Period;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import javax.swing.BorderFactory;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JToggleButton;
import javax.swing.JViewport;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import net.miginfocom.swing.MigLayout;
import org.dcm4che3.data.Tag;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.media.data.IdentityMask;
import org.weasis.core.api.media.data.MediaSeriesGroup;
import org.weasis.core.api.media.data.TagUtil;
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
 * order, with its date, modalities, series count and non-image objects, laid out on a time axis.
 * The gap between two cards grows with the time between the studies (on a compressed scale, so a
 * long history stays readable) and states that time ("3 mo"); the axis under the cards carries the
 * year where it changes and a pin per study, in the accent color when the study is selected.
 *
 * <p>A click keeps that study only in the body (the {@code studies} facet of the filter), a
 * control-click adds or removes one for a comparison, a click on the only selected card shows all
 * again. The strip hides itself when the patient has fewer than two studies.
 */
public class StudyStripPane extends JPanel {

  /** Month and day in the order of the locale, the year being on the axis. */
  private static final DateTimeFormatter SHORT_DATE = DateTimeFormatter.ofLocalizedPattern("MMMd");

  private final DicomExplorer explorer;
  private final Timeline cards = new Timeline();
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
    // The year pinned at the start of the view is painted for the visible part: no blitting
    scroll.getViewport().setScrollMode(JViewport.SIMPLE_SCROLL_MODE);
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
    cards.clear();
    List<MediaSeriesGroup> studies =
        patient == null ? List.of() : chronological(StudyContents.studiesOf(model, patient));
    selected.retainAll(studies);
    boolean shown = studies.size() >= 2;
    setVisible(shown);
    if (shown) {
      LocalDate previous = null;
      int year = Integer.MIN_VALUE;
      for (MediaSeriesGroup study : studies) {
        LocalDate date = maskedDate(study);
        boolean newYear = date != null && date.getYear() != year;
        if (newYear) {
          year = date.getYear();
        }
        Interval interval =
            previous == null || date == null ? null : Interval.between(previous, date);
        JToggleButton card = card(study, StudyContents.of(model, study), date, interval);
        cards.add(new Entry(card, newYear ? String.valueOf(year) : null, interval));
        if (date != null) {
          previous = date;
        }
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
      SwingUtilities.invokeLater(
          () -> {
            var bar = scroll.getHorizontalScrollBar();
            bar.setValue(cards.getComponentOrientation().isLeftToRight() ? bar.getMaximum() : 0);
          });
    }
  }

  private JToggleButton card(
      MediaSeriesGroup study, StudyContents contents, LocalDate date, Interval interval) {
    List<SeriesItem> visible = contents.series();
    Set<String> modalities = new TreeSet<>();
    for (SeriesItem item : visible) {
      if (StringUtil.hasText(item.modality())) {
        modalities.add(item.modality());
      }
    }
    StringBuilder text = new StringBuilder("<html><b>"); // NON-NLS
    text.append(date == null ? Messages.common("study") : SHORT_DATE.format(date));
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
    button.setHorizontalAlignment(SwingConstants.LEADING);
    button.setMargin(new Insets(2, 6, 2, 6));
    button.putClientProperty(FlatClientProperties.STYLE, "arc: 10"); // NON-NLS
    button.setSelected(selected.contains(study));
    button.setToolTipText(tooltip(study, date, interval));
    button.addActionListener(
        e -> {
          boolean multi = (e.getModifiers() & InputEvent.CTRL_MASK) != 0;
          select(study, multi);
        });
    // The pin on the axis follows the selection
    button.addItemListener(e -> cards.repaint());
    return button;
  }

  /** The full date, the description and the time since the previous study. */
  private static String tooltip(MediaSeriesGroup study, LocalDate date, Interval interval) {
    List<String> lines = new ArrayList<>();
    if (date != null) {
      lines.add("<b>" + TagUtil.formatDateTime(date) + "</b>"); // NON-NLS
    }
    String description = masked(study, Tag.StudyDescription);
    if (!description.isEmpty()) {
      lines.add(escape(description));
    }
    if (interval != null && interval.days() > 0) {
      lines.add(Messages.format("StudyStripPane.after", interval.text())); // NON-NLS
    }
    return lines.isEmpty() ? null : "<html>" + String.join("<br>", lines) + "</html>"; // NON-NLS
  }

  private static String escape(String text) {
    return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;"); // NON-NLS
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

  /**
   * The study date as the mask shows it: a shifted date keeps the intervals between studies, a
   * hidden date gives null.
   */
  private static LocalDate maskedDate(MediaSeriesGroup study) {
    LocalDate date = dateOf(study);
    if (date == null) {
      return null;
    }
    return IdentityMask.maskValue(TagD.get(Tag.StudyDate), date) instanceof LocalDate d ? d : null;
  }

  /** Masked and formatted value of a tag, empty when absent. */
  private static String masked(MediaSeriesGroup group, int tagId) {
    TagW tag = TagD.get(tagId);
    Object value = IdentityMask.maskValue(tag, group.getTagValue(tag));
    String text = value == null ? null : tag.getFormattedTagValue(value, null);
    return StringUtil.hasText(text) ? text : "";
  }

  /**
   * The width in pixels, before scaling, of the gap standing for {@code days} between two studies:
   * it grows with the logarithm of the time, so a week, a month, a year and ten years stay apart
   * without a decade of history taking ten times the room of a year.
   */
  static int gapForDays(long days) {
    return (int) Math.round(6 + 10 * Math.log1p(Math.max(0, days) / 7.0) / Math.log(2));
  }

  /**
   * The time between two studies in its most telling unit: days under about six weeks, months under
   * two years, years beyond.
   */
  record Interval(long days, int count, char unit) {

    static Interval between(LocalDate from, LocalDate to) {
      long days = ChronoUnit.DAYS.between(from, to);
      if (days < 45) {
        return new Interval(days, (int) days, 'd');
      }
      long months = Period.between(from, to).toTotalMonths();
      if (months < 24) {
        return new Interval(days, (int) months, 'm');
      }
      return new Interval(days, (int) (months / 12), 'y');
    }

    String text() {
      return Messages.format("StudyStripPane.gap." + unit, count); // NON-NLS
    }
  }

  /**
   * A card of the strip with what the axis draws for it.
   *
   * @param year the year label in the gap before the card, null when the year does not change
   * @param interval the time since the previous dated study, null when unknown
   */
  private record Entry(JToggleButton card, String year, Interval interval) {}

  /** The row of cards and the time axis painted under them. */
  private static final class Timeline extends JPanel {
    private final List<Entry> entries = new ArrayList<>();

    Timeline() {
      updateLayout();
    }

    @Override
    public void updateUI() {
      super.updateUI();
      // Called by the super constructor, before the fields exist
      if (entries != null) {
        updateLayout();
      }
    }

    /** Insets with room under the cards for the axis and its year labels. */
    private void updateLayout() {
      int axis = getFontMetrics(yearFont()).getHeight() + GuiUtils.getScaleLength(4);
      int pad = GuiUtils.getScaleLength(4);
      setLayout(
          new MigLayout(
              "nogrid, gap 0, ins %dpx 0 %dpx %dpx".formatted(pad, axis, pad),
              "",
              "[top]")); // NON-NLS
    }

    void clear() {
      entries.clear();
      removeAll();
    }

    void add(Entry entry) {
      int gap = GuiUtils.getScaleLength(entries.isEmpty() ? 4 : 6);
      Interval interval = entry.interval();
      if (interval != null) {
        gap = GuiUtils.getScaleLength(gapForDays(interval.days()));
        if (interval.days() > 0) {
          int label = getFontMetrics(gapFont()).stringWidth(interval.text());
          gap = Math.max(gap, label + GuiUtils.getScaleLength(8));
        }
      }
      if (entry.year() != null) {
        int label = getFontMetrics(yearFont()).stringWidth(entry.year());
        gap = Math.max(gap, label + GuiUtils.getScaleLength(12));
      }
      entries.add(entry);
      add(entry.card(), "gapbefore " + gap + "px"); // NON-NLS
    }

    private static Font yearFont() {
      return FontItem.SMALL_SEMIBOLD.getFont();
    }

    private static Font gapFont() {
      return FontItem.MINI.getFont();
    }

    @Override
    protected void paintComponent(Graphics g) {
      super.paintComponent(g);
      if (entries.isEmpty()) {
        return;
      }
      Graphics2D g2 = (Graphics2D) g.create();
      try {
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(
            RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        paintAxis(g2);
      } finally {
        g2.dispose();
      }
    }

    @Override
    protected void paintChildren(Graphics g) {
      super.paintChildren(g);
      // The selected studies get an accent outline, the color of their pin on the axis
      Graphics2D g2 = (Graphics2D) g.create();
      try {
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(color("Component.accentColor", Color.BLUE)); // NON-NLS
        float width = GuiUtils.getScaleLength(2f);
        g2.setStroke(new BasicStroke(width));
        int arc = GuiUtils.getScaleLength(10);
        for (Entry entry : entries) {
          if (entry.card().isSelected()) {
            Rectangle b = entry.card().getBounds();
            float o = width / 2;
            g2.draw(
                new RoundRectangle2D.Float(
                    b.x + o, b.y + o, b.width - width, b.height - width, arc, arc));
          }
        }
      } finally {
        g2.dispose();
      }
    }

    private void paintAxis(Graphics2D g2) {
      boolean ltr = getComponentOrientation().isLeftToRight();
      Color line = color("Separator.foreground", Color.GRAY); // NON-NLS
      Color faint = color("Label.disabledForeground", Color.GRAY); // NON-NLS
      Color accent = color("Component.accentColor", faint); // NON-NLS
      Color text = color("Label.foreground", Color.BLACK); // NON-NLS

      FontMetrics yearMetrics = g2.getFontMetrics(yearFont());
      FontMetrics gapMetrics = g2.getFontMetrics(gapFont());
      int bottom = 0;
      for (Entry entry : entries) {
        Rectangle b = entry.card().getBounds();
        bottom = Math.max(bottom, b.y + b.height);
      }
      int axisY = bottom + GuiUtils.getScaleLength(2) + yearMetrics.getHeight() / 2;
      int radius = GuiUtils.getScaleLength(3);

      // The axis runs from the start of the strip to the most recent study
      int start = ltr ? getInsets().left : getWidth() - getInsets().right;
      Rectangle last = entries.getLast().card().getBounds();
      g2.setColor(line);
      g2.setStroke(new BasicStroke(GuiUtils.getScaleLength(1f)));
      g2.drawLine(start, axisY, (int) last.getCenterX(), axisY);

      List<int[]> years = new ArrayList<>(); // index of the entry, center of its label
      int previousEdge = start;
      for (int i = 0; i < entries.size(); i++) {
        Entry entry = entries.get(i);
        Rectangle b = entry.card().getBounds();
        int lead = ltr ? b.x : b.x + b.width;
        int gapCenter = (previousEdge + lead) / 2;
        int gapWidth = Math.abs(lead - previousEdge);

        // The time since the previous study, in the gap at mid-height of the cards
        Interval interval = entry.interval();
        if (interval != null && interval.days() > 0) {
          String label = interval.text();
          int w = gapMetrics.stringWidth(label);
          if (w <= gapWidth - GuiUtils.getScaleLength(2)) {
            g2.setFont(gapFont());
            g2.setColor(faint);
            int y = b.y + (b.height - gapMetrics.getHeight()) / 2 + gapMetrics.getAscent();
            g2.drawString(label, gapCenter - w / 2, y);
          }
        }

        // The year where it changes, on the axis, cutting the line
        if (entry.year() != null) {
          paintYear(g2, entry.year(), gapCenter, axisY, text);
          years.add(new int[] {i, gapCenter});
        }

        // The pin of the study: a stem from the card to a dot on the axis
        int cx = (int) b.getCenterX();
        boolean on = entry.card().isSelected();
        g2.setColor(on ? accent : line);
        g2.drawLine(cx, b.y + b.height, cx, axisY);
        g2.setColor(on ? accent : faint);
        int r = on ? radius + GuiUtils.getScaleLength(1) : radius;
        g2.fillOval(cx - r, axisY - r, 2 * r, 2 * r);

        previousEdge = ltr ? b.x + b.width : b.x;
      }
      paintPinnedYear(g2, years, axisY, text);
    }

    /**
     * Keeps the year of the first visible studies at the start of the view when its label has
     * scrolled out, pushed away by the label of the next year as it comes in, like a sticky header.
     */
    private void paintPinnedYear(Graphics2D g2, List<int[]> years, int axisY, Color text) {
      Rectangle view = getVisibleRect();
      if (years.isEmpty() || view.width >= getWidth()) {
        return;
      }
      // Positions along the reading direction, so that the same code serves right-to-left
      int sign = getComponentOrientation().isLeftToRight() ? 1 : -1;
      int viewStart = sign > 0 ? view.x : -(view.x + view.width);
      FontMetrics metrics = g2.getFontMetrics(yearFont());
      int pad = GuiUtils.getScaleLength(6);
      int current = -1;
      for (int k = 0; k < years.size(); k++) {
        String year = entries.get(years.get(k)[0]).year();
        int half = metrics.stringWidth(year) / 2;
        if (sign * years.get(k)[1] - half < viewStart + pad) {
          current = k;
        }
      }
      if (current < 0) {
        return;
      }
      String year = entries.get(years.get(current)[0]).year();
      int width = metrics.stringWidth(year);
      int center = viewStart + pad + width / 2;
      if (current + 1 < years.size()) {
        String next = entries.get(years.get(current + 1)[0]).year();
        int nextStart = sign * years.get(current + 1)[1] - metrics.stringWidth(next) / 2;
        center = Math.min(center, nextStart - pad - (width + 1) / 2);
      }
      paintYear(g2, year, sign * center, axisY, text);
    }

    /** A year label centered on the axis, on a background that cuts the line. */
    private void paintYear(Graphics2D g2, String year, int center, int axisY, Color text) {
      FontMetrics metrics = g2.getFontMetrics(yearFont());
      int w = metrics.stringWidth(year);
      int pad = GuiUtils.getScaleLength(3);
      int top = axisY - metrics.getHeight() / 2;
      g2.setColor(getBackground());
      g2.fillRect(center - w / 2 - pad, top, w + 2 * pad, metrics.getHeight());
      g2.setFont(yearFont());
      g2.setColor(text);
      g2.drawString(year, center - w / 2, top + metrics.getAscent());
    }

    private static Color color(String key, Color fallback) {
      Color c = UIManager.getColor(key);
      return c == null ? fallback : c;
    }
  }
}
