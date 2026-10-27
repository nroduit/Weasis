/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.viewer2d.subtraction;

import jakarta.json.JsonObject;
import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import org.dcm4che3.data.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.explorer.ObservableEvent;
import org.weasis.core.api.explorer.model.DataExplorerModel;
import org.weasis.core.api.gui.util.GuiExecutor;
import org.weasis.core.api.media.data.MediaElement;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.media.data.MediaSeriesGroup;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.ui.editor.SeriesResolver;
import org.weasis.core.ui.editor.ViewerOpenOptions;
import org.weasis.core.ui.editor.ViewerPluginBuilder;
import org.weasis.core.ui.editor.image.ImageViewerPlugin;
import org.weasis.core.ui.editor.image.ViewCanvas;
import org.weasis.core.ui.editor.image.ViewDisplayState;
import org.weasis.core.util.FileUtil;
import org.weasis.core.util.StringUtil;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.codec.DicomSeries;
import org.weasis.dicom.codec.TagD;
import org.weasis.dicom.explorer.DicomModel;
import org.weasis.dicom.viewer2d.EventManager;
import org.weasis.dicom.viewer2d.View2dContainer;
import org.weasis.dicom.viewer2d.subtraction.SubtractionCompatibility.Pairing;
import org.weasis.dicom.viewer2d.subtraction.SubtractionCompatibility.Refusal;
import org.weasis.dicom.viewer2d.subtraction.SubtractionCompatibility.Result;

/**
 * Headless entry point of the subtraction: finds the series a mask can be taken from, proposes one,
 * builds the derived series of a spec and registers it in the study of its source, lists,
 * describes, opens and deletes the derived series. The commands and the panel both call it.
 */
public final class SubtractionService {

  private static final Logger LOGGER = LoggerFactory.getLogger(SubtractionService.class);

  /** A derived series known to this session, with what its computation reported. */
  public record Derived(
      SubtractionSpec spec,
      DicomSeries series,
      MediaSeries<DicomImageElement> source,
      MediaSeries<DicomImageElement> mask,
      Path cacheDir,
      int slices,
      long clipped,
      boolean sameFrameOfReference) {

    public String seriesUid() {
      return spec.derivedSeriesUid();
    }

    /** Whether the series is still in the model it was added to. */
    boolean isRegistered() {
      return modelOf(series) instanceof DicomModel model
          && model.getSeriesNode(seriesUid()) != null;
    }
  }

  private static final Map<String, Derived> DERIVED = new ConcurrentHashMap<>();

  private SubtractionService() {}

  /** The series of the same study a mask can be taken from, on the direct path. */
  @SuppressWarnings("unchecked")
  public static List<MediaSeries<DicomImageElement>> compatibleMasks(
      MediaSeries<DicomImageElement> series) {
    List<MediaSeries<DicomImageElement>> masks = new ArrayList<>();
    if (series == null || isDerived(series) || !(modelOf(series) instanceof DicomModel model)) {
      return masks;
    }
    MediaSeriesGroup study = model.getParent(series, DicomModel.study);
    if (study == null) {
      return masks;
    }
    for (MediaSeriesGroup group : model.getChildren(study)) {
      if (group instanceof MediaSeries<?> candidate
          && candidate.getTagValue(TagW.Derivation) == null
          && SubtractionCompatibility.isCompatible(
              series, (MediaSeries<DicomImageElement>) candidate)) {
        masks.add((MediaSeries<DicomImageElement>) candidate);
      }
    }
    return masks;
  }

  /**
   * The mask a reader would pick for the enhancement case: among the candidates, those whose
   * description is the one of the series once the contrast markers are removed ({@code T1_TRANS}
   * for {@code T1_TRANS+C}) come first; then the series acquired without contrast agent when the
   * series names one; then the earliest; then the lowest series number.
   */
  public static Optional<MediaSeries<DicomImageElement>> proposeMask(
      MediaSeries<DicomImageElement> series, List<MediaSeries<DicomImageElement>> candidates) {
    if (candidates.isEmpty()) {
      return Optional.empty();
    }
    String stem = stem(series);
    boolean withAgent = StringUtil.hasText(contrastAgent(series));
    return candidates.stream()
        .min(
            Comparator.comparing((MediaSeries<DicomImageElement> c) -> !stem.equals(stem(c)))
                .thenComparing(c -> withAgent && StringUtil.hasText(contrastAgent(c)))
                .thenComparing(SubtractionService::acquisitionTime)
                .thenComparing(SubtractionService::seriesNumber));
  }

