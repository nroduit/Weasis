/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.pref;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.media.data.AnonymizationAction;
import org.weasis.core.api.media.data.MaskRegion;
import org.weasis.core.api.media.data.MaskingModel;
import org.weasis.core.api.media.data.MaskingModelRegistry;
import org.weasis.core.api.media.data.MaskingProfile;
import org.weasis.core.api.media.data.PixelMask;
import org.weasis.core.api.media.data.TagCategory;

@DisplayName("MaskingPrefView")
class MaskingPrefViewTest {

  @TempDir Path dir;

  private Path userFile() {
    return dir.resolve("identityMasking.json");
  }

  private MaskingModelRegistry registry(String siteDocument) throws IOException {
    MaskingModelRegistry registry = new MaskingModelRegistry(MaskingModelRegistry.loadBuiltIn());
    Path site =
        siteDocument == null ? null : Files.writeString(dir.resolve("site.json"), siteDocument);
    registry.configure(site == null ? null : site.toString(), userFile());
    return registry;
  }

  @Test
  @DisplayName("the page fits the preference dialog, so a tab scrolls instead of the page")
  void pageFitsTheDialog() throws IOException {
    MaskingPrefView view = new MaskingPrefView(registry(null));

    assertAll(
        () ->
            assertTrue(
                view.getPreferredSize().height <= GuiUtils.getScaleLength(460),
                "a page taller than the dialog adds a second scroll bar next to the tab one"),
        () -> assertTrue(view.getScrollableTracksViewportWidth()));
  }

  @Test
  @DisplayName("a built-in profile is read-only, its clone is editable and saved on Apply")
  void cloneAndSave() throws IOException {
    MaskingModelRegistry registry = registry(null);
    MaskingPrefView view = new MaskingPrefView(registry);

    view.selectProfile(MaskingProfile.DISPLAY_ID);
    boolean builtInEditable = view.editorEnabled();
    MaskingProfile clone = view.cloneProfile("Demo");
    boolean cloneEditable = view.editorEnabled();
    view.setAction(TagCategory.DEVICE, AnonymizationAction.REMOVE);
    view.closeAdditionalWindow();

    MaskingProfile saved = registry.profile(clone.id()).orElseThrow();
    assertAll(
        () -> assertFalse(builtInEditable, "a built-in profile cannot be changed"),
        () -> assertTrue(cloneEditable),
        () -> assertEquals("user-demo", clone.id()),
        () -> assertEquals("Demo", saved.name()),
        () -> assertEquals(AnonymizationAction.REMOVE, saved.actionFor(TagCategory.DEVICE)),
        () ->
            assertEquals(
                AnonymizationAction.PSEUDONYMIZE,
                saved.actionFor(TagCategory.DIRECT_ID),
                "the clone keeps what its source hides"),
        () -> assertEquals(MaskingModelRegistry.Origin.USER, registry.origin(clone.id())),
        () -> assertTrue(Files.isRegularFile(userFile())));
  }

  @Test
  @DisplayName("a tag typed in the classification tab is added to the user document")
  void classifyTag() throws IOException {
    MaskingModelRegistry registry = registry(null);
    MaskingPrefView view = new MaskingPrefView(registry);
    int before = view.tagRowCount();

    view.typeTag("(0010,1040)");
    view.addTag();
    view.closeAdditionalWindow();

    assertAll(
        () -> assertEquals(before + 1, view.tagRowCount()),
        () -> assertEquals(MaskingModelRegistry.Origin.USER, registry.tagOrigin("00101040")),
        () ->
            assertTrue(
                registry.tagRules().stream()
                    .anyMatch(
                        r -> r.key().equals("00101040") && r.category() == TagCategory.DIRECT_ID)));
  }

  @Test
  @DisplayName("an invalid tag is refused and nothing is added")
  void refuseInvalidTag() throws IOException {
    MaskingModelRegistry registry = registry(null);
    MaskingPrefView view = new MaskingPrefView(registry);

    view.typeTag("   ");
    view.addTag();

    assertTrue(view.pendingModel().tags().isEmpty());
  }

  @Test
  @DisplayName("a device mask saved while the page is open survives Apply")
  void keepMasksSavedMeanwhile() throws IOException {
    MaskingModelRegistry registry = registry(null);
    MaskingPrefView view = new MaskingPrefView(registry);

    // What the device-mask page does while this one is open
    registry.saveUserMask(
        new PixelMask(
            "user-vivid",
            "Vivid",
            new PixelMask.DeviceKey("US", "VIVID7", null, null, null),
            new PixelMask.Reference(800, 600),
            List.of(new MaskRegion.Rect(0, 0, 1, 0.05, TagCategory.DIRECT_ID)),
            List.of(),
            true));
    view.cloneProfile("Demo");
    view.closeAdditionalWindow();

    assertAll(
        () -> assertTrue(registry.pixelMask("user-vivid").isPresent(), "the mask is kept"),
        () -> assertTrue(registry.profile("user-demo").isPresent()));
  }

  @Test
  @DisplayName("deleting a profile takes the defaults that named it off it")
  void deleteProfileUsedAsDefault() throws IOException {
    MaskingModelRegistry registry = registry(null);
    MaskingPrefView view = new MaskingPrefView(registry);

    view.selectProfile(MaskingProfile.DISPLAY_ID);
    MaskingProfile clone = view.cloneProfile("Session");
    view.selectSessionProfile(clone.id());
    view.removeProfile(clone.id());
    view.closeAdditionalWindow();

    assertAll(
        () -> assertEquals(MaskingProfile.DISPLAY_ID, registry.userModel().sessionProfile()),
        () -> assertEquals(MaskingProfile.DISPLAY_ID, registry.sessionProfile().id()),
        () -> assertTrue(registry.profile(clone.id()).isEmpty()));
  }

  @Test
  @DisplayName("a locked site document leaves nothing to edit and writes nothing")
  void lockedConfiguration() throws IOException {
    MaskingModelRegistry registry =
        registry(
            """
            { "locked": true,
              "profiles": [ { "id": "site-demo", "name": "Site",
                  "actions": { "DIRECT_ID": "REMOVE" } } ] }
            """);
    MaskingPrefView view = new MaskingPrefView(registry);

    view.selectProfile("site-demo");
    boolean editable = view.editorEnabled();
    view.typeTag("OperatorsName");
    view.addTag();
    view.closeAdditionalWindow();

    assertAll(
        () -> assertFalse(editable),
        () -> assertEquals(MaskingModel.EMPTY.tags(), view.pendingModel().tags()),
        () -> assertFalse(Files.exists(userFile())));
  }
}
