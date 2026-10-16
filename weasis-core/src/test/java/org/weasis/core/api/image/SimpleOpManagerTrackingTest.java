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
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.weasis.core.api.image.ImageOpNode.Param;
import org.weasis.core.api.setting.SettingEnvironment;
import org.weasis.core.api.setting.Settings;
import org.weasis.opencv.data.PlanarImage;

@DisplayNameGeneration(ReplaceUnderscores.class)
class SimpleOpManagerTrackingTest {

  private static final String VALUE = "value";

  /** Counts its runs; returns a new image, or its input when passing through. */
  static class CountingOp extends AbstractOp {
    int runs;
    boolean passThrough;
    boolean failOnce;
    final List<PlanarImage> outputs = new ArrayList<>();

    CountingOp(String name) {
      setName(name);
    }

    CountingOp(CountingOp op) {
      super(op);
      this.passThrough = op.passThrough;
    }

    @Override
    public void process() {
      runs++;
      if (failOnce) {
        failOnce = false;
        throw new IllegalStateException("expected failure");
      }
      PlanarImage output = passThrough ? getSourceImage() : image();
      outputs.add(output);
      params.put(Param.OUTPUT_IMG, output);
    }

    @Override
    public CountingOp copy() {
      return new CountingOp(this);
    }
  }

  /** An operation that cannot tell what changed. */
  static final class UntrackedOp extends CountingOp {
    UntrackedOp(String name) {
      super(name);
    }

    @Override
    public long getRevision() {
      return -1;
    }
  }

  private SimpleOpManager manager;
  private CountingOp first;
  private CountingOp middle;
  private CountingOp last;

  private static PlanarImage image() {
    PlanarImage image = mock(PlanarImage.class);
    when(image.width()).thenReturn(8);
    when(image.height()).thenReturn(8);
    return image;
  }

  @BeforeEach
  void setUp() {
    Settings.install(SettingEnvironment.inMemory(Map.of()));
    manager = new SimpleOpManager();
    first = new CountingOp("first");
    middle = new CountingOp("middle");
    last = new CountingOp("last");
    manager.addImageOperationAction(first);
    manager.addImageOperationAction(middle);
    manager.addImageOperationAction(last);
    manager.setFirstNode(image());
    manager.process();
    resetCounts();
  }

  @AfterEach
  void tearDown() {
    Settings.install(SettingEnvironment.inMemory(Map.of()));
  }

  private void resetCounts() {
    first.runs = 0;
    middle.runs = 0;
    last.runs = 0;
  }

  private List<Integer> runs() {
    return List.of(first.runs, middle.runs, last.runs);
  }

  private List<Integer> pass() {
    resetCounts();
    manager.process();
    return runs();
  }

  @Test
  void nothing_changed_runs_nothing_and_keeps_the_output() {
    PlanarImage before = manager.getLastNodeOutputImage().orElseThrow();
    assertEquals(List.of(0, 0, 0), pass());
    assertSame(before, manager.getLastNodeOutputImage().orElseThrow());
  }

  @Test
  void a_change_runs_the_node_and_those_below_it() {
    last.setParam(VALUE, 1);
    assertEquals(List.of(0, 0, 1), pass());
    first.setParam(VALUE, 1);
    assertEquals(List.of(1, 1, 1), pass());
  }

  @Test
  void an_equal_value_set_again_runs_nothing() {
    middle.setParam(VALUE, 1.5);
    pass();
    middle.setParam(VALUE, 1.5);
    assertEquals(List.of(0, 0, 0), pass());
  }

  @Test
  void the_same_mutable_object_set_again_runs_the_node() {
    Rectangle2D bounds = new Rectangle2D.Double();
    last.setParam(VALUE, bounds);
    pass();
    bounds.setRect(0, 0, 4, 4);
    last.setParam(VALUE, bounds);
    assertEquals(List.of(0, 0, 1), pass());
  }

