/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.editor.image;

import com.formdev.flatlaf.extras.FlatSVGIcon;
import com.formdev.flatlaf.extras.FlatSVGIcon.ColorFilter;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import javax.swing.AbstractButton;
import javax.swing.ButtonGroup;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JMenu;
import javax.swing.JPopupMenu;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.KeyStroke;
import javax.swing.UIManager;
import javax.swing.event.ListDataEvent;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.gui.util.ComboItemListener;
import org.weasis.core.api.gui.util.DropButtonIcon;
import org.weasis.core.api.gui.util.DropDownButton;
import org.weasis.core.api.gui.util.Feature;
import org.weasis.core.api.gui.util.GroupPopup;
import org.weasis.core.api.gui.util.GroupRadioMenu;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.image.GridBagLayoutModel;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.api.service.WProperties;
import org.weasis.core.api.util.ResourceUtil;
import org.weasis.core.api.util.ResourceUtil.ActionIcon;
import org.weasis.core.ui.Messages;
import org.weasis.core.ui.util.WtoolBar;

public class ViewerToolBar<E extends ImageElement> extends WtoolBar implements ActionListener {

  public static final List<Feature<?>> actionsButtons =
      Collections.synchronizedList(
          new ArrayList<>(
              Arrays.asList(
                  ActionW.PAN,
                  ActionW.WINLEVEL,
                  ActionW.SCROLL_SERIES,
                  ActionW.ZOOM,
                  ActionW.ROTATION,
                  ActionW.MEASURE,
                  ActionW.DRAW,
                  ActionW.CONTEXTMENU,
                  ActionW.CROSSHAIR,
                  ActionW.NO_ACTION)));

  public static final Feature<?>[] actionsScroll = {
    ActionW.SCROLL_SERIES, ActionW.ZOOM, ActionW.ROTATION, ActionW.NO_ACTION
  };
  public static final FlatSVGIcon MouseLeftIcon =
      ResourceUtil.getToolBarIcon(ActionIcon.MOUSE_LEFT);
  public static final FlatSVGIcon MouseRightIcon =
      ResourceUtil.getToolBarIcon(ActionIcon.MOUSE_RIGHT);
  public static final FlatSVGIcon MouseMiddleIcon =
      ResourceUtil.getToolBarIcon(ActionIcon.MOUSE_MIDDLE);
  public static final FlatSVGIcon MouseWheelIcon =
      ResourceUtil.getToolBarIcon(ActionIcon.MOUSE_WHEEL);

  protected final ImageViewerEventManager<E> eventManager;
  private final DropDownButton mouseLeft;
  private final DropDownButton mouseMiddle;
  private final DropDownButton mouseRight;
  private final DropDownButton mouseWheel;
  private final DropDownButton synchButton;

  public ViewerToolBar(
      final ImageViewerEventManager<E> eventManager,
      int activeMouse,
      WProperties props,
      int index) {
    super(Messages.getString("ViewerToolBar.title"), index);
    if (eventManager == null) {
      throw new IllegalArgumentException("EventManager cannot be null");
    }
    this.eventManager = eventManager;

    MouseActions actions = eventManager.getMouseActions();

    if ((activeMouse & InputEvent.BUTTON1_DOWN_MASK) == InputEvent.BUTTON1_DOWN_MASK) {
      mouseLeft = buildMouseButton(actions, MouseActions.T_LEFT);
      mouseLeft.setToolTipText(
          Messages.getString("ViewerToolBar.change")
              + " "
              + Messages.getString("ViewerToolBar.m_action"));
      add(mouseLeft);
    } else {
      mouseLeft = null;
    }

    if ((activeMouse & InputEvent.BUTTON3_DOWN_MASK) == InputEvent.BUTTON3_DOWN_MASK) {
      mouseRight = buildMouseButton(actions, MouseActions.T_RIGHT);
      add(mouseRight);
    } else {
      mouseRight = null;
    }

    if ((activeMouse & MouseActions.SCROLL_MASK) == MouseActions.SCROLL_MASK) {
      mouseWheel =
          new DropDownButton(
              MouseActions.T_WHEEL,
              buildMouseIcon(MouseActions.T_WHEEL, actions.getAction(MouseActions.T_WHEEL))) {

            @Override
            protected JPopupMenu getPopupMenu() {
              return getPopupMenuScroll(this);
            }
          };
      mouseWheel.setToolTipText(Messages.getString("ViewerToolBar.change"));
      add(mouseWheel);
    } else {
      mouseWheel = null;
    }

    if ((activeMouse & InputEvent.BUTTON2_DOWN_MASK) == InputEvent.BUTTON2_DOWN_MASK) {
      mouseMiddle = buildMouseButton(actions, MouseActions.T_MIDDLE);
      add(mouseMiddle);
    } else {
      mouseMiddle = null;
    }

    if (activeMouse > 1) {
      addSeparator();
    }

    if (props.getBooleanProperty("weasis.toolbar.layout.button", true)) {
      final DropDownButton layout =
          new DropDownButton(
              "layout", // NON-NLS
              new DropButtonIcon(ResourceUtil.getToolBarIcon(ActionIcon.LAYOUT))) {

            @Override
            protected JPopupMenu getPopupMenu() {
              return getLayoutPopupMenuButton(this);
            }
          };
      layout.setToolTipText(Messages.getString("ViewerToolBar.layout"));
      add(layout);
    }

    if (props.getBooleanProperty("weasis.toolbar.synch.button", true)) {
      synchButton = buildSynchButton();
      add(synchButton);
    } else {
      synchButton = null;
    }

    if (props.getBooleanProperty("weasis.toolbar.reset", true)) {
      final JButton resetButton = new JButton();
      resetButton.setToolTipText(Messages.getString("ViewerToolBar.disReset"));
      resetButton.setIcon(ResourceUtil.getToolBarIcon(ActionIcon.RESET));
      resetButton.addActionListener(e -> eventManager.resetDisplay());
      eventManager.getAction(ActionW.RESET).ifPresent(r -> r.registerActionState(resetButton));
      add(resetButton);
    }
  }

