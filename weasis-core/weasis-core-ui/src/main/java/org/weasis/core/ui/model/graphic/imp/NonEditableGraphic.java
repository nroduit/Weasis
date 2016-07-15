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
package org.weasis.core.ui.model.graphic.imp;

import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;

import javax.xml.bind.annotation.XmlRootElement;

import org.weasis.core.ui.model.graphic.AbstractGraphic;
import org.weasis.core.ui.model.utils.exceptions.InvalidShapeException;
import org.weasis.core.ui.util.MouseEventDouble;

/**
 * @author Nicolas Roduit
 */
@XmlRootElement(name = "nonEditable")
public class NonEditableGraphic extends AbstractGraphic {
    private static final long serialVersionUID = -6063521725986473663L;
    public static final Integer POINTS_NUMBER = 0;
    
    public NonEditableGraphic() {
        super(POINTS_NUMBER);
    }

    // TODO should be removed
    public NonEditableGraphic(Shape path) {
        this();
        setShape(path, null);
        updateLabel(null, null);
    }

    public NonEditableGraphic(NonEditableGraphic graphic) {
        super(graphic);
    }

    @Override
    public NonEditableGraphic copy() {
        return new NonEditableGraphic(this);
    }

    @Override
    protected void prepareShape() throws InvalidShapeException {
        if (!isShapeValid()) {
            throw new InvalidShapeException("This shape cannot be drawn"); //$NON-NLS-1$
        }
        buildShape();
    }

    @Override
    public void buildShape() {
        updateLabel(null, null);
    }

    @Override
    public String getUIName() {
        return "";
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
