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
import java.awt.Toolkit;
import java.awt.image.FilteredImageSource;
import java.awt.image.ImageFilter;
import java.awt.image.ImageProducer;
import java.awt.image.RGBImageFilter;

import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JPopupMenu;

import org.weasis.core.api.gui.util.ActionState;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.gui.util.ComboItemListener;
import org.weasis.core.api.gui.util.GroupPopup;
import org.weasis.core.api.gui.util.GroupRadioMenu;
import org.weasis.core.api.gui.util.RadioMenuItem;
import org.weasis.core.api.gui.util.ToggleButtonListener;
import org.weasis.core.ui.editor.image.ShowPopup;
import org.weasis.core.ui.editor.image.ViewButton;
import org.weasis.core.ui.util.TitleMenuItem;
import org.weasis.dicom.viewer2d.KOComponentFactory.KOViewButton.eState;

/**
 * @author benoit jacquemoud
 *
 * @version $Rev$ $Date$
 */
public final class KOComponentFactory {

    public static final ImageIcon KO_STAR_ICON = new ImageIcon(View2d.class.getResource("/icon/16x16/star_bw.png")); //$NON-NLS-1$
    public static final ImageIcon KO_STAR_ICON_SELECTED;
    public static final ImageIcon KO_STAR_ICON_EXIST;

    static {
        ImageFilter imageFilter = new SelectedImageFilter(new float[] { 1.0f, 0.78f, 0.0f }); // ORANGE
        ImageProducer imageProducer = new FilteredImageSource(KO_STAR_ICON.getImage().getSource(), imageFilter);
        KO_STAR_ICON_SELECTED = new ImageIcon(Toolkit.getDefaultToolkit().createImage(imageProducer));

        imageFilter = new SelectedImageFilter(new float[] { 0.0f, 0.39f, 1.0f }); // BLUE
        imageProducer = new FilteredImageSource(KO_STAR_ICON.getImage().getSource(), imageFilter);
        KO_STAR_ICON_EXIST = new ImageIcon(Toolkit.getDefaultToolkit().createImage(imageProducer));
    }

    // /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    private KOComponentFactory() {
    }

    // /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    public static ViewButton buildKoSelectionButton(final View2d view2d) {

        return new ViewButton(new ShowPopup() {

            @Override
            public void showPopup(Component invoker, int x, int y) {

                final EventManager evtMgr = EventManager.getInstance();
                ComboItemListener<?> koSelectionAction =
                    ((ComboItemListener<?>) evtMgr.getAction(ActionW.KO_SELECTION));
                JPopupMenu popupMenu = new JPopupMenu();

                popupMenu.add(new TitleMenuItem(ActionW.KO_SELECTION.getTitle(), popupMenu.getInsets()));
                popupMenu.addSeparator();

                GroupPopup groupRadioMenu = koSelectionAction.createUnregisteredGroupRadioMenu();
                if (groupRadioMenu instanceof GroupRadioMenu) {
                    for (RadioMenuItem item : ((GroupRadioMenu<?>) groupRadioMenu).getRadioMenuItemListCopy()) {
                        popupMenu.add(item);
                    }
                }
                popupMenu.addSeparator();

                ToggleButtonListener koFilterAction = (ToggleButtonListener) evtMgr.getAction(ActionW.KO_FILTER);
                final JCheckBoxMenuItem menuItem =
                    koFilterAction.createUnregiteredJCheckBoxMenuItem(ActionW.KO_FILTER.getTitle());

                popupMenu.add(menuItem);
                popupMenu.setEnabled(koSelectionAction.isActionEnabled());
                popupMenu.show(invoker, x, y);
            }
        }, View2d.KO_ICON);
    }

    // /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    public static KOViewButton buildKoStarButton(final View2d view2d) {

        return new KOViewButton(new ShowPopup() {
            @Override
            public void showPopup(Component invoker, int x, int y) {

                EventManager evtMgr = EventManager.getInstance();
                boolean currentSelectedState = view2d.koStarButton.state.equals(eState.SELECTED) ? true : false;

                if (evtMgr.getSelectedViewPane() == view2d) {
                    ActionState koToggleAction = view2d.getEventManager().getAction(ActionW.KO_TOOGLE_STATE);
                    if (koToggleAction instanceof ToggleButtonListener) {
                        // if (((ToggleButtonListener) koToggleAction).isSelected() != currentSelectedState) {
                        // // When action and view are not synchronized, adapt the state of the action.
                        // ((ToggleButtonListener) koToggleAction)
                        // .setSelectedWithoutTriggerAction(currentSelectedState);
                        // }
                        ((ToggleButtonListener) koToggleAction).setSelected(!currentSelectedState);
                    }
                }
            }
        });
    }

    // /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    public static class SelectedImageFilter extends RGBImageFilter {
        private final float[] filter;

        public SelectedImageFilter(float[] filter) {
            this.filter = filter;
            // Filter's operation doesn't depend on the pixel's location, so IndexColorModels can be filtered directly.
            canFilterIndexColorModel = true;
        }

        @Override
        public int filterRGB(int x, int y, int argb) {
            int r = (int) (((argb >> 16) & 0xff) * filter[0]);
            int g = (int) (((argb >> 8) & 0xff) * filter[1]);
            int b = (int) (((argb) & 0xff) * filter[2]);
            return (argb & 0xff000000) | (r << 16) | (g << 8) | (b);
        }
    }

    // /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    public static class KOViewButton extends ViewButton {

        protected eState state = eState.UNSELECTED;

        enum eState {
            UNSELECTED, EXIST, SELECTED
        }

        public KOViewButton(ShowPopup popup) {
            super(popup, KO_STAR_ICON);
        }

        public eState getState() {
            return state;
        }

        public void setState(eState state) {
            this.state = state;
        }

        @Override
        public Icon getIcon() {
            switch (state) {
                case UNSELECTED:
                    return KO_STAR_ICON;
                case EXIST:
                    return KO_STAR_ICON_EXIST;
                case SELECTED:
                    return KO_STAR_ICON_SELECTED;
            }
            return null;
        }
    }

}
