/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.viewer2d.fusion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.weasis.core.api.image.ImageOpNode.Param;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.opencv.data.PlanarImage;

@DisplayNameGeneration(ReplaceUnderscores.class)
class FusionOpRevisionTest {

  private static FusionOp enabledOp(PlanarImage source) {
    FusionOp op = new FusionOp();
    op.setParam(FusionOp.P_FUSION_ENABLED, true);
    op.setParam(FusionOp.P_FUSION_SERIES, mock(MediaSeries.class));
    // No slice geometry: the matching PET slice cannot be found yet
    op.setParam(FusionOp.P_BASE_IMAGE, mock(DicomImageElement.class));
    op.setParam(FusionOp.P_FUSION_WINDOW, new FusionWindow(0, 10, 1, "SUV"));
    op.setParam(Param.INPUT_IMG, source);
    return op;
  }

  @Test
  void a_missing_overlay_makes_the_run_provisional() throws Exception {
    PlanarImage source = mock(PlanarImage.class);
    FusionOp op = enabledOp(source);
    op.process();
    assertSame(source, op.getParam(Param.OUTPUT_IMG), "the base image, without overlay");
    assertEquals(-1, op.getRevision(), "runs on every pass until the overlay is there");
  }

  @Test
  void a_missing_window_makes_the_run_provisional() throws Exception {
    FusionOp op = enabledOp(mock(PlanarImage.class));
    op.setParam(FusionOp.P_FUSION_WINDOW, null);
    op.process();
    assertEquals(-1, op.getRevision());
  }

  @Test
  void a_disabled_fusion_is_tracked() throws Exception {
    FusionOp op = enabledOp(mock(PlanarImage.class));
    op.process();
    op.setParam(FusionOp.P_FUSION_ENABLED, false);
    op.process();
    assertNotEquals(-1, op.getRevision());
  }

  @Test
  void clearing_the_cache_moves_the_revision() {
    FusionOp op = new FusionOp();
    long revision = op.getRevision();
    op.clearCache();
    assertNotEquals(revision, op.getRevision());
  }
}
