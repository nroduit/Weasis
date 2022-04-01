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

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlElementWrapper;
import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.XmlType;
import jakarta.xml.bind.annotation.adapters.XmlJavaTypeAdapter;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Paint;
import java.awt.Rectangle;
import java.awt.event.KeyEvent;
import java.awt.font.FontRenderContext;
import java.awt.font.TextLayout;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import javax.swing.Icon;
import org.weasis.core.api.gui.util.GeomUtil;
import org.weasis.core.api.util.FontTools;
import org.weasis.core.api.util.ResourceUtil;
import org.weasis.core.api.util.ResourceUtil.ActionIcon;
import org.weasis.core.ui.Messages;
import org.weasis.core.ui.editor.image.ViewCanvas;
import org.weasis.core.ui.model.graphic.AbstractDragGraphic;
import org.weasis.core.ui.model.graphic.Graphic;
import org.weasis.core.ui.model.graphic.GraphicLabel;
import org.weasis.core.ui.model.utils.bean.AdvancedShape;
import org.weasis.core.ui.model.utils.bean.AdvancedShape.BasicShape;
import org.weasis.core.ui.model.utils.bean.AdvancedShape.ScaleInvariantShape;
import org.weasis.core.ui.model.utils.exceptions.InvalidShapeException;
import org.weasis.core.ui.serialize.RectangleAdapter;
import org.weasis.core.ui.util.MouseEventDouble;
import org.weasis.core.util.StringUtil;

@XmlType(name = "annotation")
@XmlRootElement(name = "annotation")
@XmlAccessorType(XmlAccessType.NONE)
public class AnnotationGraphic extends AbstractDragGraphic {

  public static final Integer POINTS_NUMBER = 2;
  public static final Icon ICON = ResourceUtil.getIcon(ActionIcon.DRAW_TEXT);

  protected Point2D ptBox;
  protected Point2D ptAnchor; // Let AB be a simple a line segment
  protected String[] labels;
  protected Boolean lineABvalid; // estimate if line segment is valid or not
  protected Rectangle2D labelBounds;
  protected Double labelWidth;
  protected Double labelHeight;

  public AnnotationGraphic() {
    super(POINTS_NUMBER);
  }

  public AnnotationGraphic(AnnotationGraphic annotationGraphic) {
    super(annotationGraphic);
  }

  @Override
  protected void initCopy(Graphic graphic) {
    super.initCopy(graphic);
    if (graphic instanceof AnnotationGraphic annotationGraphic) {
      labels = Optional.ofNullable(annotationGraphic.labels).map(String[]::clone).orElse(null);
      labelBounds =
          Optional.ofNullable(annotationGraphic.labelBounds)
              .map(Rectangle2D::getBounds2D)
              .orElse(null);
      labelWidth = annotationGraphic.labelWidth;
      labelHeight = annotationGraphic.labelHeight;
    }
  }

  @Override
  public AnnotationGraphic copy() {
    return new AnnotationGraphic(this);
  }

  @Override
  protected void prepareShape() throws InvalidShapeException {
    if (!isShapeValid()) {
      throw new InvalidShapeException("This shape cannot be drawn");
    }
    // Do not build shape as labelBounds can be initialized only by the method setLabel()
  }

  protected void setHandlePointList(Point2D ptAnchor, Point2D ptBox) {
    Point2D pt2 = (ptBox == null && ptAnchor != null) ? ptAnchor : ptBox;
    Point2D pt1 = (pt2 != null && pt2.equals(ptAnchor)) ? null : ptAnchor;

    setHandlePoint(0, pt1 == null ? null : (Point2D) pt1.clone());
    setHandlePoint(1, pt2 == null ? null : (Point2D) pt2.clone());
    buildShape(null);
  }

  @Override
  public Icon getIcon() {
    return ICON;
  }

  @Override
  public String getUIName() {
    return Messages.getString("Tools.Anno");
  }

  @XmlElementWrapper(name = "labels")
  @XmlElement(name = "label")
  public String[] getLabels() {
    return labels;
  }

  public void setLabels(String[] labels) {
    this.labels = labels;
  }

  @XmlElement(name = "labelBounds")
  @XmlJavaTypeAdapter(RectangleAdapter.Rectangle2DAdapter.class)
  public Rectangle2D getLabelBounds() {
    return labelBounds;
  }

  public void setLabelBounds(Rectangle2D labelBounds) {
    this.labelBounds = labelBounds;
  }

  @XmlAttribute(name = "labelWidth")
  public Double getLabelWidth() {
    return labelWidth;
  }

  public void setLabelWidth(Double labelWidth) {
    this.labelWidth = labelWidth;
  }

  @XmlAttribute(name = "labelHeight")
  public Double getLabelHeight() {
    return labelHeight;
  }

  public void setLabelHeight(Double labelHeight) {
    this.labelHeight = labelHeight;
  }

  @Override
  public void updateLabel(ViewCanvas<?> view2d, Point2D pos, boolean releasedEvent) {
    setLabel(labels, view2d, pos);
  }

  @Override
  public void updateLabel(Object source, ViewCanvas<?> view2d) {
    setLabel(labels, view2d);
  }

