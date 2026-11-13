/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.rt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.opencv.data.ImageCV;
import org.weasis.opencv.natives.NativeLibrary;

@DisplayNameGeneration(ReplaceUnderscores.class)
class DoseMaskedHistogramTest {

  private static final int SIZE = 32;
  private static final int MAX_DOSE = 16;

  @BeforeAll
  static void load_opencv_native_library() {
    try {
      NativeLibrary.loadLibraryFromLibraryName();
      return;
    } catch (UnsatisfiedLinkError e) {
      // fall through to the sibling module
    }
    String arch = System.getProperty("os.arch");
    String cpu = "amd64".equals(arch) ? "x86-64" : "aarch64".equals(arch) ? "aarch64" : null;
    Path lib =
        cpu == null
            ? null
            : Path.of("..", "..", "weasis-opencv", "weasis-opencv-core-linux-" + cpu)
                .resolve(Path.of("target", "classes", "natives", "libopencv_java.so"));
    Assumptions.assumeTrue(
        lib != null && Files.isRegularFile(lib), "OpenCV native library not available");
    NativeLibrary.loadLibraryFromAbsolutePath(lib);
  }

  /**
   * The dose grid scaling of a mocked Dose is 0, so every masked pixel falls into bin 0 and the
   * histogram counts the pixels of the mask.
   */
  private static Dose doseWithPlane(Mat plane) {
    DicomImageElement element = mock(DicomImageElement.class);
    when(element.getImage()).thenReturn(ImageCV.fromMat(plane));
    Dose dose = mock(Dose.class, CALLS_REAL_METHODS);
    doReturn(element).when(dose).getDosePlaneBySlice(anyDouble());
    return dose;
  }

  private static Mat triangleMask() {
    Mat mask = Mat.zeros(SIZE, SIZE, CvType.CV_8UC1);
    var poly = new MatOfPoint(new Point(2, 2), new Point(28, 4), new Point(10, 27));
    Imgproc.fillPoly(mask, List.of(poly), new Scalar(255));
    poly.release();
    return mask;
  }

  @Test
  void byte_and_float_masks_give_the_same_histogram() {
    Mat plane = new Mat(SIZE, SIZE, CvType.CV_16UC1, new Scalar(1000));
    Dose dose = doseWithPlane(plane);
    Mat byteMask = triangleMask();
    Mat floatMask = new Mat();
    byteMask.convertTo(floatMask, CvType.CV_32FC1);
    int inside = Core.countNonZero(byteMask);

    Mat fromByte = dose.getMaskedDosePlaneHist(0.0, byteMask, MAX_DOSE);
    Mat fromFloat = dose.getMaskedDosePlaneHist(0.0, floatMask, MAX_DOSE);

    assertEquals(MAX_DOSE, fromByte.rows());
    assertEquals(1, fromByte.cols());
    assertEquals(inside, fromByte.get(0, 0)[0]);
    for (int i = 0; i < MAX_DOSE; i++) {
      assertEquals(fromFloat.get(i, 0)[0], fromByte.get(i, 0)[0], "bin " + i);
    }
    assertFalse(byteMask.empty(), "the caller's mask must not be released");
    assertEquals(inside, Core.countNonZero(byteMask));
  }
}
