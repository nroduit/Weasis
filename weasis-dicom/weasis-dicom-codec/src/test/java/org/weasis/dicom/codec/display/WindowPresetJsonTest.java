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

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.weasis.dicom.codec.display.WindowPreset.Domain;
import org.weasis.dicom.codec.display.WindowPreset.DomainKind;
import org.weasis.dicom.codec.display.WindowPreset.When;
import org.weasis.dicom.ref.AnatomicRegion;
import org.weasis.dicom.ref.BodyPart;
import org.weasis.dicom.ref.BodyPartTerm;
import org.weasis.opencv.op.lut.LutShape;

class WindowPresetJsonTest {

  @Test
  void readsEveryFieldOfAPreset() {
    List<WindowPreset> presets =
        read(
            """
            {"schema": 1, "presets": [{
              "id": "site.ct.lung", "name": "Lung", "modality": ["CT", "PT"],
              "category": "Chest", "tags": ["thorax"], "hidden": true,
              "window": "1500", "level": -500,
              "domain": {"kind": "absolute", "unit": "HU"},
              "shape": "sigmoid-norm", "key": "7", "unknown": 1}]}
            """);

    WindowPreset p = presets.getFirst();
    assertAll(
        () -> assertEquals("site.ct.lung", p.id()),
        () -> assertEquals(Set.of("CT", "PT"), p.modalities()),
        () -> assertEquals("Chest", p.category()),
        () -> assertEquals(List.of("thorax"), p.tags()),
        () -> assertTrue(p.hidden()),
        () -> assertEquals(1500.0, p.window()),
        () -> assertEquals(-500.0, p.level()),
        () -> assertEquals("HU", p.domain().unit()),
        () -> assertEquals(LutShape.SIGMOID_NORM, p.shape()),
        () -> assertEquals('7', p.key()),
        () -> assertEquals(55, p.keyCode()));
  }

  @Test
  void normalizesModalityCodesAndReadsLinearExact() {
    WindowPreset p =
        read("""
                [{"id": "a", "name": "A", "modality": [" ct", "Mr"], "window": 10, "level": 5,
                  "shape": "linear-exact"}]
                """)
            .getFirst();

    assertAll(
        () -> assertEquals(Set.of("CT", "MR"), p.modalities(), "CS values are upper case"),
        () -> assertTrue(p.appliesTo("CT")),
        () -> assertEquals(LutShape.LINEAR_EXACT, p.shape()),
        () -> assertEquals("linear-exact", WindowPresetJson.toJson(p).getString("shape")));
  }

  @Test
  void readsAndWritesPreferredBodyParts() {
    WindowPreset p =
        read("""
                [{"id": "a", "name": "A", "window": 10, "level": 5,
                  "when": {"preferBodyPart": ["chest", "LUNG"]}}]
                """)
            .getFirst();

    assertAll(
        () -> assertEquals(Set.of("CHEST", "LUNG"), p.when().preferredBodyParts()),
        () -> assertEquals(Set.of(), p.when().bodyParts(), "not a filter"),
        () -> assertTrue(p.when().matches(12, false, region(BodyPart.HEAD))),
        () -> assertTrue(p.when().prefers(region(BodyPart.CHEST_ABDOMEN_AND_PELVIS)), "group"),
        () -> assertTrue(p.when().prefers(region(BodyPart.LUNG))),
        () -> assertFalse(p.when().prefers(region(BodyPart.LIVER))),
        () -> assertFalse(p.when().prefers(null)),
        () -> assertEquals(p, WindowPresetJson.fromJson(WindowPresetJson.toJson(p))));
  }

  @Test
  void appliesDefaultsAndSkipsInvalidPresets() {
    List<WindowPreset> presets =
        read(
            """
            [{"id": "a", "name": "A", "window": 10, "level": 5, "shape": "spiral", "key": "1"},
             {"id": "b", "name": "B", "window": 0, "level": 5},
             {"name": "no id", "window": 10, "level": 5},
             {"id": "c", "name": "C", "window": 10, "level": 5, "domain": {"kind": "percent", "reference": "image"}, "key": "x"}]
            """);

    assertEquals(2, presets.size());
    WindowPreset a = presets.getFirst();
    assertAll(
        () -> assertTrue(a.appliesTo("MR")),
        () -> assertEquals(LutShape.LINEAR, a.shape()),
        () -> assertNull(a.key(), "0, 1 and 2 are reserved"),
        () -> assertEquals(Domain.ABSOLUTE, a.domain()),
        () -> assertEquals(DomainKind.PERCENT, presets.get(1).domain().kind()),
        () -> assertNull(presets.get(1).key(), "letters are viewer and tool shortcuts"));
  }

  @Test
  void readsConditionsDefaultAndPercentReference() {
    List<WindowPreset> presets =
        read(
            """
            [{"id": "a", "name": "A", "window": 60, "level": 40, "default": true,
              "domain": {"kind": "percent"},
              "when": {"minBitsStored": 12, "requiresRescale": true, "bodyPart": ["head", " Neck "]}},
             {"id": "b", "name": "B", "window": 60, "level": 40, "domain": {"kind": "percent", "reference": "study"}},
             {"id": "c", "name": "C", "window": 60, "level": 40, "when": {}}]
            """);

    assertEquals(2, presets.size(), "unknown percent reference is refused");
    WindowPreset a = presets.getFirst();
    assertAll(
        () -> assertTrue(a.defaultPreset()),
        () -> assertTrue(a.isPercent()),
        () -> assertEquals(WindowPreset.REFERENCE_IMAGE, a.domain().reference()),
        () -> assertEquals(new When(12, true, Set.of("HEAD", "NECK")), a.when()),
        () -> assertEquals(When.DEFAULT, presets.get(1).when()));
  }

