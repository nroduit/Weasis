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

import java.util.Optional;
import javax.swing.JButton;
import javax.swing.JPopupMenu;
import javax.swing.JToggleButton;
import javax.swing.SwingUtilities;
import org.weasis.core.api.gui.util.ComboItemListener;
import org.weasis.core.api.gui.util.DropButtonIcon;
import org.weasis.core.api.gui.util.DropDownButton;
import org.weasis.core.api.gui.util.GroupPopup;
import org.weasis.core.api.util.ResourceUtil;
import org.weasis.core.api.util.ResourceUtil.ActionIcon;
import org.weasis.core.ui.editor.image.ImageViewerPlugin;
import org.weasis.core.ui.pref.PreferenceDialog;
import org.weasis.core.ui.util.ColorLayerUI;
import org.weasis.core.ui.util.WtoolBar;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.viewer3d.vr.CrosshairCutMode;

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

    GroupPopup menuCut = null;
    Optional<ComboItemListener<CrosshairCutMode>> cutAction =
        eventManager.getAction(ActionVol.CROSSHAIR_CUT_MODE);
    if (cutAction.isPresent()) {
      menuCut = cutAction.get().createGroupRadioMenu();
    }

    final DropDownButton cutButton =
        new DropDownButton(
            ActionVol.CROSSHAIR_CUT_MODE.cmd(),
            DropButtonIcon.createDropButtonIcon(ResourceUtil.getToolBarIcon(ActionIcon.VOLUME_CUT)),
            menuCut) {
          @Override
          protected JPopupMenu getPopupMenu() {
            JPopupMenu menu =
                (getMenuModel() == null) ? new JPopupMenu() : getMenuModel().createJPopupMenu();
            menu.setInvoker(this);
            return menu;
          }
        };
    cutButton.setToolTipText(ActionVol.CROSSHAIR_CUT_MODE.getTitle());
    add(cutButton);

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
