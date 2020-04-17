/*******************************************************************************
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/

package org.weasis.core.ui.pref;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.net.Authenticator;
import java.net.PasswordAuthentication;
import java.text.NumberFormat;

import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFormattedTextField;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JRadioButton;
import javax.swing.JTextField;
import javax.swing.SpringLayout;
import javax.swing.border.EmptyBorder;
import javax.swing.text.DefaultFormatterFactory;
import javax.swing.text.NumberFormatter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.gui.util.AbstractItemDialogPage;
import org.weasis.core.api.gui.util.CryptoHandler;
import org.weasis.core.api.gui.util.JMVUtils;
import org.weasis.core.api.service.BundleTools;
import org.weasis.core.api.service.WProperties;
import org.weasis.core.api.util.LocalUtil;
import org.weasis.core.util.StringUtil;
import org.weasis.core.ui.Messages;

public class ProxyPrefView extends AbstractItemDialogPage {
    private static final Logger LOGGER = LoggerFactory.getLogger(ProxyPrefView.class);

    private static final String PROXY_MANUAL = "proxy.manual"; //$NON-NLS-1$
    private static final String PROXY_EXCEPTIONS = "proxy.exceptions"; //$NON-NLS-1$
    private static final String PROXY_HTTP_HOST = "proxy.http.host"; //$NON-NLS-1$
    private static final String PROXY_HTTP_PORT = "proxy.http.port"; //$NON-NLS-1$
    private static final String PROXY_HTTPS_HOST = "proxy.https.host"; //$NON-NLS-1$
    private static final String PROXY_HTTPS_PORT = "proxy.https.port"; //$NON-NLS-1$
    private static final String PROXY_FTP_HOST = "proxy.ftp.host"; //$NON-NLS-1$
    private static final String PROXY_FTP_PORT = "proxy.ftp.port"; //$NON-NLS-1$
    private static final String PROXY_SOCKS_HOST = "proxy.socks.host"; //$NON-NLS-1$
    private static final String PROXY_SOCKS_PORT = "proxy.socks.port"; //$NON-NLS-1$

    private static final String PROXY_AUTH_REQUIRED = "proxy.auth"; //$NON-NLS-1$
    private static final String PROXY_AUTH_USER = "proxy.auth.user"; //$NON-NLS-1$
    private static final String PROXY_AUTH_PWD = "proxy.auth.pwd"; // NOSONAR //$NON-NLS-1$

    private final JRadioButton directConnectionRadio =
        new JRadioButton(Messages.getString("ProxyPrefView.direct")); //$NON-NLS-1$
    private final JRadioButton proxyConnectionRadio = new JRadioButton(Messages.getString("ProxyPrefView.manual")); //$NON-NLS-1$
    private final ButtonGroup buttonGroup = new ButtonGroup();

    private final JLabel lblType = new JLabel(Messages.getString("ProxyPrefView.type")); //$NON-NLS-1$
    private final JLabel lblAddress = new JLabel(Messages.getString("ProxyPrefView.host")); //$NON-NLS-1$
    private final JLabel lblPort = new JLabel(Messages.getString("ProxyPrefView.port")); //$NON-NLS-1$

    private final JCheckBox proxyAuthCheckBox = new JCheckBox(Messages.getString("ProxyPrefView.authentication")); //$NON-NLS-1$

    private final JTextField proxyHostHttp = new JTextField(20);
    private final JFormattedTextField proxyPortHttp = new JFormattedTextField();
    private final JTextField proxyHostSecure = new JTextField(20);
    private final JFormattedTextField proxyPortSecure = new JFormattedTextField();
    private final JTextField proxyHostFtp = new JTextField(20);
    private final JFormattedTextField proxyPortFtp = new JFormattedTextField();
    private final JTextField proxyHostSocks = new JTextField(20);
    private final JFormattedTextField proxyPortSocks = new JFormattedTextField();
    private final JTextField proxyExceptions = new JTextField(27);

    private final JLabel proxyLabelHttp = new JLabel("HTTP" + StringUtil.COLON); //$NON-NLS-1$
    private final JLabel proxyLabelSecure = new JLabel(Messages.getString("ProxyPrefView.secure") + StringUtil.COLON); //$NON-NLS-1$
    private final JLabel proxyLabelFtp = new JLabel("FTP" + StringUtil.COLON); //$NON-NLS-1$
    private final JLabel proxyLabelSocks = new JLabel("SOCKS" + StringUtil.COLON); //$NON-NLS-1$
    private final JLabel proxyLabelExceptions = new JLabel(Messages.getString("ProxyPrefView.exceptions") + StringUtil.COLON); //$NON-NLS-1$

    private final JLabel userLabel = new JLabel(Messages.getString("ProxyPrefView.user") + StringUtil.COLON); //$NON-NLS-1$
    private final JLabel passLabel = new JLabel(Messages.getString("ProxyPrefView.pwd") + StringUtil.COLON); //$NON-NLS-1$
    private final JTextField proxyUser = new JTextField(15);
    private final JPasswordField proxyPass = new JPasswordField(15);

    private final JPanel dataPanel = new JPanel();

    public ProxyPrefView() {
        super(Messages.getString("ProxyPrefView.proxy")); //$NON-NLS-1$
        initialize();
    }

    private void initialize() {
        setComponentPosition(5);
        setBorder(new EmptyBorder(15, 10, 10, 10));
        BorderLayout borderLayout = new BorderLayout();
        setLayout(borderLayout);

        formatPortTextField(proxyPortHttp);
        formatPortTextField(proxyPortSecure);
        formatPortTextField(proxyPortFtp);
        formatPortTextField(proxyPortSocks);

        add(buildProxyPanel(), BorderLayout.CENTER);

        JPanel bottomPanel = new JPanel();
        FlowLayout flowLayout1 = (FlowLayout) bottomPanel.getLayout();
        flowLayout1.setHgap(10);
        flowLayout1.setAlignment(FlowLayout.RIGHT);
        flowLayout1.setVgap(7);
        add(bottomPanel, BorderLayout.SOUTH);

        JButton btnNewButton = new JButton(org.weasis.core.ui.Messages.getString("restore.values")); //$NON-NLS-1$
        bottomPanel.add(JMVUtils.createHelpButton("proxy", true)); //$NON-NLS-1$
        bottomPanel.add(btnNewButton);
        btnNewButton.addActionListener(e -> resetoDefaultValues());

        this.buttonGroup.add(directConnectionRadio);
        this.buttonGroup.add(proxyConnectionRadio);
        directConnectionRadio.addActionListener(e -> proxyConnectionAction(false));
        proxyConnectionRadio.addActionListener(e -> proxyConnectionAction(true));
        proxyAuthCheckBox.addActionListener(e -> {
            proxyAuthenticationAction(proxyAuthCheckBox.isSelected());
            if (!proxyAuthCheckBox.isSelected()) {
                proxyUser.setText(null);
                proxyPass.setText(null);
            }
        });
        initState();
    }

    private void initState() {
        WProperties p = BundleTools.LOCAL_UI_PERSISTENCE;

        proxyExceptions.setText(p.getProperty(PROXY_EXCEPTIONS));

        proxyHostHttp.setText(p.getProperty(PROXY_HTTP_HOST));
        proxyPortHttp.setValue(p.getIntProperty(PROXY_HTTP_PORT, 80));
        proxyHostSecure.setText(p.getProperty(PROXY_HTTPS_HOST));
        proxyPortSecure.setValue(p.getIntProperty(PROXY_HTTPS_PORT, 443));
        proxyHostFtp.setText(p.getProperty(PROXY_FTP_HOST));
        proxyPortFtp.setValue(p.getIntProperty(PROXY_FTP_PORT, 80));
        proxyHostSocks.setText(p.getProperty(PROXY_SOCKS_HOST));
        proxyPortSocks.setValue(p.getIntProperty(PROXY_SOCKS_PORT, 1080));

        String user = p.getProperty(PROXY_AUTH_USER);
        String pass = ""; //$NON-NLS-1$
        try {
            byte[] pwd = p.getByteArrayProperty(PROXY_AUTH_PWD, null);
            if (pwd != null) {
                pwd = CryptoHandler.decrypt(pwd, PROXY_AUTH_REQUIRED);
                if (pwd != null && pwd.length > 0) {
                    pass = new String(pwd);
                }
            }
        } catch (Exception e) {
            LOGGER.error("Cannot store the proxy password", e); //$NON-NLS-1$
        }

        proxyUser.setText(user);
        proxyPass.setText(pass);
        proxyAuthCheckBox.setSelected(p.getBooleanProperty(PROXY_AUTH_REQUIRED, false));

        if (p.getBooleanProperty(PROXY_MANUAL, false))
            proxyConnectionRadio.doClick();
        else
            directConnectionRadio.doClick();
    }

    private static void formatPortTextField(JFormattedTextField port) {
        NumberFormat portFormat = LocalUtil.getNumberInstance();
        portFormat.setMinimumIntegerDigits(0);
        portFormat.setMaximumIntegerDigits(65535);
        portFormat.setMaximumFractionDigits(0);
        port.setFormatterFactory(new DefaultFormatterFactory(new NumberFormatter(portFormat)));
        port.setColumns(5);
        JMVUtils.setPreferredWidth(port, 60);
        JMVUtils.addCheckAction(port);
    }

    private JPanel buildProxyPanel() {

        dataPanel.add(directConnectionRadio);
        dataPanel.add(proxyConnectionRadio);
        dataPanel.add(lblType);
        dataPanel.add(lblAddress);
        dataPanel.add(lblPort);
        dataPanel.add(proxyLabelHttp);
        dataPanel.add(proxyHostHttp);
        dataPanel.add(proxyPortHttp);
        dataPanel.add(proxyLabelSecure);
        dataPanel.add(proxyHostSecure);
        dataPanel.add(proxyPortSecure);
        dataPanel.add(proxyLabelFtp);
        dataPanel.add(proxyHostFtp);
        dataPanel.add(proxyPortFtp);
        dataPanel.add(proxyLabelSocks);
        dataPanel.add(proxyHostSocks);
        dataPanel.add(proxyPortSocks);
        dataPanel.add(proxyLabelExceptions);
        dataPanel.add(proxyExceptions);
        dataPanel.add(proxyAuthCheckBox);
        dataPanel.add(proxyUser);
        dataPanel.add(userLabel);
        dataPanel.add(proxyPass);
        dataPanel.add(passLabel);

        SpringLayout layout = new SpringLayout();
        layout.putConstraint(SpringLayout.WEST, proxyExceptions, 3, SpringLayout.EAST, proxyLabelExceptions);

        layout.putConstraint(SpringLayout.WEST, directConnectionRadio, 20, SpringLayout.WEST, dataPanel);
        layout.putConstraint(SpringLayout.NORTH, directConnectionRadio, 20, SpringLayout.NORTH, dataPanel);

        layout.putConstraint(SpringLayout.WEST, proxyConnectionRadio, 20, SpringLayout.WEST, dataPanel);
        layout.putConstraint(SpringLayout.NORTH, proxyConnectionRadio, 20, SpringLayout.SOUTH, directConnectionRadio);
        layout.putConstraint(SpringLayout.WEST, lblType, 40, SpringLayout.WEST, dataPanel);
        layout.putConstraint(SpringLayout.NORTH, lblType, 10, SpringLayout.SOUTH, proxyConnectionRadio);
        layout.putConstraint(SpringLayout.NORTH, lblAddress, 0, SpringLayout.NORTH, lblType);
        layout.putConstraint(SpringLayout.WEST, lblAddress, 0, SpringLayout.WEST, proxyHostHttp);
        layout.putConstraint(SpringLayout.NORTH, lblPort, 0, SpringLayout.NORTH, lblType);
        layout.putConstraint(SpringLayout.WEST, lblPort, 100, SpringLayout.EAST, lblAddress);

        layout.putConstraint(SpringLayout.WEST, proxyLabelHttp, 40, SpringLayout.WEST, dataPanel);
        layout.putConstraint(SpringLayout.NORTH, proxyLabelHttp, 7, SpringLayout.SOUTH, lblType);
        layout.putConstraint(SpringLayout.WEST, proxyHostHttp, 10, SpringLayout.EAST, proxyLabelSecure);
        layout.putConstraint(SpringLayout.NORTH, proxyHostHttp, 7, SpringLayout.SOUTH, lblType);
        layout.putConstraint(SpringLayout.NORTH, proxyPortHttp, 0, SpringLayout.NORTH, proxyHostHttp);
        layout.putConstraint(SpringLayout.WEST, proxyPortHttp, 0, SpringLayout.WEST, lblPort);

        layout.putConstraint(SpringLayout.WEST, proxyLabelSecure, 40, SpringLayout.WEST, dataPanel);
        layout.putConstraint(SpringLayout.NORTH, proxyLabelSecure, 7, SpringLayout.SOUTH, proxyHostHttp);
        layout.putConstraint(SpringLayout.WEST, proxyHostSecure, 10, SpringLayout.EAST, proxyLabelSecure);
        layout.putConstraint(SpringLayout.NORTH, proxyHostSecure, 7, SpringLayout.SOUTH, proxyHostHttp);
        layout.putConstraint(SpringLayout.NORTH, proxyPortSecure, 0, SpringLayout.NORTH, proxyHostSecure);
        layout.putConstraint(SpringLayout.WEST, proxyPortSecure, 0, SpringLayout.WEST, lblPort);

        layout.putConstraint(SpringLayout.WEST, proxyLabelFtp, 40, SpringLayout.WEST, dataPanel);
        layout.putConstraint(SpringLayout.NORTH, proxyLabelFtp, 7, SpringLayout.SOUTH, proxyHostSecure);
        layout.putConstraint(SpringLayout.WEST, proxyHostFtp, 10, SpringLayout.EAST, proxyLabelSecure);
        layout.putConstraint(SpringLayout.NORTH, proxyHostFtp, 7, SpringLayout.SOUTH, proxyHostSecure);
        layout.putConstraint(SpringLayout.NORTH, proxyPortFtp, 0, SpringLayout.NORTH, proxyHostFtp);
        layout.putConstraint(SpringLayout.WEST, proxyPortFtp, 0, SpringLayout.WEST, lblPort);

        layout.putConstraint(SpringLayout.WEST, proxyLabelSocks, 40, SpringLayout.WEST, dataPanel);
        layout.putConstraint(SpringLayout.NORTH, proxyLabelSocks, 7, SpringLayout.SOUTH, proxyHostFtp);
        layout.putConstraint(SpringLayout.WEST, proxyHostSocks, 10, SpringLayout.EAST, proxyLabelSecure);
        layout.putConstraint(SpringLayout.NORTH, proxyHostSocks, 7, SpringLayout.SOUTH, proxyHostFtp);
        layout.putConstraint(SpringLayout.NORTH, proxyPortSocks, 0, SpringLayout.NORTH, proxyHostSocks);
        layout.putConstraint(SpringLayout.WEST, proxyPortSocks, 0, SpringLayout.WEST, lblPort);

        layout.putConstraint(SpringLayout.WEST, proxyLabelExceptions, 40, SpringLayout.WEST, dataPanel);
        layout.putConstraint(SpringLayout.NORTH, proxyLabelExceptions, 15, SpringLayout.SOUTH, proxyHostSocks);
        layout.putConstraint(SpringLayout.NORTH, proxyExceptions, 15, SpringLayout.SOUTH, proxyHostSocks);

        layout.putConstraint(SpringLayout.NORTH, proxyAuthCheckBox, 15, SpringLayout.SOUTH, proxyExceptions);
        layout.putConstraint(SpringLayout.WEST, proxyAuthCheckBox, 40, SpringLayout.WEST, dataPanel);

        layout.putConstraint(SpringLayout.WEST, userLabel, 60, SpringLayout.WEST, dataPanel);
        layout.putConstraint(SpringLayout.NORTH, userLabel, 5, SpringLayout.SOUTH, proxyAuthCheckBox);
        layout.putConstraint(SpringLayout.NORTH, proxyUser, 5, SpringLayout.SOUTH, proxyAuthCheckBox);
        layout.putConstraint(SpringLayout.WEST, proxyUser, 3, SpringLayout.EAST, userLabel);
        layout.putConstraint(SpringLayout.NORTH, passLabel, 7, SpringLayout.SOUTH, proxyUser);
        layout.putConstraint(SpringLayout.WEST, passLabel, 60, SpringLayout.WEST, dataPanel);
        layout.putConstraint(SpringLayout.WEST, proxyPass, 3, SpringLayout.EAST, userLabel);
        layout.putConstraint(SpringLayout.NORTH, proxyPass, 0, SpringLayout.NORTH, passLabel);

        dataPanel.setLayout(layout);

        return dataPanel;
    }

    public void proxyConnectionAction(boolean enable) {
        lblType.setEnabled(enable);
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

        proxyAuthCheckBox.setEnabled(enable);
        proxyAuthenticationAction(enable ? proxyAuthCheckBox.isSelected() : enable);
    }

    public void proxyAuthenticationAction(boolean auth) {
        userLabel.setEnabled(auth);
        proxyUser.setEnabled(auth);
        passLabel.setEnabled(auth);
        proxyPass.setEnabled(auth);
    }

    @Override
    public void resetoDefaultValues() {
        directConnectionRadio.doClick();
    }

    @Override
    public void closeAdditionalWindow() {
        WProperties p = BundleTools.LOCAL_UI_PERSISTENCE;
        boolean mproxy = proxyConnectionRadio.isSelected();
        p.putBooleanProperty(PROXY_MANUAL, mproxy);
        String exceptions = proxyExceptions.getText();
        p.setProperty(PROXY_EXCEPTIONS, exceptions);

        String val = proxyHostHttp.getText();
        p.setProperty(PROXY_HTTP_HOST, val);
        applyProxyProperty("http.proxyHost", val, mproxy); //$NON-NLS-1$
        Number port = JMVUtils.getFormattedValue(proxyPortHttp);
        if (port != null) {
            p.putIntProperty(PROXY_HTTP_PORT, port.intValue());
            applyProxyPortProperty("http.proxyPort", port.intValue(), val, mproxy); //$NON-NLS-1$
        }
        if (mproxy && StringUtil.hasText(val)) {
            applyProxyProperty("http.nonProxyHosts", exceptions, mproxy); //$NON-NLS-1$
        }
        
        val = proxyHostSecure.getText();
        p.setProperty(PROXY_HTTPS_HOST, val);
        port = JMVUtils.getFormattedValue(proxyPortSecure);
        applyProxyProperty("https.proxyHost", val, mproxy); //$NON-NLS-1$
        if (port != null) {
            p.putIntProperty(PROXY_HTTPS_PORT, port.intValue());
            applyProxyPortProperty("https.proxyPort", port.intValue(), val, mproxy); //$NON-NLS-1$
        }
        if (mproxy && StringUtil.hasText(val)) {
            applyProxyProperty("http.nonProxyHosts", exceptions, mproxy); //$NON-NLS-1$
        }
        
        val = proxyHostFtp.getText();
        p.setProperty(PROXY_FTP_HOST, val);
        applyProxyProperty("ftp.proxyHost", val, mproxy); //$NON-NLS-1$
        port = JMVUtils.getFormattedValue(proxyPortFtp);
        if (port != null) {
            p.putIntProperty(PROXY_FTP_PORT, port.intValue());
            applyProxyPortProperty("ftp.proxyPort", port.intValue(), val, mproxy); //$NON-NLS-1$
        }
        if (mproxy && StringUtil.hasText(val)) {
            applyProxyProperty("ftp.nonProxyHosts", exceptions, mproxy); //$NON-NLS-1$
        }
        
        val = proxyHostSocks.getText();
        p.setProperty(PROXY_SOCKS_HOST, val);
        applyProxyProperty("socksProxyHost", val, mproxy); //$NON-NLS-1$
        port = JMVUtils.getFormattedValue(proxyPortSocks);
        if (port != null) {
            p.putIntProperty(PROXY_SOCKS_PORT, port.intValue());
            applyProxyPortProperty("socksProxyPort", port.intValue(), val, mproxy); //$NON-NLS-1$
        }

        boolean auth = proxyAuthCheckBox.isSelected();
        p.putBooleanProperty(PROXY_AUTH_REQUIRED, auth);
        val = proxyUser.getText();
        p.setProperty(PROXY_AUTH_USER, val);
        try {
            char[] pwd = proxyPass.getPassword();
            if (pwd != null && pwd.length > 0) {
                if (auth) {
                    String authPassword = new String(pwd);
                    applyPasswordAuthentication(val, authPassword);
                    applyProxyProperty("http.proxyUser", val, mproxy); //$NON-NLS-1$
                    applyProxyProperty("http.proxyPassword", authPassword, mproxy); //$NON-NLS-1$
                }
                byte[] b = new byte[pwd.length];
                for (int i = 0; i < b.length; i++) {
                    b[i] = (byte) pwd[i];
                }
                p.putByteArrayProperty(PROXY_AUTH_PWD, CryptoHandler.encrypt(b, PROXY_AUTH_REQUIRED));
            } else {
                p.putByteArrayProperty(PROXY_AUTH_PWD, null);
            }
        } catch (Exception ex) {
            LOGGER.error("Cannot store the proxy user", ex); //$NON-NLS-1$
        }

    }

    private static void applyPasswordAuthentication(final String authUser, final String authPassword ) {
        Authenticator.setDefault(
           new Authenticator() {
              @Override
              public PasswordAuthentication getPasswordAuthentication() {
                 return new PasswordAuthentication(
                       authUser, authPassword.toCharArray());
              }
           }
        );
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