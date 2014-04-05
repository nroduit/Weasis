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

import org.weasis.launcher.WeasisLauncher;

public class AppletLauncher extends JApplet {

    private static final long serialVersionUID = -5661026047717759806L;

    @Override
    public void init() {

        new Thread() {
            @Override
            public void run() {
                try {
                    MBeanServer server = ManagementFactory.getPlatformMBeanServer();
                    ObjectName objectName = new ObjectName("weasis:name=MainWindow");
                    server.registerMBean(new WeasisApplet(AppletLauncher.this), objectName);

                    String commands = getParameter("commands");
                    System.out.println("WeasisLauncher init JApplet : " + commands);
                    WeasisLauncher.launch(commands == null ? new String[0] : new String[] { commands });

                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }.start();
    }

}
