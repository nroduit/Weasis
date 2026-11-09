/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.model.utils;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.geom.Point2D;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.weasis.core.ui.model.GraphicModel;
import org.weasis.core.ui.model.graphic.imp.line.LineGraphic;
import org.weasis.core.ui.model.imp.XmlGraphicModel;
import org.weasis.core.ui.serialize.XmlSerializer;

class GraphicUndoHistoryTest {

  private static LineGraphic line(double x2, double y2) throws Exception {
    LineGraphic line = new LineGraphic();
    line.buildGraphic(List.of(new Point2D.Double(0, 0), new Point2D.Double(x2, y2)));
    return line;
  }

  private static int graphicsIn(String xml) throws Exception {
    GraphicModel model = XmlSerializer.readPresentationModel(new StringReader(xml));
    return model == null ? 0 : model.getAllGraphics().size();
  }

  @Test
  void aCommitRecordsOneEntryPerBurstAndUndoRedoSwapTheSnapshots() throws Exception {
    List<String> restored = new ArrayList<>();
    GraphicUndoHistory history = new GraphicUndoHistory((image, xml) -> restored.add(xml), 50);
    XmlGraphicModel model = new XmlGraphicModel();
    history.track(null, model);

    assertAll(
        "nothing to undo on a fresh model, and a commit without a change adds nothing",
        () -> assertFalse(history.canUndo()),
        () -> assertFalse(history.commit()));

    model.addGraphic(line(30, 40));
    assertTrue(history.commit(), "the first graphic is one entry");
    model.addGraphic(line(10, 10));
    model.addGraphic(line(20, 20));
    assertTrue(history.commit(), "two graphics added before the commit are one entry");
    assertFalse(history.commit(), "the same model again is no entry");

    assertAll(
        () -> assertTrue(history.canUndo()),
        () -> assertFalse(history.canRedo()),
        () -> assertTrue(history.undo()),
        () -> assertEquals(1, restored.size()),
        () -> assertEquals(1, graphicsIn(restored.get(0)), "back to the first graphic alone"),
        () -> assertTrue(history.canRedo()),
        () -> assertTrue(history.undo()),
        () -> assertEquals(0, graphicsIn(restored.get(1)), "back to the empty model"),
        () -> assertFalse(history.canUndo()),
        () -> assertTrue(history.redo()),
        () -> assertEquals(1, graphicsIn(restored.get(2))),
        () -> assertTrue(history.redo()),
        () -> assertEquals(3, graphicsIn(restored.get(3))),
        () -> assertFalse(history.redo()));
  }

  @Test
  void aChangeMadeWhileApplyingASnapshotIsNotRecordedAndTheLimitHolds() throws Exception {
    XmlGraphicModel model = new XmlGraphicModel();
    List<String> restored = new ArrayList<>();
    GraphicUndoHistory[] holder = new GraphicUndoHistory[1];
    holder[0] =
        new GraphicUndoHistory(
            (image, xml) -> {
              restored.add(xml);
              // what a view does on a restore: a model change under the applying flag
              assertFalse(holder[0].commit(), "a commit while applying records nothing");
            },
            2);
    GraphicUndoHistory history = holder[0];
    history.track(null, model);
    for (int i = 1; i <= 4; i++) {
      model.addGraphic(line(i, i));
      history.commit();
    }
    assertAll(
        "only the last two entries are kept",
        () -> assertTrue(history.undo()),
        () -> assertTrue(history.undo()),
        () -> assertFalse(history.undo()),
        () -> assertEquals(2, restored.size()),
        () -> assertEquals(2, graphicsIn(restored.get(1))));

    history.clear();
    assertAll(
        () -> assertFalse(history.canUndo()),
        () -> assertFalse(history.canRedo()),
        () -> assertFalse(history.commit(), "nothing is tracked after a clear"));

    GraphicUndoHistory off = new GraphicUndoHistory((image, xml) -> {}, 0);
    off.track(null, model);
    model.addGraphic(line(9, 9));
    assertAll(
        "a zero limit turns the history off",
        () -> assertFalse(off.isEnabled()),
        () -> assertFalse(off.commit()),
        () -> assertFalse(off.canUndo()));
  }
}
