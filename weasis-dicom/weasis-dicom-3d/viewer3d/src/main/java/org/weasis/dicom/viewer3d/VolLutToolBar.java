/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.viewer3d;

import java.util.Objects;
import java.util.Optional;
import javax.swing.Icon;
import javax.swing.JPopupMenu;
import javax.swing.JToggleButton;
import javax.swing.KeyStroke;
import org.dcm4che3.img.lut.PresetWindowLevel;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.gui.util.ComboItemListener;
import org.weasis.core.api.gui.util.DropButtonIcon;
import org.weasis.core.api.gui.util.DropDownButton;
import org.weasis.core.api.gui.util.GroupPopup;
import org.weasis.core.api.gui.util.GroupRadioMenu;
import org.weasis.core.api.gui.util.RadioMenuItem;
import org.weasis.core.api.util.ResourceUtil;
import org.weasis.core.api.util.ResourceUtil.ActionIcon;
import org.weasis.core.ui.editor.image.ImageViewerEventManager;
import org.weasis.core.ui.util.WtoolBar;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.codec.display.Modality;
import org.weasis.dicom.viewer3d.vr.Preset;
import org.weasis.dicom.viewer3d.vr.PresetRadioMenu;
import org.weasis.dicom.viewer3d.vr.View3d;

public class VolLutToolBar extends WtoolBar {

  public VolLutToolBar(final ImageViewerEventManager<DicomImageElement> eventManager, int index) {
    super(Messages.getString("volume.lut.bar"), index);
    GroupPopup menuPreset = null;
    Optional<ComboItemListener<Object>> presetAction =
        Objects.requireNonNull(eventManager).getAction(ActionW.PRESET);
    if (presetAction.isPresent()) {
      menuPreset = presetAction.get().createGroupRadioMenu();
    }

    final DropDownButton presetButton = getDropDownButton(menuPreset);
    add(presetButton);
    presetAction.ifPresent(
        objectComboItemListener -> objectComboItemListener.registerActionState(presetButton));

    GroupPopup menuLut = null;
    Optional<ComboItemListener<Preset>> lutAction = eventManager.getAction(ActionVol.VOL_PRESET);
    if (lutAction.isPresent()) {
      ComboItemListener<Preset> action = lutAction.get();
      PresetRadioMenu radioMenu = new PresetRadioMenu();
      radioMenu.setModel(action.getModel());
      action.registerActionState(radioMenu);
      menuLut = radioMenu;
    }

    final DropDownButton lutButton =
        new DropDownButton(ActionVol.VOL_PRESET.cmd(), buildLutIcon(), menuLut) {
          @Override
          protected JPopupMenu getPopupMenu() {
            Modality curModality = Modality.DEFAULT;
            if (eventManager.getSelectedViewPane() instanceof View3d view3d) {
              curModality = view3d.getVolTexture().getModality();
            }

            JPopupMenu menu;
            if (getMenuModel() instanceof PresetRadioMenu groupMenu) {
              menu = groupMenu.createJPopupMenu(curModality);
            } else {
              menu = new JPopupMenu();
            }
            menu.setInvoker(this);
            return menu;
          }
        };

    lutButton.setToolTipText(Messages.getString("volume.lut.selection"));
    add(lutButton);
    lutAction.ifPresent(c -> c.registerActionState(lutButton));

    final JToggleButton invertButton = new JToggleButton();
    invertButton.setToolTipText(ActionW.INVERT_LUT.getTitle());
    invertButton.setIcon(ResourceUtil.getToolBarIcon(ActionIcon.INVERSE_LUT));
    eventManager.getAction(ActionW.INVERT_LUT).ifPresent(c -> c.registerActionState(invertButton));
    add(invertButton);
  }

  private DropDownButton getDropDownButton(GroupPopup menuPreset) {
    final DropDownButton presetButton =
        new DropDownButton(ActionW.WINLEVEL.cmd(), buildWLIcon(), menuPreset) {
          @Override
          protected JPopupMenu getPopupMenu() {
            JPopupMenu menu =
                (getMenuModel() == null) ? new JPopupMenu() : getMenuModel().createJPopupMenu();
            menu.setInvoker(this);
            if (getMenuModel() instanceof GroupRadioMenu) {
              for (RadioMenuItem item :
                  ((GroupRadioMenu<?>) getMenuModel()).getRadioMenuItemListCopy()) {
                PresetWindowLevel preset = (PresetWindowLevel) item.getUserObject();
                if (preset.getKeyCode() > 0) {
                  item.setAccelerator(KeyStroke.getKeyStroke(preset.getKeyCode(), 0));
                }
              }
            }
            return menu;
          }
        };

    presetButton.setToolTipText(ActionW.PRESET.getTitle());
    return presetButton;
  }

  private Icon buildLutIcon() {
    return DropButtonIcon.createDropButtonIcon(ResourceUtil.getToolBarIcon(ActionIcon.LUT));
  }

  private Icon buildWLIcon() {
    return DropButtonIcon.createDropButtonIcon(
        ResourceUtil.getToolBarIcon(ActionIcon.WINDOW_LEVEL));
  }
}
