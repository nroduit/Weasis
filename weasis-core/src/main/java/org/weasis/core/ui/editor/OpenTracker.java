/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.editor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * The state a viewer keeps while it builds its content from a series: the {@link PromptPolicy} that
 * settles its questions, the warnings those questions leave behind and the future that {@link
 * SeriesViewer#whenReady()} exposes. A build starts with {@link #begin()}, which hands out the
 * future to settle, so a failure before the build even starts still fails the right future.
 */
public final class OpenTracker {

  private volatile PromptPolicy promptPolicy = PromptPolicy.ASK;
  private final List<String> warnings = Collections.synchronizedList(new ArrayList<>());
  private volatile CompletableFuture<Void> ready = CompletableFuture.completedFuture(null);

  public void setPromptPolicy(PromptPolicy policy) {
    this.promptPolicy = policy == null ? PromptPolicy.ASK : policy;
  }

  public PromptPolicy promptPolicy() {
    return promptPolicy;
  }

  /** Records a question raised by the build, whether it was asked or settled by the policy. */
  public void addWarning(String message) {
    if (message != null && !message.isBlank()) {
      warnings.add(message);
    }
  }

  /** Starts a new build: forgets the previous warnings and returns the future the build settles. */
  public CompletableFuture<Void> begin() {
    warnings.clear();
    CompletableFuture<Void> future = new CompletableFuture<>();
    ready = future;
    return future;
  }

  public boolean complete() {
    return ready.complete(null);
  }

  public boolean fail(Throwable cause) {
    return ready.completeExceptionally(cause);
  }

  public CompletableFuture<Void> whenReady() {
    return ready;
  }

  /** The {@link SeriesViewer#openReport()} entries: the warnings and, when known, rectification. */
  public Map<String, Object> report(Boolean rectified) {
    Map<String, Object> report = new HashMap<>();
    if (rectified != null) {
      report.put("rectified", rectified); // NON-NLS
    }
    report.put("warnings", List.copyOf(warnings)); // NON-NLS
    return report;
  }
}
