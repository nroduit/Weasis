package org.weasis.launcher.applet;

import org.weasis.launcher.WindowBoundsUtils;
import sun.awt.SunToolkit;

import javax.management.MBeanServer;
import javax.swing.JApplet;
import javax.swing.RootPaneContainer;
import java.awt.Rectangle;
import java.awt.Window;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.List;

/**
 * User: boraldo Date: 03.02.14 Time: 14:23
 */
public class WeasisApplet implements WeasisAppletMBean {

    private List<Window> windows = new ArrayList<Window>();
    private Window mainWindow;
    private RootPaneContainer rootPaneContainer;

    @Override
    public RootPaneContainer getRootPaneContainer() {
        return rootPaneContainer;
    }

    public WeasisApplet(JApplet applet) {
        this.rootPaneContainer = applet;
        mainWindow = SunToolkit.getContainingWindow(applet);

        mainWindow.addComponentListener(new ComponentAdapter() {

            private void setWindowLocation() {
                Rectangle bound = mainWindow.getBounds();
                for (Window window : windows)
                    WindowBoundsUtils.setWindowLocation(window, bound);
            }

            @Override
            public void componentResized(ComponentEvent e) {
                setWindowLocation();
            }

            @Override
            public void componentMoved(ComponentEvent e) {
                setWindowLocation();
            }

            @Override
            public void componentShown(ComponentEvent e) {
                setWindowLocation();
            }
        });

    }

    @Override
    public void addWindow(Window window) {
        windows.add(window);
        WindowBoundsUtils.setWindowLocation(window, mainWindow.getBounds());
    }

}
