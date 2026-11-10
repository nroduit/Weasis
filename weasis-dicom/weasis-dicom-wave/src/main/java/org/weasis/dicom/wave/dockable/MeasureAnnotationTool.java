/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.wave.dockable;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.swing.BorderFactory;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.Scrollable;
import javax.swing.SwingConstants;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import javax.swing.table.TableModel;
import net.miginfocom.swing.MigLayout;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Sequence;
import org.dcm4che3.data.Tag;
import org.dcm4che3.img.util.DicomUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.gui.Insertable;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.media.data.Series;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.api.util.FontItem;
import org.weasis.core.api.util.ResourceUtil;
import org.weasis.core.api.util.ResourceUtil.ActionIcon;
import org.weasis.core.ui.docking.PluginTool;
import org.weasis.core.ui.editor.SeriesViewerEvent;
import org.weasis.core.ui.editor.SeriesViewerEvent.EVENT;
import org.weasis.core.ui.editor.SeriesViewerListener;
import org.weasis.core.ui.util.MeasureTables;
import org.weasis.core.ui.util.SimpleTableModel;
import org.weasis.core.util.StringUtil;
import org.weasis.dicom.codec.DicomSpecialElement;
import org.weasis.dicom.codec.TagD;
import org.weasis.dicom.explorer.DicomModel;
import org.weasis.dicom.wave.Messages;

/**
 * The measurement panel of the waveform viewer: the interval summary (heart rate, PR, QRS, QT,
 * QTc), the calipers of every lead, the annotations of the object and of the related reports, and
 * the interpretation text. Every section is a name / value table sized to its content; the panel
 * follows the width of its dock and scrolls vertically only.
 */
public class MeasureAnnotationTool extends PluginTool implements SeriesViewerListener, Scrollable {

  private static final Logger LOGGER = LoggerFactory.getLogger(MeasureAnnotationTool.class);

  public static final String BUTTON_NAME = "Measurements"; // NON-NLS

  private final JScrollPane rootPane;
  private final Section intervals;
  private final Section calipers;
  private final Section annotations;
  private final JPanel interpretationPanel;
  private final JTextArea interpretation = new JTextArea();

  /** Rows added by other objects annotating the displayed waveform (SR TCOORD items). */
  private List<Object[]> externalAnnotations = List.of();

  private Attributes lastAttributes;

  public MeasureAnnotationTool() {
    super("wave-measure", BUTTON_NAME, Insertable.Type.TOOL, 30); // NON-NLS
    this.rootPane = new JScrollPane();
    setIcon(ResourceUtil.getIcon(ActionIcon.MEASURE));
    rootPane.setBorder(BorderFactory.createEmptyBorder()); // remove default line
    rootPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
    setDockableWidth(300);

    setLayout(new MigLayout("ins 0 3 8 3, gap 0 6, wrap 1, fillx", "[grow, fill]")); // NON-NLS
    intervals = new Section(Messages.getString("MeasureAnnotationTool.intervals"), true);
    calipers = new Section(Messages.getString("markers"), false);
    annotations = new Section(Messages.getString("annotations"), false);
    add(intervals, "growx"); // NON-NLS
    add(calipers, "growx"); // NON-NLS
    add(annotations, "growx"); // NON-NLS

    interpretationPanel = new JPanel(new BorderLayout());
    interpretationPanel.setBorder(
        GuiUtils.getTitledBorder(Messages.getString("MeasureAnnotationTool.interpretation")));
    interpretation.setEditable(false);
    interpretation.setLineWrap(true);
    interpretation.setWrapStyleWord(true);
    interpretation.setFont(FontItem.SMALL.getFont());
    interpretation.setOpaque(false);
    interpretation.setBorder(BorderFactory.createEmptyBorder(2, 4, 4, 4));
    interpretationPanel.add(interpretation, BorderLayout.CENTER);
    interpretationPanel.setVisible(false);
    add(interpretationPanel, "growx"); // NON-NLS
    intervals.setVisible(false);
    calipers.setVisible(false);
  }

  /**
   * A titled name / value table that takes the height of its rows and hides when empty. A compact
   * section is as wide as its content (short names, short values); otherwise the table stretches to
   * the width of the dock, the name column takes what the values leave and shortened texts carry
   * their full value as tooltip.
   */
  private static final class Section extends JPanel {
    private static final int CELL_MARGIN = 8;
    private static final int MIN_NAME_WIDTH = 60;
    private static final int MAX_VALUE_SHARE_PERCENT = 55;

