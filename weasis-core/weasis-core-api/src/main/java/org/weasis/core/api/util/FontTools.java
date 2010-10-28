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

import org.weasis.core.api.Messages;

public class FontTools {

    private final static String defaultFamily = "Dialog"; //$NON-NLS-1$
    private final static Font font12 = new Font(defaultFamily, 0, 12);
    private final static Font font12Bold = new Font(defaultFamily, Font.BOLD, 12);
    private final static Font font11 = new Font(defaultFamily, 0, 11);
    private final static Font font10 = new Font(defaultFamily, 0, 10);
    private final static Font font9 = new Font(defaultFamily, 0, 9);
    private final static Font font8 = new Font(defaultFamily, 0, 8);

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
        Enumeration enumVal = jslider.getLabelTable().elements();
        while (enumVal.hasMoreElements()) {
            ((JLabel) enumVal.nextElement()).setFont(font10);
        }
    }

    public static void setFont8(JSlider jslider) {
        Enumeration enumVal = jslider.getLabelTable().elements();
        while (enumVal.hasMoreElements()) {
            ((JLabel) enumVal.nextElement()).setFont(font8);
        }
    }

    public static float getAccurateFontHeight(Graphics g) {
        return (float) g.getFontMetrics().getStringBounds("0", g).getHeight(); //$NON-NLS-1$
    }

    public static float getMidFontHeightFactor() {
        return 0.35f;
    }
}
