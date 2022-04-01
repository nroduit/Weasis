/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.api.gui.util;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import javax.swing.text.DefaultFormatterFactory;
import javax.swing.text.NumberFormatter;
import org.weasis.core.api.util.LocalUtil;

public class DecFormatter {

  private DecFormatter() {}

  private static final NumberFormat df1 = LocalUtil.getNumberInstance(); // 1 decimals
  private static final NumberFormat df2 = LocalUtil.getNumberInstance(); // 2 decimals
  private static final NumberFormat df4 = LocalUtil.getNumberInstance(); // 4 decimals
  private static final NumberFormat percent2 = LocalUtil.getPercentInstance();
  private static final DecimalFormat decimalAndNumber =
      new DecimalFormat("#,##0.#", LocalUtil.getDecimalFormatSymbols());
  // Scientific format with 4 decimals
  private static final DecimalFormat dfSci =
      new DecimalFormat("0.####E0", LocalUtil.getDecimalFormatSymbols()); // NON-NLS

  static {
    df1.setMaximumFractionDigits(1);
    df2.setMaximumFractionDigits(2);
    df4.setMaximumFractionDigits(4);
    percent2.setMaximumFractionDigits(2);
  }

  public static String allNumber(Number val) {
    return decimalAndNumber.format(val);
  }

  public static String oneDecimal(Number val) {
    return df1.format(val);
  }

  public static String twoDecimal(Number val) {
    return df2.format(val);
  }

  public static String fourDecimal(Number val) {
    return df4.format(val);
  }

  public static String percentTwoDecimal(Number val) {
    return percent2.format(val);
  }

  public static String scientificFormat(Number val) {
    return dfSci.format(val);
  }

  public static DefaultFormatterFactory setPreciseDoubleFormat(double min, double max) {
    NumberFormatter displayFormatter =
        new NumberFormatter(new DecimalFormat("#,##0.##", LocalUtil.getDecimalFormatSymbols()));
    displayFormatter.setValueClass(Double.class);
    NumberFormatter editFormatter =
        new NumberFormatter(
            new DecimalFormat("#,##0.0#############", LocalUtil.getDecimalFormatSymbols()));
    editFormatter.setValueClass(Double.class);
    editFormatter.setMinimum(min);
    editFormatter.setMaximum(max);
    editFormatter.setAllowsInvalid(true);
    return new DefaultFormatterFactory(displayFormatter, displayFormatter, editFormatter);
  }
}
