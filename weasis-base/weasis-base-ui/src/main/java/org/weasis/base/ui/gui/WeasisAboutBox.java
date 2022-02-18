/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.base.ui.gui;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.event.WindowEvent;
import java.util.Enumeration;
import java.util.Properties;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextPane;
import javax.swing.WindowConstants;
import org.weasis.base.ui.Messages;
import org.weasis.core.api.gui.util.AppProperties;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.service.BundleTools;
import org.weasis.core.api.util.ResourceUtil;
import org.weasis.core.ui.util.SimpleTableModel;

public class WeasisAboutBox extends JDialog {

  private final JTable sysTable;

  public WeasisAboutBox(Frame owner) {
    super(
        owner,
        String.format(Messages.getString("WeasisAboutBox.about"), AppProperties.WEASIS_NAME),
        true);
    sysTable =
        new JTable(
            new SimpleTableModel(
                new String[] {
                  Messages.getString("WeasisAboutBox.prop"),
                  Messages.getString("WeasisAboutBox.val")
                },
                createSysInfo()));
    sysTable.getColumnModel().setColumnMargin(GuiUtils.getScaleLength(5));
    sysTable.setAutoCreateRowSorter(true);
    sysTable.setShowHorizontalLines(true);
    sysTable.setShowVerticalLines(true);
    init();
    pack();
  }

  private void init() {
    setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
    setModal(true);
    JPanel panelRoot = new JPanel();
    panelRoot.setLayout(new BorderLayout());

    JButton jButtonClose = new JButton();
    jButtonClose.setText(Messages.getString("WeasisAboutBox.close"));
    jButtonClose.addActionListener(e -> close());
    JPanel jPanelClose = GuiUtils.getFlowLayoutPanel(FlowLayout.TRAILING, 25, 10, jButtonClose);

    JPanel jPanelInfoSys = new JPanel();
    jPanelInfoSys.setBorder(GuiUtils.getEmptyBorder(10));
    jPanelInfoSys.setLayout(new BorderLayout());

    JTextPane jTextPane1 = new JTextPane();
    jTextPane1.setFocusable(false);
    jTextPane1.setContentType("text/html");
    jTextPane1.setEditable(false);

    jTextPane1.addHyperlinkListener(GuiUtils.buildHyperlinkListener());
    String html =
        """
        <div align="center">
          <h2>%s %s</h2>
          <a href="%s">%s</a><br>
          %s<br>
          %s <a href="%s">%s</a><br>
          <p>%s %s, %s<br>
          Java VM: %s</p><br>
        </div>
        """
            .formatted(
                AppProperties.WEASIS_NAME,
                AppProperties.WEASIS_VERSION,
                BundleTools.SYSTEM_PREFERENCES.getProperty("weasis.releasenotes", ""),
                Messages.getString("WeasisWin.release"),
                BundleTools.SYSTEM_PREFERENCES.getProperty("weasis.copyrights", ""),
                AppProperties.WEASIS_NAME,
                "https://github.com/nroduit/Weasis/blob/master/3rd-party-licenses.md",
                Messages.getString("WeasisWin.otherSoft"),
                System.getProperty("os.name"),
                System.getProperty("os.version"),
                System.getProperty("os.arch"),
                System.getProperty("java.vendor.version", System.getProperty("java.vm.version")));

    jTextPane1.setText(html);
    JLabel jLabel1 = new JLabel();
    jLabel1.setIcon(ResourceUtil.getIcon(ResourceUtil.LogoIcon.LARGE).derive(0.8f));
    JPanel jPanel3 = new JPanel();
    jPanel3.setLayout(new BorderLayout());

    JTabbedPane jTabbedPane1 = new JTabbedPane();
    jTabbedPane1.add(jPanel3, getTitle());
    JPanel jPanel1 = new JPanel();
    jPanel3.add(jPanel1, BorderLayout.NORTH);
    jPanel1.add(jLabel1, null);
    JScrollPane jScrollPane3 = new JScrollPane();
    jPanel3.add(jScrollPane3, BorderLayout.CENTER);
    jScrollPane3.getViewport().add(jTextPane1, null);
    jTabbedPane1.add(jPanelInfoSys, Messages.getString("WeasisAboutBox.sys"));

    JScrollPane jScrollPane1 = new JScrollPane();
    jPanelInfoSys.add(jScrollPane1, BorderLayout.CENTER);
    jScrollPane1.setPreferredSize(GuiUtils.getDimension(430, jLabel1.getHeight()));
    jScrollPane1.getViewport().add(sysTable, null);

    panelRoot.add(jPanelClose, BorderLayout.SOUTH);
    panelRoot.add(jTabbedPane1, BorderLayout.CENTER);

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
    dispose();
  }

  private static Object[][] createSysInfo() {
    Properties sysProps = System.getProperties();
    Object[][] dataArray = new String[sysProps.size()][2];
    Enumeration<?> enumerate = sysProps.propertyNames();
    for (int i = 0; i < dataArray.length; i++) {
      dataArray[i][0] = enumerate.nextElement();
      dataArray[i][1] = sysProps.getProperty(dataArray[i][0].toString());
    }
    return dataArray;
  }
}
