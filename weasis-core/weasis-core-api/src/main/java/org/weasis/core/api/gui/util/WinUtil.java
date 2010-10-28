/*******************************************************************************
 * Copyright (c) 2010 Nicolas Roduit.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     Nicolas Roduit - initial API and implementation
 ******************************************************************************/
package org.weasis.core.api.gui.util;

import java.awt.AWTError;
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
import java.util.ArrayList;

import javax.swing.JOptionPane;

/**
 * The Class WinUtil.
 * 
 * @author Nicolas Roduit
 */
public abstract class WinUtil {

    protected static boolean c_beep_allowed = true;

    private WinUtil() {
    }

    public static Frame getParentFrame(Component component) {
        Object obj;
        for (obj = component; !(obj instanceof Frame) && obj != null; obj = ((Component) (obj)).getParent()) {
            ;
        }
        return (Frame) obj;
    }

    public static Dialog getParentDialog(Component component) {
        Object obj;
        for (obj = component; !(obj instanceof Dialog) && obj != null; obj = ((Component) (obj)).getParent()) {
            ;
        }
        return (Dialog) obj;
    }

    public static Window getParentWindow(Component component) {
        Object obj;
        for (obj = component; !(obj instanceof Window) && obj != null; obj = ((Component) (obj)).getParent()) {
            ;
        }
        return (Window) obj;
    }

    public static Window getParentDialogOrFrame(Component component) {
        Object obj;
        for (obj = component; !(obj instanceof Frame) && !(obj instanceof Dialog) && obj != null; obj =
            ((Component) (obj)).getParent()) {
            ;
        }
        return (Window) obj;
    }

    public static Component getParentOfClass(Component component, Class class1) {
        Object obj;
        for (obj = component; obj != null && !class1.isAssignableFrom(obj.getClass()); obj =
            ((Component) (obj)).getParent()) {
            ;
        }
        return ((Component) (obj));
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
        for (int i = 0; i < gd.length; i++) {
            if (gd[i].getType() == GraphicsDevice.TYPE_RASTER_SCREEN) {
                GraphicsConfiguration dgc = gd[i].getDefaultConfiguration();
                if (dgc.getBounds().contains(p)) {
                    gc = dgc;
                    break;
                }
            }
        }
        return gc;
    }

    public static Dimension getRealScreenSize(Point p) {
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
        int scrWidth = screenBounds.width - Math.abs(screenInsets.left + screenInsets.right);
        int scrHeight = screenBounds.height - Math.abs(screenInsets.top + screenInsets.bottom);
        return new Dimension(scrWidth, scrHeight);
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
        int scrWidth = screenBounds.width - Math.abs(screenInsets.left + screenInsets.right);
        int scrHeight = screenBounds.height - Math.abs(screenInsets.top + screenInsets.bottom);
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
        Window window = getParentDialogOrFrame(container);
        if (window == null) {
            centerOnScreen(component);
        } else {
            center(component, ((window)));
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

    public static Component[] searchComponentHierarchy(Component component, Class class1) {
        ArrayList arraylist = new ArrayList();
        searchComponentHierarchyImpl(component, class1, arraylist);
        Component acomponent[] = new Component[arraylist.size()];
        return (Component[]) arraylist.toArray(acomponent);
    }

    public static Component searchComponentHierarchy(Component component, Class class1, int i) {
        Component acomponent[] = searchComponentHierarchy(component, class1);
        if (acomponent == null || acomponent.length <= i) {
            return null;
        } else {
            return acomponent[i];
        }
    }

    public static void searchComponentHierarchyImpl(Component component, Class class1, ArrayList arraylist) {
        if (class1.isAssignableFrom(component.getClass())) {
            arraylist.add(component);
        }
        if (!(component instanceof Container)) {
            return;
        }
        Container container = (Container) component;
        int i = container.getComponentCount();
        for (int j = 0; j < i; j++) {
            searchComponentHierarchyImpl(container.getComponent(j), class1, arraylist);
        }
    }

    public static void makeComponentOrphan(Component component) {
        if (component != null) {
            Container container = component.getParent();
            if (container != null) {
                container.remove(component);
            }
        }
    }

    public static boolean isBeepEnabled() {
        return c_beep_allowed;
    }

    public static void setBeepEnabled(boolean flag) {
        c_beep_allowed = flag;
    }

    public static void soundBeep() {
        if (!isBeepEnabled()) {
            return;
        }
        Object obj = null;
        try {
            Toolkit toolkit = Toolkit.getDefaultToolkit();
            toolkit.beep();
        } catch (AWTError awterror) {
        }
    }

    public static void showMessageDialog(Component component, String s) {
        JOptionPane.showMessageDialog(component, s);
    }
}
