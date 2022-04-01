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

import bibliothek.gui.dock.common.CLocation;
import bibliothek.gui.dock.common.mode.ExtendedMode;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JViewport;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableModel;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Sequence;
import org.dcm4che3.data.Tag;
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
import org.weasis.core.ui.util.SimpleTableModel;
import org.weasis.core.ui.util.TableColumnAdjuster;
import org.weasis.core.util.StringUtil;
import org.weasis.dicom.codec.DicomSpecialElement;
import org.weasis.dicom.codec.TagD;
import org.weasis.dicom.codec.utils.DicomMediaUtils;
import org.weasis.dicom.explorer.DicomModel;
import org.weasis.dicom.wave.Messages;

public class MeasureAnnotationTool extends PluginTool implements SeriesViewerListener {

  private static final Logger LOGGER = LoggerFactory.getLogger(MeasureAnnotationTool.class);

  public static final String BUTTON_NAME = "Measurements"; // NON-NLS

  private final JScrollPane rootPane;
  private final JPanel tableMarkerContainer = new JPanel();
  private JTable tableMarker;

  private final JPanel tableTagContainer = new JPanel();
  private JTable tableTag;

  public MeasureAnnotationTool() {
    super(
        BUTTON_NAME, BUTTON_NAME, POSITION.EAST, ExtendedMode.NORMALIZED, Insertable.Type.TOOL, 30);
    this.rootPane = new JScrollPane();
    dockable.setTitleIcon(ResourceUtil.getIcon(ActionIcon.MEASURE));
    rootPane.setBorder(BorderFactory.createEmptyBorder()); // remove default line
    setDockableWidth(280);
    jbInit();
  }

  private void jbInit() {
    setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
    add(getMarkerPanel());
    add(getAnnotationsPanel());
  }

  public final JPanel getAnnotationsPanel() {
    final JPanel transform = new JPanel();
    transform.setAlignmentY(Component.TOP_ALIGNMENT);
    transform.setAlignmentX(Component.LEFT_ALIGNMENT);
    transform.setLayout(new BoxLayout(transform, BoxLayout.Y_AXIS));
    transform.setBorder(
        BorderFactory.createCompoundBorder(
            BorderFactory.createEmptyBorder(10, 3, 0, 3),
            GuiUtils.getTitledBorder(Messages.getString("annotations"))));

    JPanel panel1 = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
    transform.add(panel1);
    transform.add(Box.createVerticalStrut(5));
    tableTag =
        createMultipleRenderingTable(new SimpleTableModel(new String[] {}, new Object[][] {}));
    tableTag.setFont(FontItem.SMALL.getFont());

    tableTag.getTableHeader().setReorderingAllowed(false);
    tableTagContainer.setPreferredSize(GuiUtils.getDimension(50, 80));
    tableTagContainer.setLayout(new BorderLayout());
    transform.add(tableTagContainer);

    return transform;
  }

  public JPanel getMarkerPanel() {
    final JPanel transform = new JPanel();
    transform.setAlignmentY(Component.TOP_ALIGNMENT);
    transform.setAlignmentX(Component.LEFT_ALIGNMENT);
    transform.setLayout(new BoxLayout(transform, BoxLayout.Y_AXIS));
    transform.setBorder(
        BorderFactory.createCompoundBorder(
            BorderFactory.createEmptyBorder(10, 3, 0, 3),
            GuiUtils.getTitledBorder(Messages.getString("markers"))));

    JPanel panel1 = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
    transform.add(panel1);
    transform.add(Box.createVerticalStrut(5));
    tableMarker =
        createMultipleRenderingTable(new SimpleTableModel(new String[] {}, new Object[][] {}));
    tableMarker.setFont(FontItem.SMALL.getFont());

    tableMarker.getTableHeader().setReorderingAllowed(false);
    tableMarkerContainer.setPreferredSize(GuiUtils.getDimension(50, 80));
    tableMarkerContainer.setLayout(new BorderLayout());
    transform.add(tableMarkerContainer);

    return transform;
  }

  @Override
  public Component getToolComponent() {
    JViewport viewPort = rootPane.getViewport();
    if (viewPort == null) {
      viewPort = new JViewport();
      rootPane.setViewport(viewPort);
    }
    if (viewPort.getView() != this) {
      viewPort.setView(this);
    }
    return rootPane;
  }

  @Override
  protected void changeToolWindowAnchor(CLocation clocation) {
    // Do nothing
  }

  public static JTable createMultipleRenderingTable(TableModel model) {
    JTable table = new JTable(model);
    table.getTableHeader().setReorderingAllowed(false);
    table.setShowHorizontalLines(true);
    table.setShowVerticalLines(true);
    table.getColumnModel().setColumnMargin(3);
    return table;
  }

