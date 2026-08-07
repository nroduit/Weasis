/*
 * Copyright (c) 2024 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.tp.raven.datetime;

import com.formdev.flatlaf.FlatClientProperties;
import java.awt.*;
import java.text.DateFormatSymbols;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import net.miginfocom.swing.MigLayout;
import org.weasis.core.api.util.ResourceUtil;
import org.weasis.core.api.util.ResourceUtil.OtherIcon;
import org.weasis.core.ui.tp.raven.datetime.component.PanelPopupEditor;
import org.weasis.core.ui.tp.raven.datetime.component.date.DateSelectionModel;
import org.weasis.core.ui.tp.raven.datetime.component.date.DefaultDateCellRenderer;
import org.weasis.core.ui.tp.raven.datetime.component.date.Header;
import org.weasis.core.ui.tp.raven.datetime.component.date.PanelDate;
import org.weasis.core.ui.tp.raven.datetime.component.date.PanelMonth;
import org.weasis.core.ui.tp.raven.datetime.component.date.PanelYear;
import org.weasis.core.ui.tp.raven.datetime.component.date.SingleDate;
import org.weasis.core.ui.tp.raven.datetime.component.date.event.DateControlEvent;
import org.weasis.core.ui.tp.raven.datetime.component.date.event.DateControlListener;
import org.weasis.core.ui.tp.raven.datetime.component.date.event.DateSelectionModelEvent;
import org.weasis.core.ui.tp.raven.datetime.component.date.event.DateSelectionModelListener;
import org.weasis.core.ui.tp.raven.datetime.event.DateSelectionEvent;
import org.weasis.core.ui.tp.raven.datetime.event.DateSelectionListener;
import org.weasis.core.ui.tp.raven.datetime.util.InputUtils;
import org.weasis.core.ui.tp.raven.datetime.util.InputValidationListener;
import org.weasis.core.ui.tp.raven.slider.PanelSlider;
import org.weasis.core.ui.tp.raven.slider.SimpleTransition;
import org.weasis.core.ui.tp.raven.slider.SliderTransition;

/**
 * DatePicker is a class that provides methods for creating a date picker.
 *
 * @author Raven Laing
 * @see <a href="https://github.com/DJ-Raven/swing-datetime-picker">swing-datetime-picker</a>
 */
