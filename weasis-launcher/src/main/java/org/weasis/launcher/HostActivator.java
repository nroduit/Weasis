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
package org.weasis.launcher;

import org.osgi.framework.Bundle;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;

public class HostActivator implements BundleActivator {

    private BundleContext bundleContext = null;

    @Override
    public void start(BundleContext bundleContext) {
        this.bundleContext = bundleContext;
    }

    @Override
    public void stop(BundleContext bundleContext) {
        this.bundleContext = null;
    }

    public Bundle[] getBundles() {
        if (bundleContext != null) {
            return bundleContext.getBundles();
        }
        return null;
    }

    public BundleContext getBundleContext() {
        return bundleContext;
    }
}
