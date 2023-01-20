/*
 * Copyright (c) 2023 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.editor.image;

import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import org.weasis.core.api.gui.util.GeomUtil;
import org.weasis.core.ui.model.GraphicModel;
import org.weasis.core.ui.model.graphic.Graphic;
import org.weasis.core.ui.model.utils.imp.DefaultGraphicLabel;

public class PropertyChangeHandler implements PropertyChangeListener {
  private final Canvas canvas;

  public PropertyChangeHandler(Canvas canvas) {
    this.canvas = Objects.requireNonNull(canvas);
  }

  // This method is called when a property is changed (fired from a graphic)
  @Override
  public void propertyChange(PropertyChangeEvent propertychangeevent) {
    Object obj = propertychangeevent.getSource();
    String s = propertychangeevent.getPropertyName();
    if (obj instanceof Graphic graph) {
      if ("bounds".equals(s)) {
        graphicBoundsChanged(
            graph,
            (Shape) propertychangeevent.getOldValue(),
            (Shape) propertychangeevent.getNewValue(),
            canvas.getAffineTransform());
      } else if ("graphicLabel".equals(s)) {
        labelBoundsChanged(
            graph,
            (DefaultGraphicLabel) propertychangeevent.getOldValue(),
            (DefaultGraphicLabel) propertychangeevent.getNewValue(),
            canvas.getAffineTransform());
      } else if ("remove".equals(s)) {
        removeGraphic(graph);
      } else if ("remove.repaint".equals(s)) {
        removeGraphicAndRepaint(graph);
      } else if ("toFront".equals(s)) {
        toFront(graph);
      } else if ("toBack".equals(s)) {
        toBack(graph);
      }
    }
  }

  public void toFront(Graphic graphic) {
    GraphicModel graphicManager = canvas.getGraphicManager();
    List<Graphic> list = graphicManager.getModels();
    synchronized (list) {
      for (int i = 0; i < list.size(); i++) {
        if (list.get(i).equals(graphic)) {
          Collections.rotate(list.subList(i, list.size()), -1);
          break;
        }
      }
    }
    canvas.getJComponent().repaint();
  }

  public void toBack(Graphic graphic) {
    GraphicModel graphicManager = canvas.getGraphicManager();
    List<Graphic> list = graphicManager.getModels();
    synchronized (list) {
      for (int i = 0; i < list.size(); i++) {
        if (list.get(i).equals(graphic)) {
          Collections.rotate(list.subList(0, i + 1), 1);
          break;
        }
      }
    }
    canvas.getJComponent().repaint();
  }

  public void removeGraphicAndRepaint(Graphic graphic) {
    removeGraphic(graphic);
    GraphicsPane.repaint(
        canvas, graphic.getTransformedBounds(graphic.getShape(), canvas.getAffineTransform()));
  }

  public void removeGraphic(Graphic graphic) {
    GraphicModel graphicManager = canvas.getGraphicManager();
    if (graphicManager != null) {
      graphicManager.removeGraphic(graphic);
    }
    graphic.removePropertyChangeListener(this);
  }

  protected Rectangle rectangleUnion(Rectangle rectangle, Rectangle rectangle1) {
    if (rectangle == null) {
      return rectangle1;
    }
    return rectangle1 == null ? rectangle : rectangle.union(rectangle1);
  }

  protected void graphicBoundsChanged(
      Graphic graphic, Shape oldShape, Shape shape, AffineTransform transform) {
    if (graphic != null) {
      if (oldShape == null) {
        if (shape != null) {
          Rectangle rect = graphic.getTransformedBounds(shape, transform);
          GraphicsPane.repaint(canvas, rect);
        }
      } else {
        if (shape == null) {
          Rectangle rect = graphic.getTransformedBounds(oldShape, transform);
          GraphicsPane.repaint(canvas, rect);
        } else {
          Rectangle rect =
              rectangleUnion(
                  graphic.getTransformedBounds(oldShape, transform),
                  graphic.getTransformedBounds(shape, transform));
          GraphicsPane.repaint(canvas, rect);
        }
      }
    }
  }

  protected void labelBoundsChanged(
      Graphic graphic,
      DefaultGraphicLabel oldLabel,
      DefaultGraphicLabel newLabel,
      AffineTransform transform) {

    if (graphic != null) {
      boolean oldNull = oldLabel == null || oldLabel.getLabels() == null;
      boolean newNull = newLabel == null || newLabel.getLabels() == null;
      if (oldNull) {
        if (!newNull) {
          Rectangle2D rect = graphic.getTransformedBounds(newLabel, transform);
          GeomUtil.growRectangle(rect, 2);
          GraphicsPane.repaint(canvas, rect.getBounds());
        }
      } else {
        if (newNull) {
          Rectangle2D rect = graphic.getTransformedBounds(oldLabel, transform);
          GeomUtil.growRectangle(rect, 2);
          GraphicsPane.repaint(canvas, rect.getBounds());
        } else {
          Rectangle2D newRect = graphic.getTransformedBounds(newLabel, transform);
          GeomUtil.growRectangle(newRect, 2);

          Rectangle2D oldRect = graphic.getTransformedBounds(oldLabel, transform);
          GeomUtil.growRectangle(oldRect, 2);

          Rectangle rect = rectangleUnion(oldRect.getBounds(), newRect.getBounds());
          GraphicsPane.repaint(canvas, rect);
        }
      }
    }
  }
}
