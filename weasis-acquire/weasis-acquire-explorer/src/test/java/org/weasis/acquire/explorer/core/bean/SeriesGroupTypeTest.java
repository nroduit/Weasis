/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.acquire.explorer.core.bean;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.weasis.acquire.explorer.core.bean.SeriesGroup.Type;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.api.media.data.MediaElement;

/**
 * Tests {@link SeriesGroup.Type} — the MIME-type → series-grouping classifier used by the
 * Acquire/Dicomizer import path.
 *
 * <p>Coverage focuses on the pure-Java classification paths (null media, MIME-type matching, {@code
 * isVideo}). The {@code VIDEO_MP4} / {@code VIDEO_MP2} branches of {@code fromMimeType} require
 * reading actual MP4/MPEG2 file headers via {@code dcm4che} parsers and are out of scope for unit
 * tests; they are flagged as residual risks in the verification matrix.
 *
 * <p>Critical for the audit because the {@code isVideo} predicate (added in commit 7244715c7) is
 * what decides whether to apply the oversized-video rejection rule — a false negative here would
 * silently let an unbounded-size video into the DICOMization pipeline.
 */
class SeriesGroupTypeTest {

  // -- isVideo (added in commit 7244715c7) -------------------------------

  @Test
  void isVideo_nullMediaIsFalse() {
    assertFalse(Type.isVideo(null));
  }

  @Test
  void isVideo_nullMimeTypeIsFalse() {
    MediaElement media = mediaWithMime(null);

    assertFalse(Type.isVideo(media));
  }

  @Test
  void isVideo_imageMimeTypeIsFalse() {
    assertAll(
        () -> assertFalse(Type.isVideo(mediaWithMime("image/jpeg"))),
        () -> assertFalse(Type.isVideo(mediaWithMime("image/png"))),
        () -> assertFalse(Type.isVideo(mediaWithMime("image/tiff"))));
  }

  @Test
  void isVideo_pdfAndStlMimeTypesAreFalse() {
    assertAll(
        () -> assertFalse(Type.isVideo(mediaWithMime("application/pdf"))),
        () -> assertFalse(Type.isVideo(mediaWithMime("application/sla"))),
        () -> assertFalse(Type.isVideo(mediaWithMime("model/stl"))));
  }

  @Test
  void isVideo_videoMp4IsTrue() {
    assertTrue(Type.isVideo(mediaWithMime("video/mp4")));
  }

  @Test
  void isVideo_videoMpegIsTrue() {
    assertTrue(Type.isVideo(mediaWithMime("video/mpeg")));
  }

  @Test
  void isVideo_videoQuicktimeIsTrue() {
    // QuickTime is non-DICOM-compliant but still a video — must trip the size check so the user
    // is told to convert it rather than have it sneak through.
    assertTrue(Type.isVideo(mediaWithMime("video/quicktime")));
  }

  @Test
  void isVideo_videoXMatroskaIsTrue() {
    // Matroska container (video/x-matroska) — also a video, must be detected and routed to the
    // codec-compliance rejection path.
    assertTrue(Type.isVideo(mediaWithMime("video/x-matroska")));
  }

  @Test
  void isVideo_isCaseInsensitive() {
    // Per the javadoc on isVideo, MIME comparison is lower-cased before prefix-matching, so
    // a server-supplied "VIDEO/MP4" must still match.
    assertAll(
        () -> assertTrue(Type.isVideo(mediaWithMime("VIDEO/MP4"))),
        () -> assertTrue(Type.isVideo(mediaWithMime("Video/Mp4"))));
  }

  @Test
  void isVideo_doesNotMatchVideoSubstringInOtherSlot() {
    // A bogus MIME like "application/video-config" must NOT match — only the "video/" prefix
    // counts. Pin the prefix-only behaviour explicitly.
    assertFalse(Type.isVideo(mediaWithMime("application/video-config")));
  }

  // -- fromMimeType (pre-existing classifier, important for default modality) ---

  @Test
  void fromMimeType_nullMediaReturnsNull() {
    assertNull(Type.fromMimeType(null));
  }

  @Test
  void fromMimeType_imageElementReturnsImage() {
    ImageElement image = mock(ImageElement.class);

    assertEquals(Type.IMAGE, Type.fromMimeType(image));
  }

  @Test
  void fromMimeType_pdfMimeTypeReturnsPdf() {
    assertEquals(Type.PDF, Type.fromMimeType(mediaWithMime("application/pdf")));
  }

