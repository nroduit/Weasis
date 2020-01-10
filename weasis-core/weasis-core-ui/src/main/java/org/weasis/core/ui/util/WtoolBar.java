/*******************************************************************************
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/

package org.weasis.core.ui.util;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Insets;
import java.awt.event.InputEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;

import javax.swing.AbstractButton;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JSeparator;
import javax.swing.SwingConstants;

import org.weasis.core.api.gui.Insertable;

@SuppressWarnings("serial")
public class WtoolBar extends JPanel implements Toolbar {

    public static final Dimension SEPARATOR_2x24 = new Dimension(2, 24);

    private final String barName;

    private int barPosition = 100;
    private boolean rolloverBorderPainted = true;
    private boolean rolloverContentAreaFilled = true;
    private boolean useCustomUI = true;

    private Insertable attachedInsertable;

    private transient MouseListener buttonMouseHandler = new MouseAdapter() {

        @Override
        public void mouseEntered(MouseEvent e) {
            AbstractButton btn = (AbstractButton) e.getSource();
            if ((e.getModifiersEx() & InputEvent.BUTTON1_DOWN_MASK) == 0) {
                if (rolloverBorderPainted) {
                    btn.setBorderPainted(true);
                }
                if (rolloverContentAreaFilled) {
                    btn.setContentAreaFilled(true);
                }
            }
        }

        @Override
        public void mouseExited(MouseEvent e) {
            AbstractButton btn = (AbstractButton) e.getSource();
            btn.setBorderPainted(false);
            btn.setContentAreaFilled(false);
        }
    };

    public WtoolBar(String barName, int position) {
        FlowLayout flowLayout = (FlowLayout) getLayout();
        flowLayout.setVgap(0);
        flowLayout.setHgap(0);
        flowLayout.setAlignment(FlowLayout.LEADING);
        this.barName = barName;
        this.barPosition = position;
        this.setAlignmentX(LEFT_ALIGNMENT);
        this.setAlignmentY(TOP_ALIGNMENT);
        setOpaque(false);
        addSeparator(SEPARATOR_2x24);
    }

    @Override
    public Type getType() {
        return Type.TOOLBAR;
    }

    @Override
    public int getComponentPosition() {
        return barPosition;
    }

    @Override
    public void setComponentPosition(int position) {
        this.barPosition = position;
    }

    public Insertable getAttachedInsertable() {
        return attachedInsertable;
    }

    public void setAttachedInsertable(Insertable attachedInsertable) {
        this.attachedInsertable = attachedInsertable;
    }

    public void addSeparator(Dimension dim) {
        JSeparator s = new JSeparator(SwingConstants.VERTICAL);
        s.setPreferredSize(dim);
        add(s);
    }

    /** Overridden to track AbstractButton added */
    @Override
    public Component add(Component comp) {
        if (comp instanceof AbstractButton) {
            return add((AbstractButton) comp);
        } else {
            return super.add(comp);
        }
    }

    /** Adds a new button to this toolbar */

    public Component add(AbstractButton button) {
        boolean substanceLaf = javax.swing.UIManager.getLookAndFeel().getName().startsWith("Substance"); //$NON-NLS-1$
        if (useCustomUI && !substanceLaf) {
            installButtonUI(button);
        }
        super.add(button);
        if (substanceLaf) {
            button.putClientProperty("substancelaf.internal.FlatLook", Boolean.TRUE); //$NON-NLS-1$
        } else {
            configureButton(button);
            installMouseHandler(button);
        }
        return button;
    }

    /** Adds a new button to this toolbar */

    public Component add(JButton button) {
        // this method is here to maintain backward compatibility
        return add((AbstractButton) button);
    }

    /**
     * Install custom UI for this button : a light rollover effet and a custom rounded/shaded border.
     * <p>
     * This method can be overridden to replace the provided "look and feel" which uses the follwing configuration :
     * <ul>
     * <li>install a VLButtonUI
     * <li>set 2 pixels margins
     * <li>set a ToolBarButtonBorder.
     * </ul>
     */
    public static void installButtonUI(AbstractButton button) {
        button.setMargin(new Insets(2, 2, 2, 2));
        button.setUI(new RolloverButtonUI());
        button.setBorder(new ToolBarButtonBorder());
    }

    /**
     * Used internally to add a mouse listener to the button.
     * <p>
     * Can be overridden to implement custom event handling.
     */

    public void installMouseHandler(AbstractButton button) {
        button.addMouseListener(buttonMouseHandler);
    }

    /**
     * This method is invoked upon adding a button to the toolbar. It can be overridden to provide another look or feel.
     * <p>
     * Default settings are :
     * <ul>
     * <li>setRolloverEnabled(true)
     * <li>setContentAreaFilled(false);
     * <li>setOpaque(false)
     * <li>setBorderPainted(false)
     * </ul>
     */
    public static void configureButton(AbstractButton button) {
        button.setRolloverEnabled(true);
        button.setContentAreaFilled(false);
        button.setOpaque(false);
        button.setBorderPainted(false);
    }

    /**
     * Updates the rolloverBorderPainted property.
     * <p>
     * If true, when one of the toolbar buttons is rolled-over, its border will be shown.
     * <P>
     * DefaultValue is true
     */

    public void setRolloverBorderPainted(boolean painted) {
        this.rolloverBorderPainted = painted;
    }

    /** Returns the state of the rolloverBorderPainted property */

    public boolean isRolloverBorderPainter() {
        return rolloverBorderPainted;
    }

    /**
     * Updates the rolloverContentAreaFilled property.
     * <p>
     * If true, when one of the toolbar buttons is rolled-over, its content will be filled.
     * <p>
     * Default value is <b>false</b> to accommodate with VLButtonUI which paints itself the button interiors.
     *
     */

    public void setRolloverContentAreaFilled(boolean filled) {
        this.rolloverContentAreaFilled = filled;
    }

    /** Returns the value of the rolloverContentAreaFilled property */

    public boolean isRolloverContentAreaFilled() {
        return rolloverContentAreaFilled;
    }

    /**
     * Updates the useCustomUI property.
     * <p>
     * Default value is true.
     * <p>
     * When set to true the installButtonUI() method will be called when a button is added to this toolbar.
     */

    public void setUseCustomUI(boolean useCustomUI) {
        this.useCustomUI = useCustomUI;
    }

    /** Return the value of the useCustomUI property */

    public boolean isUseCustomUI() {
        return useCustomUI;
    }

    @Override
    public String toString() {
        return "WtoolBar " + getName(); //$NON-NLS-1$
    }

    @Override
    public String getComponentName() {
        return barName;
    }

    @Override
    public final WtoolBar getComponent() {
        return this;
    }

    @Override
    public boolean isComponentEnabled() {
        return isEnabled();
    }

    @Override
    public void setComponentEnabled(boolean enabled) {
        if (isComponentEnabled() != enabled) {
            setEnabled(enabled);
        }
    }

}
