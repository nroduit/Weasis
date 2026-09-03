/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.pref;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.awt.Color;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.osgi.service.prefs.Preferences;
import org.weasis.core.ui.model.graphic.Graphic;
import org.weasis.core.ui.model.utils.MeasureFormat;

/** The former default yellow follows the new default once; a color the user chose is kept. */
@DisplayNameGeneration(ReplaceUnderscores.class)
class ViewSettingColorTest {

  private static Preferences stored(Integer color, Integer generation) {
    Preferences draw = mock(Preferences.class);
    when(draw.getInt(eq("color"), anyInt())) // NON-NLS
        .thenAnswer(inv -> color == null ? inv.getArgument(1) : color);
    when(draw.getInt(eq("colorGeneration"), anyInt())) // NON-NLS
        .thenAnswer(inv -> generation == null ? inv.getArgument(1) : generation);
    return draw;
  }

  @Test
  void decimals_are_automatic_unless_the_user_chose_a_number() {
    assertAll(
        () -> assertEquals(MeasureFormat.AUTO, ViewSetting.DEFAULT_DECIMALS),
        () -> assertEquals(MeasureFormat.AUTO, new ViewSetting().getDecimals()));
  }

  @Test
  void the_line_color_is_migrated_only_when_it_was_the_unchosen_former_default() {
    int yellow = Graphic.DEFAULT_COLOR.getRGB();
    int magenta = Color.MAGENTA.getRGB();
    assertAll(
        () ->
            assertEquals(
                ViewSetting.DEFAULT_LINE_COLOR, ViewSetting.storedLineColor(stored(null, null))),
        () ->
            assertEquals(
                ViewSetting.DEFAULT_LINE_COLOR, ViewSetting.storedLineColor(stored(yellow, null))),
        () -> assertEquals(Color.MAGENTA, ViewSetting.storedLineColor(stored(magenta, null))),
        () -> assertEquals(Color.YELLOW, ViewSetting.storedLineColor(stored(yellow, 2))));
  }
}
