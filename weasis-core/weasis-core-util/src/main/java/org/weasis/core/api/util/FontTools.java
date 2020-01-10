/*******************************************************************************
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.weasis.core.api.util;

import java.awt.Font;
import java.awt.Graphics;

public class FontTools {

    private static final Font font12 = new Font(Font.SANS_SERIF, 0, 12);
    private static final Font font12Bold = new Font(Font.SANS_SERIF, Font.BOLD, 12);
    private static final Font font11 = new Font(Font.SANS_SERIF, 0, 11);
    private static final Font font10 = new Font(Font.SANS_SERIF, 0, 10);
    private static final Font font9 = new Font(Font.SANS_SERIF, 0, 9);
    private static final Font font8 = new Font(Font.SANS_SERIF, 0, 8);

    private FontTools() {
    }

    public static Font getFont12() {
        return font12;
    }

    public static Font getFont12Bold() {
        return font12Bold;
    }

    public static Font getFont11() {
        return font11;
    }

    public static Font getFont10() {
        return font10;
    }

    public static Font getFont9() {
        return font9;
    }

    public static Font getFont8() {
        return font8;
    }

    public static float getAccurateFontHeight(Graphics g) {
        return (float) g.getFontMetrics().getStringBounds("0", g).getHeight(); //$NON-NLS-1$
    }

    public static float getMidFontHeightFactor() {
        return 0.35f;
    }
}
