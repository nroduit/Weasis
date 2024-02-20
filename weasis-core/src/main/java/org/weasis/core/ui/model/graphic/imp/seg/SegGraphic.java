/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.model.graphic.imp.seg;

import java.awt.Shape;
import java.awt.Stroke;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.weasis.core.api.image.util.MeasurableLayer;
import org.weasis.core.ui.model.graphic.AbstractGraphic;
import org.weasis.core.ui.model.graphic.Graphic;
import org.weasis.core.ui.model.graphic.GraphicArea;
import org.weasis.core.ui.model.utils.bean.MeasureItem;
import org.weasis.core.ui.model.utils.exceptions.InvalidShapeException;
import org.weasis.core.ui.util.MouseEventDouble;

public class SegGraphic extends AbstractGraphic implements GraphicArea {

  private Stroke stroke;

  public SegGraphic(Shape path) {
    this(path, null);
  }

  public SegGraphic(Shape path, Stroke stroke) {
    super(0);
    this.stroke = stroke;
    setShape(path, null);
    updateLabel(null, null);
  }

  public SegGraphic(SegGraphic graphic) {
    super(graphic);
  }

  @Override
  protected void initCopy(Graphic graphic) {
    super.initCopy(graphic);
    if (graphic instanceof SegGraphic SegGraphic) {
      this.stroke = SegGraphic.stroke;
    }
  }

  @Override
  public SegGraphic copy() {
    return new SegGraphic(this);
  }

  @Override
  public void setFilled(Boolean filled) {
    if (!Objects.equals(this.filled, filled)) {
      this.filled = Optional.ofNullable(filled).orElse(DEFAULT_FILLED);
      fireDrawingChanged();
    }
  }

  @Override
  protected void prepareShape() throws InvalidShapeException {
    if (!isShapeValid()) {
      throw new InvalidShapeException("This shape cannot be drawn");
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
    return getUIName();
  }

  @Override
  public Area getArea(AffineTransform transform) {
    return new Area();
  }

  public Stroke getStroke() {
    return stroke;
  }

  public void setStroke(Stroke stroke) {
    this.stroke = stroke;
  }

  @Override
  public Stroke getStroke(Float lineThickness) {
    if (stroke != null) {
      return stroke;
    }
    return super.getStroke(lineThickness);
  }

  @Override
  public List<MeasureItem> getImageStatistics(MeasurableLayer layer, Boolean releaseEvent) {
    return null;
  }
}
