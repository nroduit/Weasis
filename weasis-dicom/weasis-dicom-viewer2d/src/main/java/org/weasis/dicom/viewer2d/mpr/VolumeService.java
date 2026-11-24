/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.viewer2d.mpr;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import javax.swing.BoundedRangeModel;
import javax.swing.JProgressBar;
import org.dcm4che3.data.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.explorer.DataExplorerView;
import org.weasis.core.api.explorer.ObservableEvent;
import org.weasis.core.api.explorer.model.DataExplorerModel;
import org.weasis.core.api.gui.util.GuiExecutor;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.media.data.MediaSeries.MEDIA_POSITION;
import org.weasis.core.api.media.data.MediaSeriesGroup;
import org.weasis.core.api.util.ResourceMonitor;
import org.weasis.core.api.util.SystemMemory;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.codec.TagD;
import org.weasis.dicom.explorer.DicomModel;
import org.weasis.dicom.viewer2d.mpr.MprView.Plane;

/**
 * The one volume of a series for every consumer: the MPR and the volume rendering of a viewer, the
 * reslicing of a fusion, a subtraction mask or a reformat, and the series a hanging protocol
 * derives. A volume is built once in the background and handed out under leases; it stays after the
 * last lease closes, up to a budget, so the next consumer finds it ready, and it leaves with its
 * series. A hanging protocol or a viewer may ask for a build ahead of time.
 */
public final class VolumeService implements PropertyChangeListener {

  private static final Logger LOGGER = LoggerFactory.getLogger(VolumeService.class);

  /** The volume of a series, rectified onto its acquisition plane or copied as acquired. */
  public record Key(MediaSeries<DicomImageElement> series, boolean rectified) {

    /** Series Instance UID of the series, for reports. */
    public String uid() {
      return TagD.getTagValue(series, Tag.SeriesInstanceUID, String.class);
    }
  }

  /** A hold on a volume, see {@link VolumeCache.Lease}. */
  public static final class Lease implements AutoCloseable {
    private final VolumeCache<Volume<?, ?>>.Lease lease;

    private Lease(VolumeCache<Volume<?, ?>>.Lease lease) {
      this.lease = lease;
    }

    /** The volume, waited for; a failed build throws. */
    public Volume<?, ?> volume() {
      return lease.join();
    }

    public Key key() {
      return (Key) lease.key();
    }

    /** The progress of the build of the volume, empty when the volume is built or has failed. */
    public Optional<BoundedRangeModel> progress() {
      return lease.hint() instanceof JProgressBar bar && !lease.future().isDone()
          ? Optional.of(bar.getModel())
          : Optional.empty();
    }

    @Override
    public void close() {
      lease.close();
    }
  }

  private static final VolumeService INSTANCE = new VolumeService();

  /** Share of the heap the unreferenced volumes may keep: they live on the heap or in a file. */
  private static final double IDLE_HEAP_SHARE = 0.4;

  private final VolumeCache<Volume<?, ?>> cache;
  private final Set<DataExplorerModel> observed = ConcurrentHashMap.newKeySet();

  private VolumeService() {
    cache =
        new VolumeCache<>(
            VolumeService::build,
            Volume::removeData,
            VolumeService::bytes,
            (long) (SystemMemory.maxHeapMemory() * IDLE_HEAP_SHARE));
  }

  public static VolumeService getInstance() {
    return INSTANCE;
  }

  /**
   * A lease on the volume of the series, built in the background when the service has none; the
   * progress bar, when given, follows the build this call starts. A reformat has no volume of its
   * own: the lease is on the volume of the acquired series it was resliced from.
   */
  public Lease acquire(MediaSeries<DicomImageElement> series, boolean rectified, JProgressBar bar) {
    MediaSeries<DicomImageElement> source = volumeSeriesOf(series);
    observe(source);
    Key key = new Key(source, rectified);
    VolumeCache<Volume<?, ?>>.Lease lease =
        cache.acquire(key, bar != null || cache.peek(key).isPresent() ? bar : progressOf(source));
    if (bar != null) {
      // A volume the service already holds, or builds for another requester, never moves this
      // bar: fill it when the volume is there, which is what hides it
      lease
          .future()
          .whenComplete(
              (volume, failure) -> GuiExecutor.execute(() -> bar.setValue(bar.getMaximum())));
    }
    return new Lease(lease);
  }

  /**
   * The series whose voxels make the volume of a series: the acquired series behind a reformat,
   * which only reslices them; any other series itself.
   */
  public static MediaSeries<DicomImageElement> volumeSeriesOf(
      MediaSeries<DicomImageElement> series) {
    if (series == null) {
      return null;
    }
    MediaSeries<DicomImageElement> source = ReformatService.sourceOf(series);
    if (source != series) {
      LOGGER.debug("The volume of a reformat is the one of its acquired series");
    }
    return source;
  }

  /**
   * Builds the rectified volume of the series ahead of a request, after the requests, when its
   * slices rectify without a question; nothing when the series has one already.
   */
  public void prefetch(MediaSeries<DicomImageElement> series) {
    series = volumeSeriesOf(series);
    if (series == null || series.size(null) < 2) {
      return;
    }
    DicomImageElement middle = series.getMedia(MEDIA_POSITION.MIDDLE, null, null);
    if (middle == null || middle.getSliceGeometry() == null) {
      return;
    }
    try {
      ReformatStack stack = new ReformatStack(Plane.getAcquisitionPlane(middle), series);
      if (stack.isNonParallelSlices()
          || stack.isVariableSliceSpacing()
          || stack.isTooFewSlicesForTransformation()) {
        return; // the question is the viewer's to ask
      }
    } catch (RuntimeException e) {
      return;
    }
    observe(series);
    cache.prefetch(new Key(series, true), progressOf(series));
  }

