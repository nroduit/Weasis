/*******************************************************************************
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.weasis.core.ui.pref;

import java.awt.Frame;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsDevice;
import java.awt.Rectangle;
import java.util.List;

import javax.swing.JFrame;

import org.weasis.core.ui.editor.image.dockable.MeasureTool;

public class Monitor {
    private final GraphicsDevice graphicsDevice;
    private double realScaleFactor;
    private Rectangle fullscreenBounds;

    public Monitor(GraphicsDevice graphicsDevice) {
        this.realScaleFactor = 0.0;
        this.graphicsDevice = graphicsDevice;
    }

    public synchronized double getRealScaleFactor() {
        return realScaleFactor;
    }

    public synchronized void setRealScaleFactor(double realScaleFactor) {
        this.realScaleFactor = realScaleFactor;
    }

    public String getMonitorID() {
        return graphicsDevice.getIDstring();
    }

    public Rectangle getBounds() {
        return graphicsDevice.getDefaultConfiguration().getBounds();
    }

    public GraphicsConfiguration getGraphicsConfiguration() {
        return graphicsDevice.getDefaultConfiguration();
    }

    public GraphicsDevice getGraphicsDevice() {
        return graphicsDevice;
    }

    public Rectangle getFullscreenBounds() {
        if (fullscreenBounds == null) {
            /*
             * As screen insets are not available on all the systems (on X11 windowing systems), the only way to get the
             * maximum visible size desktop is to maximize a JFrame
             */
            JFrame frame = new JFrame(this.getGraphicsConfiguration());
            Rectangle bound = this.getBounds();
            frame.setBounds(bound.x, bound.y, bound.width - 150, bound.height - 150);
            frame.setVisible(true);
            frame.setExtendedState(Frame.MAXIMIZED_BOTH);

            try {
                // Let time to maximize window
                Thread.sleep(500);
            } catch (InterruptedException e1) {
                Thread.currentThread().interrupt();
            }

            fullscreenBounds = frame.getBounds();
            frame.dispose();
        }
        return fullscreenBounds;
    }

    public static Monitor getMonitor(GraphicsConfiguration gconfig) {
        if (gconfig != null) {
            List<Monitor> monitors = MeasureTool.viewSetting.getMonitors();
            for (int i = 0; i < monitors.size(); i++) {
                Monitor monitor = monitors.get(i);
                if (gconfig.equals(monitor.getGraphicsConfiguration())) {
                    return monitor;
                }
            }
        }
        return null;
    }

    public static Monitor getDefaultMonitor() {
        int defIndex = ScreenPrefView.getDefaultMonitor();
        List<Monitor> monitors = MeasureTool.viewSetting.getMonitors();
        if (monitors.isEmpty()) {
            return null;
        }
        if (defIndex < 0 || defIndex >= monitors.size()) {
            defIndex = 0;
        }
        return monitors.get(defIndex);
    }
}
