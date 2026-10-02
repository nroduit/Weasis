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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.dcm4che3.img.DicomImageAdapter;
import org.dcm4che3.img.lut.ModalityLutModule;
import org.dcm4che3.img.lut.PresetWindowLevel;
import org.dcm4che3.img.stream.ImageDescriptor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.opencv.core.Core.MinMaxLocResult;
import org.weasis.dicom.ref.AnatomicRegion;
import org.weasis.dicom.ref.BodyPart;

class WindowPresetRegistryTest {

  @Test
  void builtInPresetsKeepTheEightBitRuleAndAbsoluteInstancesAreShared() {
    WindowPresetRegistry registry = new WindowPresetRegistry(WindowPresetRegistry.loadBuiltIn());

    List<PresetWindowLevel> ct12 = registry.getPresets(image("CT", 12), null);
    assertAll(
        () -> assertEquals(12, ct12.size()),
        () -> assertEquals("weasis.ct.lung", ct12.get(6).getId()),
        () -> assertEquals(-600.0, ct12.get(6).getLevel()),
        () -> assertEquals('7', ct12.get(6).getKeyCode()),
        () -> assertFalse(ct12.get(6).isFallbackDefault()),
        () -> assertEquals(0, ct12.get(1).getKeyCode(), "stroke has no shortcut"),
        () -> assertTrue(registry.getPresets(image("CT", 8), null).isEmpty()),
        () -> assertTrue(registry.getPresets(image("MR", 16), null).isEmpty()),
        () -> assertSame(ct12.get(0), registry.getPresets(image("CT", 12), null).get(0)));
  }

  @Test
  void presetsOfTheBodyPartAreListedFirstWithoutHidingTheOthers() {
    WindowPresetRegistry registry = new WindowPresetRegistry(WindowPresetRegistry.loadBuiltIn());

    List<String> chest =
        ids(registry.getPresets(image("CT", 12, true, BodyPart.CHEST, 0, 1), null));
    List<String> none = ids(registry.getPresets(image("CT", 12, true, null, 0, 1), null));
    List<PresetWindowLevel> head =
        registry.getPresets(image("CT", 12, true, BodyPart.HEAD, 0, 1), null);

    assertAll(
        () -> assertEquals(List.of("weasis.ct.mediastinum", "weasis.ct.lung"), chest.subList(0, 2)),
        () -> assertEquals(12, chest.size(), "nothing hidden"),
        () -> assertEquals("weasis.ct.brain", none.getFirst(), "document order without body part"),
        () -> assertEquals(12, none.size()),
        () -> assertEquals("weasis.ct.head-neck", head.get(4).getId()),
        () ->
            assertEquals(
                '7',
                head.stream()
                    .filter(p -> "weasis.ct.lung".equals(p.getId()))
                    .findFirst()
                    .orElseThrow()
                    .getKeyCode(),
                "keys follow the presets"));
  }

  @Test
  void presetsFollowTheRegionGroupsOfTheAnatomy() {
    WindowPresetRegistry registry = new WindowPresetRegistry(WindowPresetRegistry.loadBuiltIn());

    List<String> heart =
        ids(registry.getPresets(image("CT", 12, true, BodyPart.HEART, 0, 1), null));
    List<String> cSpine =
        ids(registry.getPresets(image("CT", 12, true, BodyPart.CERVICAL_SPINE, 0, 1), null));

    assertAll(
        () ->
            assertEquals(
                List.of("weasis.ct.mediastinum", "weasis.ct.lung"),
                heart.subList(0, 2),
                "the heart lies in the CHEST group"),
        () ->
            assertEquals(
                List.of("weasis.ct.head-neck", "weasis.ct.spine"),
                cSpine.subList(0, 2),
                "the cervical spine lies in NECK and SPINE"));
  }

