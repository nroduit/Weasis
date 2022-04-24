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

import javax.swing.Icon;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JPopupMenu;
import org.weasis.core.api.gui.util.ActionState;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.gui.util.ComboItemListener;
import org.weasis.core.api.gui.util.GroupPopup;
import org.weasis.core.api.gui.util.GroupRadioMenu;
import org.weasis.core.api.gui.util.RadioMenuItem;
import org.weasis.core.api.gui.util.ToggleButtonListener;
import org.weasis.core.api.util.ResourceUtil;
import org.weasis.core.api.util.ResourceUtil.ActionIcon;
import org.weasis.core.api.util.ResourceUtil.OtherIcon;
import org.weasis.core.ui.editor.image.ShowPopup;
import org.weasis.core.ui.editor.image.ViewButton;
import org.weasis.core.ui.util.TitleMenuItem;
import org.weasis.dicom.viewer2d.KOComponentFactory.KOViewButton.eState;

/**
 * @author benoit jacquemoud
 * @version $Rev$ $Date$
 */
public final class KOComponentFactory {

  private KOComponentFactory() {}

  public static ViewButton buildKoSelectionButton(final View2d view2d) {

    return new ViewButton(
        (invoker, x, y) -> {
          final EventManager evtMgr = EventManager.getInstance();
          ComboItemListener<?> koSelectionAction =
              ((ComboItemListener<?>) evtMgr.getAction(ActionW.KO_SELECTION));
          JPopupMenu popupMenu = new JPopupMenu();

          popupMenu.add(new TitleMenuItem(ActionW.KO_SELECTION.getTitle()));
          popupMenu.addSeparator();

          GroupPopup groupRadioMenu = koSelectionAction.createUnregisteredGroupRadioMenu();
          if (groupRadioMenu instanceof GroupRadioMenu) {
            for (RadioMenuItem item :
                ((GroupRadioMenu<?>) groupRadioMenu).getRadioMenuItemListCopy()) {
              popupMenu.add(item);
            }
          }
          popupMenu.addSeparator();

          ToggleButtonListener koFilterAction =
              (ToggleButtonListener) evtMgr.getAction(ActionW.KO_FILTER);
          final JCheckBoxMenuItem menuItem =
              koFilterAction.createUnregisteredJCCheckBoxMenuItem(
                  ActionW.KO_FILTER.getTitle(), ResourceUtil.getIcon(ActionIcon.SYNCH_STAR));

          popupMenu.add(menuItem);
          popupMenu.setEnabled(koSelectionAction.isActionEnabled());
          popupMenu.show(invoker, x, y);
        },
        ResourceUtil.getIcon(OtherIcon.KEY_IMAGE).derive(24, 24),
        ActionW.KO_SELECTION.getTitle());
  }

  public static KOViewButton buildKoStarButton(final View2d view2d) {

    return new KOViewButton(
        (invoker, x, y) -> {
          EventManager evtMgr = EventManager.getInstance();
          boolean currentSelectedState = view2d.koStarButton.state.equals(eState.SELECTED);

          if (evtMgr.getSelectedViewPane() == view2d) {
            ActionState koToggleAction =
                view2d.getEventManager().getAction(ActionW.KO_TOGGLE_STATE);
            if (koToggleAction instanceof ToggleButtonListener buttonListener) {
              // if (((ToggleButtonListener) koToggleAction).isSelected() != currentSelectedState)
              // {
              // // When action and view are not synchronized, adapt the state of the action.
              // ((ToggleButtonListener) koToggleAction)
              // .setSelectedWithoutTriggerAction(currentSelectedState);
              // }
              buttonListener.setSelected(!currentSelectedState);
            }
          }
        });
  }

  public static class KOViewButton extends ViewButton {

    protected eState state = eState.UNSELECTED;

    enum eState {
      UNSELECTED,
      EXIST,
      SELECTED
    }

    public KOViewButton(ShowPopup popup) {
      super(popup, KeyObjectToolBar.KO_STAR_ICON.derive(24, 24), "star"); // NON-NLS
    }

    public eState getState() {
      return state;
    }

    public void setState(eState state) {
      this.state = state;
    }

    @Override
    public Icon getIcon() {
      return switch (state) {
        case UNSELECTED -> KeyObjectToolBar.KO_STAR_ICON;
        case EXIST -> KeyObjectToolBar.KO_STAR_ICON_EXIST;
        case SELECTED -> KeyObjectToolBar.KO_STAR_ICON_SELECTED;
      };
    }
  }
}
