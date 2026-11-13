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

import java.util.Objects;
import javax.swing.Icon;

/**
 * A button of a side stripe that runs an action instead of showing a tool, such as the import of a
 * disc in one click. It sits among the tool buttons by position (after a tool of the same position)
 * and has no slot, no selected state and no place in the side state.
 *
 * @param id a plain lowercase token, the name of the button
 * @param position the order among the tool positions of the side
 * @param icon the icon, derived to the stripe size when it is an SVG icon; null for the default
 * @param name the tooltip
 * @param action what a click runs, on the EDT
 * @param family the tool id of the explorer the action serves (shown only with it), null for an
 *     action shown whatever the explorer
 */
public record StripeAction(
    String id, int position, Icon icon, String name, Runnable action, String family) {
  public StripeAction {
    Objects.requireNonNull(id);
    Objects.requireNonNull(name);
    Objects.requireNonNull(action);
  }

  /** An action of no family, shown whatever the explorer. */
  public StripeAction(String id, int position, Icon icon, String name, Runnable action) {
    this(id, position, icon, name, action, null);
  }
}
