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
import javax.swing.JToggleButton;
import javax.swing.SwingUtilities;
import org.weasis.core.api.util.ResourceUtil;
import org.weasis.core.api.util.ResourceUtil.ActionIcon;
import org.weasis.core.ui.editor.image.ImageViewerPlugin;
import org.weasis.core.ui.pref.PreferenceDialog;
import org.weasis.core.ui.util.ColorLayerUI;
import org.weasis.core.ui.util.WtoolBar;
import org.weasis.dicom.codec.DicomImageElement;

public class View3DToolbar extends WtoolBar {
  private EventManager eventManager;

  public View3DToolbar(int position) {
    super(Messages.getString("3d.viewer.bar"), position);
    this.eventManager = EventManager.getInstance();

    initGui();
  }

  private void initGui() {
    JButton refreshBt = new JButton(ResourceUtil.getToolBarIcon(ActionIcon.LOAD_VOLUME));
    refreshBt.setToolTipText(Messages.getString("rebuild.volume"));
    refreshBt.addActionListener(
        e -> {
          ImageViewerPlugin<DicomImageElement> container =
              eventManager.getSelectedView2dContainer();
          if (container instanceof View3DContainer view3DContainer) {
            view3DContainer.reload();
          }
        });

    add(refreshBt);

    eventManager
        .getAction(ActionVol.VOL_PROJECTION)
        .ifPresent(
            b -> {
              JToggleButton toggleButton = new JToggleButton();
              toggleButton.setToolTipText(ActionVol.VOL_PROJECTION.getTitle());
              toggleButton.setIcon(ResourceUtil.getToolBarIcon(ActionIcon.ORTHOGRAPHIC));
              b.registerActionState(toggleButton);
              add(toggleButton);
            });

    eventManager
        .getAction(ActionVol.VOL_SLICING)
        .ifPresent(
            b -> {
              JToggleButton toggleButton = new JToggleButton();
              toggleButton.setToolTipText(ActionVol.VOL_SLICING.getTitle());
              toggleButton.setIcon(ResourceUtil.getToolBarIcon(ActionIcon.VOLUME_SLICING));
              b.registerActionState(toggleButton);
              add(toggleButton);
            });

    JButton config = new JButton(ResourceUtil.getToolBarIcon(ActionIcon.VOLUME_SETTINGS));
    config.setToolTipText(Messages.getString("3d.settings"));
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
