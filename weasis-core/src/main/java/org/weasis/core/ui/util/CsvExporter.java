/*
 * Copyright (c) 2024 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.util;

import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import org.weasis.core.util.StringUtil;

public class CsvExporter {
  public static final char separator = ',';
  public static final char quote = '"';
  private final StringBuilder sb;

  public CsvExporter() {
    this.sb = new StringBuilder();
  }

  public void addQuotedNameAndSeparator(String name) {
    addQuotedName(name);
    addSeparator();
  }

  public void addQuotedName(String name) {
    if (StringUtil.hasText(name)) {
      sb.append(quote);
      sb.append(name);
      sb.append(quote);
    }
  }

  public void adName(String name) {
    if (StringUtil.hasText(name)) {
      sb.append(name);
    }
  }

  public StringBuilder getBuilder() {
    return sb;
  }

  public void addSeparator() {
    sb.append(separator);
  }

  public void addEndOfLine() {
    sb.append(System.lineSeparator());
  }

  public void copyToClipboard() {
    StringSelection stringSelection = new StringSelection(sb.toString());
    Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
    clipboard.setContents(stringSelection, null);
  }
}
