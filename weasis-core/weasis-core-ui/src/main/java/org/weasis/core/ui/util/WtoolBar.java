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
import java.util.List;
import java.util.Properties;

import javax.swing.AbstractButton;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JSeparator;

import org.osgi.service.prefs.Preferences;
import org.weasis.core.api.service.BundlePreferences;
import org.weasis.core.api.service.BundleTools;

public class WtoolBar extends JPanel implements Toolbar {

    public final static String ALL_BUNDLE = "weasis"; //$NON-NLS-1$
    public final static String ALL = "all"; //$NON-NLS-1$

    public enum TYPE {
        main, explorer, tool, empty
    };

    private final TYPE type;
    private final String barName;

    private int index = 100;
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
     * 
     * @param i
     */
    public WtoolBar(String barName, TYPE type, int index) {
        FlowLayout flowLayout = (FlowLayout) getLayout();
        flowLayout.setVgap(0);
        flowLayout.setHgap(0);
        flowLayout.setAlignment(FlowLayout.LEADING);
        this.barName = barName;
        this.type = type;
        this.index = index;
        this.setAlignmentX(LEFT_ALIGNMENT);
        this.setAlignmentY(TOP_ALIGNMENT);
        setOpaque(false);
        addSeparator(SEPARATOR_2x24);
    }

    @Override
    public TYPE getType() {
        return type;
    }

    @Override
    public int getIndex() {
        return index;
    }

    @Override
    public void setIndex(int index) {
        this.index = index;
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

    @Override
    public String getBarName() {
        return barName;
    }

    @Override
    public final WtoolBar getComponent() {
        return this;
    }

    public static void applyPreferences(List<Toolbar> toolbars, Preferences prefs, String bundleName, String className) {
        if (toolbars != null && prefs != null && bundleName != null && className != null) {
            // Remove prefs of Weasis 1.x
            try {
                if (prefs.nodeExists("toolbars")) { //$NON-NLS-1$
                    Preferences oldPref = prefs.node("toolbars"); //$NON-NLS-1$
                    oldPref.removeNode();
                }
            } catch (Exception e) {
                // Do nothing
            }
            Preferences prefNode = prefs.node(className).node("toolbars"); //$NON-NLS-1$
            for (Toolbar tb : toolbars) {
                String barName = tb.getClass().getSimpleName().toLowerCase();
                String key = "visible"; //$NON-NLS-1$
                Preferences node = prefNode.node(barName);
                String valString = node.get(key, null);
                // If not specify, value is true
                boolean val = true;
                if (valString == null) {
                    val = getBooleanProperty(BundleTools.SYSTEM_PREFERENCES, bundleName, className, barName, key, val);
                } else if (valString.equalsIgnoreCase("false")) { //$NON-NLS-1$
                    val = false;
                }
                tb.setEnabled(val);

                key = "index"; //$NON-NLS-1$
                valString = node.get(key, null);
                // If not specify, value is true
                int index = tb.getIndex();
                if (valString == null) {
                    index = getIntProperty(BundleTools.SYSTEM_PREFERENCES, bundleName, className, barName, key, index);
                } else {
                    try {
                        index = Integer.parseInt(valString);
                    } catch (NumberFormatException ignore) {
                        // return the default value
                    }
                }
                tb.setIndex(index);
            }
        }
    }

    public static void savePreferences(List<Toolbar> toolbars, Preferences prefs) {
        if (toolbars != null && prefs != null) {
            Preferences prefNode = prefs.node("toolbars"); //$NON-NLS-1$
            for (Toolbar tb : toolbars) {
                String cname = tb.getClass().getSimpleName().toLowerCase();
                Preferences node = prefNode.node(cname);
                BundlePreferences.putBooleanPreferences(node, "visible", tb.isEnabled()); //$NON-NLS-1$
                BundlePreferences.putIntPreferences(node, "index", tb.getIndex()); //$NON-NLS-1$
            }
        }
    }

    public static boolean getBooleanProperty(Properties props, String bundleName, String className, String barName,
        String key, boolean def) {
        if (props != null && bundleName != null && className != null && key != null) {
            for (String bundle : new String[] { bundleName, ALL_BUNDLE }) {
                for (String cl : new String[] { className, ALL }) {
                    StringBuffer buf = new StringBuffer(bundle);
                    buf.append('.');
                    buf.append(cl);
                    buf.append('.');
                    buf.append(barName);
                    buf.append('.');
                    buf.append(key);
                    final String value = props.getProperty(buf.toString());
                    if (value != null) {
                        if (value.equalsIgnoreCase("true")) { //$NON-NLS-1$
                            return true;
                        } else if (value.equalsIgnoreCase("false")) { //$NON-NLS-1$
                            return false;
                        }
                    }
                }
            }
        }
        return def;
    }

    private static int getIntProperty(Properties props, String bundleName, String className, String barName,
        String key, int def) {
        if (props != null && bundleName != null && className != null && key != null) {
            for (String bundle : new String[] { bundleName, ALL_BUNDLE }) {
                for (String cl : new String[] { className, ALL }) {
                    StringBuffer buf = new StringBuffer(bundle);
                    buf.append('.');
                    buf.append(cl);
                    buf.append('.');
                    buf.append(barName);
                    buf.append('.');
                    buf.append(key);
                    final String value = props.getProperty(buf.toString());
                    if (value != null) {
                        try {
                            return Integer.parseInt(value);
                        } catch (NumberFormatException ignore) {
                            // return the default value
                        }
                    }
                }
            }
        }
        return def;
    }
}