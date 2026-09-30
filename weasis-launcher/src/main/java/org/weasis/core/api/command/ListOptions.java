/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.api.command;

import picocli.CommandLine.Option;

/**
 * Picocli mixin shared by the verbs that list things: a caller with a small context asks for a
 * window of the listing instead of the whole of it. The listing always says how many entries it
 * holds and whether it stopped short, so the next call is {@code --after} that many.
 */
public class ListOptions {

  @Option(
      names = "--limit",
      paramLabel = "<n>",
      description = "At most this many entries (default: all of them).")
  @Since("5.0")
  private Integer limit;

  @Option(
      names = "--after",
      paramLabel = "<n>",
      description = "Skip the first n entries, as the previous answer's 'next' says.")
  @Since("5.0")
  private Integer after;

  /** Index of the first entry to return, counting from 0. */
  public int from() {
    return after == null || after < 0 ? 0 : after;
  }

  /** Index after the last entry to return, given how many there are. */
  public int to(int total) {
    if (limit == null) {
      return total;
    }
    if (limit <= 0) {
      throw new IllegalArgumentException("--limit must be positive");
    }
    return Math.min(total, from() + limit);
  }

  /** Whether the caller asked for a window at all. */
  public boolean bounded() {
    return limit != null || after != null;
  }
}
