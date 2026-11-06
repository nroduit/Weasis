/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.print;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import org.weasis.core.api.media.data.MaskingProfile;
import org.weasis.core.ui.print.PageSpec.Orientation;
import org.weasis.core.ui.util.PrintOptions;

/**
 * The print jobs the commands hold between {@code print:page} and {@code print:render} or {@code
 * print:send}: a bounded registry, the oldest job disposed when a new one does not fit.
 */
public final class PrintJobs {

  public static final int CAPACITY = 8;

  private static final PrintJobs INSTANCE = new PrintJobs(CAPACITY);

  private final int capacity;
  private final AtomicInteger sequence = new AtomicInteger();
  private final Map<String, PrintJob> jobs = new LinkedHashMap<>();

  PrintJobs(int capacity) {
    this.capacity = capacity;
  }

  public static PrintJobs getInstance() {
    return INSTANCE;
  }

  /** Registers a new job under a fresh handle, {@code job-N}. */
  public PrintJob create(
      String viewId,
      PageSource pages,
      Paper paper,
      Orientation orientation,
      PrintOptions options,
      MaskingProfile masking) {
    return create(id -> new PrintJob(id, viewId, pages, paper, orientation, options, masking));
  }

  PrintJob create(Function<String, PrintJob> factory) {
    List<PrintJob> evicted = new ArrayList<>();
    PrintJob job;
    synchronized (jobs) {
      job = factory.apply("job-" + sequence.incrementAndGet()); // NON-NLS
      jobs.put(job.id(), job);
      var it = jobs.values().iterator();
      while (jobs.size() > capacity && it.hasNext()) {
        evicted.add(it.next());
        it.remove();
      }
    }
    evicted.forEach(PrintJob::dispose);
    return job;
  }

  public Optional<PrintJob> find(String id) {
    synchronized (jobs) {
      return Optional.ofNullable(jobs.get(id));
    }
  }

  public List<String> ids() {
    synchronized (jobs) {
      return List.copyOf(jobs.keySet());
    }
  }

  /** Removes and disposes the job, if it is still held. */
  public void remove(String id) {
    PrintJob job;
    synchronized (jobs) {
      job = jobs.remove(id);
    }
    if (job != null) {
      job.dispose();
    }
  }
}
