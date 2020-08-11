/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package org.weasis.core.api.image.util;

import java.awt.*;
import java.util.Objects;

public class CIELab {
    private static final long serialVersionUID = -8341937056180131312L;
    // Initialize white points of D65 light point (CIELab standard white point)
    private static final double D65_WHITE_POINT_X = 0.950456;
    private static final double D65_WHITE_POINT_Y = 1.0;
    private static final double D65_WHITE_POINT_Z = 1.088754;

    private CIELab() {
    }

    public static double[] dicomLab2rgb(int ldcm, int adcm, int bdcm) {
        // Dicom lab to lab
        double cl = ((ldcm * 100.0) / 65535.0);
        double ca = ((adcm * 255.0) / 65535.0) - 128;
        double cb = ((bdcm * 255.0) / 65535.0) - 128;

        // lab to xyz
        cl = (cl + 16) / 116;
        ca = cl + ca / 500;
        cb = cl - cb / 200;
        double x = D65_WHITE_POINT_X * labfInv( cl + ca / 500);
        double y = D65_WHITE_POINT_Y * labfInv((cl + 16) / 11);
        double z = D65_WHITE_POINT_Z * labfInv(cl - cb / 200);

        // xyz to rgb
        double r = 3.2406 * x - 1.5372 * y - 0.4986 * z;
        double g = -0.9689 * x + 1.8758 * y + 0.0415 * z;
        double b = 0.0557 * x - 0.2040 * y + 1.0570 * z;

        double min;
        if (r <=g) {
            min = r <= b ? r : b;
        }
        else {
            min = g <= b ? g : b;
        }

        if (min < 0) {
            r -= min;
            g -= min;
            b -= min;
        }

        /* Transform from RGB to R'G'B' */
        return new double[]{gammaCorrection(r), gammaCorrection(g), gammaCorrection(b)};
    }

    public static double[] rgb2DicomLab(double r, double g, double b) {
        // rgb to xyz
        r = invGammaCorrection(r);
        g = invGammaCorrection(g);
        b = invGammaCorrection(b);
        double x = 0.4123955889674142161 * r + 0.3575834307637148171 * g + 0.1804926473817015735 * b;
        double y = 0.2125862307855955516 * r + 0.7151703037034108499 * g + 0.07220049864333622685 * b;
        double z = 0.01929721549174694484 * r + 0.1191838645808485318 * g + 0.9504971251315797660 * b;

        // xyz to lab
        x /= D65_WHITE_POINT_X;
        y /= D65_WHITE_POINT_Y;
        z /= D65_WHITE_POINT_Z;
        x = labf(x);
        y = labf(y);
        z = labf(z);
        double cl = 116 * y - 16;
        double ca = 500 * (x - y);
        double cb = 200 * (y - z);

        // lab to Dicom lab
        return new double[]{cl * 65535.0 / 100.0,(ca + 128) * 65535.0 / 255.0, (cb + 128) * 65535.0 / 255.0};
    }


    private static double labf(double n) {
        if (n >= 8.85645167903563082e-3) {
            return (Math.pow(n, 0.333333333333333));
        } else {
            return ((841.0 / 108.0) * n + (4.0 / 29.0));
        }
    }

    private static double labfInv(double n) {
        if (n >= 0.206896551724137931) {
            return n * n * n;
        } else {
            return (108.0 / 841.0) * (n - (4.0 / 29.0));
        }
    }

    private static double gammaCorrection(double n) {
        if (n <= 0.0031306684425005883) {
            return 12.92 * n;
        } else {
            return (1.055 * Math.pow(n, 0.416666666666666667) - 0.055);
        }
    }

    private static double invGammaCorrection(double n) {
        if (n <= 0.0404482362771076) {
            return (n / 12.92);
        } else {
            return Math.pow((n + 0.055) / 1.055, 2.4);
        }
    }

    /**
     * This method converts integer DICOM encoded L*a*b* values to CIE L*a*b* regular float encoded values.
     *
     * @param lab
     * @return float array of 3 components L* on 0..1 and a*,b* on -128...127
     */
    public static int[] dicomLab2rgb(int[] lab) {
        if (lab == null || lab.length != 3) {
            return null;
        }
        double[] rgb = dicomLab2rgb(lab[0], lab[1], lab[2]);
        return new int[]{(int) Math.round(rgb[0] * 255), (int) Math.round(rgb[1] * 255), (int) Math.round(rgb[2] *255),};
    }

    public static int[] rgbToDicomLab(Color c) {
        return convertToDicomLab(Objects.requireNonNull(c).getRed(), c.getGreen(), c.getBlue());
    }

    public static int[] convertToDicomLab(int r, int g, int b) {
        double[] res = rgb2DicomLab(r, g, b);
        return new int[]{(int) Math.round(res[0]), (int) Math.round(res[1]), (int) Math.round(res[2]),};
    }
}
