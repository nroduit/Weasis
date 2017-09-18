/*******************************************************************************
 * Copyright (c) 2017 Weasis Team.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     Nicolas Roduit - initial API and implementation
 ******************************************************************************/
package org.weasis.dicom.rt;

import java.util.Hashtable;

import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Deactivate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.gui.Insertable;
import org.weasis.core.api.gui.Insertable.Type;
import org.weasis.core.api.gui.InsertableFactory;
import org.weasis.dicom.viewer2d.EventManager;

@org.osgi.service.component.annotations.Component(service = InsertableFactory.class, immediate = false, property = {
    "org.weasis.dicom.viewer2d.View2dContainer=true" })
public class RtDisplayToolFactory implements InsertableFactory {
    private static final Logger LOGGER = LoggerFactory.getLogger(RtDisplayToolFactory.class);

    private RtDisplayTool toolPane = null;

    @Override
    public Insertable createInstance(Hashtable<String, Object> properties) {
        if (toolPane == null) {
            toolPane = new RtDisplayTool();
            EventManager.getInstance().addSeriesViewerListener(toolPane);
        }
        return toolPane;
    }

    @Override
    public void dispose(Insertable tool) {
        if (toolPane != null) {
            EventManager.getInstance().removeSeriesViewerListener(toolPane);
            toolPane = null;
        }
    }

    @Override
    public boolean isComponentCreatedByThisFactory(Insertable component) {
        return component instanceof RtDisplayTool;
    }

    @Override
    public Type getType() {
        return Type.TOOL;
    }

    // ================================================================================
    // OSGI service implementation
    // ================================================================================

    @Activate
    protected void activate(ComponentContext context) {
        LOGGER.info("Activate the RT panel"); //$NON-NLS-1$
    }

    @Deactivate
    protected void deactivate(ComponentContext context) {
        LOGGER.info("Deactivate the RT panel"); //$NON-NLS-1$
    }

}