  @Override
  public void buildShape(MouseEventDouble mouseEvent) {
    updateTool();
    AdvancedShape newShape = null;

    if (ptBox != null) {
      ViewCanvas<?> view = getDefaultView2d(mouseEvent);
      if (labels == null) {
        if (view != null) {
          setLabel(new String[] {getInitialText(view)}, view, ptBox);
          // call buildShape
          return;
        }
        if (labelHeight == 0 || labelWidth == 0) {
          // This graphic cannot be displayed, remove it.
          fireRemoveAction();
          return;
        }
      }
      newShape = new AdvancedShape(this, 2);
      Line2D line = null;
      if (lineABvalid) {
        line = new Line2D.Double(ptBox, ptAnchor);
      }
      labelBounds = new Rectangle.Double();
      labelBounds.setFrameFromCenter(
          ptBox.getX(),
          ptBox.getY(),
          ptBox.getX() + labelWidth / 2.0 + GraphicLabel.GROWING_BOUND,
          ptBox.getY()
              + labelHeight * (labels == null ? 1 : labels.length) / 2.0
              + GraphicLabel.GROWING_BOUND);
      GeomUtil.growRectangle(labelBounds, GraphicLabel.GROWING_BOUND);
      if (line != null) {
        newShape.addLinkSegmentToInvariantShape(
            line, ptBox, labelBounds, getDashStroke(lineThickness), false);

        ScaleInvariantShape arrow =
            newShape.addScaleInvShape(
                GeomUtil.getArrowShape(ptAnchor, ptBox, 15, 8),
                ptAnchor,
                getStroke(lineThickness),
                false);
        arrow.setFilled(true);
      }
      newShape.addAllInvShape(labelBounds, ptBox, getStroke(lineThickness), false);
    }

    setShape(newShape, mouseEvent);
  }

  @Override
  public int getKeyCode() {
    return KeyEvent.VK_B;
  }

  @Override
  public int getModifier() {
    return 0;
  }

  protected void updateTool() {
    ptAnchor = getHandlePoint(0);
    ptBox = getHandlePoint(1);

    lineABvalid = ptAnchor != null && !ptAnchor.equals(ptBox);
  }

  protected String getInitialText(ViewCanvas<?> view) {
    return Messages.getString("AnnotationGraphic.text_box");
  }

  @Override
  public void paintLabel(Graphics2D g2d, AffineTransform transform) {
    if (labelVisible && labels != null && labelBounds != null) {
      Paint oldPaint = g2d.getPaint();

      Rectangle2D rect = labelBounds;
      Point2D pt = new Point2D.Double(rect.getCenterX(), rect.getCenterY());
      if (transform != null) {
        transform.transform(pt, pt);
      }

      float px = (float) (pt.getX() - rect.getWidth() / 2 + GraphicLabel.GROWING_BOUND);
      float py = (float) (pt.getY() - rect.getHeight() / 2 + GraphicLabel.GROWING_BOUND);

      for (String label : labels) {
        if (StringUtil.hasText(label)) {
          py += labelHeight;
          FontTools.paintColorFontOutline(g2d, label, px, py, Color.WHITE);
        }
      }
      g2d.setPaint(oldPaint);
    }
  }

  @Override
  public Area getArea(AffineTransform transform) {
    if (shape == null) {
      return new Area();
    }
    if (shape instanceof AdvancedShape s) {
      Area area = s.getArea(transform);
      List<BasicShape> list = s.getShapeList();
      if (!list.isEmpty()) {
        BasicShape b = list.get(list.size() - 1);
        // Allow moving inside the box, not only around stroke.
        area.add(new Area(b.getRealShape()));
      }

      return area;
    } else {
      return super.getArea(transform);
    }
  }

  public Point2D getAnchorPoint() {
    updateTool();
    return ptAnchor == null ? null : (Point2D) ptAnchor.clone();
  }

  public Point2D getBoxPoint() {
    updateTool();
    return ptBox == null ? null : (Point2D) ptBox.clone();
  }

  protected void reset() {
    labels = null;
    labelBounds = null;
    labelHeight = labelWidth = 0d;
  }

  @Override
  public void setLabel(String[] labels, ViewCanvas<?> view2d) {
    Point2D pt = getBoxPoint();
    if (pt == null) {
      pt = getAnchorPoint();
    }
    if (pt != null) {
      this.setLabel(labels, view2d, pt);
    }
  }

  @Override
  public void setLabel(String[] labels, ViewCanvas<?> view2d, Point2D pos) {
    if (view2d == null || labels == null || labels.length == 0 || pos == null) {
      reset();
    } else {
      this.labels = labels;
      Font defaultFont = view2d.getFont();
      Graphics2D g2d = (Graphics2D) view2d.getJComponent().getGraphics();
      FontRenderContext fontRenderContext =
          g2d == null ? new FontRenderContext(null, false, false) : g2d.getFontRenderContext();
      updateBoundsSize(defaultFont, fontRenderContext);

      labelBounds = new Rectangle.Double();
      labelBounds.setFrameFromCenter(
          pos.getX(),
          pos.getY(),
          ptBox.getX() + labelWidth / 2.0 + GraphicLabel.GROWING_BOUND,
          ptBox.getY() + labelHeight * this.labels.length / 2.0 + GraphicLabel.GROWING_BOUND);
      GeomUtil.growRectangle(labelBounds, GraphicLabel.GROWING_BOUND);
    }
    buildShape(null);
  }

  protected void updateBoundsSize(Font defaultFont, FontRenderContext fontRenderContext) {
    Objects.requireNonNull(defaultFont);
    Objects.requireNonNull(fontRenderContext);

    if (labels == null || labels.length == 0) {
      reset();
    } else {
      double maxWidth = 0;
      for (String label : labels) {
        if (StringUtil.hasText(label)) {
          TextLayout layout = new TextLayout(label, defaultFont, fontRenderContext);
          maxWidth = Math.max(layout.getBounds().getWidth(), maxWidth);
        }
      }
      labelHeight =
          new TextLayout("Tg", defaultFont, fontRenderContext).getBounds().getHeight() // NON-NLS
              + 2;
      labelWidth = maxWidth;
    }
  }
}
