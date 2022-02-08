/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.acquire.dockable.components.actions.annotate;

import javax.swing.BoxLayout;
import org.weasis.acquire.dockable.components.actions.AbstractAcquireActionPanel;
import org.weasis.acquire.explorer.AcquireImageInfo;
import org.weasis.acquire.explorer.AcquireImageValues;
import org.weasis.base.viewer2d.EventManager;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.ui.editor.image.dockable.MeasureTool;

public class AnnotatePanel extends AbstractAcquireActionPanel {

  public AnnotatePanel() {
    setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
    MeasureTool.buildIconPanel(
        this, EventManager.getInstance(), ActionW.MEASURE, ActionW.DRAW_MEASURE, 8);
    MeasureTool.buildIconPanel(
        this, EventManager.getInstance(), ActionW.DRAW, ActionW.DRAW_GRAPHICS, 8);

    add(new AnnotationOptionsPanel());
    add(GuiUtils.boxYLastElement(5));
  }

  @Override
  public void initValues(AcquireImageInfo info, AcquireImageValues values) {
    // Nothing to do
  }
}