    private final JPanel container = new JPanel(new BorderLayout());
    private final JTable table;
    private final boolean compact;

    Section(String title, boolean compact) {
      super(new BorderLayout());
      this.compact = compact;
      setBorder(GuiUtils.getTitledBorder(title));
      table =
          createMultipleRenderingTable(new SimpleTableModel(new String[] {}, new Object[][] {}));
      table.setFont(FontItem.SMALL.getFont());
      MeasureTables.installCopyMenu(table);
      add(container, BorderLayout.CENTER);
    }

    void setRows(String[] headers, List<Object[]> rows, TableCellRenderer valueRenderer) {
      container.removeAll();
      if (rows == null || rows.isEmpty()) {
        setVisible(false);
      } else {
        table.setModel(new SimpleTableModel(headers, rows.toArray(new Object[0][])));
        TableCellRenderer values = valueRenderer == null ? new TooltipRenderer() : valueRenderer;
        table.getColumnModel().getColumn(1).setCellRenderer(values);
        if (compact) {
          MeasureTables.fit(table, container);
        } else {
          stretch();
        }
        setVisible(true);
      }
      container.revalidate();
      container.repaint();
    }

    /** Header above the rows, the table as wide as the dock, the value column at its content. */
    private void stretch() {
      table.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
      TableColumn names = table.getColumnModel().getColumn(0);
      TableColumn values = table.getColumnModel().getColumn(1);
      names.setCellRenderer(new TooltipRenderer());
      names.setMinWidth(GuiUtils.getScaleLength(MIN_NAME_WIDTH));
      names.setMaxWidth(Integer.MAX_VALUE);
      int valueWidth = contentWidth(1);
      values.setMinWidth(Math.min(valueWidth, GuiUtils.getScaleLength(MIN_NAME_WIDTH)));
      values.setPreferredWidth(valueWidth);
      values.setMaxWidth(Integer.MAX_VALUE);
      names.setPreferredWidth(Math.max(GuiUtils.getScaleLength(MIN_NAME_WIDTH), contentWidth(0)));
      container.add(table.getTableHeader(), BorderLayout.PAGE_START);
      container.add(table, BorderLayout.CENTER);
      int height =
          table.getRowHeight() * table.getRowCount()
              + table.getTableHeader().getPreferredSize().height
              + GuiUtils.insetHeight(container);
      container.setPreferredSize(new Dimension(GuiUtils.getScaleLength(100), height));
    }

    @Override
    public void doLayout() {
      if (!compact && table.getRowCount() > 0) {
        // The values keep their content width up to a share of the dock; the names get the rest
        int total = Math.max(0, container.getWidth());
        int valueWidth = Math.min(contentWidth(1), total * MAX_VALUE_SHARE_PERCENT / 100);
        TableColumn values = table.getColumnModel().getColumn(1);
        TableColumn names = table.getColumnModel().getColumn(0);
        values.setPreferredWidth(valueWidth);
        names.setPreferredWidth(
            Math.max(GuiUtils.getScaleLength(MIN_NAME_WIDTH), total - valueWidth));
      }
      super.doLayout();
    }

    /** Width that shows the header and every cell of a column in full. */
    private int contentWidth(int column) {
      TableColumn tableColumn = table.getColumnModel().getColumn(column);
      int width =
          table
              .getTableHeader()
              .getDefaultRenderer()
              .getTableCellRendererComponent(
                  table, tableColumn.getHeaderValue(), false, false, -1, column)
              .getPreferredSize()
              .width;
      for (int row = 0; row < table.getRowCount(); row++) {
        width =
            Math.max(
                width,
                table
                    .prepareRenderer(table.getCellRenderer(row, column), row, column)
                    .getPreferredSize()
                    .width);
      }
      return width + table.getIntercellSpacing().width + GuiUtils.getScaleLength(CELL_MARGIN);
    }
  }

  /** A cell whose full text is its tooltip, for the texts the column shortens. */
  private static class TooltipRenderer extends DefaultTableCellRenderer {
    @Override
    public Component getTableCellRendererComponent(
        JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
      super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
      setToolTipText(value == null ? null : value.toString());
      return this;
    }
  }

