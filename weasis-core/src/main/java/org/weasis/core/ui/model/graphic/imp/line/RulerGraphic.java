/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.model.graphic.imp.line;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.XmlType;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.util.Collections;
import java.util.List;
import javax.swing.Icon;
import org.weasis.core.Messages;
import org.weasis.core.api.gui.util.GeomUtil;
import org.weasis.core.api.image.util.MeasurableLayer;
import org.weasis.core.api.image.util.Unit;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.api.util.ResourceUtil;
import org.weasis.core.api.util.ResourceUtil.ActionIcon;
import org.weasis.core.ui.editor.image.ViewCanvas;
import org.weasis.core.ui.model.graphic.Graphic;
import org.weasis.core.ui.model.utils.bean.AdvancedShape;
import org.weasis.core.ui.model.utils.bean.MeasureItem;
import org.weasis.core.ui.model.utils.bean.Measurement;
import org.weasis.core.ui.util.MouseEventDouble;

/**
 * A graduated bar: one tick per centimetre on a calibrated image (every fifth tick longer), one per
 * fifty pixels otherwise. The tick spacing is fixed when the bar is drawn and stored with it.
 */
@XmlType(name = "ruler")
@XmlRootElement(name = "ruler")
@XmlAccessorType(XmlAccessType.NONE)
public class RulerGraphic extends LineGraphic {

  public static final Icon ICON = ResourceUtil.getIcon(ActionIcon.DRAW_RULER);
  static final double DEFAULT_TICK_PIXELS = 50;
  static final double TICK_MM = 10;

  private Double tickSpacing;

  public RulerGraphic() {
    super();
  }

  public RulerGraphic(RulerGraphic graphic) { // NOSONAR see initCopy()
    super(graphic);
  }

  @Override
  protected void initCopy(Graphic graphic) {
    super.initCopy(graphic);
    if (graphic instanceof RulerGraphic ruler) {
      tickSpacing = ruler.tickSpacing;
    }
  }

  @Override
  public RulerGraphic copy() {
    return new RulerGraphic(this);
  }

  @Override
  public Icon getIcon() {
    return ICON;
  }

  @Override
  public String getUIName() {
    return Messages.getString("Tools.ruler");
  }

  /** Distance between ticks in image pixels. */
  @XmlAttribute(name = "tick")
  public Double getTickSpacing() {
    return tickSpacing;
  }

  public void setTickSpacing(Double tickSpacing) {
    this.tickSpacing = tickSpacing;
  }

  /** Pixels per centimetre for a calibrated image, else the default pixel spacing. */
  static double tickSpacingOf(ImageElement image) {
    if (image == null) {
      return DEFAULT_TICK_PIXELS;
    }
    Unit unit = image.getPixelSpacingUnit();
    double pixelSize = image.getPixelSize();
    if (unit == null || unit == Unit.PIXEL || pixelSize <= 0) {
      return DEFAULT_TICK_PIXELS;
    }
    double mmPerPixel = pixelSize * unit.getFactorToMeters() * 1000;
    double tick = TICK_MM / mmPerPixel;
    return tick < 4 ? tick * 10 : tick;
  }

  @Override
  public void buildShape(MouseEventDouble mouseEvent) {
    updateTool();
    ViewCanvas<?> view = getDefaultView2d(mouseEvent);
    if (view != null) {
      tickSpacing = tickSpacingOf(view.getImage());
    }
    AdvancedShape newShape = null;
    if (lineABvalid) {
      newShape = new AdvancedShape(this, 2);
      newShape.addShape(new Line2D.Double(ptA, ptB));
      newShape.addShape(ticks(), getStroke(lineThickness), true);
    }
    setShape(newShape, mouseEvent);
    updateLabel(mouseEvent, getDefaultView2d(mouseEvent));
  }

  private Path2D ticks() {
    double tick = tickSpacing == null ? DEFAULT_TICK_PIXELS : tickSpacing;
    double length = ptA.distance(ptB);
    double minor = Math.clamp(tick * 0.3, 3, 10);
    double major = Math.clamp(tick * 0.5, 4, 16);
    Path2D path = new Path2D.Double();
    for (int k = 0; k * tick <= length; k++) {
      Point2D p = GeomUtil.getColinearPointWithLength(ptA, ptB, k * tick);
      double half = (k % 5 == 0 ? major : minor) / 2;
      Point2D up = GeomUtil.getPerpendicularPointFromLine(ptA, ptB, p, half);
      Point2D down = GeomUtil.getPerpendicularPointFromLine(ptA, ptB, p, -half);
      if (up != null && down != null) {
        path.append(new Line2D.Double(up, down), false);
      }
    }
    return path;
  }

  @Override
  public List<MeasureItem> computeMeasurements(
      MeasurableLayer layer, boolean releaseEvent, Unit displayUnit) {
    return Collections.emptyList();
  }

  @Override
  public List<Measurement> getMeasurementList() {
    return Collections.emptyList();
  }
}
