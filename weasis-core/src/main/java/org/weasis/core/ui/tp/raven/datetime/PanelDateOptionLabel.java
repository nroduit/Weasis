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

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * {@link PanelDateOptionLabel}
 *
 * @author Raven Laing
 * @see <a href="https://github.com/DJ-Raven/swing-datetime-picker">swing-datetime-picker</a>
 */
public class PanelDateOptionLabel {

  private final List<Item> listItems = new ArrayList<>();

  public PanelDateOptionLabel() {}

  public void add(String label, LabelCallback callback) {
    listItems.add(new Item(label, callback));
  }

  public List<Item> getListItems() {
    return listItems;
  }

  private void remove(int index) {
    listItems.remove(index);
  }

  private void clear() {
    listItems.clear();
  }

  public static class Item {

    public String getLabel() {
      return label;
    }

    public LabelCallback getCallback() {
      return callback;
    }

    public Item(String label, LabelCallback callback) {
      this.label = label;
      this.callback = callback;
    }

    private final String label;
    private final LabelCallback callback;
  }

  public interface LabelCallback {

    LabelCallback TODAY = () -> new LocalDate[] {LocalDate.now(ZoneId.systemDefault())};
    LabelCallback YESTERDAY =
        () -> new LocalDate[] {LocalDate.now(ZoneId.systemDefault()).minusDays(1)};
    LabelCallback LAST_7_DAYS =
        () ->
            new LocalDate[] {
              LocalDate.now(ZoneId.systemDefault()).minusDays(7),
              LocalDate.now(ZoneId.systemDefault())
            };
    LabelCallback LAST_30_DAYS =
        () ->
            new LocalDate[] {
              LocalDate.now(ZoneId.systemDefault()).minusDays(30),
              LocalDate.now(ZoneId.systemDefault())
            };
    LabelCallback THIS_MONTH =
        () -> {
          LocalDate now = LocalDate.now(ZoneId.systemDefault());
          return new LocalDate[] {now.withDayOfMonth(1), now.withDayOfMonth(now.lengthOfMonth())};
        };
    LabelCallback LAST_MONTH =
        () -> {
          LocalDate lastMonth = LocalDate.now(ZoneId.systemDefault()).minusMonths(1);
          return new LocalDate[] {
            lastMonth.withDayOfMonth(1), lastMonth.withDayOfMonth(lastMonth.lengthOfMonth())
          };
        };
    LabelCallback LAST_YEAR =
        () -> {
          LocalDate lastYear = LocalDate.now(ZoneId.systemDefault()).minusYears(1).withDayOfYear(1);
          return new LocalDate[] {lastYear, lastYear.withDayOfYear(lastYear.lengthOfYear())};
        };

    LabelCallback CUSTOM = null;

    LocalDate[] getDate();
  }
}
