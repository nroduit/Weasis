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

import java.awt.Component;

/**
 * A subscriber's hold on the bus. Closing it is the only way to stop the deliveries, so a UI
 * subscriber binds it to a lifetime it does not control itself: the component that owns it ({@link
 * #until(Component)}) or the view it serves ({@link #untilViewClosed(String)}).
 */
public interface Subscription extends AutoCloseable {

  /** How a subscriber receives its events. */
  enum Delivery {
    /** On a thread of the bus, one event at a time, never two deliveries at once. */
    ASYNC,
    /** On the event dispatch thread, the pending events of a tick drained in one call. */
    EDT
  }

  /** A short handle, unique for the life of the instance. */
  String id();

  /** What the subscriber said it is, for the listing of subscribers. */
  String owner();

  EventFilter filter();

  Delivery delivery();

  boolean isActive();

  /** How many events this subscriber lost for falling behind. */
  long dropped();

  /** Closes when the component is no longer displayable, which is when its window closed. */
  Subscription until(Component component);

  /** Closes when the workspace view with this id is closed. */
  Subscription untilViewClosed(String viewId);

  /** Whether the owner component was given and is no longer displayable. */
  boolean isDangling();

  @Override
  void close();
}
