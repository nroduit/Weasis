/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.model.graphic.imp.area;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.Stroke;
import java.awt.geom.AffineTransform;
import java.util.Collections;
import java.util.List;
import javax.swing.Icon;
import org.weasis.core.api.image.util.MeasurableLayer;
import org.weasis.core.api.image.util.Unit;
import org.weasis.core.api.util.ResourceUtil;
import org.weasis.core.api.util.ResourceUtil.ActionIcon;
import org.weasis.core.ui.Messages;
import org.weasis.core.ui.editor.image.ViewCanvas;
import org.weasis.core.ui.model.utils.Draggable;
import org.weasis.core.ui.model.utils.bean.MeasureItem;
import org.weasis.core.ui.model.utils.imp.SelectedDragSequence;

/**
 * The Class SelectGraphic.
 *
 * @author Nicolas Roduit
 * @author Benoit Jacquemoud
 */
public class SelectGraphic extends RectangleGraphic {

  public static final Icon ICON = ResourceUtil.getIcon(ActionIcon.DRAW_SELECTION);

  public SelectGraphic() {
    super();
    setPaint(Color.WHITE);
  }

  public SelectGraphic(SelectGraphic graphic) {
    super(graphic);
  }

  @Override
  public SelectGraphic copy() {
    return new SelectGraphic(this);
  }

  @Override
  public Icon getIcon() {
    return ICON;
  }

  @Override
  public String getUIName() {
    return Messages.getString("MeasureToolBar.sel");
  }

  @Override
  public void paint(Graphics2D g2d, AffineTransform transform) {
    Color oldColor = g2d.getColor();
    Stroke oldStroke = g2d.getStroke();
    g2d.setPaint(Color.white);

    float[] dash = {5f};
    Shape transformedShape = transform == null ? shape : transform.createTransformedShape(shape);

    g2d.setStroke(new BasicStroke(1.0F, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 5F, dash, 0));
    g2d.draw(transformedShape);

    g2d.setColor(Color.black);
    g2d.setStroke(
        new BasicStroke(1.0F, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 5F, dash, 5F));
    g2d.draw(transformedShape);

    g2d.setColor(oldColor);
    g2d.setStroke(oldStroke);
  }

  @Override
  public Draggable createResizeDrag(Integer i) {
    return new SelectedDragSequence(this);
  }

  @Override
  public Boolean isGraphicComplete() {
    return pts.size() > 1;
  }

  @Override
  public Boolean getSelected() {
    return Boolean.TRUE;
  }

  @Override
  public void updateLabel(Object source, ViewCanvas<?> view2d) {
    // Do not draw any labels
  }

  @Override
  public List<MeasureItem> computeMeasurements(
      MeasurableLayer layer, boolean releaseEvent, Unit displayUnit) {
    return Collections.emptyList();
  }
}