  public void readAnnotations(Attributes attributes) {
    tableTagContainer.removeAll();

    // just clear tableContainer if measList is null
    if (attributes != null) {
      List<Object[]> list = new ArrayList<>();
      readAcquisitionContextSequence(attributes, list);
      readFiltersFrequency(attributes, list);
      readWaveformAnnotations(attributes, list);

      Object[][] labels = new Object[list.size()][];
      for (int i = 0; i < labels.length; i++) {
        labels[i] = list.get(i);
      }
      String[] headers = {
        Messages.getString("MeasureAnnotationTool.tag"),
        Messages.getString("MeasureAnnotationTool.value")
      };
      tableTag.setModel(new SimpleTableModel(headers, labels));
      tableTag.getColumnModel().getColumn(1).setCellRenderer(new TagRenderer());
      int height =
          (tableTag.getRowHeight() + tableTag.getRowMargin()) * tableTag.getRowCount()
              + tableTag.getTableHeader().getHeight()
              + 5;
      tableTagContainer.setPreferredSize(
          new Dimension(tableTag.getColumnModel().getTotalColumnWidth(), height));
      tableTagContainer.add(tableTag.getTableHeader(), BorderLayout.PAGE_START);
      tableTagContainer.add(tableTag, BorderLayout.CENTER);
      TableColumnAdjuster.pack(tableTag);
    } else {
      tableTagContainer.setPreferredSize(GuiUtils.getDimension(50, 50));
    }
    tableTagContainer.revalidate();
    tableTagContainer.repaint();
  }

  public void updateMeasuredItems(List<Object[]> list) {
    tableMarkerContainer.removeAll();

    if (list != null && !list.isEmpty()) {
      Object[][] labels = new Object[list.size()][];
      for (int i = 0; i < labels.length; i++) {
        labels[i] = list.get(i);
      }
      String[] headers = {
        Messages.getString("MeasureAnnotationTool.lead"),
        Messages.getString("MeasureAnnotationTool.tag"),
        Messages.getString("MeasureAnnotationTool.value")
      };
      tableMarker.setModel(new SimpleTableModel(headers, labels));
      tableMarker.getColumnModel().getColumn(1).setCellRenderer(new TagRenderer());
      int height =
          (tableMarker.getRowHeight() + tableMarker.getRowMargin()) * tableMarker.getRowCount()
              + tableMarker.getTableHeader().getHeight()
              + 5;
      tableMarkerContainer.setPreferredSize(
          new Dimension(tableMarker.getColumnModel().getTotalColumnWidth(), height));
      tableMarkerContainer.add(tableMarker.getTableHeader(), BorderLayout.PAGE_START);
      tableMarkerContainer.add(tableMarker, BorderLayout.CENTER);
      TableColumnAdjuster.pack(tableMarker);
    } else {
      tableMarkerContainer.setPreferredSize(GuiUtils.getDimension(50, 50));
    }
    tableMarkerContainer.revalidate();
    tableMarkerContainer.repaint();
  }

  public static int getNumberOfMeasures(boolean[] select) {
    int k = 0;
    for (boolean b : select) {
      if (b) {
        k++;
      }
    }
    return k;
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
      double filterLow =
          DicomMediaUtils.getDoubleFromDicomElement(item, Tag.FilterLowFrequency, 0.0);
      addValueToModel(list, TagD.get(Tag.FilterLowFrequency), filterLow + " Hz"); // NON-NLS
      double filterHigh =
          DicomMediaUtils.getDoubleFromDicomElement(item, Tag.FilterHighFrequency, 0.0);
      addValueToModel(list, TagD.get(Tag.FilterHighFrequency), filterHigh + " Hz"); // NON-NLS
      double notchFilter =
          DicomMediaUtils.getDoubleFromDicomElement(item, Tag.NotchFilterFrequency, 0.0);
      addValueToModel(list, TagD.get(Tag.NotchFilterFrequency), notchFilter + " Hz"); // NON-NLS

      for (int i = 1; i < chDefSeq.size(); i++) {
        item = chDefSeq.get(i);
        String title = item.getNestedDataset(Tag.ChannelSourceSequence).getString(Tag.CodeMeaning);
        double low = DicomMediaUtils.getDoubleFromDicomElement(item, Tag.FilterLowFrequency, 0.0);
        if (low != filterLow) {
          addValueToModel(
              list,
              title + " - " + TagD.get(Tag.FilterLowFrequency).getDisplayedName(),
              low + " Hz"); // NON-NLS
        }

        double high = DicomMediaUtils.getDoubleFromDicomElement(item, Tag.FilterHighFrequency, 0.0);
        if (high != filterHigh) {
          addValueToModel(
              list,
              title + " - " + TagD.get(Tag.FilterHighFrequency).getDisplayedName(),
              high + " Hz"); // NON-NLS
        }

        double notch =
            DicomMediaUtils.getDoubleFromDicomElement(item, Tag.NotchFilterFrequency, 0.0);
        if (notch != notchFilter) {
          addValueToModel(
              list,
              title + " - " + TagD.get(Tag.NotchFilterFrequency).getDisplayedName(),
              notch + " Hz"); // NON-NLS
        }
      }
    }
  }

  private void readWaveformAnnotations(Attributes attributes, List<Object[]> list) {
    Sequence anSeq = attributes.getSequence(Tag.WaveformAnnotationSequence);
    if (anSeq != null) {
      for (Attributes item : anSeq) {
        try {
          String text = item.getString(Tag.UnformattedTextValue);
          if (StringUtil.hasText(text)) {
            addValueToModel(list, Messages.getString("text"), text);
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

  public static class TagRenderer extends DefaultTableCellRenderer {

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
