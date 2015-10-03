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
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.util.List;

import javax.swing.Icon;

import org.weasis.core.api.image.util.MeasurableLayer;
import org.weasis.core.api.image.util.Unit;
import org.weasis.core.ui.util.MouseEventDouble;

/**
 * @author Nicolas Roduit
 */

public class NonEditableGraphic extends BasicGraphic {

    public NonEditableGraphic(Shape path, float lineThickness, Color paintColor, boolean labelVisible, boolean filled)
        throws IllegalStateException {
        super(0, paintColor, lineThickness, labelVisible, filled);
        setShape(path, null);
        updateLabel(null, null);
    }

    @Override
    protected void buildShape() {
        updateLabel(null, null);
    }

    @Override
    public Icon getIcon() {
        return null;
    }

    @Override
    public String getUIName() {
        return ""; //$NON-NLS-1$
    }

    @Override
    public List<MeasureItem> computeMeasurements(MeasurableLayer layer, boolean releaseEvent, Unit displayUnit) {
        return null;
    }

    @Override
    public List<Measurement> getMeasurementList() {
        return null;
    }

    @Override
    public boolean isOnGraphicLabel(MouseEventDouble mouseevent) {
        return false;
    }

    @Override
    public String getDescription() {
        return ""; //$NON-NLS-1$
    }

    @Override
    public Area getArea(AffineTransform transform) {
        return new Area();
    }

}
