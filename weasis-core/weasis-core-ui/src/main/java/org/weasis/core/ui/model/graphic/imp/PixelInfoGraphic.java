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
import jakarta.xml.bind.annotation.XmlType;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import javax.swing.Icon;
import org.weasis.core.api.gui.util.GeomUtil;
import org.weasis.core.api.image.measure.MeasurementsAdapter;
import org.weasis.core.api.image.util.MeasurableLayer;
import org.weasis.core.api.image.util.Unit;
import org.weasis.core.api.util.ResourceUtil;
import org.weasis.core.api.util.ResourceUtil.ActionIcon;
import org.weasis.core.ui.Messages;
import org.weasis.core.ui.editor.image.PixelInfo;
import org.weasis.core.ui.editor.image.ViewCanvas;
import org.weasis.core.ui.model.graphic.GraphicLabel;
import org.weasis.core.ui.model.utils.bean.AdvancedShape;
import org.weasis.core.ui.model.utils.bean.AdvancedShape.ScaleInvariantShape;
import org.weasis.core.ui.model.utils.bean.MeasureItem;
import org.weasis.core.ui.model.utils.bean.Measurement;
import org.weasis.core.ui.util.MouseEventDouble;

@XmlType(name = "pixelInfo")
@XmlRootElement(name = "pixelInfo")
public class PixelInfoGraphic extends AnnotationGraphic {

  public static final Icon ICON = ResourceUtil.getIcon(ActionIcon.DRAW_PIXEL_INFO);

  public static final Measurement ANCHOR_POINT_X =
      new Measurement(Messages.getString("PixelInfoGraphic.x"), 1, true, true, false);
  public static final Measurement ANCHOR_POINT_Y =
      new Measurement(Messages.getString("PixelInfoGraphic.y"), 2, true, true, false);

  private PixelInfo pixelInfo;

  public PixelInfoGraphic() {
    super();
  }

  public PixelInfoGraphic(PixelInfoGraphic graphic) {
    super(graphic);
  }

  public PixelInfo getPixelInfo() {
    return pixelInfo;
  }

  public void setPixelInfo(PixelInfo pixelInfo) {
    this.pixelInfo = pixelInfo;
  }

  @Override
  public PixelInfoGraphic copy() {
    return new PixelInfoGraphic(this);
  }

  @Override
  public Icon getIcon() {
    return ICON;
  }

  @Override
  public String getUIName() {
    return Messages.getString("Tools.pixelInfo");
  }

  @Override
  public int getKeyCode() {
    return 0;
  }