  /** Marks of a contrast phase in a series description, in the usual spellings. */
  private static final Pattern CONTRAST_MARKS =
      Pattern.compile(
          "\\+\\s*c\\b|\\bc\\s*\\+|\\b(post|pre|gd|gado|gadolinium|contrast|ce|km|inj)\\b|[^a-z0-9]+",
          Pattern.CASE_INSENSITIVE);

  /** The description without its contrast marks, spaces and case: the sequence it names. */
  static String stem(MediaSeries<DicomImageElement> series) {
    String description = TagD.getTagValue(series, Tag.SeriesDescription, String.class);
    return description == null
        ? ""
        : CONTRAST_MARKS.matcher(description.toLowerCase(Locale.ROOT)).replaceAll("");
  }

  /**
   * Builds the derived series of the spec, or returns it when this session already built it and the
   * explorer still holds it.
   *
   * @throws IllegalStateException when the operands are not on the same grid
   * @throws IOException when a slice cannot be written
   */
  public static Derived create(SubtractionSpec spec) throws IOException {
    MediaSeries<DicomImageElement> series = resolve(spec.seriesUid());
    MediaSeries<DicomImageElement> mask = resolve(spec.maskUid());
    return create(spec, series, mask);
  }

  public static Derived create(
      SubtractionSpec spec,
      MediaSeries<DicomImageElement> series,
      MediaSeries<DicomImageElement> mask)
      throws IOException {
    Derived known = DERIVED.get(spec.derivedSeriesUid());
    if (known != null && known.isRegistered()) {
      return known;
    }
    if (isDerived(series) || isDerived(mask)) {
      throw new IllegalStateException("A series Weasis derived cannot be an operand");
    }
    Result result = SubtractionCompatibility.pair(series, mask);
    if (result instanceof Refusal refusal) {
      throw new IllegalStateException(
          "The mask cannot be subtracted from the series: " + refusal.reason());
    }
    Pairing pairing = (Pairing) result;
    if (!pairing.sameFrameOfReference()) {
      LOGGER.warn(
          "Subtracting {} from {} without a frame of reference: the grids are assumed aligned",
          spec.maskUid(),
          spec.seriesUid());
    }
    SubtractionBuilder.Result built = SubtractionBuilder.build(spec, series, mask, pairing);
    Derived derived =
        new Derived(
            spec,
            built.series(),
            series,
            mask,
            built.cacheDir(),
            built.slices(),
            built.clipped(),
            pairing.sameFrameOfReference());
    GuiExecutor.invokeAndRethrow(() -> register(derived));
    DERIVED.put(derived.seriesUid(), derived);
    LOGGER.info(
        "Subtraction {} built: {} slices, {} clipped pixels",
        derived.seriesUid(),
        built.slices(),
        built.clipped());
    return derived;
  }

  /** Adds the derived series to the study of its source, where the explorer lists it. */
  private static void register(Derived derived) {
    if (!(modelOf(derived.source()) instanceof DicomModel model)) {
      throw new IllegalStateException("The series is not held by the DICOM explorer");
    }
    MediaSeriesGroup study = model.getParent(derived.source(), DicomModel.study);
    if (study == null) {
      throw new IllegalStateException("The series has no study");
    }
    DicomSeries series = derived.series();
    series.setTag(TagW.ExplorerModel, model);
    model.addHierarchyNode(study, series);
    model.firePropertyChange(
        new ObservableEvent(ObservableEvent.BasicAction.ADD, model, null, series));
  }

  /** The derived series this session built that the explorer still holds. */
  public static List<Derived> list(String studyUid) {
    List<Derived> result = new ArrayList<>();
    for (Derived derived : DERIVED.values()) {
      if (!derived.isRegistered()) {
        DERIVED.remove(derived.seriesUid());
        continue;
      }
      if (studyUid == null || studyUid.equals(studyUid(derived.source()))) {
        result.add(derived);
      }
    }
    result.sort(Comparator.comparing(Derived::seriesUid));
    return result;
  }

