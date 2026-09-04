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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.awt.Rectangle;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.dcm4che3.data.Tag;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.weasis.core.api.media.data.IdentityMask;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.api.media.data.MaskRegion;
import org.weasis.core.api.media.data.MaskingModel;
import org.weasis.core.api.media.data.MaskingModelRegistry;
import org.weasis.core.api.media.data.MaskingProfile;
import org.weasis.core.api.media.data.MediaElement;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.media.data.PixelMask;
import org.weasis.core.api.media.data.PixelMask.DeviceKey;
import org.weasis.core.api.media.data.PixelMask.Reference;
import org.weasis.core.api.media.data.TagCategory;
import org.weasis.core.api.media.data.TagReadable;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.api.media.data.Taggable;
import org.weasis.dicom.codec.PixelMaskMatcher.Reason;
import org.weasis.dicom.codec.Redaction.Mask;
import org.weasis.dicom.codec.Redaction.Scope;

/** {@link Isolated}: the registry and the session mask are global. */
@Isolated
@DisplayName("Pixel mask library")
class PixelMaskLibraryTest {

  private static final String STATION = "USVIVID01";

  @BeforeEach
  void installLibrary() {
    MaskingModelRegistry registry = new MaskingModelRegistry(MaskingModelRegistry.loadBuiltIn());
    registry.contribute(MaskingModel.EMPTY.withMasks(List.of(banner(), wideBanner())));
    MaskingModelRegistry.useInstance(registry);
  }

  @AfterEach
  void reset() {
    IdentityMask.setSessionMasking(false);
    MaskingModelRegistry.useInstance(null);
  }

  private static PixelMask banner() {
    return new PixelMask(
        "us-banner",
        "US banner",
        new DeviceKey("US", STATION, null, null, null),
        new Reference(1024, 768),
        List.of(
            new MaskRegion.Rect(0, 0, 1, 0.05, TagCategory.DIRECT_ID),
            new MaskRegion.Rect(0, 0.9, 0.3, 0.1, TagCategory.DEVICE)),
        List.of(),
        true);
  }

  /** Same modality, no station: less specific, so it must lose to the one above. */
  private static PixelMask wideBanner() {
    return new PixelMask(
        "us-any",
        "Any ultrasound",
        new DeviceKey("US", null, null, null, null),
        new Reference(1024, 768),
        List.of(new MaskRegion.Rect(0, 0, 1, 0.2, TagCategory.DIRECT_ID)),
        List.of(),
        true);
  }

