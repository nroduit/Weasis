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
import javax.swing.KeyStroke;

import org.weasis.core.api.gui.util.ActionState;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.gui.util.ComboItemListener;
import org.weasis.core.api.gui.util.DropButtonIcon;
import org.weasis.core.api.gui.util.DropDownButton;
import org.weasis.core.api.gui.util.SliderChangeListener;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.ui.Messages;
import org.weasis.core.ui.graphic.model.AbstractLayerModel;
import org.weasis.core.ui.util.WtoolBar;

public class ViewerToolBar<E extends ImageElement> extends WtoolBar implements ActionListener {

    public final static ActionW[] actionsButtons =
    // ActionW.DRAW,
        { ActionW.PAN, ActionW.WINLEVEL, ActionW.SCROLL_SERIES, ActionW.ZOOM, ActionW.ROTATION, ActionW.MEASURE,
            ActionW.CONTEXTMENU };
    public final static ActionW[] actionsScroll = { ActionW.SCROLL_SERIES, ActionW.ZOOM, ActionW.ROTATION };

    protected final ImageViewerEventManager<E> eventManager;
    private final DropDownButton mouseLeft;
    private DropDownButton mouseMiddle;
    private DropDownButton mouseRight;
    private DropDownButton mouseWheel;
    private final MeasureToolBar measureToolBar = new MeasureToolBar() {

        @Override
        public void setDrawActions() {
            ImageViewerPlugin<E> container = eventManager.getSelectedView2dContainer();
            if (container != null) {
                for (DefaultView2d<E> v : container.getImagePanels()) {
                    AbstractLayerModel model = v.getLayerModel();
                    if (model != null) {
                        if (jTgleButtonArrow.isSelected()) {
                            model.setCreateGraphic(null);
                        } else if (jTgleButtonLine.isSelected()) {
                            model.setCreateGraphic(lineGraphic);
                        } else if (jTgleButtonRectangle.isSelected()) {
                            model.setCreateGraphic(rectangleGraphic);
                        } else if (jTgleButtonEllipse.isSelected()) {
                            model.setCreateGraphic(circleGraphic);
                        } else if (jTgleButtonAngle.isSelected()) {
                            model.setCreateGraphic(angleToolGraphic);
                        } else {
                            model.setCreateGraphic(null);
                        }
                    }
                }
            }
        }

        @Override
        protected AbstractLayerModel getCurrentLayerModel() {
            DefaultView2d view = eventManager.getSelectedViewPane();
            if (view != null) {
                return view.getLayerModel();
            }
            return null;
        }
    };

    public ViewerToolBar(final ImageViewerEventManager<E> eventManager) {
        super("viewer2dBar", TYPE.main); //$NON-NLS-1$
        if (eventManager == null) {
            throw new IllegalArgumentException("EventManager cannot be null"); //$NON-NLS-1$
        }
        this.eventManager = eventManager;
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
                new DropDownButton(MouseActions.WHEEL, new DropButtonIcon(new ImageIcon(MouseActions.class
                    .getResource("/icon/32x32/mouse-wheel-" + actions.getAction(MouseActions.WHEEL) + ".png")))) { //$NON-NLS-1$ //$NON-NLS-2$

                    @Override
                    protected JPopupMenu getPopupMenu() {
                        return getPopupMenuScroll(this);
                    }
                };
            mouseWheel.setToolTipText(Messages.getString("ViewerToolBar.change")); //$NON-NLS-1$
            add(mouseWheel);
        }

        // addSeparator(WtoolBar.SEPARATOR_2x24);

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

        final DropDownButton button =
            new DropDownButton(ActionW.SYNCH.getCommand(), new DropButtonIcon(new ImageIcon(MouseActions.class
                .getResource("/icon/32x32/synch.png")))) { //$NON-NLS-1$

                @Override
                protected JPopupMenu getPopupMenu() {
                    return getSychPopupMenuButton(this);
                }
            };
        button.setToolTipText(Messages.getString("ViewerToolBar.synch")); //$NON-NLS-1$
        add(button);

        // addSeparator(WtoolBar.SEPARATOR_2x24);
        final JButton jButtonActualZoom =
            new JButton(new ImageIcon(MouseActions.class.getResource("/icon/22x22/zoom-original.png"))); //$NON-NLS-1$
        jButtonActualZoom.setToolTipText(Messages.getString("ViewerToolBar.zoom_1")); //$NON-NLS-1$
        jButtonActualZoom.addActionListener(new ActionListener() {

            public void actionPerformed(ActionEvent e) {
                ActionState zoom = eventManager.getAction(ActionW.ZOOM);
                if (zoom instanceof SliderChangeListener) {
                    ((SliderChangeListener) zoom).setValue(eventManager.viewScaleToSliderValue(1.0));
                }
            }
        });
        add(jButtonActualZoom);

