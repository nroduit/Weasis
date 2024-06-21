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

import javax.swing.*;
import javax.swing.text.*;

public class LimitedTextField extends JTextField {
  public LimitedTextField(int limit) {
    super();
    setDocument(new LimitedDocument(limit));
  }

  private static class LimitedDocument extends PlainDocument {
    private final int limit;

    LimitedDocument(int limit) {
      this.limit = limit;
    }

    @Override
    public void insertString(int offset, String str, AttributeSet attr)
        throws BadLocationException {
      if (str == null) {
        return;
      }

      if ((getLength() + str.length()) <= limit) {
        super.insertString(offset, str, attr);
      }
    }
  }
}