  private DropDownButton buildMouseButton(MouseActions actions, String actionLabel) {
    String action = actions.getAction(actionLabel);
    final DropDownButton button =
        new DropDownButton(actionLabel, buildMouseIcon(actionLabel, action)) {

          @Override
          protected JPopupMenu getPopupMenu() {
            return getPopupMenuButton(this);
          }
        };
    button.setActionCommand(action);
    button.setToolTipText(Messages.getString("ViewerToolBar.change"));
    return button;
  }

  public DropDownButton getMouseLeft() {
    return mouseLeft;
  }

  private JPopupMenu getLayoutPopupMenuButton(DropDownButton dropDownButton) {
    Optional<ComboItemListener<GridBagLayoutModel>> layout = eventManager.getAction(ActionW.LAYOUT);
    JPopupMenu popupMouseButtons = new JPopupMenu();
    if (layout.isPresent()) {
      JMenu menu = layout.get().createUnregisteredRadioMenu("layout"); // NON-NLS
      popupMouseButtons.setInvoker(dropDownButton);
      Component[] cps = menu.getMenuComponents();
      for (Component cp : cps) {
        popupMouseButtons.add(cp);
      }
    }
    return popupMouseButtons;
  }

  public void removeMouseAction(Feature<?> action) {
    if (action != null) {
      actionsButtons.remove(action);
      String cmd = action.cmd();
      MouseActions actions = eventManager.getMouseActions();
      if (cmd.equals(mouseLeft.getActionCommand())) {
        Feature<?> last = actionsButtons.get(0);
        actions.setAction(MouseActions.T_LEFT, last.cmd());
        changeButtonState(MouseActions.T_LEFT, last.cmd());
      }
    }
  }

  private JPopupMenu getPopupMenuButton(DropDownButton dropButton) {
    String type = dropButton.getType();
    String action = eventManager.getMouseActions().getAction(type);
    JPopupMenu popupMouseButtons = new JPopupMenu(type);
    popupMouseButtons.setInvoker(dropButton);
    ButtonGroup groupButtons = new ButtonGroup();
    synchronized (actionsButtons) {
      for (Feature<?> b : actionsButtons) {
        if (eventManager.isActionRegistered(b)) {
          JRadioButtonMenuItem radio =
              new JRadioButtonMenuItem(b.getTitle(), b.getIcon(), b.cmd().equals(action));
          GuiUtils.applySelectedIconEffect(radio);
          radio.setActionCommand(b.cmd());
          radio.addActionListener(this);
          if (MouseActions.T_LEFT.equals(type)) {
            radio.setAccelerator(KeyStroke.getKeyStroke(b.getKeyCode(), b.getModifier()));
          }
          popupMouseButtons.add(radio);
          groupButtons.add(radio);
        }
      }
    }
    return popupMouseButtons;
  }

