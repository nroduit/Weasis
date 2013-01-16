package org.weasis.core.ui.pref;

public class Monitor {
    private final String id;
    private int width;
    private int height;
    private double realScaleFactor;

    public Monitor(String id, int width, int height) {
        this.id = id;
        this.width = width;
        this.height = height;
        this.realScaleFactor = 0.0;
    }

    public double getRealScaleFactor() {
        return realScaleFactor;
    }

    public void setRealScaleFactor(double realScaleFactor) {
        this.realScaleFactor = realScaleFactor;
    }

    public String getId() {
        return id;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public void changeResolution(int width, int height) {
        if (realScaleFactor != 0.0) {
            if (((double) this.width / this.height) - ((double) width / height) > 0.01) {
                // If screen ratio changes, reset scale factor
                realScaleFactor = 0.0;
            } else {
                // TODO validate
                realScaleFactor *= (double) this.width / width;
            }

        }
        this.width = width;
        this.height = height;
    }

}
