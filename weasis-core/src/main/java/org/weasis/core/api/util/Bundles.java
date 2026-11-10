/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.api.util;

import java.text.MessageFormat;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.launcher.PseudoLocale;
import org.weasis.launcher.UiOrientation;

/**
 * What the {@code Messages} class of every module delegates to: the bundle of the module for the
 * default locale, the lookup with the {@code !key!} fallback, the {@link MessageFormat} filling of
 * a string with arguments, and the common section of weasis-core.
 *
 * <p>A translation reaches a plugin through its class loader: the translation packages are
 * published on the shared library loader, so {@link ResourceBundle#getBundle} finds {@code
 * messages_<lang>.properties} by parent delegation. The pseudo-locales {@code en-XA} and {@code
 * ar-XB} are derived from the English base.
 */
public final class Bundles {
  private static final Logger LOGGER = LoggerFactory.getLogger(Bundles.class);

  /** The prefix of the keys of the common section in the weasis-core bundle. */
  public static final String COMMON_PREFIX = "common."; // NON-NLS

  private Bundles() {}

  /**
   * The bundle of a module for the default locale.
   *
   * @param baseName the base name of the bundle, e.g. {@code org.weasis.dicom.viewer2d.messages}
   * @param owner a class of the module, whose class loader sees the English base and the packages
   */
  public static ResourceBundle of(String baseName, Class<?> owner) {
    Locale locale = Locale.getDefault();
    ClassLoader loader = owner.getClassLoader();
    if (PseudoLocale.isPseudo(locale)) {
      return PseudoLocale.wrap(ResourceBundle.getBundle(baseName, Locale.ROOT, loader), locale);
    }
    return ResourceBundle.getBundle(baseName, locale, loader);
  }

  /** The string of a key, or {@code !key!} when the bundle has no such key. */
  public static String get(ResourceBundle bundle, String key) {
    try {
      return bundle.getString(key);
    } catch (MissingResourceException e) {
      return '!' + key + '!';
    }
  }

  /**
   * The string of a key with its {@code {0}} arguments filled by {@link MessageFormat} in the
   * default locale; when the interface is right to left, each argument is wrapped in Unicode
   * isolates so that a value of the other direction keeps its order ({@link UiOrientation#format}).
   * A pattern that cannot be parsed (an unbalanced brace, a lone apostrophe) is returned as is and
   * logged once per call, so that a translation error never breaks the view.
   */
  public static String format(ResourceBundle bundle, String key, Object... args) {
    return format(get(bundle, key), args);
  }

  /** {@link #format(ResourceBundle, String, Object...)} on a pattern already resolved. */
  public static String format(String pattern, Object... args) {
    try {
      return UiOrientation.format(new MessageFormat(pattern, Locale.getDefault()), args);
    } catch (IllegalArgumentException e) {
      LOGGER.warn("Invalid message pattern \"{}\": {}", pattern, e.getMessage());
      return pattern;
    }
  }

  /**
   * A string of the common section of weasis-core: the words every module shares (Cancel, Close,
   * Delete, Series...), translated once. The key is given without the {@value #COMMON_PREFIX}
   * prefix.
   */
  public static String common(String key) {
    return org.weasis.core.Messages.getString(COMMON_PREFIX + key);
  }
}