        final JButton jButtonBestFit =
            new JButton(new ImageIcon(MouseActions.class.getResource("/icon/22x22/zoom-bestfit.png"))); //$NON-NLS-1$
        jButtonBestFit.setToolTipText(Messages.getString("ViewerToolBar.zoom_b")); //$NON-NLS-1$
        jButtonBestFit.addActionListener(new ActionListener() {

            public void actionPerformed(ActionEvent e) {
                // Pass the value 0.0 (convention: best fit zoom value) directly to the property change, otherwise the
                // value is adjusted by the BoundedRangeModel
                eventManager.firePropertyChange(ActionW.ZOOM.getCommand(), null, 0.0);
            }
        });
        add(jButtonBestFit);
        displayMeasureToobar();
    }

    private DropDownButton buildMouseButton(MouseActions actions, String actionLabel) {
        String action = actions.getAction(actionLabel);
        final DropDownButton button =
            new DropDownButton(actionLabel, new DropButtonIcon(new ImageIcon(MouseActions.class
                .getResource("/icon/32x32/mouse-" + actionLabel + "-" + action + ".png")))) { //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$

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

    private JPopupMenu getSychPopupMenuButton(DropDownButton dropDownButton) {
        ActionState synch = eventManager.getAction(ActionW.SYNCH);
        JPopupMenu popupMouseButtons = new JPopupMenu();
        if (synch instanceof ComboItemListener) {
            JMenu menu = ((ComboItemListener) synch).createUnregisteredRadioMenu("synch"); //$NON-NLS-1$
            popupMouseButtons.setInvoker(dropDownButton);
            Component[] cps = menu.getMenuComponents();
            for (int i = 0; i < cps.length; i++) {
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

    @Override
    public void initialize() {
        measureToolBar.initialize();
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
                    .getCommand().equals(action));
            radio.setActionCommand(actionsButtons[i].getCommand());
            radio.addActionListener(this);
            if (MouseActions.LEFT.equals(type)) {
                radio.setAccelerator(KeyStroke.getKeyStroke(actionsButtons[i].getKeyCode(), actionsButtons[i]
                    .getModifier()));
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
                    .getCommand().equals(action));
            radio.setActionCommand(actionsScroll[i].getCommand());
            radio.addActionListener(this);
            popupMouseScroll.add(radio);
            groupButtons.add(radio);
        }

        return popupMouseScroll;
    }

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
                    changeButtonState((DropDownButton) pop.getInvoker(), pop.getLabel(), item.getActionCommand());
                    // AbstractProperties.setProperty("mouse_" + pop.getLabel(), item.getActionCommand());
                }
            }
        }
    }

    private void displayMeasureToobar() {
        final String measCmd = ActionW.MEASURE.getCommand();
        int active = eventManager.getMouseActions().getActiveButtons();
        if (measCmd.equals(mouseLeft.getActionCommand())
            || (((active & InputEvent.BUTTON2_DOWN_MASK) == InputEvent.BUTTON2_DOWN_MASK) && measCmd.equals(mouseMiddle
                .getActionCommand()))
            || (((active & InputEvent.BUTTON3_DOWN_MASK) == InputEvent.BUTTON3_DOWN_MASK) && measCmd.equals(mouseRight
                .getActionCommand()))) {
            measureToolBar.setVisible(true);
            measureToolBar.initialize();
        } else {
            measureToolBar.setVisible(false);
        }
        revalidate();
        repaint();
    }

    public void changeButtonState(DropDownButton button, String type, String action) {
        Icon icon = null;
        try {
            icon = new DropButtonIcon(new ImageIcon(MouseActions.class.getResource("/icon/32x32/mouse-" + type + "-" //$NON-NLS-1$ //$NON-NLS-2$
                + action + ".png"))); //$NON-NLS-1$
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }
        button.setIcon(icon);
        button.setActionCommand(action);
        displayMeasureToobar();
    }

    public final static ActionW getNextCommand(ActionW[] actions, String command) {
        if (actions != null && actions.length > 0) {
            int index = 0;
            for (int i = 0; i < actions.length; i++) {
                if (actions[i].getCommand().equals(command)) {
                    index = i == actions.length - 1 ? 0 : i + 1;
                }
            }
            return actions[index];
        }
        return null;
    }
}
