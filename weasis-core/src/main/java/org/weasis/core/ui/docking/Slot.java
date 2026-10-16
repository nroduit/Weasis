/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.docking;

/**
 * The two slots of a shell side, stacked under one horizontal divider. A tool opens in its
 * preferred slot and replaces what was there; a third slot would be a new constant here and a new
 * entry in the slot list of a {@link ShellState.ShellSide}, nothing else.
 */
public enum Slot {
  TOP,
  BOTTOM;

  /** The other slot of the same side. */
  public Slot other() {
    return this == TOP ? BOTTOM : TOP;
  }
}
