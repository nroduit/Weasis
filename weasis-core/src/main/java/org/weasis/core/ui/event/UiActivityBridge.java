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

import java.awt.AWTEvent;
import java.awt.Dialog;
import java.awt.GraphicsEnvironment;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.AWTEventListener;
import java.awt.event.InputEvent;
import java.awt.event.WindowEvent;
import java.util.concurrent.TimeUnit;
import java.util.function.ToIntFunction;
import org.weasis.core.api.event.Event;
import org.weasis.core.api.event.EventBus;
import org.weasis.core.api.event.WeasisEvents;

/**
 * Publishes {@code weasis:user.active} from the mouse and keyboard, at most once every two seconds,
 * and {@code weasis:ui.modal} when a modal dialog opens or closes: component-agnostic signals read
 * from the toolkit, which a batch boundary or a caller whose command waits can use. Headless,
 * nothing is installed.
 */
public final class UiActivityBridge implements AWTEventListener {

  private static final long ACTIVITY_INTERVAL = TimeUnit.SECONDS.toNanos(2);

  private final ToIntFunction<Window> windowNumber;
  private volatile long lastActivity;

  private UiActivityBridge(ToIntFunction<Window> windowNumber) {
    this.windowNumber = windowNumber;
  }

  /**
   * Installs the bridge; {@code windowNumber} numbers a window as {@code workspace:list} does, or
   * returns 0 for one it does not know.
   */
  public static void install(ToIntFunction<Window> windowNumber) {
    if (GraphicsEnvironment.isHeadless()) {
      return;
    }
    Toolkit.getDefaultToolkit()
        .addAWTEventListener(
            new UiActivityBridge(windowNumber),
            AWTEvent.MOUSE_EVENT_MASK
                | AWTEvent.MOUSE_MOTION_EVENT_MASK
                | AWTEvent.KEY_EVENT_MASK
                | AWTEvent.WINDOW_EVENT_MASK);
  }

  @Override
  public void eventDispatched(AWTEvent event) {
    if (event instanceof InputEvent input && isUserInput(input)) {
      long now = System.nanoTime();
      if (now - lastActivity < ACTIVITY_INTERVAL) {
        return;
      }
      lastActivity = now;
      Window window = windowOf(input.getComponent());
      Event.Builder active = Event.of(WeasisEvents.USER_ACTIVE).coalesce(WeasisEvents.USER_ACTIVE);
      if (window != null) {
        int number = windowNumber.applyAsInt(window);
        if (number > 0) {
          active.put("window", number); // NON-NLS
        }
      }
      EventBus.global().publish(active);
    } else if (event instanceof WindowEvent windowEvent
        && windowEvent.getWindow() instanceof Dialog dialog
        && dialog.isModal()) {
      String state =
          switch (windowEvent.getID()) {
            case WindowEvent.WINDOW_OPENED -> "opened"; // NON-NLS
            case WindowEvent.WINDOW_CLOSED -> "closed"; // NON-NLS
            default -> null;
          };
      if (state != null) {
        EventBus.global()
            .publish(
                Event.of(WeasisEvents.UI_MODAL)
                    .put("state", state) // NON-NLS
                    .identity("title", dialog.getTitle(), Event.FREE_TEXT)); // NON-NLS
      }
    }
  }

  /**
   * Presses, drags, wheel turns, moves and key presses; not the enter/exit the toolkit makes up.
   */
  private static boolean isUserInput(InputEvent input) {
    return switch (input.getID()) {
      case java.awt.event.MouseEvent.MOUSE_PRESSED,
          java.awt.event.MouseEvent.MOUSE_DRAGGED,
          java.awt.event.MouseEvent.MOUSE_WHEEL,
          java.awt.event.MouseEvent.MOUSE_MOVED,
          java.awt.event.KeyEvent.KEY_PRESSED ->
          true;
      default -> false;
    };
  }

  private static Window windowOf(java.awt.Component component) {
    return component instanceof Window window
        ? window
        : component == null ? null : javax.swing.SwingUtilities.getWindowAncestor(component);
  }
}
