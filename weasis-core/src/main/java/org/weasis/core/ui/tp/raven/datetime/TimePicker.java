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
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import javax.swing.*;
import net.miginfocom.swing.MigLayout;
import org.weasis.core.api.util.ResourceUtil;
import org.weasis.core.api.util.ResourceUtil.OtherIcon;
import org.weasis.core.ui.tp.raven.datetime.component.PanelPopupEditor;
import org.weasis.core.ui.tp.raven.datetime.component.time.Header;
import org.weasis.core.ui.tp.raven.datetime.component.time.PanelClock;
import org.weasis.core.ui.tp.raven.datetime.component.time.TimeSelectionModel;
import org.weasis.core.ui.tp.raven.datetime.component.time.event.TimeActionListener;
import org.weasis.core.ui.tp.raven.datetime.component.time.event.TimeSelectionModelEvent;
import org.weasis.core.ui.tp.raven.datetime.component.time.event.TimeSelectionModelListener;
import org.weasis.core.ui.tp.raven.datetime.event.TimeSelectionEvent;
import org.weasis.core.ui.tp.raven.datetime.event.TimeSelectionListener;
import org.weasis.core.ui.tp.raven.datetime.util.InputUtils;
import org.weasis.core.ui.tp.raven.datetime.util.InputValidationListener;

/**
 * TimePicker is a class that provides methods for creating a time picker.
 *
 * @author Raven Laing
 * @see <a href="https://github.com/DJ-Raven/swing-datetime-picker">swing-datetime-picker</a>
 */
public class TimePicker extends PanelPopupEditor implements TimeSelectionModelListener {

