/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.base.ui;

import java.util.ResourceBundle;
import org.weasis.core.api.util.Bundles;

/** The strings of this module; see {@link Bundles} for how a translation is found. */
public class Messages {
  private static final String BUNDLE_NAME = "org.weasis.base.ui.messages"; // NON-NLS

  private static final ResourceBundle RESOURCE_BUNDLE = Bundles.of(BUNDLE_NAME, Messages.class);

  private Messages() {}

  /** The string of a key, or {@code !key!} when the bundle has no such key. */
  public static String getString(String key) {
    return Bundles.get(RESOURCE_BUNDLE, key);
  }

  /** The string of a key with its {@code {0}} arguments filled by {@code MessageFormat}. */
  public static String format(String key, Object... args) {
    return Bundles.format(RESOURCE_BUNDLE, key, args);
  }

  /** A string of the common section of weasis-core, e.g. {@code common("cancel")}. */
  public static String common(String key) {
    return Bundles.common(key);
  }
}
