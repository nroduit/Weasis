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

import com.formdev.flatlaf.IntelliJTheme;
import com.formdev.flatlaf.IntelliJTheme.ThemeLaf;
import com.formdev.flatlaf.util.LoggingFacade;
import java.io.IOException;

public class FlatWeasisTheme extends ThemeLaf {
  public static boolean setup() {
    return setup(new FlatWeasisTheme());
  }

  public FlatWeasisTheme() {
    super(loadTheme("Weasis.theme.json"));
  }

  @Override
  public String getName() {
    return "Weasis"; // NON-NLS
  }

  static IntelliJTheme loadTheme(String name) {
    try {
      return new IntelliJTheme(
          FlatWeasisTheme.class.getResourceAsStream("/org/weasis/theme/" + name));
    } catch (IOException var3) {
      String msg = "FlatLaf: Failed to load Weasis theme"; // NON-NLS
      LoggingFacade.INSTANCE.logSevere(msg, var3);
      throw new RuntimeException(msg, var3);
    }
  }
}
