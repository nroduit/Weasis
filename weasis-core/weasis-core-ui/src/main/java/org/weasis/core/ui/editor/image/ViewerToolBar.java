/*******************************************************************************
 * Copyright (c) 2010 Nicolas Roduit.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     Nicolas Roduit - initial API and implementation
 ******************************************************************************/
package org.weasis.core.ui.editor.image;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;

import javax.swing.Box;
import javax.swing.ButtonGroup;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JMenu;
import javax.swing.JPopupMenu;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.JToggleButton;
import javax.swing.KeyStroke;

import org.weasis.core.api.gui.util.ActionState;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.gui.util.ComboItemListener;
import org.weasis.core.api.gui.util.DropButtonIcon;
import org.weasis.core.api.gui.util.DropDownButton;
import org.weasis.core.api.gui.util.RadioMenuItem;
import org.weasis.core.api.gui.util.SliderChangeListener;
import org.weasis.core.api.gui.util.ToggleButtonListener;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.ui.Messages;
import org.weasis.core.ui.util.WtoolBar;

public class ViewerToolBar<E extends ImageElement> extends WtoolBar implements ActionListener {

    public static final ActionW[] actionsButtons =
    // ActionW.DRAW,
        { ActionW.PAN, ActionW.WINLEVEL, ActionW.SCROLL_SERIES, ActionW.ZOOM, ActionW.ROTATION, ActionW.MEASURE,
            ActionW.CONTEXTMENU };
    public static final ActionW[] actionsScroll = { ActionW.SCROLL_SERIES, ActionW.ZOOM, ActionW.ROTATION };
    public static final Icon MouseLeftIcon =
        new ImageIcon(MouseActions.class.getResource("/icon/32x32/mouse-left.png")); //$NON-NLS-1$
    public static final Icon MouseRightIcon = new ImageIcon(
        MouseActions.class.getResource("/icon/32x32/mouse-right.png")); //$NON-NLS-1$
    public static final Icon MouseMiddleIcon = new ImageIcon(
        MouseActions.class.getResource("/icon/32x32/mouse-middle.png")); //$NON-NLS-1$
    public static final Icon MouseWheelIcon = new ImageIcon(
        MouseActions.class.getResource("/icon/32x32/mouse-wheel.png")); //$NON-NLS-1$

    protected final ImageViewerEventManager<E> eventManager;
    private final DropDownButton mouseLeft;
    private DropDownButton mouseMiddle;
    private DropDownButton mouseRight;
    private DropDownButton mouseWheel;
    private final MeasureToolBar<E> measureToolBar;