  @Test
  void fromMimeType_pdfMimeTypeIsCaseInsensitive() {
    // The classifier lower-cases the MIME before comparing; an upper-case "APPLICATION/PDF"
    // must still classify as PDF (otherwise the user's PDF report ends up in the wrong series).
    assertEquals(Type.PDF, Type.fromMimeType(mediaWithMime("APPLICATION/PDF")));
  }

  @Test
  void fromMimeType_stlMimeVariantsAllReturnStl() {
    assertAll(
        () -> assertEquals(Type.STL, Type.fromMimeType(mediaWithMime("application/sla"))),
        () -> assertEquals(Type.STL, Type.fromMimeType(mediaWithMime("model/stl"))),
        () -> assertEquals(Type.STL, Type.fromMimeType(mediaWithMime("model/x.stl-binary"))));
  }

  @Test
  void fromMimeType_unknownMimeReturnsNull() {
    // null return signals "rejected" to the import pipeline — the user is then warned that the
    // file is not importable. False classification here would silently accept garbage.
    assertNull(Type.fromMimeType(mediaWithMime("application/octet-stream")));
    assertNull(Type.fromMimeType(mediaWithMime("text/plain")));
    assertNull(Type.fromMimeType(mediaWithMime("audio/wav")));
  }

  @Test
  void fromMimeType_nullMimeReturnsNull() {
    assertNull(Type.fromMimeType(mediaWithMime(null)));
  }

  @Test
  void fromMimeType_videoMimePrefixIsRoutedToParser() {
    // For a non-image, video/mp* MIME, fromMimeType delegates to videoType() which reads the
    // file header. Without a real file the parser raises an exception swallowed by the
    // implementation, so the call resolves to null (the "must convert before import" signal).
    // Pin that contract — a regression that returned VIDEO_MP4 by default would let any video
    // file silently classify as DICOM-compliant.
    MediaElement media = mediaWithMime("video/mp4");
    when(media.getFilePath()).thenReturn(java.nio.file.Path.of("/does/not/exist.mp4"));

    assertNull(
        Type.fromMimeType(media),
        "non-readable video file -> classifier returns null, user warned to convert");
  }

  // -- Type metadata (default modality drives DICOMization output) ----------

  @Test
  void defaultModality_imageAndDateAndNameAndVideoUseXc() {
    // Critical: IMAGE_DATE / IMAGE_NAME / VIDEO_MP2 / VIDEO_MP4 all default to "XC" (External
    // Camera Photography). A regression to "OT" or "" would silently change the modality on
    // every exported study, breaking PACS routing rules keyed off the Modality tag.
    assertAll(
        () -> assertEquals("XC", Type.IMAGE.getDefaultModality()),
        () -> assertEquals("XC", Type.IMAGE_DATE.getDefaultModality()),
        () -> assertEquals("XC", Type.IMAGE_NAME.getDefaultModality()),
        () -> assertEquals("XC", Type.VIDEO_MP2.getDefaultModality()),
        () -> assertEquals("XC", Type.VIDEO_MP4.getDefaultModality()));
  }

  @Test
  void defaultModality_pdfIsDocumentAndStlIs3dModel() {
    assertAll(
        () -> assertEquals("DOC", Type.PDF.getDefaultModality()),
        () -> assertEquals("M3D", Type.STL.getDefaultModality()));
  }

  @Test
  void videoLabelsBindMp4ToMpeg4AndMp2ToMpeg2() {
    // Commit 7244715c7 fixed a label swap (VIDEO_MP4 used to display "MPEG-2" and vice versa).
    // Pin the corrected mapping so a future refactor cannot silently re-swap them: the user
    // would otherwise be told "MPEG-2" while the file is in fact MP4.
    assertAll(
        () ->
            assertTrue(
                Type.VIDEO_MP4.getDescription().toLowerCase().contains("mpeg-4")
                    || Type.VIDEO_MP4.getDescription().toLowerCase().contains("mpeg4"),
                "VIDEO_MP4 description should reference MPEG-4, was: "
                    + Type.VIDEO_MP4.getDescription()),
        () ->
            assertTrue(
                Type.VIDEO_MP2.getDescription().toLowerCase().contains("mpeg-2")
                    || Type.VIDEO_MP2.getDescription().toLowerCase().contains("mpeg2"),
                "VIDEO_MP2 description should reference MPEG-2, was: "
                    + Type.VIDEO_MP2.getDescription()));
  }

  // -- helpers --------------------------------------------------------------

  private static MediaElement mediaWithMime(String mime) {
    MediaElement m = mock(MediaElement.class);
    lenient().when(m.getMimeType()).thenReturn(mime);
    return m;
  }
}
