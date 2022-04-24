/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.explorer.pref.node;

import java.awt.Window;
import java.util.UUID;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.border.Border;
import net.miginfocom.swing.MigLayout;
import org.weasis.core.api.auth.AuthMethod;
import org.weasis.core.api.auth.AuthProvider;
import org.weasis.core.api.auth.AuthRegistration;
import org.weasis.core.api.auth.DefaultAuthMethod;
import org.weasis.core.api.auth.OAuth2ServiceFactory;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.util.NetworkUtil;
import org.weasis.core.util.StringUtil;
import org.weasis.dicom.explorer.Messages;

public class AuthMethodDialog extends JDialog {

  private final AuthMethod authMethod;
  private final JComboBox<AuthMethod> parentCombobox;
  private final JComboBox<AuthMethod> comboBoxAuth = new JComboBox<>();
  private final Border spaceY = BorderFactory.createEmptyBorder(10, 3, 0, 3);
  private final JTextField name = new JTextField(50);
  private final JTextField authorizationURI = new JTextField(50);
  private final JTextField tokenURI = new JTextField(50);
  private final JTextField revokeTokenURI = new JTextField(50);
  private final JCheckBox oidc = new JCheckBox("OpenID Connect"); // NON-NLS
  private final JTextField clientID = new JTextField(50);
  private final JTextField clientSecret = new JTextField(50);
  private final JTextField scope = new JTextField(50);

  public AuthMethodDialog(
      Window parent, String title, AuthMethod authMethod, JComboBox<AuthMethod> parentCombobox) {
    super(parent, title, ModalityType.APPLICATION_MODAL);
    this.parentCombobox = parentCombobox;
    comboBoxAuth.addItem(OAuth2ServiceFactory.googleAuthTemplate);
    comboBoxAuth.addItem(OAuth2ServiceFactory.keycloackTemplate);
    boolean addAuth = false;
    if (authMethod == null) {
      addAuth = true;
      this.authMethod =
          new DefaultAuthMethod(
              UUID.randomUUID().toString(),
              new AuthProvider(null, null, null, null, false),
              new AuthRegistration(null, null, null));
      this.authMethod.setLocal(true);
    } else {
      this.authMethod = authMethod;
    }
    initComponents(addAuth);
    fill(this.authMethod);
    pack();
  }

  private void initComponents(boolean addAuth) {
    JPanel panel = new JPanel();
    panel.setLayout(new MigLayout("insets 10lp 15lp 10lp 15lp", "[grow ,fill][grow 0]")); // NON-NLS

    if (addAuth) {
      buildHeader(panel);
    }
    panel.add(
        new JLabel("ID" + StringUtil.COLON_AND_SPACE + authMethod.getUid()),
        "newline, spanx"); // NON-NLS
    panel.add(getProvider(), "newline, spanx"); // NON-NLS
    panel.add(getRegistration(), "newline, spanx"); // NON-NLS
    buildFooter(panel);
    setContentPane(panel);
  }

  private void buildFooter(JPanel panel) {
    JButton okButton = new JButton(Messages.getString("PrinterDialog.ok"));
    okButton.addActionListener(e -> okButtonActionPerformed());
    JButton cancelButton = new JButton(Messages.getString("PrinterDialog.cancel"));
    cancelButton.addActionListener(e -> dispose());
    panel.add(okButton, "newline, skip, growx 0, alignx trailing"); // NON-NLS
    panel.add(cancelButton, "gap 15lp 0lp 10lp 10lp"); // NON-NLS
  }

  public void buildHeader(JPanel panel) {
    JLabel headersLabel = new JLabel(Messages.getString("template") + StringUtil.COLON);
    JButton buttonFill = new JButton(Messages.getString("fill"));
    buttonFill.addActionListener(
        e -> {
          AuthMethod m = (AuthMethod) comboBoxAuth.getSelectedItem();
          if (OAuth2ServiceFactory.keycloackTemplate.equals(m)) {
            JTextField textFieldName = new JTextField();
            JTextField textFieldURL = new JTextField();
            JTextField textFieldRealm = new JTextField();

            Object[] inputFields = {
              Messages.getString("name"),
              textFieldName,
              "Base URL", // NON-NLS
              textFieldURL,
              "Realm", // NON-NLS
              textFieldRealm
            };

            int option =
                JOptionPane.showConfirmDialog(
                    this,
                    inputFields,
                    Messages.getString("enter.keycloak.inf"),
                    JOptionPane.OK_CANCEL_OPTION,
                    JOptionPane.PLAIN_MESSAGE);

            if (option == JOptionPane.OK_OPTION) {
              AuthProvider p =
                  OAuth2ServiceFactory.buildKeycloackProvider(
                      textFieldName.getText(), textFieldURL.getText(), textFieldRealm.getText());
              m = new DefaultAuthMethod(UUID.randomUUID().toString(), p, m.getAuthRegistration());
              m.setLocal(true);
            }
          }
          fill(m);
        });

    panel.add(
        GuiUtils.getFlowLayoutPanel(
            0, 0, headersLabel, comboBoxAuth, GuiUtils.boxHorizontalStrut(15), buttonFill),
        GuiUtils.NEWLINE);
  }

