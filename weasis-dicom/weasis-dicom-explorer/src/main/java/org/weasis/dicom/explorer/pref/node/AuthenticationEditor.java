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
import java.util.ArrayList;
import java.util.List;
import javax.swing.JComboBox;
import org.weasis.core.api.auth.AuthMethod;
import org.weasis.core.api.auth.OAuth2ServiceFactory;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.dicom.explorer.Messages;

public class AuthenticationEditor extends AbstractListEditor<AuthMethod> {

  private final JComboBox<AuthMethod> comboBox;

  public AuthenticationEditor(Window parent, JComboBox<AuthMethod> comboBox) {
    super(parent, Messages.getString("authentication.manager"));
    this.comboBox = comboBox;
    initializeList();
    pack();
  }

  @Override
  protected void initializeList() {
    List<AuthMethod> list = new ArrayList<>();
    for (int i = 0; i < comboBox.getItemCount(); i++) {
      AuthMethod auth = comboBox.getItemAt(i);
      if (!OAuth2ServiceFactory.noAuth.equals(auth)) {
        list.add(auth);
      }
    }
    itemList.setListData(list.toArray(new AuthMethod[0]));
  }

  @Override
  protected void deleteItem(AuthMethod item) {
    comboBox.removeItem(item);
  }

  @Override
  protected void modifyItem(AuthMethod input) {
    AuthMethodDialog dialog =
        new AuthMethodDialog(this, Messages.getString("auth.method"), input, comboBox);
    GuiUtils.showCenterScreen(dialog);
    if (input == null) {
      initializeList();
    }
  }
}
