package org.weasis.dicom.viewer2d;

import java.awt.Component;
import java.awt.Graphics;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JButton;
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

public class More2DToolBar<DicomImageElement> extends WtoolBar {

    public More2DToolBar() {
        super("More 2D Tools", TYPE.tool);

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

        final JToggleButton invertButton = new JToggleButton();
        invertButton.setToolTipText("Invert Grayscale");
        // TODO change ICON
        invertButton.setIcon(new ImageIcon(WtoolBar.class.getResource("/icon/32x32/invert.png"))); //$NON-NLS-1$
        ActionState invlutAction = EventManager.getInstance().getAction(ActionW.INVERSELUT);
        if (invlutAction instanceof ToggleButtonListener) {
            ((ToggleButtonListener) invlutAction).registerComponent(invertButton);
        }
        add(invertButton);

        final JButton resetButton = new JButton();
        resetButton.setToolTipText("Display Reset");
        // TODO change ICON
        resetButton.setIcon(new ImageIcon(WtoolBar.class.getResource("/icon/32x32/reset.png"))); //$NON-NLS-1$
        resetButton.addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent e) {
                EventManager.getInstance().reset(ResetTools.All);
            }
        });
        add(resetButton);

    }

    private Icon buildWLIcon() {
        // TODO change ICON
        final Icon mouseIcon = new ImageIcon(WtoolBar.class.getResource("/icon/22x22/winLevel.png")); //$NON-NLS-1$

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
