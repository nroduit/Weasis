/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.pref;

import java.awt.FlowLayout;
import java.text.NumberFormat;
import javax.swing.ButtonGroup;
import javax.swing.JFormattedTextField;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JTextField;
import javax.swing.text.DefaultFormatterFactory;
import javax.swing.text.NumberFormatter;
import net.miginfocom.swing.MigLayout;
import org.weasis.core.Messages;
import org.weasis.core.api.gui.util.AbstractItemDialogPage;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.service.WProperties;
import org.weasis.core.util.StringUtil;

public class ProxyPrefView extends AbstractItemDialogPage {

  private static final String PROXY_MANUAL = "proxy.manual";
  private static final String PROXY_EXCEPTIONS = "proxy.exceptions";
  private static final String PROXY_HTTP_HOST = "proxy.http.host";
  private static final String PROXY_HTTP_PORT = "proxy.http.port";
  private static final String PROXY_HTTPS_HOST = "proxy.https.host";
  private static final String PROXY_HTTPS_PORT = "proxy.https.port";
  private static final String PROXY_FTP_HOST = "proxy.ftp.host";
  private static final String PROXY_FTP_PORT = "proxy.ftp.port";
  private static final String PROXY_SOCKS_HOST = "proxy.socks.host";
  private static final String PROXY_SOCKS_PORT = "proxy.socks.port";

  private final JRadioButton directConnectionRadio =
      new JRadioButton(Messages.getString("ProxyPrefView.direct"));
  private final JRadioButton proxyConnectionRadio =
      new JRadioButton(Messages.getString("ProxyPrefView.manual"));
  private final ButtonGroup buttonGroup = new ButtonGroup();

  private final JLabel lblAddress = new JLabel(Messages.getString("ProxyPrefView.host"));
  private final JLabel lblPort = new JLabel(Messages.getString("ProxyPrefView.port"));

  private final JTextField proxyHostHttp = new JTextField(20);
  private final JFormattedTextField proxyPortHttp = new JFormattedTextField();
  private final JTextField proxyHostSecure = new JTextField(20);
  private final JFormattedTextField proxyPortSecure = new JFormattedTextField();
  private final JTextField proxyHostFtp = new JTextField(20);
  private final JFormattedTextField proxyPortFtp = new JFormattedTextField();
  private final JTextField proxyHostSocks = new JTextField(20);
  private final JFormattedTextField proxyPortSocks = new JFormattedTextField();
  private final JTextField proxyExceptions = new JTextField(27);

  private final JLabel proxyLabelHttp = new JLabel("HTTP" + StringUtil.COLON);
  private final JLabel proxyLabelSecure =
      new JLabel(Messages.getString("ProxyPrefView.secure") + StringUtil.COLON);
  private final JLabel proxyLabelFtp = new JLabel("FTP" + StringUtil.COLON);
  private final JLabel proxyLabelSocks = new JLabel("SOCKS" + StringUtil.COLON);
  private final JLabel proxyLabelExceptions =
      new JLabel(Messages.getString("ProxyPrefView.exceptions") + StringUtil.COLON);

  public ProxyPrefView() {
    super(Messages.getString("ProxyPrefView.proxy"), 110);
    initialize();
  }

  private void initialize() {
    formatPortTextField(proxyPortHttp);
    formatPortTextField(proxyPortSecure);
    formatPortTextField(proxyPortFtp);
    formatPortTextField(proxyPortSocks);

    add(GuiUtils.getFlowLayoutPanel(FlowLayout.LEADING, 0, BLOCK_SEPARATOR, directConnectionRadio));
    add(
        GuiUtils.getFlowLayoutPanel(
            FlowLayout.LEADING, 0, ITEM_SEPARATOR_SMALL, proxyConnectionRadio));
    add(GuiUtils.getHorizontalBoxLayoutPanel(buildProxyPanel()));

    this.buttonGroup.add(directConnectionRadio);
    this.buttonGroup.add(proxyConnectionRadio);
    directConnectionRadio.addActionListener(e -> proxyConnectionAction(false));
    proxyConnectionRadio.addActionListener(e -> proxyConnectionAction(true));
    initState();

    add(GuiUtils.boxYLastElement(5));

    getProperties().setProperty(PreferenceDialog.KEY_SHOW_RESTORE, Boolean.TRUE.toString());
    getProperties().setProperty(PreferenceDialog.KEY_HELP, "proxy"); // NON-NLS
  }

  private void initState() {
    WProperties p = GuiUtils.getUICore().getLocalPersistence();

    proxyExceptions.setText(p.getProperty(PROXY_EXCEPTIONS));

    proxyHostHttp.setText(p.getProperty(PROXY_HTTP_HOST));
    proxyPortHttp.setValue(p.getIntProperty(PROXY_HTTP_PORT, 80));
    proxyHostSecure.setText(p.getProperty(PROXY_HTTPS_HOST));
    proxyPortSecure.setValue(p.getIntProperty(PROXY_HTTPS_PORT, 443));
    proxyHostFtp.setText(p.getProperty(PROXY_FTP_HOST));
    proxyPortFtp.setValue(p.getIntProperty(PROXY_FTP_PORT, 80));
    proxyHostSocks.setText(p.getProperty(PROXY_SOCKS_HOST));
    proxyPortSocks.setValue(p.getIntProperty(PROXY_SOCKS_PORT, 1080));

    // To remove in version 5
    if (StringUtil.hasText(p.getProperty("proxy.auth.pwd"))) {
      p.setProperty("proxy.auth.pwd", "");
    }

    if (p.getBooleanProperty(PROXY_MANUAL, false)) proxyConnectionRadio.doClick();
    else directConnectionRadio.doClick();
  }