  @Test
  void laterLayersOverrideByIdAndUserFileIsRead(@TempDir Path dir) throws IOException {
    Path site = dir.resolve("site.json");
    Files.writeString(
        site,
        """
        {"schema": 1, "presets": [
          {"id": "weasis.ct.lung", "name": "Lung", "modality": ["CT"], "window": 1600, "level": -600, "key": "7"},
          {"id": "site.ct.liver", "name": "Liver", "modality": ["CT"], "window": 150, "level": 60, "key": "7"},
          {"id": "site.ct.hidden", "name": "Hidden", "modality": ["CT"], "window": 10, "level": 5, "hidden": true}
        ]}
        """);
    Path user = dir.resolve(WindowPresetRegistry.USER_FILE);
    Files.writeString(
        user,
        """
        {"schema": 1, "presets": [
          {"id": "site.ct.liver", "name": "My liver", "modality": ["CT"], "window": 180, "level": 70}
        ]}
        """);
    WindowPresetRegistry registry = new WindowPresetRegistry(WindowPresetRegistry.loadBuiltIn());
    AtomicInteger notified = new AtomicInteger();
    registry.addListener(notified::incrementAndGet);

    registry.configure(site, user);

    List<PresetWindowLevel> ct = registry.getPresets(image("CT", 12), null);
    PresetWindowLevel lung = ct.get(6);
    PresetWindowLevel liver = ct.get(12);
    assertAll(
        () -> assertEquals(1, notified.get()),
        () -> assertEquals(13, ct.size(), "hidden preset not offered"),
        () -> assertEquals(1600.0, lung.getWindow(), "site shadows the built-in in place"),
        () -> assertEquals("My liver", liver.getName(), "user shadows the site"),
        () -> assertEquals(0, liver.getKeyCode()),
        () -> assertTrue(registry.find("site.ct.hidden").isPresent()));
  }

  @Test
  void conditionsFilterEachImage() {
    WindowPresetRegistry registry =
        registryOf(
            """
            [{"id": "sc", "name": "Any depth", "modality": ["CT"], "window": 400, "level": 40,
              "when": {"minBitsStored": 1}},
             {"id": "rescaled", "name": "Rescaled", "modality": ["CT"], "window": 400, "level": 40,
              "when": {"minBitsStored": 1, "requiresRescale": true}},
             {"id": "head", "name": "Head", "modality": ["CT"], "window": 80, "level": 40,
              "when": {"bodyPart": ["HEAD"]}}]
            """);

    assertAll(
        () -> assertEquals(ids("sc"), ids(registry.getPresets(image("CT", 8), null))),
        () ->
            assertEquals(
                ids("sc", "rescaled"),
                ids(registry.getPresets(image("CT", 8, true, null, 0, 1), null))),
        () ->
            assertEquals(
                ids("head", "sc"),
                ids(registry.getPresets(image("CT", 12, false, BodyPart.HEAD, 0, 1), null)),
                "a preset required for the body part is also listed first"),
        () -> assertEquals(ids("sc"), ids(registry.getPresets(image("CT", 12), null))));
  }

  @Test
  void percentPresetsAreResolvedOnTheRangeOfEachImage() {
    WindowPresetRegistry registry =
        registryOf(
            """
            [{"id": "bright", "name": "Bright", "modality": ["MR"], "window": 50, "level": 75,
              "domain": {"kind": "percent"}, "key": "4"}]
            """);

    PresetWindowLevel first =
        registry.getPresets(image("MR", 12, false, null, 1000, 500), null).get(0);
    PresetWindowLevel second =
        registry.getPresets(image("MR", 12, false, null, 2000, 1000), null).get(0);

    assertAll(
        () -> assertEquals(500.0, first.getWindow()),
        () -> assertEquals(750.0, first.getLevel(), "min 0 + 75 % of 1000"),
        () -> assertEquals(1000.0, second.getWindow()),
        () -> assertEquals(1500.0, second.getLevel()),
        () -> assertEquals('4', second.getKeyCode()),
        () -> assertTrue(first.isSamePreset(second), "same id while scrolling"));
  }

