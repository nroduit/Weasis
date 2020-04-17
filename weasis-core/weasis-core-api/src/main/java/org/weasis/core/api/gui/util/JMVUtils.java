/*******************************************************************************
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.weasis.core.api.gui.util;

import java.awt.Color;
import java.awt.Component;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.GraphicsEnvironment;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.text.ParseException;
import java.util.Objects;

import javax.swing.AbstractAction;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFormattedTextField;
import javax.swing.JFormattedTextField.AbstractFormatterFactory;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPopupMenu;
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
import javax.swing.plaf.basic.BasicComboPopup;
import javax.swing.table.TableColumn;
import javax.swing.table.TableModel;
import javax.swing.text.DefaultFormatterFactory;
import javax.swing.text.Style;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.StyleSheet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.Messages;
import org.weasis.core.api.service.BundleTools;
import org.weasis.core.util.StringUtil;

/**
 * The Class JMVUtils.
 */
public class JMVUtils {
    private static final Logger LOGGER = LoggerFactory.getLogger(JMVUtils.class);

    public static final Color TREE_BACKROUND = (Color) javax.swing.UIManager.get("Tree.background"); //$NON-NLS-1$
    public static final Color TREE_SELECTION_BACKROUND = (Color) javax.swing.UIManager.get("Tree.selectionBackground"); //$NON-NLS-1$

    private JMVUtils() {
        super();
    }

    /**
     * @deprecated use LangUtil instead
     */
    @Deprecated
    public static boolean getNULLtoFalse(Object val) {
        return Boolean.TRUE.equals(val);
    }

    /**
     * @deprecated use LangUtil instead
     */
    @Deprecated
    public static boolean getNULLtoTrue(Object val) {
        if (val instanceof Boolean) {
            return ((Boolean) val).booleanValue();
        }
        return true;
    }

    public static void setPreferredWidth(Component component, int width, int minWidth) {
        Dimension dim = component.getPreferredSize();
        dim.width = width;
        component.setPreferredSize(dim);
        dim = component.getMinimumSize();
        dim.width = minWidth;
        component.setMinimumSize(dim);
    }

    public static void setPreferredWidth(Component component, int width) {
        setPreferredWidth(component, width, 50);
    }

    public static void setPreferredHeight(Component component, int height) {
        Dimension dim = component.getPreferredSize();
        dim.height = height;
        component.setPreferredSize(dim);
        dim = component.getMinimumSize();
        dim.height = 50;
        component.setMinimumSize(dim);
    }

    public static void showCenterScreen(Window window) {
        try {
            Rectangle bound = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice()
                .getDefaultConfiguration().getBounds();
            window.setLocation(bound.x + (bound.width - window.getWidth()) / 2,
                bound.y + (bound.height - window.getHeight()) / 2);
        } catch (Exception e) {
            LOGGER.error("Cannot center the window to the screen", e); //$NON-NLS-1$
        }
        window.setVisible(true);
    }

    public static void showCenterScreen(Window window, Component parent) {
        if (parent == null) {
            showCenterScreen(window);
        } else {
            Dimension sSize = parent.getSize();
            Dimension wSize = window.getSize();
            Point p = parent.getLocationOnScreen();
            window.setLocation(p.x + ((sSize.width - wSize.width) / 2), p.y + ((sSize.height - wSize.height) / 2));
            window.setVisible(true);
        }
    }

    public static void formatTableHeaders(JTable table, int alignment) {
        TableHeaderRenderer renderer = new TableHeaderRenderer();
        renderer.setHorizontalAlignment(alignment);
        for (int i = 0; i < table.getColumnCount(); i++) {
            TableColumn col = table.getColumnModel().getColumn(i);
            col.setHeaderRenderer(renderer);
        }
    }

    public static void formatTableHeaders(JTable table, int alignment, int columnSize) {
        TableHeaderRenderer renderer = new TableHeaderRenderer();

        renderer.setHorizontalAlignment(alignment);
        for (int i = 0; i < table.getColumnCount(); i++) {
            TableColumn col = table.getColumnModel().getColumn(i);
            col.setHeaderRenderer(renderer);
            col.setPreferredWidth(columnSize);
        }
    }

    public static String[] getColumnNames(TableModel model) {
        if (model == null) {
            return new String[0];
        }
        String[] names = new String[model.getColumnCount()];
        for (int i = 0; i < names.length; i++) {
            names[i] = model.getColumnName(i);
        }
        return names;
    }

    public static void addChangeListener(JSlider slider, ChangeListener listener) {
        ChangeListener[] listeners = slider.getChangeListeners();
        for (int i = 0; i < listeners.length; i++) {
            if (listener == listeners[i]) {
                return;
            }
        }
        slider.addChangeListener(listener);
    }

