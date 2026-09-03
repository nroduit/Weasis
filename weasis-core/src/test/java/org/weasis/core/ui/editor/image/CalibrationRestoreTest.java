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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.weasis.core.api.image.util.Unit;
import org.weasis.core.api.media.data.ImageElement;

/** Removing the user's calibration gives the image back what it provides itself. */
@DisplayNameGeneration(ReplaceUnderscores.class)
class CalibrationRestoreTest {

  @Test
  void an_image_without_its_own_calibration_goes_back_to_pixels() {
    ImageElement image = mock(ImageElement.class, CALLS_REAL_METHODS);
    image.setPixelSize(0.25, 0.25);
    image.setPixelSpacingUnit(Unit.MILLIMETER);
    image.setPixelSizeCalibrationDescription("Modified by user"); // NON-NLS
    image.setPixelSizeModifiedByUser(true);

    image.initPixelConfiguration();

    assertAll(
        () -> assertEquals(1.0, image.getPixelSize()),
        () -> assertEquals(Unit.PIXEL, image.getPixelSpacingUnit()),
        () -> assertNull(image.getPixelSizeCalibrationDescription()),
        () -> assertFalse(image.isPixelSizeModifiedByUser()));
  }

  @Test
  void a_view_restores_the_calibration_of_the_image_by_default() {
    ViewCanvas<?> view = mock(ViewCanvas.class);
    ImageElement image = mock(ImageElement.class);
    doCallRealMethod().when(view).restoreCalibration(image);
    view.restoreCalibration(image);
    verify(image).initPixelConfiguration();
  }
}
