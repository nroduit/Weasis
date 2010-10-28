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
package org.weasis.core.ui.graphic;

import java.awt.Color;
import java.awt.Font;

import org.weasis.core.api.util.FontTools;

public class InfoCorner {

    private transient Color color = Color.yellow;
    private transient Font font = FontTools.getFont12();

    private boolean annotations = true;
    private boolean imageOrientation = true;
    private boolean scale = true;
    private boolean lut = false;
    private boolean pixelValue = false;
    private boolean windowLevel = true;
    private boolean zoom = true;
    private boolean rotation = false;
    private boolean frame = true;

    public Color getColor() {
        return color;
    }

    public void setColor(Color color) {
        this.color = color;
    }

    public Font getFont() {
        return font;
    }

    public void setFont(Font font) {
        this.font = font;
    }

    public boolean isImageOrientation() {
        return imageOrientation;
    }

    public void setImageOrientation(boolean imageOrientation) {
        this.imageOrientation = imageOrientation;
    }

    public boolean isScale() {
        return scale;
    }

    public void setScale(boolean scale) {
        this.scale = scale;
    }

    public boolean isLut() {
        return lut;
    }

    public void setLut(boolean lut) {
        this.lut = lut;
    }

    public boolean isPixelValue() {
        return pixelValue;
    }

    public void setPixelValue(boolean pixelValue) {
        this.pixelValue = pixelValue;
    }

    public boolean isWindowLevel() {
        return windowLevel;
    }

    public void setWindowLevel(boolean windowLevel) {
        this.windowLevel = windowLevel;
    }

    public boolean isZoom() {
        return zoom;
    }

    public void setZoom(boolean zoom) {
        this.zoom = zoom;
    }

    public boolean isRotation() {
        return rotation;
    }

    public void setRotation(boolean rotation) {
        this.rotation = rotation;
    }

    public boolean isFrame() {
        return frame;
    }

    public void setFrame(boolean frame) {
        this.frame = frame;
    }

    public boolean isAnnotations() {
        return annotations;
    }

    public void setAnnotations(boolean annotations) {
        this.annotations = annotations;
    }

}