  @Override
  public Component getToolComponent() {
    return getToolComponentFromJScrollPane(rootPane);
  }

  public static JTable createMultipleRenderingTable(TableModel model) {
    JTable table = new JTable(model);
    table.getTableHeader().setReorderingAllowed(false);
    table.setShowHorizontalLines(true);
    table.setShowVerticalLines(true);
    table.getColumnModel().setColumnMargin(3);
    return table;
  }

  // ---- Scrollable: follow the width of the dock, scroll vertically ----

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
    return orientation == SwingConstants.VERTICAL ? visibleRect.height : visibleRect.width;
  }

  @Override
  public boolean getScrollableTracksViewportWidth() {
    return true;
  }

  @Override
  public boolean getScrollableTracksViewportHeight() {
    return false;
  }

  // ---- content ----

  /** Replaces the rows contributed by other objects and rebuilds the annotation table. */
  public void setExternalAnnotations(List<Object[]> rows) {
    this.externalAnnotations = rows == null ? List.of() : List.copyOf(rows);
    readAnnotations(lastAttributes);
  }

  /** The annotations of the object (acquisition context, filters, measurements) and its text. */
  public void readAnnotations(Attributes attributes) {
    this.lastAttributes = attributes;
    StringBuilder text = new StringBuilder();
    List<Object[]> list = new ArrayList<>();
    if (attributes != null) {
      readAcquisitionContextSequence(attributes, list);
      readFiltersFrequency(attributes, list);
      readWaveformAnnotations(attributes, list, text);
      list.addAll(externalAnnotations);
    }
    String[] headers = {Messages.common("tag"), Messages.common("value")};
    annotations.setRows(headers, list, new TagRenderer());
    interpretation.setText(text.toString());
    interpretationPanel.setVisible(!text.isEmpty());
    revalidate();
    repaint();
  }

  /**
   * The interval summary and the calipers, as name / value rows.
   *
   * @param intervalRows heart rate, R-R, PR, QRS, QT, QTc...
   * @param caliperRows one row per measure of every caliper, the lead in the name
   */
  public void updateCalipers(List<Object[]> intervalRows, List<Object[]> caliperRows) {
    String[] headers = {Messages.common("tag"), Messages.common("value")};
    intervals.setRows(headers, intervalRows, null);
    calipers.setRows(headers, caliperRows, null);
    revalidate();
    repaint();
  }

  private void readAcquisitionContextSequence(Attributes attributes, List<Object[]> list) {
    Sequence ctxSeq = attributes.getSequence(Tag.AcquisitionContextSequence);
    if (ctxSeq != null) {
      for (Attributes item : ctxSeq) {
        try {
          String value = "";
          if ("NUMERIC".equalsIgnoreCase(item.getString(Tag.ValueType))) {
            value = item.getString(Tag.NumericValue);
          } else {
            Optional<Attributes> cdSeq =
                Optional.ofNullable(item.getNestedDataset(Tag.ConceptCodeSequence));
            if (cdSeq.isPresent()) {
              value = cdSeq.get().getString(Tag.CodeMeaning);
            }
          }
          String name =
              Optional.of(item.getNestedDataset(Tag.ConceptNameCodeSequence))
                  .get()
                  .getString(Tag.CodeMeaning);
          addValueToModel(list, name, value);
        } catch (Exception e) {
          LOGGER.error("Cannot read AcquisitionContextSequence", e);
        }
      }
    }
  }

  private static void addValueToModel(List<Object[]> list, Object column1, Object column2) {
    if (column1 != null && column2 != null) {
      Object[] row = new Object[] {column1, column2};
      list.add(row);
    }
  }

  private void readFiltersFrequency(Attributes attributes, List<Object[]> list) {

    Attributes dcm = Optional.of(attributes.getNestedDataset(Tag.WaveformSequence)).get();
    Sequence chDefSeq = Optional.of(dcm.getSequence(Tag.ChannelDefinitionSequence)).get();

    if (!chDefSeq.isEmpty()) {
      Attributes item = chDefSeq.get(0);
      double filterLow = DicomUtils.getDoubleFromDicomElement(item, Tag.FilterLowFrequency, 0.0);
      addValueToModel(list, TagD.get(Tag.FilterLowFrequency), filterLow + " Hz"); // NON-NLS
      double filterHigh = DicomUtils.getDoubleFromDicomElement(item, Tag.FilterHighFrequency, 0.0);
      addValueToModel(list, TagD.get(Tag.FilterHighFrequency), filterHigh + " Hz"); // NON-NLS
      double notchFilter =
          DicomUtils.getDoubleFromDicomElement(item, Tag.NotchFilterFrequency, 0.0);
      addValueToModel(list, TagD.get(Tag.NotchFilterFrequency), notchFilter + " Hz"); // NON-NLS

      for (int i = 1; i < chDefSeq.size(); i++) {
        item = chDefSeq.get(i);
        String title = item.getNestedDataset(Tag.ChannelSourceSequence).getString(Tag.CodeMeaning);
        double low = DicomUtils.getDoubleFromDicomElement(item, Tag.FilterLowFrequency, 0.0);
        if (low != filterLow) {
          addValueToModel(
              list,
              title + " - " + TagD.get(Tag.FilterLowFrequency).getDisplayedName(),
              low + " Hz"); // NON-NLS
        }

        double high = DicomUtils.getDoubleFromDicomElement(item, Tag.FilterHighFrequency, 0.0);
        if (high != filterHigh) {
          addValueToModel(
              list,
              title + " - " + TagD.get(Tag.FilterHighFrequency).getDisplayedName(),
              high + " Hz"); // NON-NLS
        }

        double notch = DicomUtils.getDoubleFromDicomElement(item, Tag.NotchFilterFrequency, 0.0);
        if (notch != notchFilter) {
          addValueToModel(
              list,
              title + " - " + TagD.get(Tag.NotchFilterFrequency).getDisplayedName(),
              notch + " Hz"); // NON-NLS
        }
      }
    }
  }

  private void readWaveformAnnotations(
      Attributes attributes, List<Object[]> list, StringBuilder interpretationText) {
    Sequence anSeq = attributes.getSequence(Tag.WaveformAnnotationSequence);
    if (anSeq != null) {
      for (Attributes item : anSeq) {
        try {
          String text = item.getString(Tag.UnformattedTextValue);
          if (StringUtil.hasText(text)) {
            if (!interpretationText.isEmpty()) {
              interpretationText.append('\n');
            }
            interpretationText.append(text.trim());
            continue;
          }
          Optional<Attributes> mSeq =
              Optional.ofNullable(item.getNestedDataset(Tag.MeasurementUnitsCodeSequence));
          if (mSeq.isPresent()) {
            String name =
                item.getNestedDataset(Tag.ConceptNameCodeSequence).getString(Tag.CodeMeaning);
            String value = item.getString(Tag.NumericValue);
            String unit = mSeq.get().getString(Tag.CodeValue);
            addValueToModel(list, name, value + " " + unit);
          } else if ("POINT".equals(item.getString(Tag.TemporalRangeType))) {
            String name =
                item.getNestedDataset(Tag.ConceptNameCodeSequence).getString(Tag.CodeMeaning);
            String value = item.getString(Tag.ReferencedSamplePositions);
            String unit = item.getString(Tag.TemporalRangeType);
            addValueToModel(list, name, value + " " + unit);
          }

        } catch (Exception e) {
          LOGGER.error("Cannot read MeasurementUnitsCodeSequence", e);
        }
      }
    }
  }

  @Override
  public void changingViewContentEvent(SeriesViewerEvent event) {
    EVENT type = event.getEventType();
    if (EVENT.LAYOUT.equals(type) && event.getSeries() instanceof Series) {
      setSeries((Series<?>) event.getSeries());
    }
  }

  public void setSeries(Series<?> series) {
    if (series != null) {
      // Should have only one object by series (if more, they are split in several subseries in
      // dicomModel)
      DicomSpecialElement s = DicomModel.getFirstSpecialElement(series, DicomSpecialElement.class);
      if (s != null) {
        readAnnotations(s.getMediaReader().getDicomObject());
      }
    }
  }

  public static class TagRenderer extends TooltipRenderer {

    public TagRenderer() {
      setFont(FontItem.SMALL.getFont());
    }

    @Override
    public Component getTableCellRendererComponent(
        JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
      Component val =
          super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

      TableModel model = table.getModel();
      Object tag = model.getValueAt(row, 0);
      if (tag instanceof TagW tagW) {
        setValue(tagW.getFormattedTagValue(value, null));
      }
      return val;
    }
  }
}
