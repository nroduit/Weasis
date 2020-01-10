/*******************************************************************************
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.weasis.core.ui.editor.image.dockable;

import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JPopupMenu;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.SwingConstants;

import org.weasis.core.api.gui.util.DropDownButton;
import org.weasis.core.api.gui.util.JSliderW;
import org.weasis.core.api.gui.util.SliderChangeListener;
import org.weasis.core.ui.Messages;
import org.weasis.core.ui.docking.PluginTool;
import org.weasis.core.ui.util.WtoolBar;

import bibliothek.gui.dock.common.CLocation;
import bibliothek.gui.dock.common.mode.ExtendedMode;

public abstract class MiniTool extends PluginTool implements ActionListener {

    public static final String BUTTON_NAME = Messages.getString("MiniToolDockable.title"); //$NON-NLS-1$

    private SliderChangeListener currentAction;
    private final JSliderW slider;
    private boolean vertical = true;

    public MiniTool(String pluginName) {
        super(BUTTON_NAME, pluginName, POSITION.EAST, ExtendedMode.NORMALIZED, PluginTool.Type.TOOL, 5);
        // TODO display a button to minimize or do not display the tab
        dockable.setTitleShown(false);
        setDockableWidth(32);
        currentAction = getActions()[0];
        slider = createSlider(currentAction, vertical);
        jbInit();
    }

    private void jbInit() {
        setLayout(new BoxLayout(this, vertical ? BoxLayout.Y_AXIS : BoxLayout.X_AXIS));

        Dimension dim = new Dimension(5, 5);
        add(Box.createRigidArea(dim));
        final DropDownButton button = new DropDownButton("Mini", currentAction.getActionW().getSmallDropButtonIcon()) { //$NON-NLS-1$

            @Override
            protected JPopupMenu getPopupMenu() {
                return getPopupMenuScroll(this);
            }
        };
        button.setToolTipText(Messages.getString("MiniToolDockable.change")); //$NON-NLS-1$
        WtoolBar.installButtonUI(button);
        WtoolBar.configureButton(button);

        button.setAlignmentY(CENTER_ALIGNMENT);
        button.setAlignmentX(CENTER_ALIGNMENT);
        add(button);
        add(Box.createRigidArea(dim));
        slider.setAlignmentY(CENTER_ALIGNMENT);
        slider.setAlignmentX(CENTER_ALIGNMENT);
        add(slider);
        add(Box.createRigidArea(dim));

    }

    public abstract SliderChangeListener[] getActions();

    public static JSliderW createSlider(final SliderChangeListener action, boolean vertical) {
        JSliderW slider = new JSliderW(action.getSliderMin(), action.getSliderMax(), action.getSliderValue());
        slider.setdisplayValueInTitle(false);
        slider.setInverted(vertical);
        slider.setOrientation(vertical ? SwingConstants.VERTICAL : SwingConstants.HORIZONTAL);
        slider.setPaintTicks(true);
        slider.setPreferredSize(new Dimension(35, 250));
        slider.setShowLabels(false);
        action.registerActionState(slider);
        return slider;
    }

    @Override
    protected void changeToolWindowAnchor(CLocation clocation) {
        int w = getWidth();
        int h = getHeight();
        if (w != 0 && h != 0) {
            vertical = h >= w;
            if (vertical != slider.getInverted()) {
                setLayout(new BoxLayout(this, vertical ? BoxLayout.Y_AXIS : BoxLayout.X_AXIS));
                slider.getParent()
                    .setLayout(new BoxLayout(slider.getParent(), vertical ? BoxLayout.Y_AXIS : BoxLayout.X_AXIS));
                slider.setInverted(vertical);
                slider.setOrientation(vertical ? SwingConstants.VERTICAL : SwingConstants.HORIZONTAL);
                slider.revalidate();
                slider.repaint();
            }
        }
    }

    private JPopupMenu getPopupMenuScroll(DropDownButton dropButton) {
        String type = dropButton.getType();
        JPopupMenu popupMouseScroll = new JPopupMenu(type);
        popupMouseScroll.setInvoker(dropButton);
        ButtonGroup groupButtons = new ButtonGroup();
        SliderChangeListener[] actions = getActions();
        for (int i = 0; i < actions.length; i++) {
            JRadioButtonMenuItem radio = new JRadioButtonMenuItem(actions[i].toString(),
                actions[i].getActionW().getSmallIcon(), actions[i].equals(currentAction));
            radio.setActionCommand(Integer.toString(i));
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

                SliderChangeListener newAction = getAction(e.getActionCommand());
                if (newAction == null || currentAction == newAction) {
                    return;
                }
                if (currentAction != null) {
                    currentAction.unregisterActionState(slider);
                }
                newAction.registerActionState(slider);
                // SliderChangeListener.setSliderLabelValues(slider, newAction.getMin(), newAction.getMax());

                currentAction = newAction;

                JPopupMenu pop = (JPopupMenu) item.getParent();
                if (pop.getInvoker() instanceof DropDownButton) {
                    ((DropDownButton) pop.getInvoker()).setIcon(currentAction.getActionW().getSmallDropButtonIcon());
                }
            }
        }
    }

    private SliderChangeListener getAction(String actionCommand) {
        try {
            int index = Integer.parseInt(actionCommand);
            SliderChangeListener[] actions = getActions();
            if (index >= 0 && index < actions.length) {
                return actions[index];
            }
        } catch (NumberFormatException e) {
        }
        return null;
    }

}