  /** The progress of a build nobody shows yet, for the consumers that come while it runs. */
  private static JProgressBar progressOf(MediaSeries<DicomImageElement> series) {
    return new JProgressBar(0, Math.max(1, series.size(null)));
  }

  /**
   * A lease on a volume the service holds, for a consumer handed the volume itself; empty when the
   * service does not hold that instance (built outside it), the consumer then owning nothing.
   */
  public Optional<Lease> leaseOf(Volume<?, ?> volume) {
    if (volume == null || volume.getStack() == null) {
      return Optional.empty();
    }
    Key key = new Key(volume.getStack().getSeries(), !volume.isBasic());
    if (cache.peek(key).filter(held -> held == volume).isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(new Lease(cache.acquire(key, null)));
  }

  /**
   * The built volume of a stack, when the service holds one: the stack's own, or the one of the
   * acquired series when the stack is a reformat's.
   */
  public Optional<Volume<?, ?>> peek(OriginalStack stack) {
    if (stack == null) {
      return Optional.empty();
    }
    MediaSeries<DicomImageElement> source = volumeSeriesOf(stack.getSeries());
    boolean reformat = source != stack.getSeries();
    for (boolean rectified : new boolean[] {true, false}) {
      Optional<Volume<?, ?>> volume = cache.peek(new Key(source, rectified));
      if (volume.isPresent() && (reformat || stack.equals(volume.get().getStack()))) {
        return volume;
      }
    }
    return Optional.empty();
  }

  /** Forgets the volumes of a series, now or when their last lease closes. */
  public void evict(MediaSeries<?> series) {
    cache.evict(new Key(cast(series), true));
    cache.evict(new Key(cast(series), false));
  }

  public List<VolumeCache.Info> snapshot() {
    return cache.snapshot();
  }

  public long budget() {
    return cache.budget();
  }

  private static Volume<?, ?> build(Object key, Object hint) {
    Key k = (Key) key;
    DicomImageElement middle = k.series().getMedia(MEDIA_POSITION.MIDDLE, null, null);
    if (middle == null) {
      throw new IllegalStateException("The series has no image");
    }
    ReformatStack stack = new ReformatStack(Plane.getAcquisitionPlane(middle), k.series());
    if (stack.getWidth() == 0 || stack.getHeight() == 0) {
      throw new IllegalStateException("The series has no image");
    }
    Volume<?, ?> volume =
        Volume.createVolume(stack, hint instanceof JProgressBar bar ? bar : null, !k.rectified());
    if (volume == null) {
      throw new IllegalStateException("The volume cannot be built");
    }
    ResourceMonitor.getInstance().recordVolume(stack.getSourceStack().size());
    LOGGER.info(
        "Volume of {} ready: {}x{}x{}, {} MB",
        k.uid(),
        volume.getSizeX(),
        volume.getSizeY(),
        volume.getSizeZ(),
        bytes(volume) / (1024 * 1024));
    return volume;
  }

  private static long bytes(Volume<?, ?> volume) {
    return (long) volume.getSizeX()
        * volume.getSizeY()
        * volume.getSizeZ()
        * org.opencv.core.CvType.ELEM_SIZE(volume.getCvType());
  }

  @SuppressWarnings("unchecked")
  private static MediaSeries<DicomImageElement> cast(MediaSeries<?> series) {
    return (MediaSeries<DicomImageElement>) series;
  }

  /** Follows the explorer holding the series, to let its volumes go when the series does. */
  private void observe(MediaSeries<?> series) {
    if (series == null
        || !(series.getTagValue(org.weasis.core.api.media.data.TagW.ExplorerModel)
            instanceof DataExplorerModel model)) {
      return;
    }
    if (observed.add(model)) {
      model.addPropertyChangeListener(this);
    }
  }

  @Override
  public void propertyChange(PropertyChangeEvent evt) {
    if (!(evt instanceof ObservableEvent event)
        || !ObservableEvent.BasicAction.REMOVE.equals(event.getActionCommand())
        || !(event.getNewValue() instanceof MediaSeriesGroup removed)
        || !(event.getSource() instanceof DicomModel model)) {
      return;
    }
    List<Key> gone = new ArrayList<>();
    for (VolumeCache.Info info : cache.snapshot()) {
      if (info.key() instanceof Key key && belongs(model, key.series(), removed)) {
        gone.add(key);
      }
    }
    gone.forEach(k -> cache.evict(k));
  }

  /** Whether the series is the removed node or lies under it (its study or patient). */
  private static boolean belongs(
      DicomModel model, MediaSeries<?> series, MediaSeriesGroup removed) {
    if (series == removed) {
      return true;
    }
    MediaSeriesGroup study = model.getParent(series, DicomModel.study);
    MediaSeriesGroup patient = model.getParent(series, DicomModel.patient);
    return removed.equals(study) || removed.equals(patient);
  }

  /** Observes the explorers already open, for the volumes built before any series was seen. */
  public void observeExplorers() {
    for (DataExplorerView view : GuiUtils.getUICore().getExplorerPlugins()) {
      DataExplorerModel model = view.getDataExplorerModel();
      if (model != null && observed.add(model)) {
        model.addPropertyChangeListener(this);
      }
    }
  }
}
