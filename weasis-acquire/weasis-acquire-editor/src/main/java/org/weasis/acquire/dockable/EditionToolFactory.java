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
package org.weasis.acquire.dockable;

import java.util.Hashtable;

import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Deactivate;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Service;
import org.osgi.service.component.ComponentContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.base.viewer2d.EventManager;
import org.weasis.core.api.gui.Insertable;
import org.weasis.core.api.gui.Insertable.Type;
import org.weasis.core.api.gui.InsertableFactory;
import org.weasis.core.api.gui.util.ActionW;

/**
 * 
 * @author Yannick LARVOR
 * @version 2.5.0
 * @since v2.5.0 - 2016-04-06 - ylar - creation
 * 
 */
@Component(immediate = false)
@Service
@Property(name = "org.weasis.base.viewer2d.View2dContainer", value = "true")
public class EditionToolFactory implements InsertableFactory {
    private static final Logger LOGGER = LoggerFactory.getLogger(EditionToolFactory.class);

    private EditionTool toolPane = null;

    @Override
    public Type getType() {
        return Type.TOOL;
    }

    @Override
    public Insertable createInstance(Hashtable<String, Object> properties) {
        if (toolPane == null) {
            EventManager eventManager = EventManager.getInstance();

            // Remove actions which are not useful
            eventManager.removeAction(ActionW.SCROLL_SERIES);
            eventManager.removeAction(ActionW.WINLEVEL);
            eventManager.removeAction(ActionW.WINDOW);
            eventManager.removeAction(ActionW.LEVEL);
            eventManager.removeAction(ActionW.ROTATION);
            eventManager.removeAction(ActionW.FLIP);
            eventManager.removeAction(ActionW.FILTER);
            eventManager.removeAction(ActionW.INVERSESTACK);
            eventManager.removeAction(ActionW.INVERT_LUT);
            eventManager.removeAction(ActionW.LUT);
            eventManager.removeAction(ActionW.LAYOUT);
            eventManager.removeAction(ActionW.SYNCH);
            
            toolPane = new EditionTool(getType());
        }
        return toolPane;
    }

    @Override
    public void dispose(Insertable tool) {
        if (toolPane != null) {
            toolPane = null;
        }
    }

    @Override
    public boolean isComponentCreatedByThisFactory(Insertable tool) {
        return tool instanceof EditionTool;
    }

    @Activate
    protected void activate(ComponentContext context) {
        LOGGER.info("Activate the TransformationTool panel");
    }

    @Deactivate
    protected void deactivate(ComponentContext context) {
        LOGGER.info("Deactivate the TransformationTool panel");
    }

}