  @Test
  void conditionsMatchBitsRescaleAndBodyPart() {
    When when = new When(12, true, Set.of("head"));
    assertAll(
        () -> assertTrue(when.matches(12, true, region(BodyPart.HEAD))),
        () -> assertTrue(when.matches(12, true, region(BodyPart.BRAIN)), "HEAD is a group"),
        () -> assertFalse(when.matches(8, true, region(BodyPart.HEAD))),
        () -> assertFalse(when.matches(16, false, region(BodyPart.HEAD))),
        () -> assertFalse(when.matches(16, true, region(BodyPart.CHEST))),
        () -> assertFalse(when.matches(16, true, null)),
        () -> assertTrue(When.DEFAULT.matches(9, false, null)),
        () -> assertFalse(When.DEFAULT.matches(8, true, region(BodyPart.HEAD))));
  }

  @Test
  void anatomyTokensAreCodesTermsGroupsOrLocalTerms() {
    When code = new When(1, false, Set.of("SCT:10200004"));
    When term = new When(1, false, Set.of("THORAX"));
    When local = new When(1, false, Set.of("tete"));
    assertAll(
        () -> assertTrue(code.matches(12, false, region(BodyPart.LIVER))),
        () -> assertFalse(code.matches(12, false, region(BodyPart.ABDOMEN))),
        () -> assertTrue(term.matches(12, false, region(BodyPart.CHEST)), "term of Table L-1"),
        () -> assertFalse(term.matches(12, false, region(BodyPart.LUNG))),
        () -> assertTrue(local.matches(12, false, new AnatomicRegion(new BodyPartTerm("TETE")))),
        () -> assertFalse(local.matches(12, false, region(BodyPart.HEAD))));
  }

  private static AnatomicRegion region(BodyPart part) {
    return new AnatomicRegion(part);
  }

  @Test
  void percentPresetResolvesOnTheRange() {
    WindowPreset preset =
        new WindowPreset(
            "p",
            "P",
            null,
            null,
            null,
            false,
            50,
            25,
            new Domain(DomainKind.PERCENT, null, null),
            null,
            '5',
            null,
            false);

    var resolved = preset.resolve(-1000, 3000);
    assertAll(
        () -> assertEquals(2000.0, resolved.getWindow()),
        () -> assertEquals(0.0, resolved.getLevel()),
        () -> assertEquals("p", resolved.getId()),
        () -> assertEquals('5', resolved.getKeyCode()),
        () -> assertNull(preset.resolve(100, 100), "empty range"));
  }

  @Test
  void refusesANewerSchema(@TempDir Path dir) throws IOException {
    String json = "{\"schema\": 2, \"presets\": []}";
    Path file = Files.writeString(dir.resolve("newer.json"), json);

    assertAll(
        () -> assertThrows(IllegalArgumentException.class, () -> read(json)),
        () -> assertThrows(IOException.class, () -> WindowPresetJson.read(file)));
  }

  @Test
  void readsASingleStringAsAOneItemList() {
    WindowPreset p =
        read("""
                [{"id": "a", "name": "A", "modality": "CT", "window": 10, "level": 5,
                  "when": {"bodyPart": "HEAD", "preferBodyPart": 3}}]
                """)
            .getFirst();

    assertAll(
        () -> assertEquals(Set.of("CT"), p.modalities()),
        () -> assertFalse(p.appliesTo("MR")),
        () -> assertEquals(Set.of("HEAD"), p.when().bodyParts()),
        () -> assertEquals(Set.of(), p.when().preferredBodyParts(), "a number is ignored"));
  }

  @Test
  void writesAndReadsBackTheSamePresets(@TempDir Path dir) throws IOException {
    List<WindowPreset> presets =
        List.of(
            new WindowPreset(
                "user.ct.liver",
                "Liver",
                Set.of("CT"),
                "Abdomen",
                List.of("liver"),
                false,
                150.5,
                60,
                new Domain(DomainKind.ABSOLUTE, "HU", null),
                LutShape.SIGMOID,
                '9',
                new When(12, true, Set.of("abdomen")),
                false),
            new WindowPreset(
                "user.mr.bright",
                "Bright",
                Set.of("MR"),
                null,
                null,
                false,
                60,
                45,
                new Domain(DomainKind.PERCENT, null, WindowPreset.REFERENCE_SERIES),
                null,
                null,
                null,
                true),
            new WindowPreset(
                "user.all.wide",
                "Wide",
                null,
                null,
                null,
                true,
                4000,
                0,
                null,
                null,
                null,
                null,
                false));
    Path file = dir.resolve("presets.json");

    WindowPresetJson.write(file, presets);

    assertEquals(presets, WindowPresetJson.read(file));
    String text = Files.readString(file);
    assertTrue(text.contains("\"level\": 60") || text.contains("\"level\":60"), text);
  }

  private static List<WindowPreset> read(String json) {
    return WindowPresetJson.read(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));
  }
}
