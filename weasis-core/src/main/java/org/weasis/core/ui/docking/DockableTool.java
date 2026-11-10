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

import java.awt.Component;
import org.weasis.core.api.gui.Insertable;
import org.weasis.core.ui.editor.Relevant;

/**
 * A panel hosted by a shell side around the workspace. Its {@link #getToolId() id} is stable across
 * viewer types, so the display tool of a 2D viewer and the display tool of a 3D viewer are the same
 * tool for the shell: swapped in place, named by a UI profile, remembered by a session. {@link
 * Relevant#isRelevant} decides whether it is offered for the selected view (always, unless the tool
 * overrides it); the user's choice to hide it is a setting on top of that.
 */
public interface DockableTool extends Insertable, Relevant {

  /** A plain lowercase token, never a localized name: {@code display}, {@code measure}, ... */
  String getToolId();

  /** The slot the tool opens in when the user clicks its stripe button. */
  default Slot getPreferredSlot() {
    return Slot.TOP;
  }

  /**
   * The share of the side height this tool asks for when it fills the bottom slot, in ]0, 1[, or 0
   * to share the ratio of the side with the other bottom tools. A tool with a share of its own
   * opens at it and then keeps the ratio the user drags for it.
   */
  default double getPreferredBottomShare() {
    return 0;
  }

  void showDockable();

  void closeDockable();

  Component getToolComponent();

  int getDockableWidth();
}
