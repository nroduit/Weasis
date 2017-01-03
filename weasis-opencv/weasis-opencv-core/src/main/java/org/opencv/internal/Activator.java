package org.opencv.internal;

import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;

public class Activator implements BundleActivator {

    @Override
    public void start(final BundleContext bundleContext) throws Exception {
        // // load the native OpenCV library
        System.loadLibrary("opencv_java");
    }

    @Override
    public void stop(BundleContext bundleContext) throws Exception {
    }

}
