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

import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.util.UUID;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import org.weasis.core.api.auth.AuthMethod;
import org.weasis.core.api.auth.AuthProvider;
import org.weasis.core.api.auth.AuthRegistration;
import org.weasis.core.api.auth.DefaultAuthMethod;
import org.weasis.core.api.auth.OAuth2ServiceFactory;
import org.weasis.core.api.util.FontTools;
import org.weasis.core.api.util.NetworkUtil;
import org.weasis.core.util.StringUtil;
import org.weasis.dicom.explorer.Messages;

public class AuthMethodDialog extends JDialog {

  public static final Font TITLE_FONT = FontTools.getFont12Bold();
  public static final Color TITLE_COLOR = Color.GRAY;

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
    final JPanel rootPane = new JPanel();
    rootPane.setBorder(new EmptyBorder(10, 15, 10, 15));
    this.setContentPane(rootPane);

    rootPane.setLayout(new BoxLayout(rootPane, BoxLayout.Y_AXIS));
    if (addAuth) {
      rootPane.add(getHeader());
    }
    rootPane.add(getHeader2());
    rootPane.add(getProvider());
    rootPane.add(getRegistration());
    rootPane.add(getFooter());

    final JPanel panel1 = new JPanel();
    panel1.setAlignmentY(Component.TOP_ALIGNMENT);
    panel1.setAlignmentX(Component.LEFT_ALIGNMENT);
    panel1.setLayout(new GridBagLayout());
    rootPane.add(panel1);
  }

  private JPanel getFooter() {
    JPanel footPanel = new JPanel();
    FlowLayout flowLayout = (FlowLayout) footPanel.getLayout();
    flowLayout.setVgap(15);
    flowLayout.setAlignment(FlowLayout.RIGHT);
    flowLayout.setHgap(20);

    JButton okButton = new JButton();
    footPanel.add(okButton);

    okButton.setText(Messages.getString("PrinterDialog.ok"));
    okButton.addActionListener(e -> okButtonActionPerformed());
    JButton cancelButton = new JButton();
    footPanel.add(cancelButton);

    cancelButton.setText(Messages.getString("PrinterDialog.cancel"));
    cancelButton.addActionListener(e -> dispose());
    return footPanel;
  }

  public JPanel getHeader() {
    final JPanel content = new JPanel();
    FlowLayout flowLayout = (FlowLayout) content.getLayout();
    flowLayout.setVgap(15);
    flowLayout.setAlignment(FlowLayout.LEADING);
    flowLayout.setHgap(10);

    JLabel headersLabel = new JLabel();
    headersLabel.setText(Messages.getString("template") + StringUtil.COLON);
    content.add(headersLabel);
    content.add(comboBoxAuth);
    JButton btnfill = new JButton(Messages.getString("fill"));
    content.add(btnfill);
    btnfill.addActionListener(
        e -> {
          AuthMethod m = (AuthMethod) comboBoxAuth.getSelectedItem();
          if (OAuth2ServiceFactory.keycloackTemplate.equals(m)) {
            JTextField textFieldName = new JTextField();
            JTextField textFieldURL = new JTextField();
            JTextField textFieldRealm = new JTextField();

            Object[] inputFields = {
              Messages.getString("name"),
              textFieldName,
              "Base URL",
              textFieldURL,
              "Realm",
              textFieldRealm // NON-NLS
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
    return content;
  }

  public JPanel getHeader2() {
    final JPanel content = new JPanel();
    FlowLayout flowLayout = (FlowLayout) content.getLayout();
    flowLayout.setVgap(0);
    flowLayout.setAlignment(FlowLayout.LEADING);
    flowLayout.setHgap(10);

    JLabel idlabel = new JLabel();
    idlabel.setText("ID" + StringUtil.COLON_AND_SPACE + authMethod.getUid());
    content.add(idlabel);
    return content;
  }

  public JPanel getProvider() {
    final JPanel content = new JPanel();
    content.setLayout(new GridBagLayout());
    content.setBorder(
        BorderFactory.createCompoundBorder(
            spaceY,
            new TitledBorder(
                null,
                "Provider", // NON-NLS
                TitledBorder.DEFAULT_JUSTIFICATION,
                TitledBorder.DEFAULT_POSITION,
                TITLE_FONT,
                TITLE_COLOR)));

    buildGridConstraint(content, "Name", name, 0); // NON-NLS
    buildGridConstraint(content, "Authorization URI", authorizationURI, 1); // NON-NLS
    buildGridConstraint(content, "Token URI", tokenURI, 2); // NON-NLS
    buildGridConstraint(content, "Revoke URI", revokeTokenURI, 3); // NON-NLS
    GridBagConstraints g = new GridBagConstraints();
    g.anchor = GridBagConstraints.WEST;
    g.insets = new Insets(0, 0, 5, 5);
    g.gridx = 1;
    g.gridy = 4;
    content.add(oidc, g);
    return content;
  }

  private static void buildGridConstraint(JPanel content, String key, Component c, int col) {
    GridBagConstraints g = new GridBagConstraints();
    g.anchor = GridBagConstraints.EAST;
    g.insets = new Insets(0, 0, 5, 5);
    g.gridx = 0;
    g.gridy = col;
    content.add(new JLabel(key + StringUtil.COLON), g);
    g = new GridBagConstraints();
    g.anchor = GridBagConstraints.WEST;
    g.insets = new Insets(0, 0, 5, 5);
    g.gridx = 1;
    g.gridy = col;
    content.add(c, g);
  }

  public JPanel getRegistration() {
    final JPanel content = new JPanel();
    content.setLayout(new GridBagLayout());
    content.setBorder(
        BorderFactory.createCompoundBorder(
            spaceY,
            new TitledBorder(
                null,
                "Registration", // NON-NLS
                TitledBorder.DEFAULT_JUSTIFICATION,
                TitledBorder.DEFAULT_POSITION,
                TITLE_FONT,
                TITLE_COLOR)));
    buildGridConstraint(content, "Client ID", clientID, 0); // NON-NLS
    buildGridConstraint(content, "Client Secret", clientSecret, 1); // NON-NLS
    buildGridConstraint(content, "Scope", scope, 2); // NON-NLS
    return content;
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
