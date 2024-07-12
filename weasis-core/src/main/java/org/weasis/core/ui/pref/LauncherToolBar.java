/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.pref;

import java.util.List;
import javax.swing.Icon;
import javax.swing.JButton;
import org.weasis.core.Messages;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.util.ResourceUtil;
import org.weasis.core.ui.editor.image.ImageViewerEventManager;
import org.weasis.core.ui.launcher.Launcher;
import org.weasis.core.ui.util.DefaultAction;
import org.weasis.core.ui.util.DynamicToolbar;
import org.weasis.core.ui.util.WtoolBar;
import org.weasis.core.util.StringUtil;

public class LauncherToolBar extends WtoolBar implements DynamicToolbar {

  private final ImageViewerEventManager<?> eventManager;

  public LauncherToolBar(ImageViewerEventManager<?> eventManager, int index) {
    super(Messages.getString("launcher"), index);
    this.eventManager = eventManager;
    updateLaunchers(eventManager);
  }

  public void updateLaunchers(ImageViewerEventManager<?> eventManager) {
    removeAll();
    addLaunchers(GuiUtils.getUICore().getDicomLaunchers(), eventManager);
    addLaunchers(GuiUtils.getUICore().getOtherLaunchers(), eventManager);
  }

  private void addLaunchers(List<Launcher> launchers, ImageViewerEventManager<?> eventManager) {
    int size = ResourceUtil.TOOLBAR_ICON_SIZE;
    for (Launcher launcher : launchers) {
      if (launcher.isEnable() && launcher.isButton() && launcher.getConfiguration().isValid()) {
        Icon icon = launcher.getResizeIcon(size, size);
        DefaultAction action = new DefaultAction(null, icon, _ -> launcher.execute(eventManager));
        final JButton launcherButton = new JButton(action);
        launcherButton.setToolTipText(
            Launcher.Type.DICOM + StringUtil.COLON_AND_SPACE + launcher.getName());
        add(launcherButton);
      }
    }
  }

  @Override
  public void updateToolbar() {
    updateLaunchers(eventManager);
  }
}