  public static Optional<Derived> find(String seriesUid) {
    Derived derived = seriesUid == null ? null : DERIVED.get(seriesUid);
    if (derived != null && !derived.isRegistered()) {
      DERIVED.remove(seriesUid);
      return Optional.empty();
    }
    return Optional.ofNullable(derived);
  }

  /** Removes the derived series from the explorer and its slices from the cache. */
  public static boolean delete(String seriesUid) {
    Derived derived = DERIVED.remove(seriesUid);
    if (derived == null) {
      return false;
    }
    GuiExecutor.invokeAndRethrow(
        () -> {
          if (modelOf(derived.series()) instanceof DicomModel model) {
            MediaSeriesGroup node = model.getSeriesNode(seriesUid);
            if (node != null) {
              model.removeSeries(node);
            }
          }
        });
    FileUtil.delete(derived.cacheDir());
    return true;
  }

  /**
   * Shows the derived series in the selected pane when a 2D viewer of the same patient is selected,
   * else in a new viewer; returns the pane it went to when it went to one. EDT only.
   */
  public static JsonObject open(Derived derived) {
    DicomSeries series = derived.series();
    DataExplorerModel model = modelOf(series);
    ImageViewerPlugin<DicomImageElement> container =
        EventManager.getInstance().getSelectedView2dContainer();
    if (container instanceof View2dContainer viewer
        && model instanceof DicomModel dicomModel
        && viewer.getSelectedViewCanvas() != null
        && viewer.getGroupID() != null
        && viewer
            .getGroupID()
            .equals(dicomModel.getParent(series, model.getTreeModelNodeForNewPlugin()))) {
      ViewCanvas<DicomImageElement> view = viewer.getSelectedViewCanvas();
      viewer.setSeriesToView(view, series);
      viewer.setSelectedImagePane(view);
      return ViewDisplayState.target(viewer, view).build();
    }
    ViewerPluginBuilder.openInDefaultViewer(series, model, ViewerOpenOptions.defaults());
    return null;
  }

  public static String studyUid(MediaSeries<DicomImageElement> series) {
    DicomImageElement first = series.getMedia(MediaSeries.MEDIA_POSITION.FIRST, null, null);
    return first == null ? null : TagD.getTagValue(first, Tag.StudyInstanceUID, String.class);
  }

  @SuppressWarnings("unchecked")
  private static MediaSeries<DicomImageElement> resolve(String uid) {
    MediaSeries<? extends MediaElement> series =
        SeriesResolver.ofApplication()
            .resolve(uid)
            .orElseThrow(() -> new IllegalArgumentException("Unknown series: " + uid));
    if (series.getMedia(MediaSeries.MEDIA_POSITION.FIRST, null, null)
        instanceof DicomImageElement) {
      return (MediaSeries<DicomImageElement>) series;
    }
    throw new IllegalStateException("The series holds no image: " + uid);
  }

  /** A series Weasis derived in this session (subtraction, MIP) is never an operand. */
  private static boolean isDerived(MediaSeries<?> series) {
    return series != null && series.getTagValue(TagW.Derivation) != null;
  }

  private static DataExplorerModel modelOf(MediaSeries<?> series) {
    return series.getTagValue(TagW.ExplorerModel) instanceof DataExplorerModel model ? model : null;
  }

  private static String contrastAgent(MediaSeries<DicomImageElement> series) {
    DicomImageElement first = series.getMedia(MediaSeries.MEDIA_POSITION.FIRST, null, null);
    return first == null ? null : TagD.getTagValue(first, Tag.ContrastBolusAgent, String.class);
  }

  private static LocalTime acquisitionTime(MediaSeries<DicomImageElement> series) {
    DicomImageElement first = series.getMedia(MediaSeries.MEDIA_POSITION.FIRST, null, null);
    LocalTime time =
        first == null ? null : TagD.getTagValue(first, Tag.AcquisitionTime, LocalTime.class);
    if (time == null) {
      time = TagD.getTagValue(series, Tag.SeriesTime, LocalTime.class);
    }
    return time == null ? LocalTime.MAX : time;
  }

  private static int seriesNumber(MediaSeries<DicomImageElement> series) {
    Integer number = TagD.getTagValue(series, Tag.SeriesNumber, Integer.class);
    return number == null ? Integer.MAX_VALUE : number;
  }
}
