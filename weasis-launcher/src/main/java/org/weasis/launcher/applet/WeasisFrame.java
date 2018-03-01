/*******************************************************************************
 * Copyright (c) 2009-2018 Weasis Team and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 *     Nicolas Roduit - initial API and implementation
 *******************************************************************************/
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
