/*
 * VLDocking Framework 3.0 Copyright VLSOLUTIONS, 2004-2009
 * 
 * email : info at vlsolutions.com ------------------------------------------------------------------------ This
 * software is distributed under the LGPL license
 * 
 * The fact that you are presently reading this and using this class means that you have had knowledge of the LGPL
 * license and that you accept its terms.
 * 
 * You can read the complete license here :
 * 
 * http://www.gnu.org/licenses/lgpl.html
 */

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

public class WtoolBar extends JPanel {

    public enum TYPE {
        main, explorer, tool
    };

    public final static Dimension SEPARATOR_2x24 = new Dimension(2, 24);

    private final TYPE type;
    private final String barName;

    private boolean rolloverBorderPainted = true;
    private boolean rolloverContentAreaFilled = true;
    private boolean useCustomUI = true;

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

    /**
     * Constructs a toolbar with the given name.
     * <p>
     * The name is used when reading/writing XML configuration. It must not be null if you use this feature.
     */
    public WtoolBar(String barName, TYPE type) {
        FlowLayout flowLayout = (FlowLayout) getLayout();
        flowLayout.setVgap(0);
        flowLayout.setHgap(0);
        flowLayout.setAlignment(FlowLayout.LEADING);
        this.barName = barName;
        this.type = type;
        this.setAlignmentX(LEFT_ALIGNMENT);
        this.setAlignmentY(TOP_ALIGNMENT);
        setOpaque(false);
        addSeparator(SEPARATOR_2x24);
    }

    public TYPE getType() {
        return type;
    }

    public void addSeparator(Dimension dim) {
        JSeparator s = new JSeparator(JSeparator.VERTICAL);
        s.setPreferredSize(dim);
        add(s);
    }

    /** Overriden to track AbstractButton added */
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
            button.putClientProperty("substancelaf.componentFlat", Boolean.TRUE); //$NON-NLS-1$
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
     * This method can be overriden to replace the provided "look and feel" which uses the follwing configuration :
     * <ul>
     * <li>install a VLButtonUI
     * <li>set 2 pixels margins
     * <li>set a ToolBarButtonBorder.
     * </ul>
     */
    public static void installButtonUI(AbstractButton button) {
        button.setMargin(new Insets(2, 2, 2, 2));
        button.setUI(new VLButtonUI());
        button.setBorder(new ToolBarButtonBorder());
    }

    /**
     * Used internally to add a mouse listener to the button.
     * <p>
     * Can be overriden to implement custom event handling.
     */
    public void installMouseHandler(AbstractButton button) {
        button.addMouseListener(buttonMouseHandler);
    }

    /**
     * This method is invoked upon adding a button to the toolbar. It can be overriden to provide another look or feel.
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
     * Default value is <b>false</b> to accomodate with VLButtonUI which paints itself the button interiors.
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

    public String getBarName() {
        return barName;
    }

}