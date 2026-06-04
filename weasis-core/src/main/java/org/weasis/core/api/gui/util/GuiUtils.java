/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.api.gui.util;

import com.formdev.flatlaf.FlatIconColors;
import com.formdev.flatlaf.extras.FlatSVGIcon;
import com.formdev.flatlaf.extras.FlatSVGIcon.ColorFilter;
import com.formdev.flatlaf.icons.FlatTreeCollapsedIcon;
import com.formdev.flatlaf.ui.FlatUIUtils;
import com.formdev.flatlaf.util.SystemInfo;
import com.formdev.flatlaf.util.UIScale;
import java.awt.Color;
import java.awt.Component;
import java.awt.ComponentOrientation;
import java.awt.Container;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.Event;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.NumberFormat;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import javax.swing.AbstractAction;
import javax.swing.AbstractButton;
import javax.swing.Box;
import javax.swing.Box.Filler;
import javax.swing.BoxLayout;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFormattedTextField;
import javax.swing.JFormattedTextField.AbstractFormatterFactory;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JSlider;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JTextPane;
import javax.swing.KeyStroke;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.UIManager;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import javax.swing.event.ChangeListener;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import javax.swing.text.DefaultFormatterFactory;
import javax.swing.text.NumberFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.Messages;
import org.weasis.core.api.service.UICore;
import org.weasis.core.api.util.FontItem;
import org.weasis.core.util.StringUtil;

public class GuiUtils {
  private static final Logger LOGGER = LoggerFactory.getLogger(GuiUtils.class);

  public static final String HTML_START = "<html>";

  public static final String HTML_COLOR_START = "<html><font color='"; // NON-NLS

  public static final String HTML_COLOR_PATTERN =
      """
          <html><font color="%s">%s</font></html>
          """;
  public static final String HTML_END = "</html>";
  public static final String HTML_BR = "<br>";

  public static final String NEWLINE = "newline"; // NON-NLS

  /** Enum representing the 9 possible window positions on the screen. */
  public enum WindowPosition {
    CENTER, // Center of the screen
    NW, // Northwest (top-left)
    N, // North (top-center)
    NE, // Northeast (top-right)
    W, // West (middle-left)
    E, // East (middle-right)
    SW, // Southwest (bottom-left)
    S, // South (bottom-center)
    SE // Southeast (bottom-right)
  }

  public enum IconColor {
    // see https://jetbrains.design/intellij/principles/icons/#action-icons
    ACTIONS_RED(UIManager.getColor(FlatIconColors.ACTIONS_RED.key)),
    ACTIONS_YELLOW(UIManager.getColor(FlatIconColors.ACTIONS_YELLOW.key)),
    ACTIONS_GREEN(UIManager.getColor(FlatIconColors.ACTIONS_GREEN.key)),
    ACTIONS_BLUE(UIManager.getColor(FlatIconColors.ACTIONS_BLUE.key)),
    ACTIONS_GREY(UIManager.getColor(FlatIconColors.ACTIONS_GREY.key)),
    ACTIONS_GREY_INLINE(UIManager.getColor(FlatIconColors.ACTIONS_GREYINLINE.key));

    public final Color color;

    IconColor(Color color) {
      this.color = color;
    }

    public Color getColor() {
      return color;
    }

    public String getHtmlCode() {
      return String.format(
          "rgb(%d,%d,%d)", color.getRed(), color.getGreen(), color.getBlue()); // NON-NLS
    }
  }

  private GuiUtils() {}

  public static UICore getUICore() {
    return UICore.getInstance();
  }

  public static Dimension getBigIconButtonSize(JComponent c) {
    Insets insets = c.getInsets();
    int size = c.getFontMetrics(c.getFont()).getHeight() + insets.top + insets.bottom;
    return new Dimension(size, size);
  }

  public static Dimension getComponentSizeFromText(JComponent c, String text) {
    Font font = c.getFont();
    if (font == null) {
      font = FontItem.DEFAULT.getFont();
    }
    Insets insets = c.getInsets();
    int width = c.getFontMetrics(font).stringWidth(text) + insets.left + insets.right;
    int height = c.getFontMetrics(font).getHeight() + insets.top + insets.bottom;
    return new Dimension(width, height);
  }