  private JPopupMenu getPopupMenuScroll(DropDownButton dropButton) {
    String type = dropButton.getType();
    String action = eventManager.getMouseActions().getAction(type);
    JPopupMenu popupMouseScroll = new JPopupMenu(type);
    popupMouseScroll.setInvoker(dropButton);
    ButtonGroup groupButtons = new ButtonGroup();
    for (Feature<?> actionW : actionsScroll) {
      if (eventManager.isActionRegistered(actionW)) {
        JRadioButtonMenuItem radio =
            new JRadioButtonMenuItem(
                actionW.getTitle(), actionW.getIcon(), actionW.cmd().equals(action));
        GuiUtils.applySelectedIconEffect(radio);
        radio.setActionCommand(actionW.cmd());
        radio.addActionListener(this);
        popupMouseScroll.add(radio);
        groupButtons.add(radio);
      }
    }

    return popupMouseScroll;
  }

  @Override
  public void actionPerformed(ActionEvent e) {
    if (e.getSource() instanceof JRadioButtonMenuItem item
        && item.getParent() instanceof JPopupMenu popupMenu) {
      MouseActions mouseActions = eventManager.getMouseActions();
      mouseActions.setAction(popupMenu.getLabel(), item.getActionCommand());
      ImageViewerPlugin<E> view = eventManager.getSelectedView2dContainer();
      if (view != null) {
        view.setMouseActions(mouseActions);
      }
      if (popupMenu.getInvoker() instanceof DropDownButton) {
        changeButtonState(popupMenu.getLabel(), item.getActionCommand());
      }
    }
  }

  public boolean isCommandActive(String cmd) {
    int active = eventManager.getMouseActions().getActiveButtons();
    return cmd != null && checkButtonCommand(cmd, mouseLeft)
        || (((active & InputEvent.BUTTON2_DOWN_MASK) == InputEvent.BUTTON2_DOWN_MASK)
            && checkButtonCommand(cmd, mouseMiddle))
        || (((active & InputEvent.BUTTON3_DOWN_MASK) == InputEvent.BUTTON3_DOWN_MASK)
            && checkButtonCommand(cmd, mouseRight));
  }

  private static boolean checkButtonCommand(String cmd, JButton button) {
    return button != null && cmd.equals(button.getActionCommand());
  }

  public void changeButtonState(String type, String cmd) {
    DropDownButton button = getDropDownButton(type);
    if (button != null) {
      Icon icon = buildMouseIcon(type, cmd);
      button.setIcon(icon);
      button.setActionCommand(cmd);
    }
  }

  private Icon buildMouseIcon(String type, String cmd) {
    final Icon mouseIcon = getMouseIcon(type);
    Feature<?> action = getAction(actionsButtons, cmd);
    final Icon smallIcon = action == null ? ActionW.NO_ACTION.getIcon() : action.getIcon();

    return getDopButtonIcon(mouseIcon, smallIcon);
  }

  static Icon getDopButtonIcon(Icon bckIcon, Icon smallIcon) {
    return new DropButtonIcon(
        new Icon() {

          @Override
          public void paintIcon(Component c, Graphics g, int x, int y) {
            if (c instanceof AbstractButton model) {
              Icon icon = null;
              if (!model.isEnabled()) {
                icon = UIManager.getLookAndFeel().getDisabledIcon(model, bckIcon);
              }
              if (icon == null) {
                icon = bckIcon;
              }
              icon.paintIcon(c, g, x, y);
              if (smallIcon != null) {
                Icon sIcon = null;
                if (!model.isEnabled()) {
                  sIcon = UIManager.getLookAndFeel().getDisabledIcon(model, smallIcon);
                }
                if (sIcon == null) {
                  sIcon = smallIcon;
                }
                int sx = x + bckIcon.getIconWidth() - sIcon.getIconWidth();
                int sy = y + bckIcon.getIconHeight() - sIcon.getIconHeight();
                sIcon.paintIcon(c, g, sx, sy);
              }
            }
          }

          @Override
          public int getIconWidth() {
            return bckIcon.getIconWidth();
          }

          @Override
          public int getIconHeight() {
            return bckIcon.getIconHeight();
          }
        });
  }

