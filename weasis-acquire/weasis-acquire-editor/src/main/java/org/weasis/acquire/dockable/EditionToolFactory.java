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

import java.awt.event.KeyEvent;
import java.util.Hashtable;
import java.util.Optional;

import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Deactivate;
import org.osgi.service.component.ComponentContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.acquire.dockable.components.actions.calibrate.CalibrationPanel;
import org.weasis.base.viewer2d.EventManager;
import org.weasis.core.api.gui.Insertable;
import org.weasis.core.api.gui.Insertable.Type;
import org.weasis.core.api.gui.InsertableFactory;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.gui.util.BasicActionState;
import org.weasis.core.api.gui.util.ComboItemListener;
import org.weasis.core.ui.editor.image.MeasureToolBar;
import org.weasis.core.ui.model.graphic.Graphic;

/**
 * 
 * @author Yannick LARVOR, Nicolas Roduit
 * @version 2.5.0
 * @since v2.5.0 - 2016-04-06 - ylar - creation
 * 
 */
@org.apache.felix.scr.annotations.Component(immediate = false)
@org.apache.felix.scr.annotations.Service
@org.apache.felix.scr.annotations.Property(name = "org.weasis.base.viewer2d.View2dContainer", value = "true")
public class EditionToolFactory implements InsertableFactory {
    private static final Logger LOGGER = LoggerFactory.getLogger(EditionToolFactory.class);

    public static final ActionW EDITON = new ActionW("Edit", "draw.edition", KeyEvent.VK_G, 0, null) { //$NON-NLS-1$ $NON-NLS-2$
        @Override
        public boolean isDrawingAction() {
            return true;
        }
    };
    // Starting cmd by "draw.sub." defines a drawing action with a derivative action
    public static final ActionW DRAW_EDITON = new ActionW("", ActionW.DRAW_CMD_PREFIX + EDITON.cmd(), 0, 0, null); //$NON-NLS-1$

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
            eventManager.setAction(new BasicActionState(EDITON));
            eventManager.setAction(new ComboItemListener<Graphic>(DRAW_EDITON,
                new Graphic[] {MeasureToolBar.selectionGraphic, CalibrationPanel.CALIBRATION_LINE_GRAPHIC }) {

                @Override
                public void itemStateChanged(Object object) {
                    // Do nothing
                }
            });
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