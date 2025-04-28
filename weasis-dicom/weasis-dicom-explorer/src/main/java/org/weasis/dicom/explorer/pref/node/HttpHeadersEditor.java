/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.explorer.pref.node;

import java.awt.Window;
import javax.swing.JOptionPane;
import org.weasis.core.api.gui.util.WinUtil;
import org.weasis.core.util.StringUtil;
import org.weasis.dicom.explorer.Messages;

public class HttpHeadersEditor extends AbstractListEditor<String> {
  private final DicomWebNode node;

  public HttpHeadersEditor(Window parent, DicomWebNode node) {
    super(parent, Messages.getString("DicomWebNodeDialog.httpHeaders"));
    this.node = node;
    initializeList();
    pack();
  }

  @Override
  protected void initializeList() {
    if (node.getHeaders().isEmpty()) {
      itemList.setListData(new String[0]);
    } else {
      itemList.setListData(
          node.getHeaders().entrySet().stream()
              .map(entry -> entry.getKey() + StringUtil.COLON_AND_SPACE + entry.getValue())
              .toArray(String[]::new));
    }
  }

  @Override
  protected void deleteItem(String item) {
    String[] kv = item.split(":", 2);
    if (kv.length == 2) {
      node.removeHeader(kv[0]);
    }
  }

  @Override
  protected void modifyItem(String input) {
    String property =
        (String)
            JOptionPane.showInputDialog(
                WinUtil.getValidComponent(this),
                Messages.getString("HttpHeadersEditor.msg_keyValue"),
                this.getTitle(),
                JOptionPane.PLAIN_MESSAGE,
                null,
                null,
                input);
    if (StringUtil.hasLength(property)) {
      String[] kv = property.split(":", 2);
      if (kv.length == 2) {
        node.addHeader(kv[0].trim(), kv[1].trim());
      }
      initializeList();
    }
  }
}
