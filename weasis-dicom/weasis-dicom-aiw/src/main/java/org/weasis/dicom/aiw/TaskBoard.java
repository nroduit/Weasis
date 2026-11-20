/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.aiw;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Code;
import org.dcm4che3.data.Sequence;
import org.dcm4che3.data.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.dicom.web.ProcedureStepState;

/**
 * What is known of the tasks of the studies shown, read from their server in the background and
 * kept here, so that the menus and the marks of the explorer never wait for the network: they read
 * this board, and the board tells its listener when a study has news.
 *
 * <p>A study is read the first time it is asked for, after every task created or canceled from
 * here, and again every {@code pollSeconds} of its server while one of its tasks is waiting or
 * running.
 */
public final class TaskBoard {

  private static final Logger LOGGER = LoggerFactory.getLogger(TaskBoard.class);

  private static final TaskBoard INSTANCE = new TaskBoard();

  /**
   * One task of a study.
   *
   * @param workitem the UID of its workitem
   * @param code the task, by its value, scheme and meaning
   * @param state its state, null when the server gave one that is not of the standard
   * @param inputSeries the series it was asked on, empty when the server does not say
   * @param outputSeries the series of its results, empty until it is completed with results
   */
  public record Task(
      String workitem,
      Code code,
      ProcedureStepState state,
      Set<String> inputSeries,
      Set<String> outputSeries) {

    public boolean pending() {
      return state == ProcedureStepState.SCHEDULED || state == ProcedureStepState.IN_PROGRESS;
    }

    public boolean ready() {
      return state == ProcedureStepState.COMPLETED && !outputSeries.isEmpty();
    }

    /** Whether the task is the one of the catalog entry (value and scheme, PS3.3 Table 8.8-1). */
    public boolean is(Code other) {
      return code != null
          && Objects.equals(code.getCodeValue(), other.getCodeValue())
          && Objects.equals(code.getCodingSchemeDesignator(), other.getCodingSchemeDesignator());
    }

    static Task of(Attributes workitem) {
      Code code = null;
      Sequence codes = workitem.getSequence(Tag.ScheduledWorkitemCodeSequence);
      if (codes != null && !codes.isEmpty()) {
        Attributes c = codes.get(0);
        String value = c.getString(Tag.CodeValue);
        String scheme = c.getString(Tag.CodingSchemeDesignator);
        if (value != null && scheme != null) {
          code = new Code(value, scheme, null, c.getString(Tag.CodeMeaning, value));
        }
      }
      return new Task(
          workitem.getString(Tag.SOPInstanceUID),
          code,
          UpsService.stateOf(workitem),
          seriesOf(workitem.getSequence(Tag.InputInformationSequence)),
          seriesOf(workitem.getSequence(Tag.OutputInformationSequence)));
    }

    private static Set<String> seriesOf(Sequence items) {
      Set<String> series = new LinkedHashSet<>();
      if (items != null) {
        for (Attributes item : items) {
          String uid = item.getString(Tag.SeriesInstanceUID);
          if (uid != null) {
            series.add(uid);
          }
        }
      }
      return Set.copyOf(series);
    }
  }

  /**
   * The tasks of a study as last read.
   *
   * @param tasks the tasks, empty before the first answer
   * @param loaded false until the server answered once
   * @param error why the last read failed, null when it succeeded
   */
  public record Snapshot(List<Task> tasks, boolean loaded, String error) {
    static final Snapshot UNKNOWN = new Snapshot(List.of(), false, null);

    public boolean hasPending() {
      return tasks.stream().anyMatch(Task::pending);
    }
  }

  /** Where a study is read: the service of its server and how often to read it again. */
  public record Source(UpsService service, String patientId, String accession, int pollSeconds) {}

  private final Map<String, Snapshot> snapshots = new ConcurrentHashMap<>();
  private final Map<String, Source> sources = new ConcurrentHashMap<>();
  private final Set<String> reading = ConcurrentHashMap.newKeySet();
  private final Map<String, Long> nextPoll = new ConcurrentHashMap<>();
  private final ExecutorService worker = Executors.newVirtualThreadPerTaskExecutor();
  private volatile ScheduledExecutorService poller;
  private volatile Consumer<String> listener = _ -> {};

