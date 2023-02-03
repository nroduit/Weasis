/*
 * Copyright (c) 2012 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.viewer3d;

import javax.swing.JButton;
import javax.swing.SwingUtilities;
import org.weasis.core.api.util.ResourceUtil;
import org.weasis.core.api.util.ResourceUtil.ActionIcon;
import org.weasis.core.ui.editor.image.ImageViewerEventManager;
import org.weasis.core.ui.editor.image.ImageViewerPlugin;
import org.weasis.core.ui.pref.PreferenceDialog;
import org.weasis.core.ui.util.ColorLayerUI;
import org.weasis.core.ui.util.WtoolBar;
import org.weasis.core.util.StringUtil;

public class View3DToolbar extends WtoolBar {

  public static final String NAME = View3DFactory.NAME + StringUtil.SPACE + "Bar";
  private ImageViewerEventManager eventManager;

  public View3DToolbar(ImageViewerEventManager eventManager, int position) {
    super(NAME, position);
    this.eventManager = eventManager;

    initGui();
  }

  private void initGui() {
    JButton refreshBt = new JButton(ResourceUtil.getToolBarIcon(ActionIcon.LOAD_VOLUME));
    refreshBt.setToolTipText("Rebuild the volume");
    refreshBt.addActionListener(
        e -> {
          ImageViewerPlugin container = EventManager.getInstance().getSelectedView2dContainer();
          if (container instanceof View3DContainer) {
            ((View3DContainer) container).reload();
          }
        });

    add(refreshBt);

    JButton config = new JButton(ResourceUtil.getToolBarIcon(ActionIcon.SETTINGS));
    config.setToolTipText("3D Settings");
    config.addActionListener(
        e -> {
          ColorLayerUI layer = ColorLayerUI.createTransparentLayerUI(View3DToolbar.this);
          PreferenceDialog dialog =
              new PreferenceDialog(SwingUtilities.getWindowAncestor(View3DToolbar.this));
          dialog.showPage(View3DFactory.NAME);
          ColorLayerUI.showCenterScreen(dialog, layer);
        });
    add(config);
  }
}
