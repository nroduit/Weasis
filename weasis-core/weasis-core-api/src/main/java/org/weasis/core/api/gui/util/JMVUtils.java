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
package org.weasis.core.api.gui.util;

import java.awt.Color;
import java.awt.Component;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Hashtable;

import javax.media.jai.PlanarImage;
import javax.swing.AbstractAction;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFormattedTextField;
import javax.swing.JFormattedTextField.AbstractFormatterFactory;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JSlider;
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.JTextPane;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.UIManager;
import javax.swing.event.ChangeListener;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.plaf.basic.BasicComboPopup;
import javax.swing.table.TableColumn;
import javax.swing.table.TableModel;
import javax.swing.text.DefaultFormatterFactory;
import javax.swing.text.MutableAttributeSet;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.Style;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyleContext;
import javax.swing.text.StyledDocument;
import javax.swing.text.TabSet;
import javax.swing.text.TabStop;

import org.weasis.core.api.Messages;

/**
 * The Class JMVUtils.
 * 
 * @author Nicolas Roduit
 */
public class JMVUtils {

    public final static Color lightGray = new Color(237, 237, 237);

    public static int getNumberOfInvolvedTiles(PlanarImage img, Rectangle bound) {
        int maxTileIndexX = img.getMinTileX() + img.getNumXTiles();
        int maxTileIndexY = img.getMinTileY() + img.getNumYTiles();
        int nbTiles = 0;
        // Loop over tiles within the clipping region
        for (int tj = img.getMinTileY(); tj < maxTileIndexY; tj++) {
            for (int ti = img.getMinTileX(); ti < maxTileIndexX; ti++) {
                if (bound == null || bound.intersects(img.getTileRect(ti, tj))) {
                    nbTiles++;
                }
            }
        }
        return nbTiles;
    }

    public static int getNumberOfInvolvedTilesOnXaxis(PlanarImage img, Rectangle area) {
        int maxTileIndexX = img.getMinTileX() + img.getNumXTiles();
        int nbTiles = 0;
        Rectangle bound = area.getBounds();
        bound.y = img.tileYToY(img.getMinTileY());
        // Loop over tiles within the clipping region
        for (int ti = img.getMinTileX(), tj = img.getMinTileY(); ti < maxTileIndexX; ti++) {
            if (bound == null || bound.intersects(img.getTileRect(ti, tj))) {
                nbTiles++;
            }
        }
        return nbTiles;
    }

    public static ArrayList convertHashtableToArrayList(Hashtable list) {
        ArrayList list2 = new ArrayList(list.size());
        Enumeration enumVal = list.elements();
        while (enumVal.hasMoreElements()) {
            list2.add(enumVal.nextElement());
        }
        return list2;
    }

    public static void setPreferredWidth(JComponent component, int width, int minWidth) {
        Dimension dim = component.getPreferredSize();
        dim.width = width;
        component.setPreferredSize(dim);
        dim = component.getMinimumSize();
        dim.width = minWidth;
        component.setMinimumSize(dim);
    }

    public static void setPreferredWidth(JComponent component, int width) {
        setPreferredWidth(component, width, 50);
    }

    public static void setPreferredHeight(JComponent component, int height) {
        Dimension dim = component.getPreferredSize();
        dim.height = height;
        component.setPreferredSize(dim);
        dim = component.getMinimumSize();
        dim.height = 50;
        component.setMinimumSize(dim);
    }

    // public static void setPreferredWidth(JSlider component, int width) {
    // Dimension dim = component.getPreferredSize();
    // dim.width = width;
    // String look = UIManager.getLookAndFeel().getName();
    // if (look.startsWith("GTK")) {
    // dim.height += 20; // pour corriger les problï¿½mes d'affichage en mode ou les valeurs sont dï¿½sactivï¿½es
    // }
    // component.setPreferredSize(dim);
    // }

    public static void showCenterScreen(Window window) {
        try {
            Rectangle bound =
                GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice().getDefaultConfiguration()
                    .getBounds();
            window.setLocation(bound.x + (bound.width - window.getWidth()) / 2,
                bound.y + (bound.height - window.getHeight()) / 2);
        } catch (Exception e) {
            e.printStackTrace();
        }
        window.setVisible(true);
    }

