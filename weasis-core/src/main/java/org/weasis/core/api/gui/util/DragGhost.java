/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.api.gui.util;

import java.awt.Component;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Window;
import java.util.IdentityHashMap;
import java.util.Map;
import javax.swing.Icon;
import javax.swing.RootPaneContainer;
import javax.swing.SwingUtilities;

/**
 * The thumbnail that follows the pointer while something is dragged (a series from the explorer):
 * drawn on the glass pane of the Weasis window under the pointer, so that it follows the drag into
 * the viewer windows as well as the main window. A window whose glass pane is not a {@link
 * GhostGlassPane} gets one for the time of the drag, and its own glass pane back afterwards.
 *
 * <p>The positions are screen positions, as the drag source gives them: the windows of Weasis share
 * them on X11 (XWayland included), Windows and macOS.
 */
public final class DragGhost {

  private static Icon icon;
  private static Point offset;
  private static GhostGlassPane shown;
  private static final Map<RootPaneContainer, Component> replaced = new IdentityHashMap<>();

  private DragGhost() {}

  /**
   * Starts showing {@code dragged}; {@code grip} is the point of the icon under the pointer.
   *
   * @param screen the pointer, in screen coordinates
   */
  public static void start(Icon dragged, Point grip, Point screen) {
    stop();
    icon = dragged;
    offset = grip == null ? new Point() : new Point(grip);
    move(screen);
  }

  /** Moves the thumbnail to the pointer, into the window under it. */
  public static void move(Point screen) {
    if (icon == null || screen == null) {
      return;
    }
    RootPaneContainer target = windowAt(screen);
    GhostGlassPane pane = target == null ? null : ghostPaneOf(target);
    if (pane != shown) {
      hide(shown);
      shown = pane;
      if (pane != null) {
        pane.setIcon(icon);
        pane.setVisible(true);
      }
    }
    if (pane != null) {
      Point p = new Point(screen);
      SwingUtilities.convertPointFromScreen(p, pane);
      p.translate(-offset.x, -offset.y);
      pane.setImagePosition(p);
    }
  }

  /** Hides the thumbnail and gives the windows their own glass panes back. */
  public static void stop() {
    hide(shown);
    shown = null;
    icon = null;
    offset = null;
    for (Map.Entry<RootPaneContainer, Component> entry : replaced.entrySet()) {
      entry.getKey().setGlassPane(entry.getValue());
      entry.getValue().setVisible(false);
      // swapping the glass pane repaints nothing: clear the last image of the thumbnail
      entry.getKey().getRootPane().repaint();
    }
    replaced.clear();
  }

  private static void hide(GhostGlassPane pane) {
    if (pane != null) {
      pane.setImagePosition(null);
      pane.setIcon(null);
      pane.setVisible(false);
    }
  }

  /**
   * The Weasis window under a screen point; when windows overlap there, the last opened one (a
   * viewer window is opened after the main window and usually lies over it).
   */
  private static RootPaneContainer windowAt(Point screen) {
    RootPaneContainer found = null;
    for (Window window : Window.getWindows()) {
      if (window.isShowing()
          && window instanceof RootPaneContainer container
          && new Rectangle(window.getLocationOnScreen(), window.getSize()).contains(screen)) {
        found = container;
      }
    }
    return found;
  }

  private static GhostGlassPane ghostPaneOf(RootPaneContainer container) {
    if (container.getGlassPane() instanceof GhostGlassPane pane) {
      return pane;
    }
    GhostGlassPane pane = new GhostGlassPane();
    replaced.put(container, container.getGlassPane());
    container.setGlassPane(pane);
    return pane;
  }
}
