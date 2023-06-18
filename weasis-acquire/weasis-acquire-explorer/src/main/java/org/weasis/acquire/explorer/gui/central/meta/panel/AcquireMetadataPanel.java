/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.acquire.explorer.gui.central.meta.panel;

import com.github.lgooddatepicker.components.DatePickerSettings;
import com.github.lgooddatepicker.tableeditors.DateTableEditor;
import com.github.lgooddatepicker.tableeditors.TimeTableEditor;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.Insets;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.swing.AbstractCellEditor;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.DefaultCellEditor;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.border.TitledBorder;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellEditor;
import org.dcm4che3.data.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.acquire.explorer.AcquireImageInfo;
import org.weasis.acquire.explorer.gui.central.meta.model.AcquireMetadataTableModel;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.gui.util.GuiUtils.IconColor;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.api.media.data.TagW.TagType;
import org.weasis.core.api.service.BundleTools;
import org.weasis.core.api.util.FontItem;
import org.weasis.core.api.util.LocalUtil;
import org.weasis.core.api.util.ResourceUtil;
import org.weasis.core.ui.util.CalendarUtil;
import org.weasis.core.ui.util.TableColumnAdjuster;
import org.weasis.core.util.StringUtil;
import org.weasis.dicom.codec.TagD;
import org.weasis.dicom.codec.display.Modality;

public abstract class AcquireMetadataPanel extends JPanel implements TableModelListener {
  private static final Logger LOGGER = LoggerFactory.getLogger(AcquireMetadataPanel.class);

  protected final String title;
  protected final JLabel label = new JLabel();
  protected final JTable table;
  protected AcquireImageInfo imageInfo;
  protected TitledBorder titleBorder;
  protected static final Font SMALL_FONT = FontItem.SMALL.getFont();

  protected AcquireMetadataPanel(String title) {
    this.title = title;
    this.titleBorder = GuiUtils.getTitledBorder(getDisplayText());
    setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
    setBorder(titleBorder);

    this.table = new JTable();
    // Force committing value when losing the focus
    table.putClientProperty("terminateEditOnFocusLost", Boolean.TRUE);
    table.setFont(SMALL_FONT);
    table.getTableHeader().setReorderingAllowed(false);
    table.setShowHorizontalLines(true);
    table.setShowVerticalLines(true);
    table.setIntercellSpacing(GuiUtils.getDimension(2, 2));
    updateTable();
    setMetaVisible(false);
  }

  public abstract AcquireMetadataTableModel newTableModel();

  public String getDisplayText() {
    return title;
  }

  public void stopEditing() {
    TableCellEditor editor = table.getCellEditor();
    if (editor != null) {
      editor.stopCellEditing();
    }
  }

  public void setImageInfo(AcquireImageInfo imageInfo) {
    this.imageInfo = imageInfo;
    this.titleBorder.setTitle(getDisplayText());
    setMetaVisible(imageInfo != null);
    update();
  }

  public void setMetaVisible(boolean visible) {
    setVisible(visible);
  }

  public void update() {
    updateLabel();
    updateTable();
  }

  public void updateLabel() {
    this.titleBorder.setTitle(getDisplayText());
  }

  public void updateTable() {
    if (table.isEditing()) table.getCellEditor().stopCellEditing();
    removeAll();
    AcquireMetadataTableModel model = newTableModel();
    model.addTableModelListener(this);
    table.setModel(model);
    table.getColumnModel().getColumn(1).setCellRenderer(new TagRenderer());
    table.getColumnModel().getColumn(1).setCellEditor(new AcquireImageCellEditor());
    TableColumnAdjuster.pack(table);
    add(table.getTableHeader());
    add(table);
  }

  @Override
  public void tableChanged(TableModelEvent e) {
    // DO NOTHING
  }

  public static class TagRenderer extends DefaultTableCellRenderer {

    @Override
    public Component getTableCellRendererComponent(
        JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
      Component val =
          super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

      AcquireMetadataTableModel model = (AcquireMetadataTableModel) table.getModel();
      if (model.isCellEditable(row, column)) {
        Color c =
            model.isValueRequired(row) && isEmptyValue(value)
                ? IconColor.ACTIONS_RED.getColor()
                : getForeground();
        setBorder(BorderFactory.createDashedBorder(c, 2, 2));
      }

      Object tag = model.getValueAt(row, 0);
      if (tag instanceof TagW tagW) {
        setValue(tagW.getFormattedTagValue(value, null));
      }
      return val;
    }

    private boolean isEmptyValue(Object value) {
      if (value == null) {
        return true;
      }
      if (value instanceof String s) {
        return !StringUtil.hasText(s);
      }
      return false;
    }
  }

  public static class AcquireImageCellEditor extends AbstractCellEditor implements TableCellEditor {
    private static final JComboBox<String> bodyPartsCombo = new JComboBox<>(getBodyPartValues());
    private static final JComboBox<TagD.Sex> sexCombo = new JComboBox<>(TagD.Sex.values());
    private static final JComboBox<Modality> modalityCombo =
        new JComboBox<>(Modality.getAllModalitiesExceptDefault());
    private static final JComboBox<String> studyDescCombo =
        new JComboBox<>(getValues("weasis.acquire.meta.study.description", null));
    private static final JComboBox<String> seriesDescCombo =
        new JComboBox<>(getValues("weasis.acquire.meta.series.description", null));

    static {
      initCombo(bodyPartsCombo);
      initCombo(sexCombo);
      initCombo(modalityCombo);
      initCombo(studyDescCombo);
      initCombo(seriesDescCombo);
    }