  TaskBoard() {}

  public static TaskBoard get() {
    return INSTANCE;
  }

  /** Who is told, with the Study Instance UID, each time a study was read; one listener. */
  public void setListener(Consumer<String> listener) {
    this.listener = listener == null ? _ -> {} : listener;
  }

  /**
   * The tasks of a study as last read, at once. A study never asked for is read in the background
   * and comes back not loaded; the listener is told when the answer is here.
   */
  public Snapshot snapshot(String studyUid, Source source) {
    Snapshot current = snapshots.get(studyUid);
    if (current == null) {
      current = Snapshot.UNKNOWN;
      refresh(studyUid, source);
    } else {
      sources.put(studyUid, source);
    }
    return current;
  }

  /** The tasks of a study as last read, without asking the server; unknown when never read. */
  public Snapshot peek(String studyUid) {
    return snapshots.getOrDefault(studyUid, Snapshot.UNKNOWN);
  }

  /** Reads the tasks of a study again in the background, unless a read is already running. */
  public void refresh(String studyUid, Source source) {
    Objects.requireNonNull(studyUid);
    sources.put(studyUid, Objects.requireNonNull(source));
    if (reading.add(studyUid)) {
      worker.execute(() -> read(studyUid, source));
    }
  }

  private void read(String studyUid, Source source) {
    try {
      store(studyUid, source, new Snapshot(readNow(studyUid, source), true, null));
    } catch (IOException | RuntimeException e) {
      LOGGER.warn("Cannot read the UPS workitems of study {}", studyUid, e);
      Snapshot previous = peek(studyUid);
      store(studyUid, source, new Snapshot(previous.tasks(), previous.loaded(), message(e)));
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    } finally {
      reading.remove(studyUid);
    }
  }

  static List<Task> readNow(String studyUid, Source source)
      throws IOException, InterruptedException {
    List<Task> tasks = new ArrayList<>();
    for (Attributes workitem :
        source.service().search(studyUid, source.patientId(), source.accession())) {
      tasks.add(Task.of(workitem));
    }
    return List.copyOf(tasks);
  }

  private void store(String studyUid, Source source, Snapshot snapshot) {
    snapshots.put(studyUid, snapshot);
    if (snapshot.hasPending() && source.pollSeconds() > 0) {
      nextPoll.put(studyUid, System.nanoTime() + TimeUnit.SECONDS.toNanos(source.pollSeconds()));
      startPoller();
    } else {
      nextPoll.remove(studyUid);
    }
    try {
      listener.accept(studyUid);
    } catch (RuntimeException e) {
      LOGGER.warn("Task board listener failed", e);
    }
  }

  private void startPoller() {
    if (poller == null) {
      synchronized (this) {
        if (poller == null) {
          ScheduledExecutorService service =
              Executors.newSingleThreadScheduledExecutor(
                  r -> {
                    Thread t = new Thread(r, "ups-task-poll"); // NON-NLS
                    t.setDaemon(true);
                    return t;
                  });
          service.scheduleWithFixedDelay(this::poll, 5, 5, TimeUnit.SECONDS);
          poller = service;
        }
      }
    }
  }

  private void poll() {
    long now = System.nanoTime();
    nextPoll.forEach(
        (studyUid, due) -> {
          Source source = sources.get(studyUid);
          if (source != null && now - due >= 0) {
            refresh(studyUid, source);
          }
        });
  }

  /** Forgets a study, when it leaves the model. */
  public void forget(String studyUid) {
    snapshots.remove(studyUid);
    sources.remove(studyUid);
    nextPoll.remove(studyUid);
  }

  /** The task of the workitem in what is known of a study. */
  public Optional<Task> find(String studyUid, String workitemUid) {
    return peek(studyUid).tasks().stream()
        .filter(t -> Objects.equals(t.workitem(), workitemUid))
        .findFirst();
  }

  private static String message(Exception e) {
    return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
  }
}
