/*******************************************************************************
 * Copyright (c) 2010 Nicolas Roduit.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse  License v1.0
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
import java.util.List;

import org.weasis.core.api.gui.util.GUIEntry;
import org.weasis.core.api.image.util.ImageLayer;
import org.weasis.core.ui.editor.image.DefaultView2d;

/**
 * The Interface Graphic.
 * 
 * @author Nicolas Roduit , Benoit Jacquemoud
 */
public interface Graphic extends GUIEntry, Cloneable {

    Graphic deepCopy();

    Area getArea(AffineTransform transform);

    String[] getLabel();

    void setLabel(String[] label, DefaultView2d view2d);

    void setSelected(boolean flag);

    boolean isSelected();

    void paint(Graphics2D g2, AffineTransform transform);

    void paintLabel(Graphics2D g2, AffineTransform transform);

    void updateLabel(Object source, DefaultView2d view2d);

    Rectangle getBounds(AffineTransform transform);

    Rectangle getRepaintBounds(AffineTransform transform);

    GraphicLabel getGraphicLabel();

    boolean isLabelVisible();

    boolean intersects(Rectangle rectangle, AffineTransform transform);

    void addPropertyChangeListener(PropertyChangeListener propertychangelistener);

    void removePropertyChangeListener(PropertyChangeListener propertychangelistener);

    void fireRemoveAction();

    void toFront();

    void toBack();

    void setLayerID(int layerID);

    int getLayerID();

    Shape getShape();

    Rectangle getTransformedBounds(Shape shape, AffineTransform transform);

    Rectangle getTransformedBounds(GraphicLabel label, AffineTransform transform);

    List<MeasureItem> computeMeasurements(ImageLayer layer, boolean releaseEvent);

    List<Measurement> getMeasurementList();

}