  @Test
  void a_node_passing_its_input_through_leaves_those_below_untouched() {
    middle.passThrough = true;
    middle.setParam(VALUE, 1);
    assertEquals(List.of(0, 1, 1), pass(), "the input of the last node changed");
    middle.setParam(VALUE, 2);
    assertEquals(List.of(0, 1, 0), pass(), "same input object for the last node");
  }

  @Test
  void a_disabled_node_passes_its_input_and_runs_again_once_enabled() {
    middle.setEnabled(false);
    assertEquals(List.of(0, 0, 1), pass());
    assertSame(
        first.getParam(Param.OUTPUT_IMG), last.getParam(Param.INPUT_IMG), "middle passed through");
    assertEquals(List.of(0, 0, 0), pass());
    middle.setEnabled(true);
    assertEquals(List.of(0, 1, 1), pass());
  }

  @Test
  void a_failed_node_is_tried_again() {
    middle.failOnce = true;
    middle.setParam(VALUE, 1);
    assertEquals(List.of(0, 1, 1), pass());
    assertSame(first.getParam(Param.OUTPUT_IMG), middle.getParam(Param.OUTPUT_IMG));
    assertEquals(List.of(0, 1, 1), pass());
    assertEquals(List.of(0, 0, 0), pass());
  }

  @Test
  void a_new_source_runs_everything() {
    manager.setFirstNode(image());
    assertEquals(List.of(1, 1, 1), pass());
  }

  @Test
  void the_same_source_set_again_runs_nothing() {
    manager.setFirstNode(manager.getFirstNodeInputImage().orElseThrow());
    assertEquals(List.of(0, 0, 0), pass());
  }

  @Test
  void a_released_output_is_computed_again() {
    PlanarImage output = middle.outputs.getLast();
    when(output.isReleased()).thenReturn(true);
    assertEquals(List.of(0, 1, 1), pass());
  }

  @Test
  void a_missing_output_is_computed_again() {
    manager.resetLastNodeOutputImage();
    assertEquals(List.of(0, 0, 1), pass());
  }

  @Test
  void adding_or_removing_a_node_runs_everything() {
    CountingOp added = new CountingOp("added");
    manager.addImageOperationAction(added);
    assertEquals(List.of(1, 1, 1), pass());
    manager.removeImageOperationAction(added);
    assertEquals(List.of(1, 1, 1), pass());
  }

  @Test
  void clearing_the_images_runs_everything() {
    manager.clearNodeIOCache();
    manager.setFirstNode(image());
    assertEquals(List.of(1, 1, 1), pass());
  }

  @Test
  void a_node_without_revision_runs_on_every_pass() {
    UntrackedOp untracked = new UntrackedOp("untracked");
    untracked.passThrough = true;
    manager.addImageOperationAction(untracked, SimpleOpManager.Position.BEFORE, last);
    pass();
    untracked.runs = 0;
    assertEquals(List.of(0, 0, 0), pass(), "passing through: the last node sees the same input");
    assertEquals(1, untracked.runs);
  }

  @Test
  void invalidate_runs_the_node() {
    middle.invalidate();
    assertEquals(List.of(0, 1, 1), pass());
  }

  @Test
  void a_copy_starts_with_nothing_to_reuse() {
    SimpleOpManager copy = manager.copy();
    copy.setFirstNode(manager.getFirstNodeInputImage().orElseThrow());
    copy.process();
    copy.getOperations().forEach(node -> assertEquals(1, ((CountingOp) node).runs, node.getName()));
    assertNotSame(manager.getLastNodeOutputImage(), copy.getLastNodeOutputImage());
  }

  @Test
  void without_tracking_every_node_runs() {
    Settings.install(SettingEnvironment.inMemory(Map.of("weasis.display.op.tracking", "false")));
    assertEquals(List.of(1, 1, 1), pass());
    assertEquals(List.of(1, 1, 1), pass());
    Settings.install(SettingEnvironment.inMemory(Map.of()));
    assertEquals(List.of(1, 1, 1), pass(), "nothing recorded while off");
    assertEquals(List.of(0, 0, 0), pass());
  }
}
