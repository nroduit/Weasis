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
package org.weasis.core.ui.editor.image.dockable;

import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.Icon;
import javax.swing.JPopupMenu;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.SwingConstants;

import org.noos.xing.mydoggy.DockedTypeDescriptor;
import org.noos.xing.mydoggy.ToolWindow;
import org.noos.xing.mydoggy.ToolWindowAnchor;
import org.noos.xing.mydoggy.ToolWindowType;
import org.weasis.core.api.gui.util.DropDownButton;
import org.weasis.core.api.gui.util.JSliderW;
import org.weasis.core.api.gui.util.SliderChangeListener;
import org.weasis.core.ui.Messages;
import org.weasis.core.ui.docking.PluginTool;
import org.weasis.core.ui.util.WtoolBar;

public abstract class MiniTool extends PluginTool implements ActionListener {

    public static final String BUTTON_NAME = Messages.getString("MiniToolDockable.title"); //$NON-NLS-1$

    private SliderChangeListener currentAction;
    private final JSliderW slider;

    public MiniTool(String pluginName, Icon icon) {
        super(BUTTON_NAME, pluginName, ToolWindowAnchor.RIGHT, PluginTool.TYPE.tool);
        setDockableWidth(40);
        currentAction = getActions()[0];
        slider = createSlider(currentAction, getAnchor());
        jbInit();
    }

    private void jbInit() {
        boolean vertical = ToolWindowAnchor.RIGHT.equals(getAnchor()) || ToolWindowAnchor.LEFT.equals(getAnchor());
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

    public static JSliderW createSlider(final SliderChangeListener action, ToolWindowAnchor anchor) {
        boolean vertical = ToolWindowAnchor.RIGHT.equals(anchor) || ToolWindowAnchor.LEFT.equals(anchor);
        JSliderW slider = new JSliderW(action.getMin(), action.getMax(), action.getValue());
        slider.setDisplayOnlyValue(true);
        slider.setInverted(vertical);
        slider.setOrientation(vertical ? SwingConstants.VERTICAL : SwingConstants.HORIZONTAL);
        slider.setPreferredSize(new Dimension(35, 250));
        action.registerSlider(slider);
        return slider;
    }

    @Override
    protected void changeToolWindowAnchor(ToolWindowAnchor anchor) {
        boolean vertical = ToolWindowAnchor.RIGHT.equals(anchor) || ToolWindowAnchor.LEFT.equals(anchor);
        setLayout(new BoxLayout(this, vertical ? BoxLayout.Y_AXIS : BoxLayout.X_AXIS));
        slider.getParent().setLayout(new BoxLayout(slider.getParent(), vertical ? BoxLayout.Y_AXIS : BoxLayout.X_AXIS));
        slider.setInverted(vertical);
        slider.setOrientation(vertical ? SwingConstants.VERTICAL : SwingConstants.HORIZONTAL);
        ToolWindow win = getToolWindow();
        if (win != null) {
            DockedTypeDescriptor dockedTypeDescriptor =
                (DockedTypeDescriptor) win.getTypeDescriptor(ToolWindowType.DOCKED);
            int width = this.getDockableWidth();
            if (width > 0) {
                dockedTypeDescriptor.setDockLength(vertical ? width : width + 10);
            }
        }
        slider.revalidate();
        slider.repaint();
    }

    private JPopupMenu getPopupMenuScroll(DropDownButton dropButton) {
        String type = dropButton.getType();
        JPopupMenu popupMouseScroll = new JPopupMenu(type);
        popupMouseScroll.setInvoker(dropButton);
        ButtonGroup groupButtons = new ButtonGroup();
        SliderChangeListener[] actions = getActions();
        for (int i = 0; i < actions.length; i++) {
            JRadioButtonMenuItem radio =
                new JRadioButtonMenuItem(actions[i].toString(), actions[i].getActionW().getSmallIcon(),
                    actions[i].equals(currentAction));
            radio.setActionCommand("" + i); //$NON-NLS-1$
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
                if (currentAction == newAction) {
                    return;
                }
                if (currentAction != null) {
                    currentAction.unregisterSlider(slider);
                }
                if (newAction != null) {
                    newAction.registerSlider(slider);
                    // SliderChangeListener.setSliderLabelValues(slider, newAction.getMin(), newAction.getMax());
                }
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
