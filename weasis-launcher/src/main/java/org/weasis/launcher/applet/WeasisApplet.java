package org.weasis.launcher.applet;

import javax.swing.JApplet;
import javax.swing.RootPaneContainer;

/**
 * User: boraldo Date: 03.02.14 Time: 14:23
 */
public class WeasisApplet implements WeasisAppletMBean {

    private RootPaneContainer rootPaneContainer;

    @Override
    public RootPaneContainer getRootPaneContainer() {
        return rootPaneContainer;
    }

    public WeasisApplet(JApplet applet) {
        this.rootPaneContainer = applet;
    }

}