  @Override
  public void buildShape(MouseEventDouble mouseEvent) {
    updateTool();
    AdvancedShape newShape = null;
    ViewCanvas<?> view2d = getDefaultView2d(mouseEvent);

    if (ptBox != null) {
      if (labels == null) {
        if (view2d != null) {
          setLabel(null, view2d, ptBox);
          // call buildShape
          return;
        }
        if (labelHeight == 0 || labelWidth == 0) {
          // This graphic cannot be displayed, remove it.
          fireRemoveAction();
          return;
        }
      }
      if (view2d != null && ptAnchor != null) {
        setLabel(labels, view2d, ptBox);
        // call buildShape
        return;
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
            line, ptBox, labelBounds, getDashStroke(lineThickness), true);

        ScaleInvariantShape arrow =
            newShape.addScaleInvShape(
                GeomUtil.getArrowShape(ptAnchor, ptBox, 15, 8),
                ptAnchor,
                getStroke(lineThickness),
                true);
        arrow.setFilled(true);
      }
      newShape.addAllInvShape(labelBounds, ptBox, getStroke(lineThickness), true);
    }
    setShape(newShape, mouseEvent);
  }

  @Override
  public void setLabel(String[] labels, ViewCanvas<?> view2d, Point2D pos) {
    String[] lbs = null;
    if (view2d != null && ptAnchor != null) {
      pixelInfo =
          view2d.getPixelInfo(
              new Point((int) Math.floor(ptAnchor.getX()), (int) Math.floor(ptAnchor.getY())));
      if (pixelInfo != null) {
        lbs = new String[] {pixelInfo.getPixelValueText()};
      }
    } else {
      lbs = labels;
    }
    if (lbs == null) {
      lbs = new String[] {Messages.getString("PixelInfoGraphic.no_val")};
    }
    super.setLabel(lbs, view2d, pos);
    // MeasurableLayer layer = view2d == null ? null : view2d.getMeasurableLayer();
    // AbstractLayerModel model = (view2d != null) ? view2d.getLayerModel() : null;
    // if (model != null) {
    // ArrayList<Graphic> selectedGraphics = model.getSelectedGraphics();
    // boolean isMultiSelection = selectedGraphics.size() > 1;
    // MeasureTool measureToolListener = null;
    //
    // if (selectedGraphics.size() == 1 && selectedGraphics.get(0) == this) {
    // GraphicsListener[] gfxListeners = model.getGraphicSelectionListeners();
    // if (gfxListeners != null) {
    // for (GraphicsListener listener : gfxListeners) {
    // if (listener instanceof MeasureTool) {
    // measureToolListener = (MeasureTool) listener;
    // break;
    // }
    // }
    // }
    // }
    //
    // if (measureToolListener != null && !isMultiSelection) {
    // Unit displayUnit = (Unit) view2d.getActionValue(ActionW.SPATIAL_UNIT.cmd());
    // List<MeasureItem> measList = computeMeasurements(layer, true, displayUnit);
    // measureToolListener.updateMeasuredItems(measList);
    // }
    // }
  }

  @Override
  public List<MeasureItem> computeMeasurements(
      MeasurableLayer layer, boolean releaseEvent, Unit displayUnit) {
    // FIXME not used
    if (layer != null && layer.hasContent() && isShapeValid()) {
      MeasurementsAdapter adapter = layer.getMeasurementAdapter(displayUnit);

      if (adapter != null) {
        ArrayList<MeasureItem> measVal = new ArrayList<>();

        if (ANCHOR_POINT_X.getComputed()) {
          measVal.add(
              new MeasureItem(
                  ANCHOR_POINT_X, adapter.getXCalibratedValue(ptAnchor.getX()), adapter.getUnit()));
        }
        if (ANCHOR_POINT_Y.getComputed()) {
          measVal.add(
              new MeasureItem(
                  ANCHOR_POINT_Y, adapter.getYCalibratedValue(ptAnchor.getY()), adapter.getUnit()));
        }
        String unit = Unit.PIXEL.getAbbreviation();
        if (!unit.equals(adapter.getUnit())) {
          if (ANCHOR_POINT_X.getComputed()) {
            measVal.add(
                new MeasureItem(ANCHOR_POINT_X, ptAnchor.getX() + adapter.getOffsetX(), unit));
          }
          if (ANCHOR_POINT_Y.getComputed()) {
            measVal.add(
                new MeasureItem(ANCHOR_POINT_Y, ptAnchor.getY() + adapter.getOffsetY(), unit));
          }
        }

        if (Objects.nonNull(pixelInfo)) {
          double[] values = pixelInfo.getValues();
          String[] channelNames = pixelInfo.getChannelNames();
          if (Objects.nonNull(values)) {
            for (int i = 0; i < values.length; i++) {
              Measurement m =
                  new Measurement(
                      (channelNames == null || i >= channelNames.length)
                          ? Messages.getString("PixelInfoGraphic.unknown")
                          : channelNames[i],
                      i + 2,
                      true,
                      true,
                      false);
              measVal.add(new MeasureItem(m, values[i], pixelInfo.getPixelValueUnit()));
            }
          }
        }
        return measVal;
      }
    }
    return Collections.emptyList();
  }
}
