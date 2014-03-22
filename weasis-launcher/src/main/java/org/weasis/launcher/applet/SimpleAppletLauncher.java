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

import java.awt.Window;
import java.lang.management.ManagementFactory;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import javax.jnlp.ServiceManager;
import javax.jnlp.SingleInstanceListener;
import javax.jnlp.SingleInstanceService;
import javax.jnlp.UnavailableServiceException;
import javax.management.*;
import javax.swing.JApplet;

import org.osgi.util.tracker.ServiceTracker;
import org.weasis.launcher.WeasisLauncher;

import sun.awt.SunToolkit;

public class SimpleAppletLauncher extends JApplet {

    public static final String PREFIX = "jnlp.weasis.";
    public static final int PREFIX_LENGTH = PREFIX.length();

    @Override
    public void init() {

        new Thread() {
            @Override
            public void run() {
                try {

                    MBeanServer server = ManagementFactory.getPlatformMBeanServer();
                    ObjectName objectName = new ObjectName("weasis:name=MainWindow");
                    server.registerMBean(new WeasisApplet(SimpleAppletLauncher.this), objectName);

                    Properties properties = System.getProperties();
                    for (String key : properties.stringPropertyNames()) {
                        if (key.startsWith(PREFIX)) {
                            String value = properties.getProperty(key);
                            key = key.substring(PREFIX_LENGTH);
                            System.setProperty(key, value);
                        }
                    }

                    String commands = getParameter("commands");
                    WeasisLauncher.launch(commands == null ? new String[0] : commands.split(" "));
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }.start();
    }

}
