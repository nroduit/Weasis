/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.api.util;

/**
 * A source of native (off-heap) memory tracked by the {@link MemoryManager}.
 *
 * <p>Implementations report how much native memory they currently hold so the manager can sum the
 * footprint of every consumer and expose a single, process-wide view of native-memory pressure.
 */
@FunctionalInterface
public interface NativeMemoryConsumer {

  /**
   * @return the amount of native memory currently held by this consumer, in bytes (never negative).
   */
  long usedNativeMemory();
}
