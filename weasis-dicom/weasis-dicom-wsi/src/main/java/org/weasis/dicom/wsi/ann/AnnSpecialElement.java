/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.wsi.ann;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.CompletableFuture;
import org.dcm4che3.data.Attributes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.dicom.codec.DicomMediaIO;
import org.weasis.dicom.codec.DicomSpecialElement;
import org.weasis.dicom.wsi.ann.BulkAnnotations.Group;
import org.weasis.dicom.wsi.model.PyramidLevel;
import org.weasis.dicom.wsi.model.Slide;

/**
 * A Microscopy Bulk Simple Annotations object of the study: read against the slide it annotates the
 * first time a view of that slide asks, its groups indexed in the background, and shown or hidden
 * group by group.
 */
public class AnnSpecialElement extends DicomSpecialElement {
  private static final Logger LOGGER = LoggerFactory.getLogger(AnnSpecialElement.class);

  /** The indexed groups of one slide, built in the background. */
  public static final class SlideAnnotations {
    private final BulkAnnotations annotations;
    private final List<AnnotationIndex> indexes = new ArrayList<>();
    private volatile boolean ready;

    SlideAnnotations(BulkAnnotations annotations) {
      this.annotations = annotations;
    }

    public List<Group> groups() {
      return annotations.groups();
    }

    /** The indexes of the groups once built, in the order of the groups; empty before. */
    public List<AnnotationIndex> indexes() {
      return ready ? List.copyOf(indexes) : List.of();
    }

    public boolean isReady() {
      return ready;
    }
  }

  private final Set<String> referencedInstances;
  private final Map<Slide, SlideAnnotations> bySlide = new WeakHashMap<>();
  private final BitSet hidden = new BitSet();

  public AnnSpecialElement(DicomMediaIO mediaIO) {
    super(mediaIO);
    Attributes dcm = mediaIO.getDicomObject();
    this.referencedInstances = dcm == null ? Set.of() : BulkAnnotations.referencedInstances(dcm);
  }

  // "ANN [1] <description>", the model of the converter when the series says nothing
  @Override
  protected void initLabel() {
    String desc =
        org.weasis.dicom.codec.TagD.getTagValue(
            this, org.dcm4che3.data.Tag.SeriesDescription, String.class);
    if (desc == null || desc.isBlank()) {
      Attributes dcm = getMediaReader() == null ? null : getMediaReader().getDicomObject();
      desc = dcm == null ? null : dcm.getString(org.dcm4che3.data.Tag.ManufacturerModelName);
    }
    label = (getLabelPrefix() + (desc == null ? "" : desc)).trim();
  }

  /** Whether the object annotates an instance of the slide; cheap. */
  public boolean appliesTo(Slide slide) {
    for (PyramidLevel level : slide.levels()) {
      for (String uid : level.instanceUIDs()) {
        if (referencedInstances.contains(uid)) {
          return true;
        }
      }
    }
    return false;
  }

  /**
   * The annotations of a slide, read on the first call; their indexes are built in the background
   * and {@code whenReady} runs once they are, from a worker thread.
   */
  public synchronized Optional<SlideAnnotations> annotations(Slide slide, Runnable whenReady) {
    SlideAnnotations known = bySlide.get(slide);
    if (known != null) {
      return Optional.of(known);
    }
    Attributes dcm = getMediaReader() == null ? null : getMediaReader().getDicomObject();
    if (dcm == null) {
      return Optional.empty();
    }
    Optional<BulkAnnotations> read = BulkAnnotations.read(dcm, slide);
    if (read.isEmpty()) {
      return Optional.empty();
    }
    SlideAnnotations result = new SlideAnnotations(read.get());
    bySlide.put(slide, result);
    CompletableFuture.runAsync(
        () -> {
          try {
            long start = System.nanoTime();
            for (Group group : result.annotations.groups()) {
              result.indexes.add(AnnotationIndex.build(group, slide.width(), slide.height()));
            }
            result.ready = true;
            LOGGER.info(
                "Indexed {} annotation group(s) of {} in {} ms",
                result.indexes.size(),
                getLabel(),
                (System.nanoTime() - start) / 1_000_000);
            whenReady.run();
          } catch (RuntimeException e) {
            LOGGER.warn("Cannot index the annotations of {}", getLabel(), e);
          }
        });
    return Optional.of(result);
  }

  /** Whether a group, by its position in the groups, is drawn. */
  public boolean isShown(int group) {
    return !hidden.get(group);
  }

  public void setShown(int group, boolean shown) {
    hidden.set(group, !shown);
  }
}
