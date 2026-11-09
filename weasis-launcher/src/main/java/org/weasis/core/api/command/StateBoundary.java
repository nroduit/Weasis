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

import java.util.List;
import java.util.Optional;

/**
 * The transaction boundary of the display: a checkpoint of what the viewer shows, taken before a
 * batch of commands acts, and the rollback to it when the batch fails. The viewer registers one
 * implementation on the {@link CommandManager}; a headless launcher has none and every call is a
 * no-op, so the script runner and the remote dispatch never depend on the UI.
 */
public interface StateBoundary {

  /** The boundary of a launcher without a display: nothing captured, nothing restored. */
  StateBoundary NONE =
      new StateBoundary() {
        @Override
        public Optional<String> checkpoint(String label, CommandOrigin origin) {
          return Optional.empty();
        }

        @Override
        public List<String> rollback(String checkpointId) {
          return List.of("No display to restore");
        }
      };

  /**
   * Takes a checkpoint of the display.
   *
   * @param label what the checkpoint precedes, shown to the user ("before script demo.wcs")
   * @param origin who asked for the work the checkpoint precedes
   * @return the checkpoint id, or empty when nothing can be captured (no workspace, a modal dialog
   *     open, an interaction in progress)
   */
  Optional<String> checkpoint(String label, CommandOrigin origin);

  /**
   * Restores the display of a checkpoint.
   *
   * @return what could not be restored, one line each; empty when everything came back
   * @throws IllegalArgumentException when the checkpoint is unknown
   */
  List<String> rollback(String checkpointId);
}
