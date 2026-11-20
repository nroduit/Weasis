/*
 * Copyright (c) 2022 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.launcher;

import com.formdev.flatlaf.FlatDarkLaf;

/**
 * The dark theme of Weasis: the FlatLaf dark theme with the colors of {@code
 * FlatWeasisTheme.properties}, which FlatLaf reads from the package of this class.
 */
public class FlatWeasisTheme extends FlatDarkLaf {

  public static final String NAME = "Weasis"; // NON-NLS

  public static boolean setup() {
    return setup(new FlatWeasisTheme());
  }

  @Override
  public String getName() {
    return NAME;
  }

  @Override
  public String getDescription() {
    return "Weasis dark theme"; // NON-NLS
  }
}
