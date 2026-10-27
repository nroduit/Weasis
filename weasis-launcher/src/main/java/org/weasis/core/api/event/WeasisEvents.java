/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.api.event;

import java.util.List;

/**
 * The events of the application itself and of the command bridge, published by the launcher and the
 * bus: they exist in every profile, so the bus declares them.
 */
public final class WeasisEvents {

  private static final String SINCE = "5.0"; // NON-NLS

  /** The plugins are started and the commands answer. */
  public static final String READY = "weasis:ready"; // NON-NLS

  /** The application is stopping. */
  public static final String SHUTDOWN = "weasis:shutdown"; // NON-NLS

  /** A subscriber lost events for falling behind; to that subscriber only. */
  public static final String SUBSCRIBER_DROPPED = "weasis:subscriber.dropped"; // NON-NLS

  /** Keeps a stream alive through a proxy; carries the last sequence number. */
  public static final String HEARTBEAT = "weasis:heartbeat"; // NON-NLS

  /** A modal dialog opened or closed, so a caller whose command waits knows why. */
  public static final String UI_MODAL = "weasis:ui.modal"; // NON-NLS

  /** The user is interacting; at most one every two seconds. */
  public static final String USER_ACTIVE = "weasis:user.active"; // NON-NLS

  /** A remote command finished; for a host that tails its own calls. */
  public static final String COMMAND_FINISHED = "command:finished"; // NON-NLS

  private WeasisEvents() {}

  public static List<EventType> types() {
    return List.of(
        EventType.of(READY, SINCE, "The plugins are started and the commands answer.")
            .field("version", String.class, "application version")
            .field("apiVersion", String.class, "command API version")
            .field("profile", String.class, "configuration profile")
            .build(),
        EventType.of(SHUTDOWN, SINCE, "The application is stopping.").build(),
        EventType.of(
                SUBSCRIBER_DROPPED,
                SINCE,
                "This subscriber fell behind: the oldest pending events were dropped; one that "
                    + "loses "
                    + EventBus.DETACH_AFTER_DROPS
                    + " events without catching up is detached.")
            .field("subscriber", String.class, "subscription id")
            .field("dropped", int.class, "events dropped since the subscription started")
            .field("detached", boolean.class, "whether the subscription was closed")
            .build(),
        EventType.of(HEARTBEAT, SINCE, "Sent on an idle stream; seq is the last sequence number.")
            .build(),
        EventType.of(UI_MODAL, SINCE, "A modal dialog opened or closed.")
            .field("state", String.class, "opened or closed")
            .field("title", String.class, "dialog title")
            .build(),
        EventType.of(USER_ACTIVE, SINCE, "The user moved the mouse or typed.")
            .coalesced()
            .field("window", int.class, "window number, as workspace:list numbers them")
            .build(),
        EventType.of(COMMAND_FINISHED, SINCE, "A command of a remote caller finished.")
            .field("command", String.class, "namespace:verb")
            .field("status", int.class, "exit status")
            .field("durationMs", int.class, "how long it ran")
            .build());
  }
}
