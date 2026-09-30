/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.docking.ws.drag;

import org.weasis.core.ui.docking.ws.DockSide;
import org.weasis.core.ui.docking.ws.TabGroup;

/** Result of hit-testing a tab drag position: where the dragged view would land. */
public sealed interface DropZone {

  /** Insert as a tab of {@code group} at {@code tabIndex} (-1 appends). */
  record Stack(TabGroup group, int tabIndex) implements DropZone {}

  /** Split {@code group}, the dragged view taking the {@code side} half. */
  record Split(TabGroup group, DockSide side) implements DropZone {}

  /** Detach into a new window at the pointer location. */
  record Detach() implements DropZone {}
}
