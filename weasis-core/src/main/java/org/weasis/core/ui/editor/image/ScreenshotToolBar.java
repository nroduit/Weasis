/*
 * Copyright (c) 2009-2018 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.editor.image;

import javax.swing.JButton;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.api.util.ResourceUtil;
import org.weasis.core.api.util.ResourceUtil.ActionIcon;
import org.weasis.core.ui.Messages;
import org.weasis.core.ui.util.WtoolBar;

public class ScreenshotToolBar<I extends ImageElement> extends WtoolBar {

  public ScreenshotToolBar(final ImageViewerEventManager<I> eventManager, int index) {
    super(Messages.getString("screenshot.bar"), index);
    if (eventManager == null) {
      throw new IllegalArgumentException("EventManager cannot be null");
    }

    final JButton metaButton = new JButton(ResourceUtil.getToolBarIcon(ActionIcon.EXPORT_IMAGE));
    metaButton.setToolTipText(Messages.getString("capture.the.selected.view"));
    metaButton.addActionListener(
        e -> ScreenshotDialog.showDialog(eventManager.getSelectedViewPane()));
    add(metaButton);

    eventManager.getAction(ActionW.EXPORT_VIEW).ifPresent(s -> s.registerActionState(metaButton));
  }
}