  @Test
  void seriesPercentPresetsUseTheSeriesRangeThroughTheModalityLut() {
    WindowPresetRegistry registry =
        registryOf(
            """
            [{"id": "wide", "name": "Wide", "modality": ["ct"], "window": 50, "level": 50,
              "domain": {"kind": "percent", "reference": "series"}}]
            """);
    MinMaxLocResult stored = new MinMaxLocResult();
    stored.minVal = 0;
    stored.maxVal = 4000;
    DicomImageAdapter first = image("CT", 12, true, null, 1000, 0);
    DicomImageAdapter second = image("CT", 12, true, null, 2000, 500);
    for (DicomImageAdapter adapter : List.of(first, second)) {
      when(adapter.getImageDescriptor().getSeriesPixelRange()).thenReturn(Optional.of(stored));
      when(adapter.pixelToRealValue(any(), any()))
          .thenAnswer(i -> ((Number) i.getArgument(0)).doubleValue() - 1000);
    }
    DicomImageAdapter noSeriesRange = image("CT", 12, true, null, 1000, 0);

    PresetWindowLevel p1 = registry.getPresets(first, null).get(0);
    PresetWindowLevel p2 = registry.getPresets(second, null).get(0);
    PresetWindowLevel p3 = registry.getPresets(noSeriesRange, null).get(0);

    assertAll(
        () -> assertEquals(2000.0, p1.getWindow(), "50 % of -1000..3000"),
        () -> assertEquals(1000.0, p1.getLevel()),
        () -> assertEquals(p1.getWindow(), p2.getWindow(), "same values on every image"),
        () -> assertEquals(p1.getLevel(), p2.getLevel()),
        () -> assertEquals(500.0, p3.getWindow(), "image range without the series attributes"),
        () -> assertEquals(0.0, p3.getLevel()));
  }

  @Test
  void defaultIsTheFlaggedPresetOfTheLatestLayer(@TempDir Path dir) throws IOException {
    Path user = dir.resolve(WindowPresetRegistry.USER_FILE);
    Files.writeString(
        user,
        """
        [{"id": "user.mr.wide", "name": "Wide", "modality": ["MR"], "window": 100, "level": 50,
          "domain": {"kind": "percent"}, "default": true}]
        """);
    WindowPresetRegistry registry =
        registryOf(
            """
            [{"id": "a", "name": "A", "modality": ["MR"], "window": 400, "level": 40, "default": true},
             {"id": "b", "name": "B", "modality": ["MR"], "window": 400, "level": 80, "default": true}]
            """);

    List<PresetWindowLevel> builtInOnly =
        registry.getPresets(image("MR", 12, false, null, 1000, 500), null);
    registry.configure(null, user);
    List<PresetWindowLevel> withUser =
        registry.getPresets(image("MR", 12, false, null, 1000, 500), null);

    assertAll(
        () -> assertTrue(builtInOnly.get(0).isFallbackDefault(), "first flagged in a layer"),
        () -> assertFalse(builtInOnly.get(1).isFallbackDefault()),
        () -> assertFalse(withUser.get(0).isFallbackDefault()),
        () -> assertTrue(withUser.get(2).isFallbackDefault(), "the user layer wins"),
        () -> assertEquals("user.mr.wide", withUser.get(2).getId()));
  }

