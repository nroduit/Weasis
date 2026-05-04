/*
 * Copyright (c) 2021 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.codec.seg;

import java.awt.Color;
import java.awt.EventQueue;
import java.lang.ref.SoftReference;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Set;
import java.util.TreeMap;
import java.util.WeakHashMap;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import javax.swing.tree.DefaultMutableTreeNode;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Sequence;
import org.dcm4che3.data.Tag;
import org.dcm4che3.img.data.CIELab;
import org.dcm4che3.img.util.DicomUtils;
import org.joml.Vector3d;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.explorer.DataExplorerView;
import org.weasis.core.api.explorer.ObservableEvent;
import org.weasis.core.api.gui.util.DecFormatter;
import org.weasis.core.api.gui.util.GuiExecutor;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.image.measure.MeasurementsAdapter;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.api.util.ResourceUtil.OtherIcon;
import org.weasis.core.api.util.ResourceUtil.ResourceIconPath;
import org.weasis.core.ui.model.graphic.imp.seg.SegMeasurableLayer;
import org.weasis.core.ui.model.graphic.imp.seg.SegRegion;
import org.weasis.core.ui.util.StructToolTipTreeNode;
import org.weasis.core.util.StringUtil;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.codec.DicomMediaIO;
import org.weasis.dicom.codec.DicomSeries;
import org.weasis.dicom.codec.HiddenSeriesManager;
import org.weasis.dicom.codec.HiddenSpecialElement;
import org.weasis.dicom.codec.SpecialElementReferences;
import org.weasis.dicom.codec.SpecialElementRegion;
import org.weasis.dicom.codec.TagD;
import org.weasis.dicom.codec.geometry.VectorUtils;
import org.weasis.dicom.codec.utils.DicomMediaUtils;
import org.weasis.dicom.macro.Code;
import org.weasis.opencv.seg.RegionAttributes;

public class SegSpecialElement extends HiddenSpecialElement
    implements SpecialElementReferences, SpecialElementRegion {
  private static final Logger LOGGER = LoggerFactory.getLogger(SegSpecialElement.class);

  /**
   * Pluggable scheduler for the asynchronous build of the canonical segmentation volume. The
   * weasis-dicom-explorer module installs a UI-aware executor that surfaces the build as a
   * cancellable task in the explorer's bottom loading panel; in headless contexts the {@link
   * SegmentationVolumeBuildExecutor#DEFAULT default} fork-join pool executor is used.
   */
  private static volatile SegmentationVolumeBuildExecutor volumeBuildExecutor =
      SegmentationVolumeBuildExecutor.DEFAULT;

  /**
   * Installs a custom scheduler for the asynchronous canonical segmentation volume build (e.g. the
   * UI-aware executor registered by the DICOM explorer bundle). Passing {@code null} restores the
   * default fork-join pool executor.
   */
  public static void setVolumeBuildExecutor(SegmentationVolumeBuildExecutor exec) {
    volumeBuildExecutor = exec == null ? SegmentationVolumeBuildExecutor.DEFAULT : exec;
  }

  /** Tracks the first/last frame index a given region appears in (sentinel {@code -1} = unused). */
  private record FrameRange(int first, int last) {
    static final FrameRange EMPTY = new FrameRange(-1, -1);

    FrameRange withFrame(int idx) {
      return first == -1 ? new FrameRange(idx, idx) : new FrameRange(first, idx);
    }

    int middle() {
      return (first + last) / 2;
    }
  }

  /** Recognised values of (0062,0001) SegmentationType. */
  private enum SegmentationKind {
    BINARY,
    FRACTIONAL,
    LABELMAP;

    static SegmentationKind fromDicom(String type) {
      if (type == null) return BINARY;
      return switch (type.toUpperCase(Locale.US)) {
        case "FRACTIONAL" -> FRACTIONAL;
        case "LABELMAP" -> LABELMAP;
        default -> BINARY;
      };
    }
  }

  private final Map<String, Map<String, Set<LazyContourLoader>>> refMap = new HashMap<>();
  private final Map<Integer, LazyContourLoader> roiMap = new HashMap<>();
  private final TreeMap<Double, Set<LazyContourLoader>> positionMap = new TreeMap<>();
  private final Map<Integer, SegRegion<DicomImageElement>> segAttributes = new HashMap<>();

  /**
   * Sign-normalized unit normal of the segmentation plane, captured from the first frame that
   * contributes to the {@link #positionMap}. Used by {@link SpecialElementRegion#getContours} to
   * project the queried image's IPP onto a comparable axis even when the image and segmentation use
   * different row/column directions or sign conventions.
   */
  private volatile Vector3d referenceNormal;

  /**
   * Lazily-built canonical 3D segmentation volume, kept under a {@link SoftReference} so it can be
   * reclaimed under memory pressure and re-built on demand. Shared by 2D overlays (when image and
   * SEG orientations diverge), MPR overlays and Volume Rendering.
   */
  private volatile SoftReference<SegmentationVolume> segVolumeRef;

  private final Object segVolumeLock = new Object();

  /**
   * Per–image-volume cache of <em>image-aligned</em> {@link SegmentationVolume}s, i.e. SEG copies
   * that were resampled onto a specific display image volume's voxel grid (used by MPR overlays and
   * the 3D segmentation texture so the same combined transform / texture coords address both the
   * image and the SEG). The key is the consumer-supplied identity of the image volume (no {@code
   * equals}/{@code hashCode} requirements — the {@link WeakHashMap} relies on identity); the value
   * is held under a {@link SoftReference} so the JVM can reclaim it under memory pressure. When the
   * image volume becomes unreachable the {@link WeakHashMap} entry disappears automatically,
   * allowing the soft-referenced volume to be collected.
   *
   * <p>Access is guarded by {@link #segVolumeLock} so building, lookup and disposal stay coherent
   * with the canonical-volume state machine.
   */
  private final Map<Object, SoftReference<SegmentationVolume>> alignedVolumes = new WeakHashMap<>();

  /**
   * In-flight asynchronous build of the canonical segmentation volume. While non-null, calls to
   * {@link #getOrBuildSegmentationVolume()} return {@code null} immediately (so the EDT is never
   * blocked by {@link SegmentationVolumeBuilder#buildCanonical}). When the build completes a {@link
   * ObservableEvent.BasicAction#UPDATE} event is fired so views can repaint and pick up the
   * now-cached volume.
   */
  private volatile CompletableFuture<SegmentationVolume> segVolumeBuildFuture;

  /**
   * Sticky flag set when the user cancels (or the build otherwise fails with a {@link
   * CancellationException}) the asynchronous canonical volume build. Once set, every subsequent
   * call to {@link #getOrBuildSegmentationVolume()} returns {@code null} without re-scheduling the
   * work, so scrolling / repainting does not silently re-trigger the heavy build the user just
   * aborted. Cleared by {@link #retrySegmentationVolumeBuild()} (explicit user request) or {@link
   * #disposeSegmentationVolume()} (full reset).
   */
  private volatile boolean segVolumeBuildAborted;

  /**
   * Native slice spacing of the SEG (in mm) along {@link #referenceNormal}, captured from
   * PixelMeasuresSequence (SpacingBetweenSlices, fallback SliceThickness) on the first frame that
   * contributes to the {@link #positionMap}. Used to:
   *
   * <ul>
   *   <li>quantise positionMap keys so that frames belonging to different segments but lying on the
   *       same canonical Z (multi-segment highdicom output) collapse onto a single map entry;
   *   <li>widen the tolerance in {@link SpecialElementRegion#getContours} when the queried image's
   *       slice grid does not coincide with the SEG's finer grid.
   * </ul>
   */
  private volatile double segSliceSpacing;

  private volatile float opacity = 1.0f;
  private volatile boolean visible = true;

  /**
   * Async loading lifecycle. {@link #initContours} flips this to {@code LOADING} on entry and to
   * {@code READY} or {@code FAILED} on exit. Consumers (2D overlays, MPR, 3D, the SegmentationTool
   * tree) gate their reads on {@link #isReady()} so that a partially-built SEG is never queried.
   */
  public enum LoadState {
    PENDING,
    LOADING,
    READY,
    FAILED
  }

  private volatile LoadState loadState = LoadState.PENDING;

  public SegSpecialElement(DicomMediaIO mediaIO) {
    super(mediaIO);
  }

  /** Returns {@code true} when contours have been fully built and are safe to consume. */
  @Override
  public boolean isReady() {
    return loadState == LoadState.READY;
  }

  public LoadState getLoadState() {
    return loadState;
  }

  /** Releases all data built by {@link #initContours} and the cached segmentation volume. */
  public void disposeContours() {
    roiMap.clear();
    positionMap.clear();
    segAttributes.clear();
    referenceNormal = null;
    segSliceSpacing = 0;
    disposeSegmentationVolume();
  }

  /** Returns the underlying DICOM dataset, or {@code null} if the media reader is missing. */
  private Attributes dicomObject() {
    return mediaIO == null ? null : ((DicomMediaIO) mediaIO).getDicomObject();
  }

  public static DefaultMutableTreeNode buildStructRegionNode(SegRegion<?> contour) {
    return new StructToolTipTreeNode(contour, false) {
      @Override
      public String getToolTipText() {
        return buildSegRegionTooltip((SegRegion<?>) getUserObject());
      }
    };
  }

  private static String buildSegRegionTooltip(SegRegion<?> seg) {
    StringBuilder buf = new StringBuilder();
    buf.append(GuiUtils.HTML_START)
        .append("<b>")
        .append(seg.getLabel())
        .append("</b>")
        .append(GuiUtils.HTML_BR);
    appendLine(buf, "Algorithm type", seg.getType());
    appendLine(buf, "Algorithm name", seg.getAlgorithmName());
    appendList(buf, "Categories", seg.getCategories());
    appendList(buf, "Anatomic regions", seg.getAnatomicRegionCodes());
    appendVoxelCount(buf, seg);
    appendVolume(buf, seg);
    if (seg.isFractional()) {
      appendFractionalLut(buf, seg);
    }
    buf.append(GuiUtils.HTML_END);
    return buf.toString();
  }

  private static void appendLine(StringBuilder buf, String label, String value) {
    if (StringUtil.hasText(value)) {
      buf.append(label).append(StringUtil.COLON_AND_SPACE).append(value).append(GuiUtils.HTML_BR);
    }
  }

  private static void appendList(StringBuilder buf, String label, List<String> values) {
    if (values != null && !values.isEmpty()) {
      buf.append(label)
          .append(StringUtil.COLON_AND_SPACE)
          .append(String.join(", ", values))
          .append(GuiUtils.HTML_BR);
    }
  }

  private static void appendVoxelCount(StringBuilder buf, SegRegion<?> seg) {
    buf.append("Voxel count");
    if (seg.isFractional()) {
      buf.append(" (weighted");
      if (StringUtil.hasText(seg.getFractionalType())) {
        buf.append(", ").append(seg.getFractionalType().toLowerCase());
      }
      buf.append(")");
    }
    buf.append(StringUtil.COLON_AND_SPACE)
        .append(DecFormatter.allNumber(seg.getNumberOfPixels()))
        .append(GuiUtils.HTML_BR);
  }

  private static void appendVolume(StringBuilder buf, SegRegion<?> seg) {
    SegMeasurableLayer<?> layer = seg.getMeasurableLayer();
    if (layer == null) {
      return;
    }
    MeasurementsAdapter adapter =
        layer.getMeasurementAdapter(layer.getSourceImage().getPixelSpacingUnit());
    double ratio = adapter.calibrationRatio();
    buf.append("Volume (%s3)".formatted(adapter.unit()))
        .append(StringUtil.COLON_AND_SPACE)
        .append(
            DecFormatter.twoDecimal(seg.getNumberOfPixels() * ratio * ratio * layer.getThickness()))
        .append(GuiUtils.HTML_BR);
  }

  private static void appendFractionalLut(StringBuilder buf, SegRegion<?> seg) {
    boolean occupancy = "OCCUPANCY".equalsIgnoreCase(seg.getFractionalType());
    String unit = occupancy ? " %" : "";
    String minLabel = "0" + unit;
    String maxLabel = (occupancy ? "100" : "1") + unit;
    buf.append("LUT")
        .append(StringUtil.COLON_AND_SPACE)
        .append(GuiUtils.HTML_BR)
        .append(
            StructToolTipTreeNode.buildHorizontalLutBar(seg.getColor(), 24, minLabel, maxLabel));
  }

  @Override
  protected void initLabel() {
    StringBuilder buf = new StringBuilder();
    Integer val = TagD.getTagValue(this, Tag.InstanceNumber, Integer.class);
    if (val != null) {
      buf.append("[").append(val).append("] ");
    }
    Attributes dicom = dicomObject();
    if (dicom != null) {
      String item = dicom.getString(Tag.SeriesDescription);
      if (item != null) {
        buf.append(item);
      }
      item = dicom.getString(Tag.ContentLabel);
      if (item != null) {
        buf.append(" - ").append(item);
      }
    }
    label = buf.toString();
  }

  public Map<String, Map<String, Set<LazyContourLoader>>> getRefMap() {
    return refMap;
  }

  @Override
  public NavigableMap<Double, Set<LazyContourLoader>> getPositionMap() {
    return positionMap;
  }

  @Override
  public Vector3d getReferenceNormal() {
    Vector3d n = referenceNormal;
    return n == null ? null : new Vector3d(n);
  }

  @Override
  public double getSliceSpacing() {
    return segSliceSpacing;
  }

  @Override
  public Vector3d getMaskRowDirection() {
    double[] iop = readSegIop();
    return iop == null ? null : new Vector3d(iop[0], iop[1], iop[2]);
  }

  @Override
  public Vector3d getMaskColumnDirection() {
    double[] iop = readSegIop();
    return iop == null ? null : new Vector3d(iop[3], iop[4], iop[5]);
  }

  @Override
  public double[] getMaskPixelSpacing() {
    Attributes dicom = dicomObject();
    if (dicom == null) {
      return null;
    }
    double[] ps =
        readNestedDoubleArray(
            dicom, Tag.SharedFunctionalGroupsSequence, Tag.PixelMeasuresSequence, Tag.PixelSpacing);
    if (ps != null && ps.length >= 2) {
      return ps;
    }
    return DicomUtils.getDoubleArrayFromDicomElement(dicom, Tag.PixelSpacing, null);
  }

  @Override
  public int getMaskWidth() {
    Attributes dicom = dicomObject();
    return dicom == null ? 0 : dicom.getInt(Tag.Columns, 0);
  }

  @Override
  public int getMaskHeight() {
    Attributes dicom = dicomObject();
    return dicom == null ? 0 : dicom.getInt(Tag.Rows, 0);
  }

  private double[] readSegIop() {
    Attributes dicom = dicomObject();
    if (dicom == null) {
      return null;
    }
    double[] iop =
        readNestedDoubleArray(
            dicom,
            Tag.SharedFunctionalGroupsSequence,
            Tag.PlaneOrientationSequence,
            Tag.ImageOrientationPatient);
    if (iop != null && iop.length == 6) {
      return iop;
    }
    return DicomUtils.getDoubleArrayFromDicomElement(dicom, Tag.ImageOrientationPatient, null);
  }

  /** Reads a double array nested two sequences deep ({@code container/outer/inner[valueTag]}). */
  private static double[] readNestedDoubleArray(
      Attributes container, int outerSeqTag, int innerSeqTag, int valueTag) {
    Attributes outer = container.getNestedDataset(outerSeqTag);
    if (outer == null) {
      return null;
    }
    Attributes inner = outer.getNestedDataset(innerSeqTag);
    if (inner == null) {
      return null;
    }
    return DicomUtils.getDoubleArrayFromDicomElement(inner, valueTag, null);
  }

  /**
   * Returns the cached canonical {@link SegmentationVolume} if one is currently held under the soft
   * reference, without ever triggering a (synchronous or asynchronous) build. Useful from code
   * paths that only want to opportunistically reuse an existing canonical volume — e.g. the MPR /
   * 3D image-aligned SEG builders taking the resample-from-canonical fast path — without paying for
   * a fresh build when the canonical volume is not ready yet.
   *
   * @return the cached canonical volume, or {@code null} when none is cached
   */
  public SegmentationVolume peekCanonicalSegmentationVolume() {
    SoftReference<SegmentationVolume> ref = segVolumeRef;
    return ref == null ? null : ref.get();
  }

  /**
   * Returns the cached canonical {@link SegmentationVolume}, building it lazily on first call (and
   * rebuilding if the soft reference has been cleared by GC). Returns {@code null} when the volume
   * cannot be built.
   *
   * <p>The volume lives on the SEG's own native grid (independent of any displayed image volume),
   * so a single instance can be shared by 2D overlays (across orientations), MPR and Volume
   * Rendering. Callers that need a different grid must resample it.
   */
  @Override
  public SegmentationVolume getOrBuildSegmentationVolume() {
    SoftReference<SegmentationVolume> ref = segVolumeRef;
    SegmentationVolume v = ref == null ? null : ref.get();
    if (v != null) {
      return v;
    }
    // Never block the EDT: building the canonical volume can take seconds for large multi-frame
    // SEGs (per-frame mask decode + canonical resample). When called from a paint pass we
    // schedule the build asynchronously and return null; once the build completes an UPDATE
    // event is fired so the views repaint and the next call hits the cached volume.
    if (EventQueue.isDispatchThread()) {
      // If the user previously canceled the build, do not silently re-launch it on every
      // scroll / repaint — the SEG stays without a 3D volume until the user explicitly retries
      // via retrySegmentationVolumeBuild().
      if (segVolumeBuildAborted) {
        return null;
      }
      ensureAsyncBuild();
      return null;
    }
    synchronized (segVolumeLock) {
      ref = segVolumeRef;
      v = ref == null ? null : ref.get();
      if (v != null) {
        return v;
      }
      DicomSeries series = getMediaReader() == null ? null : getMediaReader().getMediaSeries();
      v = SegmentationVolumeBuilder.buildCanonical(this, series);
      if (v != null) {
        segVolumeRef = new SoftReference<>(v);
      }
      return v;
    }
  }

  /**
   * Schedules a background build of the canonical segmentation volume if none is in flight and the
   * volume is not already cached. Safe to call from any thread; returns immediately. When the build
   * completes (successfully or not), the in-flight future is cleared and an {@link
   * ObservableEvent.BasicAction#UPDATE} event is fired on the owning DICOM model so views can
   * repaint and pick up the freshly-built volume on their next call to {@link
   * #getOrBuildSegmentationVolume()}.
   */
  private void ensureAsyncBuild() {
    SoftReference<SegmentationVolume> ref = segVolumeRef;
    if (ref != null && ref.get() != null) {
      return;
    }
    if (segVolumeBuildFuture != null || segVolumeBuildAborted) {
      return;
    }
    synchronized (segVolumeLock) {
      ref = segVolumeRef;
      if (ref != null && ref.get() != null) {
        return;
      }
      if (segVolumeBuildFuture != null || segVolumeBuildAborted) {
        return;
      }
      DicomSeries series = getMediaReader() == null ? null : getMediaReader().getMediaSeries();
      if (series == null) {
        return;
      }
      CompletableFuture<SegmentationVolume> future =
          volumeBuildExecutor.schedule(
              this, () -> SegmentationVolumeBuilder.buildCanonical(this, series));
      segVolumeBuildFuture = future;
      future.whenComplete(
          (vol, err) -> {
            synchronized (segVolumeLock) {
              if (vol != null) {
                segVolumeRef = new SoftReference<>(vol);
              }
              segVolumeBuildFuture = null;
              if (err instanceof CancellationException) {
                // User aborted the build (or it was cancelled programmatically): remember the
                // decision so paint/scroll passes do not silently re-launch the same heavy work.
                segVolumeBuildAborted = true;
              }
            }
            if (err instanceof CancellationException) {
              // Fully unload the segmentation: drop every per-frame loader, the position map,
              // segment attributes and the reference normal. The next paint/scroll will see an
              // empty SEG (getContours returns null) instead of repeatedly re-triggering work
              // the user just aborted. Done on the EDT to avoid racing with paint reads of the
              // (non-thread-safe) maps.
              GuiExecutor.execute(() -> unloadAfterAbort(series));
            } else if (err != null) {
              LOGGER.error("Async build of canonical segmentation volume failed", err);
            }
            if (vol != null) {
              fireSegUpdateEvent();
            }
          });
    }
  }

  /** Notifies the owning DICOM model that this SEG was updated, so views repaint. */
  private void fireSegUpdateEvent() {
    List<DataExplorerView> explorerPlugins = GuiUtils.getUICore().getExplorerPlugins();
    explorerPlugins.stream()
        .map(DataExplorerView::getDataExplorerModel)
        .filter(m -> "DICOM".equals(m.toString()))
        .findFirst()
        .ifPresent(
            model ->
                model.firePropertyChange(
                    new ObservableEvent(ObservableEvent.BasicAction.UPDATE, model, null, this)));
  }

  @Override
  public boolean isSegmentationVolumeBuilding() {
    return segVolumeBuildFuture != null;
  }

  /**
   * Returns {@code true} when the user (or a programmatic cancel) has aborted the asynchronous
   * canonical volume build. While in this state {@link #getOrBuildSegmentationVolume()} returns
   * {@code null} without re-scheduling the work — call {@link #retrySegmentationVolumeBuild()} to
   * clear the flag and allow a new build attempt.
   */
  public boolean isSegmentationVolumeBuildAborted() {
    return segVolumeBuildAborted;
  }

  /**
   * Clears the "aborted" flag set by a previous cancellation so the canonical segmentation volume
   * can be rebuilt on the next call to {@link #getOrBuildSegmentationVolume()}. Safe to call
   * repeatedly; no-op when no abort is pending.
   */
  public void retrySegmentationVolumeBuild() {
    synchronized (segVolumeLock) {
      segVolumeBuildAborted = false;
    }
  }

  /**
   * Unloads all SEG data after the user aborted the canonical volume build: clears every per-frame
   * loader, the position map, segment attributes and the reference normal, and notifies listeners
   * so the explorer/segmentation tool drop the now-empty SEG. Must be called on the EDT so it does
   * not race with paint reads on the (non-thread-safe) maps. Keeps {@link #segVolumeBuildAborted}
   * set so the next paint/scroll does not re-launch the build.
   */
  private void unloadAfterAbort(DicomSeries series) {
    roiMap.clear();
    refMap.clear();
    positionMap.clear();
    segAttributes.clear();
    referenceNormal = null;
    segSliceSpacing = 0;
    unregisterFromHiddenSeriesManager(series);
    LOGGER.info("Segmentation volume build cancelled — unloading SEG data");
    fireSegUpdateEvent();
  }

  /**
   * Removes this SEG from {@link HiddenSeriesManager}'s registries (series2Elements,
   * patient2Series, reference2Series, sopRef2Series) so the explorer / segmentation tool stop
   * advertising it. Mirrors the cleanup performed by {@link DicomSeries#dispose()} but scoped to
   * this single element rather than the whole hidden series.
   */
  private void unregisterFromHiddenSeriesManager(DicomSeries series) {
    String seriesUID = TagD.getTagValue(series, Tag.SeriesInstanceUID, String.class);
    if (!StringUtil.hasText(seriesUID)) {
      return;
    }
    HiddenSeriesManager manager = HiddenSeriesManager.getInstance();
    Set<HiddenSpecialElement> set = manager.series2Elements.get(seriesUID);
    boolean seriesEmptyAfter = false;
    if (set != null) {
      set.remove(this);
      if (set.isEmpty()) {
        manager.series2Elements.remove(seriesUID);
        seriesEmptyAfter = true;
      }
    }
    String sopUID = TagD.getTagValue(this, Tag.SOPInstanceUID, String.class);
    if (sopUID != null) {
      Set<String> refs = manager.sopRef2Series.get(sopUID);
      if (refs != null) {
        refs.remove(seriesUID);
        if (refs.isEmpty()) {
          manager.sopRef2Series.remove(sopUID);
        }
      }
    }
    if (seriesEmptyAfter) {
      String patientPseudoUID = (String) getTagValue(TagW.PatientPseudoUID);
      if (patientPseudoUID != null) {
        Set<String> list = manager.patient2Series.get(patientPseudoUID);
        if (list != null) {
          list.remove(seriesUID);
          if (list.isEmpty()) {
            manager.patient2Series.remove(patientPseudoUID);
          }
        }
      }
      manager
          .reference2Series
          .entrySet()
          .removeIf(
              entry -> {
                Set<String> referencingSeries = entry.getValue();
                referencingSeries.remove(seriesUID);
                return referencingSeries.isEmpty();
              });
    }
  }

  /** Releases the cached canonical segmentation volume, if any. */
  public void disposeSegmentationVolume() {
    synchronized (segVolumeLock) {
      CompletableFuture<SegmentationVolume> f = segVolumeBuildFuture;
      if (f != null) {
        f.cancel(true);
        segVolumeBuildFuture = null;
      }
      // Full reset: a subsequent getOrBuildSegmentationVolume() should be allowed to rebuild.
      segVolumeBuildAborted = false;
      SoftReference<SegmentationVolume> ref = segVolumeRef;
      if (ref != null) {
        SegmentationVolume v = ref.get();
        if (v != null) {
          v.removeData();
        }
        segVolumeRef = null;
      }
      // Aligned (image-volume-specific) copies are owned by this SEG too: free their native
      // buffers and clear the cache so a later getOrBuildAlignedVolume() rebuilds from scratch.
      // If an active consumer still holds a retain() we log a warning but still free the data
      // because this is a full-reset (the SEG itself is being discarded).
      for (SoftReference<SegmentationVolume> r : alignedVolumes.values()) {
        SegmentationVolume v = r == null ? null : r.get();
        if (v != null) {
          int remaining = v.getUseCount();
          if (remaining > 0) {
            LOGGER.warn(
                "disposeSegmentationVolume: freeing SegmentationVolume with {} active consumer(s)"
                    + " — views displaying this SEG may show stale or empty overlays",
                remaining);
          }
          v.removeData();
        }
      }
      alignedVolumes.clear();
    }
  }

  /**
   * Returns a {@link SegmentationVolume} resampled onto the image-volume grid identified by {@code
   * imageVolumeKey}, building it lazily on the first request and caching the result so MPR overlays
   * and the 3D segmentation texture can share a single instance instead of each resampling the SEG
   * independently.
   *
   * <p>The cache uses identity semantics on {@code imageVolumeKey} (typically the displayed image
   * {@code Volume} instance) and is held under a {@link SoftReference}, so the volume is freed
   * either when the image volume becomes unreachable (entry GC'd from the {@link WeakHashMap}),
   * when the JVM reclaims soft references under pressure, or explicitly via {@link
   * #disposeAlignedVolume(Object)} / {@link #disposeSegmentationVolume()}.
   *
   * <p>The {@code builder} is invoked at most once per cache miss while holding the SEG's volume
   * lock; it should perform the actual resample (e.g. {@code SegVolumeBuilder.build}) and may
   * return {@code null} to indicate the volume cannot be built (in which case nothing is cached).
   *
   * @param imageVolumeKey identity key for the target image volume (must not be {@code null})
   * @param builder factory invoked on a cache miss to produce the resampled volume
   * @return the cached or freshly-built aligned volume, or {@code null} when the build failed
   */
  public SegmentationVolume getOrBuildAlignedVolume(
      Object imageVolumeKey, Function<SegSpecialElement, SegmentationVolume> builder) {
    if (imageVolumeKey == null || builder == null) {
      return null;
    }
    synchronized (segVolumeLock) {
      SoftReference<SegmentationVolume> ref = alignedVolumes.get(imageVolumeKey);
      SegmentationVolume v = ref == null ? null : ref.get();
      if (v != null) {
        return v;
      }
      // Drop the stale soft reference (if any) before rebuilding.
      if (ref != null) {
        alignedVolumes.remove(imageVolumeKey);
      }
      v = builder.apply(this);
      if (v != null) {
        alignedVolumes.put(imageVolumeKey, new SoftReference<>(v));
      }
      return v;
    }
  }

  /**
   * Releases the image-aligned {@link SegmentationVolume} cached for {@code imageVolumeKey} (if
   * any). The cache entry is always removed so a subsequent {@link #getOrBuildAlignedVolume} call
   * rebuilds from scratch.
   *
   * <p>The CPU buffers of the volume are freed immediately <em>only</em> when no consumer is
   * actively holding a {@link SegmentationVolume#retain() retain} on it (i.e. its use-count is
   * zero). When a consumer does hold a retain the data will be freed automatically when that
   * consumer calls {@link SegmentationVolume#release()}.
   */
  public void disposeAlignedVolume(Object imageVolumeKey) {
    if (imageVolumeKey == null) {
      return;
    }
    synchronized (segVolumeLock) {
      SoftReference<SegmentationVolume> ref = alignedVolumes.remove(imageVolumeKey);
      SegmentationVolume v = ref == null ? null : ref.get();
      if (v != null && v.getUseCount() <= 0) {
        // No active consumer retains this volume: free the CPU buffers now.
        v.removeData();
      }
      // If getUseCount() > 0 the data stays alive; the last release() call on the consumer side
      // will call removeData() automatically.
    }
  }

  public Map<Integer, SegRegion<DicomImageElement>> getSegAttributes() {
    return segAttributes;
  }

  /**
   * Returns {@code true} when at least one segment in this SEG carries a fractional encoding
   * (PROBABILITY or OCCUPANCY). The canonical 3D segmentation volume collapses fractional frames to
   * binary stamps, so the per-frame "graphics" path must remain primary for those SEGs to keep the
   * alpha-mask fidelity expected by {@code FractionalOverlay}.
   */
  @Override
  public boolean isFractionalSeg() {
    for (SegRegion<DicomImageElement> r : segAttributes.values()) {
      if (r.isFractional()) {
        return true;
      }
    }
    return false;
  }

  @Override
  public boolean isVisible() {
    return visible;
  }

  @Override
  public void setVisible(boolean visible) {
    this.visible = visible;
  }

  @Override
  public float getOpacity() {
    return opacity;
  }

  @Override
  public void setOpacity(float opacity) {
    this.opacity = Math.clamp(opacity, 0.0f, 1.0f);
    updateOpacityInSegAttributes(this.opacity);
  }

  @Override
  public void initReferences(String originSeriesUID) {
    refMap.clear();
    Attributes dicom = dicomObject();
    if (dicom != null) {
      Function<String, Map<String, Set<LazyContourLoader>>> addSeries =
          seriesUID -> refMap.computeIfAbsent(seriesUID, _ -> new HashMap<>());
      HiddenSeriesManager.getInstance().extractReferencedSeries(dicom, originSeriesUID, addSeries);
    }
  }

  @Override
  public ResourceIconPath getIconPath() {
    return OtherIcon.SEGMENTATION;
  }

  /** Builds the per-frame contours for this segmentation. */
  public void initContours(DicomSeries series, List<DicomSeries> refSeriesList) {
    if (series == null) {
      loadState = LoadState.READY;
      return;
    }
    loadState = LoadState.LOADING;
    roiMap.clear();

    Attributes dicom = dicomObject();
    if (dicom == null) {
      loadState = LoadState.READY;
      return;
    }
    boolean failed = false;
    try {
      SegmentationKind kind = SegmentationKind.fromDicom(dicom.getString(Tag.SegmentationType));
      int maxFractionalValue = dicom.getInt(Tag.MaximumFractionalValue, 255);
      String fractionalType =
          kind == SegmentationKind.FRACTIONAL
              ? dicom.getString(Tag.SegmentationFractionalType)
              : null;

      Map<SegRegion<DicomImageElement>, FrameRange> regionPosition =
          parseSegmentSequence(dicom.getSequence(Tag.SegmentSequence), kind, fractionalType);

      Sequence perFrameSeq = dicom.getSequence(Tag.PerFrameFunctionalGroupsSequence);
      if (perFrameSeq != null) {
        processPerFrameSequence(
            series, refSeriesList, regionPosition, perFrameSeq, kind, maxFractionalValue);

        if (kind == SegmentationKind.LABELMAP
            && !regionPosition.isEmpty()
            && !perFrameSeq.isEmpty()) {
          int last = perFrameSeq.size() - 1;
          regionPosition.replaceAll((_, _) -> new FrameRange(0, last));
        }
      }

      refineSegSliceSpacingFromPositionMap();
      regionPosition.forEach(
          (region, p) -> region.setMeasurableLayer(getMeasurableLayer(series, p)));
    } catch (RuntimeException e) {
      failed = true;
      LOGGER.error("Error initializing SEG contours", e);
      throw e;
    } finally {
      loadState = failed ? LoadState.FAILED : LoadState.READY;
    }
  }

  private Map<SegRegion<DicomImageElement>, FrameRange> parseSegmentSequence(
      Sequence segSeq, SegmentationKind kind, String fractionalType) {
    Map<SegRegion<DicomImageElement>, FrameRange> regionPosition = new HashMap<>();
    if (segSeq == null) {
      return regionPosition;
    }
    for (Attributes seg : segSeq) {
      int nb = seg.getInt(Tag.SegmentNumber, -1);
      String segmentLabel = seg.getString(Tag.SegmentLabel, "" + nb);

      int[] colorRgb =
          CIELab.dicomLab2rgb(
              DicomUtils.getIntArrayFromDicomElement(seg, Tag.RecommendedDisplayCIELabValue, null));
      Color rgbColor = RegionAttributes.getColor(colorRgb, nb, opacity);

      SegRegion<DicomImageElement> attributes = new SegRegion<>(nb, segmentLabel, rgbColor);
      attributes.setInteriorOpacity(0.2f);
      attributes.setDescription(seg.getString(Tag.SegmentDescription));
      attributes.setType(seg.getString(Tag.SegmentAlgorithmType));
      attributes.setAlgorithmName(seg.getString(Tag.SegmentAlgorithmName));
      if (kind == SegmentationKind.FRACTIONAL) {
        attributes.setFractionalType(fractionalType);
      }

      Sequence regionSeq = seg.getSequence(Tag.AnatomicRegionSequence);
      if (regionSeq != null) {
        attributes.setAnatomicRegionCodes(
            Code.toCodeMacros(regionSeq).stream().map(Code::getCodeMeaning).toList());
      }
      Sequence categorySeq = seg.getSequence(Tag.SegmentedPropertyCategoryCodeSequence);
      if (categorySeq != null) {
        attributes.setCategories(
            Code.toCodeMacros(categorySeq).stream().map(Code::getCodeMeaning).toList());
      }

      segAttributes.put(nb, attributes);
      regionPosition.put(attributes, FrameRange.EMPTY);
    }
    return regionPosition;
  }

  private void processPerFrameSequence(
      DicomSeries series,
      List<DicomSeries> refSeriesList,
      Map<SegRegion<DicomImageElement>, FrameRange> regionPosition,
      Sequence perFrameSeq,
      SegmentationKind kind,
      int maxFractionalValue) {

    Attributes dicom = dicomObject();
    Attributes sharedFG = dicom.getNestedDataset(Tag.SharedFunctionalGroupsSequence);
    String segFrameOfRefUID = dicom.getString(Tag.FrameOfReferenceUID);
    String seriesFrameOfRefUID = TagD.getTagValue(series, Tag.FrameOfReferenceUID, String.class);
    boolean sameFrameOfRef =
        segFrameOfRefUID != null && segFrameOfRefUID.equals(seriesFrameOfRefUID);

    int index = 0;
    for (Attributes frame : perFrameSeq) {
      index++;
      DicomImageElement binaryMask = series.getMedia(index - 1, null, null);
      if (binaryMask != null) {
        registerFrameLoader(frame, binaryMask, index, kind, maxFractionalValue, regionPosition);
      }

      LazyContourLoader loader = roiMap.get(index);
      if (loader == null) {
        continue;
      }
      // Strategy: prioritize spatial metadata matching over SOP UID-based matching.
      // Correctly handles AI-generated SEGs that resample images (empty Derivation Image
      // Sequence) and SEGs that are not pixel-aligned with the source images.
      boolean addedByPosition = sameFrameOfRef && addPositionMap(frame, sharedFG, loader);
      if (!addedByPosition) {
        associateContours(refSeriesList, frame, sharedFG, loader, index, sameFrameOfRef);
      }
    }
  }

  private void registerFrameLoader(
      Attributes frame,
      DicomImageElement binaryMask,
      int index,
      SegmentationKind kind,
      int maxFractionalValue,
      Map<SegRegion<DicomImageElement>, FrameRange> regionPosition) {
    if (kind == SegmentationKind.LABELMAP) {
      // LABELMAP: a single frame contains all segments at this slice (pixel value = SegmentNumber).
      roiMap.put(index, new LabelMapContourLoader(binaryMask, index, segAttributes));
      return;
    }
    Attributes refSeqNb = frame.getNestedDataset(Tag.SegmentIdentificationSequence);
    if (refSeqNb == null) {
      return;
    }
    Integer segmentNumber =
        DicomUtils.getIntegerFromDicomElement(refSeqNb, Tag.ReferencedSegmentNumber, null);
    if (segmentNumber == null) {
      return;
    }
    SegRegion<DicomImageElement> region = segAttributes.get(segmentNumber);
    if (region == null) {
      return;
    }
    LazyContourLoader loader =
        kind == SegmentationKind.FRACTIONAL
            ? new FractionalContourLoader(binaryMask, index, region, maxFractionalValue)
            : new BasicContourLoader(binaryMask, index, region);
    roiMap.put(index, loader);
    FrameRange p = regionPosition.get(region);
    if (p != null) {
      regionPosition.put(region, p.withFrame(index - 1));
    }
  }

  /** Returns TRUE/FALSE per SpatialLocationsPreserved, or {@code null} when absent. */
  private static Boolean isSpatialLocationsPreserved(Sequence derivationSeq) {
    if (derivationSeq == null) {
      return null;
    }
    for (Attributes derivation : derivationSeq) {
      Sequence srcSeq = derivation.getSequence(Tag.SourceImageSequence);
      if (srcSeq == null) {
        continue;
      }
      for (Attributes src : srcSeq) {
        String slp = src.getString(Tag.SpatialLocationsPreserved);
        if ("YES".equals(slp)) return Boolean.TRUE;
        if ("NO".equals(slp)) return Boolean.FALSE;
      }
    }
    return null;
  }

  private void associateContours(
      List<DicomSeries> refSeriesList,
      Attributes frame,
      Attributes sharedFG,
      LazyContourLoader loader,
      int index,
      boolean sameFrameOfRef) {

    Map<String, List<Integer>> sopUIDToFramesMap = new HashMap<>();
    Sequence derivationSeq = frame.getSequence(Tag.DerivationImageSequence);
    if (derivationSeq != null) {
      Boolean spatialPreserved = isSpatialLocationsPreserved(derivationSeq);
      if (Boolean.FALSE.equals(spatialPreserved)) {
        LOGGER.debug(
            "Segmentation frame {} declares SpatialLocationsPreserved=NO; "
                + "skipping SOP UID-based pixel overlay, using spatial fallback",
            index);
        if (sameFrameOfRef) {
          addPositionMap(frame, sharedFG, loader);
        } else {
          addPositionMapFromRefSeries(frame, sharedFG, refSeriesList, loader);
        }
        return;
      }
      for (Attributes derivation : derivationSeq) {
        HiddenSeriesManager.addSourceImage(derivation, sopUIDToFramesMap);
      }
    }

    if (sopUIDToFramesMap.isEmpty()) {
      if (!refSeriesList.isEmpty()
          && addPositionMapFromRefSeries(frame, sharedFG, refSeriesList, loader)) {
        return;
      }
      // No SOPInstanceUIDs and no spatial match: keep the loader indexed by frame.
      roiMap.put(index, loader);
      return;
    }
    mergeFramesIntoRefMap(sopUIDToFramesMap, loader);
  }

  private void mergeFramesIntoRefMap(
      Map<String, List<Integer>> sopUIDToFramesMap, LazyContourLoader loader) {
    for (Map<String, Set<LazyContourLoader>> map : refMap.values()) {
      sopUIDToFramesMap.forEach(
          (sopUID, frames) -> {
            Set<LazyContourLoader> list = map.get(sopUID);
            if (list == null) {
              return;
            }
            if (frames.isEmpty()) {
              list.add(loader);
            } else {
              for (Integer frameNumber : frames) {
                map.computeIfAbsent(sopUID + "_" + frameNumber, _ -> new LinkedHashSet<>())
                    .add(loader);
              }
            }
          });
    }
  }

  /** Spatial-position fallback: tries SEG own metadata first, then matches by IPP against refs. */
  private boolean addPositionMapFromRefSeries(
      Attributes frame,
      Attributes sharedFG,
      List<DicomSeries> refSeriesList,
      LazyContourLoader loader) {
    if (addPositionMap(frame, sharedFG, loader)) {
      return true;
    }
    double[] segIPP = findSegIpp(frame, sharedFG);
    if (segIPP == null) {
      return false;
    }
    for (DicomSeries refSeries : refSeriesList) {
      for (DicomImageElement dcm : refSeries.getMedias(null, null)) {
        double[] imagePosition = TagD.getTagValue(dcm, Tag.ImagePositionPatient, double[].class);
        if (isWithinTolerance(imagePosition, segIPP, 0.01)) {
          return registerRefSlicePosition(dcm, loader);
        }
      }
    }
    return false;
  }

  /** Resolves the SEG frame's IPP by walking per-frame → shared → top-level. */
  private double[] findSegIpp(Attributes frame, Attributes sharedFG) {
    Attributes planePosition = frame.getNestedDataset(Tag.PlanePositionSequence);
    double[] ipp =
        planePosition == null ? null : planePosition.getDoubles(Tag.ImagePositionPatient);
    if (ipp == null && sharedFG != null) {
      Attributes sharedPos = sharedFG.getNestedDataset(Tag.PlanePositionSequence);
      if (sharedPos != null) {
        ipp = sharedPos.getDoubles(Tag.ImagePositionPatient);
      }
    }
    if (ipp == null) {
      ipp = TagD.getTagValue(mediaIO, Tag.ImagePositionPatient, double[].class);
    }
    return ipp;
  }

  /**
   * Indexes loader under refImage's SlicePosition (or SliceLocation fallback). Always returns true.
   */
  private boolean registerRefSlicePosition(DicomImageElement dcm, LazyContourLoader loader) {
    Double refSlicePos = (Double) dcm.getTagValue(TagW.SlicePosition);
    Double key =
        refSlicePos != null ? refSlicePos : TagD.getTagValue(dcm, Tag.SliceLocation, Double.class);
    if (key != null) {
      positionMap.computeIfAbsent(quantisePositionKey(key), _ -> new LinkedHashSet<>()).add(loader);
    }
    return true;
  }

  private static boolean isWithinTolerance(double[] a, double[] b, double tolerance) {
    if (a == null || b == null || a.length != b.length) {
      return false;
    }
    for (int i = 0; i < a.length; i++) {
      if (Math.abs(a[i] - b[i]) > tolerance) {
        return false;
      }
    }
    return true;
  }

  /**
   * Computes the slice position from the SEG frame's spatial metadata (Plane Position/Orientation
   * sequences) and indexes {@code loader} in {@link #positionMap}. Falls back to shared FG and then
   * top-level attributes; returns {@code false} when no scalar position can be derived.
   */
  private boolean addPositionMap(Attributes frame, Attributes sharedFG, LazyContourLoader loader) {
    double[] pos =
        getDoubleArrayWithFallback(
            frame, sharedFG, Tag.PlanePositionSequence, Tag.ImagePositionPatient);
    if (pos == null || pos.length != 3) {
      return false;
    }
    double[] orientation =
        getDoubleArrayWithFallback(
            frame, sharedFG, Tag.PlaneOrientationSequence, Tag.ImageOrientationPatient);

    Double slicePos = computeSlicePosition(pos, orientation);
    if (slicePos == null) {
      return false;
    }
    positionMap
        .computeIfAbsent(quantisePositionKey(slicePos), _ -> new LinkedHashSet<>())
        .add(loader);
    captureSegSliceSpacing(sharedFG);
    return true;
  }

  /**
   * Returns the scalar slice position (normal·IPP) or a SliceLocation fallback; null when unknown.
   */
  private Double computeSlicePosition(double[] pos, double[] orientation) {
    if (orientation != null && orientation.length == 6) {
      Vector3d pPos = new Vector3d(pos);
      Vector3d vr = new Vector3d(orientation);
      Vector3d vc = new Vector3d(orientation[3], orientation[4], orientation[5]);
      Vector3d normal = VectorUtils.computeNormalOfSurface(vr, vc);
      // Normalize sign convention to match DicomMediaUtils.computeSlicePosition().
      VectorUtils.orientNormalToDominantPositiveAxis(normal);

      // Capture the SEG's reference normal on the first frame; reject frames whose orientation
      // diverges from it so positionMap keys remain mutually comparable.
      Vector3d ref = referenceNormal;
      if (ref == null) {
        referenceNormal = new Vector3d(normal);
      } else if (Math.abs(ref.dot(normal)) < SpecialElementRegion.ORIENTATION_COSINE_STRICT) {
        LOGGER.debug("Skipping SEG frame whose orientation diverges from the reference normal");
        return null;
      }
      return normal.dot(pPos);
    }
    // No IOP (e.g. NM): fall back to top-level SliceLocation.
    Attributes dicom = dicomObject();
    if (dicom == null) {
      return null;
    }
    return DicomUtils.getDoubleFromDicomElement(dicom, Tag.SliceLocation, null);
  }

  /** Captures SEG slice spacing from PixelMeasuresSequence (shared then top-level) on first hit. */
  private void captureSegSliceSpacing(Attributes sharedFG) {
    if (segSliceSpacing > 0) {
      return;
    }
    double sp = readPixelMeasuresSpacing(sharedFG);
    if (sp <= 0) {
      Attributes dicom = dicomObject();
      if (dicom != null) {
        sp = readPixelMeasuresSpacing(dicom);
        if (sp <= 0) {
          Double v = DicomUtils.getDoubleFromDicomElement(dicom, Tag.SpacingBetweenSlices, null);
          if (v == null || v <= 0) {
            v = DicomUtils.getDoubleFromDicomElement(dicom, Tag.SliceThickness, null);
          }
          if (v != null && v > 0) {
            sp = v;
          }
        }
      }
    }
    if (sp > 0) {
      segSliceSpacing = sp;
    }
  }

  private static double readPixelMeasuresSpacing(Attributes container) {
    if (container == null) {
      return 0;
    }
    Attributes pm = container.getNestedDataset(Tag.PixelMeasuresSequence);
    if (pm == null) {
      return 0;
    }
    Double v = DicomUtils.getDoubleFromDicomElement(pm, Tag.SpacingBetweenSlices, null);
    if (v == null || v <= 0) {
      v = DicomUtils.getDoubleFromDicomElement(pm, Tag.SliceThickness, null);
    }
    return v == null ? 0 : v;
  }

  /** Quantises {@code slicePos} to a quarter of segSliceSpacing (or 1e-4 when unknown). */
  private double quantisePositionKey(double slicePos) {
    double sp = segSliceSpacing;
    double q = sp > 0 ? sp * 0.25 : 1e-4;
    return Math.round(slicePos / q) * q;
  }

  /** Refines {@link #segSliceSpacing} from the median delta of consecutive positionMap keys. */
  private void refineSegSliceSpacingFromPositionMap() {
    int n = positionMap.size();
    if (n < 2) {
      return;
    }
    double[] deltas = new double[n - 1];
    int i = 0;
    double prev = Double.NaN;
    for (Double key : positionMap.keySet()) {
      if (i > 0) {
        deltas[i - 1] = Math.abs(key - prev);
      }
      prev = key;
      i++;
    }
    Arrays.sort(deltas);
    double median = deltas[deltas.length / 2];
    if (median > 0
        && (segSliceSpacing <= 0 || Math.abs(median - segSliceSpacing) / median > 0.05)) {
      segSliceSpacing = median;
    }
  }

  /** Reads a double[] from per-frame, then shared FG, then top-level — first non-null wins. */
  private double[] getDoubleArrayWithFallback(
      Attributes frame, Attributes sharedFG, int seqTag, int valueTag) {
    double[] val = nestedDoubleArray(frame, seqTag, valueTag);
    if (val != null) {
      return val;
    }
    val = nestedDoubleArray(sharedFG, seqTag, valueTag);
    if (val != null) {
      return val;
    }
    return TagD.getTagValue(mediaIO, valueTag, double[].class);
  }

  private static double[] nestedDoubleArray(Attributes attrs, int seqTag, int valueTag) {
    if (attrs == null) {
      return null;
    }
    Attributes nested = attrs.getNestedDataset(seqTag);
    return nested == null
        ? null
        : DicomUtils.getDoubleArrayFromDicomElement(nested, valueTag, null);
  }

  private SegMeasurableLayer<DicomImageElement> getMeasurableLayer(
      DicomSeries series, FrameRange p) {
    if (series == null || series.size(null) <= 0 || p == null || p.first < 0 || p.last < 0) {
      return null;
    }
    DicomImageElement first = series.getMedia(p.first, null, null);
    DicomImageElement last = series.getMedia(p.last, null, null);
    DicomImageElement img = series.getMedia(p.middle(), null, null);
    if (img == null || first == null || last == null) {
      return null;
    }
    double thickness = DicomMediaUtils.getThickness(first, last);
    if (thickness <= 0.0) {
      thickness = 1.0;
    } else {
      thickness /= (p.last - p.first) + 1;
    }
    return new SegMeasurableLayer<>(img, thickness);
  }
}
