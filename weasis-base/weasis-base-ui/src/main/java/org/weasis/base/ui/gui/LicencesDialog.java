/*
 * Copyright (c) 2023 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.base.ui.gui;

import com.formdev.flatlaf.extras.FlatSVGIcon;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.Image;
import java.awt.event.WindowEvent;
import java.util.List;
import java.util.Optional;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.SwingConstants;
import javax.swing.WindowConstants;
import org.weasis.base.ui.Messages;
import org.weasis.core.api.gui.util.AbstractTabLicense;
import org.weasis.core.api.gui.util.GUIEntry;
import org.weasis.core.api.gui.util.GuiUtils;

/**
 * @author João Bolsson (joaobolsson@animati.com.br)
 * @author Pedro Costa (pedro.costa@animati.com.br)
 * @version 2023, May 04.
 */
public class LicencesDialog extends JDialog {

  private final JButton jButtonClose;
  private final JTabbedPane tabbedPane;
  private List<AbstractTabLicense> tabsList;

  /**
   * Creates a dialog to insert third party licences.
   *
   * @param owner Dialog parent.
   * @param list List of licences.
   */
  public LicencesDialog(final Frame owner, List<AbstractTabLicense> list) {
    super(owner, Messages.getString("LicencesDialog.title"), true);
    this.tabsList = list;
    this.jButtonClose = new JButton(Messages.getString("WeasisAboutBox.close"));
    jButtonClose.addActionListener(e -> close());
    this.tabbedPane = new JTabbedPane(SwingConstants.LEFT);
    tabbedPane.putClientProperty("JTabbedPane.showTabSeparators", true);
    tabbedPane.putClientProperty("JTabbedPane.tabIconPlacement", SwingConstants.TOP);
    initGUI(list);
    pack();
  }

  private void initGUI(List<AbstractTabLicense> list) {
    setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);

    JPanel panelRoot = new JPanel();
    panelRoot.setLayout(new BorderLayout());

    int iconSize = GuiUtils.getScaleLength(64);

    for (AbstractTabLicense tabLicense : list) {
      addEntry(tabLicense, iconSize);
    }

    panelRoot.add(tabbedPane, BorderLayout.CENTER);
    JPanel jPanelClose = GuiUtils.getFlowLayoutPanel(FlowLayout.TRAILING, 25, 10, jButtonClose);
    panelRoot.add(jPanelClose, BorderLayout.SOUTH);

    getContentPane().add(panelRoot, null);
  }

  // Overridden so we can exit when window is closed
  @Override
  protected void processWindowEvent(WindowEvent e) {
    if (WindowEvent.WINDOW_CLOSING == e.getID()) {
      close();
    }
    super.processWindowEvent(e);
  }

  private void close() {
    Optional.ofNullable(tabsList)
        .ifPresent(
            _ -> {
              for (AbstractTabLicense abstractTabLicense : tabsList) {
                abstractTabLicense.closing();
              }
            });
    dispose();
  }

  private void addEntry(AbstractTabLicense tabLicense, int iconSize) {
    GUIEntry entry = tabLicense.getGuiEntry();
    String pattern =
        """
                <html><center>%s<br><small>%s</small></center></html>
                """;
    tabbedPane.addTab(
        String.format(pattern, entry.getUIName(), entry.getDescription()),
        resizeIcon(entry.getIcon(), iconSize),
        tabLicense);
  }

  private Icon resizeIcon(Icon icon, int iconSize) {
    Icon resizeIcon = icon;
    if (icon.getIconWidth() != iconSize) {
      int iconHeight = icon.getIconHeight() * iconSize / icon.getIconWidth();
      if (icon instanceof FlatSVGIcon flatSVGIcon) {
        resizeIcon = flatSVGIcon.derive(iconSize, iconHeight);
      } else if (icon instanceof ImageIcon imageIcon) {
        Image scaleImage =
            imageIcon
                .getImage()
                .getScaledInstance(iconSize, iconHeight, java.awt.Image.SCALE_SMOOTH);
        resizeIcon = new ImageIcon(scaleImage);
      }
    }
    return resizeIcon;
  }
}
