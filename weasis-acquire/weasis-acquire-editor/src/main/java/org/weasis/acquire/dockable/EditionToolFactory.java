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
import org.weasis.acquire.Messages;
import org.weasis.acquire.dockable.components.actions.calibrate.CalibrationPanel;
import org.weasis.base.viewer2d.EventManager;
import org.weasis.core.api.gui.Insertable;
import org.weasis.core.api.gui.Insertable.Type;
import org.weasis.core.api.gui.InsertableFactory;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.gui.util.BasicActionState;
import org.weasis.core.api.gui.util.ComboItemListener;
import org.weasis.core.api.gui.util.Feature;
import org.weasis.core.api.gui.util.Feature.BasicActionStateValue;
import org.weasis.core.api.gui.util.Feature.ComboItemListenerValue;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.ui.docking.ExtToolFactory;
import org.weasis.core.ui.editor.image.ImageViewerEventManager;
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
public class EditionToolFactory extends ExtToolFactory<ImageElement> {
  public static final BasicActionStateValue EDITION =
      new BasicActionStateValue(
          Messages.getString("edit"), "draw.edition", KeyEvent.VK_G, 0, null) { // NON-NLS
        @Override
        public boolean isDrawingAction() {
          return true;
        }
      };
  // Starting cmd by "draw.sub." defines a drawing action with a derivative action
  public static final ComboItemListenerValue<Graphic> DRAW_EDITION =
      new ComboItemListenerValue<>("", Feature.DRAW_CMD_PREFIX + EDITION.cmd(), 0, 0, null);

  public EditionToolFactory() {
    super(EditionTool.BUTTON_NAME);
  }

  @Override
  public Type getType() {
    return Type.TOOL;
  }

  @Override
  public boolean isComponentCreatedByThisFactory(Insertable tool) {
    return tool instanceof EditionTool;
  }

  @Override
  protected ImageViewerEventManager<ImageElement> getImageViewerEventManager() {
    return EventManager.getInstance();
  }

  @Override
  protected boolean isCompatible(Hashtable<String, Object> properties) {
    return true;
  }

  @Override
  protected Insertable getInstance(Hashtable<String, Object> properties) {
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
    GuiUtils.getUICore()
        .getSystemPreferences()
        .setProperty("weasis.contextmenu.close", Boolean.FALSE.toString());

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
    return new EditionTool(getType());
  }
}
