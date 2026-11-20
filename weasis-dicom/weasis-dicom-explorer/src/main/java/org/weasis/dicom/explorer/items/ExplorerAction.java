/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.explorer.items;

import java.util.Objects;

/**
 * One thing a plugin offers to do with an {@link ExplorerTarget}: an entry of the menus of the
 * explorer. The target is already bound: {@code run} needs no argument.
 *
 * @param id a stable id within its provider, for scripts and tests
 * @param label the text of the entry, in the language of the interface
 * @param tooltip more about the entry, may be null
 * @param enabled false to show the entry without letting it run (a task in progress)
 * @param run what the entry does, on the event dispatch thread; long work goes to a task
 */
public record ExplorerAction(
    String id, String label, String tooltip, boolean enabled, Runnable run) {

  public ExplorerAction {
    Objects.requireNonNull(id);
    Objects.requireNonNull(label);
    Objects.requireNonNull(run);
  }

  /** An enabled entry without a tooltip. */
  public static ExplorerAction of(String id, String label, Runnable run) {
    return new ExplorerAction(id, label, null, true, run);
  }

  /** An entry that only states something, such as a task that is running. */
  public static ExplorerAction info(String id, String label) {
    return new ExplorerAction(id, label, null, false, () -> {});
  }
}
