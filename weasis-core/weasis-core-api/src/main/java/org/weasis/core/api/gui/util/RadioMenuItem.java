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

import javax.swing.Icon;
import javax.swing.JRadioButtonMenuItem;

public class RadioMenuItem extends JRadioButtonMenuItem {

  private final Object userObject;

  public RadioMenuItem(String text, Object userObject) {
    this(text, null, userObject);
  }

  public RadioMenuItem(String text, Icon icon, Object userObject) {
    this(text, icon, userObject, false);
  }

  public RadioMenuItem(String text, Icon icon, Object userObject, boolean selected) {
    super(text, icon, selected);
    this.userObject = userObject;
  }

  public Object getUserObject() {
    return userObject;
  }
}
