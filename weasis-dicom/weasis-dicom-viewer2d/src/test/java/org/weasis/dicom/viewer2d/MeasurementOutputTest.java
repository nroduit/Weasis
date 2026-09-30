/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.viewer2d;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.weasis.core.api.image.util.Unit;
import org.weasis.core.ui.model.graphic.imp.AnnotationGraphic;
import org.weasis.core.ui.model.graphic.imp.angle.OpenAngleToolGraphic;
import org.weasis.core.ui.model.graphic.imp.area.PolygonGraphic;
import org.weasis.core.ui.model.graphic.imp.area.ThreePointsCircleGraphic;
import org.weasis.core.ui.model.graphic.imp.line.LineGraphic;
import org.weasis.core.ui.model.utils.ImageStatistics;
import org.weasis.core.ui.model.utils.bean.MeasureItem;
import org.weasis.core.ui.model.utils.bean.Measurement;

/** The measured values a caller reads: stable keys, English names, UCUM units, 'measure' types. */
class MeasurementOutputTest {

  private static JsonArray json(List<MeasureItem> items) {
    return View2dCommands.measurementsJson(items, Unit.MILLIMETER, "HU").build();
  }

  @Test
  void aMeasuredValueNamesItsKeyAndItsUnit() {
    Measurement length = new Measurement("length", "Longueur", 1, true); // NON-NLS
    JsonObject item = json(List.of(new MeasureItem(length, 12.5, "mm"))).getJsonObject(0);
    assertAll(
        () -> assertEquals("length", item.getString("key")),
        () -> assertEquals("Length", item.getString("name")),
        () -> assertEquals(12.5, item.getJsonNumber("value").doubleValue()),
        () -> assertEquals("mm", item.getString("unit")),
        () -> assertFalse(item.containsKey("id"), item.toString()));
  }

  @Test
  void aStatisticIsInTheUnitOfTheImageValues() {
    JsonArray items =
        json(
            List.of(
                new MeasureItem(ImageStatistics.IMAGE_MEAN, -204.0, "HU"),
                new MeasureItem(ImageStatistics.IMAGE_PIXELS, 190.0, "pix")));
    assertAll(
        () -> assertEquals("stats.mean", items.getJsonObject(0).getString("key")),
        () -> assertEquals("Mean", items.getJsonObject(0).getString("name")),
        () -> assertEquals("[hnsf'U]", items.getJsonObject(0).getString("unit")),
        () -> assertEquals("stats.pixels", items.getJsonObject(1).getString("key")),
        () -> assertEquals("{pixel}", items.getJsonObject(1).getString("unit")));
  }

  /** The same key twice in one region: one value per colour channel, or per fused series. */
  @Test
  void aQualifiedStatisticSaysWhatItWasComputedOn() {
    JsonArray items =
        json(
            List.of(
                new MeasureItem(ImageStatistics.IMAGE_MIN, " 2", 30.0, "HU"),
                new MeasureItem(ImageStatistics.IMAGE_MAX, " PT", 4.8, "SUVbw, g/ml")));
    assertAll(
        () -> assertEquals(2, items.getJsonObject(0).getInt("channel")),
        () -> assertEquals("PT", items.getJsonObject(1).getString("fusion")));
  }

  @Test
  void aGraphicIsNamedAsTheMeasureVerbNamesIt() {
    assertAll(
        () -> assertEquals("line", View2dCommands.Graphics.typeName(new LineGraphic())),
        () -> assertEquals("polygon", View2dCommands.Graphics.typeName(new PolygonGraphic())),
        () ->
            assertEquals(
                "circle", View2dCommands.Graphics.typeName(new ThreePointsCircleGraphic())),
        () ->
            assertEquals(
                "open-angle", View2dCommands.Graphics.typeName(new OpenAngleToolGraphic())),
        () ->
            assertEquals("annotation", View2dCommands.Graphics.typeName(new AnnotationGraphic())));
  }
}