    public static void showCenterScreen(Window window, Window parent) {
        Dimension sSize = parent.getSize();
        Dimension wSize = window.getSize();
        window.setLocation(((sSize.width - wSize.width) / 2), ((sSize.height - wSize.height) / 2));
        window.setVisible(true);
    }

    public static void formatTableHeaders(JTable table, int alignement) {
        TableHaederRenderer renderer = new TableHaederRenderer();
        renderer.setHorizontalAlignment(alignement);
        for (int i = 0; i < table.getColumnCount(); i++) {
            TableColumn col = table.getColumnModel().getColumn(i);
            col.setHeaderRenderer(renderer);
        }
    }

    public static void formatTableHeaders(JTable table, int alignement, int columnSize) {
        TableHaederRenderer renderer = new TableHaederRenderer();

        renderer.setHorizontalAlignment(alignement);
        for (int i = 0; i < table.getColumnCount(); i++) {
            TableColumn col = table.getColumnModel().getColumn(i);
            col.setHeaderRenderer(renderer);
            col.setPreferredWidth(columnSize);
        }
    }

    public static String[] getColumnNames(TableModel model) {
        if (model == null)
            return new String[0];
        String[] names = new String[model.getColumnCount()];
        for (int i = 0; i < names.length; i++) {
            names[i] = model.getColumnName(i);
        }
        return names;
    }

    public static void setList(JComboBox jComboBox, Object first, Object[] obj) {
        jComboBox.removeAllItems();
        jComboBox.addItem(first);
        for (int i = 0; i < obj.length; i++) {
            jComboBox.addItem(obj[i]);
        }
    }

    public static void setList(JComboBox jComboBox, Object[] obj, Object last) {
        jComboBox.removeAllItems();
        for (int i = 0; i < obj.length; i++) {
            jComboBox.addItem(obj[i]);
        }
        jComboBox.addItem(last);
    }

    public static void setList(JComboBox jComboBox, java.util.List list) {
        jComboBox.removeAllItems();
        for (int i = 0; i < list.size(); i++) {
            jComboBox.addItem(list.get(i));
        }
    }

    public static void addChangeListener(JSlider slider, ChangeListener listener) {
        ChangeListener[] listeners = slider.getChangeListeners();
        for (int i = 0; i < listeners.length; i++) {
            if (listener == listeners[i])
                return;
        }
        slider.addChangeListener(listener);
    }

