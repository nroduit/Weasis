/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.codec.seg;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ForkJoinPool;

/**
 * SPI used by {@link SegSpecialElement} to schedule the asynchronous build of the canonical
 * segmentation volume. The default implementation simply submits the work to the common {@link
 * ForkJoinPool} and exposes no UI. The DICOM explorer module installs a richer implementation that
 * surfaces the build as a cancellable task in the explorer's bottom loading panel.
 *
 * <p>Implementations must:
 *
 * <ul>
 *   <li>Run {@code work} off the EDT.
 *   <li>Return a {@link CompletableFuture} that completes with the built volume (or {@code null}
 *       when the work returns null, or exceptionally on failure).
 *   <li>Honour {@link CompletableFuture#cancel(boolean)} on the returned future by cancelling the
 *       underlying work and any UI surface they may have created.
 * </ul>
 */
@FunctionalInterface
public interface SegmentationVolumeBuildExecutor {

  /**
   * Default executor backed by {@link ForkJoinPool#commonPool()} with no UI integration. Used when
   * no richer executor has been registered (e.g. headless tests or modules running without the
   * DICOM explorer).
   */
  SegmentationVolumeBuildExecutor DEFAULT =
      (seg, work) ->
          CompletableFuture.supplyAsync(
              () -> {
                try {
                  return work.call();
                } catch (RuntimeException e) {
                  throw e;
                } catch (Exception e) {
                  throw new CompletionException(e);
                }
              },
              ForkJoinPool.commonPool());

  /**
   * Schedules {@code work} asynchronously on behalf of {@code seg}.
   *
   * @param seg the segmentation owning the build
   * @param work the build work to run; must not be invoked on the EDT
   * @return a future that completes with the built volume; cancelling it must cancel the work
   */
  CompletableFuture<SegmentationVolume> schedule(
      SegSpecialElement seg, Callable<SegmentationVolume> work);
}
