/*
 * Copyright (c) 2024 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.tp.raven.datetime.component.date;

import com.formdev.flatlaf.FlatClientProperties;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JPanel;
import net.miginfocom.swing.MigLayout;
import org.weasis.core.api.util.ResourceUtil;
import org.weasis.core.api.util.ResourceUtil.ActionIcon;
import org.weasis.core.ui.tp.raven.datetime.DatePicker;
import org.weasis.core.ui.tp.raven.datetime.component.date.event.DateControlEvent;
import org.weasis.core.ui.tp.raven.datetime.component.date.event.DateControlListener;

/* Header is a class that provides a header for the DatePicker.
 *
 * @author Raven Laing
 * @see <a href="https://github.com/DJ-Raven/swing-datetime-picker">swing-datetime-picker</a>
 */
public class Header extends JPanel {

  protected JButton buttonMonth;
  protected JButton buttonYear;

  protected Icon backIcon;

  protected Icon forwardIcon;

  public Header() {
    this(10, 2023);
  }

  public Header(int month, int year) {
    init(month, year);
  }

  private void init(int month, int year) {
    putClientProperty(FlatClientProperties.STYLE, "background:null");
    setLayout(new MigLayout("fill,insets 3", "[]push[][]push[]", "fill"));

    JButton cmdBack = createButton();
    JButton cmdNext = createButton();
    backIcon = ResourceUtil.getDirectionIcon(ActionIcon.PREVIOUS);
    forwardIcon = ResourceUtil.getDirectionIcon(ActionIcon.NEXT);
    cmdBack.setIcon(backIcon);
    cmdNext.setIcon(forwardIcon);
    buttonMonth = createButton();
    buttonYear = createButton();

    cmdBack.addActionListener(
        e ->
            fireDateControlChanged(
                new DateControlEvent(this, DateControlEvent.DAY_STATE, DateControlEvent.BACK)));
    cmdNext.addActionListener(
        e ->
            fireDateControlChanged(
                new DateControlEvent(this, DateControlEvent.DAY_STATE, DateControlEvent.FORWARD)));
    buttonMonth.addActionListener(
        e ->
            fireDateControlChanged(
                new DateControlEvent(this, DateControlEvent.DAY_STATE, DateControlEvent.MONTH)));
    buttonYear.addActionListener(
        e ->
            fireDateControlChanged(
                new DateControlEvent(this, DateControlEvent.DAY_STATE, DateControlEvent.YEAR)));

    add(cmdBack);
    add(buttonMonth);
    add(buttonYear);
    add(cmdNext);
    setDate(month, year);
  }

  protected JButton createButton() {
    JButton button = new JButton();
    button.putClientProperty(
        FlatClientProperties.STYLE,
        "background:null;"
            + "arc:10;"
            + "borderWidth:0;"
            + "focusWidth:0;"
            + "innerFocusWidth:0;"
            + "margin:0,5,0,5");
    return button;
  }

  public void addDateControlListener(DateControlListener listener) {
    listenerList.add(DateControlListener.class, listener);
  }

  public void removeDateControlListener(DateControlListener listener) {
    listenerList.remove(DateControlListener.class, listener);
  }

  public void fireDateControlChanged(DateControlEvent event) {
    Object[] listeners = listenerList.getListenerList();
    for (int i = listeners.length - 2; i >= 0; i -= 2) {
      if (listeners[i] == DateControlListener.class) {
        ((DateControlListener) listeners[i + 1]).dateControlChanged(event);
      }
    }
  }

  public void setDate(int month, int year) {
    buttonMonth.setText(DatePicker.getDefaultMonths()[month]);
    buttonYear.setText(year + "");
  }

  public Icon getBackIcon() {
    return backIcon;
  }

  public void setBackIcon(Icon backIcon) {
    this.backIcon = backIcon;
  }

  public Icon getForwardIcon() {
    return forwardIcon;
  }

  public void setForwardIcon(Icon forwardIcon) {
    this.forwardIcon = forwardIcon;
  }
}
