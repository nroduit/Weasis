/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.layout;

import static org.junit.jupiter.api.Assertions.*;

import java.awt.Dimension;
import org.junit.jupiter.api.Test;
import org.weasis.core.api.gui.layout.ConstraintSpec;
import org.weasis.core.api.gui.layout.MigLayoutModel;

/** Unit tests for MigLayoutModel's weight, shrink, and constraint management features. */
class MigLayoutModelWeightTest {

  @Test
  void testSetColumnWeights() {
    MigLayoutModel model =
        new MigLayoutModel(
            "test1", "Test Layout", 2, 3, "org.weasis.core.ui.editor.image.DefaultView2d");

    ConstraintSpec[] specs = {
      new ConstraintSpec(100), new ConstraintSpec(200), new ConstraintSpec(100)
    };
    model.setColumnConstraintSpecs(specs);

    String constraints = model.getColumnConstraints();
    assertNotNull(constraints);
    assertTrue(constraints.contains("grow 100"));
    assertTrue(constraints.contains("grow 200"));
    assertTrue(constraints.contains("fill"));
  }

  @Test
  void testSetColumnWeightsWithShrink() {
    MigLayoutModel model =
        new MigLayoutModel(
            "test2", "Test Layout", 2, 3, "org.weasis.core.ui.editor.image.DefaultView2d");

    ConstraintSpec[] specs = {
      new ConstraintSpec(100, 50), new ConstraintSpec(100, 100), new ConstraintSpec(100, 25)
    };
    model.setColumnConstraintSpecs(specs);

    String constraints = model.getColumnConstraints();
    assertNotNull(constraints);
    assertTrue(constraints.contains("grow 100"));
    assertTrue(constraints.contains("shrink 50"));
    assertTrue(constraints.contains("shrink 100"));
    assertTrue(constraints.contains("shrink 25"));
  }

  @Test
  void testSetRowWeights() {
    MigLayoutModel model =
        new MigLayoutModel(
            "test3", "Test Layout", 3, 2, "org.weasis.core.ui.editor.image.DefaultView2d");

    ConstraintSpec[] specs = {
      new ConstraintSpec(100), new ConstraintSpec(50), new ConstraintSpec(150)
    };
    model.setRowConstraintSpecs(specs);

    String constraints = model.getRowConstraints();
    assertNotNull(constraints);
    assertTrue(constraints.contains("grow 100"));
    assertTrue(constraints.contains("grow 50"));
    assertTrue(constraints.contains("grow 150"));
  }

  @Test
  void testSetRowWeightsWithShrink() {
    MigLayoutModel model =
        new MigLayoutModel(
            "test4", "Test Layout", 2, 2, "org.weasis.core.ui.editor.image.DefaultView2d");

    ConstraintSpec[] specs = {new ConstraintSpec(100, 0), new ConstraintSpec(200, 100)};
    model.setRowConstraintSpecs(specs);

    String constraints = model.getRowConstraints();
    assertNotNull(constraints);
    assertTrue(constraints.contains("grow 100"));
    assertTrue(constraints.contains("grow 200"));
    assertTrue(constraints.contains("shrink 0"));
    assertTrue(constraints.contains("shrink 100"));
  }

  @Test
  void testConstraintSpecSimple() {
    ConstraintSpec spec = new ConstraintSpec(150);
    String constraint = spec.toConstraintString();

    assertTrue(constraint.contains("grow 150"));
    assertTrue(constraint.contains("shrink 100"));
    assertTrue(constraint.contains("fill"));
  }

  @Test
  void testConstraintSpecWithShrink() {
    ConstraintSpec spec = new ConstraintSpec(100, 50);
    String constraint = spec.toConstraintString();

    assertTrue(constraint.contains("grow 100"));
    assertTrue(constraint.contains("shrink 50"));
    assertTrue(constraint.contains("fill"));
  }

  @Test
  void testConstraintSpecFixed() {
    ConstraintSpec spec = ConstraintSpec.fixed(200);
    String constraint = spec.toConstraintString();

    assertTrue(constraint.contains("grow 0"));
    assertTrue(constraint.contains("shrink 0"));
    assertTrue(constraint.contains("200::200"));
  }

  @Test
  void testConstraintSpecWithBounds() {
    ConstraintSpec spec = ConstraintSpec.withBounds(100, 400);
    String constraint = spec.toConstraintString();

    assertTrue(constraint.contains("grow 100"));
    assertTrue(constraint.contains("shrink 100"));
    assertTrue(constraint.contains("100::400"));
  }

  @Test
  void testSetColumnConstraintSpecs() {
    MigLayoutModel model =
        new MigLayoutModel(
            "test5", "Test Layout", 1, 3, "org.weasis.core.ui.editor.image.DefaultView2d");

    ConstraintSpec[] specs = {
      ConstraintSpec.fixed(200), new ConstraintSpec(100, 100), ConstraintSpec.fixed(150)
    };
    model.setColumnConstraintSpecs(specs);

    String constraints = model.getColumnConstraints();
    assertNotNull(constraints);
    assertTrue(constraints.contains("200::200"));
    assertTrue(constraints.contains("150::150"));
  }

  @Test
  void testSetRowConstraintSpecs() {
    MigLayoutModel model =
        new MigLayoutModel(
            "test6", "Test Layout", 3, 1, "org.weasis.core.ui.editor.image.DefaultView2d");

    ConstraintSpec[] specs = {
      ConstraintSpec.fixed(50), new ConstraintSpec(100, 100), ConstraintSpec.fixed(30)
    };
    model.setRowConstraintSpecs(specs);

    String constraints = model.getRowConstraints();
    assertNotNull(constraints);
    assertTrue(constraints.contains("50::50"));
    assertTrue(constraints.contains("30::30"));
  }

  @Test
  void testNegativeWeightsAreRejected() {
    MigLayoutModel model =
        new MigLayoutModel(
            "test7", "Test Layout", 1, 2, "org.weasis.core.ui.editor.image.DefaultView2d");

    // ConstraintSpec constructor should reject negative weights
    assertThrows(
        IllegalArgumentException.class,
        () -> {
          new ConstraintSpec(-50, 100);
        });
  }

  @Test
  void testInvalidConstraintSpecArrayLength() {
    MigLayoutModel model =
        new MigLayoutModel(
            "test8", "Test Layout", 2, 3, "org.weasis.core.ui.editor.image.DefaultView2d");

    String originalConstraints = model.getColumnConstraints();

    // Try to set wrong number of constraint specs
    ConstraintSpec[] specs = {
      new ConstraintSpec(100), new ConstraintSpec(100)
    }; // Only 2 specs for 3 columns
    model.setColumnConstraintSpecs(specs);

    // Constraints should not change
    assertEquals(originalConstraints, model.getColumnConstraints());
  }

  @Test
  void testConstraintSpecWithPartialBounds() {
    ConstraintSpec spec = new ConstraintSpec(100, 50, true, 100, 500);
    String constraint = spec.toConstraintString();

    assertTrue(constraint.contains("100::500"));
  }

  @Test
  void testGridSize() {
    MigLayoutModel model =
        new MigLayoutModel(
            "test9", "Test Layout", 3, 4, "org.weasis.core.ui.editor.image.DefaultView2d");

    Dimension gridSize = model.getGridSize();
    assertEquals(4, gridSize.width);
    assertEquals(3, gridSize.height);
  }
}
