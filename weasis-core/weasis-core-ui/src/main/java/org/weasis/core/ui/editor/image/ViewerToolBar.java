/*******************************************************************************
 * Copyright (c) 2009-2018 Weasis Team and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 *     Nicolas Roduit - initial API and implementation
 *******************************************************************************/
package org.weasis.core.ui.editor.image;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import javax.swing.AbstractButton;
import javax.swing.Box;
import javax.swing.ButtonGroup;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JMenu;
import javax.swing.JPopupMenu;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.KeyStroke;
import javax.swing.UIManager;
import javax.swing.event.ListDataEvent;

import org.weasis.core.api.gui.util.ActionState;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.gui.util.ComboItemListener;
import org.weasis.core.api.gui.util.DropButtonIcon;
import org.weasis.core.api.gui.util.DropDownButton;
import org.weasis.core.api.gui.util.GroupPopup;
import org.weasis.core.api.gui.util.GroupRadioMenu;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.api.service.WProperties;
import org.weasis.core.ui.Messages;
import org.weasis.core.ui.util.WtoolBar;

public class ViewerToolBar<E extends ImageElement> extends WtoolBar implements ActionListener {

    public static final List<ActionW> actionsButtons = Collections.synchronizedList(new ArrayList<>(Arrays
        .asList(new ActionW[] { ActionW.PAN, ActionW.WINLEVEL, ActionW.SCROLL_SERIES, ActionW.ZOOM, ActionW.ROTATION,
            ActionW.MEASURE, ActionW.DRAW, ActionW.CONTEXTMENU, ActionW.CROSSHAIR, ActionW.NO_ACTION })));

    public static final ActionW[] actionsScroll =
        { ActionW.SCROLL_SERIES, ActionW.ZOOM, ActionW.ROTATION, ActionW.NO_ACTION };
    public static final Icon MouseLeftIcon =
        new ImageIcon(MouseActions.class.getResource("/icon/32x32/mouse-left.png")); //$NON-NLS-1$
    public static final Icon MouseRightIcon =
        new ImageIcon(MouseActions.class.getResource("/icon/32x32/mouse-right.png")); //$NON-NLS-1$
    public static final Icon MouseMiddleIcon =
        new ImageIcon(MouseActions.class.getResource("/icon/32x32/mouse-middle.png")); //$NON-NLS-1$
    public static final Icon MouseWheelIcon =
        new ImageIcon(MouseActions.class.getResource("/icon/32x32/mouse-wheel.png")); //$NON-NLS-1$

    protected final ImageViewerEventManager<E> eventManager;
    private final DropDownButton mouseLeft;
    private final DropDownButton mouseMiddle;
    private final DropDownButton mouseRight;
    private final DropDownButton mouseWheel;
    private final DropDownButton synchButton;