  public static int getComponentWidthFromText(JComponent c, String text) {
    Font font = c.getFont();
    if (font == null) {
      font = FontItem.DEFAULT.getFont();
    }
    Insets insets = c.getInsets();
    return c.getFontMetrics(font).stringWidth(text) + insets.left + insets.right;
  }

  public static FlatSVGIcon getDerivedIcon(FlatSVGIcon flatSVGIcon, ColorFilter filter) {
    FlatSVGIcon icon = new FlatSVGIcon(flatSVGIcon);
    icon.setColorFilter(filter);
    return icon;
  }

  public static void applySelectedIconEffect(AbstractButton button) {
    if (button.getIcon() instanceof FlatSVGIcon flatSVGIcon) {
      button.setSelectedIcon(
          GuiUtils.getDerivedIcon(flatSVGIcon, GuiUtils.getSelectedColorFilter(button)));
    }
  }

  public static ColorFilter getSelectedColorFilter(JComponent c) {
    ColorFilter colorFilter = new ColorFilter();
    Color color = IconColor.ACTIONS_BLUE.color;
    if (c instanceof JMenuItem) {
      color = FlatUIUtils.getUIColor("MenuItem.selectionForeground", color);
    }

    colorFilter.add(new Color(0x6E6E6E), color);
    return colorFilter;
  }

  public static Icon getUpArrowIcon() {
    return new FlatTreeCollapsedIcon() {
      @Override
      protected void paintIcon(Component c, Graphics2D g) {
        g.rotate(Math.toRadians(-90), width / 2., height / 2.);
        super.paintIcon(c, g);
      }
    };
  }

  public static Icon getDownArrowIcon() {
    return UIManager.getIcon("Tree.expandedIcon");
  }

  public static int getScaleLength(int length) {
    return (int) (length * UIScale.getUserScaleFactor());
  }

  public static float getScaleLength(float length) {
    return length * UIScale.getUserScaleFactor();
  }

  public static double getScaleLength(double length) {
    return length * UIScale.getUserScaleFactor();
  }

  public static void rightToLeftChanged(Container c, boolean rightToLeft) {
    c.applyComponentOrientation(
        rightToLeft ? ComponentOrientation.RIGHT_TO_LEFT : ComponentOrientation.LEFT_TO_RIGHT);
    c.revalidate();
    c.repaint();
  }

  public static TitledBorder getTitledBorder(String title) {
    return new TitledBorder(
        null,
        title,
        TitledBorder.DEFAULT_JUSTIFICATION,
        TitledBorder.DEFAULT_POSITION,
        FontItem.DEFAULT_SEMIBOLD.getFont(),
        null);
  }

  public static Border getEmptyBorder(int gap) {
    int g = getScaleLength(gap);
    return new EmptyBorder(g, g, g, g);
  }

  public static Border getEmptyBorder(int horizontal, int vertical) {
    int h = getScaleLength(horizontal);
    int v = getScaleLength(vertical);
    return new EmptyBorder(v, h, v, h);
  }

  public static Border getEmptyBorder(int top, int left, int bottom, int right) {
    return new EmptyBorder(
        getScaleLength(top), getScaleLength(left), getScaleLength(bottom), getScaleLength(right));
  }

  public static JPanel getFlowLayoutPanel(JComponent... items) {
    return getFlowLayoutPanel(FlowLayout.LEADING, 5, 5, items);
  }

  public static JPanel getFlowLayoutPanel(int horizontalGap, int verticalGap, JComponent... items) {
    return getFlowLayoutPanel(FlowLayout.LEADING, horizontalGap, verticalGap, items);
  }

  public static JPanel getFlowLayoutPanel(
      int align, int horizontalGap, int verticalGap, JComponent... items) {
    int h = getScaleLength(horizontalGap);
    int v = getScaleLength(verticalGap);
    JPanel panel = new JPanel();
    panel.setLayout(new FlowLayout(align, h, v));
    for (JComponent item : items) {
      panel.add(item);
    }
    return panel;
  }

  public static JPanel getHorizontalBoxLayoutPanel(JComponent... items) {
    return getBoxLayoutPanel(BoxLayout.LINE_AXIS, 0, false, items);
  }

