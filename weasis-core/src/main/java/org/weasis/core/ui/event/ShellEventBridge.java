/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.event;

import java.util.List;
import org.weasis.core.api.event.Event;
import org.weasis.core.api.event.EventBus;
import org.weasis.core.api.event.EventType;

/** The bus events of the shell around the workspace. */
public final class ShellEventBridge {

  public static final String EXPLORER_CHANGED = "workspace:explorer.changed"; // NON-NLS

  private ShellEventBridge() {}

  public static List<EventType> types() {
    return List.of(
        EventType.of(
                EXPLORER_CHANGED,
                "5.0", // NON-NLS
                "The explorer side shows another family: an explorer and the tools that serve it.")
            .field("explorer", String.class, "tool id of the explorer shown, as workspace:explorer")
            .build());
  }

  /** Publishes that the explorer side shows the family of that explorer. */
  public static void explorerChanged(String explorerId) {
    EventBus.global().publish(Event.of(EXPLORER_CHANGED).put("explorer", explorerId)); // NON-NLS
  }
}
