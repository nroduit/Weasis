/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.model.graphic;

import jakarta.xml.bind.JAXBException;
import java.awt.geom.Point2D;
import java.io.Reader;
import java.util.List;
import java.util.Objects;
import org.opencv.core.Point3;
import org.weasis.core.api.image.measure.PlaneGeometry;
import org.weasis.core.api.image.util.MeasurableLayer;
import org.weasis.core.api.image.util.Unit;
import org.weasis.core.ui.editor.image.ImageRegionStatistics;
import org.weasis.core.ui.editor.image.ViewCanvas;
import org.weasis.core.ui.model.AbstractGraphicModel;
import org.weasis.core.ui.model.GraphicModel;
import org.weasis.core.ui.model.layer.GraphicLayer;
import org.weasis.core.ui.model.utils.bean.MeasureItem;
import org.weasis.core.ui.model.utils.exceptions.InvalidShapeException;
import org.weasis.core.ui.serialize.XmlSerializer;

/**
 * Headless entry point of the measurement tools: build a graphic from a tool key and points,
 * compute its values, and read or write the presentation model. The panel and the commands are
 * clients of this service.
 */
public final class MeasurementService {

  private static final class Holder {
    private static final MeasurementService INSTANCE =
        new MeasurementService(GraphicRegistry.getInstance());
  }

  private final GraphicRegistry registry;

  MeasurementService(GraphicRegistry registry) {
    this.registry = Objects.requireNonNull(registry);
  }

  public static MeasurementService getInstance() {
    return Holder.INSTANCE;
  }

  public GraphicRegistry getRegistry() {
    return registry;
  }

  /**
   * Builds a graphic of a tool from image points, configured like the palette item.
   *
   * @throws IllegalArgumentException when the key is unknown
   * @throws InvalidShapeException when the points do not make a valid shape for the tool
   */
  public Graphic create(String toolKey, List<Point2D> points) throws InvalidShapeException {
    Graphic prototype =
        registry
            .prototype(toolKey)
            .orElseThrow(() -> new IllegalArgumentException("Unknown graphic tool: " + toolKey));
    Graphic graphic = prototype.copy();
    graphic.buildGraphic(points);
    return graphic;
  }

  /** Builds the graphic and anchors it in patient space through the plane geometry, if any. */
  public Graphic create(String toolKey, List<Point2D> points, PlaneGeometry geometry)
      throws InvalidShapeException {
    Graphic graphic = create(toolKey, points);
    graphic.anchor(geometry);
    return graphic;
  }

  /** Patient-space points of a graphic, empty when it was never drawn on a located plane. */
  public List<Point3> patientPoints(Graphic graphic) {
    SpatialAnchor anchor = graphic.getAnchor();
    return anchor == null ? List.of() : anchor.getPoints();
  }

  /** Creates the graphic and adds it to the view, on the given layer or the tool's default one. */
  public Graphic draw(
      ViewCanvas<?> canvas, String toolKey, List<Point2D> points, GraphicLayer layer)
      throws InvalidShapeException {
    Graphic graphic = create(toolKey, points);
    AbstractGraphicModel.addGraphicToModel(canvas, layer, graphic);
    canvas.getJComponent().repaint();
    return graphic;
  }

  /** All the values of a graphic, including the slow ones, in the given display unit. */
  public List<MeasureItem> measure(Graphic graphic, MeasurableLayer layer, Unit unit) {
    return graphic.computeMeasurements(layer, true, unit);
  }

  /** Pixel statistics of the region enclosed by an area graphic. */
  public List<MeasureItem> statistics(GraphicArea area, MeasurableLayer layer) {
    return ImageRegionStatistics.getImageStatistics(area, layer, true);
  }

  public String toXml(GraphicModel model) throws JAXBException {
    return XmlSerializer.toXml(model);
  }

  public GraphicModel fromXml(Reader reader) throws JAXBException {
    return XmlSerializer.readPresentationModel(reader);
  }
}
