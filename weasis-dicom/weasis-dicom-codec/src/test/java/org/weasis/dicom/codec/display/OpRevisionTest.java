/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.codec.display;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.awt.Color;
import java.awt.geom.Rectangle2D;
import java.util.List;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.image.ImageOpNode.Param;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.api.service.UICore;
import org.weasis.core.api.service.WProperties;
import org.weasis.dicom.codec.Redaction;
import org.weasis.opencv.data.PlanarImage;

/** The ops reading something outside their parameters move their revision when it changes. */
@DisplayNameGeneration(ReplaceUnderscores.class)
class OpRevisionTest {

  private static PlanarImage source() {
    PlanarImage image = mock(PlanarImage.class);
    when(image.width()).thenReturn(8);
    return image;
  }

  private static MockedStatic<GuiUtils> preferences(WProperties properties) {
    UICore core = mock(UICore.class);
    when(core.getSystemPreferences()).thenReturn(properties);
    MockedStatic<GuiUtils> gui = mockStatic(GuiUtils.class);
    gui.when(GuiUtils::getUICore).thenReturn(core);
    return gui;
  }

  @Test
  void overlay_runs_again_when_the_color_preference_changes() throws Exception {
    WProperties properties = new WProperties();
    properties.putColorProperty(OverlayOp.OVERLAY_COLOR_KEY, Color.WHITE);
    try (MockedStatic<GuiUtils> _ = preferences(properties)) {
      OverlayOp op = new OverlayOp();
      op.setParam(OverlayOp.P_SHOW, true);
      op.setParam(Param.INPUT_IMG, source());
      op.process();
      long revision = op.getRevision();
      assertEquals(revision, op.getRevision(), "same color: no change");

      properties.putColorProperty(OverlayOp.OVERLAY_COLOR_KEY, Color.YELLOW);
      assertNotEquals(revision, op.getRevision());
    }
  }

  @Test
  void overlay_hidden_ignores_the_color_preference() {
    WProperties properties = new WProperties();
    try (MockedStatic<GuiUtils> _ = preferences(properties)) {
      OverlayOp op = new OverlayOp();
      op.setParam(OverlayOp.P_SHOW, false);
      long revision = op.getRevision();
      properties.putColorProperty(OverlayOp.OVERLAY_COLOR_KEY, Color.YELLOW);
      assertEquals(revision, op.getRevision());
    }
  }

  @Test
  void redaction_runs_again_when_the_mask_in_force_changes() throws Exception {
    // No mask at the run: nothing burned, so no native call
    ImageElement image = mock(ImageElement.class);
    RedactionOp op = new RedactionOp();
    op.setParam(RedactionOp.P_IMAGE, image);
    op.setParam(Param.INPUT_IMG, source());
    op.process();
    long revision = op.getRevision();
    assertEquals(revision, op.getRevision(), "still no mask: no change");

    when(image.getTagValue(TagW.RedactionMask))
        .thenReturn(new Redaction.Mask(List.of(new Rectangle2D.Double(0, 0, 4, 4))));
    assertNotEquals(revision, op.getRevision(), "no parameter was set");
  }

  @Test
  void redaction_off_does_not_resolve_the_mask() {
    ImageElement image = mock(ImageElement.class);
    RedactionOp op = new RedactionOp();
    op.setParam(RedactionOp.P_IMAGE, image);
    op.setParam(RedactionOp.P_SHOW, false);
    long revision = op.getRevision();
    when(image.getTagValue(TagW.RedactionMask))
        .thenReturn(new Redaction.Mask(List.of(new Rectangle2D.Double(0, 0, 4, 4))));
    assertEquals(revision, op.getRevision());
  }
}
