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
package org.weasis.base.viewer2d.internal;

import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;
import org.weasis.base.viewer2d.EventManager;
import org.weasis.base.viewer2d.View2dContainer;
import org.weasis.core.api.service.BundlePreferences;
import org.weasis.core.ui.docking.UIManager;

public class Activator implements BundleActivator {

    public static final BundlePreferences PREFERENCES = new BundlePreferences();
    private final BundleContext context = FrameworkUtil.getBundle(this.getClass()).getBundleContext();

    @Override
    public void start(BundleContext bundleContext) throws Exception {
        PREFERENCES.init(context);
    }

    @Override
    public void stop(BundleContext bundleContext) throws Exception {
        // Save preferences
        EventManager.getInstance().savePreferences();
        PREFERENCES.close();
        UIManager.closeSeriesViewerType(View2dContainer.class);
    }

}