  private static void formatPortTextField(JFormattedTextField port) {
    NumberFormat portFormat = NumberFormat.getNumberInstance();
    portFormat.setMinimumIntegerDigits(0);
    portFormat.setMaximumIntegerDigits(65535);
    portFormat.setMaximumFractionDigits(0);
    port.setFormatterFactory(new DefaultFormatterFactory(new NumberFormatter(portFormat)));
    port.setColumns(5);
    GuiUtils.addCheckAction(port);
  }

  private JPanel buildProxyPanel() {
    JPanel dataPanel = new JPanel();
    dataPanel.setLayout(
        new MigLayout("insets 5lp 15lp 5lp 0, fillx", "[right]rel[grow,fill][grow 0]")); // NON-NLS

    dataPanel.add(lblAddress, "newline, cell 1 0, growx 0, alignx center"); // NON-NLS
    dataPanel.add(lblPort, "growx 0, alignx center"); // NON-NLS
    dataPanel.add(proxyLabelHttp, GuiUtils.NEWLINE);
    dataPanel.add(proxyHostHttp);
    dataPanel.add(proxyPortHttp);
    dataPanel.add(proxyLabelSecure, GuiUtils.NEWLINE);
    dataPanel.add(proxyHostSecure);
    dataPanel.add(proxyPortSecure);
    dataPanel.add(proxyLabelFtp, GuiUtils.NEWLINE);
    dataPanel.add(proxyHostFtp);
    dataPanel.add(proxyPortFtp);
    dataPanel.add(proxyLabelSocks, GuiUtils.NEWLINE);
    dataPanel.add(proxyHostSocks);
    dataPanel.add(proxyPortSocks);

    dataPanel.add(proxyLabelExceptions, GuiUtils.NEWLINE);
    dataPanel.add(proxyExceptions, "spanx 2"); // NON-NLS
    return dataPanel;
  }

  public void proxyConnectionAction(boolean enable) {
    lblAddress.setEnabled(enable);
    lblPort.setEnabled(enable);
    proxyLabelHttp.setEnabled(enable);
    proxyHostHttp.setEnabled(enable);
    proxyPortHttp.setEnabled(enable);
    proxyLabelSecure.setEnabled(enable);
    proxyHostSecure.setEnabled(enable);
    proxyPortSecure.setEnabled(enable);
    proxyLabelFtp.setEnabled(enable);
    proxyHostFtp.setEnabled(enable);
    proxyPortFtp.setEnabled(enable);
    proxyLabelSocks.setEnabled(enable);
    proxyHostSocks.setEnabled(enable);
    proxyPortSocks.setEnabled(enable);
    proxyLabelExceptions.setEnabled(enable);
    proxyExceptions.setEnabled(enable);
  }

  @Override
  public void resetToDefaultValues() {
    directConnectionRadio.doClick();
  }

  @Override
  public void closeAdditionalWindow() {
    WProperties p = GuiUtils.getUICore().getLocalPersistence();
    boolean mproxy = proxyConnectionRadio.isSelected();
    p.putBooleanProperty(PROXY_MANUAL, mproxy);
    String exceptions = proxyExceptions.getText();
    p.setProperty(PROXY_EXCEPTIONS, exceptions);

    String val = proxyHostHttp.getText();
    p.setProperty(PROXY_HTTP_HOST, val);
    applyProxyProperty("http.proxyHost", val, mproxy);
    Number port = GuiUtils.getFormattedValue(proxyPortHttp);
    if (port != null) {
      p.putIntProperty(PROXY_HTTP_PORT, port.intValue());
      applyProxyPortProperty("http.proxyPort", port.intValue(), val, mproxy);
    }
    if (mproxy && StringUtil.hasText(val)) {
      applyProxyProperty("http.nonProxyHosts", exceptions, mproxy);
    }

    val = proxyHostSecure.getText();
    p.setProperty(PROXY_HTTPS_HOST, val);
    port = GuiUtils.getFormattedValue(proxyPortSecure);
    applyProxyProperty("https.proxyHost", val, mproxy);
    if (port != null) {
      p.putIntProperty(PROXY_HTTPS_PORT, port.intValue());
      applyProxyPortProperty("https.proxyPort", port.intValue(), val, mproxy);
    }
    if (mproxy && StringUtil.hasText(val)) {
      applyProxyProperty("http.nonProxyHosts", exceptions, mproxy);
    }

    val = proxyHostFtp.getText();
    p.setProperty(PROXY_FTP_HOST, val);
    applyProxyProperty("ftp.proxyHost", val, mproxy);
    port = GuiUtils.getFormattedValue(proxyPortFtp);
    if (port != null) {
      p.putIntProperty(PROXY_FTP_PORT, port.intValue());
      applyProxyPortProperty("ftp.proxyPort", port.intValue(), val, mproxy);
    }
    if (mproxy && StringUtil.hasText(val)) {
      applyProxyProperty("ftp.nonProxyHosts", exceptions, mproxy);
    }

    val = proxyHostSocks.getText();
    p.setProperty(PROXY_SOCKS_HOST, val);
    applyProxyProperty("socksProxyHost", val, mproxy);
    port = GuiUtils.getFormattedValue(proxyPortSocks);
    if (port != null) {
      p.putIntProperty(PROXY_SOCKS_PORT, port.intValue());
      applyProxyPortProperty("socksProxyPort", port.intValue(), val, mproxy);
    }
  }

  private static void applyProxyProperty(String key, String value, boolean manual) {
    if (manual && StringUtil.hasText(value)) {
      System.setProperty(key, value);
    }
  }

  private static void applyProxyPortProperty(String key, int port, String prop, boolean manual) {
    if (manual && StringUtil.hasText(prop)) {
      System.setProperty(key, String.valueOf(port));
    }
  }
}
