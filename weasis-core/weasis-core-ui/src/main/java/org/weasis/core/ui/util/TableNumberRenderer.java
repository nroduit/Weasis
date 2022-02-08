/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.util;

import java.text.NumberFormat;
import javax.swing.SwingConstants;
import javax.swing.table.DefaultTableCellRenderer;
import org.weasis.core.api.util.LocalUtil;

/** @author Nicolas Roduit */
public class TableNumberRenderer extends DefaultTableCellRenderer {
  private final NumberFormat formatter;

  public TableNumberRenderer() {
    this(2);
  }

  public TableNumberRenderer(int maxDecimal) {
    this.formatter = LocalUtil.getNumberInstance();
    formatter.setMaximumFractionDigits(maxDecimal);
  }

  @Override
  public void setValue(Object value) {
    if (value instanceof Number) {
      setHorizontalAlignment(SwingConstants.RIGHT);
      setText(formatter.format(value));
    } else {
      setHorizontalAlignment(SwingConstants.LEFT);
      setText((value == null) ? "" : value.toString());
    }
  }
}
