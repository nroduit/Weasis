package org.weasis.launcher;

import java.awt.Rectangle;
import java.awt.Window;

/**
 * User: boraldo
 * Date: 03.02.14
 * Time: 14:44
 */
public class WindowBoundsUtils {

    public static void setWindowLocation(Window window, Rectangle bound) {
        int x = bound.x + (bound.width - window.getWidth()) / 2;
        int y = bound.y + (bound.height - window.getHeight()) / 2;
        window.setLocation(x, y);
    }

}
