/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.qr;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.JProgressBar;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.io.DicomInputStream;
import org.dcm4che3.io.DicomInputStream.IncludeBulkData;
import org.dcm4che3.io.DicomOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.gui.util.GuiExecutor;
import org.weasis.core.api.media.data.MediaSeriesGroup;
import org.weasis.core.util.FileUtil;
import org.weasis.core.util.StringUtil;
import org.weasis.dicom.codec.DicomSeries;
import org.weasis.dicom.codec.TagD;
import org.weasis.dicom.codec.utils.SeriesInstanceList;
import org.weasis.dicom.explorer.DicomModel;
import org.weasis.dicom.explorer.LoadLocalDicom;
import org.weasis.dicom.explorer.pref.node.AbstractDicomNode.RetrieveType;
import org.weasis.dicom.explorer.wado.LoadSeries;
import org.weasis.dicom.explorer.wado.SeriesDownloadManager;
import org.weasis.dicom.mf.SopInstance;
import org.weasis.dicom.op.CGet;
import org.weasis.dicom.op.CMove;
import org.weasis.dicom.param.DicomParam;
import org.weasis.dicom.param.DicomProgress;
import org.weasis.dicom.param.DicomState;

/**
 * Retrieves one series with C-GET or C-MOVE, as a task of the download manager so that it offers
 * the same per-series progress, stop and resume as a DICOMweb download.
 *
 * <p>The first pass retrieves the whole series. A resumed series only requests the SOP instances
 * that are still missing, falling back to a series level retrieve when the archive does not answer
 * an instance level request.
 */
public class LoadQrSeries extends LoadSeries {
  private static final Logger LOGGER = LoggerFactory.getLogger(LoadQrSeries.class);

  /** Number of SOP Instance UIDs sent in a single retrieve identifier. */
  private static final int UID_BATCH_SIZE = 500;

  /** Bound to the wait for the queued imports so a stalled import cannot hang the task. */
  private static final long IMPORT_TIMEOUT_SEC = 120;

  /** Bound to the wait for the stopped retrieve this task resumes. */
  private static final long PREVIOUS_TIMEOUT_SEC = 30;

  private static final String LEVEL_SERIES = "SERIES"; // NON-NLS
  private static final String LEVEL_IMAGE = "IMAGE"; // NON-NLS

  /**
   * Tags {@link org.weasis.dicom.codec.utils.PatientComparator} builds the patient pseudo UID from,
   * so a retrieved object is filed under the patient the query returned.
   */
  private static final int[] IDENTITY_OVERRIDES = {
    Tag.PatientID, Tag.IssuerOfPatientID, Tag.PatientName, Tag.PatientBirthDate, Tag.PatientSex
  };

  private final RetrieveContext context;
  private final AtomicBoolean cancelRequested = new AtomicBoolean();
  private final AtomicBoolean retrieving = new AtomicBoolean();
  private final DicomProgress progress = new DicomProgress();

  /** Task this one resumes, null for a first retrieve. */
  private final LoadQrSeries previous;

  /** Instances accounted for by the requests already completed, the retrieve being batched. */
  private final AtomicInteger completedBefore = new AtomicInteger();

  private final AtomicInteger received = new AtomicInteger();

  public LoadQrSeries(DicomSeries dicomSeries, RetrieveContext context, boolean startDownloading) {
    super(dicomSeries, context.getDicomModel(), null, 1, true, startDownloading);
    this.context = context;
    this.previous = null;
    setPOpeningStrategy(context.getOpeningStrategy());
    context.register(this);
  }

  /** Continues a stopped retrieve, keeping the progress bar already shown on the thumbnail. */
  private LoadQrSeries(LoadQrSeries previous) {
    super(
        previous.getDicomSeries(),
        previous.context.getDicomModel(),
        null,
        previous.getProgressBar(),
        1,
        true,
        previous.isStartDownloading());
    this.context = previous.context;
    this.previous = previous;
    setPOpeningStrategy(previous.getOpeningStrategy());
    context.register(this);
  }

  @Override
  protected Boolean doInBackground() {
    awaitPrevious();
    if (getDicomSeries().size(null) > 0) {
      // Resuming: only a known instance list tells what is left, whereas a first pass asks for the
      // whole series and would pay an image level C-FIND for nothing
      context.fillInstances(getDicomSeries());
    }
    List<SopInstance> missing = getMissingInstances();
    if (missing != null && missing.isEmpty()) {
      return true; // Nothing left to retrieve, the series is complete
    }

    initProgressBar(missing);
    progress.addProgressListener(this::handleProgress);
    if (RetrieveType.CGET == context.getType()) {
      progress.addProgressListener(this::importStoredFile);
    }

    retrieving.set(true);
    boolean done;
    try {
      if (missing == null) {
        done = retrieveSeries();
      } else {
        done = retrieveInstances(missing);
        if (!done && !cancelRequested.get()) {
          // The archive refused the instance level request: fall back on the whole series
          LOGGER.info("Instance level retrieve failed, falling back to a series level retrieve");
          done = retrieveSeries();
        }
      }
    } finally {
      retrieving.set(false);
    }
    setHasError(!done);
    awaitPendingImports();
    return done;
  }