  private static <T> T taggable(Class<T> type, Map<Integer, Object> dicomTags) {
    Map<TagW, Object> tags = new HashMap<>();
    dicomTags.forEach((tag, value) -> tags.put(TagD.get(tag), value));
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

  private static ImageElement image(int columns, int rows) {
    return taggable(ImageElement.class, Map.of(Tag.Columns, columns, Tag.Rows, rows));
  }

  private static MediaSeries<?> series(String station) {
    return taggable(
        MediaSeries.class, Map.of(Tag.Modality, "US", Tag.StationName, station)); // NON-NLS
  }

  /** A series that knows its frames, for what a series-wide change does to them. */
  @SuppressWarnings("unchecked")
  private static MediaSeries<?> seriesOf(ImageElement... frames) {
    MediaSeries<MediaElement> series = (MediaSeries<MediaElement>) series(STATION);
    when(series.getMedias(null, null)).thenReturn(List.<MediaElement>of(frames));
    return series;
  }

  @Test
  @DisplayName("nothing is hidden while no mask is in force")
  void unmaskedReadingIsUntouched() {
    assertNull(Redaction.effective(image(1024, 768), series(STATION)));
  }

  @Test
  @DisplayName("the most specific entry applies, and the profile decides which regions burn")
  void profileGate() {
    ImageElement image = image(1024, 768);
    MediaSeries<?> series = series(STATION);

    Mask display =
        IdentityMask.forProfile(MaskingProfile.display())
            .callMasked(() -> Redaction.effective(image, series));
    Mask publication =
        IdentityMask.forProfile(MaskingProfile.publication())
            .callMasked(() -> Redaction.effective(image, series));

    assertAll(
        () -> assertNotNull(display),
        () -> assertEquals("us-banner", display.origin(), "the station entry beats the wide one"),
        () -> assertEquals(1, display.shapes().size(), "Display keeps the equipment label"),
        () -> assertEquals(2, publication.shapes().size(), "Publication removes it too"),
        () -> assertTrue(display.fromLibrary()));
  }

  @Test
  @DisplayName("an image of another shape is refused rather than stretched")
  void geometryGuard() {
    ImageElement wide = image(1920, 1080);
    MediaSeries<?> series = series(STATION);

    Mask mask =
        IdentityMask.forProfile(MaskingProfile.display())
            .callMasked(() -> Redaction.effective(wide, series));
    Reason reason =
        PixelMaskMatcher.evaluate(series, wide, MaskingProfile.DISPLAY_ID).stream()
            .filter(c -> c.mask().id().equals("us-banner"))
            .findFirst()
            .orElseThrow()
            .reason();

    assertAll(() -> assertNull(mask), () -> assertEquals(Reason.GEOMETRY, reason));
  }

  @Test
  @DisplayName("another device gets nothing")
  void deviceKey() {
    ImageElement image = image(1024, 768);
    MediaSeries<?> other = series("USVIVID99");

    Mask mask =
        IdentityMask.forProfile(MaskingProfile.display())
            .callMasked(() -> Redaction.effective(image, other));

    assertAll(
        () -> assertNotNull(mask, "the entry without a station still applies"),
        () -> assertEquals("us-any", mask.origin()));
  }

  @Test
  @DisplayName("a drawn mask wins, and clearing it suppresses the library entry")
  void drawnWinsAndSuppression() {
    ImageElement image = image(1024, 768);
    MediaSeries<?> series = series(STATION);
    Redaction.write(image, new Mask(List.of(new Rectangle(0, 0, 5, 5))));

    Mask drawn =
        IdentityMask.forProfile(MaskingProfile.display())
            .callMasked(() -> Redaction.effective(image, series));
    Redaction.suppress(image);
    Mask suppressed =
        IdentityMask.forProfile(MaskingProfile.display())
            .callMasked(() -> Redaction.effective(image, series));

    assertAll(
        () -> assertFalse(drawn.fromLibrary()),
        () -> assertEquals(1, drawn.shapes().size()),
        () -> assertNotNull(suppressed),
        () -> assertTrue(suppressed.isEmpty(), "an empty mask hides nothing, library included"));
  }

  @Test
  @DisplayName("editing a proposed mask copies it onto the image")
  void materializeMakesItYours() {
    ImageElement image = image(1024, 768);
    MediaSeries<?> series = series(STATION);

    Mask copy =
        IdentityMask.forProfile(MaskingProfile.display())
            .callMasked(() -> Redaction.materialize(image, series, image));

    assertAll(
        () -> assertNotNull(copy),
        () -> assertNull(copy.origin(), "the copy belongs to the user"),
        () -> assertEquals(1, copy.shapes().size()),
        () -> assertNull(Redaction.read(image).origin()));
  }

  @Test
  @DisplayName("an entry picked by the user burns every region, with no mask in force")
  void pickedEntryIsAppliedWhole() {
    ImageElement image = image(1024, 768);
    MediaSeries<?> series = series(STATION);

    Mask picked = Redaction.of(banner(), image);
    Redaction.write(image, picked);

    assertAll(
        () -> assertNotNull(picked),
        () -> assertEquals(2, picked.shapes().size(), "the Display profile would keep one of them"),
        () -> assertEquals("us-banner", picked.origin()),
        () -> assertEquals(picked, Redaction.effective(image, series)));
  }

  @Test
  @DisplayName("clearing an image drops its tag, so a series mask applied next is seen")
  void resetFreesTheScopeBelow() {
    ImageElement image = image(1024, 768);
    MediaSeries<?> series = series(STATION);

    Redaction.write(image, Redaction.of(banner(), image));
    Redaction.reset(image, series, Scope.IMAGE);
    Mask cleared = Redaction.effective(image, series);
    Redaction.write(series, Redaction.of(banner(), image));

    assertAll(
        () -> assertNull(Redaction.read(image), "an empty mask would blind every series mask"),
        () -> assertNull(cleared),
        () -> assertEquals(2, Redaction.effective(image, series).shapes().size()));
  }

  @Test
  @DisplayName("clearing regions the library proposes suppresses them for the session")
  void resetSuppressesTheProposal() {
    ImageElement image = image(1024, 768);
    MediaSeries<?> series = series(STATION);

    IdentityMask.forProfile(MaskingProfile.display())
        .runMasked(() -> Redaction.reset(image, series, Scope.IMAGE));
    Mask suppressed = Redaction.read(image);

    assertAll(
        () -> assertNotNull(suppressed, "dropping the tag would propose the entry again at once"),
        () -> assertTrue(suppressed.isEmpty()));
  }

  @Test
  @DisplayName("a mask applied to a series frees its frames of what the series answers for")
  void seriesMaskFreesItsFrames() {
    ImageElement suppressed = image(1024, 768);
    ImageElement sameEntry = image(1024, 768);
    ImageElement drawn = image(1024, 768);
    MediaSeries<?> series = seriesOf(suppressed, sameEntry, drawn);
    Redaction.suppress(suppressed);
    Redaction.write(sameEntry, Redaction.of(banner(), sameEntry));
    Redaction.write(drawn, new Mask(List.of(new Rectangle(0, 0, 5, 5))));

    Redaction.apply(suppressed, series, Scope.SERIES, Redaction.of(banner(), suppressed));
    Redaction.reset(suppressed, series, Scope.SERIES);

    assertAll(
        () -> assertNull(Redaction.read(suppressed), "the frame stops hiding the series regions"),
        () -> assertNull(Redaction.read(sameEntry), "the series answers for that entry now"),
        () -> assertNull(Redaction.effective(sameEntry, series), "removed with the series"),
        () ->
            assertEquals(1, Redaction.effective(drawn, series).shapes().size(), "a drawing wins"));
  }

  @Test
  @DisplayName(
      "an image whose pixels may carry identity asks for a review until something hides it")
  void reviewAdvice() {
    ImageElement image =
        taggable(
            ImageElement.class,
            Map.of(Tag.Columns, 1024, Tag.Rows, 768, Tag.BurnedInAnnotation, "YES"));
    MediaSeries<?> series = series(STATION);

    boolean before = Redaction.requiresReview(image, series);
    Redaction.write(image, new Mask(List.of(new Rectangle(0, 0, 5, 5))));
    boolean after = Redaction.requiresReview(image, series);

    assertAll(() -> assertTrue(before), () -> assertFalse(after));
  }

  @Test
  @DisplayName("a disabled entry and a profile restriction are reported, not applied")
  void rejections() {
    MaskingModelRegistry registry = MaskingModelRegistry.getInstance();
    registry.contribute(
        MaskingModel.EMPTY.withMasks(
            List.of(
                banner().withEnabled(false),
                new PixelMask(
                    "publication-only",
                    "Publication only",
                    new DeviceKey("US", STATION, null, null, null),
                    new Reference(1024, 768),
                    List.of(new MaskRegion.Rect(0, 0, 1, 0.05, TagCategory.DIRECT_ID)),
                    List.of(MaskingProfile.PUBLICATION_ID),
                    true))));

    List<PixelMaskMatcher.Candidate> candidates =
        PixelMaskMatcher.evaluate(series(STATION), image(1024, 768), MaskingProfile.DISPLAY_ID);

    assertAll(
        () ->
            assertEquals(
                Reason.DISABLED,
                candidates.stream()
                    .filter(c -> c.mask().id().equals("us-banner"))
                    .findFirst()
                    .orElseThrow()
                    .reason()),
        () ->
            assertEquals(
                Reason.PROFILE,
                candidates.stream()
                    .filter(c -> c.mask().id().equals("publication-only"))
                    .findFirst()
                    .orElseThrow()
                    .reason()));
  }
}
