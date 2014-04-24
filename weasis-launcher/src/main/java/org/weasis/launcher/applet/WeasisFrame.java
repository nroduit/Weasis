package org.weasis.launcher.applet;

import javax.swing.JApplet;
import javax.swing.RootPaneContainer;

/**
 * User: boraldo Date: 03.02.14 Time: 14:23
 */
public class WeasisFrame implements WeasisFrameMBean {

    private RootPaneContainer rootPaneContainer;

    public WeasisFrame() {
    }

    public WeasisFrame(JApplet applet) {
        this.rootPaneContainer = applet;
    }

    public void setRootPaneContainer(RootPaneContainer rootPaneContainer) {
        this.rootPaneContainer = rootPaneContainer;
    }

    @Override
    public RootPaneContainer getRootPaneContainer() {
        return rootPaneContainer;
    }

}
