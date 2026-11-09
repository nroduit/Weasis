/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core;

import java.util.MissingResourceException;
import java.util.ResourceBundle;
import org.weasis.launcher.Translations;

public class Messages {
  private static final String BUNDLE_NAME = "org.weasis.core.messages"; // NON-NLS

  // Through the launcher: weasis-core can be on the application class path, which does not see the
  // translation packages
  private static final ResourceBundle RESOURCE_BUNDLE =
      Translations.getBundle(BUNDLE_NAME, Messages.class);

  private Messages() {}

  public static String getString(String key) {
    try {
      return RESOURCE_BUNDLE.getString(key);
    } catch (MissingResourceException e) {
      return '!' + key + '!';
    }
  }
}
