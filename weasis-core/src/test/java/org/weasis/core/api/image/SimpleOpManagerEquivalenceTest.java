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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Scalar;
import org.weasis.core.api.image.ImageOpNode.Param;
import org.weasis.core.api.image.cv.OpenCvTestLoader;
import org.weasis.core.api.image.util.KernelData;
import org.weasis.core.api.setting.SettingEnvironment;
import org.weasis.core.api.setting.Settings;
import org.weasis.opencv.data.ImageCV;
import org.weasis.opencv.data.PlanarImage;
import org.weasis.opencv.op.lut.ColorLut;

/** The tracked chain gives, pass after pass, the image a full run gives. */
@DisplayNameGeneration(ReplaceUnderscores.class)
class SimpleOpManagerEquivalenceTest {

  private static final int SIZE = 24;
  private static final List<KernelData> KERNELS =
      List.of(KernelData.NONE, KernelData.MEAN, KernelData.SHARPEN, KernelData.GAUSSIAN_3);
  private static final List<ColorLut> LUTS = List.of(ColorLut.IMAGE, ColorLut.HUE, ColorLut.RED);

  @BeforeAll
  static void loadOpenCV() {
    assumeTrue(OpenCvTestLoader.tryLoad(), "OpenCV native library unavailable, skipping");
  }

  @AfterEach
  void tearDown() {
    Settings.install(SettingEnvironment.inMemory(Map.of()));
  }

  private static ImageCV source(Random random) {
    ImageCV img = new ImageCV(SIZE, SIZE, CvType.CV_8UC1);
    Core.randu(img, 0, 256);
    img.put(0, 0, new byte[] {(byte) random.nextInt(256)});
    return img;
  }

  private static SimpleOpManager chain() {
    SimpleOpManager manager = new SimpleOpManager();
    manager.addImageOperationAction(new BrightnessOp());
    manager.addImageOperationAction(new FilterOp());
    manager.addImageOperationAction(new PseudoColorOp());
    manager.addImageOperationAction(new FlipOp());
    manager.addImageOperationAction(new RotationOp());
    manager.addImageOperationAction(new AffineTransformOp());
    return manager;
  }

  private static void change(SimpleOpManager manager, Random random) {
    switch (random.nextInt(9)) {
      case 0 ->
          manager.setParamValue(
              BrightnessOp.OP_NAME,
              BrightnessOp.P_BRIGHTNESS_VALUE,
              (double) random.nextInt(3) * 10);
      case 1 ->
          manager.setParamValue(
              FilterOp.OP_NAME,
              FilterOp.P_KERNEL_DATA,
              KERNELS.get(random.nextInt(KERNELS.size())));
      case 2 ->
          manager.setParamValue(
              PseudoColorOp.OP_NAME,
              PseudoColorOp.P_LUT,
              LUTS.get(random.nextInt(LUTS.size())).getByteLut());
      case 3 ->
          manager.setParamValue(
              PseudoColorOp.OP_NAME, PseudoColorOp.P_LUT_INVERSE, random.nextBoolean());
      case 4 -> manager.setParamValue(FlipOp.OP_NAME, FlipOp.P_FLIP, random.nextBoolean());
      case 5 ->
          manager.setParamValue(RotationOp.OP_NAME, RotationOp.P_ROTATE, 90 * random.nextInt(4));
      case 6 -> {
        double zoom = 1 + random.nextInt(3) * 0.5;
        double shift = random.nextInt(3);
        manager.setParamValue(
            AffineTransformOp.OP_NAME,
            AffineTransformOp.P_AFFINE_MATRIX,
            List.of(zoom, 0.0, shift, 0.0, zoom, shift));
        manager.setParamValue(
            AffineTransformOp.OP_NAME,
            AffineTransformOp.P_DST_BOUNDS,
            new Rectangle2D.Double(0, 0, SIZE * zoom, SIZE * zoom));
      }
      case 7 -> {
        String op = manager.getOperations().get(random.nextInt(6)).getName();
        manager.getNode(op).orElseThrow().setEnabled(random.nextBoolean());
      }
      default -> {
        // A redundant pass: nothing changed
      }
    }
  }

  private static PlanarImage fullRun(SimpleOpManager tracked, PlanarImage source) {
    SimpleOpManager full = tracked.copy();
    full.setFirstNode(source);
    return full.process().orElse(null);
  }

  @Test
  void random_changes_give_the_image_of_a_full_run() {
    Random random = new Random(20261017);
    SimpleOpManager tracked = chain();
    PlanarImage source = source(random);
    tracked.setFirstNode(source);
    int skippedPasses = 0;
    for (int step = 0; step < 400; step++) {
      if (random.nextInt(20) == 0) {
        source = source(random);
        tracked.setFirstNode(source);
      }
      change(tracked, random);
      List<PlanarImage> outputsBefore = outputs(tracked);
      PlanarImage result = tracked.process().orElse(null);
      if (outputs(tracked).equals(outputsBefore)) {
        skippedPasses++;
      }
      assertTrue(SimpleOpManager.isSameImage(result, fullRun(tracked, source)), "step " + step);
    }
    assertTrue(skippedPasses > 0, "some passes reused every output");
  }

  private static List<PlanarImage> outputs(SimpleOpManager manager) {
    List<PlanarImage> list = new ArrayList<>();
    for (ImageOpNode node : manager.getOperations()) {
      list.add((PlanarImage) node.getParam(Param.OUTPUT_IMG));
    }
    return list;
  }

  /** Reads a value outside its parameters and does not call invalidate(): a broken plugin op. */
  static final class HiddenInputOp extends AbstractOp {
    static double fill;

    HiddenInputOp() {
      setName("hidden");
    }

    HiddenInputOp(HiddenInputOp op) {
      super(op);
    }

    @Override
    public void process() {
      PlanarImage source = getSourceImage();
      ImageCV result = new ImageCV(source.height(), source.width(), source.type());
      result.setTo(new Scalar(fill));
      params.put(Param.OUTPUT_IMG, result);
    }

    @Override
    public HiddenInputOp copy() {
      return new HiddenInputOp(this);
    }
  }

  @Test
  void verify_mode_catches_a_hidden_input_and_shows_the_right_image() {
    SimpleOpManager manager = new SimpleOpManager();
    manager.addImageOperationAction(new HiddenInputOp());
    manager.addImageOperationAction(new FlipOp());
    manager.setFirstNode(source(new Random(1)));
    HiddenInputOp.fill = 10;
    manager.process();

    HiddenInputOp.fill = 20;
    manager.setParamValue(FlipOp.OP_NAME, FlipOp.P_FLIP, true);
    assertEquals(10.0, manager.process().orElseThrow().get(0, 0)[0], "tracked: the stale image");

    Settings.install(SettingEnvironment.inMemory(Map.of("weasis.display.op.verify", "true")));
    HiddenInputOp.fill = 30;
    manager.setParamValue(FlipOp.OP_NAME, FlipOp.P_FLIP, false);
    assertEquals(30.0, manager.process().orElseThrow().get(0, 0)[0], "verified: run again");
  }
}
