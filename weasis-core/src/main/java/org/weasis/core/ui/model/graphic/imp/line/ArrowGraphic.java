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
import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.XmlType;
import java.awt.Shape;
import java.awt.geom.Line2D;
import java.util.Collections;
import java.util.List;
import javax.swing.Icon;
import org.weasis.core.Messages;
import org.weasis.core.api.gui.util.GeomUtil;
import org.weasis.core.api.image.util.MeasurableLayer;
import org.weasis.core.api.image.util.Unit;
import org.weasis.core.api.util.ResourceUtil;
import org.weasis.core.api.util.ResourceUtil.ActionIcon;
import org.weasis.core.ui.model.utils.bean.AdvancedShape;
import org.weasis.core.ui.model.utils.bean.MeasureItem;
import org.weasis.core.ui.model.utils.bean.Measurement;
import org.weasis.core.ui.util.MouseEventDouble;

/** A segment with a filled head at its first point, the one placed on what it points at. */
@XmlType(name = "arrow")
@XmlRootElement(name = "arrow")
@XmlAccessorType(XmlAccessType.NONE)
public class ArrowGraphic extends LineGraphic {

  public static final Icon ICON = ResourceUtil.getIcon(ActionIcon.DRAW_ARROW);
  private static final double HEAD_LENGTH = 15;
  private static final double HEAD_WIDTH = 8;

  public ArrowGraphic() {
    super();
  }

  public ArrowGraphic(ArrowGraphic graphic) {
    super(graphic);
  }

  @Override
  public ArrowGraphic copy() {
    return new ArrowGraphic(this);
  }

  @Override
  public Icon getIcon() {
    return ICON;
  }

  @Override
  public String getUIName() {
    return Messages.getString("Tools.arrow");
  }

  @Override
  public void buildShape(MouseEventDouble mouseEvent) {
    updateTool();
    AdvancedShape newShape = null;
    if (lineABvalid) {
      newShape = new AdvancedShape(this, 2);
      newShape.addShape(new Line2D.Double(ptA, ptB));
      Shape head = GeomUtil.getArrowShape(ptA, ptB, HEAD_LENGTH, HEAD_WIDTH);
      if (head != null) {
        newShape.addScaleInvShape(head, ptA, getStroke(lineThickness), false).setFilled(true);
      }
    }
    setShape(newShape, mouseEvent);
    updateLabel(mouseEvent, getDefaultView2d(mouseEvent));
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
