/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.model.graphic.imp;

import jakarta.xml.bind.annotation.XmlRootElement;
import java.awt.Shape;
import java.awt.Stroke;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.util.Objects;
import java.util.Optional;
import org.weasis.core.ui.model.graphic.AbstractGraphic;
import org.weasis.core.ui.model.graphic.Graphic;
import org.weasis.core.ui.model.utils.exceptions.InvalidShapeException;
import org.weasis.core.ui.util.MouseEventDouble;

/**
 * @author Nicolas Roduit
 */
@XmlRootElement(name = "nonEditable")
public class NonEditableGraphic extends AbstractGraphic {

  private Stroke stroke;

  public NonEditableGraphic(Shape path) {
    this(path, null);
  }

  public NonEditableGraphic(Shape path, Stroke stroke) {
    super(0);
    this.stroke = stroke;
    setShape(path, null);
    updateLabel(null, null);
  }

  public NonEditableGraphic(NonEditableGraphic graphic) {
    super(graphic);
  }

  @Override
  protected void initCopy(Graphic graphic) {
    super.initCopy(graphic);
    if (graphic instanceof NonEditableGraphic nonEditableGraphic) {
      this.stroke = nonEditableGraphic.stroke;
    }
  }

  @Override
  public NonEditableGraphic copy() {
    return new NonEditableGraphic(this);
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
}
