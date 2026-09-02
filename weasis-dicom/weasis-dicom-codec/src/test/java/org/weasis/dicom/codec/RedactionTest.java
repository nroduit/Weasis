/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.codec;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.geom.Ellipse2D;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.weasis.core.api.image.ImageOpEvent;
import org.weasis.core.api.image.ImageOpEvent.OpEvent;
import org.weasis.core.api.image.SimpleOpManager;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.media.data.TagReadable;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.api.media.data.Taggable;
import org.weasis.dicom.codec.Redaction.Mask;
import org.weasis.dicom.codec.display.RedactionOp;

@DisplayName("Redaction")
class RedactionTest {

  /** Minimal tag store: the real elements need a reader and pixel data we do not have here. */
  private static <T> T taggable(Class<T> type) {
    Map<TagW, Object> tags = new HashMap<>();
    T mock = mock(type);
    doAnswer(
            inv -> {
              tags.put(inv.getArgument(0), inv.getArgument(1));
              return null;
            })
        .when((Taggable) mock)
        .setTag(any(), any());
    when(((TagReadable) mock).getTagValue(any())).thenAnswer(inv -> tags.get(inv.getArgument(0)));
    return mock;
  }

  private static Mask mask(int x) {
    return new Mask(List.of(new Rectangle(x, 0, 10, 10)));
  }

  private static ImageElement withMask(Mask mask) {
    ImageElement image = taggable(ImageElement.class);
    Redaction.write(image, mask);
    return image;
  }

  @Test
  @DisplayName("nothing stored means no mask")
  void readsNullWhenUnset() {
    assertAll(
        () -> assertNull(Redaction.read(taggable(ImageElement.class))),
        () -> assertNull(Redaction.read(null)),
        () -> assertNull(Redaction.effective(null, null)));
  }

  @Test
  @DisplayName("a stored mask round-trips")
  void writeThenRead() {
    ImageElement image = taggable(ImageElement.class);
    Mask mask = mask(0);

    Redaction.write(image, mask);

    assertSame(mask, Redaction.read(image));
  }

  @Test
  @DisplayName("the series mask applies when the image has none")
  void seriesScopeAppliesToImage() {
    ImageElement image = taggable(ImageElement.class);
    MediaSeries<?> series = taggable(MediaSeries.class);
    Mask seriesMask = mask(5);

    Redaction.write(series, seriesMask);

    assertSame(seriesMask, Redaction.effective(image, series));
  }

  @Test
  @DisplayName("the image mask wins over the series mask")
  void imageScopeOverridesSeries() {
    ImageElement image = taggable(ImageElement.class);
    MediaSeries<?> series = taggable(MediaSeries.class);
    Mask imageMask = mask(1);

    Redaction.write(series, mask(5));
    Redaction.write(image, imageMask);

    assertSame(imageMask, Redaction.effective(image, series));
  }

  @Test
  @DisplayName("adding shapes keeps the previous ones, each as drawn")
  void addAccumulatesShapes() {
    ImageElement image = taggable(ImageElement.class);
    Ellipse2D ellipse = new Ellipse2D.Double(10, 10, 20, 8);

    Redaction.add(image, new Rectangle(0, 0, 5, 5));
    Redaction.add(image, ellipse);

    List<Shape> shapes = Redaction.read(image).shapes();
    assertAll(
        () -> assertEquals(2, shapes.size()),
        () -> assertSame(ellipse, shapes.get(1), "the shape is kept, not its bounds"));
  }

  @Test
  @DisplayName("a shape is required and a mask is immutable")
  void modelInvariants() {
    Mask mask = mask(0);
    ImageElement image = taggable(ImageElement.class);
    assertAll(
        () -> assertThrows(NullPointerException.class, () -> Redaction.add(image, null)),
        () ->
            assertThrows(
                UnsupportedOperationException.class,
                () -> mask.shapes().add(new Rectangle(1, 1, 1, 1))),
        () -> assertTrue(new Mask(List.of()).isEmpty()),
        () -> assertFalse(mask.isEmpty()));
  }

  @Test
  @DisplayName("no mask means no operations to apply")
  void opManagerIsNullWithoutMask() {
    assertAll(
        () -> assertNull(Redaction.opManagerFor(null)),
        () -> assertNull(Redaction.opManagerFor(taggable(ImageElement.class))),
        () ->
            assertNull(
                Redaction.opManagerFor(withMask(new Mask(List.of()))),
                "an empty region list is nothing to burn"));
  }

  @Test
  @DisplayName("a mask yields operations carrying it")
  void opManagerCarriesTheMask() {
    Mask mask = mask(3);
    SimpleOpManager manager = Redaction.opManagerFor(withMask(mask));

    assertAll(
        () -> assertNotNull(manager),
        () ->
            assertEquals(
                Optional.of(mask), manager.getParamValue(RedactionOp.OP_NAME, RedactionOp.P_MASK)));
  }

  @Test
  @DisplayName("an export pipeline gets the burn operation only when regions exist")
  void addToAppendsOnlyWithRegions() {
    SimpleOpManager empty = new SimpleOpManager();
    SimpleOpManager withRegions = new SimpleOpManager();
    MediaSeries<?> series = taggable(MediaSeries.class);
    Mask seriesMask = mask(7);
    Redaction.write(series, seriesMask);

    boolean addedWithout = Redaction.addTo(empty, taggable(ImageElement.class), null);
    boolean addedWith = Redaction.addTo(withRegions, taggable(ImageElement.class), series);

    assertAll(
        () -> assertFalse(addedWithout),
        () ->
            assertEquals(
                Optional.empty(), empty.getParamValue(RedactionOp.OP_NAME, RedactionOp.P_MASK)),
        () -> assertTrue(addedWith),
        () ->
            assertEquals(
                Optional.of(seriesMask),
                withRegions.getParamValue(RedactionOp.OP_NAME, RedactionOp.P_MASK)));
  }

  @Test
  @DisplayName("the view operation picks up the regions when the image changes")
  void opFollowsImageChanges() {
    ImageElement image = taggable(ImageElement.class);
    MediaSeries<ImageElement> series = taggable(MediaSeries.class);
    Mask seriesMask = mask(4);
    Redaction.write(series, seriesMask);
    RedactionOp op = new RedactionOp();

    op.handleImageOpEvent(new ImageOpEvent(OpEvent.IMAGE_CHANGE, series, image, null));
    Object afterChange = op.getParam(RedactionOp.P_MASK);
    op.handleImageOpEvent(new ImageOpEvent(OpEvent.RESET_DISPLAY, null, null, null));

    assertAll(
        () -> assertSame(seriesMask, afterChange, "the series regions apply to the new image"),
        () -> assertNull(op.getParam(RedactionOp.P_MASK), "no image, no regions"),
        () -> assertSame(RedactionOp.class, op.copy().getClass()));
  }

  @Test
  @DisplayName("clearing removes the mask")
  void clearRemoves() {
    ImageElement image = taggable(ImageElement.class);
    Redaction.write(image, mask(0));

    Redaction.clear(image);

    assertNull(Redaction.read(image));
  }
}
