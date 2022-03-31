/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.api.gui.util;

import java.awt.Component;
import java.awt.Container;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.Window;
import javax.swing.JFrame;
import javax.swing.RootPaneContainer;
import javax.swing.SwingUtilities;

/** The Class WinUtil. */
public class WinUtil {

  private WinUtil() {}

  public static JFrame getParentJFrame(Component c) {
    for (Container p = c.getParent(); p != null; p = p.getParent()) {
      if (p instanceof JFrame frame) {
        return frame;
      }
    }
    return null;
  }

  public static Frame getParentFrame(Component c) {
    for (Container p = c.getParent(); p != null; p = p.getParent()) {
      if (p instanceof Frame frame) {
        return frame;
      }
    }
    return null;
  }

  public static Dialog getParentDialog(Component c) {
    for (Container p = c.getParent(); p != null; p = p.getParent()) {
      if (p instanceof Dialog dialog) {
        return dialog;
      }
    }
    return null;
  }

  public static RootPaneContainer getRootPaneContainer(Component c) {
    if (c instanceof RootPaneContainer container) {
      return container;
    }
    for (Container p = c.getParent(); p != null; p = p.getParent()) {
      if (p instanceof RootPaneContainer container) {
        return container;
      }
    }
    return null;
  }

  public static Window getParentWindow(Component component) {
    return SwingUtilities.getWindowAncestor(component);
  }

  @SuppressWarnings("unchecked")
  public static <T> T getParentOfClass(Component component, Class<T> class1) {
    Component obj = component;
    while (obj != null && !class1.isAssignableFrom(obj.getClass())) {
      obj = obj.getParent();
    }
    return (T) (obj);
  }

  public static void center(Component component, int i, int j, int k, int l) {
    int i1 = component.getSize().width;
    int j1 = component.getSize().height;
    int k1 = (k - i1) / 2;
    int l1 = (l - j1) / 2;
    int i2 = i + k1;
    int j2 = j + l1;
    Dimension dimension = getScreenSize();
    if (i2 + i1 >= dimension.width) {
      i2 = dimension.width - i1;
    }
    if (j2 + j1 >= dimension.height) {
      j2 = dimension.height - j1;
    }
    if (i2 < 0) {
      i2 = 0;
    }
    if (j2 < 0) {
      j2 = 0;
    }
    component.setLocation(i2, j2);
  }

  public static GraphicsConfiguration getGraphicsDeviceConfig(Point p) {
    GraphicsConfiguration gc = null;
    // Try to find GraphicsConfiguration, that includes mouse
    // pointer position
    GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
    GraphicsDevice[] gd = ge.getScreenDevices();
    for (GraphicsDevice graphicsDevice : gd) {
      if (graphicsDevice.getType() == GraphicsDevice.TYPE_RASTER_SCREEN) {
        GraphicsConfiguration dgc = graphicsDevice.getDefaultConfiguration();
        if (dgc.getBounds().contains(p)) {
          gc = dgc;
          break;
        }
      }
    }
    return gc;
  }

  public static Rectangle getClosedScreenBound(Rectangle bound) {
    Toolkit toolkit = Toolkit.getDefaultToolkit();
    GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
    GraphicsDevice[] gd = ge.getScreenDevices();
    Rectangle screenBounds = null;
    Rectangle intersect = null;
    for (GraphicsDevice graphicsDevice : gd) {
      GraphicsConfiguration config = graphicsDevice.getDefaultConfiguration();
      Rectangle b = config.getBounds();
      Rectangle intersection = bound.intersection(b);
      if (intersection.width > 0 && intersection.height > 0) {
        if (intersect == null
            || (intersect.width * intersect.height) < (intersection.width * intersection.height)) {
          Insets inset = toolkit.getScreenInsets(config);
          b.x += inset.left;
          b.y += inset.top;
          b.width -= (inset.left + inset.right);
          b.height -= (inset.top + inset.bottom);

          screenBounds = b;
          intersect = intersection;
        }
      }
    }
    return screenBounds;
  }

  public static void adjustLocationToFitScreen(Component cp, Point p) {
    if (cp == null) {
      return;
    }
    if (p == null) {
      centerOnScreen(cp);
      return;
    }
    Toolkit toolkit = Toolkit.getDefaultToolkit();
    Rectangle screenBounds;
    Insets screenInsets;
    GraphicsConfiguration gc = getGraphicsDeviceConfig(p);
    if (gc != null) {
      // If we have GraphicsConfiguration use it to get
      // screen bounds and insets
      screenInsets = toolkit.getScreenInsets(gc);
      screenBounds = gc.getBounds();
    } else {
      // If we don't have GraphicsConfiguration use primary screen
      // and empty insets
      screenInsets = new Insets(0, 0, 0, 0);
      screenBounds = new Rectangle(toolkit.getScreenSize());
    }
    int scrWidth = screenBounds.width - (screenInsets.left + screenInsets.right);
    int scrHeight = screenBounds.height - (screenInsets.top + screenInsets.bottom);
    Dimension size = cp.getPreferredSize();
    if ((p.x + size.width) > screenBounds.x + scrWidth) {
      p.x = screenBounds.x + scrWidth - (size.width + 2);
    }
    if ((p.y + size.height) > screenBounds.y + scrHeight) {
      p.y = screenBounds.y + scrHeight - (size.height + 2);
    }
    /*
     * Change is made to the desired (X,Y) values, when the PopupMenu is too tall OR too wide for the screen
     */
    if (p.x < screenBounds.x) {
      p.x = screenBounds.x;
    }
    if (p.y < screenBounds.y) {
      p.y = screenBounds.y;
    }
    cp.setLocation(p);
  }

  public static void center(Component component, Point point, Dimension dimension) {
    center(component, point.x, point.y, dimension.width, dimension.height);
  }

  public static void center(Component component, Component component1) {
    if (component1 == null) {
      center(component);
    } else {
      center(component, component1.getLocation(), component1.getSize());
    }
  }

  private static void center(Component component) {
    Container container = component != null ? component.getParent() : null;
    if (container != null) {
      Window window = SwingUtilities.getWindowAncestor(container);
      if (window == null) {
        centerOnScreen(component);
      } else {
        center(component, window);
      }
    }
  }

  public static void centerOnScreen(Component component) {
    Dimension dimension = getScreenSize();
    center(component, 0, 0, dimension.width, dimension.height);
  }

  public static Point translate(Component component, Point point) {
    if (point == null || component == null) {
      return null;
    } else {
      Point point1 = component.getLocationOnScreen();
      point1.translate(point.x, point.y);
      return point1;
    }
  }

  public static Dimension getScreenSize() {
    return Toolkit.getDefaultToolkit().getScreenSize();
  }
}