  @Test
  void savesDeletesAndImportsUserPresets(@TempDir Path dir) throws IOException {
    Path user = dir.resolve(WindowPresetRegistry.USER_FILE);
    List<Path> mirrored = new java.util.ArrayList<>();
    WindowPresetRegistry registry = new WindowPresetRegistry(WindowPresetRegistry.loadBuiltIn());
    registry.configure(null, user, mirrored::add);

    WindowPreset liver =
        new WindowPreset(
            "user.ct.liver",
            "Liver",
            java.util.Set.of("CT"),
            null,
            null,
            false,
            150,
            60,
            null,
            null,
            '4',
            null,
            false);
    registry.saveUser(liver);

    assertAll(
        () -> assertTrue(Files.isRegularFile(user)),
        () -> assertEquals(List.of(user), mirrored),
        () -> assertEquals(List.of(liver), registry.userPresets()),
        () -> assertEquals(WindowPresetRegistry.Origin.USER, registry.origin("user.ct.liver")),
        () -> assertEquals(WindowPresetRegistry.Origin.BUILT_IN, registry.origin("weasis.ct.lung")),
        () ->
            assertEquals(
                "user.ct.liver",
                registry.getPresets(image("CT", 12), null).get(12).getId(),
                "offered after the built-in presets"));

    assertTrue(registry.deleteUser("user.ct.liver"));
    assertFalse(registry.deleteUser("user.ct.liver"));
    assertEquals(12, registry.presets().size());
    assertEquals(List.of(), registry.userPresets());
  }

  @Test
  void userKeyTakesOverTheBuiltInKey(@TempDir Path dir) throws IOException {
    WindowPresetRegistry registry = new WindowPresetRegistry(WindowPresetRegistry.loadBuiltIn());
    registry.configure(null, dir.resolve(WindowPresetRegistry.USER_FILE));

    registry.saveUser(userPreset("user.ct.sinus", '3'));

    List<PresetWindowLevel> ct = registry.getPresets(image("CT", 12), null);
    assertAll(
        () -> assertEquals("weasis.ct.brain", ct.getFirst().getId()),
        () -> assertEquals(0, ct.getFirst().getKeyCode(), "brain gives up its key"),
        () -> assertEquals('3', ct.getLast().getKeyCode()),
        () -> assertEquals('7', ct.get(6).getKeyCode(), "other keys unchanged"));
  }

  @Test
  void unreadableUserFileIsKeptAsideBeforeBeingRewritten(@TempDir Path dir) throws IOException {
    Path user = dir.resolve(WindowPresetRegistry.USER_FILE);
    String corrupt = "{\"schema\": 1, \"presets\": [";
    Files.writeString(user, corrupt);
    WindowPresetRegistry registry = new WindowPresetRegistry(WindowPresetRegistry.loadBuiltIn());
    registry.configure(null, user);

    WindowPreset sinus = userPreset("user.ct.sinus", null);
    registry.saveUser(sinus);

    Path backup = dir.resolve(WindowPresetRegistry.USER_FILE + ".bak");
    assertAll(
        () -> assertEquals(corrupt, Files.readString(backup)),
        () -> assertEquals(List.of(sinus), WindowPresetJson.read(user)));
  }

  @Test
  void savingDoesNotReadTheSiteAgain(@TempDir Path dir) throws IOException {
    Path site = dir.resolve("site.json");
    Files.writeString(
        site,
        """
        [{"id": "site.ct.liver", "name": "Liver", "modality": ["CT"], "window": 150, "level": 60}]
        """);
    WindowPresetRegistry registry = new WindowPresetRegistry(WindowPresetRegistry.loadBuiltIn());
    registry.configure(site, dir.resolve(WindowPresetRegistry.USER_FILE));
    Files.delete(site);

    registry.saveUser(userPreset("user.ct.sinus", null));
    registry.reload();

    assertAll(
        () -> assertEquals(WindowPresetRegistry.Origin.SITE, registry.origin("site.ct.liver")),
        () -> assertEquals(14, registry.presets().size(), "unreadable site keeps the last one"));
  }

  private static WindowPreset userPreset(String id, Character key) {
    return new WindowPreset(
        id, "Sinus", Set.of("CT"), null, null, false, 2000, 400, null, null, key, null, false);
  }

  @Test
  void importsJsonIntoTheUserNamespace(@TempDir Path dir) throws IOException {
    Path json = dir.resolve("shared.json");
    WindowPresetRegistry.export(json, WindowPresetRegistry.loadBuiltIn());

    List<WindowPreset> fromJson = WindowPresetRegistry.readImport(json);

    assertAll(
        () -> assertEquals(12, fromJson.size()),
        () -> assertEquals("user.ct.lung", fromJson.get(6).id()),
        () -> assertEquals(1500.0, fromJson.get(6).window()),
        () -> assertThrows(IOException.class, () -> WindowPresetRegistry.readImport(dir)));
  }

