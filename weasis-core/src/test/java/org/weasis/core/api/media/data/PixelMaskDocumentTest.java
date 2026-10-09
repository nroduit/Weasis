/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.api.media.data;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.weasis.core.api.media.data.PixelMask.DeviceKey;
import org.weasis.core.api.media.data.PixelMask.Reference;
import org.weasis.core.api.util.LayeredEntries.Origin;

@DisplayName("Pixel masks in the masking document")
class PixelMaskDocumentTest {

  @TempDir Path dir;

  private static PixelMask mask(String id, boolean enabled) {
    return new PixelMask(
        id,
        "US banner",
        new DeviceKey("US", "USVIVID01", "GE*", null, null),
        new Reference(1024, 768),
        List.of(
            new MaskRegion.Rect(0, 0, 1, 0.05, TagCategory.DIRECT_ID),
            new MaskRegion.Ellipse(0.1, 0.2, 0.3, 0.4, TagCategory.INSTITUTION),
            new MaskRegion.Polygon(List.of(0.1, 0.1, 0.5, 0.1, 0.5, 0.4), TagCategory.FREE_TEXT)),
        List.of(MaskingProfile.PUBLICATION_ID),
        enabled);
  }

  @Test
  @DisplayName("an entry read back is the one written")
  void roundTrip() throws IOException {
    Path file = dir.resolve("masking.json");
    MaskingModel.EMPTY.withMasks(List.of(mask("us-banner", true))).write(file);

    MaskingModel read = MaskingModel.read(file);

    assertAll(
        () -> assertEquals(1, read.masks().size()),
        () -> assertEquals(mask("us-banner", true), read.masks().getFirst()),
        () -> assertTrue(Files.readString(file).contains("\"masks\"")));
  }

  @Test
  @DisplayName("a region of unknown type or an entry without a reference is skipped")
  void invalidEntries() throws IOException {
    Path file =
        Files.writeString(
            dir.resolve("invalid.json"),
            """
            { "masks": [
                { "id": "no-reference", "regions": [] },
                { "id": "bad-region", "reference": { "columns": 800, "rows": 600 },
                  "regions": [ { "type": "star", "x": 0, "y": 0, "w": 1, "h": 1 },
                               { "type": "rect", "x": 0, "y": 0, "w": 1, "h": 0.1 } ] } ] }
            """);

    List<PixelMask> masks = MaskingModel.read(file).masks();

    assertAll(
        () -> assertEquals(1, masks.size(), "the entry without a reference size is dropped"),
        () -> assertEquals("bad-region", masks.getFirst().id()),
        () -> assertEquals(1, masks.getFirst().regions().size()),
        () ->
            assertEquals(
                TagCategory.DIRECT_ID,
                masks.getFirst().regions().getFirst().category(),
                "a region without a category hides identity"));
  }

  @Test
  @DisplayName("a drawn shape is normalized and scaled back")
  void geometry() {
    MaskRegion rect =
        MaskRegion.of(new Rectangle2D.Double(102.4, 76.8, 512, 384), 1024, 768, TagCategory.DATE);
    MaskRegion ellipse =
        MaskRegion.of(new Ellipse2D.Double(0, 0, 512, 384), 1024, 768, TagCategory.DEVICE);
    Path2D triangle = new Path2D.Double();
    triangle.moveTo(0, 0);
    triangle.lineTo(512, 0);
    triangle.lineTo(0, 384);
    triangle.closePath();
    MaskRegion polygon = MaskRegion.of(triangle, 1024, 768, TagCategory.FREE_TEXT);

    Rectangle2D scaled = rect.toShape(2048, 1536).getBounds2D();
    assertAll(
        () -> assertInstanceOf(MaskRegion.Rect.class, rect),
        () -> assertInstanceOf(MaskRegion.Ellipse.class, ellipse),
        () -> assertInstanceOf(MaskRegion.Polygon.class, polygon),
        () -> assertEquals(0.1, ((MaskRegion.Rect) rect).x(), 1e-6),
        () -> assertEquals(204.8, scaled.getX(), 1e-3, "a region follows the image size"),
        () -> assertEquals(1024, scaled.getWidth(), 1e-3),
        () -> assertEquals(0.5, polygon.bounds().getWidth(), 1e-6),
        () ->
            assertThrows(
                IllegalArgumentException.class,
                () -> MaskRegion.of(triangle, 0, 768, TagCategory.OTHER)));
  }

  @Test
  @DisplayName("a user entry overrides a site one and is saved to the user document")
  void registryLayers() throws IOException {
    Path site =
        Files.writeString(
            dir.resolve("site.json"),
            """
            { "masks": [ { "id": "us-banner", "name": "Site banner",
                  "match": { "modality": "US" },
                  "reference": { "columns": 1024, "rows": 768 },
                  "regions": [ { "type": "rect", "x": 0, "y": 0, "w": 1, "h": 0.1 } ] } ] }
            """);
    Path userFile = dir.resolve("identityMasking.json");
    MaskingModelRegistry registry = new MaskingModelRegistry(MaskingModelRegistry.loadBuiltIn());
    registry.configure(site, userFile);

    Origin fromSite = registry.masks().origin("us-banner");
    registry.saveUserMask(mask("us-banner", false));

    assertAll(
        () -> assertEquals(Origin.SITE, fromSite),
        () -> assertEquals(Origin.USER, registry.masks().origin("us-banner")),
        () -> assertFalse(registry.pixelMask("us-banner").orElseThrow().enabled()),
        () -> assertEquals(1, MaskingModel.read(userFile).masks().size()),
        () -> assertTrue(registry.deleteUserMask("us-banner")),
        () ->
            assertEquals(
                Origin.SITE, registry.masks().origin("us-banner"), "the site entry is back"));
  }

  @Test
  @DisplayName("a device key matches by prefix, and the most specific one is the longest")
  void deviceKey() {
    DeviceKey precise = new DeviceKey("US", "USVIVID01", "GE*", null, null);
    DeviceKey wide = new DeviceKey("US", null, null, null, null);

    assertAll(
        () -> assertTrue(precise.matches("us", " usvivid01 ", "GE Healthcare", "E95", "Site")),
        () -> assertFalse(precise.matches("US", "USVIVID02", "GE Healthcare", "E95", null)),
        () ->
            assertFalse(precise.matches("US", "USVIVID01", null, null, null), "no value, no match"),
        () -> assertTrue(wide.matches("US", null, null, null, null)),
        () -> assertEquals(3, precise.specificity()),
        () -> assertEquals(1, wide.specificity()),
        () -> assertTrue(mask("x", true).appliesTo(MaskingProfile.PUBLICATION_ID)),
        () -> assertFalse(mask("x", true).appliesTo(MaskingProfile.DISPLAY_ID)));
  }
}
