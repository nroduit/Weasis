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

import java.lang.management.ManagementFactory;

import javax.management.MBeanServer;
import javax.management.ObjectName;
import javax.swing.JApplet;

import org.weasis.launcher.WebstartLauncher;

public class WebStartAppletLauncher extends JApplet {

    private static final long serialVersionUID = -3060663263099612730L;

    @Override
    public void init() {

        new Thread() {
            @Override
            public void run() {
                try {
                    MBeanServer server = ManagementFactory.getPlatformMBeanServer();
                    ObjectName objectName = new ObjectName("weasis:name=MainWindow"); //$NON-NLS-1$
                    server.registerMBean(new WeasisFrame(WebStartAppletLauncher.this), objectName);

                    // TODO test commands with quotes (path) in html page
                    String commands = getParameter("commands"); //$NON-NLS-1$
                    System.out.println("WebstartLauncher init JApplet : " + commands); //$NON-NLS-1$
                    WebstartLauncher.launch(commands == null ? new String[0] : commands.split(" ")); //$NON-NLS-1$

                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }.start();
    }
}
