/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.codec.export;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.dcm4che3.data.Attributes;
import org.weasis.core.api.media.data.MaskingProfile;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.media.data.PixelReviewAdvisor;
import org.weasis.core.ui.editor.image.export.AnimatedImageSink;
import org.weasis.core.ui.editor.image.export.AnimationFormat;
import org.weasis.core.ui.editor.image.export.FrameSink;
import org.weasis.core.ui.editor.image.export.FrameSinkFactory;
import org.weasis.dicom.codec.DcmMediaReader;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.codec.Messages;

/**
 * Offers the DICOM Secondary Capture output to the animation export dialog, with the patient and
 * study identity taken from the series the animation was made from.
 */
public class DicomScSinkFactory implements FrameSinkFactory {

  private final Attributes source;
  private final String seriesDescription;
  private final DicomScFrameSink.ScCodec codec;
  private final boolean burnedInPixels;

  public DicomScSinkFactory(Attributes source, String seriesDescription, boolean burnedInPixels) {
    this(source, seriesDescription, DicomScFrameSink.ScCodec.JPEG_BASELINE, burnedInPixels);
  }

  /**
   * @param burnedInPixels whether the source pixels may carry identity of their own, which no
   *     masking profile removes
   */
  public DicomScSinkFactory(
      Attributes source,
      String seriesDescription,
      DicomScFrameSink.ScCodec codec,
      boolean burnedInPixels) {
    this.source = source;
    this.seriesDescription = seriesDescription;
    this.codec = codec;
    this.burnedInPixels = burnedInPixels;
  }

  /**
   * The formats offered for a DICOM animation: both animated images, plus the Secondary Capture
   * that keeps the result in the study.
   */
  public static List<FrameSinkFactory> defaultSinks(
      MediaSeries<DicomImageElement> series, String seriesDescription) {
    DicomImageElement image = middleImage(series);
    return List.of(
        AnimatedImageSink.factory(AnimationFormat.APNG),
        AnimatedImageSink.factory(AnimationFormat.GIF),
        new DicomScSinkFactory(
            headerOf(image), seriesDescription, PixelReviewAdvisor.review(image, series)));
  }

  private static DicomImageElement middleImage(MediaSeries<DicomImageElement> series) {
    return series == null ? null : series.getMedia(MediaSeries.MEDIA_POSITION.MIDDLE, null, null);
  }

  /** The header of {@code image}, or {@code null} when there is none. */
  public static Attributes headerOf(DicomImageElement image) {
    return Optional.ofNullable(image)
        .map(DicomImageElement::getMediaReader)
        .map(DcmMediaReader::getDicomObject)
        .orElse(null);
  }

  @Override
  public String title() {
    return Messages.getString("dicom.secondary.capture");
  }

  @Override
  public String extension() {
    return "dcm"; // NON-NLS
  }

  @Override
  public String advice() {
    return Messages.getString("dicom.secondary.capture.advice");
  }

  @Override
  public boolean buffersRawFrames() {
    return false;
  }

  @Override
  public FrameSink create(Path file, MaskingProfile profile) {
    return new DicomScFrameSink(file, source, seriesDescription, codec, profile, burnedInPixels);
  }
}