  public JPanel getProvider() {
    MigLayout layout = new MigLayout("fillx", "[right]rel[grow,fill]"); // NON-NLS
    JPanel panel = new JPanel(layout);
    panel.setBorder(
        BorderFactory.createCompoundBorder(
            spaceY, GuiUtils.getTitledBorder("Provider"))); // NON-NLS

    panel.add(new JLabel("Name" + StringUtil.COLON), GuiUtils.NEWLINE); // NON-NLS
    panel.add(name, "");
    panel.add(new JLabel("Authorization URI" + StringUtil.COLON), GuiUtils.NEWLINE); // NON-NLS
    panel.add(authorizationURI, "");
    panel.add(new JLabel("Token URI" + StringUtil.COLON), GuiUtils.NEWLINE); // NON-NLS
    panel.add(tokenURI, "");
    panel.add(new JLabel("Revoke URI" + StringUtil.COLON), GuiUtils.NEWLINE); // NON-NLS
    panel.add(revokeTokenURI, "");
    return panel;
  }

  public JPanel getRegistration() {
    MigLayout layout = new MigLayout("fillx", "[right]rel[grow,fill]"); // NON-NLS
    JPanel panel = new JPanel(layout);
    panel.setBorder(
        BorderFactory.createCompoundBorder(
            spaceY, GuiUtils.getTitledBorder("Registration"))); // NON-NLS

    panel.add(new JLabel("Client ID" + StringUtil.COLON), GuiUtils.NEWLINE); // NON-NLS
    panel.add(clientID, "");
    panel.add(new JLabel("Client Secret" + StringUtil.COLON), GuiUtils.NEWLINE); // NON-NLS
    panel.add(clientSecret, "");
    panel.add(new JLabel("Scope" + StringUtil.COLON), GuiUtils.NEWLINE); // NON-NLS
    panel.add(scope, "");
    return panel;
  }

  private void fill(AuthMethod authMethod) {
    if (authMethod != null) {
      AuthProvider provider = authMethod.getAuthProvider();
      name.setText(provider.getName());
      authorizationURI.setText(provider.getAuthorizationUri());
      tokenURI.setText(provider.getTokenUri());
      revokeTokenURI.setText(provider.getRevokeTokenUri());
      oidc.setSelected(provider.getOpenId());

      AuthRegistration reg = authMethod.getAuthRegistration();
      clientID.setText(reg.getClientId());
      clientSecret.setText(reg.getClientSecret());
      scope.setText(reg.getScope());
    }
  }

  private void okButtonActionPerformed() {
    String n = name.getText();
    String authURI = authorizationURI.getText();
    String tURI = tokenURI.getText();
    String rURI = revokeTokenURI.getText();

    if (!StringUtil.hasText(n)
        || !StringUtil.hasText(authURI)
        || !NetworkUtil.urlValidator(authURI)
        || !StringUtil.hasText(tURI)
        || !NetworkUtil.urlValidator(tURI)) {
      JOptionPane.showMessageDialog(
          this,
          Messages.getString("missing.fields"),
          Messages.getString("error"),
          JOptionPane.ERROR_MESSAGE);
      return;
    }

    AuthProvider provider = authMethod.getAuthProvider();
    boolean addMethod = provider.getName() == null;
    provider.setName(n);
    provider.setAuthorizationUri(authURI);
    provider.setTokenUri(tURI);
    provider.setRevokeTokenUri(rURI);
    provider.setOpenId(oidc.isSelected());

    comboBoxAuth.repaint();

    AuthRegistration reg = authMethod.getAuthRegistration();
    reg.setClientId(clientID.getText());
    reg.setClientSecret(clientSecret.getText());
    reg.setScope(scope.getText());

    if (addMethod) {
      parentCombobox.addItem(authMethod);
      parentCombobox.setSelectedItem(authMethod);
    }
    AuthenticationPersistence.getMethods().put(authMethod.getUid(), authMethod);
    AuthenticationPersistence.saveMethod();
    dispose();
  }
}
