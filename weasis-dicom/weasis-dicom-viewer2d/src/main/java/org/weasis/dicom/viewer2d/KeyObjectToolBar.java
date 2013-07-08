package org.weasis.dicom.viewer2d;

import java.awt.Component;
import java.awt.Graphics;
import java.awt.Toolkit;
import java.awt.image.FilteredImageSource;
import java.awt.image.ImageFilter;
import java.awt.image.ImageProducer;

import javax.swing.AbstractButton;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JPopupMenu;
import javax.swing.JToggleButton;
import javax.swing.UIManager;

import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.gui.util.ComboItemListener;
import org.weasis.core.api.gui.util.DropButtonIcon;
import org.weasis.core.api.gui.util.DropDownButton;
import org.weasis.core.api.gui.util.GroupRadioMenu;
import org.weasis.core.api.gui.util.ToggleButtonListener;
import org.weasis.core.ui.util.WtoolBar;
import org.weasis.dicom.viewer2d.KOManager.SelectedImageFilter;

@SuppressWarnings("serial")
public class KeyObjectToolBar<DicomImageElement> extends WtoolBar {

    public static final ImageIcon KO_STAR_ICON = new ImageIcon(View2d.class.getResource("/icon/24x24/star_bw.png"));
    public static final ImageIcon KO_STAR_ICON_SELECTED;
    public static final ImageIcon KO_FILTER_ICON = new ImageIcon(View2d.class.getResource("/icon/24x24/synch-KO.png"));
    public static final ImageIcon KO_FILTER_ICON_SELECTED;

    static {
        ImageFilter imageFilter = new SelectedImageFilter(new float[] { 1.0f, 0.78f, 0.0f }); // ORANGE

        ImageProducer imageProducer = new FilteredImageSource(KO_STAR_ICON.getImage().getSource(), imageFilter);
        KO_STAR_ICON_SELECTED = new ImageIcon(Toolkit.getDefaultToolkit().createImage(imageProducer));

        imageProducer = new FilteredImageSource(KO_FILTER_ICON.getImage().getSource(), imageFilter);
        KO_FILTER_ICON_SELECTED = new ImageIcon(Toolkit.getDefaultToolkit().createImage(imageProducer));
    }

    public KeyObjectToolBar(int index) {
        super("KeyObject Tools", index);

        final EventManager evtMgr = EventManager.getInstance();

        // --------------------------------------------------------------------------------------------------
        ToggleButtonListener koToggleAction = (ToggleButtonListener) evtMgr.getAction(ActionW.KO_STATE);
        final JToggleButton toggleKOSelectionBtn = new JToggleButton();

        toggleKOSelectionBtn.setToolTipText("Toggle KO State");
        toggleKOSelectionBtn.setIcon(KO_STAR_ICON);
        toggleKOSelectionBtn.setSelectedIcon(KO_STAR_ICON_SELECTED);

        koToggleAction.registerActionState(toggleKOSelectionBtn);
        add(toggleKOSelectionBtn);

        // --------------------------------------------------------------------------------------------------
        ToggleButtonListener koFilterAction = (ToggleButtonListener) evtMgr.getAction(ActionW.KO_FILTER);
        final JToggleButton koFilterBtn = new JToggleButton();

        koFilterBtn.setToolTipText("Filter selected KO only");
        koFilterBtn.setIcon(KO_FILTER_ICON);
        koFilterBtn.setSelectedIcon(KO_FILTER_ICON_SELECTED);

        koFilterAction.registerActionState(koFilterBtn);
        add(koFilterBtn);

        // --------------------------------------------------------------------------------------------------
        ComboItemListener koSelectionAction = (ComboItemListener) evtMgr.getAction(ActionW.KO_SELECTION);
        GroupRadioMenu koSelectionMenu = koSelectionAction.createGroupRadioMenu();

        @SuppressWarnings("serial")
        final DropDownButton koSelectionButton =
            new DropDownButton(ActionW.KO_SELECTION.cmd(), buildKoSelectionIcon(), koSelectionMenu) {
                @Override
                protected JPopupMenu getPopupMenu() {
                    JPopupMenu menu = (getMenuModel() == null) ? new JPopupMenu() : getMenuModel().createJPopupMenu();
                    menu.setInvoker(this);
                    return menu;
                }
            };

        koSelectionButton.setToolTipText("Change KO Selection");
        koSelectionAction.registerActionState(koSelectionButton);
        add(koSelectionButton);
    }

    private Icon buildKoSelectionIcon() {
        final Icon mouseIcon = new ImageIcon(View2d.class.getResource("/icon/24x24/dcm-KO.png"));

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
