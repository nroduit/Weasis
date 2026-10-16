/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.api.image;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import java.awt.Color;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.weasis.core.api.image.ImageOpNode.Param;
import org.weasis.opencv.data.PlanarImage;

@DisplayNameGeneration(ReplaceUnderscores.class)
class AbstractOpTrackingTest {

  private static final String KEY = "key";

  /** An operation writing into its map directly, as a plugin subclass may. */
  static final class DirectOp extends AbstractOp {
    DirectOp() {}

    DirectOp(DirectOp op) {
      super(op);
    }

    void putDirectly(String key, Object value) {
      params.put(key, value);
    }

    Map<String, Object> map() {
      return params;
    }

    @Override
    public void process() {
      params.put(Param.OUTPUT_IMG, params.get(Param.INPUT_IMG));
    }

    @Override
    public DirectOp copy() {
      return new DirectOp(this);
    }
  }

  private static boolean moves(DirectOp op, Consumer<DirectOp> change) {
    long before = op.getRevision();
    change.accept(op);
    return op.getRevision() != before;
  }

  private static DirectOp opWith(Object value) {
    DirectOp op = new DirectOp();
    op.setParam(KEY, value);
    return op;
  }

  @Test
  void a_different_value_moves_the_revision() {
    assertTrue(moves(opWith(1.0), op -> op.setParam(KEY, 2.0)));
    assertTrue(moves(new DirectOp(), op -> op.setParam(KEY, 2.0)));
  }

  @Test
  void an_equal_immutable_value_does_not_move_it() {
    assertFalse(moves(opWith(1.0), op -> op.setParam(KEY, 1.0)));
    assertFalse(moves(opWith("a"), op -> op.setParam(KEY, new String("a"))));
    assertFalse(moves(opWith(Color.RED), op -> op.setParam(KEY, new Color(255, 0, 0))));
    assertFalse(
        moves(
            opWith(ZoomOp.Interpolation.BICUBIC),
            op -> op.setParam(KEY, ZoomOp.Interpolation.BICUBIC)));
    assertFalse(moves(new DirectOp(), op -> op.setParam(KEY, null)), "null on an absent key");
  }

  @Test
  void an_equal_immutable_list_does_not_move_it_but_a_mutable_one_does() {
    assertFalse(moves(opWith(List.of(1.0, 0.0)), op -> op.setParam(KEY, List.of(1.0, 0.0))));
    assertFalse(
        moves(
            opWith(List.of(1.0, 0.0)),
            op -> op.setParam(KEY, java.util.stream.Stream.of(1.0, 0.0).toList())));
    assertTrue(
        moves(
            opWith(new ArrayList<>(List.of(1.0))),
            op -> op.setParam(KEY, new ArrayList<>(List.of(1.0)))));
    assertTrue(
        moves(
            opWith(List.of(new ArrayList<>())), op -> op.setParam(KEY, List.of(new ArrayList<>()))),
        "an immutable list of mutable elements");
  }

  @Test
  void a_null_value_stored_under_a_key_compares_safely() {
    assertTrue(moves(opWith(null), op -> op.setParam(KEY, 1.0)), "null to a value");
    assertTrue(moves(opWith(1.0), op -> op.setParam(KEY, null)), "a value to null");
    assertFalse(moves(opWith(null), op -> op.setParam(KEY, null)), "null to null");
  }

  @Test
  void the_same_mutable_object_set_again_moves_it() {
    Rectangle2D bounds = new Rectangle2D.Double(0, 0, 10, 10);
    DirectOp op = opWith(bounds);
    assertTrue(moves(op, o -> o.setParam(KEY, bounds)));
    assertTrue(
        moves(op, o -> o.setParam(KEY, new Rectangle2D.Double(0, 0, 10, 10))),
        "an equal mutable object: the stored one may have been modified in place");
  }

  @Test
  void the_same_immutable_object_set_again_does_not_move_it() {
    List<Double> matrix = List.of(1.0, 0.0, 0.0, 0.0, 1.0, 0.0);
    assertFalse(moves(opWith(matrix), op -> op.setParam(KEY, matrix)));
  }

