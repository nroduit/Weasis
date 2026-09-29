/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.editor.image;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.geom.Point2D;
import java.util.List;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.weasis.core.ui.model.graphic.imp.line.ArrowGraphic;
import org.weasis.core.ui.model.graphic.imp.line.LineGraphic;
import org.weasis.core.ui.model.layer.LayerType;

/** Only a finished, plain measurement line can calibrate an image. */
@DisplayNameGeneration(ReplaceUnderscores.class)
class CalibrationLineTest {

  private static <T extends LineGraphic> T drawn(T line, LayerType layerType) throws Exception {
    line.setLayerType(layerType);
    line.buildGraphic(List.of(new Point2D.Double(0, 0), new Point2D.Double(100, 0)));
    return line;
  }

  @Test
  void a_finished_measurement_line_is_accepted_and_nothing_else() throws Exception {
    LineGraphic unfinished = new LineGraphic();
    unfinished.setLayerType(LayerType.MEASURE);
    assertAll(
        () -> assertTrue(CalibrationView.accepts(drawn(new LineGraphic(), LayerType.MEASURE))),
        () -> assertFalse(CalibrationView.accepts(drawn(new LineGraphic(), LayerType.DRAW))),
        () -> assertFalse(CalibrationView.accepts(drawn(new ArrowGraphic(), LayerType.MEASURE))),
        () -> assertFalse(CalibrationView.accepts(unfinished)),
        () -> assertFalse(CalibrationView.accepts(null)));
  }
}
