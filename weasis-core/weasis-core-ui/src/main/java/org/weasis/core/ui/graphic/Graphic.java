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

import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.beans.PropertyChangeListener;

import org.weasis.core.api.gui.util.GUIEntry;

/**
 * The Interface Graphic.
 * 
 * @author Nicolas Roduit
 */
public interface Graphic extends GUIEntry {

    public Area getArea();

    public String[] getLabel();

    public void setLabel(String[] label, Graphics2D g2d);

    public void setSelected(boolean flag);

    public boolean isSelected();

    public void paint(Graphics2D g2, AffineTransform transform);

    public void paintLabel(Graphics2D g2, AffineTransform transform);

    public void updateLabel(Object source, Graphics2D g2d);

    /**
     * The bound of the shape. Attention, this function does not return accurate bound for complex shape, use instead
     * getArea().
     * 
     * @return the bounding box of the shape
     */
    public Rectangle getBounds();

    public Rectangle getRepaintBounds();

    public GraphicLabel getGraphicLabel();

    public void showProperties();

    public boolean intersects(Rectangle rectangle);

    public boolean intersects(Rectangle rectangle, AffineTransform transform);

    public void addPropertyChangeListener(PropertyChangeListener propertychangelistener);

    public void removePropertyChangeListener(PropertyChangeListener propertychangelistener);

    public void fireRemoveAction();

    public Shape getShape();

    public Rectangle getTransformedBounds(Shape shape, AffineTransform affineTransform);

}
