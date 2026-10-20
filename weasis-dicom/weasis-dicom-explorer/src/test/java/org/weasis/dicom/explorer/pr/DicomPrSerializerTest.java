/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.explorer.pr;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;

@DisplayNameGeneration(ReplaceUnderscores.class)
class DicomPrSerializerTest {

  private static Attributes image() {
    Attributes image = new Attributes();
    image.setInt(Tag.Columns, VR.US, 512);
    image.setInt(Tag.Rows, VR.US, 256);
    image.setDouble(Tag.PixelSpacing, VR.DS, 0.6, 0.4);
    return image;
  }

  private static Attributes displayedArea(Attributes image, double[] spacing) {
    Attributes pr = new Attributes();
    DicomPrSerializer.addDisplayedAreaSelectionSequence(pr, image, spacing);
    return pr.getNestedDataset(Tag.DisplayedAreaSelectionSequence);
  }

  @Test
  void displayed_area_is_the_whole_image() {
    Attributes area = displayedArea(image(), null);

    assertArrayEquals(new int[] {1, 1}, area.getInts(Tag.DisplayedAreaTopLeftHandCorner));
    assertArrayEquals(new int[] {512, 256}, area.getInts(Tag.DisplayedAreaBottomRightHandCorner));
    assertEquals("SCALE TO FIT", area.getString(Tag.PresentationSizeMode));
  }

  @Test
  void calibration_is_written_as_presentation_pixel_spacing() {
    Attributes area = displayedArea(image(), new double[] {0.3, 0.2});

    assertArrayEquals(new double[] {0.3, 0.2}, area.getDoubles(Tag.PresentationPixelSpacing));
    assertNull(area.getInts(Tag.PresentationPixelAspectRatio));
  }

  @Test
  void without_calibration_the_aspect_ratio_of_the_image_is_written() {
    Attributes area = displayedArea(image(), null);

    assertArrayEquals(new int[] {3, 2}, area.getInts(Tag.PresentationPixelAspectRatio));
    assertNull(area.getDoubles(Tag.PresentationPixelSpacing));
  }

  @Test
  void image_attributes_are_left_unchanged() {
    Attributes image = image();
    displayedArea(image, new double[] {0.3, 0.2});

    assertFalse(image.contains(Tag.PresentationPixelSpacing));
  }
}
