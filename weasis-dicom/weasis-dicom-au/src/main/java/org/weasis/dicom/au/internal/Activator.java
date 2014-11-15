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
package org.weasis.dicom.au.internal;

import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.weasis.core.ui.docking.UIManager;
import org.weasis.dicom.au.AuContainer;

public class Activator implements BundleActivator {

    @Override
    public void start(final BundleContext bundleContext) throws Exception {
    }

    @Override
    public void stop(BundleContext bundleContext) throws Exception {
        UIManager.closeSeriesViewerType(AuContainer.class);
    }

}
