/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.editor;

import java.awt.GraphicsConfiguration;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.Toolkit;

/**
 * Describes how a viewer should be displayed in an external (detached) window.
 *
 * <p>Use the static factory methods to create instances:
 *
 * <ul>
 *   <li>{@link #onScreen(GraphicsDevice)} – open on a specific screen device
 *   <li>{@link #onOtherScreen(Rectangle)} – automatically pick a screen that does <em>not</em>
 *       contain the given bounds (typically the main application window)
 *   <li>{@link #atBounds(Rectangle)} – open at explicit pixel coordinates (low-level escape hatch)
 * </ul>
 *
 * <p>All variants expose the resolved screen bounds via {@link #screenBounds()}, which is what the
 * docking framework uses to position the external window.
 *
 * @see ViewerOpenOptions#externalDisplay()
 */
public sealed interface ExternalDisplay {

  /**
   * Returns the usable screen bounds (excluding system insets such as taskbars) for the target
   * display.
   */
  Rectangle screenBounds();

  // ---------------------------------------------------------------------------
  // Factory methods
  // ---------------------------------------------------------------------------

  /**
   * Open on the specified screen device.
   *
   * @param screen the target {@link GraphicsDevice}; must not be {@code null}
   */
  static ExternalDisplay onScreen(GraphicsDevice screen) {
    return new OnScreen(screen);
  }

  /**
   * Automatically select a screen that does <em>not</em> contain the given rectangle (typically the
   * main application window bounds). If no other screen is found, {@code null} is returned.
   *
   * @param currentWindowBounds the bounds of the main window to avoid
   * @return an {@link ExternalDisplay} targeting another screen, or {@code null} if only one screen
   *     is available
   */
  static ExternalDisplay onOtherScreen(Rectangle currentWindowBounds) {
    GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
    GraphicsDevice[] devices = ge.getScreenDevices();
    if (devices.length <= 1) {
      return null;
    }
    for (GraphicsDevice device : devices) {
      Rectangle bounds = device.getDefaultConfiguration().getBounds();
      if (!bounds.contains(currentWindowBounds)) {
        return new OnScreen(device);
      }
    }
    return null;
  }

  /**
   * Open at explicit pixel coordinates. This is a low-level escape hatch; prefer {@link
   * #onScreen(GraphicsDevice)} when possible.
   *
   * @param bounds the exact screen area to use
   */
  static ExternalDisplay atBounds(Rectangle bounds) {
    return new AtBounds(bounds);
  }

  // ---------------------------------------------------------------------------
  // Sealed implementations
  // ---------------------------------------------------------------------------

  /** Open on a specific screen device. */
  record OnScreen(GraphicsDevice screen) implements ExternalDisplay {

    public OnScreen {
      if (screen == null) {
        throw new IllegalArgumentException("screen must not be null");
      }
    }

    @Override
    public Rectangle screenBounds() {
      GraphicsConfiguration config = screen.getDefaultConfiguration();
      Rectangle bounds = config.getBounds();
      Insets insets = Toolkit.getDefaultToolkit().getScreenInsets(config);
      bounds.x += insets.left;
      bounds.y += insets.top;
      bounds.width -= (insets.left + insets.right);
      bounds.height -= (insets.top + insets.bottom);
      return bounds;
    }

    @Override
    public String toString() {
      return "ExternalDisplay.OnScreen[" + screen.getIDstring() + "]";
    }
  }

  /** Open at explicit pixel coordinates. */
  record AtBounds(Rectangle bounds) implements ExternalDisplay {

    public AtBounds {
      if (bounds == null) {
        throw new IllegalArgumentException("bounds must not be null");
      }
    }

    @Override
    public Rectangle screenBounds() {
      return new Rectangle(bounds);
    }

    @Override
    public String toString() {
      return "ExternalDisplay.AtBounds[" + bounds + "]";
    }
  }
}
