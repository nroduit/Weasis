/*
 * Copyright (c) 2025 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.pref;

import java.util.Locale;

public class JLocalePercentage extends JLocale {
  private final int percentage;

  public JLocalePercentage(Locale locale, int percentage) {
    super(locale);
    this.percentage = percentage;
  }

  public int getPercentage() {
    return percentage;
  }

  @Override
  public String toString() {
    return super.toString() + " (" + percentage + "%)";
  }
}