  public static JPanel getVerticalBoxLayoutPanel(JComponent... items) {
    return getBoxLayoutPanel(BoxLayout.PAGE_AXIS, 0, false, items);
  }

  public static JPanel getHorizontalBoxLayoutPanel(int gap, JComponent... items) {
    return getBoxLayoutPanel(BoxLayout.LINE_AXIS, gap, true, items);
  }

  public static JPanel getVerticalBoxLayoutPanel(int gap, JComponent... items) {
    return getBoxLayoutPanel(BoxLayout.PAGE_AXIS, gap, true, items);
  }

  private static JPanel getBoxLayoutPanel(
      int axis, int gap, boolean externalGap, JComponent... items) {
    JPanel panel = new JPanel();
    panel.setLayout(new BoxLayout(panel, axis));
    boolean vertical = axis == BoxLayout.PAGE_AXIS || axis == BoxLayout.Y_AXIS;
    if (externalGap && gap > 0) {
      panel.add(vertical ? boxVerticalStrut(gap) : boxHorizontalStrut(gap));
    }

    for (JComponent item : items) {
      panel.add(item);
      if (gap > 0) {
        panel.add(vertical ? boxVerticalStrut(gap) : boxHorizontalStrut(gap));
      }
    }
    if (gap > 0 && !externalGap) {
      panel.remove(panel.getComponentCount() - 1);
    }
    return panel;
  }

  public static JComponent boxVerticalStrut(int height) {
    int v = getScaleLength(height);
    return new Filler(new Dimension(0, v), new Dimension(0, v), new Dimension(Short.MAX_VALUE, v));
  }

  public static JComponent boxHorizontalStrut(int width) {
    int w = getScaleLength(width);
    return new Filler(new Dimension(w, 0), new Dimension(w, 0), new Dimension(w, Short.MAX_VALUE));
  }

  public static Filler boxXLastElement(int minimumWidth) {
    int w = getScaleLength(minimumWidth);
    return new Box.Filler(
        new Dimension(w, 0), new Dimension(w, 0), new Dimension(Integer.MAX_VALUE, 0));
  }

  public static Filler boxYLastElement(int minimumHeight) {
    int h = getScaleLength(minimumHeight);
    return new Box.Filler(
        new Dimension(0, h), new Dimension(0, h), new Dimension(0, Integer.MAX_VALUE));
  }

  public static Dimension getDimension(int width, int height) {
    int w = getScaleLength(width);
    int h = getScaleLength(height);
    return new Dimension(w, h);
  }

  public static void setPreferredWidth(Component component, int width, int minWidth) {
    Dimension dim = component.getPreferredSize();
    dim.width = getScaleLength(width);
    component.setPreferredSize(dim);
    dim = component.getMinimumSize();
    dim.width = getScaleLength(minWidth);
    component.setMinimumSize(dim);
    component.setMaximumSize(new Dimension(Short.MAX_VALUE, dim.height));
  }

  public static void setWidth(Component component, int width) {
    int sWidth = getScaleLength(width);
    Dimension dim = component.getMinimumSize();
    dim.width = sWidth;
    component.setMinimumSize(dim);
    dim = component.getPreferredSize();
    dim.width = sWidth;
    component.setPreferredSize(dim);
    component.setMaximumSize(dim);
  }

  public static void setPreferredWidth(Component component, int width) {
    setPreferredWidth(component, width, 50);
  }

  public static void setPreferredHeight(Component component, int height) {
    setPreferredHeight(component, height, 50);
  }

  public static void setPreferredHeight(Component component, int height, int minHeight) {
    Dimension dim = component.getPreferredSize();
    dim.height = getScaleLength(height);
    component.setPreferredSize(dim);
    dim = component.getMinimumSize();
    dim.height = getScaleLength(minHeight);
    component.setMinimumSize(dim);
  }

  public static void showCenterScreen(Window window) {
    showCenterScreen(window, null, WindowPosition.CENTER);
  }

  public static void showCenterScreen(Window window, Component parent) {
    showCenterScreen(window, parent, WindowPosition.CENTER);
  }

