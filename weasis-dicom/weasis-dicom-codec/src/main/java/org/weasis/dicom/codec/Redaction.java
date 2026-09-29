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
import java.util.Optional;
import java.util.function.Predicate;
import org.dcm4che3.data.Tag;
import org.weasis.core.api.image.SimpleOpManager;
import org.weasis.core.api.image.SimpleOpManager.Position;
import org.weasis.core.api.image.ZoomOp;
import org.weasis.core.api.media.data.AnonymizationAction;
import org.weasis.core.api.media.data.IdentityMask;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.api.media.data.MaskRegion;
import org.weasis.core.api.media.data.MaskingProfile;
import org.weasis.core.api.media.data.MediaElement;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.media.data.PixelMask;
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
 * edited. The image-scoped mask wins where both exist, and an empty stored mask hides nothing, so
 * it suppresses the regions the device library would otherwise propose.
 *
 * <p>When nothing is stored, the regions come from the {@link PixelMask} entries of the masking
 * document, matched on the acquisition device ({@link PixelMaskMatcher}) and filtered by the mask
 * in force: a region is burned only when its category is not kept by the profile, so normal
 * unmasked reading shows the pixels as they are.
 */
public final class Redaction {

  /** Where a set of regions applies. */
  public enum Scope {
    IMAGE,
    SERIES
  }

  /**
   * The closed shapes of an image or a series, in image coordinates, burned in order.
   *
   * @param origin id of the device entry the regions come from, null when the user drew them
   */
  public record Mask(List<Shape> shapes, String origin) {
    public Mask {
      shapes = List.copyOf(shapes);
    }

    public Mask(List<Shape> shapes) {
      this(shapes, null);
    }

    public boolean isEmpty() {
      return shapes.isEmpty();
    }

    public boolean fromLibrary() {
      return origin != null;
    }
  }

  private Redaction() {}

  /**
   * The mask to apply to {@code image}: its own if set, otherwise the one of its series, otherwise
   * the regions the device library proposes for the mask in force.
   */
  public static Mask effective(ImageElement image, MediaSeries<?> series) {
    Mask mask = read(image);
    if (mask == null) {
      mask = read(series);
    }
    return mask == null ? library(image, series) : mask;
  }

  /**
   * The regions of the device entry that applies, or null. Empty while nothing masks the display:
   * hiding burned-in text during a normal reading would take away what the user reads the image
   * with.
   */
  public static Mask library(ImageElement image, MediaSeries<?> series) {
    Optional<IdentityMask> active = IdentityMask.active();
    if (active.isEmpty() || image == null) {
      return null;
    }
    IdentityMask mask = active.get();
    String profileId = mask.profile().map(MaskingProfile::id).orElse(null);
    return PixelMaskMatcher.best(series, image, profileId)
        .map(
            entry ->
                of(
                    entry,
                    image,
                    region -> mask.actionFor(region.category()) != AnonymizationAction.KEEP))
        .orElse(null);
  }

  /**
   * The regions of a device entry on that image, whatever the mask in force keeps: an entry the
   * user picks is applied as it was drawn, where {@link #library} only proposes what the profile
   * does not keep.
   *
   * @return the mask, or null when the image names no size or the entry has no region
   */
  public static Mask of(PixelMask entry, ImageElement image) {
    return image == null ? null : of(entry, image, _ -> true);
  }

  private static Mask of(PixelMask entry, ImageElement image, Predicate<MaskRegion> burn) {
    Integer columns = TagD.getTagValue(image, Tag.Columns, Integer.class);
    Integer rows = TagD.getTagValue(image, Tag.Rows, Integer.class);
    if (columns == null || rows == null || columns <= 0 || rows <= 0) {
      return null;
    }
    List<Shape> shapes = new ArrayList<>();
    for (MaskRegion region : entry.regions()) {
      if (burn.test(region)) {
        shapes.add(region.toShape(columns, rows));
      }
    }
    return shapes.isEmpty() ? null : new Mask(shapes, entry.id());
  }

  /**
   * Copies the regions the library proposes onto {@code target} as the user's own, so an edit
   * starts from what is displayed and leaves the library entry alone.
   *
   * @return the mask now stored on the target, or null when there was nothing to copy
   */
  public static Mask materialize(ImageElement image, MediaSeries<?> series, Taggable target) {
    if (read(target) != null) {
      return read(target);
    }
    Mask mask = effective(image, series);
    if (mask == null || !mask.fromLibrary()) {
      return mask;
    }
    Mask copy = new Mask(mask.shapes(), null);
    write(target, copy);
    return copy;
  }

  /**
   * Whether that image should be looked at before it leaves Weasis: its pixels may carry identity
   * and nothing is burned over them. Installed as the {@link org.weasis.core.api.media.data
   * .PixelReviewAdvisor} of the application by the codec.
   */
  public static boolean requiresReview(MediaElement media, MediaSeries<?> series) {
    if (!(media instanceof ImageElement image)) {
      return false;
    }
    Mask mask = effective(image, series);
    if (mask != null && !mask.isEmpty()) {
      return false;
    }
    String modality = TagD.getTagValue(series, Tag.Modality, String.class);
    return PixelPrivacy.requiresReview(image, modality);
  }

  /** Hides nothing on that image or series, device entries included, until it is cleared. */
  public static void suppress(Taggable taggable) {
    write(taggable, new Mask(List.of(), null));
  }

  /**
   * Stores {@code mask} for that scope. On a series it also frees its frames of what the series now
   * carries: an empty mask, which exists only to hide what the scope below proposed, and a copy of
   * the same device entry applied to one frame first — the series answers for both from now on, and
   * a removal made on the series must take them away with it. Regions drawn on a frame are left
   * alone and keep winning.
   */
  public static void apply(ImageElement image, MediaSeries<?> series, Scope scope, Mask mask) {
    if (scope != Scope.SERIES) {
      write(image, mask);
      return;
    }
    write(series, mask);
    freeFrames(series, mask);
  }

  /** Drops the empty masks of the frames of {@code series}, which hide its regions one by one. */
  public static void unsuppressFrames(MediaSeries<?> series) {
    freeFrames(series, null);
  }

  /**
   * Frees the frames of a series from what the series itself now carries: an empty mask, and a copy
   * of the same device entry applied to one frame before the whole series. Both would outlive a
   * removal made on the series, which reaches the series tag only.
   */
  private static void freeFrames(MediaSeries<?> series, Mask applied) {
    if (series == null) {
      return;
    }
    for (Object media : series.getMedias(null, null)) {
      if (media instanceof ImageElement frame && superseded(read(frame), applied)) {
        clear(frame);
      }
    }
  }

  /** Regions drawn on a frame are never superseded: they are an edit, and they keep winning. */
  private static boolean superseded(Mask stored, Mask applied) {
    if (stored == null) {
      return false;
    }
    return stored.isEmpty()
        || (applied != null && applied.fromLibrary() && applied.origin().equals(stored.origin()));
  }

  /**
   * Takes back the regions of one scope: what that scope stores is dropped, and what would then
   * show again from below — the series mask under an image, an entry of the device library — is
   * hidden by an empty mask instead, or clearing would put the same regions back at once.
   *
   * <p>Dropping the tag matters: an empty mask left on an image wins over every series mask stored
   * afterwards, and the series would look unmasked on that frame alone.
   */
  public static void reset(ImageElement image, MediaSeries<?> series, Scope scope) {
    Taggable target = scope == Scope.SERIES ? series : image;
    clear(target);
    Mask below = scope == Scope.SERIES ? library(image, series) : effective(image, series);
    if (below != null && !below.isEmpty()) {
      suppress(target);
    }
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
