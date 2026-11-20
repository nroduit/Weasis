/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.print;

import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.util.List;
import java.util.Set;
import org.weasis.core.api.util.FontItem;

/**
 * The font of text sent to a printer as text. A print job replaces a logical font (Dialog,
 * SansSerif) by a printer font while keeping the glyph positions of the screen font, which spaces
 * the letters unevenly; a real font is sent as it is drawn.
 */
public final class PrintFonts {

  private static final Set<String> LOGICAL_FAMILIES =
      Set.of(Font.DIALOG, Font.DIALOG_INPUT, Font.SANS_SERIF, Font.SERIF, Font.MONOSPACED);
  private static final List<String> FALLBACK_FAMILIES =
      List.of(
          "Noto Sans", // NON-NLS
          "DejaVu Sans", // NON-NLS
          "Liberation Sans", // NON-NLS
          "Segoe UI", // NON-NLS
          "Helvetica Neue", // NON-NLS
          "Arial"); // NON-NLS

  private PrintFonts() {}

  /**
   * The family of the application font when it names a real font, else the first common sans-serif
   * font installed, else the logical one.
   */
  public static String family() {
    String application = FontItem.DEFAULT.getFont().getFamily();
    return isLogical(application) ? Installed.FALLBACK : application;
  }

  /** The installed fonts are listed once, and only when a logical font has to be replaced. */
  private static final class Installed {
    static final String FALLBACK =
        family(
            null,
            Set.of(
                GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()));
  }

  static boolean isLogical(String family) {
    return family == null || LOGICAL_FAMILIES.contains(family);
  }

  /**
   * {@code font} as it is when it names a real font, else the same style and size in {@link
   * #family()}: for a text that keeps the font of the interface, such as a semibold one.
   */
  public static Font of(Font font) {
    return isLogical(font.getFamily())
        ? new Font(family(), font.getStyle(), 1).deriveFont(font.getSize2D())
        : font;
  }

  static String family(String application, Set<String> installed) {
    if (!isLogical(application)) {
      return application;
    }
    return FALLBACK_FAMILIES.stream()
        .filter(installed::contains)
        .findFirst()
        .orElse(Font.SANS_SERIF);
  }

  /** A font of {@link #family()} in this style and size. */
  public static Font font(int style, float size) {
    return new Font(family(), style, 1).deriveFont(size);
  }
}