    public ViewerToolBar(final ImageViewerEventManager<E> eventManager, int activeMouse, WProperties props, int index) {
        super(Messages.getString("ViewerToolBar.title"), index); //$NON-NLS-1$
        if (eventManager == null) {
            throw new IllegalArgumentException("EventManager cannot be null"); //$NON-NLS-1$
        }
        this.eventManager = eventManager;

        MouseActions actions = eventManager.getMouseActions();

        if ((activeMouse & InputEvent.BUTTON1_DOWN_MASK) == InputEvent.BUTTON1_DOWN_MASK) {
            mouseLeft = buildMouseButton(actions, MouseActions.T_LEFT);
            mouseLeft.setToolTipText(
                Messages.getString("ViewerToolBar.change") + " " + Messages.getString("ViewerToolBar.m_action")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
            add(mouseLeft);
        } else {
            mouseLeft = null;
        }

        if ((activeMouse & InputEvent.BUTTON2_DOWN_MASK) == InputEvent.BUTTON2_DOWN_MASK) {
            mouseMiddle = buildMouseButton(actions, MouseActions.T_MIDDLE);
            add(mouseMiddle);
        } else {
            mouseMiddle = null;
        }

        if ((activeMouse & InputEvent.BUTTON3_DOWN_MASK) == InputEvent.BUTTON3_DOWN_MASK) {
            mouseRight = buildMouseButton(actions, MouseActions.T_RIGHT);
            add(mouseRight);
        } else {
            mouseRight = null;
        }

        if ((activeMouse & MouseActions.SCROLL_MASK) == MouseActions.SCROLL_MASK) {
            mouseWheel = new DropDownButton(MouseActions.T_WHEEL,
                buildMouseIcon(MouseActions.T_WHEEL, actions.getAction(MouseActions.T_WHEEL))) {

                @Override
                protected JPopupMenu getPopupMenu() {
                    return getPopupMenuScroll(this);
                }
            };
            mouseWheel.setToolTipText(Messages.getString("ViewerToolBar.change")); //$NON-NLS-1$
            add(mouseWheel);
        } else {
            mouseWheel = null;
        }

        if (activeMouse > 1) {
            addSeparator(WtoolBar.SEPARATOR_2x24);
        }

        if (props.getBooleanProperty("weasis.toolbar.layoutbouton", true)) { //$NON-NLS-1$
            final DropDownButton layout =
                new DropDownButton("layout", new DropButtonIcon(new ImageIcon(MouseActions.class //$NON-NLS-1$
                    .getResource("/icon/32x32/layout.png")))) { //$NON-NLS-1$

                    @Override
                    protected JPopupMenu getPopupMenu() {
                        return getLayoutPopupMenuButton(this);
                    }
                };
            layout.setToolTipText(Messages.getString("ViewerToolBar.layout")); //$NON-NLS-1$
            add(layout);
        }

        if (props.getBooleanProperty("weasis.toolbar.synchbouton", true)) { //$NON-NLS-1$
            add(Box.createRigidArea(new Dimension(5, 0)));

            synchButton = buildSynchButton();
            add(synchButton);
        } else {
            synchButton = null;
        }

        if (props.getBooleanProperty("weasis.toolbar.reset", true)) { //$NON-NLS-1$
            final JButton resetButton = new JButton();
            resetButton.setToolTipText(Messages.getString("ViewerToolBar.disReset")); //$NON-NLS-1$
            resetButton.setIcon(new ImageIcon(WtoolBar.class.getResource("/icon/32x32/reset.png"))); //$NON-NLS-1$
            resetButton.addActionListener(e -> eventManager.resetDisplay());
            ActionState layout = eventManager.getAction(ActionW.RESET);
            if (layout != null) {
                layout.registerActionState(resetButton);
            }
            add(resetButton);
        }
    }

    private DropDownButton buildMouseButton(MouseActions actions, String actionLabel) {
        String action = actions.getAction(actionLabel);
        final DropDownButton button = new DropDownButton(actionLabel, buildMouseIcon(actionLabel, action)) {

            @Override
            protected JPopupMenu getPopupMenu() {
                return getPopupMenuButton(this);
            }
        };
        button.setActionCommand(action);
        button.setToolTipText(Messages.getString("ViewerToolBar.change")); //$NON-NLS-1$
        return button;
    }

    public DropDownButton getMouseLeft() {
        return mouseLeft;
    }

    private JPopupMenu getLayoutPopupMenuButton(DropDownButton dropDownButton) {
        ActionState layout = eventManager.getAction(ActionW.LAYOUT);
        JPopupMenu popupMouseButtons = new JPopupMenu();
        if (layout instanceof ComboItemListener) {
            JMenu menu = ((ComboItemListener) layout).createUnregisteredRadioMenu("layout"); //$NON-NLS-1$
            popupMouseButtons.setInvoker(dropDownButton);
            Component[] cps = menu.getMenuComponents();
            for (int i = 0; i < cps.length; i++) {
                popupMouseButtons.add(cps[i]);
            }
        }
        return popupMouseButtons;
    }

    public void removeMouseAction(ActionW action) {
        if (action != null) {
            actionsButtons.remove(action);
            String cmd = action.cmd();
            MouseActions actions = eventManager.getMouseActions();
            if (cmd.equals(mouseLeft.getActionCommand())) {
                ActionW last = actionsButtons.get(0);
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
            for (int i = 0; i < actionsButtons.size(); i++) {
                ActionW b = actionsButtons.get(i);
                if (eventManager.isActionRegistered(b)) {
                    JRadioButtonMenuItem radio =
                        new JRadioButtonMenuItem(b.getTitle(), b.getIcon(), b.cmd().equals(action));
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
        for (int i = 0; i < actionsScroll.length; i++) {
            if (eventManager.isActionRegistered(actionsScroll[i])) {
                JRadioButtonMenuItem radio = new JRadioButtonMenuItem(actionsScroll[i].getTitle(),
                    actionsScroll[i].getIcon(), actionsScroll[i].cmd().equals(action));
                radio.setActionCommand(actionsScroll[i].cmd());
                radio.addActionListener(this);
                popupMouseScroll.add(radio);
                groupButtons.add(radio);
            }
        }

        return popupMouseScroll;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if (e.getSource() instanceof JRadioButtonMenuItem) {
            JRadioButtonMenuItem item = (JRadioButtonMenuItem) e.getSource();
            if (item.getParent() instanceof JPopupMenu) {
                JPopupMenu pop = (JPopupMenu) item.getParent();
                MouseActions mouseActions = eventManager.getMouseActions();
                mouseActions.setAction(pop.getLabel(), item.getActionCommand());
                ImageViewerPlugin<E> view = eventManager.getSelectedView2dContainer();
                if (view != null) {
                    view.setMouseActions(mouseActions);
                }
                if (pop.getInvoker() instanceof DropDownButton) {
                    changeButtonState(pop.getLabel(), item.getActionCommand());
                }
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
        return (button == null) ? false : cmd.equals(button.getActionCommand());
    }

    public void changeButtonState(String type, String action) {
        DropDownButton button = getDropDownButton(type);
        if (button != null) {
            Icon icon = buildMouseIcon(type, action);
            button.setIcon(icon);
            button.setActionCommand(action);
        }
    }

    private Icon buildMouseIcon(String type, String action) {
        final Icon mouseIcon = getMouseIcon(type);
        ActionW actionW = getAction(actionsButtons, action);
        final Icon smallIcon = actionW == null ? ActionW.NO_ACTION.getIcon() : actionW.getIcon();

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
                    if (smallIcon != null) {
                        Icon sIcon = null;
                        if (!model.isEnabled()) {
                            sIcon = UIManager.getLookAndFeel().getDisabledIcon(model, smallIcon);
                        }
                        if (sIcon == null) {
                            sIcon = smallIcon;
                        }
                        int sx = x + mouseIcon.getIconWidth() - sIcon.getIconWidth();
                        int sy = y + mouseIcon.getIconHeight() - sIcon.getIconHeight();
                        sIcon.paintIcon(c, g, sx, sy);
                    }
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

    private DropDownButton buildSynchButton() {
        GroupPopup menu = null;
        ActionState synch = eventManager.getAction(ActionW.SYNCH);
        SynchView synchView = SynchView.DEFAULT_STACK;
        if (synch instanceof ComboItemListener) {
            ComboItemListener m = (ComboItemListener) synch;
            Object sel = m.getSelectedItem();
            if (sel instanceof SynchView) {
                synchView = (SynchView) sel;
            }
            menu = new SynchGroupMenu();
            m.registerActionState(menu);
        }
        final DropDownButton button = new DropDownButton(ActionW.SYNCH.cmd(), buildSynchIcon(synchView), menu) {
            @Override
            protected JPopupMenu getPopupMenu() {
                JPopupMenu menu = (getMenuModel() == null) ? new JPopupMenu() : getMenuModel().createJPopupMenu();
                menu.setInvoker(this);
                return menu;
            }

        };
        button.setToolTipText(Messages.getString("ViewerToolBar.synch")); //$NON-NLS-1$
        if (synch != null) {
            synch.registerActionState(button);
        }
        return button;
    }

    private static Icon buildSynchIcon(SynchView synch) {
        final Icon mouseIcon = new ImageIcon(MouseActions.class.getResource("/icon/32x32/synch.png")); //$NON-NLS-1$
        final Icon smallIcon = synch.getIcon();
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
                    if (smallIcon != null) {
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

    public ActionW getAction(List<ActionW> buttons, String command) {
        if (buttons != null) {
            synchronized (buttons) { //NOSONAR lock object is the list for iterating its elements safely
                for (ActionW a : buttons) {
                    if (a.cmd().equals(command)) {
                        return a;
                    }
                }
            }
        }
        return null;
    }

    private static Icon getMouseIcon(String type) {
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

    public static final ActionW getNextCommand(List<ActionW> buttons, String command) {
        if (buttons != null && !buttons.isEmpty()) {
            int index = 0;
            synchronized (buttons) { //NOSONAR lock object is the list for iterating its elements safely
                for (int i = 0; i < buttons.size(); i++) {
                    ActionW b = buttons.get(i);
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
            Object sel = dataModel.getSelectedItem();
            if (sel instanceof SynchView && synchButton != null) {
                Icon icon = buildSynchIcon((SynchView) sel);
                synchButton.setIcon(icon);
                synchButton.setActionCommand(sel.toString());
            }
        }
    }
}
