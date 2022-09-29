/*
 * Copyright (c) 2022 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.api.util;

import java.awt.Font;
import javax.swing.UIManager;

public enum FontItem {
  H1_SEMIBOLD("h1.font", "H1 semibold"), // NON-NLS
  H1("h1.regular.font", "H1"), // NON-NLS
  H2_SEMIBOLD("h2.font", "H2 semibold"), // NON-NLS
  H2("h2.regular.font", "H2"), // NON-NLS
  H3_SEMIBOLD("h3.font", "H3 semibold"), // NON-NLS
  H3("h3.regular.font", "H3"), // NON-NLS
  LARGE("large.font", "Large"), // NON-NLS
  DEFAULT_BOLD("h4.font", "H4 (default bold)"), // NON-NLS
  DEFAULT_SEMIBOLD("semibold.font", "Default semibold"), // NON-NLS
  DEFAULT("defaultFont", "Default font"), // NON-NLS
  DEFAULT_LIGHT("light.font", "Default light font"), // NON-NLS
  MONOSPACED("monospaced.font", "Default monospaced font"), // NON-NLS
  MEDIUM_SEMIBOLD("medium.semibold.font", "Medium semibold"), // NON-NLS
  MEDIUM("medium.font", "Medium"), // NON-NLS
  SMALL_SEMIBOLD("small.semibold.font", "Small semibold"), // NON-NLS
  SMALL("small.font", "Small"), // NON-NLS
  MINI_SEMIBOLD("mini.semibold.font", "Mini semibold"), // NON-NLS
  MINI("mini.font", "Mini"), // NON-NLS
  MICRO_SEMIBOLD("micro.semibold.font", "Micro semibold"), // NON-NLS
  MICRO("micro.font", "Micro"); // NON-NLS

  private final String key;
  private final String name;

  FontItem(String key, String name) {
    this.key = key;
    this.name = name;
  }

  public String getKey() {
    return key;
  }

  public String getName() {
    return name;
  }

  public Font getFont() {
    return UIManager.getFont(key);
  }

  @Override
  public String toString() {
    return name;
  }

  public static FontItem getFontItem(String key) {
    for (FontItem item : FontItem.values()) {
      if (item.key.equals(key)) {
        return item;
      }
    }
    return DEFAULT;
  }
}
