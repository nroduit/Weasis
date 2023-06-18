/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.launcher;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.MessageFormat;
import java.util.Date;
import java.util.logging.Formatter;
import java.util.logging.LogRecord;

public class ConsoleFormatter extends Formatter {

  private static final MessageFormat messageFormat =
      new MessageFormat(
          System.getProperty(
                  "org.apache.sling.commons.log.pattern",
                  "{0,date,dd.MM.yyyy HH:mm:ss.SSS} *{4}* [{2}] {3}: {5}") // NON-NLS
              + "\n"); // NON-NLS

  public ConsoleFormatter() {
    super();
  }

  @Override
  public String format(LogRecord logRecord) {
    String format = logRecord.getMessage();

    java.util.ResourceBundle catalog = logRecord.getResourceBundle();
    if (catalog != null) {
      try {
        format = catalog.getString(format);
      } catch (java.util.MissingResourceException ex) {
        // Drop through.  Use record message as format
      }
    }

    // Do the formatting.
    try {
      Object[] parameters = logRecord.getParameters();
      if (parameters == null || parameters.length == 0) {
        // No parameters.  Just return format string.
        return getFinalFormat(logRecord, format);
      }
      // Is it a java.text style format?
      // Ideally we could match with
      // Pattern.compile("\\{\\d").matcher(format).find())
      // However the cost is 14% higher, so we cheaply use indexOf
      // and charAt to look for that pattern.
      int index = -1;
      int fence = format.length() - 1;
      while ((index = format.indexOf('{', index + 1)) > -1) {
        if (index >= fence) break;
        char digit = format.charAt(index + 1);
        if (digit >= '0' && digit <= '9') {
          return getFinalFormat(logRecord, MessageFormat.format(format, parameters));
        }
      }
      return getFinalFormat(logRecord, format);

    } catch (Exception ex) {
      // Formatting failed: use localized format string.
      return getFinalFormat(logRecord, format);
    }
  }

  private static String getFinalFormat(LogRecord logRecord, String message) {
    Object[] arguments = new Object[6];
    arguments[0] = new Date(logRecord.getMillis());
    arguments[2] = Thread.currentThread().getName();
    arguments[3] = logRecord.getLoggerName();
    arguments[4] = logRecord.getLevel();
    arguments[5] = message;

    if (logRecord.getThrown() != null) {
      StringWriter sw = new StringWriter();
      PrintWriter pw = new PrintWriter(sw);
      pw.print(messageFormat.format(arguments));
      logRecord.getThrown().printStackTrace(pw);
      pw.close();
      return sw.toString();
    }

    return messageFormat.format(arguments);
  }
}