  private DropDownButton buildSynchButton() {
    GroupPopup menu = null;
    ComboItemListener<SynchView> synch = eventManager.getAction(ActionW.SYNCH).orElse(null);
    SynchView synchView = SynchView.DEFAULT_STACK;
    if (synch != null) {
      if (synch.getSelectedItem() instanceof SynchView sel) {
        synchView = sel;
      }
      menu = new SynchGroupMenu();
      synch.registerActionState(menu);
    }
    final DropDownButton button =
        new DropDownButton(ActionW.SYNCH.cmd(), buildSynchIcon(synchView), menu) {
          @Override
          protected JPopupMenu getPopupMenu() {
            JPopupMenu menu =
                (getMenuModel() == null) ? new JPopupMenu() : getMenuModel().createJPopupMenu();
            menu.setInvoker(this);
            return menu;
          }
        };
    button.setToolTipText(Messages.getString("ViewerToolBar.synch"));
    if (synch != null) {
      synch.registerActionState(button);
    }
    return button;
  }

  private static Icon buildSynchIcon(SynchView synch) {
    final Icon mouseIcon = ResourceUtil.getToolBarIcon(ActionIcon.SYNCH_LARGE);
    final FlatSVGIcon smallIcon =
        GuiUtils.getDerivedIcon(
            synch.getIcon(), new ColorFilter().add(new Color(0x6E6E6E), new Color(0x389FD6)));
    return new DropButtonIcon(
        new Icon() {

          @Override
          public void paintIcon(Component c, Graphics g, int x, int y) {
            if (c instanceof AbstractButton model) {
              Icon icon = null;
              if (!model.isEnabled()) {
                icon = UIManager.getLookAndFeel().getDisabledIcon(model, mouseIcon);
              }
              if (icon == null) {
                icon = mouseIcon;
              }
              Icon sIcon = null;
              if (!model.isEnabled()) {
                sIcon = UIManager.getLookAndFeel().getDisabledIcon(model, smallIcon);
              }
              if (sIcon == null) {
                sIcon = smallIcon;
              }
              int x2 = x + mouseIcon.getIconWidth() / 2 - sIcon.getIconWidth() / 2;
              int y2 = y + mouseIcon.getIconHeight() / 2 - sIcon.getIconHeight() / 2;
              sIcon.paintIcon(c, g, x2, y2);
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

  public DropDownButton getDropDownButton(String type) {
    if (MouseActions.T_LEFT.equals(type)) {
      return mouseLeft;
    } else if (MouseActions.T_RIGHT.equals(type)) {
      return mouseRight;
    } else if (MouseActions.T_MIDDLE.equals(type)) {
      return mouseMiddle;
    } else if (MouseActions.T_WHEEL.equals(type)) {
      return mouseWheel;
    }
    return null;
  }

  public Feature<?> getAction(List<Feature<?>> buttons, String command) {
    if (buttons != null) {
      synchronized (buttons) { // NOSONAR lock object is the list for iterating its elements safely
        for (Feature<?> a : buttons) {
          if (a.cmd().equals(command)) {
            return a;
          }
        }
      }
    }
    return null;
  }

  private static FlatSVGIcon getMouseIcon(String type) {
    if (MouseActions.T_LEFT.equals(type)) {
      return MouseLeftIcon;
    } else if (MouseActions.T_RIGHT.equals(type)) {
      return MouseRightIcon;
    } else if (MouseActions.T_MIDDLE.equals(type)) {
      return MouseMiddleIcon;
    } else if (MouseActions.T_WHEEL.equals(type)) {
      return MouseWheelIcon;
    }
    return MouseLeftIcon;
  }

  public static Feature<?> getNextCommand(List<Feature<?>> buttons, String command) {
    if (buttons != null && !buttons.isEmpty()) {
      int index = 0;
      synchronized (buttons) { // NOSONAR lock object is the list for iterating its elements safely
        for (int i = 0; i < buttons.size(); i++) {
          Feature<?> b = buttons.get(i);
          if (b.cmd().equals(command)) {
            index = (i == buttons.size() - 1) ? 0 : i + 1;
            break;
          }
        }
        return buttons.get(index);
      }
    }
    return null;
  }

  class SynchGroupMenu extends GroupRadioMenu<SynchView> {

    @Override
    public void contentsChanged(ListDataEvent e) {
      super.contentsChanged(e);
      changeButtonState();
    }

    public void changeButtonState() {
      if (synchButton != null && dataModel.getSelectedItem() instanceof SynchView sel) {
        Icon icon = buildSynchIcon(sel);
        synchButton.setIcon(icon);
        synchButton.setActionCommand(sel.toString());
      }
    }
  }
}
