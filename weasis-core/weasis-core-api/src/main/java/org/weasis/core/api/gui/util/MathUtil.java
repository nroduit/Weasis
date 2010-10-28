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
package org.weasis.core.api.gui.util;

import java.awt.geom.Point2D;

/**
 * The Class MathUtil.
 * 
 * @author Nicolas Roduit
 */
public class MathUtil {

    public static double computeDistanceFloat(double x1, double y1, double x2, double y2) {
        return Point2D.distance(x1, y1, x2, y2);
    }

    public static double getOrientation(double x1, double y1, double x2, double y2) {
        // utilise arctan2 pour lever l'ambiguité 180 - degrés
        double teta = Math.atan2(y1 - y2, x1 - x2);
        double angle = Math.toDegrees(teta); // convert from radians to degrees
        // conversion angle trigonométrique de 0 à 180 degrés
        if (angle < 0) {
            angle = -angle;
        } else {
            angle = 180 - angle;
        }
        return angle;
    }

    public static double getAzimuth(double x1, double y1, double x2, double y2) {
        double angle = Math.toDegrees(Math.atan2(y2 - y1, x2 - x1));
        angle = (angle + 450.0) % 360.0;
        return angle;
    }

    public static float checkMin0(float val) {
        return (val < 0) ? 0.0f : val;
    }

    public static float checkMax(float val, float max) {
        return (val > max) ? max : val;
    }

    public static float checkMinMax(float val, float min, float max) {
        if (val < min) {
            val = min;
        }
        if (val > max) {
            val = max;
        }
        return val;
    }

    public static int checkMinMax(int val, int min, int max) {
        if (val < min) {
            val = min;
        }
        if (val > max) {
            val = max;
        }
        return val;
    }
}
