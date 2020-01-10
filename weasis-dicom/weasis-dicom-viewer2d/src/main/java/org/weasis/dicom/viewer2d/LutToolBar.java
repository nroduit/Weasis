/*******************************************************************************
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.weasis.dicom.viewer2d;

import java.awt.Component;
import java.awt.Graphics;

import javax.swing.AbstractButton;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JPopupMenu;
import javax.swing.JToggleButton;
import javax.swing.UIManager;

import org.weasis.core.api.gui.util.ActionState;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.gui.util.ComboItemListener;
import org.weasis.core.api.gui.util.DropButtonIcon;
import org.weasis.core.api.gui.util.DropDownButton;
import org.weasis.core.api.gui.util.GroupPopup;
import org.weasis.core.api.gui.util.ToggleButtonListener;
import org.weasis.core.ui.editor.image.ImageViewerEventManager;
import org.weasis.core.ui.util.WtoolBar;
import org.weasis.dicom.codec.DicomImageElement;

public class LutToolBar extends WtoolBar {

    public LutToolBar(final ImageViewerEventManager<DicomImageElement> eventManager, int index) {
        super(Messages.getString("LutToolBar.lookupbar"), index); //$NON-NLS-1$
        if (eventManager == null) {
            throw new IllegalArgumentException("EventManager cannot be null"); //$NON-NLS-1$
        }

        GroupPopup menu = null;
        ActionState presetAction = eventManager.getAction(ActionW.PRESET);
        if (presetAction instanceof ComboItemListener) {
            menu = ((ComboItemListener) presetAction).createGroupRadioMenu();
        }

        final DropDownButton presetButton = new DropDownButton(ActionW.WINLEVEL.cmd(), buildWLIcon(), menu) {
            @Override
            protected JPopupMenu getPopupMenu() {
                JPopupMenu menu = (getMenuModel() == null) ? new JPopupMenu() : getMenuModel().createJPopupMenu();
                menu.setInvoker(this);
                return menu;
            }
        };

        presetButton.setToolTipText(Messages.getString("LutToolBar.presets")); //$NON-NLS-1$
        add(presetButton);
        if (presetAction != null) {
            presetAction.registerActionState(presetButton);
        }

        GroupPopup menuLut = null;
        ActionState lutAction = eventManager.getAction(ActionW.LUT);
        if (lutAction instanceof ComboItemListener) {
            menuLut = ((ComboItemListener) lutAction).createGroupRadioMenu();
        }

        final DropDownButton lutButton = new DropDownButton(ActionW.LUT.cmd(), buildLutIcon(), menuLut) {
            @Override
            protected JPopupMenu getPopupMenu() {
                JPopupMenu menu = (getMenuModel() == null) ? new JPopupMenu() : getMenuModel().createJPopupMenu();
                menu.setInvoker(this);
                return menu;
            }
        };

        lutButton.setToolTipText(Messages.getString("LutToolBar.lustSelection")); //$NON-NLS-1$
        add(lutButton);
        if (lutAction != null) {
            lutAction.registerActionState(lutButton);
        }

        final JToggleButton invertButton = new JToggleButton();
        invertButton.setToolTipText(ActionW.INVERT_LUT.getTitle());
        invertButton.setIcon(new ImageIcon(WtoolBar.class.getResource("/icon/32x32/invert.png"))); //$NON-NLS-1$
        ActionState invlutAction = eventManager.getAction(ActionW.INVERT_LUT);
        if (invlutAction instanceof ToggleButtonListener) {
            ((ToggleButtonListener) invlutAction).registerActionState(invertButton);
        }
        add(invertButton);
    }

    private Icon buildLutIcon() {
        final Icon mouseIcon = new ImageIcon(WtoolBar.class.getResource("/icon/32x32/lut.png")); //$NON-NLS-1$

        return new DropButtonIcon(new Icon() {

            @Override
            public void paintIcon(Component c, Graphics g, int x, int y) {
                if (c instanceof AbstractButton) {
                    AbstractButton model = (AbstractButton) c;
                    Icon icon = null;
                    if (!model.isEnabled()) {
                        icon = UIManager.getLookAndFeel().getDisabledIcon(model, mouseIcon);
                    }
                    if (icon == null) {
                        icon = mouseIcon;
                    }
                    icon.paintIcon(c, g, x, y);
                }
            }

            @Override
            public int getIconWidth() {
                return mouseIcon.getIconWidth();
            }

            @Override
            public int getIconHeight() {
                return mouseIcon.getIconHeight();
            }
        });
    }

    private Icon buildWLIcon() {
        final Icon mouseIcon = new ImageIcon(WtoolBar.class.getResource("/icon/32x32/winLevel.png")); //$NON-NLS-1$

        return new DropButtonIcon(new Icon() {

            @Override
            public void paintIcon(Component c, Graphics g, int x, int y) {
                if (c instanceof AbstractButton) {
                    AbstractButton model = (AbstractButton) c;
                    Icon icon = null;
                    if (!model.isEnabled()) {
                        icon = UIManager.getLookAndFeel().getDisabledIcon(model, mouseIcon);
                    }
                    if (icon == null) {
                        icon = mouseIcon;
                    }
                    icon.paintIcon(c, g, x, y);
                }
            }

            @Override
            public int getIconWidth() {
                return mouseIcon.getIconWidth();
            }

            @Override
            public int getIconHeight() {
                return mouseIcon.getIconHeight();
            }
        });
    }

}
