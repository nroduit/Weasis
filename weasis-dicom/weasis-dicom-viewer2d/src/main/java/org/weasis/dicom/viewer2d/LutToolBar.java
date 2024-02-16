/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.viewer2d;

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
import org.weasis.opencv.op.lut.ByteLut;

public class LutToolBar extends WtoolBar {

  public LutToolBar(final ImageViewerEventManager<DicomImageElement> eventManager, int index) {
    super(Messages.getString("LutToolBar.lookupbar"), index);

    GroupPopup menuPreset = null;
    Optional<ComboItemListener<Object>> presetAction =
        Objects.requireNonNull(eventManager).getAction(ActionW.PRESET);
    if (presetAction.isPresent()) {
      menuPreset = presetAction.get().createGroupRadioMenu();
    }

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
    add(presetButton);
    presetAction.ifPresent(
        objectComboItemListener -> objectComboItemListener.registerActionState(presetButton));

    GroupPopup menuLut = null;
    Optional<ComboItemListener<ByteLut>> lutAction = eventManager.getAction(ActionW.LUT);
    if (lutAction.isPresent()) {
      menuLut = lutAction.get().createGroupRadioMenu();
    }

    final DropDownButton lutButton =
        new DropDownButton(ActionW.LUT.cmd(), buildLutIcon(), menuLut) {
          @Override
          protected JPopupMenu getPopupMenu() {
            JPopupMenu menu =
                (getMenuModel() == null) ? new JPopupMenu() : getMenuModel().createJPopupMenu();
            menu.setInvoker(this);
            return menu;
          }
        };

    lutButton.setToolTipText(Messages.getString("LutToolBar.lustSelection"));
    add(lutButton);
    lutAction.ifPresent(c -> c.registerActionState(lutButton));

    final JToggleButton invertButton = new JToggleButton();
    invertButton.setToolTipText(ActionW.INVERT_LUT.getTitle());
    invertButton.setIcon(ResourceUtil.getToolBarIcon(ActionIcon.INVERSE_LUT));
    eventManager.getAction(ActionW.INVERT_LUT).ifPresent(c -> c.registerActionState(invertButton));
    add(invertButton);
  }

  private Icon buildLutIcon() {
    return DropButtonIcon.createDropButtonIcon(ResourceUtil.getToolBarIcon(ActionIcon.LUT));
  }

  private Icon buildWLIcon() {
    return DropButtonIcon.createDropButtonIcon(
        ResourceUtil.getToolBarIcon(ActionIcon.WINDOW_LEVEL));
  }
}
