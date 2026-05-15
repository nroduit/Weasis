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

import java.awt.FlowLayout;
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
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.gui.util.WinUtil;
import org.weasis.core.api.net.NetworkUtil;
import org.weasis.core.api.net.auth.AuthMethod;
import org.weasis.core.api.net.auth.AuthProvider;
import org.weasis.core.api.net.auth.AuthRegistration;
import org.weasis.core.api.net.auth.DefaultAuthMethod;
import org.weasis.core.api.net.auth.OAuth2ServiceFactory;
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
  private final JTextField audience = new JTextField(50);

  public AuthMethodDialog(
      Window parent, String title, AuthMethod authMethod, JComboBox<AuthMethod> parentCombobox) {
    super(parent, title, ModalityType.APPLICATION_MODAL);
    this.parentCombobox = parentCombobox;
    comboBoxAuth.addItem(OAuth2ServiceFactory.GOOGLE_AUTH_TEMPLATE);
    comboBoxAuth.addItem(OAuth2ServiceFactory.KEYCLOAK_TEMPLATE);
    boolean addAuth = false;
    if (authMethod == null) {
      addAuth = true;
      this.authMethod =
          new DefaultAuthMethod(
              UUID.randomUUID().toString(),
              new AuthProvider(null, null, null, null, false),
              AuthRegistration.empty());
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
    JButton helpButton = GuiUtils.createHelpButton("dicomweb-config"); // NON-NLS
    panel.add(
        GuiUtils.getFlowLayoutPanel(
            FlowLayout.TRAILING,
            0,
            0,
            helpButton,
            GuiUtils.boxHorizontalStrut(15),
            okButton,
            GuiUtils.boxHorizontalStrut(15),
            cancelButton),
        "newline, spanx, gap 15lp 0lp 10lp 10lp, alignx trailing"); // NON-NLS
  }

  public void buildHeader(JPanel panel) {
    JLabel headersLabel = new JLabel(Messages.getString("template") + StringUtil.COLON);
    JButton buttonFill = new JButton(Messages.getString("fill"));
    buttonFill.addActionListener(
        e -> {
          AuthMethod m = (AuthMethod) comboBoxAuth.getSelectedItem();
          if (OAuth2ServiceFactory.KEYCLOAK_TEMPLATE.equals(m)) {
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
                    WinUtil.getValidComponent(this),
                    inputFields,
                    Messages.getString("enter.keycloak.inf"),
                    JOptionPane.OK_CANCEL_OPTION,
                    JOptionPane.PLAIN_MESSAGE);

            if (option == JOptionPane.OK_OPTION) {
              AuthProvider p =
                  OAuth2ServiceFactory.buildKeycloakProvider(
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
    MigLayout layout = new MigLayout("fillx, insets 5lp", "[right]rel[grow,fill]"); // NON-NLS
    JPanel panel = new JPanel(layout);
    panel.setBorder(
        BorderFactory.createCompoundBorder(
            spaceY, GuiUtils.getTitledBorder("Provider"))); // NON-NLS

    panel.add(new JLabel("Name" + StringUtil.COLON), GuiUtils.NEWLINE); // NON-NLS
    panel.add(name, "");
    GuiUtils.addValidation(name, StringUtil::hasText);
    panel.add(new JLabel("Authorization URI" + StringUtil.COLON), GuiUtils.NEWLINE); // NON-NLS
    panel.add(authorizationURI, "");
    GuiUtils.addValidation(authorizationURI, NetworkUtil::isValidUrlLikeUri);
    panel.add(new JLabel("Token URI" + StringUtil.COLON), GuiUtils.NEWLINE); // NON-NLS
    GuiUtils.addValidation(tokenURI, NetworkUtil::isValidUrlLikeUri);
    panel.add(tokenURI, "");
    panel.add(new JLabel("Revoke URI" + StringUtil.COLON), GuiUtils.NEWLINE); // NON-NLS
    GuiUtils.addValidation(revokeTokenURI, NetworkUtil::isValidUrlLikeUri);
    panel.add(revokeTokenURI, "");
    return panel;
  }

  public JPanel getRegistration() {
    MigLayout layout = new MigLayout("fillx, ins 5lp", "[right]rel[grow,fill]"); // NON-NLS
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
    panel.add(new JLabel("Audience" + StringUtil.COLON), GuiUtils.NEWLINE); // NON-NLS
    panel.add(audience, "");
    return panel;
  }

  private void fill(AuthMethod authMethod) {
    if (authMethod != null) {
      AuthProvider provider = authMethod.getAuthProvider();
      name.setText(provider.name());
      authorizationURI.setText(provider.authorizationUri());
      tokenURI.setText(provider.tokenUri());
      revokeTokenURI.setText(provider.revokeTokenUri());
      oidc.setSelected(provider.openId());

      AuthRegistration reg = authMethod.getAuthRegistration();
      clientID.setText(reg.clientId());
      clientSecret.setText(reg.clientSecret());
      scope.setText(reg.scope());
      audience.setText(reg.audience());
    }
  }

  private void okButtonActionPerformed() {
    String n = name.getText();
    String authURI = authorizationURI.getText();
    String tURI = tokenURI.getText();
    String rURI = revokeTokenURI.getText();

    if (!StringUtil.hasText(n)
        || !StringUtil.hasText(authURI)
        || !NetworkUtil.isValidUrlLikeUri(authURI)
        || !NetworkUtil.isValidUrlLikeUri(tURI)
        || !NetworkUtil.isValidUrlLikeUri(tURI)) {
      JOptionPane.showMessageDialog(
          WinUtil.getValidComponent(this),
          Messages.getString("missing.fields"),
          Messages.getString("error"),
          JOptionPane.ERROR_MESSAGE);
      return;
    }

    // Create new AuthProvider since it's a record (immutable)
    AuthProvider currentProvider = authMethod.getAuthProvider();
    boolean addMethod = currentProvider.name() == null;
    AuthProvider newProvider = new AuthProvider(n, authURI, tURI, rURI, oidc.isSelected());

    // Create new AuthRegistration since it's a record (immutable)
    DefaultAuthMethod updatedAuth = updateAuthMethod(newProvider);

    // Update the parent combobox
    if (addMethod) {
      parentCombobox.addItem(updatedAuth);
      parentCombobox.setSelectedItem(updatedAuth);
    } else {
      // Replace the existing item in the combobox
      int index = -1;
      for (int i = 0; i < parentCombobox.getItemCount(); i++) {
        if (parentCombobox.getItemAt(i).getUid().equals(authMethod.getUid())) {
          index = i;
          break;
        }
      }
      if (index >= 0) {
        parentCombobox.removeItemAt(index);
        parentCombobox.insertItemAt(updatedAuth, index);
        parentCombobox.setSelectedIndex(index);
      }
    }

    comboBoxAuth.repaint();

    dispose();
  }

  private DefaultAuthMethod updateAuthMethod(AuthProvider newProvider) {
    AuthRegistration newRegistration =
        new AuthRegistration(
            clientID.getText(),
            clientSecret.getText(),
            scope.getText(),
            audience.getText(),
            authMethod.getAuthRegistration().user() // preserve existing user
            );

    // Since AuthMethod implementations might be immutable too, we need to handle this properly
    DefaultAuthMethod updatedAuth =
        new DefaultAuthMethod(authMethod.getUid(), newProvider, newRegistration);
    updatedAuth.setLocal(authMethod.isLocal());
    updatedAuth.setCode(authMethod.getCode());
    return updatedAuth;
  }
}
