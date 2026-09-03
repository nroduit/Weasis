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

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.awt.geom.Point2D;
import java.beans.PropertyChangeEvent;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.opencv.core.Point3;
import org.weasis.core.api.image.measure.PlaneGeometry;
import org.weasis.core.api.image.util.MeasurableLayer;
import org.weasis.core.ui.editor.image.ViewCanvas;
import org.weasis.core.ui.model.GraphicModel;
import org.weasis.core.ui.model.graphic.imp.AnnotationGraphic;
import org.weasis.core.ui.model.graphic.imp.line.LineGraphic;
import org.weasis.core.ui.model.imp.XmlGraphicModel;
import org.weasis.core.ui.serialize.XmlSerializer;

@DisplayNameGeneration(ReplaceUnderscores.class)
class SpatialAnchorTest {

  /** An axial plane at z = 10 mm with 0.5 mm pixels, origin at the patient origin. */
  static final PlaneGeometry AXIAL =
      new PlaneGeometry() {
        @Override
        public String getFrameOfReferenceUID() {
          return "1.2.3"; // NON-NLS
        }

        @Override
        public Point3 getNormal() {
          return new Point3(0, 0, 1);
        }

        @Override
        public Point3 toPatient(Point2D p) {
          return new Point3(p.getX() * 0.5, p.getY() * 0.5, 10);
        }

        @Override
        public Point2D toImage(Point3 p) {
          return new Point2D.Double(p.x / 0.5, p.y / 0.5);
        }
      };

  private static LineGraphic line() throws Exception {
    LineGraphic line = new LineGraphic();
    line.buildGraphic(List.of(new Point2D.Double(0, 0), new Point2D.Double(30, 40)));
    return line;
  }

  @Test
  void anchoring_converts_handle_points_to_patient_space() throws Exception {
    LineGraphic line = line();
    assertNull(line.getAnchor());
    line.anchor(AXIAL);
    SpatialAnchor anchor = line.getAnchor();
    assertNotNull(anchor);
    assertAll(
        () -> assertEquals("1.2.3", anchor.getFrameOfReferenceUID()),
        () -> assertEquals(new Point3(0, 0, 1), anchor.getNormal()),
        () ->
            assertEquals(List.of(new Point3(0, 0, 10), new Point3(15, 20, 10)), anchor.getPoints()),
        () -> assertEquals(new Point2D.Double(30, 40), anchor.toImage(AXIAL).get(1)),
        () -> assertEquals(anchor, line.copy().getAnchor()));
  }

  @Test
  void anchor_change_is_notified_once() throws Exception {
    LineGraphic line = line();
    List<PropertyChangeEvent> events = new ArrayList<>();
    line.addPropertyChangeListener(
        e -> {
          if (Graphic.PROPERTY_ANCHOR.equals(e.getPropertyName())) {
            events.add(e);
          }
        });
    line.anchor(AXIAL);
    line.anchor(AXIAL);
    assertEquals(1, events.size());
    assertEquals(line.getAnchor(), events.getFirst().getNewValue());
  }

  @Test
  void annotation_is_anchored_on_the_plane_of_the_view_when_released() {
    AnnotationGraphic annotation = new AnnotationGraphic();
    annotation.setPts(List.of(new Point2D.Double(0, 0), new Point2D.Double(30, 40)));
    MeasurableLayer layer = mock(MeasurableLayer.class);
    when(layer.getPlaneGeometry()).thenReturn(AXIAL);
    ViewCanvas<?> view = mock(ViewCanvas.class);
    when(view.getMeasurableLayer()).thenReturn(layer);

    annotation.updateLabel(Boolean.FALSE, view);
    assertNull(annotation.getAnchor());
    annotation.updateLabel(Boolean.TRUE, view);
    assertEquals(
        List.of(new Point3(0, 0, 10), new Point3(15, 20, 10)), annotation.getAnchor().getPoints());
  }

  @Test
  void anchor_is_written_and_read_back_and_absent_from_older_files() throws Exception {
    XmlGraphicModel model = new XmlGraphicModel();
    LineGraphic line = line();
    line.anchor(AXIAL);
    model.addGraphic(line);

    String xml = XmlSerializer.toXml(model);
    assertTrue(
        xml.contains("<anchor frameOfReference=\"1.2.3\" nx=\"0.0\" ny=\"0.0\" nz=\"1.0\">"));
    assertTrue(xml.contains("<pt x=\"15.0\" y=\"20.0\" z=\"10.0\"/>"));

    GraphicModel read = XmlSerializer.readPresentationModel(new StringReader(xml));
    assertEquals(line.getAnchor(), read.getModels().getFirst().getAnchor());

    String legacy = xml.replaceAll("<anchor .*?</anchor>", "");
    GraphicModel old = XmlSerializer.readPresentationModel(new StringReader(legacy));
    assertNull(old.getModels().getFirst().getAnchor());
  }
}