  @Test
  void a_record_counts_as_a_value() {
    record Window(double min, double max) {}
    assertFalse(moves(opWith(new Window(0, 1)), op -> op.setParam(KEY, new Window(0, 1))));
    assertTrue(moves(opWith(new Window(0, 1)), op -> op.setParam(KEY, new Window(0, 2))));
  }

  @Test
  void input_and_output_images_never_move_it() {
    PlanarImage image = mock(PlanarImage.class);
    DirectOp op = new DirectOp();
    assertFalse(moves(op, o -> o.setParam(Param.INPUT_IMG, image)));
    assertFalse(moves(op, o -> o.setParam(Param.OUTPUT_IMG, image)));
    assertFalse(moves(op, AbstractOp::clearIOCache));
    assertFalse(moves(op, o -> o.removeParam(Param.INPUT_IMG)));
  }

  @Test
  void removing_moves_it_only_when_the_key_was_there() {
    assertTrue(moves(opWith(1.0), op -> op.removeParam(KEY)));
    assertFalse(moves(new DirectOp(), op -> op.removeParam(KEY)));
  }

  @Test
  void every_mutation_path_of_the_map_is_tracked() {
    assertTrue(moves(new DirectOp(), op -> op.putDirectly(KEY, 1)), "put on the field");
    assertTrue(moves(new DirectOp(), op -> op.setAllParameters(Map.of(KEY, 1))), "putAll");
    assertTrue(moves(new DirectOp(), op -> op.map().putIfAbsent(KEY, 1)), "putIfAbsent");
    assertFalse(moves(opWith(1), op -> op.map().putIfAbsent(KEY, 2)), "putIfAbsent, present");
    assertTrue(moves(opWith(1), op -> op.map().replace(KEY, 2)), "replace");
    assertTrue(moves(opWith(1), op -> op.map().replace(KEY, 1, 2)), "replace if");
    assertTrue(moves(opWith(1), op -> op.map().remove(KEY, 1)), "remove if");
    assertTrue(moves(opWith(1), op -> op.map().merge(KEY, 1, (a, b) -> 3)), "merge");
    assertTrue(moves(opWith(1), op -> op.map().compute(KEY, (k, v) -> 5)), "compute");
    assertTrue(
        moves(opWith(1), op -> op.map().computeIfPresent(KEY, (k, v) -> null)), "computeIfPresent");
    assertTrue(
        moves(new DirectOp(), op -> op.map().computeIfAbsent(KEY, k -> 1)), "computeIfAbsent");
    assertTrue(moves(opWith(1), op -> op.map().replaceAll((k, v) -> 7)), "replaceAll");
    assertTrue(moves(opWith(1), AbstractOp::clearParams), "clear");
  }

  @Test
  void clearing_input_and_output_only_does_not_move_it() {
    DirectOp op = new DirectOp();
    op.setParam(Param.INPUT_IMG, mock(PlanarImage.class));
    assertFalse(moves(op, AbstractOp::clearParams));
  }

  @Test
  void enabling_and_naming_are_parameters() {
    DirectOp op = new DirectOp();
    assertTrue(moves(op, o -> o.setEnabled(false)));
    assertFalse(moves(op, o -> o.setEnabled(false)));
    assertTrue(moves(op, o -> o.setName("other")));
  }

  @Test
  void invalidate_moves_it() {
    assertTrue(moves(new DirectOp(), ImageOpNode::invalidate));
  }

  @Test
  void a_copy_keeps_the_parameters_without_the_images() {
    DirectOp op = opWith(1.0);
    op.setParam(Param.OUTPUT_IMG, mock(PlanarImage.class));
    DirectOp copy = op.copy();
    assertEquals(1.0, copy.getParam(KEY));
    assertEquals(null, copy.getParam(Param.OUTPUT_IMG));
    assertTrue(moves(copy, o -> o.setParam(KEY, 2.0)));
    assertEquals(1.0, op.getParam(KEY), "the copy has its own map");
  }

  @Test
  void the_map_still_behaves_as_a_map() {
    DirectOp op = opWith(1.0);
    Map<String, Object> expected = new HashMap<>();
    expected.put(KEY, 1.0);
    assertEquals(expected, op.map());
    assertNotEquals(null, op.map());
  }
}
