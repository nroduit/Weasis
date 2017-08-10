/*******************************************************************************
 * Copyright (c) 2016 Weasis Team and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Nicolas Roduit - initial API and implementation
 *******************************************************************************/
package org.weasis.dicom.rendering;

import java.util.Hashtable;

import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Deactivate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.gui.Insertable;
import org.weasis.core.api.gui.Insertable.Type;
import org.weasis.core.api.gui.InsertableFactory;

@org.osgi.service.component.annotations.Component(service = InsertableFactory.class, immediate = false, property = {
    "org.weasis.dicom.viewer2d.View2dContainer=true" })
public class DicomRenderingFactory implements InsertableFactory {

    private static final Logger LOGGER = LoggerFactory.getLogger(DicomRenderingFactory.class);

    private RenderingToolbar toolBar = null;

    @Override
    public Type getType() {
        return Type.TOOLBAR;
    }

    @Override
    public Insertable createInstance(Hashtable<String, Object> properties) {
        if (toolBar == null) {
            toolBar = new RenderingToolbar();
        }
        return toolBar;
    }

    @Override
    public boolean isComponentCreatedByThisFactory(Insertable component) {
        return component instanceof RenderingToolbar;
    }

    @Override
    public void dispose(Insertable bar) {
        if (toolBar != null) {
            toolBar = null;
        }
    }

    @Activate
    protected void activate(ComponentContext context) throws Exception {
        LOGGER.info("{} activated", RenderingToolbar.PUBLISH_MF.getTitle()); //$NON-NLS-1$
    }

    @Deactivate
    protected void deactivate(ComponentContext context) {
        LOGGER.info("{} deactivated", RenderingToolbar.PUBLISH_MF.getTitle()); //$NON-NLS-1$
    }
}