    public static void addCheckAction(final JFormattedTextField textField) {
        textField.setHorizontalAlignment(SwingConstants.RIGHT);
        textField.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "check"); //$NON-NLS-1$
        textField.getActionMap().put("check", new AbstractAction() { //$NON-NLS-1$

                @Override
                public void actionPerformed(ActionEvent e) {
                    try {
                        textField.commitEdit(); // so use it.
                        textField.postActionEvent(); // stop editing //for DefaultCellEditor
                    } catch (java.text.ParseException exc) {
                    }
                    textField.setValue(textField.getValue());
                }
            });
    }

    public static void setNumberModel(JSpinner spin, int val, int min, int max, int delta) {
        spin.setModel(new SpinnerNumberModel(val < min ? min : val > max ? max : val, min, max, delta));
        final JFormattedTextField ftf = ((JSpinner.DefaultEditor) spin.getEditor()).getTextField();
        ftf.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "check"); //$NON-NLS-1$
        ftf.getActionMap().put("check", new AbstractAction() { //$NON-NLS-1$

                @Override
                public void actionPerformed(ActionEvent e) {
                    try {
                        ftf.commitEdit(); // so use it.
                    } catch (java.text.ParseException exc) {
                    }
                    ftf.setValue(ftf.getValue());
                }
            });
    }

    public static void formatCheckAction(JSpinner spin) {
        final JFormattedTextField ftf = ((JSpinner.DefaultEditor) spin.getEditor()).getTextField();
        ftf.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "check"); //$NON-NLS-1$
        ftf.getActionMap().put("check", new AbstractAction() { //$NON-NLS-1$

                @Override
                public void actionPerformed(ActionEvent e) {
                    try {
                        ftf.commitEdit(); // so use it.
                    } catch (java.text.ParseException exc) {
                    }
                    ftf.setValue(ftf.getValue());
                }
            });
    }

    public static Number getFormattedValue(JFormattedTextField textField) {
        AbstractFormatterFactory formatter = textField.getFormatterFactory();
        if (formatter instanceof DefaultFormatterFactory
            && textField.getFormatter().equals(((DefaultFormatterFactory) formatter).getEditFormatter())) {
            try {
                // to be sure that the value is commit (by default it is when the JFormattedTextField losing the focus)
                textField.commitEdit();
            } catch (ParseException pe) {
            }
        }
        Number val = null;
        try {
            val = (Number) textField.getValue();
        } catch (Exception ex) {
        }
        return val;
    }

    // A convenience method for creating menu items
    public static JMenuItem menuItem(String label, ActionListener listener, String command, int mnemonic,
        int acceleratorKey) {
        JMenuItem item = new JMenuItem(label);
        item.addActionListener(listener);
        item.setActionCommand(command);
        if (mnemonic != 0) {
            item.setMnemonic((char) mnemonic);
        }
        if (acceleratorKey != 0) {
            item.setAccelerator(KeyStroke.getKeyStroke(acceleratorKey, java.awt.Event.CTRL_MASK));
        }
        return item;
    }

    public static Dimension getSmallIconButtonSize() {
        String look = UIManager.getLookAndFeel().getName();
        if (look.equalsIgnoreCase("CDE/Motif"))
            return new Dimension(38, 34);
        else if (look.startsWith("GTK"))
            return new Dimension(28, 28);
        else
            return new Dimension(22, 22);
    }

    public static Dimension getBigIconButtonSize() {
        String look = UIManager.getLookAndFeel().getName();
        if (look.equalsIgnoreCase("CDE/Motif"))
            return new Dimension(46, 42);
        else if (look.equalsIgnoreCase("Mac OS X Aqua") || look.startsWith("GTK"))
            return new Dimension(36, 36);
        else
            return new Dimension(34, 34);
    }

    public static Dimension getBigIconToogleButtonSize() {
        String look = UIManager.getLookAndFeel().getName();
        if (look.equalsIgnoreCase("Mac OS X Aqua") || look.startsWith("GTK"))
            return new Dimension(36, 36);
        else
            return new Dimension(30, 30);
    }

    public static JButton createHelpButton(boolean small) {
        JButton jButtonHelp;
        if (small) {
            jButtonHelp = new JButton(new ImageIcon(JMVUtils.class.getResource("/com/jmvision/icon16/help2.png"))); //$NON-NLS-1$
            jButtonHelp.setPreferredSize(getSmallIconButtonSize());
        } else {
            jButtonHelp = new JButton(new ImageIcon(JMVUtils.class.getResource("/com/jmvision/icon24/help2.png"))); //$NON-NLS-1$
            jButtonHelp.setPreferredSize(getBigIconButtonSize());
        }
        return jButtonHelp;
    }

    public static void addStylesToDocument(StyledDocument doc, Color textColor) {
        // Initialize some styles.
        final MutableAttributeSet def = new SimpleAttributeSet();
        Style style = StyleContext.getDefaultStyleContext().getStyle(StyleContext.DEFAULT_STYLE);
        Style regular = doc.addStyle("regular", style); //$NON-NLS-1$
        StyleConstants.setFontFamily(def, "SansSerif"); //$NON-NLS-1$
        if (textColor == null) {
            textColor = UIManager.getColor("text"); //$NON-NLS-1$
        }
        StyleConstants.setForeground(def, textColor);
        TabStop[] tabs = new TabStop[1];
        tabs[0] = new TabStop(25.0f, TabStop.ALIGN_LEFT, TabStop.LEAD_NONE);
        StyleConstants.setTabSet(def, new TabSet(tabs));
        doc.setParagraphAttributes(0, Integer.MAX_VALUE, def, true);
        Style s = doc.addStyle("title", regular); //$NON-NLS-1$
        StyleConstants.setFontSize(s, 16);
        StyleConstants.setBold(s, true);
        s = doc.addStyle("bold", regular); //$NON-NLS-1$
        StyleConstants.setBold(s, true);
        s = doc.addStyle("small", regular); //$NON-NLS-1$
        StyleConstants.setFontSize(s, 10);
        s = doc.addStyle("large", regular); //$NON-NLS-1$
        StyleConstants.setFontSize(s, 14);
    }

    public static String getValueRGBasText(Color color) {
        if (color == null)
            return ""; //$NON-NLS-1$
        return "red = " + color.getRed() + ", green = " + color.getGreen() + ", blue = " + color.getBlue(); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
    }

    public static String getValueRGBasText2(Color color) {
        if (color == null)
            return ""; //$NON-NLS-1$
        return color.getRed() + ":" + color.getGreen() + ":" + color.getBlue(); //$NON-NLS-1$ //$NON-NLS-2$
    }

    public static Color getLogoBlue() {
        return new Color(108, 138, 157);
    }

    public static Color getLogoBlue50() {
        return new Color(181, 196, 206);
    }

    public static Color getLogoBlue25() {
        return new Color(218, 226, 230);
    }

    public static Color getLogoBlue15() {
        return new Color(233, 238, 240);
    }

    public static Color getLogoOrange() {
        return new Color(218, 124, 0);
    }

    public static Color getLogoOrange50() {
        return new Color(236, 189, 127);
    }

    public static Color getLogoOrange25() {
        return new Color(246, 222, 191);
    }

    public static Color getLogoOrange15() {
        return new Color(249, 235, 217);
    }

    public static int getMaxLength(Rectangle bounds) {
        if (bounds.width < bounds.height)
            return bounds.height;
        return bounds.width;
    }

    public static boolean textHasContent(String aText) {
        return (aText != null) && (!aText.trim().equals("")); //$NON-NLS-1$
    }

    public static void addTooltipToComboList(final JComboBox combo) {
        Object comp = combo.getUI().getAccessibleChild(combo, 0);
        if (comp instanceof BasicComboPopup) {
            final BasicComboPopup popup = (BasicComboPopup) comp;
            popup.getList().getSelectionModel().addListSelectionListener(new ListSelectionListener() {

                @Override
                public void valueChanged(ListSelectionEvent e) {
                    if (!e.getValueIsAdjusting()) {
                        ListSelectionModel model = (ListSelectionModel) e.getSource();
                        int first = model.getMinSelectionIndex();
                        if (first >= 0) {
                            Object item = combo.getItemAt(first);
                            ((JComponent) combo.getRenderer()).setToolTipText(item.toString());
                        }
                    }
                }
            });
        }
    }

    public static void OpenInDefaultBrowser(Component parent, URL url) {
        if (url != null) {
            if (AbstractProperties.OPERATING_SYSTEM.startsWith("linux")) { //$NON-NLS-1$
                try {
                    String cmd = String.format("xdg-open %s", url); //$NON-NLS-1$
                    Runtime.getRuntime().exec(cmd);
                } catch (IOException e1) {
                    e1.printStackTrace();
                }
            } else if (Desktop.isDesktopSupported()) {
                final Desktop desktop = Desktop.getDesktop();
                if (desktop.isSupported(Desktop.Action.BROWSE)) {
                    try {
                        desktop.browse(url.toURI());

                    } catch (IOException ex) {
                        ex.printStackTrace();
                    } catch (URISyntaxException ex2) {
                        ex2.printStackTrace();
                    }
                }
            } else {
                JOptionPane.showMessageDialog(parent,
                    Messages.getString("JMVUtils.browser") + url, Messages.getString("JMVUtils.error"), //$NON-NLS-1$ //$NON-NLS-2$
                    JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    public static HyperlinkListener buildHyperlinkListener() {
        return new HyperlinkListener() {
            @Override
            public void hyperlinkUpdate(HyperlinkEvent e) {
                JTextPane pane = (JTextPane) e.getSource();
                if (e.getEventType() == HyperlinkEvent.EventType.ENTERED) {
                    pane.setToolTipText(e.getDescription());
                } else if (e.getEventType() == HyperlinkEvent.EventType.EXITED) {
                    pane.setToolTipText(null);
                } else if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
                    Component parent = e.getSource() instanceof Component ? (Component) e.getSource() : null;
                    OpenInDefaultBrowser(parent, e.getURL());
                }
            }
        };
    }
}
