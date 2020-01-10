/*******************************************************************************
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.weasis.core.api.image.util;

import java.awt.color.ColorSpace;

public class CIELab extends ColorSpace {
    private static final long serialVersionUID = -8341937056180131312L;

    private static final ColorSpace CIEXYZ = ColorSpace.getInstance(ColorSpace.CS_CIEXYZ);
    private static final double N = 4.0 / 29.0;

    private CIELab() {
        super(ColorSpace.TYPE_Lab, 3);
    }

    private static class Holder {
        static final CIELab INSTANCE = new CIELab();

        private Holder() {
        }
    }

    public static CIELab getInstance() {
        return Holder.INSTANCE;
    }

    @Override
    public float[] fromCIEXYZ(float[] colorvalue) {
        double l = f(colorvalue[1]);
        double L = 116.0 * l - 16.0;
        double a = 500.0 * (f(colorvalue[0]) - l);
        double b = 200.0 * (l - f(colorvalue[2]));
        return new float[] { (float) L, (float) a, (float) b };
    }

    @Override
    public float[] fromRGB(float[] rgbvalue) {
        float[] xyz = CIEXYZ.fromRGB(rgbvalue);
        return fromCIEXYZ(xyz);
    }

    @Override
    public float getMaxValue(int component) {
        return 128f;
    }

    @Override
    public float getMinValue(int component) {
        return (component == 0) ? 0f : -128f;
    }

    @Override
    public String getName(int idx) {
        return String.valueOf("Lab".charAt(idx)); //$NON-NLS-1$
    }

    @Override
    public float[] toCIEXYZ(float[] colorvalue) {
        double i = (colorvalue[0] + 16.0) * (1.0 / 116.0);
        double x = fInv(i + colorvalue[1] * (1.0 / 500.0));
        double y = fInv(i);
        double z = fInv(i - colorvalue[2] * (1.0 / 200.0));
        return new float[] { (float) x, (float) y, (float) z };
    }

    @Override
    public float[] toRGB(float[] colorvalue) {
        float[] xyz = toCIEXYZ(colorvalue);
        return CIEXYZ.toRGB(xyz);
    }

    private static double f(double x) {
        if (x > 216.0 / 24389.0) {
            return Math.cbrt(x);
        } else {
            return (841.0 / 108.0) * x + N;
        }
    }

    private static double fInv(double x) {
        if (x > 6.0 / 29.0) {
            return x * x * x;
        } else {
            return (108.0 / 841.0) * (x - N);
        }
    }

    /**
     * This method converts integer DICOM encoded L*a*b* values to CIE L*a*b* regular float encoded values.
     *
     * @param lab
     * @return float array of 3 components L* on 0..1 and a*,b* on -128...127
     */
    public static float[] convertToFloatLab(int[] lab) {
        if (lab == null || lab.length != 3) {
            return null;
        }
        float[] ret = new float[3];
        ret[0] = lab[0] / 655.35f;
        ret[1] = lab[1] / 257.0f - 128;
        ret[2] = lab[2] / 257.0f - 128;
        return ret;
    }

    public static int[] convertToDicomLab(float[] lab) {
        if (lab == null || lab.length != 3) {
            return null;
        }
        int[] ret = new int[3];
        ret[0] = (int) (lab[0] * 655.35f);
        ret[1] = (int) ((lab[1] + 128f) * 257.0f);
        ret[2] = (int) ((lab[2] + 128f) * 257.0f);
        return ret;
    }
}