  /**
   * Lists the instances left to retrieve, or null when the whole series must be requested because
   * nothing is known about its content yet or because none of its instances is loaded.
   */
  private List<SopInstance> getMissingInstances() {
    SeriesInstanceList instanceList = getSeriesInstanceList();
    if (instanceList.isEmpty()) {
      return null;
    }
    DicomSeries series = getDicomSeries();
    MediaSeriesGroup study = getDicomModel().getParent(series, DicomModel.study);
    List<SopInstance> missing =
        instanceList.getSortedList().stream()
            .filter(
                sop ->
                    !LoadSeries.isSOPInstanceUIDExist(
                        getDicomModel(), study, series, sop.getSopInstanceUID()))
            .toList();
    return missing.size() == instanceList.size() ? null : missing;
  }

  /** Instances of the series: the enumerated ones, or the count the series query returned. */
  private int totalInstances() {
    int enumerated = getSeriesInstanceList().size();
    if (enumerated > 0) {
      return enumerated;
    }
    Integer queried =
        TagD.getTagValue(getDicomSeries(), Tag.NumberOfSeriesRelatedInstances, Integer.class);
    return queried == null ? 0 : queried;
  }

  private void initProgressBar(List<SopInstance> missing) {
    int total = totalInstances();
    int alreadyLoaded = missing == null ? 0 : total - missing.size();
    received.set(alreadyLoaded);
    completedBefore.set(alreadyLoaded);
    GuiExecutor.execute(
        () -> {
          if (total > 0) {
            getProgressBar().setIndeterminate(false);
            getProgressBar().setMaximum(total);
            getProgressBar().setValue(alreadyLoaded);
          } else {
            getProgressBar().setIndeterminate(true);
          }
        });
  }

  private boolean retrieveSeries() {
    return execute(buildKeys(LEVEL_SERIES, null));
  }

  private boolean retrieveInstances(List<SopInstance> missing) {
    for (List<String> batch : batchUids(instanceUids(missing))) {
      if (isCancelled() || cancelRequested.get()) {
        return true;
      }
      if (!execute(buildKeys(LEVEL_IMAGE, batch))) {
        return false;
      }
    }
    return true;
  }

  private DicomParam[] buildKeys(String level, List<String> sopInstanceUids) {
    MediaSeriesGroup study = getDicomModel().getParent(getDicomSeries(), DicomModel.study);
    return buildKeys(
        TagD.getTagValue(study, Tag.StudyInstanceUID, String.class),
        TagD.getTagValue(getDicomSeries(), Tag.SeriesInstanceUID, String.class),
        level,
        sopInstanceUids);
  }

  /**
   * Builds the retrieve identifier. The level overrides the one the information model sets, so a
   * series or a list of instances is requested instead of the whole study.
   */
  static DicomParam[] buildKeys(
      String studyUid, String seriesUid, String level, List<String> sopInstanceUids) {
    List<DicomParam> keys = new ArrayList<>(4);
    keys.add(new DicomParam(Tag.QueryRetrieveLevel, level));
    keys.add(new DicomParam(Tag.StudyInstanceUID, studyUid));
    keys.add(new DicomParam(Tag.SeriesInstanceUID, seriesUid));
    if (sopInstanceUids != null) {
      keys.add(new DicomParam(Tag.SOPInstanceUID, sopInstanceUids.toArray(new String[0])));
    }
    return keys.toArray(new DicomParam[0]);
  }

  /**
   * UIDs to match on. A multiframe series lists one entry per frame, but the retrieve identifies
   * whole instances, so the same UID must not be repeated.
   */
  static List<String> instanceUids(List<SopInstance> instances) {
    return instances.stream().map(SopInstance::getSopInstanceUID).distinct().toList();
  }

  /** Splits the instances to retrieve so a single identifier never carries too many UIDs. */
  static List<List<String>> batchUids(List<String> uids) {
    List<List<String>> batches = new ArrayList<>();
    for (int start = 0; start < uids.size(); start += UID_BATCH_SIZE) {
      batches.add(uids.subList(start, Math.min(start + UID_BATCH_SIZE, uids.size())));
    }
    return batches;
  }

