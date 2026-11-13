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

  /**
   * The family of the tool on the explorer side: the tool id of the explorer it serves (the DICOM
   * import serves the DICOM explorer), its own id for an explorer heading a family, null for a tool
   * shown whatever the explorer. The stripe shows the explorers first, then only the tools of the
   * explorer shown.
   */
  default String getFamily() {
    return null;
  }

  /** Whether the tool heads its family: an explorer, which its companions serve. */
  default boolean isFamilyHead() {
    return getToolId().equals(getFamily());
  }

  /**
   * Whether the tool may leave its side to become a tab of the workspace. An explorer heading a
   * family never does: it anchors the stripe of its side and feeds the viewers beside it, so it
   * stays in its slot and widens the side when it needs room.
   */
  default boolean canBeView() {
    return !isFamilyHead();
  }

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

  /**
   * The content width, in device pixels, this tool needs to be seen whole when the user opens it.
   * When the side is narrower, it widens to that width (up to a share of the window) while the tool
   * is shown, then returns to the width of the user; a drag of the divider meanwhile is kept. 0,
   * the default, never widens the side.
   */
  default int getWidthToFit() {
    return 0;
  }

  void showDockable();

  void closeDockable();

  Component getToolComponent();

  int getDockableWidth();
}
