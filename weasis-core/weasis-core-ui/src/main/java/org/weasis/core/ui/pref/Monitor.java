package org.weasis.core.ui.pref;

import java.awt.GraphicsConfiguration;
import java.awt.GraphicsDevice;
import java.awt.Rectangle;

public class Monitor {
    private final GraphicsDevice graphicsDevice;
    private double pitch;

    public Monitor(GraphicsDevice graphicsDevice) {
        this.pitch = 0.0;
        this.graphicsDevice = graphicsDevice;
    }

    public double getPitch() {
        return pitch;
    }

    public void setPitch(double realScaleFactor) {
        this.pitch = realScaleFactor;
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