  private boolean execute(DicomParam[] keys) {
    completedBefore.set(received.get());
    DicomState state =
        RetrieveType.CGET == context.getType() ? executeGet(keys) : executeMove(keys);
    if (state == null) {
      return false;
    }
    // Status is qualified: LoadSeries declares its own nested Status enum
    int status = state.getStatus();
    if (status == org.dcm4che3.net.Status.Success || status == org.dcm4che3.net.Status.Cancel) {
      return true;
    }
    LOGGER.error("Retrieve of series {}: {}", getDicomSeries(), state.getMessage());
    return false;
  }

  private DicomState executeGet(DicomParam[] keys) {
    return CGet.process(
        context.getParams(),
        context.getCallingNodeForQuery(),
        context.getCalledNode(),
        progress,
        context.getStorageDir(),
        RetrieveContext.getCGetSopClassUrl(),
        keys);
  }

  private DicomState executeMove(DicomParam[] keys) {
    // The objects come back to the local listener, not through the retrieve association
    String error = context.ensureListenerStarted();
    if (error != null) {
      LOGGER.error("Retrieve of series {}: {}", getDicomSeries(), error);
      return null;
    }
    return CMove.process(
        context.getParams(),
        context.getCallingNodeForMove(),
        context.getCalledNode(),
        context.getDestinationAet(),
        progress,
        keys);
  }

  /** Imports each object as soon as C-GET has stored it, so the series fills up progressively. */
  private void importStoredFile(DicomProgress p) {
    if (p.getAttributes() != null || p.getProcessedFile() == null || cancelRequested.get()) {
      return;
    }
    Path file = p.getProcessedFile();
    applyIdentityOverrides(file, getDicomModel(), getDicomSeries());
    LoadLocalDicom task =
        new LoadLocalDicom(
            new File[] {file.toFile()}, false, getDicomModel(), context.getOpeningStrategy());
    DicomModel.LOADING_EXECUTOR.execute(task);
  }

  /**
   * Rewrites a retrieved object with the patient identity held by the model, the same way a
   * DICOMweb download applies the manifest overrides.
   *
   * <p>The import rebuilds the hierarchy from the object itself, and the patient node is keyed on a
   * pseudo UID derived from those tags. An archive answering the query and the retrieve with even
   * slightly different patient attributes would therefore file the objects under another patient
   * than the series being filled: that series would stay empty and a resumed retrieve, seeing
   * nothing loaded, would download everything again.
   *
   * @param file the received object, rewritten in place
   * @param model the explorer model holding the series being retrieved
   * @param target the series being filled, null to resolve it from the object itself, as for the
   *     objects a C-MOVE sends to the store listener
   */
  public static void applyIdentityOverrides(Path file, DicomModel model, DicomSeries target) {
    // Written next to the object then moved over it, because the dataset reads its bulk data back
    // from the original file while the copy is being written
    Path rewritten = file.resolveSibling(file.getFileName() + ".ovr"); // NON-NLS
    try {
      Attributes dataset;
      String tsuid;
      try (DicomInputStream in = new DicomInputStream(file.toFile())) {
        in.setIncludeBulkData(IncludeBulkData.URI);
        dataset = in.readDataset();
        tsuid = in.getTransferSyntax();
      }

      MediaSeriesGroup series =
          target != null ? target : findSeries(model, dataset.getString(Tag.SeriesInstanceUID));
      MediaSeriesGroup patient = model.getParent(series, DicomModel.patient);
      MediaSeriesGroup study = model.getParent(series, DicomModel.study);
      if (patient == null && study == null) {
        return; // Not a series of this retrieve, import the object as it was received
      }

      Attributes before = identity(dataset);
      SeriesDownloadManager.applyOverrides(dataset, IDENTITY_OVERRIDES, patient, study);
      if (before.equals(identity(dataset))) {
        return; // The archive is consistent with its query answer, the object can be kept as is
      }

      try (DicomOutputStream out = new DicomOutputStream(rewritten.toFile())) {
        out.writeDataset(dataset.createFileMetaInformation(tsuid), dataset);
        out.finish();
      }
      Files.move(rewritten, file, StandardCopyOption.REPLACE_EXISTING);
      LOGGER.debug("Patient identity of {} aligned with the query answer", file);
    } catch (Exception e) {
      // The object stays importable as received, only its patient identity may differ
      LOGGER.error("Applying the patient identity to {}", file, e);
      FileUtil.delete(rewritten);
    }
  }

  private static MediaSeriesGroup findSeries(DicomModel model, String seriesUid) {
    return StringUtil.hasText(seriesUid) ? model.getSeriesNode(seriesUid) : null;
  }

  /** Copy of the identifying tags, to tell whether the override actually changed the object. */
  private static Attributes identity(Attributes dataset) {
    Attributes identity = new Attributes(IDENTITY_OVERRIDES.length);
    identity.addSelected(dataset, IDENTITY_OVERRIDES);
    return identity;
  }

