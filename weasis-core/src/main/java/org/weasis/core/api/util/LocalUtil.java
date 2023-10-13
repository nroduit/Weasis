/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.api.util;

import java.util.Locale;
import org.weasis.core.util.StringUtil;

public class LocalUtil {

  private LocalUtil() {}

  /**
   * Returns the IETF BCP 47 language tag string according the <code>Locale</code> value.
   *
   * @return the IETF BCP 47 language tag string
   */
  public static String localeToText(Locale value) {
    if (value == null) {
      return "en"; // NON-NLS
    }
    return value.toLanguageTag();
  }

  /**
   * Returns the <code>Locale</code> value according the IETF BCP 47 language tag or the suffix of
   * the i18n jars. Null or empty string will return the ENGLISH <code>Locale</code>. The value
   * "system " returns the system default <code>Locale</code>.
   *
   * @return the <code>Locale</code> value
   */
  public static Locale textToLocale(String value) {
    if (!StringUtil.hasText(value)) {
      return Locale.ENGLISH;
    }

    if (!"system".equals(value)) { // NON-NLS
      return Locale.forLanguageTag(value.replace("_", "-"));
    }
    return Locale.getDefault();
  }
}