    private Optional<TableCellEditor> editor;

    @Override
    public Object getCellEditorValue() {
      return editor.map(e -> convertValue(e.getCellEditorValue())).orElse(null);
    }

    private Object convertValue(Object val) {
      if (val instanceof TagD.Sex sex) {
        return sex.getValue();
      } else if (val instanceof Modality modality) {
        return modality.name();
      }
      return val;
    }

    @Override
    public Component getTableCellEditorComponent(
        JTable table, Object value, boolean isSelected, int row, int column) {
      TableCellEditor cellEditor;
      Object tag = table.getModel().getValueAt(row, 0);
      int tagID = 0;
      boolean date = false;
      boolean time = false;
      if (tag instanceof TagW tagW) {
        tagID = tagW.getId();
        TagType type = tagW.getType();
        date = TagType.DICOM_DATE == type || TagType.DATE == type;
        time = TagType.DICOM_TIME == type || TagType.TIME == type;
      }
      if (tagID == Tag.BodyPartExamined) {
        cellEditor = new DefaultCellEditor(bodyPartsCombo);
      } else if (tagID == Tag.PatientSex) {
        cellEditor = new DefaultCellEditor(sexCombo);
      } else if (tagID == Tag.Modality) {
        cellEditor = new DefaultCellEditor(modalityCombo);
      } else if (tagID == Tag.StudyDescription) {
        cellEditor = getCellEditor(studyDescCombo);
      } else if (tagID == Tag.SeriesDescription) {
        cellEditor = getCellEditor(seriesDescCombo);
      } else if (date) {
        DateTableEditor datePicker = buildDatePicker();
        JTextField picker = datePicker.getDatePicker().getComponentDateTextField();
        Insets margin = picker.getMargin();
        int height = table.getRowHeight(row) - margin.top - margin.bottom;
        GuiUtils.setPreferredHeight(picker, height);
        GuiUtils.setPreferredHeight(
            datePicker.getDatePicker().getComponentToggleCalendarButton(), height);
        cellEditor = datePicker;
      } else if (time) {
        TimeTableEditor tableEditor = new TimeTableEditor(false, true, true);
        tableEditor.getTimePickerSettings().fontInvalidTime = SMALL_FONT;
        tableEditor.getTimePickerSettings().fontValidTime = SMALL_FONT;
        tableEditor.getTimePickerSettings().fontVetoedTime = SMALL_FONT;
        JButton button = tableEditor.getTimePicker().getComponentToggleTimeMenuButton();
        Insets margin = button.getMargin();
        int height = table.getRowHeight(row) - margin.top - margin.bottom;
        GuiUtils.setPreferredHeight(button, height, height);
        GuiUtils.setPreferredHeight(tableEditor.getTimePicker(), height, height);
        GuiUtils.setPreferredHeight(
            tableEditor.getTimePicker().getComponentTimeTextField(), height, height);
        cellEditor = tableEditor;
      } else {
        cellEditor = new DefaultCellEditor(new JTextField());
      }
      editor = Optional.of(cellEditor);
      Component c = cellEditor.getTableCellEditorComponent(table, value, isSelected, row, column);
      c.setFont(SMALL_FONT);
      return c;
    }

    private static DefaultCellEditor getCellEditor(JComboBox<?> combo) {
      if (combo.getItemCount() == 0) {
        return new DefaultCellEditor(new JTextField());
      } else {
        return new DefaultCellEditor(combo);
      }
    }

    private static void initCombo(JComboBox<?> combo) {
      combo.setFont(AcquireMetadataPanel.SMALL_FONT);
      combo.setMaximumRowCount(15);
      GuiUtils.setPreferredWidth(combo, 80);
    }

    private DateTableEditor buildDatePicker() {
      DateTableEditor d = new DateTableEditor(false, true, true);
      DatePickerSettings settings = d.getDatePickerSettings();
      settings.setFontInvalidDate(SMALL_FONT);
      settings.setFontValidDate(SMALL_FONT);
      settings.setFontVetoedDate(SMALL_FONT);

      CalendarUtil.adaptCalendarColors(settings);

      settings.setFormatForDatesCommonEra(LocalUtil.getDateFormatter());
      settings.setFormatForDatesBeforeCommonEra(LocalUtil.getDateFormatter());

      settings.setFormatForDatesCommonEra(LocalUtil.getDateFormatter());
      settings.setFormatForDatesBeforeCommonEra(LocalUtil.getDateFormatter());
      return d;
    }

    public static String[] getBodyPartValues() {
      // https://dicom.nema.org/medical/dicom/current/output/chtml/part16/chapter_L.html
      List<String> list = new ArrayList<>();
      try (BufferedReader br =
          Files.newBufferedReader(ResourceUtil.getResource(Path.of("bodyPartExamined.csv")))) {
        String line;
        while ((line = br.readLine()) != null) {
          String[] columns = line.split(",");
          if (columns.length > 2 && StringUtil.hasText(columns[2]) && columns[2].length() <= 16) {
            list.add(columns[2]);
          }
        }
      } catch (IOException ex) {
        LOGGER.error("Cannot read body part values", ex);
      }
      return list.toArray(new String[0]);
    }

    public static String[] getValues(String property, String defaultValues) {
      String values = BundleTools.SYSTEM_PREFERENCES.getProperty(property, defaultValues);
      if (values == null) {
        return new String[0];
      }
      String[] val = values.split(",");
      List<String> list = new ArrayList<>(val.length);
      for (String s : val) {
        if (StringUtil.hasText(s)) {
          list.add(s.trim());
        }
      }
      return list.toArray(new String[0]);
    }
  }
}
