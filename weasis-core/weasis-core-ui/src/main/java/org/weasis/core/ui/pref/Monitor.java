package org.weasis.core.ui.pref;

import java.awt.GraphicsConfiguration;
import java.awt.GraphicsDevice;
import java.awt.Rectangle;

public class Monitor {
    private final GraphicsDevice graphicsDevice;
    private double realScaleFactor;

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

}
