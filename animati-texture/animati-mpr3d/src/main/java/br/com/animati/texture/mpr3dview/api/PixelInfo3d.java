/*
 * @copyright Copyright (c) 2012 Animati Sistemas de Informática Ltda.
 * (http://www.animati.com.br)
 */

package br.com.animati.texture.mpr3dview.api;

import javax.vecmath.Point3d;

import org.weasis.core.ui.editor.image.PixelInfo;

/**
 *
 * @author Gabriela Bauermann (gabriela@animati.com.br)
 * @version 2014, 04 Jul.
 */
public class PixelInfo3d extends PixelInfo {
    private Point3d position3d;

    public void setPosition3d(Point3d point) {
        position3d = point;
    }

    public Point3d getPosition3d() {
        return position3d;
    }

    @Override
    public String getPixelPositionText() {
        if (position3d == null) {
            return super.getPixelPositionText();
        } else {
            StringBuilder text = new StringBuilder("(");
            text.append(String.format("%.0f", position3d.x));
            text.append(",");
            text.append(String.format("%.0f", position3d.y));
            text.append(",");
            text.append(String.format("%.0f", position3d.z));
            text.append(")");
            return text.toString();
        }
    }

    @Override
    public String getPixelValueText() {
        // Not supported yet.
        return null;

    }

}