    public ViewerToolBar(final ImageViewerEventManager<E> eventManager) {
        super("viewer2dBar", TYPE.main); //$NON-NLS-1$
        if (eventManager == null) {
            throw new IllegalArgumentException("EventManager cannot be null"); //$NON-NLS-1$
        }
        this.eventManager = eventManager;
        measureToolBar = new MeasureToolBar<E>(eventManager);

        MouseActions actions = eventManager.getMouseActions();
        int active = actions.getActiveButtons();
        mouseLeft = buildMouseButton(actions, MouseActions.LEFT);
        mouseLeft
            .setToolTipText(Messages.getString("ViewerToolBar.change") + Messages.getString("ViewerToolBar.m_action")); //$NON-NLS-1$ //$NON-NLS-2$
        add(mouseLeft);
        if (((active & InputEvent.BUTTON2_DOWN_MASK) == InputEvent.BUTTON2_DOWN_MASK)) {
            add(mouseMiddle = buildMouseButton(actions, MouseActions.MIDDLE));
        }
        if (((active & InputEvent.BUTTON3_DOWN_MASK) == InputEvent.BUTTON3_DOWN_MASK)) {
            add(mouseRight = buildMouseButton(actions, MouseActions.RIGHT));
        }

        if (((active & MouseActions.SCROLL_MASK) == MouseActions.SCROLL_MASK)) {
            mouseWheel =
                new DropDownButton(MouseActions.WHEEL, buildMouseIcon(MouseActions.WHEEL,
                    actions.getAction(MouseActions.WHEEL))) {

                    @Override
                    protected JPopupMenu getPopupMenu() {
                        return getPopupMenuScroll(this);
                    }
                };
            mouseWheel.setToolTipText(Messages.getString("ViewerToolBar.change")); //$NON-NLS-1$
            add(mouseWheel);
        }

        addSeparator(WtoolBar.SEPARATOR_2x24);

        final DropDownButton layout = new DropDownButton("layout", new DropButtonIcon(new ImageIcon(MouseActions.class //$NON-NLS-1$
            .getResource("/icon/32x32/layout.png")))) { //$NON-NLS-1$

                @Override
                protected JPopupMenu getPopupMenu() {
                    return getLayoutPopupMenuButton(this);
                }
            };
        layout.setToolTipText(Messages.getString("ViewerToolBar.layout")); //$NON-NLS-1$
        add(layout);

        add(Box.createRigidArea(new Dimension(5, 0)));

        final DropDownButton button = buildSynchButton();
        add(button);

        addSeparator(WtoolBar.SEPARATOR_2x24);

        final JButton jButtonActualZoom =
            new JButton(new ImageIcon(MouseActions.class.getResource("/icon/32x32/zoom-original.png"))); //$NON-NLS-1$
        jButtonActualZoom.setToolTipText(Messages.getString("ViewerToolBar.zoom_1")); //$NON-NLS-1$
        jButtonActualZoom.addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent e) {
                ActionState zoom = eventManager.getAction(ActionW.ZOOM);
                if (zoom instanceof SliderChangeListener) {
                    ((SliderChangeListener) zoom).setValue(eventManager.viewScaleToSliderValue(1.0));
                }
            }
        });
        add(jButtonActualZoom);

        final JButton jButtonBestFit =
            new JButton(new ImageIcon(MouseActions.class.getResource("/icon/32x32/zoom-bestfit.png"))); //$NON-NLS-1$
        jButtonBestFit.setToolTipText(Messages.getString("ViewerToolBar.zoom_b")); //$NON-NLS-1$
        jButtonBestFit.addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent e) {
                // Pass the value 0.0 (convention: best fit zoom value) directly to the property change, otherwise the
                // value is adjusted by the BoundedRangeModel
                eventManager.firePropertyChange(ActionW.ZOOM.cmd(), null, 0.0);
            }
        });
        add(jButtonBestFit);

        final JToggleButton jButtonLens =
            new JToggleButton(new ImageIcon(MouseActions.class.getResource("/icon/32x32/zoom-lens.png"))); //$NON-NLS-1$
        jButtonLens.setToolTipText("Show the magnifying lens"); //$NON-NLS-1$
        ActionState lens = eventManager.getAction(ActionW.LENS);
        if (lens instanceof ToggleButtonListener) {
            ((ToggleButtonListener) lens).registerComponent(jButtonLens);
        }
        add(jButtonLens);

        displayMeasureToobar();
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

    private JPopupMenu getSychPopupMenuButton(final DropDownButton dropDownButton) {
        ActionState synch = eventManager.getAction(ActionW.SYNCH);
        JPopupMenu popupMouseButtons = new JPopupMenu();
        if (synch instanceof ComboItemListener) {
            final ComboItemListener combo = ((ComboItemListener) synch);
            ActionListener listener = new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    if (e.getSource() instanceof RadioMenuItem) {
                        RadioMenuItem item = (RadioMenuItem) e.getSource();
                        Icon icon = buildSynchIcon((SynchView) item.getObject());
                        dropDownButton.setIcon(icon);
                    }
                }
            };
            JMenu menu = combo.createUnregisteredRadioMenu("synch"); //$NON-NLS-1$
            popupMouseButtons.setInvoker(dropDownButton);
            Component[] cps = menu.getMenuComponents();
            for (int i = 0; i < cps.length; i++) {
                if (cps[i] instanceof RadioMenuItem) {
                    RadioMenuItem button = (RadioMenuItem) cps[i];
                    button.addActionListener(listener);
                }
                popupMouseButtons.add(cps[i]);
            }
        }
        return popupMouseButtons;
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

    public MeasureToolBar getMeasureToolBar() {
        return measureToolBar;
    }

    private JPopupMenu getPopupMenuButton(DropDownButton dropButton) {
        String type = dropButton.getType();
        String action = eventManager.getMouseActions().getAction(type);
        JPopupMenu popupMouseButtons = new JPopupMenu(type);
        popupMouseButtons.setInvoker(dropButton);
        ButtonGroup groupButtons = new ButtonGroup();
        for (int i = 0; i < actionsButtons.length; i++) {
            JRadioButtonMenuItem radio =
                new JRadioButtonMenuItem(actionsButtons[i].getTitle(), actionsButtons[i].getIcon(), actionsButtons[i]
                    .cmd().equals(action));
            radio.setActionCommand(actionsButtons[i].cmd());
            radio.addActionListener(this);
            if (MouseActions.LEFT.equals(type)) {
                radio.setAccelerator(KeyStroke.getKeyStroke(actionsButtons[i].getKeyCode(),
                    actionsButtons[i].getModifier()));
            }
            popupMouseButtons.add(radio);
            groupButtons.add(radio);
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
            JRadioButtonMenuItem radio =
                new JRadioButtonMenuItem(actionsScroll[i].getTitle(), actionsScroll[i].getIcon(), actionsScroll[i]
                    .cmd().equals(action));
            radio.setActionCommand(actionsScroll[i].cmd());
            radio.addActionListener(this);
            popupMouseScroll.add(radio);
            groupButtons.add(radio);
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

    private void displayMeasureToobar() {
        if (isCommandActive(ActionW.MEASURE.cmd())) {
            measureToolBar.setVisible(true);
        } else {
            measureToolBar.setVisible(false);
        }
        revalidate();
        repaint();
    }

    public boolean isCommandActive(String cmd) {
        int active = eventManager.getMouseActions().getActiveButtons();
        if (cmd != null
            && cmd.equals(mouseLeft.getActionCommand())
            || (((active & InputEvent.BUTTON2_DOWN_MASK) == InputEvent.BUTTON2_DOWN_MASK) && ((mouseMiddle == null)
                ? false : cmd.equals(mouseMiddle.getActionCommand())))
            || (((active & InputEvent.BUTTON3_DOWN_MASK) == InputEvent.BUTTON3_DOWN_MASK) && ((mouseRight == null)
                ? false : cmd.equals(mouseRight.getActionCommand())))) {
            return true;
        }
        return false;
    }

    public void changeButtonState(String type, String action) {
        DropDownButton button = getDropDownButton(type);
        if (button != null) {
            Icon icon = buildMouseIcon(type, action);
            button.setIcon(icon);
            button.setActionCommand(action);
            displayMeasureToobar();
        }
    }

    private Icon buildMouseIcon(String type, String action) {
        final Icon mouseIcon = getMouseIcon(type);
        ActionW actionW = getAction(actionsButtons, action);
        final Icon smallIcon = actionW == null ? null : actionW.getIcon();
        return new DropButtonIcon(new Icon() {

            @Override
            public void paintIcon(Component c, Graphics g, int x, int y) {
                mouseIcon.paintIcon(c, g, x, y);
                if (smallIcon != null) {
                    x += mouseIcon.getIconWidth() - smallIcon.getIconWidth();
                    y += mouseIcon.getIconHeight() - smallIcon.getIconHeight();
                    smallIcon.paintIcon(c, g, x, y);
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
        ActionState synch = eventManager.getAction(ActionW.SYNCH);
        SynchView synchView = SynchView.DEFAULT_STACK;
        if (synch instanceof ComboItemListener) {
            Object sel = ((ComboItemListener) synch).getSelectedItem();
            if (sel instanceof SynchView) {
                synchView = (SynchView) sel;
            }
        }
        final DropDownButton button = new DropDownButton(ActionW.SYNCH.cmd(), buildSynchIcon(synchView)) { //$NON-NLS-1$

                @Override
                protected JPopupMenu getPopupMenu() {
                    return getSychPopupMenuButton(this);
                }
            };
        button.setToolTipText(Messages.getString("ViewerToolBar.synch")); //$NON-NLS-1$
        return button;
    }

    private Icon buildSynchIcon(SynchView synch) {
        final Icon mouseIcon = new ImageIcon(MouseActions.class.getResource("/icon/32x32/synch.png")); //$NON-NLS-1$
        final Icon smallIcon = synch.getIcon();
        return new DropButtonIcon(new Icon() {

            @Override
            public void paintIcon(Component c, Graphics g, int x, int y) {
                if (smallIcon != null) {
                    int x2 = x + mouseIcon.getIconWidth() / 2 - smallIcon.getIconWidth() / 2;
                    int y2 = y + mouseIcon.getIconHeight() / 2 - smallIcon.getIconHeight() / 2;
                    smallIcon.paintIcon(c, g, x2, y2);
                }
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

    public DropDownButton getDropDownButton(String type) {
        if (MouseActions.LEFT.equals(type)) {
            return mouseLeft;
        } else if (MouseActions.RIGHT.equals(type)) {
            return mouseRight;
        } else if (MouseActions.MIDDLE.equals(type)) {
            return mouseMiddle;
        } else if (MouseActions.WHEEL.equals(type)) {
            return mouseWheel;
        }
        return null;
    }

    private ActionW getAction(ActionW[] actions, String command) {
        if (actions != null) {
            for (ActionW a : actions) {
                if (a.cmd().equals(command)) {
                    return a;
                }
            }
        }
        return null;
    }

    private Icon getMouseIcon(String type) {
        if (MouseActions.LEFT.equals(type)) {
            return MouseLeftIcon;
        } else if (MouseActions.RIGHT.equals(type)) {
            return MouseRightIcon;
        } else if (MouseActions.MIDDLE.equals(type)) {
            return MouseMiddleIcon;
        } else if (MouseActions.WHEEL.equals(type)) {
            return MouseWheelIcon;
        }
        return MouseLeftIcon;
    }

    public static final ActionW getNextCommand(ActionW[] actions, String command) {
        if (actions != null && actions.length > 0) {
            int index = 0;
            for (int i = 0; i < actions.length; i++) {
                if (actions[i].cmd().equals(command)) {
                    index = i == actions.length - 1 ? 0 : i + 1;
                }
            }
            return actions[index];
        }
        return null;
    }
}
