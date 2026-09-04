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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.weasis.core.api.media.data.MaskRegion;
import org.weasis.core.api.media.data.MaskingProfile;
import org.weasis.core.api.media.data.PixelMask;
import org.weasis.core.api.media.data.PixelMask.DeviceKey;
import org.weasis.core.api.media.data.PixelMask.Reference;
import org.weasis.core.api.media.data.TagCategory;
import org.weasis.dicom.codec.KarnakMasks.Loss;

@DisplayName("Karnak mask interchange")
class KarnakMasksTest {

  @TempDir Path dir;

  private static PixelMask mask(DeviceKey key, MaskRegion... regions) {
    return new PixelMask(
        "us-banner", "US banner", key, new Reference(1024, 768), List.of(regions), List.of(), true);
  }

  @Test
  @DisplayName("a station entry becomes a masks block with absolute rectangles")
  void exportStationEntry() {
    List<Loss> losses = new ArrayList<>();
    String yaml =
        KarnakMasks.toYaml(
            List.of(
                mask(
                    new DeviceKey("US", "USVIVID01", null, null, null),
                    new MaskRegion.Rect(0, 0, 1, 0.0625, TagCategory.DIRECT_ID))),
            MaskingProfile.display(),
            KarnakMasks.BLACK,
            false,
            losses);

    assertAll(
        () -> assertTrue(yaml.contains("masks:")),
        () -> assertTrue(yaml.contains("- stationName: \"USVIVID01\"")),
        () -> assertTrue(yaml.contains("imageWidth: 1024")),
        () -> assertTrue(yaml.contains("imageHeight: 768")),
        () -> assertTrue(yaml.contains("color: \"000000\"")),
        () -> assertTrue(yaml.contains("- \"0 0 1024 48\""), yaml),
        () -> assertTrue(losses.isEmpty()));
  }

  @Test
  @DisplayName("what Karnak cannot express is reported")
  void losses() {
    List<Loss> losses = new ArrayList<>();
    String yaml =
        KarnakMasks.toYaml(
            List.of(
                mask(
                    new DeviceKey("US", "USVIVID01", "GE*", null, null),
                    new MaskRegion.Rect(0, 0, 1, 0.05, TagCategory.DIRECT_ID)),
                mask(
                    new DeviceKey(null, null, null, null, null),
                    new MaskRegion.Ellipse(0.1, 0.1, 0.2, 0.2, TagCategory.DIRECT_ID))),
            MaskingProfile.display(),
            null,
            false,
            losses);

    assertAll(
        () -> assertTrue(losses.stream().anyMatch(l -> l.message().contains("manufacturer"))),
        () -> assertTrue(losses.stream().anyMatch(l -> l.message().contains("bounding"))),
        () -> assertTrue(yaml.contains("- stationName: \"*\""), "no station means every station"),
        () -> assertFalse(yaml.contains("color:"), "no color makes Karnak blur"));
  }

  @Test
  @DisplayName("a region the profile keeps is not exported")
  void onlyWhatTheProfileHides() {
    List<Loss> losses = new ArrayList<>();
    String yaml =
        KarnakMasks.toYaml(
            List.of(
                mask(
                    new DeviceKey("US", "USVIVID01", null, null, null),
                    new MaskRegion.Rect(0, 0, 1, 0.05, TagCategory.DEVICE))),
            MaskingProfile.display(),
            KarnakMasks.BLACK,
            false,
            losses);

    assertAll(
        () -> assertFalse(yaml.contains("rectangles:")),
        () -> assertTrue(losses.stream().anyMatch(l -> l.message().contains("No region"))));
  }

  @Test
  @DisplayName("a complete profile is written when asked")
  void completeProfile() {
    String yaml =
        KarnakMasks.toYaml(
            List.of(
                mask(
                    new DeviceKey("US", "USVIVID01", null, null, null),
                    new MaskRegion.Rect(0, 0, 1, 0.05, TagCategory.DIRECT_ID))),
            MaskingProfile.publication(),
            KarnakMasks.BLACK,
            true,
            new ArrayList<>());

    assertAll(
        () -> assertTrue(yaml.contains("codename: \"clean.pixel.data\"")),
        () -> assertTrue(yaml.contains("minimumKarnakVersion")));
  }

  @Test
  @DisplayName("a Karnak masks block is read back, with and without an image size")
  void importBlock() throws IOException {
    Path file =
        Files.writeString(
            dir.resolve("profile.yml"),
            """
            name: "Clean pixel data"
            masks:
              - stationName: "*"
                color: "ff0000"
                rectangles:
                  - "25 15 150 50"
                  - "350 15 150 50"
              - stationName: "VIVID7-003467"
                imageWidth: 800
                imageHeight: 600
                rectangles:
                  - "0 0 800 40"
            """);

    List<PixelMask> masks = KarnakMasks.read(file, new Reference(1024, 768));
    PixelMask any = masks.getFirst();
    PixelMask station = masks.get(1);

    assertAll(
        () -> assertEquals(2, masks.size()),
        () -> assertEquals(2, any.regions().size()),
        () -> assertNull(any.match().stationName(), "a star matches every station"),
        () -> assertEquals(1024, any.reference().columns(), "the fallback size is used"),
        () -> assertEquals("VIVID7-003467", station.match().stationName()),
        () -> assertEquals(800, station.reference().columns()),
        () ->
            assertEquals(
                TagCategory.DIRECT_ID,
                station.regions().getFirst().category(),
                "Karnak has no categories: every region hides identity"),
        () ->
            assertEquals(
                1.0,
                ((MaskRegion.Rect) station.regions().getFirst()).w(),
                1e-6,
                "a full-width band stays full width"));
  }

  @Test
  @DisplayName("a masks block whose items sit at column 0 is read, and the next key ends it")
  void importUnindentedBlock() throws IOException {
    Path file =
        Files.writeString(
            dir.resolve("flat.yml"),
            """
            name: "Clean pixel data"
            masks:
            - stationName: "VIVID7"
              rectangles:
              - "0 0 800 40"
              - "0 40 800 20"
            - stationName: "US1"
              rectangles:
              - "0 0 10 10"
            profileElements:
            - name: "Anything else"
            """);

    List<PixelMask> masks = KarnakMasks.read(file, new Reference(800, 600));

    assertAll(
        () -> assertEquals(2, masks.size()),
        () -> assertEquals("VIVID7", masks.getFirst().match().stationName()),
        () -> assertEquals(2, masks.getFirst().regions().size()),
        () -> assertEquals("US1", masks.get(1).match().stationName()));
  }

  @Test
  @DisplayName("an unknown field is refused rather than guessed")
  void refuseUnknownField() throws IOException {
    Path file =
        Files.writeString(
            dir.resolve("unknown.yml"),
            """
            masks:
              - stationName: "US1"
                sopClassUID: "1.2.840.10008.5.1.4.1.1.6.1"
                rectangles:
                  - "0 0 10 10"
            """);

    assertThrows(IOException.class, () -> KarnakMasks.read(file, new Reference(1024, 768)));
  }

  @Test
  @DisplayName("a document without masks is refused")
  void refuseWithoutMasks() throws IOException {
    Path file = Files.writeString(dir.resolve("none.yml"), "name: \"Profile\"\n");
    assertThrows(IOException.class, () -> KarnakMasks.read(file, new Reference(1024, 768)));
  }

  private static void assertNull(Object value, String message) {
    org.junit.jupiter.api.Assertions.assertNull(value, message);
  }
}
