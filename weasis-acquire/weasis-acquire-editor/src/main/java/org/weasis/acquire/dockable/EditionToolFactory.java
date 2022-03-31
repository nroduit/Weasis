/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.acquire.dockable;

import java.awt.event.KeyEvent;
import java.util.Hashtable;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Deactivate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.acquire.Messages;
import org.weasis.acquire.dockable.components.actions.calibrate.CalibrationPanel;
import org.weasis.base.viewer2d.EventManager;
import org.weasis.core.api.gui.Insertable;
import org.weasis.core.api.gui.Insertable.Type;
import org.weasis.core.api.gui.InsertableFactory;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.gui.util.BasicActionState;
import org.weasis.core.api.gui.util.ComboItemListener;
import org.weasis.core.api.service.BundleTools;
import org.weasis.core.ui.editor.image.MeasureToolBar;
import org.weasis.core.ui.model.graphic.Graphic;

/**
 * @author Yannick LARVOR
 * @author Nicolas Roduit
 * @since v2.5.0
 */
@org.osgi.service.component.annotations.Component(
    service = InsertableFactory.class,
    property = {"org.weasis.base.viewer2d.View2dContainer=true"})
public class EditionToolFactory implements InsertableFactory {
  private static final Logger LOGGER = LoggerFactory.getLogger(EditionToolFactory.class);

  public static final ActionW EDITION =
      new ActionW(Messages.getString("edit"), "draw.edition", KeyEvent.VK_G, 0, null) { // NON-NLS
        @Override
        public boolean isDrawingAction() {
          return true;
        }
      };
  // Starting cmd by "draw.sub." defines a drawing action with a derivative action
  public static final ActionW DRAW_EDITION =
      new ActionW("", ActionW.DRAW_CMD_PREFIX + EDITION.cmd(), 0, 0, null);

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
      eventManager.removeAction(ActionW.INVERSE_STACK);
      eventManager.removeAction(ActionW.INVERT_LUT);
      eventManager.removeAction(ActionW.LUT);
      eventManager.removeAction(ActionW.LAYOUT);
      eventManager.removeAction(ActionW.SYNCH);
      BundleTools.SYSTEM_PREFERENCES.setProperty(
          "weasis.contextmenu.close", Boolean.FALSE.toString());

      eventManager.setAction(new BasicActionState(EDITION));
      eventManager.setAction(
          new ComboItemListener<>(
              DRAW_EDITION,
              new Graphic[] {
                MeasureToolBar.selectionGraphic, CalibrationPanel.CALIBRATION_LINE_GRAPHIC
              }) {

            @Override
            public void itemStateChanged(Object object) {
              // Do nothing
            }
          });
      toolPane = new EditionTool(getType());
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
  public boolean isComponentCreatedByThisFactory(Insertable tool) {
    return tool instanceof EditionTool;
  }

  // ================================================================================
  // OSGI service implementation
  // ================================================================================

  @Activate
  protected void activate(ComponentContext context) {
    LOGGER.info("Activate the TransformationTool panel");
  }

  @Deactivate
  protected void deactivate(ComponentContext context) {
    LOGGER.info("Deactivate the TransformationTool panel");
  }
}