  private static WindowPresetRegistry registryOf(String json) {
    return new WindowPresetRegistry(
        WindowPresetJson.read(
            new java.io.ByteArrayInputStream(
                json.getBytes(java.nio.charset.StandardCharsets.UTF_8))));
  }

  private static List<String> ids(String... ids) {
    return List.of(ids);
  }

  private static List<String> ids(List<PresetWindowLevel> presets) {
    return presets.stream().map(PresetWindowLevel::getId).toList();
  }

  private static DicomImageAdapter image(String modality, int bitsStored) {
    return image(modality, bitsStored, false, null, 0, 1);
  }

  private static DicomImageAdapter image(
      String modality,
      int bitsStored,
      boolean rescale,
      BodyPart bodyPart,
      double fullWidth,
      double fullCenter) {
    DicomImageAdapter adapter = mock(DicomImageAdapter.class);
    ImageDescriptor descriptor = mock(ImageDescriptor.class);
    ModalityLutModule mLut = mock(ModalityLutModule.class);
    when(mLut.getRescaleSlope())
        .thenReturn(rescale ? OptionalDouble.of(1.0) : OptionalDouble.empty());
    when(mLut.getLut()).thenReturn(Optional.empty());
    when(adapter.getBitsStored()).thenReturn(bitsStored);
    when(adapter.getImageDescriptor()).thenReturn(descriptor);
    when(adapter.getFullDynamicWidth(any())).thenReturn(fullWidth);
    when(adapter.getFullDynamicCenter(any())).thenReturn(fullCenter);
    when(descriptor.getModality()).thenReturn(modality);
    when(descriptor.getModalityLutForFrame(anyInt())).thenReturn(mLut);
    when(descriptor.getAnatomicRegion())
        .thenReturn(bodyPart == null ? null : new AnatomicRegion(bodyPart));
    return adapter;
  }

  @Test
  void aLockedSitePresetIgnoresTheUserOverride(@TempDir Path dir) throws IOException {
    Path site = dir.resolve("site.json");
    Files.writeString(
        site,
        """
        [{"id": "weasis.ct.lung", "name": "Site lung", "modality": ["CT"], "window": 1600,
          "level": -600, "locked": true},
         {"id": "site.ct.liver", "name": "Liver", "modality": ["CT"], "window": 150, "level": 60}]
        """);
    Path user = dir.resolve(WindowPresetRegistry.USER_FILE);
    Files.writeString(
        user,
        """
        [{"id": "weasis.ct.lung", "name": "My lung", "modality": ["CT"], "window": 1500,
          "level": -500, "hidden": true},
         {"id": "site.ct.liver", "name": "My liver", "modality": ["CT"], "window": 180, "level": 70}]
        """);
    WindowPresetRegistry registry = new WindowPresetRegistry(WindowPresetRegistry.loadBuiltIn());
    registry.configure(site, user);

    WindowPreset lung = registry.find("weasis.ct.lung").orElseThrow();
    WindowPreset liver = registry.find("site.ct.liver").orElseThrow();
    assertAll(
        () -> assertEquals("Site lung", lung.name()),
        () -> assertFalse(lung.hidden(), "the user cannot hide a locked preset"),
        () -> assertEquals(WindowPresetRegistry.Origin.SITE, registry.origin("weasis.ct.lung")),
        () -> assertTrue(registry.isLocked("weasis.ct.lung")),
        () -> assertEquals("My liver", liver.name()),
        () -> assertFalse(registry.isLocked("site.ct.liver")),
        () -> assertFalse(lung.withId("user.ct.lung").locked(), "a copy is not locked"),
        () -> assertEquals(2, registry.userPresets().size(), "the user file is kept as written"));
  }
}