    private static void addCheckActionToJFormattedTextField(final JFormattedTextField textField) {
        textField.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "check"); //$NON-NLS-1$
        textField.getActionMap().put("check", new AbstractAction() { //$NON-NLS-1$

            @Override
            public void actionPerformed(ActionEvent e) {
                try {
                    textField.commitEdit(); // so use it.
                    textField.postActionEvent(); // stop editing //for DefaultCellEditor
                } catch (java.text.ParseException pe) {
                    LOGGER.error("Exception when commit value in {}", textField.getClass().getName(), pe); //$NON-NLS-1$
                }
                textField.setValue(textField.getValue());
            }
        });
    }

    public static void addCheckAction(final JFormattedTextField textField) {
        textField.setHorizontalAlignment(SwingConstants.RIGHT);
        addCheckActionToJFormattedTextField(textField);
    }

    public static void setNumberModel(JSpinner spin, int val, int min, int max, int delta) {
        spin.setModel(new SpinnerNumberModel(val < min ? min : val > max ? max : val, min, max, delta));
        final JFormattedTextField ftf = ((JSpinner.DefaultEditor) spin.getEditor()).getTextField();
        addCheckActionToJFormattedTextField(ftf);
    }

    public static void formatCheckAction(JSpinner spin) {
        final JFormattedTextField ftf = ((JSpinner.DefaultEditor) spin.getEditor()).getTextField();
        addCheckActionToJFormattedTextField(ftf);
    }

    public static Number getFormattedValue(JFormattedTextField textField) {
        AbstractFormatterFactory formatter = textField.getFormatterFactory();
        if (formatter instanceof DefaultFormatterFactory
            && textField.getFormatter().equals(((DefaultFormatterFactory) formatter).getEditFormatter())) {
            try {
                // to be sure that the value is commit (by default it is when the JFormattedTextField losing the focus)
                textField.commitEdit();
            } catch (ParseException pe) {
                LOGGER.error("Exception when commit value in {}", textField.getClass().getName(), pe); //$NON-NLS-1$
            }
        }
        Number val = null;
        try {
            val = (Number) textField.getValue();
        } catch (Exception e) {
            LOGGER.error("Cannot get number form textField", e); //$NON-NLS-1$
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
        if ("CDE/Motif".equalsIgnoreCase(look)) { //$NON-NLS-1$
            return new Dimension(38, 34);
        } else if (look.startsWith("GTK")) { //$NON-NLS-1$
            return new Dimension(28, 28);
        } else {
            return new Dimension(22, 22);
        }
    }

    public static Dimension getBigIconButtonSize() {
        String look = UIManager.getLookAndFeel().getName();
        if ("CDE/Motif".equalsIgnoreCase(look)) { //$NON-NLS-1$
            return new Dimension(46, 42);
        } else if ("Mac OS X Aqua".equalsIgnoreCase(look) || look.startsWith("GTK")) { //$NON-NLS-1$ //$NON-NLS-2$
            return new Dimension(36, 36);
        } else {
            return new Dimension(34, 34);
        }
    }

    public static Dimension getBigIconToogleButtonSize() {
        String look = UIManager.getLookAndFeel().getName();
        if ("Mac OS X Aqua".equalsIgnoreCase(look) || look.startsWith("GTK")) { //$NON-NLS-1$ //$NON-NLS-2$
            return new Dimension(36, 36);
        } else {
            return new Dimension(30, 30);
        }
    }
    
    public static JButton createHelpButton(final String topic, boolean small) {
        JButton jButtonHelp;
        if (small) {
          jButtonHelp =
              new JButton(new ImageIcon(JMVUtils.class.getResource("/icon/16x16/help.png"))); //$NON-NLS-1$
          jButtonHelp.setPreferredSize(getSmallIconButtonSize());
        } else {
          jButtonHelp =
              new JButton(new ImageIcon(JMVUtils.class.getResource("/icon/22x22/help.png"))); //$NON-NLS-1$
          jButtonHelp.setPreferredSize(getBigIconButtonSize());
        }
        jButtonHelp.addActionListener(
            e -> {
              try {
                JMVUtils.openInDefaultBrowser(
                    jButtonHelp,
                    new URL(BundleTools.SYSTEM_PREFERENCES.getProperty("weasis.help.online") + topic)); //$NON-NLS-1$
              } catch (MalformedURLException e1) {
                LOGGER.error("Cannot open online help", e1); //$NON-NLS-1$
              }
            });

        return jButtonHelp;
      }

    public static HTMLEditorKit buildHTMLEditorKit(JComponent component) {
        Objects.requireNonNull(component);
        HTMLEditorKit kit = new HTMLEditorKit();
        StyleSheet ss = kit.getStyleSheet();
        ss.addRule("body {font-family:sans-serif;font-size:12pt;background-color:#" //$NON-NLS-1$
            + Integer.toHexString((component.getBackground().getRGB() & 0xffffff) | 0x1000000).substring(1) + ";color:#" //$NON-NLS-1$
            + Integer.toHexString((component.getForeground().getRGB() & 0xffffff) | 0x1000000).substring(1)
            + ";margin:3;font-weight:normal;}"); //$NON-NLS-1$
        return kit;
    }

    public static void addStylesToHTML(StyledDocument doc) {
        // Initialize some styles.
        Style regular = doc.getStyle("default"); //$NON-NLS-1$
        Style s = doc.addStyle("title", regular); //$NON-NLS-1$
        StyleConstants.setFontSize(s, 16);
        StyleConstants.setBold(s, true);
        s = doc.addStyle("bold", regular); //$NON-NLS-1$
        StyleConstants.setBold(s, true);
        StyleConstants.setFontSize(s, 12);
        s = doc.addStyle("small", regular); //$NON-NLS-1$
        StyleConstants.setFontSize(s, 10);
        s = doc.addStyle("large", regular); //$NON-NLS-1$
        StyleConstants.setFontSize(s, 14);
        s = doc.addStyle("italic", regular); //$NON-NLS-1$
        StyleConstants.setFontSize(s, 12);
        StyleConstants.setItalic(s, true);
    }

    public static int getMaxLength(Rectangle bounds) {
        if (bounds.width < bounds.height) {
            return bounds.height;
        }
        return bounds.width;
    }

    public static void addTooltipToComboList(final JComboBox<?> combo) {
        Object comp = combo.getUI().getAccessibleChild(combo, 0);
        if (comp instanceof BasicComboPopup) {
            final BasicComboPopup popup = (BasicComboPopup) comp;
            popup.getList().getSelectionModel().addListSelectionListener(e -> {
                if (!e.getValueIsAdjusting()) {
                    ListSelectionModel model = (ListSelectionModel) e.getSource();
                    int first = model.getMinSelectionIndex();
                    if (first >= 0) {
                        Object item = combo.getItemAt(first);
                        ((JComponent) combo.getRenderer()).setToolTipText(item.toString());
                    }
                }
            });
        }
    }

    public static void openInDefaultBrowser(Component parent, URL url) {
        if (url != null) {
            if (AppProperties.OPERATING_SYSTEM.startsWith("linux")) { //$NON-NLS-1$
                try {
                    String cmd = String.format("xdg-open %s", url); //$NON-NLS-1$
                    Runtime.getRuntime().exec(cmd);
                } catch (IOException e) {
                    LOGGER.error("Cannot open URL to the system browser", e); //$NON-NLS-1$
                }
            } else if (Desktop.isDesktopSupported()) {
                final Desktop desktop = Desktop.getDesktop();
                if (desktop.isSupported(Desktop.Action.BROWSE)) {
                    try {
                        desktop.browse(url.toURI());
                    } catch (IOException | URISyntaxException e) {
                        LOGGER.error("Cannot open URL to the desktop browser", e); //$NON-NLS-1$
                    }
                }
            } else {
                JOptionPane.showMessageDialog(parent,
                    Messages.getString("JMVUtils.browser") + StringUtil.COLON_AND_SPACE + url, //$NON-NLS-1$
                    Messages.getString("JMVUtils.error"), //$NON-NLS-1$
                    JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    public static HyperlinkListener buildHyperlinkListener() {
        return e -> {
            JTextPane pane = (JTextPane) e.getSource();
            if (e.getEventType() == HyperlinkEvent.EventType.ENTERED) {
                pane.setToolTipText(e.getDescription());
            } else if (e.getEventType() == HyperlinkEvent.EventType.EXITED) {
                pane.setToolTipText(null);
            } else if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
                Component parent = e.getSource() instanceof Component ? (Component) e.getSource() : null;
                openInDefaultBrowser(parent, e.getURL());
            }
        };
    }

    public static void addItemToMenu(JMenu menu, JMenuItem item) {
        if (menu != null && item != null) {
            menu.add(item);
        }
    }

    public static void addItemToMenu(JPopupMenu menu, Component item) {
        if (menu != null && item != null) {
            menu.add(item);
        }
    }

    public static Color getComplementaryColor(Color color) {
        float[] c = new float[3];
        Color.RGBtoHSB(color.getRed(), color.getGreen(), color.getBlue(), c);
        return Color.getHSBColor(c[0] + 0.5F, c[1], c[2]);
    }

    public static String getValueRGBasText(Color color) {
        if (color == null) {
            return ""; //$NON-NLS-1$
        }
        return "red = " + color.getRed() + ", green = " + color.getGreen() + ", blue = " + color.getBlue(); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
    }

    public static String getValueRGBasText2(Color color) {
        if (color == null) {
            return ""; //$NON-NLS-1$
        }
        return color.getRed() + ":" + color.getGreen() + ":" + color.getBlue(); //$NON-NLS-1$ //$NON-NLS-2$
    }

}
