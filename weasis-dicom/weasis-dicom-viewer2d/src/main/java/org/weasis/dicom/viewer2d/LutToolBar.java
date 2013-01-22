package org.weasis.dicom.viewer2d;

import java.awt.Component;
import java.awt.Graphics;

import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JPopupMenu;
import javax.swing.JToggleButton;

import org.weasis.core.api.gui.util.ActionState;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.gui.util.ComboItemListener;
import org.weasis.core.api.gui.util.DropButtonIcon;
import org.weasis.core.api.gui.util.DropDownButton;
import org.weasis.core.api.gui.util.GroupRadioMenu;
import org.weasis.core.api.gui.util.ToggleButtonListener;
import org.weasis.core.ui.util.WtoolBar;

public class LutToolBar<DicomImageElement> extends WtoolBar {

    public LutToolBar() {
        super("Lookup Table Toolbar", TYPE.tool);

        GroupRadioMenu menu = null;
        ActionState presetAction = EventManager.getInstance().getAction(ActionW.PRESET);
        if (presetAction instanceof ComboItemListener) {
            menu = ((ComboItemListener) presetAction).createGroupRadioMenu();
        }

        final DropDownButton presetButton = new DropDownButton(ActionW.WINLEVEL.cmd(), buildWLIcon(), menu) { //$NON-NLS-1$
                @Override
                protected JPopupMenu getPopupMenu() {
                    JPopupMenu menu = (getMenuModel() == null) ? new JPopupMenu() : getMenuModel().createJPopupMenu();
                    menu.setInvoker(this);
                    return menu;
                }
            };

        presetButton.setToolTipText("Presets");
        add(presetButton);

        GroupRadioMenu menuLut = null;
        ActionState lutAction = EventManager.getInstance().getAction(ActionW.LUT);
        if (lutAction instanceof ComboItemListener) {
            menuLut = ((ComboItemListener) lutAction).createGroupRadioMenu();
        }

        final DropDownButton lutButton = new DropDownButton(ActionW.LUT.cmd(), buildLutIcon(), menuLut) { //$NON-NLS-1$
                @Override
                protected JPopupMenu getPopupMenu() {
                    JPopupMenu menu = (getMenuModel() == null) ? new JPopupMenu() : getMenuModel().createJPopupMenu();
                    menu.setInvoker(this);
                    return menu;
                }
            };

        lutButton.setToolTipText("LUT selection");
        add(lutButton);

        final JToggleButton invertButton = new JToggleButton();
        invertButton.setToolTipText("Invert Grayscale");
        invertButton.setIcon(new ImageIcon(WtoolBar.class.getResource("/icon/32x32/invert.png"))); //$NON-NLS-1$
        ActionState invlutAction = EventManager.getInstance().getAction(ActionW.INVERSELUT);
        if (invlutAction instanceof ToggleButtonListener) {
            ((ToggleButtonListener) invlutAction).registerComponent(invertButton);
        }
        add(invertButton);
    }

    private Icon buildLutIcon() {
        final Icon mouseIcon = new ImageIcon(WtoolBar.class.getResource("/icon/32x32/winLevel.png")); //$NON-NLS-1$

        return new DropButtonIcon(new Icon() {

            @Override
            public void paintIcon(Component c, Graphics g, int x, int y) {
                mouseIcon.paintIcon(c, g, x, y);
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
                mouseIcon.paintIcon(c, g, x, y);
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
