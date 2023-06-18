/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.acquire.dockable.components.actions.calibrate;

import javax.swing.JLabel;
import org.weasis.acquire.Messages;
import org.weasis.acquire.dockable.EditionToolFactory;
import org.weasis.acquire.dockable.components.actions.AbstractAcquireActionPanel;
import org.weasis.acquire.dockable.components.actions.rectify.RectifyPanel;
import org.weasis.acquire.explorer.AcquireImageInfo;
import org.weasis.acquire.explorer.AcquireImageValues;
import org.weasis.acquire.graphics.CalibrationGraphic;
import org.weasis.base.viewer2d.EventManager;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.ui.editor.image.ImageViewerPlugin;

public class CalibrationPanel extends AbstractAcquireActionPanel {

  public static final CalibrationGraphic CALIBRATION_LINE_GRAPHIC = new CalibrationGraphic();

  public CalibrationPanel() {
    add(new JLabel(Messages.getString("CalibrationPanel.draw_line")));
  }

  @Override
  public void initValues(AcquireImageInfo info, AcquireImageValues values) {
    EventManager.getInstance()
        .getAction(EditionToolFactory.DRAW_EDITION)
        .ifPresent(a -> a.setSelectedItem(CalibrationPanel.CALIBRATION_LINE_GRAPHIC));
    ImageViewerPlugin<ImageElement> container =
        EventManager.getInstance().getSelectedView2dContainer();
    RectifyPanel.applyEditAction(this, container);
  }
}
