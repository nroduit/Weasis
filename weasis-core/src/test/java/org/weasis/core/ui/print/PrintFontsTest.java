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

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.awt.Font;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PrintFontsTest {

  @Test
  void a_real_application_font_is_kept() {
    assertEquals("Inter", PrintFonts.family("Inter", Set.of("Arial"))); // NON-NLS
  }

  @Test
  void a_logical_font_keeps_its_style_and_size_in_the_print_family() {
    Font printed = PrintFonts.of(new Font(Font.DIALOG, Font.BOLD, 13));
    assertEquals(Font.BOLD, printed.getStyle());
    assertEquals(13f, printed.getSize2D());
    assertEquals(new Font(PrintFonts.family(), Font.PLAIN, 1).getFamily(), printed.getFamily());
  }

  @Test
  void a_logical_font_gives_way_to_an_installed_one() {
    assertEquals(
        "DejaVu Sans", // NON-NLS
        PrintFonts.family(Font.DIALOG, Set.of("Arial", "DejaVu Sans"))); // NON-NLS
    assertEquals(Font.SANS_SERIF, PrintFonts.family(Font.SANS_SERIF, Set.of()));
  }
}
