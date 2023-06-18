/*
 * Copyright (c) 2022 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.util;

import com.formdev.flatlaf.ui.FlatUIUtils;
import com.github.lgooddatepicker.components.DatePickerSettings;
import com.github.lgooddatepicker.components.DatePickerSettings.DateArea;
import java.awt.Color;
import javax.swing.BorderFactory;

public class CalendarUtil {

  private CalendarUtil() {}

  public static void adaptCalendarColors(DatePickerSettings settings) {
    Color textButton = FlatUIUtils.getUIColor("Button.foreground", Color.BLACK);
    Color backgroundButton = FlatUIUtils.getUIColor("Button.background", Color.WHITE);
    Color disabledBackgroundButton = FlatUIUtils.getUIColor("Button.disabledText", Color.GRAY);

    Color panel = FlatUIUtils.getUIColor("Panel.background", Color.LIGHT_GRAY);
    Color error = FlatUIUtils.getUIColor("Component.error.borderColor", Color.BLACK);
    Color border = FlatUIUtils.getUIColor("Component.borderColor", Color.BLACK);

    Color text = FlatUIUtils.getUIColor("TextField.foreground", Color.BLACK);
    Color background = FlatUIUtils.getUIColor("TextField.background", Color.WHITE);
    Color selectionText = FlatUIUtils.getUIColor("TextField.selectionForeground", Color.WHITE);
    Color selectionBackground =
        FlatUIUtils.getUIColor("TextField.selectionBackground", Color.BLACK);
    Color disabledBackground = FlatUIUtils.getUIColor("TextField.disabledBackground", Color.GRAY);

    settings.setColor(DateArea.BackgroundClearLabel, background);
    settings.setColor(DateArea.BackgroundMonthAndYearMenuLabels, background);
    settings.setColor(DateArea.BackgroundMonthAndYearNavigationButtons, backgroundButton);
    settings.setColor(DateArea.BackgroundCalendarPanelLabelsOnHover, selectionBackground);
    settings.setColor(DateArea.BackgroundOverallCalendarPanel, panel);
    settings.setColor(DateArea.BackgroundTodayLabel, background);
    settings.setColor(DateArea.BackgroundTopLeftLabelAboveWeekNumbers, background);

    settings.setColor(DateArea.CalendarBackgroundNormalDates, background);
    settings.setColor(DateArea.CalendarBackgroundSelectedDate, selectionBackground);
    settings.setColor(DateArea.CalendarBackgroundVetoedDates, disabledBackgroundButton);
    settings.setColor(DateArea.CalendarBorderSelectedDate, border);
    settings.setColor(DateArea.CalendarDefaultBackgroundHighlightedDates, selectionBackground);
    settings.setColor(DateArea.CalendarDefaultTextHighlightedDates, selectionText);
    settings.setColor(DateArea.CalendarTextNormalDates, text);
    settings.setColor(DateArea.CalendarTextWeekdays, text);
    settings.setColor(DateArea.CalendarTextWeekNumbers, text);

    settings.setColor(DateArea.TextClearLabel, text);
    settings.setColor(DateArea.TextMonthAndYearMenuLabels, text);
    settings.setColor(DateArea.TextMonthAndYearNavigationButtons, textButton);
    settings.setColor(DateArea.TextTodayLabel, text);
    settings.setColor(DateArea.TextCalendarPanelLabelsOnHover, selectionText);

    settings.setColor(DateArea.TextFieldBackgroundDisallowedEmptyDate, disabledBackground);
    settings.setColor(DateArea.TextFieldBackgroundInvalidDate, background);
    settings.setColor(DateArea.TextFieldBackgroundValidDate, background);
    settings.setColor(DateArea.TextFieldBackgroundVetoedDate, background);
    settings.setColor(DateArea.TextFieldBackgroundDisabled, disabledBackground);

    settings.setColor(DateArea.DatePickerTextInvalidDate, error);
    settings.setColor(DateArea.DatePickerTextValidDate, text);
    settings.setColor(DateArea.DatePickerTextVetoedDate, text);
    settings.setColor(DateArea.DatePickerTextDisabled, disabledBackground);

    settings.setColorBackgroundWeekdayLabels(backgroundButton, true);
    settings.setColorBackgroundWeekNumberLabels(backgroundButton, true);
    settings.setBorderCalendarPopup(BorderFactory.createEmptyBorder());
  }
}