  /**
   * Positions a window on the screen according to the specified direction.
   *
   * @param window The window to position
   * @param parent The parent component to position relative to, or null for screen positioning
   * @param direction The position direction: CENTER, NW, N, NE, W, E, SW, S, SE
   */
  public static void showCenterScreen(Window window, Component parent, WindowPosition direction) {
    try {
      Rectangle bound;
      if (parent == null) {
        bound =
            GraphicsEnvironment.getLocalGraphicsEnvironment()
                .getDefaultScreenDevice()
                .getDefaultConfiguration()
                .getBounds();
      } else {
        Dimension sSize = parent.getSize();
        Point p = parent.getLocationOnScreen();
        bound = new Rectangle(p.x, p.y, sSize.width, sSize.height);
      }

      int x, y;
      int windowWidth = window.getWidth();
      int windowHeight = window.getHeight();

      switch (direction) {
        case NW -> {
          x = bound.x;
          y = bound.y;
        }
        case N -> {
          x = bound.x + (bound.width - windowWidth) / 2;
          y = bound.y;
        }
        case NE -> {
          x = bound.x + bound.width - windowWidth;
          y = bound.y;
        }
        case W -> {
          x = bound.x;
          y = bound.y + (bound.height - windowHeight) / 2;
        }
        case E -> {
          x = bound.x + bound.width - windowWidth;
          y = bound.y + (bound.height - windowHeight) / 2;
        }
        case SW -> {
          x = bound.x;
          y = bound.y + bound.height - windowHeight;
        }
        case S -> {
          x = bound.x + (bound.width - windowWidth) / 2;
          y = bound.y + bound.height - windowHeight;
        }
        case SE -> {
          x = bound.x + bound.width - windowWidth;
          y = bound.y + bound.height - windowHeight;
        }
        default -> { // CENTER
          x = bound.x + (bound.width - windowWidth) / 2;
          y = bound.y + (bound.height - windowHeight) / 2;
        }
      }

      window.setLocation(x, y);
    } catch (Exception e) {
      LOGGER.error("Cannot position the window", e);
    }
    window.setVisible(true);
  }

  public static void addChangeListener(JSlider slider, ChangeListener listener) {
    ChangeListener[] listeners = slider.getChangeListeners();
    for (ChangeListener changeListener : listeners) {
      if (listener == changeListener) {
        return;
      }
    }
    slider.addChangeListener(listener);
  }

  public static JSlider createSlider(int min, int max, int value, int tickDivisions) {
    JSlider slider = new JSlider(min, max, value);
    slider.setMajorTickSpacing(Math.max(1, (max - min) / tickDivisions));
    slider.setPaintTicks(true);
    return slider;
  }

