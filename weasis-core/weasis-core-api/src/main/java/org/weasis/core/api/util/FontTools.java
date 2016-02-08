/*******************************************************************************
 * Copyright (c) 2010 Nicolas Roduit.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Nicolas Roduit - initial API and implementation
 ******************************************************************************/
package org.weasis.core.api.util;

import java.awt.Font;
import java.awt.Graphics;
import java.util.Enumeration;

import javax.swing.JLabel;
import javax.swing.JSlider;

public class FontTools {

    private static final String DEFAULT_FAMILY = "Dialog"; //$NON-NLS-1$

    private static final Font font12 = new Font(DEFAULT_FAMILY, 0, 12);
    private static final Font font12Bold = new Font(DEFAULT_FAMILY, Font.BOLD, 12);
    private static final Font font11 = new Font(DEFAULT_FAMILY, 0, 11);
    private static final Font font10 = new Font(DEFAULT_FAMILY, 0, 10);
    private static final Font font9 = new Font(DEFAULT_FAMILY, 0, 9);
    private static final Font font8 = new Font(DEFAULT_FAMILY, 0, 8);

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

    public static void setFont10(JSlider jslider) {
        Enumeration<?> enumVal = jslider.getLabelTable().elements();
        while (enumVal.hasMoreElements()) {
            Object el = enumVal.nextElement();
            if (el instanceof JLabel) {
                ((JLabel) el).setFont(font10);
            }
        }
    }

    public static void setFont8(JSlider jslider) {
        Enumeration<?> enumVal = jslider.getLabelTable().elements();
        while (enumVal.hasMoreElements()) {
            Object el = enumVal.nextElement();
            if (el instanceof JLabel) {
                ((JLabel) el).setFont(font8);
            }
        }
    }

    public static float getAccurateFontHeight(Graphics g) {
        return (float) g.getFontMetrics().getStringBounds("0", g).getHeight(); //$NON-NLS-1$
    }

    public static float getMidFontHeightFactor() {
        return 0.35f;
    }
}
