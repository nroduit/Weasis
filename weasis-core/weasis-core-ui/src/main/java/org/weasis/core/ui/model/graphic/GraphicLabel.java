/*******************************************************************************
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.weasis.core.ui.model.graphic;

import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.geom.Rectangle2D;

import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

import org.weasis.core.api.util.Copyable;
import org.weasis.core.ui.editor.image.ViewCanvas;
import org.weasis.core.ui.model.utils.imp.DefaultGraphicLabel;

@XmlJavaTypeAdapter(DefaultGraphicLabel.Adapter.class)
public interface GraphicLabel extends Copyable<GraphicLabel> {
    /**
     * Minimum value is 3 because paintBoundOutline grows of 2 pixels the outer rectangle painting, and paintFontOutline
     * grows of 1 pixel all string painting
     */
    static final int GROWING_BOUND = 3;

    static final Double DEFAULT_OFFSET_X = 0d;
    static final Double DEFAULT_OFFSET_Y = 0d;

    void reset();

    /**
     * @return Label array of strings if defined
     */
    String[] getLabels();

    Rectangle2D getLabelBounds();

    /**
     * Should be used to check if mouse coordinates are inside/outside label bounding rectangle. Also useful to check
     * intersection with clipping rectangle.
     *
     * @param transform
     * @return Labels bounding rectangle in real world with size rescaled. It takes care of the current transformation
     *         scaling factor so labels painting have invariant size according to the given transformation.
     */
    Rectangle2D getBounds(AffineTransform transform);

    /**
     * @param transform
     * @return Real label bounding rectangle translated according to given transformation. <br>
     */
    Rectangle2D getTransformedBounds(AffineTransform transform);

    /**
     * Sets label strings and compute bounding rectangle size and position in pixel world according to the DefaultView
     * which defines current "Font"<br>
     */
    public void setLabel(ViewCanvas<?> view2d, Double xPos, Double yPos, String... labels);

    void paint(Graphics2D g2d, AffineTransform transform, boolean selected);

    Area getArea(AffineTransform transform);

    void move(Double deltaX, Double deltaY);

    Double getOffsetX();

    Double getOffsetY();
}