public class DatePicker extends PanelPopupEditor
    implements DateSelectionModelListener, DateControlListener, ChangeListener {

  private static String[] defaultWeekdays = null;
  private static String[] defaultMonths = null;

  private DateTimeFormatter format;
  private String dateFormatPattern = "dd/MM/yyyy";
  private DateSelectionListener dateSelectionListener;
  private InputValidationListener<LocalDate> inputValidationListener;
  private DateSelectionModel dateSelectionModel;
  private PanelDateOption panelDateOption;
  private PanelDateOptionLabel panelDateOptionLabel;
  private InputUtils.ValueCallback valueCallback;
  private Icon editorIcon;
  private String separator = " to ";
  private boolean usePanelOption;
  private boolean closeAfterSelected;
  private boolean animationEnabled = true;
  private boolean startWeekOnMonday;
  private float selectionArc = 999;
  private int month = 10;
  private int year = 2023;
  private Color color;
  private JButton editorButton;
  private SelectionState selectionState = SelectionState.DATE;
  private PanelDate panelDate;
  private PanelMonth panelMonth;
  private PanelYear panelYear;

  private DefaultDateCellRenderer defaultDateCellRenderer = new DefaultDateCellRenderer();
  private final Header header = new Header();
  private final PanelSlider panelSlider = new PanelSlider();

  public DatePicker() {
    this(null);
  }

  public DatePicker(DateSelectionModel dateSelectionModel) {
    init(dateSelectionModel);
  }

  private void init(DateSelectionModel dateSelectionModel) {
    putClientProperty(
        FlatClientProperties.STYLE,
        "[light]background:darken($Panel.background,2%);"
            + "[dark]background:lighten($Panel.background,2%);");
    setLayout(new MigLayout("wrap,insets 10,fill", "[fill]", "[top,grow 0][center,fill]"));

    format = DateTimeFormatter.ofPattern(dateFormatPattern);
    header.addDateControlListener(this);

    if (dateSelectionModel == null) {
      dateSelectionModel = createDefaultDateSelection();
    }
    setDateSelectionModel(dateSelectionModel);

    add(header);
    add(panelSlider, "width 260!,height 250!");
    initDate();
    JPanel buttonPanel = createButtonPanel();
    add(buttonPanel, "dock south");
  }

  private void initDate() {
    LocalDate date = LocalDate.now(ZoneId.systemDefault());
    int month = date.getMonthValue() - 1;
    int year = date.getYear();
    this.month = month;
    this.year = year;
    header.setDate(month, year);
    panelSlider.addSlide(createPanelDate(month, year), null);
  }

  public void setToBack() {
    if (selectionState == SelectionState.DATE) {
      if (month == 0) {
        month = 11;
        year--;
      } else {
        month--;
      }
      header.setDate(month, year);
      panelSlider.addSlide(
          createPanelDate(month, year), getSliderTransition(SimpleTransition.SliderType.BACK));
    } else if (selectionState == SelectionState.MONTH) {
      year--;
      header.setDate(month, year);
      panelSlider.addSlide(
          createPanelMonth(year), getSliderTransition(SimpleTransition.SliderType.BACK));
    } else {
      int oldYear = this.panelYear.getYear();
      panelSlider.addSlide(
          createPanelYear(oldYear - PanelYear.YEAR_CELL),
          getSliderTransition(SimpleTransition.SliderType.BACK));
    }
  }

  public void setToForward() {
    if (selectionState == SelectionState.DATE) {
      if (month == 11) {
        month = 0;
        year++;
      } else {
        month++;
      }
      header.setDate(month, year);
      panelSlider.addSlide(
          createPanelDate(month, year), getSliderTransition(SimpleTransition.SliderType.FORWARD));
    } else if (selectionState == SelectionState.MONTH) {
      year++;
      header.setDate(month, year);
      panelSlider.addSlide(
          createPanelMonth(year), getSliderTransition(SimpleTransition.SliderType.FORWARD));
    } else {
      int oldYear = this.panelYear.getYear();
      panelSlider.addSlide(
          createPanelYear(oldYear + PanelYear.YEAR_CELL),
          getSliderTransition(SimpleTransition.SliderType.FORWARD));
    }
  }

  public void selectMonth() {
    if (selectionState != SelectionState.MONTH) {
      panelSlider.addSlide(
          createPanelMonth(year),
          getSliderTransition(
              selectionState == SelectionState.DATE
                  ? SimpleTransition.SliderType.TOP_DOWN
                  : SimpleTransition.SliderType.DOWN_TOP));
      selectionState = SelectionState.MONTH;
    } else {
      panelSlider.addSlide(
          createPanelDate(month, year), getSliderTransition(SimpleTransition.SliderType.DOWN_TOP));
      selectionState = SelectionState.DATE;
    }
  }

  public void selectYear() {
    if (selectionState != SelectionState.YEAR) {
      panelSlider.addSlide(
          createPanelYear(year), getSliderTransition(SimpleTransition.SliderType.TOP_DOWN));
      selectionState = SelectionState.YEAR;
    } else {
      panelSlider.addSlide(
          createPanelDate(month, year), getSliderTransition(SimpleTransition.SliderType.DOWN_TOP));
      selectionState = SelectionState.DATE;
    }
  }

  public DateSelectionMode getDateSelectionMode() {
    return dateSelectionModel.getDateSelectionMode();
  }

  public void setDateSelectionMode(DateSelectionMode dateSelectionMode) {
    if (getDateSelectionMode() != dateSelectionMode) {
      this.dateSelectionModel.setDateSelectionMode(dateSelectionMode);
      if (editor != null) {
        InputUtils.changeDateFormatted(
            editor,
            dateFormatPattern,
            getDateSelectionMode() == DateSelectionMode.BETWEEN_DATE_SELECTED,
            separator,
            getInputValidationListener());
        this.defaultPlaceholder = null;
        clearSelectedDate();
        commitEdit();
      }
      repaint();
    }
  }

  public void now() {
    LocalDate date = LocalDate.now(ZoneId.systemDefault());
    if (getDateSelectionMode() == DateSelectionMode.BETWEEN_DATE_SELECTED) {
      setSelectedDateRange(date, date);
    } else {
      setSelectedDate(date);
    }
  }

  public void toDateSelectionView() {
    LocalDate date = getSelectedDate();
    if (date == null) {
      date = LocalDate.now(ZoneId.systemDefault());
    }
    int m = date.getMonthValue() - 1;
    int y = date.getYear();
    if (selectionState != SelectionState.DATE || y != year || m != month) {
      panelSlider.addSlide(
          createPanelDate(m, y), getSliderTransition(SimpleTransition.SliderType.DEFAULT));
      month = m;
      year = y;
      selectionState = SelectionState.DATE;
      header.setDate(month, year);
      updateSelected();
    }
  }

  public void selectCurrentMonth() {
    LocalDate date = LocalDate.now(ZoneId.systemDefault());
    if (getDateSelectionMode() == DateSelectionMode.BETWEEN_DATE_SELECTED) {
      setSelectedDateRange(date.withDayOfMonth(1), date);
    } else {
      setSelectedDate(date);
    }
  }

  public void setSelectedDate(LocalDate date) {
    if (date == null) {
      clearSelectedDate();
    } else {
      dateSelectionModel.setDate(new SingleDate(date));
      if (getDateSelectionMode() == DateSelectionMode.BETWEEN_DATE_SELECTED) {
        dateSelectionModel.setToDate(new SingleDate(date));
      }
      slideTo(date);
    }
  }

  public void setSelectedDateRange(LocalDate from, LocalDate to) {
    if (getDateSelectionMode() == DateSelectionMode.SINGLE_DATE_SELECTED) {
      throw new IllegalArgumentException("Single date mode can't accept the range date");
    }
    dateSelectionModel.setSelectDate(new SingleDate(from), new SingleDate(to));
    slideTo(from);
  }

  public void setEditor(JFormattedTextField editor) {
    if (editor != this.editor) {
      JFormattedTextField old = this.editor;
      if (old != null) {
        uninstallEditor(old);
      }
      this.editor = editor;
      if (this.editor != null) {
        installEditor(editor);
        if (editorValidation) {
          validChanged(editor, isValid);
        } else {
          validChanged(editor, true);
        }
      }
    }
  }

  public Icon getEditorIcon() {
    return editorIcon;
  }

  public void setEditorIcon(Icon editorIcon) {
    this.editorIcon = editorIcon;
    editorButton.setIcon(editorIcon);
  }

  public DateSelectionAble getDateSelectionAble() {
    return dateSelectionModel.getDateSelectionAble();
  }

  public void setDateSelectionAble(DateSelectionAble dateSelectionAble) {
    this.dateSelectionModel.setDateSelectionAble(dateSelectionAble);
    if (selectionState == SelectionState.DATE) {
      panelDate.load();
    }
    commitEdit();
  }

  public Color getColor() {
    return color;
  }

  public void setColor(Color color) {
    this.color = color;
    repaint();
  }

  public String getSeparator() {
    return separator;
  }

  public void setSeparator(String separator) {
    if (separator == null) {
      throw new IllegalArgumentException("separator can't be null");
    }
    if (!this.separator.equals(separator)) {
      this.separator = separator;
      if (editor != null) {
        InputUtils.changeDateFormatted(
            editor,
            dateFormatPattern,
            getDateSelectionMode() == DateSelectionMode.BETWEEN_DATE_SELECTED,
            separator,
            getInputValidationListener());
        this.defaultPlaceholder = null;
        setEditorValue();
      }
    }
  }

  public String getDateFormat() {
    return this.dateFormatPattern;
  }

  public void setDateFormat(String format) {
    if (format == null) {
      throw new IllegalArgumentException("format can't be null");
    }
    if (!this.dateFormatPattern.equals(format)) {
      this.format = DateTimeFormatter.ofPattern(format);
      if (editor != null) {
        InputUtils.changeDateFormatted(
            editor,
            format,
            getDateSelectionMode() == DateSelectionMode.BETWEEN_DATE_SELECTED,
            separator,
            getInputValidationListener());
        this.defaultPlaceholder = null;
      }
      this.dateFormatPattern = format;
    }
  }

  public boolean isUsePanelOption() {
    return usePanelOption;
  }

  public void setUsePanelOption(boolean usePanelOption) {
    if (this.usePanelOption != usePanelOption) {
      this.usePanelOption = usePanelOption;
      if (usePanelOption) {
        if (panelDateOption == null) {
          panelDateOption = new PanelDateOption(this);
          panelDateOption.installDateOptionLabel();
        }
        add(panelDateOption, "dock east,gap 0 10 10 10");
        repaint();
        revalidate();
      } else {
        if (panelDateOption != null) {
          remove(panelDateOption);
          panelDateOption = null;
          repaint();
          revalidate();
        }
      }
    }
  }

  public PanelDateOptionLabel getPanelDateOptionLabel() {
    return panelDateOptionLabel;
  }

  public void setPanelDateOptionLabel(PanelDateOptionLabel panelDateOptionLabel) {
    if (panelDateOptionLabel == null) {
      throw new IllegalArgumentException("panelDateOptionLabel can't be null");
    }
    this.panelDateOptionLabel = panelDateOptionLabel;
    if (panelDateOption != null) {
      panelDateOption.installDateOptionLabel();
    }
  }

  public boolean isCloseAfterSelected() {
    return closeAfterSelected;
  }

  public void setCloseAfterSelected(boolean closeAfterSelected) {
    this.closeAfterSelected = closeAfterSelected;
  }

  public boolean isAnimationEnabled() {
    return animationEnabled;
  }

  public void setAnimationEnabled(boolean animationEnabled) {
    this.animationEnabled = animationEnabled;
  }

  public boolean isStartWeekOnMonday() {
    return startWeekOnMonday;
  }

  public void setStartWeekOnMonday(boolean startWeekOnMonday) {
    if (this.startWeekOnMonday != startWeekOnMonday) {
      this.startWeekOnMonday = startWeekOnMonday;
      if (selectionState == SelectionState.DATE && panelDate != null) {
        // update the panel date
        panelDate.load();
        panelDate.repaint();
        panelDate.revalidate();
      }
    }
  }

  public float getSelectionArc() {
    return selectionArc;
  }

  public void setSelectionArc(float selectionArc) {
    if (this.selectionArc != selectionArc) {
      this.selectionArc = selectionArc;
      updateSelected();
    }
  }

  public void clearSelectedDate() {
    dateSelectionModel.setSelectDate(null, null);
    updateSelected();
  }

  public boolean isDateSelected() {
    if (getDateSelectionMode() == DateSelectionMode.SINGLE_DATE_SELECTED) {
      return dateSelectionModel.getDate() != null;
    } else {
      return dateSelectionModel.getDate() != null && dateSelectionModel.getToDate() != null;
    }
  }

  public LocalDate getSelectedDate() {
    SingleDate date = dateSelectionModel.getDate();
    if (date != null) {
      return date.toLocalDate();
    }
    return null;
  }

  public LocalDate[] getSelectedDateRange() {
    SingleDate from = dateSelectionModel.getDate();
    if (from != null) {
      LocalDate[] dates = new LocalDate[2];
      dates[0] = from.toLocalDate();
      SingleDate to = dateSelectionModel.getToDate();
      if (to != null) {
        dates[1] = to.toLocalDate();
        return dates;
      }
    }
    return null;
  }

  public String getSelectedDateAsString() {
    if (isDateSelected()) {
      if (getDateSelectionMode() == DateSelectionMode.SINGLE_DATE_SELECTED) {
        return format.format(getSelectedDate());
      } else {
        LocalDate[] dates = getSelectedDateRange();
        return format.format(dates[0]) + separator + format.format(dates[1]);
      }
    } else {
      return null;
    }
  }

  public void slideTo(LocalDate date) {
    int m = date.getMonthValue() - 1;
    int y = date.getYear();
    if (year != y || month != m) {
      if (year < y || (year <= y && month < m)) {
        panelSlider.addSlide(
            createPanelDate(m, y), getSliderTransition(SimpleTransition.SliderType.FORWARD));
      } else {
        panelSlider.addSlide(
            createPanelDate(m, y), getSliderTransition(SimpleTransition.SliderType.BACK));
      }
      month = m;
      year = y;
      selectionState = SelectionState.DATE;
      header.setDate(month, year);
    } else {
      if (selectionState != SelectionState.DATE) {
        panelSlider.addSlide(
            createPanelDate(m, y), getSliderTransition(SimpleTransition.SliderType.DOWN_TOP));
        selectionState = SelectionState.DATE;
      }
    }
    updateSelected();
  }

  public void addDateSelectionListener(DateSelectionListener listener) {
    listenerList.add(DateSelectionListener.class, listener);
  }

  public void removeDateSelectionListener(DateSelectionListener listener) {
    listenerList.remove(DateSelectionListener.class, listener);
  }

  public DefaultDateCellRenderer getDefaultDateCellRenderer() {
    return defaultDateCellRenderer;
  }

  public void setDefaultDateCellRenderer(DefaultDateCellRenderer defaultDateCellRenderer) {
    this.defaultDateCellRenderer = defaultDateCellRenderer;
    repaint();
  }

  public Header getHeader() {
    return header;
  }

  public DateSelectionModel getDateSelectionModel() {
    return dateSelectionModel;
  }

  public void setDateSelectionModel(DateSelectionModel dateSelectionModel) {
    if (dateSelectionModel == null) {
      throw new IllegalArgumentException("dateSelectionModel can't be null");
    }
    if (this.dateSelectionModel != dateSelectionModel) {
      DateSelectionModel old = this.dateSelectionModel;
      if (old != null) {
        old.removeDatePickerSelectionListener(this);
      }
      this.dateSelectionModel = dateSelectionModel;
      this.dateSelectionModel.addDatePickerSelectionListener(this);
    }
  }

  @Override
  public void dateSelectionModelChanged(DateSelectionModelEvent e) {
    if (e.getAction() == DateSelectionModelEvent.DATE) {
      verifyDateSelection();
    }
    repaint();
  }

  @Override
  public void dateControlChanged(DateControlEvent e) {
    if (e.getType() == DateControlEvent.BACK) {
      setToBack();
    } else if (e.getType() == DateControlEvent.FORWARD) {
      setToForward();
    } else if (e.getType() == DateControlEvent.MONTH) {
      selectMonth();
    } else if (e.getType() == DateControlEvent.YEAR) {
      selectYear();
    }
  }

  @Override
  public void stateChanged(ChangeEvent e) {
    if (e.getSource() == panelMonth) {
      this.month = panelMonth.getSelectedMonth();
      header.setDate(month, year);
      panelSlider.addSlide(
          createPanelDate(month, year), getSliderTransition(SimpleTransition.SliderType.DOWN_TOP));
      selectionState = SelectionState.DATE;
    } else if (e.getSource() == panelYear) {
      this.year = panelYear.getSelectedYear();
      header.setDate(month, year);
      panelSlider.addSlide(
          createPanelMonth(year), getSliderTransition(SimpleTransition.SliderType.DOWN_TOP));
      selectionState = SelectionState.MONTH;
    }
  }

  protected DateSelectionModel createDefaultDateSelection() {
    return new DateSelectionModel();
  }

  protected SliderTransition getSliderTransition(SimpleTransition.SliderType type) {
    if (!animationEnabled) {
      return null;
    }
    return SimpleTransition.get(type);
  }

  private void updateSelected() {
    if (selectionState == SelectionState.DATE) {
      panelDate.checkSelection();
    } else if (selectionState == SelectionState.MONTH) {
      panelMonth.checkSelection();
    } else if (selectionState == SelectionState.YEAR) {
      panelYear.checkSelection();
    }
  }

  private void installEditor(JFormattedTextField editor) {
    if (editor != null) {
      JToolBar toolBar = new JToolBar();
      editorButton =
          new JButton(editorIcon != null ? editorIcon : ResourceUtil.getIcon(OtherIcon.CALENDAR));
      toolBar.add(editorButton);
      editorButton.addActionListener(
          e -> {
            if (editor.isEnabled()) {
              editor.grabFocus();
              showPopup();
            }
          });
      InputUtils.useDateInput(
          editor,
          dateFormatPattern,
          getDateSelectionMode() == DateSelectionMode.BETWEEN_DATE_SELECTED,
          separator,
          getValueCallback(),
          getInputValidationListener());
      setEditorValue();
      editor.putClientProperty(FlatClientProperties.TEXT_FIELD_TRAILING_COMPONENT, toolBar);
      addDateSelectionListener(getDateSelectionListener());
    }
  }

  private void uninstallEditor(JFormattedTextField editor) {
    if (editor != null) {
      editorButton = null;
      InputUtils.removePropertyChange(editor);
      if (dateSelectionListener != null) {
        removeDateSelectionListener(dateSelectionListener);
      }
    }
  }

  private InputUtils.ValueCallback getValueCallback() {
    if (valueCallback == null) {
      valueCallback =
          value -> {
            if (value == null && isDateSelected()) {
              clearSelectedDate();
            } else {
              if (value != null && !value.equals(getSelectedDateAsString())) {
                if (getDateSelectionMode() == DateSelectionMode.SINGLE_DATE_SELECTED) {
                  LocalDate date = InputUtils.stringToDate(format, value.toString());
                  if (date != null) {
                    setSelectedDate(date);
                  }
                } else {
                  LocalDate[] dates = InputUtils.stringToDate(format, separator, value.toString());
                  if (dates != null) {
                    setSelectedDateRange(dates[0], dates[1]);
                  }
                }
              }
            }
          };
    }
    return valueCallback;
  }

  private DateSelectionListener getDateSelectionListener() {
    if (dateSelectionListener == null) {
      dateSelectionListener = _ -> setEditorValue();
    }
    return dateSelectionListener;
  }

  private void setEditorValue() {
    String value = getSelectedDateAsString();
    if (value != null) {
      if (!editor.getText().equalsIgnoreCase(value)) {
        editor.setValue(value);
      }
    } else {
      editor.setValue(null);
    }
  }

  private InputValidationListener<LocalDate> getInputValidationListener() {
    if (inputValidationListener == null) {
      inputValidationListener =
          new InputValidationListener<LocalDate>() {

            @Override
            public boolean isValidation() {
              return dateSelectionModel.getDateSelectionAble() != null;
            }

            @Override
            public void inputChanged(boolean status) {
              checkValidation(status);
            }

            @Override
            public boolean checkSelectionAble(LocalDate date) {
              if (dateSelectionModel.getDateSelectionAble() == null) return true;
              return dateSelectionModel.getDateSelectionAble().isDateSelectedAble(date);
            }
          };
    }
    return inputValidationListener;
  }

  @Override
  protected String getDefaultPlaceholder() {
    if (defaultPlaceholder == null) {
      if (getDateSelectionMode() == DateSelectionMode.BETWEEN_DATE_SELECTED) {
        String d = InputUtils.datePatternToInputFormat(dateFormatPattern, "-");
        defaultPlaceholder = d + separator + d;
      } else {
        defaultPlaceholder = InputUtils.datePatternToInputFormat(dateFormatPattern, "-");
      }
    }
    return defaultPlaceholder;
  }

  @Override
  protected void popupOpen() {
    toDateSelectionView();
  }

  private void verifyDateSelection() {
    if (getDateSelectionMode() == DateSelectionMode.BETWEEN_DATE_SELECTED) {
      SingleDate fromDate = dateSelectionModel.getDate();
      SingleDate toDate = dateSelectionModel.getToDate();
      if ((fromDate == null && toDate != null) || (fromDate != null && toDate == null)) {
        return;
      }
    }
    if (isCloseAfterSelected()) {
      closePopup();
    }
    fireDateSelectionChanged(new DateSelectionEvent(this));
    if (panelDateOption != null) {
      panelDateOption.setSelectedCustom();
    }
  }

  public void fireDateSelectionChanged(DateSelectionEvent event) {
    Object[] listeners = listenerList.getListenerList();
    for (int i = listeners.length - 2; i >= 0; i -= 2) {
      if (listeners[i] == DateSelectionListener.class) {
        ((DateSelectionListener) listeners[i + 1]).dateSelected(event);
      }
    }
  }

  private void setPanelDate(PanelDate panelDate) {
    this.panelDate = panelDate;
  }

  private void setPanelMonth(PanelMonth panelMonth) {
    PanelMonth old = this.panelMonth;
    if (old != null) {
      old.removeChangeListener(this);
    }
    this.panelMonth = panelMonth;
    this.panelMonth.addChangeListener(this);
  }

  private void setPanelYear(PanelYear panelYear) {
    PanelYear old = this.panelYear;
    if (old != null) {
      old.removeChangeListener(this);
    }
    this.panelYear = panelYear;
    this.panelYear.addChangeListener(this);
  }

  private PanelDate createPanelDate(int month, int year) {
    PanelDate panelDate = new PanelDate(this, month, year);
    setPanelDate(panelDate);
    return panelDate;
  }

  private PanelMonth createPanelMonth(int year) {
    PanelMonth panelMonth = new PanelMonth(this, year);
    setPanelMonth(panelMonth);
    return panelMonth;
  }

  private PanelYear createPanelYear(int year) {
    PanelYear panelYear = new PanelYear(this, year);
    setPanelYear(panelYear);
    return panelYear;
  }

  public static void setDefaultWeekdays(String[] defaultWeekdays) {
    if (defaultWeekdays == null) {
      DatePicker.defaultWeekdays = null;
    } else {
      DatePicker.defaultWeekdays = Arrays.copyOf(defaultWeekdays, defaultWeekdays.length);
    }
  }

  public static String[] getDefaultWeekdays() {
    if (defaultWeekdays == null) {
      return DateFormatSymbols.getInstance().getShortWeekdays();
    }
    return Arrays.copyOf(defaultWeekdays, defaultWeekdays.length);
  }

  public static void setDefaultMonths(String[] defaultMonths) {
    if (defaultMonths == null) {
      DatePicker.defaultMonths = null;
    } else {
      DatePicker.defaultMonths = Arrays.copyOf(defaultMonths, defaultMonths.length);
    }
  }

  public static String[] getDefaultMonths() {
    if (defaultMonths == null) {
      return DateFormatSymbols.getInstance().getMonths();
    }
    return Arrays.copyOf(defaultMonths, defaultMonths.length);
  }

  public enum DateSelectionMode {
    SINGLE_DATE_SELECTED,
    BETWEEN_DATE_SELECTED
  }

  private enum SelectionState {
    DATE,
    MONTH,
    YEAR
  }
}
