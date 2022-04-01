/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.launcher;

import com.formdev.flatlaf.extras.FlatSVGIcon;
import java.awt.BorderLayout;
import java.awt.Container;
import java.awt.EventQueue;
import java.awt.Font;
import java.awt.Frame;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.Window;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Path;
import java.util.Map;
import java.util.Properties;
import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.RootPaneContainer;
import javax.swing.SwingConstants;
import javax.swing.UIManager;
import org.osgi.framework.BundleContext;

public class WeasisLoader {

  private static final Logger LOGGER = System.getLogger(WeasisLoader.class.getName());

  public static final String LBL_LOADING = Messages.getString("WebStartLoader.load");
  public static final String LBL_DOWNLOADING = Messages.getString("WebStartLoader.download");

  private JButton cancelButton;
  private JLabel loadingLabel;
  private JProgressBar downloadProgress;
  private Container container;

  private final Path resPath;
  private final WeasisMainFrame mainFrame;

  public WeasisLoader(Path resPath, WeasisMainFrame mainFrame) {
    this.resPath = resPath;
    this.mainFrame = mainFrame;
  }

  public void writeLabel(String text) {
    loadingLabel.setText(text);
  }

  /*
   * Init splashScreen
   */
  public void initGUI() {
    loadingLabel = new JLabel();
    downloadProgress = new JProgressBar();
    cancelButton = new JButton();
    Font font = UIManager.getFont("h4.font");
    RootPaneContainer frame = mainFrame.getRootPaneContainer();

    Window win = new Window((Frame) frame);
    win.addWindowListener(
        new java.awt.event.WindowAdapter() {

          @Override
          public void windowClosing(java.awt.event.WindowEvent evt) {
            closing();
          }
        });
    container = win;

    loadingLabel.setText(LBL_LOADING);
    loadingLabel.setFocusable(false);
    loadingLabel.setBorder(BorderFactory.createEmptyBorder(5, 3, 5, 3));

    downloadProgress.setFocusable(false);
    downloadProgress.setStringPainted(true);
    downloadProgress.setFont(font);
    downloadProgress.setString(LBL_LOADING);

    cancelButton.setFont(font);
    cancelButton.setText(Messages.getString("WebStartLoader.cancel"));
    cancelButton.addActionListener(evt -> closing());

    Icon icon = new FlatSVGIcon(resPath.resolve("svg/logo/WeasisAbout.svg").toUri());
    String text =
        String.format(
            Messages.getString("WebStartLoader.title"), System.getProperty("weasis.name"));
    JLabel imagePane = new JLabel(text, icon, SwingConstants.CENTER);
    imagePane.setFont(UIManager.getFont("h3.font"));
    imagePane.setVerticalTextPosition(SwingConstants.TOP);
    imagePane.setHorizontalTextPosition(SwingConstants.CENTER);
    imagePane.setFocusable(false);

    JPanel panel = new JPanel(new BorderLayout());
    panel.add(imagePane, BorderLayout.CENTER);

    JPanel panelProgress = new JPanel(new BorderLayout());
    panelProgress.add(loadingLabel, BorderLayout.NORTH);
    panelProgress.add(downloadProgress, BorderLayout.CENTER);
    panelProgress.add(cancelButton, BorderLayout.EAST);

    panel.add(panelProgress, BorderLayout.SOUTH);
    panel.setBorder(BorderFactory.createEmptyBorder(3, 3, 3, 3));

    container.add(panel, BorderLayout.CENTER);

    if (container instanceof Window window) {
      window.pack();
    }
  }

  public WeasisMainFrame getMainFrame() {
    return mainFrame;
  }

  /*
   * Set maximum value for progress bar
   */
  public void setMax(final int max) {
    if (isClosed()) {
      return;
    }
    EventQueue.invokeLater(() -> downloadProgress.setMaximum(max));
  }

  /*
   * Set actual value of progress bar
   */
  public void setValue(final int val) {
    if (isClosed()) {
      return;
    }
    EventQueue.invokeLater(
        () -> {
          downloadProgress.setString(
              String.format(
                  Messages.getString("WebStartLoader.end"), val, downloadProgress.getMaximum()));
          downloadProgress.setValue(val);
          downloadProgress.repaint();
        });
  }

  private void closing() {
    System.exit(0);
  }

  public boolean isClosed() {
    return container == null;
  }

  public void open() {
    try {
      EventQueue.invokeAndWait(
          () -> {
            if (container == null) {
              initGUI();
            }
            displayOnScreen();
          });
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    } catch (InvocationTargetException e) {
      LOGGER.log(Level.ERROR, "Display splashscreen", e);
    }
  }

  public void close() {
    if (isClosed()) {
      return;
    }
    EventQueue.invokeLater(
        () -> {
          container.setVisible(false);
          if (container.getParent() != null) {
            container.getParent().remove(container);
          }
          if (container instanceof Window window) {
            window.dispose();
          }
          container = null;
          cancelButton = null;
          downloadProgress = null;
          loadingLabel = null;
        });
  }

  private void displayOnScreen() {
    if (container instanceof Window) {
      try {
        Rectangle bounds =
            GraphicsEnvironment.getLocalGraphicsEnvironment()
                .getDefaultScreenDevice()
                .getDefaultConfiguration()
                .getBounds();
        int x = bounds.x + (bounds.width - container.getWidth()) / 2;
        int y = bounds.y + (bounds.height - container.getHeight()) / 2;

        container.setLocation(x, y);
      } catch (Exception e) {
        LOGGER.log(Level.ERROR, "Set splashscreen location", e);
      }
      container.setVisible(true);
    }
  }

  public void setFelix(
      Map<String, String> serverProp, BundleContext bundleContext, Properties modulesi18n) {
    AutoProcessor.process(serverProp, modulesi18n, bundleContext, this);
  }
}