  private final DateTimeFormatter format12h =
      DateTimeFormatter.ofPattern("hh:mm a", Locale.ENGLISH);
  private final DateTimeFormatter format24h = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH);
  private TimeSelectionModel timeSelectionModel;
  private TimeSelectionListener timeSelectionListener;
  private InputValidationListener<LocalTime> inputValidationListener;
  private InputUtils.ValueCallback valueCallback;
  private Icon editorIcon;
  private MigLayout layout;
  private int orientation = SwingConstants.VERTICAL;
  private Color color;
  private JButton editorButton;
  private LocalTime oldSelectedTime;

  private Header header;
  private PanelClock panelClock;

  public TimePicker() {
    this(null);
  }

  public TimePicker(TimeSelectionModel model) {
    init(model);
  }

  private void init(TimeSelectionModel model) {
    putClientProperty(
        FlatClientProperties.STYLE,
        "[light]background:darken($Panel.background,2%);"
            + "[dark]background:lighten($Panel.background,2%);");
    layout = new MigLayout("wrap,fill,insets 3", "fill", "fill");
    setLayout(layout);

    if (model == null) {
      model = createDefaultTimeSelection();
    }
    setTimeSelectionModel(model);

    header = new Header(this, new HeaderActionListener());
    panelClock = new PanelClock(this, new ClockActionListener());
    add(header, "width 120:120");
    add(panelClock, "width 230:230,height 230:230");

    JPanel buttonPanel = createButtonPanel();
    add(buttonPanel, "dock south");
  }

  public int getOrientation() {
    return orientation;
  }

  public void setOrientation(int orientation) {
    if (this.orientation != orientation) {
      String c = orientation == SwingConstants.VERTICAL ? "wrap," : "";
      layout.setLayoutConstraints(c + "fill,insets 3");
      this.orientation = orientation;
      header.setOrientation(orientation);
      revalidate();
    }
  }

  public void setEditor(JFormattedTextField editor) {
    if (editor != this.editor) {
      if (this.editor != null) {
        uninstallEditor(this.editor);
      }
      this.editor = editor;
      if (editor != null) {
        installEditor(editor);
        if (editorValidation) {
          validChanged(editor, isValid);
        } else {
          validChanged(editor, true);
        }
      }
    }
  }

  public boolean is24HourView() {
    return panelClock.isUse24hour();
  }

  public void set24HourView(boolean hour24) {
    if (panelClock.isUse24hour() != hour24) {
      panelClock.setUse24hour(hour24);
      header.setUse24hour(hour24);
      panelClock.updateClock();
      header.updateHeader();
      repaint();
      if (editor != null) {
        InputUtils.changeTimeFormatted(editor, hour24, getInputValidationListener());
        this.defaultPlaceholder = null;
        setEditorValue();
      }
    }
  }

  public Color getColor() {
    return color;
  }

  public void setColor(Color color) {
    this.color = color;
    header.setColor(color);
    panelClock.setColor(color);
  }

  public Icon getEditorIcon() {
    return editorIcon;
  }

  public void setEditorIcon(Icon editorIcon) {
    this.editorIcon = editorIcon;
    if (editorButton != null) {
      editorButton.setIcon(editorIcon);
    }
  }

  /** Set time to current local time */
  public void now() {
    setSelectedTime(LocalTime.now(ZoneId.systemDefault()));
  }

  public void setSelectedTime(LocalTime time) {
    if (time == null) {
      clearSelectedTime();
    } else {
      int hour = time.getHour();
      int minute = time.getMinute();
      timeSelectionModel.set(hour, minute);
    }
  }

  public void clearSelectedTime() {
    timeSelectionModel.set(-1, -1);
  }

  public boolean isTimeSelected() {
    return timeSelectionModel.isSelected();
  }

  public LocalTime getSelectedTime() {
    return timeSelectionModel.getTime();
  }

  public String getSelectedTimeAsString() {
    if (isTimeSelected()) {
      if (panelClock.isUse24hour()) {
        return format24h.format(getSelectedTime());
      } else {
        return format12h.format(getSelectedTime());
      }
    } else {
      return null;
    }
  }

  protected TimeSelectionModel createDefaultTimeSelection() {
    return new TimeSelectionModel();
  }

  public TimeSelectionAble getTimeSelectionAble() {
    return timeSelectionModel.getTimeSelectionAble();
  }

  public void setTimeSelectionAble(TimeSelectionAble timeSelectionAble) {
    timeSelectionModel.setTimeSelectionAble(timeSelectionAble);
    commitEdit();
  }

  public TimeSelectionModel getTimeSelectionModel() {
    return timeSelectionModel;
  }

  public void setTimeSelectionModel(TimeSelectionModel timeSelectionModel) {
    if (timeSelectionModel == null) {
      throw new IllegalArgumentException("timeSelectionModel can't be null");
    }
    if (this.timeSelectionModel != timeSelectionModel) {
      TimeSelectionModel old = this.timeSelectionModel;
      if (old != null) {
        old.removeTimePickerSelectionListener(this);
      }
      this.timeSelectionModel = timeSelectionModel;
      this.timeSelectionModel.addTimePickerSelectionListener(this);
    }
  }

  public boolean isHourSelectionView() {
    return panelClock.isHourSelectionView();
  }

  public void setHourSelectionView(boolean hourSelectionView) {
    header.setHourSelectionView(hourSelectionView);
    panelClock.setHourSelectionViewImmediately(hourSelectionView);
  }

  public void addTimeSelectionListener(TimeSelectionListener listener) {
    listenerList.add(TimeSelectionListener.class, listener);
  }

  public void removeTimeSelectionListener(TimeSelectionListener listener) {
    listenerList.remove(TimeSelectionListener.class, listener);
  }

  private void installEditor(JFormattedTextField editor) {
    JToolBar toolBar = new JToolBar();
    editorButton =
        new JButton(editorIcon != null ? editorIcon : ResourceUtil.getIcon(OtherIcon.CLOCK));
    toolBar.add(editorButton);
    editorButton.addActionListener(
        e -> {
          if (editor.isEnabled()) {
            editor.grabFocus();
            showPopup();
          }
        });
    InputUtils.useTimeInput(
        editor, panelClock.isUse24hour(), getValueCallback(), getInputValidationListener());
    setEditorValue();
    editor.putClientProperty(FlatClientProperties.TEXT_FIELD_TRAILING_COMPONENT, toolBar);
    addTimeSelectionListener(getTimeSelectionListener());
  }

  private void uninstallEditor(JFormattedTextField editor) {
    if (editor != null) {
      editorButton = null;
      InputUtils.removePropertyChange(editor);
      if (timeSelectionListener != null) {
        removeTimeSelectionListener(timeSelectionListener);
      }
    }
  }

  private InputUtils.ValueCallback getValueCallback() {
    if (valueCallback == null) {
      valueCallback =
          value -> {
            if (value == null && isTimeSelected()) {
              clearSelectedTime();
            } else {
              if (value != null && !value.equals(getSelectedTimeAsString())) {
                LocalTime time =
                    InputUtils.stringToTime(panelClock.isUse24hour(), value.toString());
                if (time != null) {
                  setSelectedTime(time);
                }
              }
            }
          };
    }
    return valueCallback;
  }

  private TimeSelectionListener getTimeSelectionListener() {
    if (timeSelectionListener == null) {
      timeSelectionListener = _ -> setEditorValue();
    }
    return timeSelectionListener;
  }

  private void setEditorValue() {
    String value = getSelectedTimeAsString();
    if (value != null) {
      if (!editor.getText().equalsIgnoreCase(value)) {
        editor.setValue(value);
      }
    } else {
      editor.setValue(null);
    }
  }

  private InputValidationListener<LocalTime> getInputValidationListener() {
    if (inputValidationListener == null) {
      inputValidationListener =
          new InputValidationListener<>() {

            @Override
            public boolean isValidation() {
              return timeSelectionModel.getTimeSelectionAble() != null;
            }

            @Override
            public void inputChanged(boolean status) {
              checkValidation(status);
            }

            @Override
            public boolean checkSelectionAble(LocalTime time) {
              int hour = time.getHour();
              int minute = time.getMinute();
              return timeSelectionModel.checkSelection(hour, minute);
            }
          };
    }
    return inputValidationListener;
  }

  @Override
  protected String getDefaultPlaceholder() {
    if (defaultPlaceholder == null) {
      String pattern;
      if (is24HourView()) {
        pattern = "--:--";
      } else {
        pattern = "--:-- --";
      }
      defaultPlaceholder = pattern;
    }
    return defaultPlaceholder;
  }

  @Override
  protected void popupOpen() {
    setHourSelectionView(true);
  }

  private void verifyTimeSelection() {
    LocalTime time = getSelectedTime();
    if ((time == null && oldSelectedTime == null)) {
      return;
    } else if (time != null && oldSelectedTime != null) {
      if (time.equals(oldSelectedTime)) {
        return;
      }
    }
    oldSelectedTime = time;
    fireTimeSelectionChanged(new TimeSelectionEvent(this));
  }

  public void fireTimeSelectionChanged(TimeSelectionEvent event) {
    Object[] listeners = listenerList.getListenerList();
    for (int i = listeners.length - 2; i >= 0; i -= 2) {
      if (listeners[i] == TimeSelectionListener.class) {
        ((TimeSelectionListener) listeners[i + 1]).timeSelected(event);
      }
    }
  }

  @Override
  public void setEnabled(boolean enabled) {
    super.setEnabled(enabled);
    header.setEnabled(enabled);
    panelClock.setEnabled(enabled);
  }

  public Header getHeader() {
    return header;
  }

  @Override
  public void timeSelectionModelChanged(TimeSelectionModelEvent e) {
    // check if user clear the time selection. so we set to the hour selection view
    if (!timeSelectionModel.isSelected() && e.getAction() == TimeSelectionModelEvent.HOUR_MINUTE) {
      panelClock.setHourSelectionView(true);
      header.setHourSelectionView(true);
    }
    panelClock.updateClock();
    header.updateHeader();
    repaint();
    verifyTimeSelection();
  }

  private class ClockActionListener implements TimeActionListener {

    @Override
    public void selectionViewChanged(boolean isHourSelectionView) {
      if (isHourSelectionView) {
        panelClock.setHourSelectionView(false);
        header.setHourSelectionView(false);
      }
    }
  }

  private class HeaderActionListener implements TimeActionListener {

    @Override
    public void selectionViewChanged(boolean isHourSelectionView) {
      panelClock.setHourSelectionView(isHourSelectionView);
    }
  }
}
