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

import java.awt.Shape;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.weasis.core.api.image.SimpleOpManager;
import org.weasis.core.api.image.SimpleOpManager.Position;
import org.weasis.core.api.image.ZoomOp;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.api.media.data.MediaElement;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.media.data.SeriesThumbnail;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.api.media.data.Taggable;
import org.weasis.dicom.codec.display.RedactionOp;
import org.weasis.dicom.codec.display.RedactionRenderer;

/**
 * Regions burned over the pixels to hide identity that no tag masking can reach — text printed into
 * the image by the modality. Each region is a closed shape in image coordinates, filled by {@link
 * RedactionRenderer} with the median color of its surroundings.
 *
 * <p>Stored per image or per series. A series-scoped mask is not copied onto the frames: a
 * multi-frame series is one {@link ImageElement} per frame, so copies would drift as soon as one is
 * edited. The image-scoped mask wins where both exist.
 */
public final class Redaction {

  /** Where a set of regions applies. */
  public enum Scope {
    IMAGE,
    SERIES
  }

  /** The closed shapes of an image or a series, in image coordinates, burned in order. */
  public record Mask(List<Shape> shapes) {
    public Mask {
      shapes = List.copyOf(shapes);
    }

    public boolean isEmpty() {
      return shapes.isEmpty();
    }
  }

  private Redaction() {}

  /** The mask to apply to {@code image}: its own if set, otherwise the one of its series. */
  public static Mask effective(ImageElement image, MediaSeries<?> series) {
    Mask mask = read(image);
    return mask == null ? read(series) : mask;
  }

  public static Mask read(Taggable taggable) {
    return taggable == null || !(taggable.getTagValue(TagW.RedactionMask) instanceof Mask mask)
        ? null
        : mask;
  }

  public static void write(Taggable taggable, Mask mask) {
    if (taggable != null) {
      taggable.setTag(TagW.RedactionMask, mask);
    }
  }

  /** Adds a closed shape to the mask of {@code taggable}. */
  public static void add(Taggable taggable, Shape shape) {
    Objects.requireNonNull(shape);
    Mask current = read(taggable);
    List<Shape> shapes = current == null ? new ArrayList<>() : new ArrayList<>(current.shapes());
    shapes.add(shape);
    write(taggable, new Mask(shapes));
  }

  /**
   * Adds the operation burning the regions of {@code image}, or of its series, to {@code manager},
   * so an exported image hides what the view hides. It goes before the zoom, which rescales the
   * image out of the coordinates the regions are expressed in.
   *
   * @return whether regions were found and added
   */
  public static boolean addTo(SimpleOpManager manager, ImageElement image, MediaSeries<?> series) {
    Mask mask = effective(image, series);
    if (mask == null || mask.isEmpty()) {
      return false;
    }
    manager.addImageOperationAction(
        operationFor(mask), Position.BEFORE, manager.getNode(ZoomOp.OP_NAME).orElse(null));
    return true;
  }

  /** Removes every region, so the pixels are shown unmodified again. */
  public static void clear(Taggable taggable) {
    write(taggable, null);
  }

  /**
   * Operations that burn the mask of {@code taggable}, or {@code null} when there is none. Lets a
   * consumer outside the viewer — an explorer thumbnail — render the same pixels as the view.
   */
  public static SimpleOpManager opManagerFor(Taggable taggable) {
    Mask mask = read(taggable);
    if (mask == null || mask.isEmpty()) {
      return null;
    }
    SimpleOpManager manager = new SimpleOpManager();
    manager.addImageOperationAction(operationFor(mask));
    return manager;
  }

  private static RedactionOp operationFor(Mask mask) {
    RedactionOp op = new RedactionOp();
    op.setParam(RedactionOp.P_MASK, mask);
    return op;
  }

  /**
   * Rebuilds the series thumbnail so it stops showing pixels the mask now hides. The thumbnail is
   * cached both in memory and as a temporary file keyed on the media, so both have to go.
   */
  public static void refreshThumbnail(MediaSeries<? extends MediaElement> series) {
    if (series == null
        || !(series.getTagValue(TagW.Thumbnail) instanceof SeriesThumbnail thumbnail)) {
      return;
    }
    MediaElement media = series.getMedia(MediaSeries.MEDIA_POSITION.MIDDLE, null, null);
    if (media != null) {
      media.setTag(TagW.ThumbnailPath, null);
    }
    thumbnail.setOpManager(opManagerFor(series));
    thumbnail.reBuildThumbnail();
  }
}