  private static void addCheckActionToJFormattedTextField(final JFormattedTextField textField) {
    textField.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "check"); // NON-NLS
    textField
        .getActionMap()
        .put(
            "check", // NON-NLS
            new AbstractAction() {

              @Override
              public void actionPerformed(ActionEvent e) {
                try {
                  textField.commitEdit(); // so use it.
                  textField.postActionEvent(); // stop editing //for DefaultCellEditor
                } catch (java.text.ParseException pe) {
                  LOGGER.error(
                      "Exception when commit value in {}", textField.getClass().getName(), pe);
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
    spin.setModel(new SpinnerNumberModel(val < min ? min : Math.min(val, max), min, max, delta));
    JFormattedTextField ftf = ((JSpinner.DefaultEditor) spin.getEditor()).getTextField();
    addCheckActionToJFormattedTextField(ftf);
  }

  public static void setSpinnerWidth(JSpinner spin, int valueWidth) {
    Component mySpinnerEditor = spin.getEditor();
    JFormattedTextField ftf = ((JSpinner.DefaultEditor) mySpinnerEditor).getTextField();
    ftf.setColumns(valueWidth);
  }

  public static void formatCheckAction(JSpinner spin) {
    formatCheckAction(spin, null);
  }

  public static void formatCheckAction(
      JSpinner spin, JFormattedTextField.AbstractFormatter defaultFormat) {
    final JFormattedTextField ftf = ((JSpinner.DefaultEditor) spin.getEditor()).getTextField();
    if (defaultFormat != null) {
      ftf.setFormatterFactory(new DefaultFormatterFactory(defaultFormat));
    }
    addCheckActionToJFormattedTextField(ftf);
  }

  public static Number getFormattedValue(JFormattedTextField textField) {
    AbstractFormatterFactory formatter = textField.getFormatterFactory();
    if (formatter instanceof DefaultFormatterFactory factory
        && textField.getFormatter().equals(factory.getEditFormatter())) {
      try {
        // to be sure that the value is commit (by default it is when the JFormattedTextField losing
        // the focus)
        textField.commitEdit();
      } catch (ParseException pe) {
        LOGGER.error("Exception when commit value in {}", textField.getClass().getName(), pe);
      }
    }
    Number val = null;
    try {
      val = (Number) textField.getValue();
    } catch (Exception e) {
      LOGGER.error("Cannot get number form textField", e);
    }
    return val;
  }

  /**
   * Adds a dynamic validation listener to the given JTextField.
   *
   * @param textField The JTextField to validate.
   * @param validationLogic A lambda or Predicate to validate the field's content.
   */
  public static void addValidation(JTextField textField, Predicate<String> validationLogic) {
    textField.putClientProperty("JComponent.outline", "error");
    textField
        .getDocument()
        .addDocumentListener(
            new DocumentListener() {
              @Override
              public void insertUpdate(DocumentEvent e) {
                validate();
              }

              @Override
              public void removeUpdate(DocumentEvent e) {
                validate();
              }

              @Override
              public void changedUpdate(DocumentEvent e) {
                validate();
              }

              private void validate() {
                String text = textField.getText().trim();
                if (validationLogic.test(text)) {
                  // Input is valid - no error
                  textField.putClientProperty("JComponent.outline", null);
                } else {
                  // Input is invalid - show error outline
                  textField.putClientProperty("JComponent.outline", "error");
                }
              }
            });
  }

  public static void formatPortTextField(JFormattedTextField port) {
    NumberFormat portFormat = NumberFormat.getNumberInstance();
    portFormat.setMinimumIntegerDigits(0);
    portFormat.setMaximumIntegerDigits(65535);
    portFormat.setMaximumFractionDigits(0);
    port.setFormatterFactory(new DefaultFormatterFactory(new NumberFormatter(portFormat)));
    port.setColumns(5);
    GuiUtils.addCheckAction(port);
  }

  // A convenience method for creating menu items
  public static JMenuItem menuItem(
      String label, ActionListener listener, String command, int mnemonic, int acceleratorKey) {
    JMenuItem item = new JMenuItem(label);
    item.addActionListener(listener);
    item.setActionCommand(command);
    if (mnemonic != 0) {
      item.setMnemonic((char) mnemonic);
    }
    if (acceleratorKey != 0) {
      item.setAccelerator(KeyStroke.getKeyStroke(acceleratorKey, Event.CTRL_MASK));
    }
    return item;
  }

  public static JButton createHelpButton(String topic) {
    JButton jButtonHelp = new JButton();
    jButtonHelp.putClientProperty("JButton.buttonType", "help");
    jButtonHelp.setToolTipText(Messages.getString("online.documentation"));
    jButtonHelp.addActionListener(createHelpActionListener(jButtonHelp, topic));
    return jButtonHelp;
  }

  /**
   * Build a panel containing a wrapped read-only message and a help button on the right side that
   * opens the given documentation topic. Useful for {@code JOptionPane.showXxxDialog(..., panel,
   * ...)} confirmations where the user must understand the implications of the choice.
   *
   * @param message the message to display
   * @param helpTopic the documentation topic appended to {@code weasis.help.online}
   * @return a JPanel ready to be passed to a JOptionPane
   */
  public static JPanel buildHelpMessagePanel(String message, String helpTopic) {
    JPanel panel = new JPanel(new java.awt.BorderLayout(10, 0));
    JTextArea textArea = new JTextArea(message);
    textArea.setEditable(false);
    textArea.setOpaque(false);
    textArea.setLineWrap(true);
    textArea.setWrapStyleWord(true);
    textArea.setColumns(40);
    panel.add(textArea, java.awt.BorderLayout.CENTER);
    panel.add(createHelpButton(helpTopic), java.awt.BorderLayout.EAST);
    return panel;
  }

  public static ActionListener createHelpActionListener(JButton jButtonHelp, String topic) {
    return _ -> {
      GuiUtils.openInDefaultBrowser(
          jButtonHelp,
          URI.create(
              GuiUtils.getUICore().getSystemPreferences().getProperty("weasis.help.online")
                  + topic));
    };
  }

  public static int getMaxLength(Rectangle bounds) {
    return Math.max(bounds.width, bounds.height);
  }

  public static void openInDefaultBrowser(Component parent, URL url) {
    if (url != null) {
      try {
        openInDefaultBrowser(parent, url.toURI());
      } catch (URISyntaxException e) {
        throw new RuntimeException(e);
      }
    }
  }

  public static void openInDefaultBrowser(Component parent, URI uri) {
    if (uri != null) {
      if (SystemInfo.isLinux) {
        openInLinuxBrowser(parent, uri);
      } else if (Desktop.isDesktopSupported()) {
        final Desktop desktop = Desktop.getDesktop();
        if (desktop.isSupported(Desktop.Action.BROWSE)) {
          try {
            desktop.browse(uri);
          } catch (IOException e) {
            LOGGER.error("Cannot open URL to the desktop browser", e);
          }
        }
      } else {
        JOptionPane.showMessageDialog(
            WinUtil.getValidComponent(parent),
            Messages.getString("JMVUtils.browser") + StringUtil.COLON_AND_SPACE + uri,
            Messages.getString("JMVUtils.error"),
            JOptionPane.ERROR_MESSAGE);
      }
    }
  }

  public static void openSystemExplorer(Component parent, Path path) {
    if (path != null) {
      if (SystemInfo.isLinux) {
        openCommand(parent, "xdg-open", path.toString()); // NON-NLS
      } else if (Desktop.isDesktopSupported()
          && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE_FILE_DIR)) {
        Desktop.getDesktop().browseFileDirectory(path.toFile());
      } else if (SystemInfo.isWindows) {
        openCommand(parent, "explorer", path.toString()); // NON-NLS
      } else if (SystemInfo.isMacOS) {
        openCommand(parent, "/usr/bin/open", path.toString()); // NON-NLS
      } else {
        JOptionPane.showMessageDialog(
            WinUtil.getValidComponent(parent),
            Messages.getString("JMVUtils.browser") + StringUtil.COLON_AND_SPACE + path,
            Messages.getString("JMVUtils.error"),
            JOptionPane.ERROR_MESSAGE);
      }
    }
  }

  /** Browser executables tried, in order, when no user preference can be resolved. */
  private static final List<String> LINUX_BROWSER_FALLBACKS =
      List.of(
          "x-www-browser", // Debian/Ubuntu alternatives symlink to the system browser
          "google-chrome",
          "google-chrome-stable",
          "brave-browser",
          "brave-browser-stable",
          "vivaldi",
          "vivaldi-stable",
          "microsoft-edge",
          "microsoft-edge-stable",
          "opera",
          "chromium",
          "chromium-browser",
          "firefox"); // last: on Ubuntu this is a snap and cannot read host /tmp // NON-NLS

  /**
   * Opens a URI in a web browser on Linux.
   *
   * <p>{@code xdg-open} resolves a {@code file://} URI by the file's MIME type, so a local HTML
   * page is handed to whatever application is registered for {@code text/html} — which is not
   * necessarily a browser, and may be a sandboxed app unable to read the file. For {@code file://}
   * URIs a web browser is therefore launched directly. Other schemes keep going through {@code
   * xdg-open}, which dispatches them to the user's default browser.
   */
  private static void openInLinuxBrowser(Component parent, URI uri) {
    if ("file".equalsIgnoreCase(uri.getScheme())) { // NON-NLS
      String browser = findLinuxBrowserCommand();
      openCommand(parent, browser == null ? "xdg-open" : browser, uri.toString()); // NON-NLS
    } else {
      openCommand(parent, "xdg-open", uri.toString()); // NON-NLS
    }
  }

  /**
   * Resolves a web browser executable on Linux, preferring the {@code $BROWSER} environment
   * variable, then the user's configured default web browser, then a list of well-known browsers.
   *
   * @return a browser command (name or path), or {@code null} if none could be found
   */
  private static String findLinuxBrowserCommand() {
    List<String> candidates = new ArrayList<>();
    String browserEnv = System.getenv("BROWSER");
    if (StringUtil.hasText(browserEnv)) {
      for (String entry : browserEnv.split(File.pathSeparator)) {
        String exe = entry.trim().split("\\s+")[0]; // drop any %s placeholder
        if (!exe.isEmpty()) {
          candidates.add(exe);
        }
      }
    }
    String defaultBrowser = resolveLinuxDefaultBrowser();
    if (defaultBrowser != null) {
      candidates.add(defaultBrowser);
    }
    candidates.addAll(LINUX_BROWSER_FALLBACKS);
    return candidates.stream().filter(GuiUtils::isExecutableOnPath).findFirst().orElse(null);
  }

  /** Returns the executable of the user's default web browser, via {@code xdg-settings}. */
  private static String resolveLinuxDefaultBrowser() {
    try {
      Process process =
          new ProcessBuilder("xdg-settings", "get", "default-web-browser") // NON-NLS
              .redirectError(ProcessBuilder.Redirect.DISCARD)
              .start();
      process.getOutputStream().close();
      if (process.waitFor(5, TimeUnit.SECONDS) && process.exitValue() == 0) {
        String desktopEntry =
            new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).strip();
        if (StringUtil.hasText(desktopEntry)) {
          return execFromDesktopEntry(desktopEntry);
        }
      }
    } catch (IOException e) {
      LOGGER.debug("Cannot query the default web browser", e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
    return null;
  }

  /**
   * Extracts the executable from the {@code Exec=} line of a freedesktop {@code .desktop} entry.
   */
  private static String execFromDesktopEntry(String desktopEntry) {
    String fileName = desktopEntry.endsWith(".desktop") ? desktopEntry : desktopEntry + ".desktop";
    for (Path dir : linuxApplicationDirs()) {
      Path desktopFile = dir.resolve(fileName);
      if (Files.isReadable(desktopFile)) {
        try {
          for (String line : Files.readAllLines(desktopFile, StandardCharsets.UTF_8)) {
            if (line.startsWith("Exec=")) { // NON-NLS
              String exec = line.substring(5).trim();
              return exec.isEmpty() ? null : exec.split("\\s+")[0]; // drop %U/%f field codes
            }
          }
        } catch (IOException e) {
          LOGGER.debug("Cannot read desktop entry {}", desktopFile, e);
        }
      }
    }
    return null;
  }

  /** Standard freedesktop directories holding {@code .desktop} application entries. */
  private static List<Path> linuxApplicationDirs() {
    List<Path> dirs = new ArrayList<>();
    String dataHome = System.getenv("XDG_DATA_HOME");
    if (StringUtil.hasText(dataHome)) {
      dirs.add(Path.of(dataHome, "applications"));
    } else {
      dirs.add(Path.of(System.getProperty("user.home"), ".local", "share", "applications"));
    }
    String dataDirs = System.getenv("XDG_DATA_DIRS");
    if (!StringUtil.hasText(dataDirs)) {
      dataDirs = "/usr/local/share:/usr/share"; // NON-NLS
    }
    for (String dir : dataDirs.split(File.pathSeparator)) {
      if (!dir.isEmpty()) {
        dirs.add(Path.of(dir, "applications"));
      }
    }
    dirs.add(Path.of("/var/lib/snapd/desktop/applications")); // NON-NLS
    return dirs;
  }

  /** Tests whether the given command resolves to an executable file (absolute path or on PATH). */
  private static boolean isExecutableOnPath(String command) {
    if (command.indexOf(File.separatorChar) >= 0) {
      return Files.isExecutable(Path.of(command));
    }
    String path = System.getenv("PATH");
    if (!StringUtil.hasText(path)) {
      return false;
    }
    for (String dir : path.split(File.pathSeparator)) {
      if (!dir.isEmpty() && Files.isExecutable(Path.of(dir, command))) {
        return true;
      }
    }
    return false;
  }

  /**
   * Launches an external command (a launcher such as {@code xdg-open}, {@code explorer} or {@code
   * open}, or a browser executable) to open the given target.
   *
   * <p>Launchers delegate to the target application and return promptly, as does a browser when an
   * instance is already running; a freshly started browser instead stays in the foreground. The
   * method therefore waits only briefly: a non-zero exit within that window is reported as a
   * failure, while a process still running afterwards is assumed to have opened the target and is
   * left running (destroying it would close the browser it just opened).
   */
  private static void openCommand(Component parent, String cmd, String target) {
    try {
      Process process =
          new ProcessBuilder(cmd, target)
              .redirectOutput(ProcessBuilder.Redirect.DISCARD)
              .redirectError(ProcessBuilder.Redirect.DISCARD)
              .start();
      process.getOutputStream().close();
      if (process.waitFor(5, TimeUnit.SECONDS) && process.exitValue() != 0) {
        LOGGER.error("'{}' failed (exit {}) for {}", cmd, process.exitValue(), target);
        showOpenCommandError(parent, target);
      }
    } catch (IOException e) {
      LOGGER.error("Cannot run '{}' for {}", cmd, target, e);
      showOpenCommandError(parent, target);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  private static void showOpenCommandError(Component parent, String target) {
    JOptionPane.showMessageDialog(
        WinUtil.getValidComponent(parent),
        Messages.getString("JMVUtils.browser") + StringUtil.COLON_AND_SPACE + target,
        Messages.getString("JMVUtils.error"),
        JOptionPane.ERROR_MESSAGE);
  }

  public static HyperlinkListener buildHyperlinkListener() {
    return e -> {
      JTextPane pane = (JTextPane) e.getSource();
      if (e.getEventType() == HyperlinkEvent.EventType.ENTERED) {
        pane.setToolTipText(e.getDescription());
      } else if (e.getEventType() == HyperlinkEvent.EventType.EXITED) {
        pane.setToolTipText(null);
      } else if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
        Component parent = e.getSource() instanceof Component c ? c : null;
        openInDefaultBrowser(WinUtil.getValidComponent(parent), e.getURL());
      }
    };
  }

  public static JTextPane getPanelWithHyperlink(String html) {
    JTextPane jTextPane1 = new JTextPane();
    jTextPane1.setContentType("text/html");
    jTextPane1.setEditable(false);
    jTextPane1.addHyperlinkListener(buildHyperlinkListener());
    jTextPane1.setText(html);
    return jTextPane1;
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

  public static Object[] setRenderingHints(
      Graphics g, boolean antialiasing, boolean stroke, boolean text) {
    Graphics2D g2 = (Graphics2D) g;
    Object[] oldRenderingHints =
        new Object[] {
          g2.getRenderingHint(RenderingHints.KEY_ANTIALIASING),
          g2.getRenderingHint(RenderingHints.KEY_STROKE_CONTROL),
          g2.getRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING)
        };

    if (antialiasing) {
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    }
    if (stroke) {
      boolean useQuartz = Boolean.getBoolean("apple.awt.graphics.UseQuartz");
      g2.setRenderingHint(
          RenderingHints.KEY_STROKE_CONTROL,
          useQuartz ? RenderingHints.VALUE_STROKE_PURE : RenderingHints.VALUE_STROKE_NORMALIZE);
    }
    if (text) {
      g2.setRenderingHint(
          RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
    }

    return oldRenderingHints;
  }

  public static void resetRenderingHints(Graphics g, Object[] oldRenderingHints) {
    if (oldRenderingHints.length == 3) {
      Graphics2D g2 = (Graphics2D) g;
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, oldRenderingHints[0]);
      g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, oldRenderingHints[1]);
      g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, oldRenderingHints[2]);
    }
  }

  public static int insetWidth(JPanel panel) {
    if (panel != null) {
      Insets insets = panel.getInsets();
      if (insets != null) {
        return insets.left + insets.right;
      }
    }
    return 0;
  }

  public static int insetHeight(JPanel panel) {
    if (panel != null) {
      Insets insets = panel.getInsets();
      if (insets != null) {
        return insets.top + insets.bottom;
      }
    }
    return 0;
  }
}
