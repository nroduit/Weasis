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
                    ObjectName objectName = new ObjectName("weasis:name=MainWindow");
                    server.registerMBean(new WeasisFrame(WebStartAppletLauncher.this), objectName);

                    // TODO test commands with quotes (path) in html page
                    String commands = getParameter("commands");
                    System.out.println("WebstartLauncher init JApplet : " + commands);
                    WebstartLauncher.launch(commands == null ? new String[0] : commands.split(" "));

                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }.start();
    }
}
