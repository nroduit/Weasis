package org.weasis.base.explorer.internal;

import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.weasis.core.api.service.BundlePreferences;

public class Activator implements BundleActivator {

    public static final BundlePreferences PREFERENCES = new BundlePreferences();

    public void start(final BundleContext context) throws Exception {
        PREFERENCES.init(context);
    }

    public void stop(BundleContext bundleContext) throws Exception {
        PREFERENCES.close();
    }

}