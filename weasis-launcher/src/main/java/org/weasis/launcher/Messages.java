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

import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

/**
 * The strings of the launcher. The English {@code messages.properties} is in the launcher jar; the
 * other languages come from the {@code weasis-launcher-i18n} translation package, set by {@link
 * Translations#loadLauncher} before the first string is displayed.
 */
public class Messages {
  static final String BUNDLE_NAME = "org.weasis.launcher.messages"; // NON-NLS

  private static volatile ResourceBundle bundle;

  private Messages() {}

  /** Replaces the bundle, e.g. by the one of the translation package for the default locale. */
  static void setBundle(ResourceBundle resourceBundle) {
    bundle = resourceBundle;
  }

  private static ResourceBundle getBundle() {
    ResourceBundle b = bundle;
    if (b == null) {
      // Only the English base is in the launcher jar
      b = ResourceBundle.getBundle(BUNDLE_NAME, Locale.ROOT);
      bundle = b;
    }
    return b;
  }

  public static String getString(String key) {
    try {
      return getBundle().getString(key);
    } catch (MissingResourceException e) {
      return '!' + key + '!';
    }
  }
}
