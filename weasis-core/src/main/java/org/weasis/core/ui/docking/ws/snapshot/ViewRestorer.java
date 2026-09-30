/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.docking.ws.snapshot;

import java.util.Optional;
import org.weasis.core.ui.docking.ws.WorkspaceView;

/**
 * Resolves a snapshot descriptor back to a live view when a layout is restored: an existing view
 * (matched by id) or one recreated from the descriptor's state. An empty result skips the view and
 * the layout collapses gracefully.
 */
@FunctionalInterface
public interface ViewRestorer {

  Optional<WorkspaceView> recreate(WorkspaceSnapshot.ViewDescriptor descriptor);
}