  /**
   * Advances the bar on each stored object, falling back on the sub-operation counts of the
   * response for C-MOVE, whose objects do not come back through this association.
   */
  private void handleProgress(DicomProgress p) {
    if (cancelRequested.get()) {
      // Objects keep arriving until the archive answers the C-CANCEL: the bar must stay put on the
      // count reached, which is also where a resumed retrieve will start again
      return;
    }
    int value;
    if (p.getAttributes() == null && p.getProcessedFile() != null) {
      value = received.incrementAndGet();
    } else {
      int completed = p.getNumberOfCompletedSuboperations();
      if (completed < 0) {
        return;
      }
      int handled =
          completed
              + Math.max(p.getNumberOfFailedSuboperations(), 0)
              + Math.max(p.getNumberOfWarningSuboperations(), 0);
      int batchTotal = completedBefore.get() + handled;
      value = received.updateAndGet(current -> Math.max(current, batchTotal));
      int remaining = p.getNumberOfRemainingSuboperations();
      if (remaining >= 0 && getSeriesInstanceList().isEmpty()) {
        // Nothing was enumerated: take the total the archive announces
        int total = batchTotal + remaining;
        GuiExecutor.execute(
            () -> {
              getProgressBar().setIndeterminate(false);
              getProgressBar().setMaximum(total);
            });
      }
    }
    GuiExecutor.execute(
        () -> {
          JProgressBar bar = getProgressBar();
          if (value > bar.getMaximum()) {
            // The series query under-reported its content, follow what actually arrives
            bar.setMaximum(value);
          }
          bar.setValue(value);
        });
  }

  /**
   * Waits for the retrieve this task resumes. A stopped retrieve unwinds on its own once the
   * C-CANCEL is answered, so without this the two would briefly request the same series at once.
   */
  private void awaitPrevious() {
    if (previous == null) {
      return;
    }
    try {
      previous.get(PREVIOUS_TIMEOUT_SEC, TimeUnit.SECONDS);
    } catch (CancellationException e) {
      // The previous retrieve was aborted, there is nothing left to wait for
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    } catch (TimeoutException e) {
      LOGGER.warn("Timeout while stopping the previous retrieve of series {}", getDicomSeries());
    } catch (ExecutionException e) {
      LOGGER.error("Previous retrieve of series {}", getDicomSeries(), e);
    }
  }

  /**
   * Waits for the objects handed to the single-threaded import executor: {@link #done()} removes a
   * series that is still empty, so it must not run before the imports it triggered. Skipped when
   * stopping, where that completion never runs and the executor may be busy for a long time.
   */
  private void awaitPendingImports() {
    if (cancelRequested.get() || isCancelled()) {
      return;
    }
    Future<?> barrier = DicomModel.LOADING_EXECUTOR.submit(() -> {});
    try {
      barrier.get(IMPORT_TIMEOUT_SEC, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    } catch (TimeoutException e) {
      LOGGER.warn("Timeout while importing the objects of series {}", getDicomSeries());
    } catch (ExecutionException e) {
      LOGGER.error("Importing the objects of series {}", getDicomSeries(), e);
    }
  }

  @Override
  protected LoadSeries createResumeTask() {
    return new LoadQrSeries(this);
  }

  /**
   * Frees the worker of the replaced retrieve at once. The download manager replaces a running
   * series to let a higher priority one take its slot, so waiting for the archive to answer the
   * C-CANCEL would keep the thread busy and defeat the reordering.
   */
  @Override
  public LoadSeries cancelAndReplace(LoadSeries s, boolean restartAllDownload) {
    if (s instanceof LoadQrSeries qrSeries) {
      qrSeries.preempt();
    }
    return super.cancelAndReplace(s, restartAllDownload);
  }

  /** Tears the association down, then takes the worker back without waiting for an answer. */
  private void preempt() {
    if (isDone()) {
      return;
    }
    cancelRequested.set(true);
    progress.abort();
    super.cancel();
  }

  /** Sends a C-CANCEL and lets the SCU release the association instead of cutting the worker. */
  @Override
  public boolean cancel() {
    boolean first = cancelRequested.compareAndSet(false, true);
    if (isDone() || !first || !retrieving.get()) {
      // Nothing on the wire to unwind, stop right away
      return super.cancel();
    }
    stopProgress();
    GracefulCancel.request(this::isDone, progress::cancel, progress::abort, super::cancel);
    return true;
  }

  /**
   * A graceful cancel lets the worker end on its own, so the task is not {@code isCancelled()} when
   * {@link #done()} runs. Without this the stopped series would be treated as complete and could no
   * longer be resumed.
   */
  @Override
  public boolean isStopped() {
    return cancelRequested.get() || super.isStopped();
  }

  @Override
  protected void done() {
    context.unregister(this);
    super.done();
    if (isStopped()) {
      // The stop only completes now, so the global message still announced a running download
      LoadSeries.notifyDownloadCompletion(getDicomModel());
    }
  }
}
