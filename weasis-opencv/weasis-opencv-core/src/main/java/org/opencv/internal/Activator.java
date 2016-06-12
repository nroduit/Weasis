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
package org.opencv.internal;

import org.opencv.core.Core;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;

public class Activator implements BundleActivator {

    @Override
    public void start(final BundleContext bundleContext) throws Exception {
        // // load the native OpenCV library
        System.loadLibrary(Core.NATIVE_LIBRARY_NAME);
    }

    @Override
    public void stop(BundleContext bundleContext) throws Exception {
    }

}
