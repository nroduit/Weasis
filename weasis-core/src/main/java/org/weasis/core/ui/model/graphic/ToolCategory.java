/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.model.graphic;

/** Where a graphic tool is offered: the measurement palette, the drawing palette, or neither. */
public enum ToolCategory {
  /** Produces measurements, drawn on the {@code MEASURE} layer. */
  MEASURE,
  /** Decorative shapes and text, no measurement. */
  DRAW,
  /** Tools reachable from a panel or a command only, never from the default palettes. */
  ADVANCED
}
