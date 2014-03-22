package org.weasis.launcher.applet;

import java.awt.Window;

import javax.swing.RootPaneContainer;

/**
 * User: boraldo
 * Date: 03.02.14
 * Time: 14:23
 */
public interface WeasisAppletMBean {

    void addWindow(Window window);

    RootPaneContainer getRootPaneContainer();

}
