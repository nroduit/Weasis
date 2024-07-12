/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.send;

import java.util.List;
import java.util.Objects;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import org.dcm4che3.net.Status;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.auth.AuthMethod;
import org.weasis.core.api.auth.OAuth2ServiceFactory;
import org.weasis.core.api.gui.util.AppProperties;
import org.weasis.core.api.gui.util.GuiExecutor;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.gui.util.WinUtil;
import org.weasis.core.util.StringUtil;
import org.weasis.core.util.StringUtil.Suffix;
import org.weasis.dicom.explorer.CheckTreeModel;
import org.weasis.dicom.explorer.DicomModel;
import org.weasis.dicom.explorer.ExportDicomView;
import org.weasis.dicom.explorer.pref.node.AbstractDicomNode;
import org.weasis.dicom.explorer.pref.node.AbstractDicomNode.UsageType;
import org.weasis.dicom.explorer.pref.node.AuthenticationPersistence;
import org.weasis.dicom.explorer.pref.node.DefaultDicomNode;
import org.weasis.dicom.explorer.pref.node.DicomWebNode;
import org.weasis.dicom.op.CStore;
import org.weasis.dicom.param.AdvancedParams;
import org.weasis.dicom.param.ConnectOptions;
import org.weasis.dicom.param.DicomNode;
import org.weasis.dicom.param.DicomProgress;
import org.weasis.dicom.param.DicomState;
import org.weasis.dicom.web.ContentType;

public class SendDicomView extends ExportDicomView {

  private static final Logger LOGGER = LoggerFactory.getLogger(SendDicomView.class);

  private static final String LAST_SEL_NODE = "lastSelNode";

  private final JComboBox<AbstractDicomNode> comboNode = new JComboBox<>();
  private AuthMethod authMethod;

  public SendDicomView(DicomModel dicomModel, CheckTreeModel treeModel) {
    super(Messages.getString("SendDicomView.title"), 5, dicomModel, treeModel);
    initialize();
  }

  @Override
  public SendDicomView initGUI() {
    final JLabel lblDest =
        new JLabel(Messages.getString("SendDicomView.destination") + StringUtil.COLON);
    AbstractDicomNode.addTooltipToComboList(comboNode);

    add(GuiUtils.getFlowLayoutPanel(ITEM_SEPARATOR_SMALL, 0, lblDest, comboNode));
    add(GuiUtils.boxVerticalStrut(ITEM_SEPARATOR));

    super.initGUI();
    return this;
  }

  protected void initialize() {
    AbstractDicomNode.loadDicomNodes(comboNode, AbstractDicomNode.Type.DICOM, UsageType.STORAGE);
    AbstractDicomNode.loadDicomNodes(comboNode, AbstractDicomNode.Type.WEB, UsageType.STORAGE);
    String desc = SendDicomFactory.EXPORT_PERSISTENCE.getProperty(LAST_SEL_NODE);
    AbstractDicomNode.selectDicomNode(comboNode, desc);
  }

  public void applyChange() {
    final AbstractDicomNode node = (AbstractDicomNode) comboNode.getSelectedItem();
    if (node != null) {
      SendDicomFactory.EXPORT_PERSISTENCE.setProperty(LAST_SEL_NODE, node.getDescription());
    }
  }

  @Override
  public void closeAdditionalWindow() {
    applyChange();
    super.closeAdditionalWindow();
  }

  protected boolean exportAction(List<String> files, DicomProgress dicomProgress) {
    Object selectedItem = comboNode.getSelectedItem();
    if (selectedItem instanceof final DefaultDicomNode node) {
      String weasisAet =
          GuiUtils.getUICore()
              .getSystemPreferences()
              .getProperty("weasis.aet", "WEASIS_AE"); // NON-NLS
      AdvancedParams params = new AdvancedParams();
      ConnectOptions connectOptions = new ConnectOptions();
      connectOptions.setConnectTimeout(3000);
      connectOptions.setAcceptTimeout(5000);
      params.setConnectOptions(connectOptions);
      final DicomState state =
          CStore.process(
              params, new DicomNode(weasisAet), node.getDicomNode(), files, dicomProgress);
      if (state.getStatus() != Status.Success && state.getStatus() != Status.Cancel) {
        showErrorMessage(null, null, state);
        return false;
      } else {
        LOGGER.info("Dicom send: {}", state.getMessage());
      }
    } else if (selectedItem instanceof final DicomWebNode node) {
      AuthMethod auth = AuthenticationPersistence.getAuthMethod(node.getAuthMethodUid());
      if (!OAuth2ServiceFactory.noAuth.equals(auth)) {
        String oldCode = auth.getCode();
        authMethod = auth;
        if (authMethod.getToken() == null) {
          return false;
        }
        if (!Objects.equals(oldCode, authMethod.getCode())) {
          AuthenticationPersistence.saveMethod();
        }
      }

      try (StowRS stowRS =
          new StowRS(
              node.getUrl().toString(),
              ContentType.APPLICATION_DICOM,
              AppProperties.WEASIS_NAME,
              node.getHeaders())) {
        DicomState state = stowRS.uploadDicom(files, true, authMethod);
        if (state.getStatus() != Status.Success && state.getStatus() != Status.Cancel) {
          showErrorMessage(null, null, state);
          return false;
        }
      } catch (Exception e) {
        showErrorMessage("StowRS error: {}", e, null); // NON-NLS
        return false;
      }
    }
    return true;
  }

  private void showErrorMessage(String title, Exception e, DicomState state) {
    if (e != null) {
      LOGGER.error(title, e.getMessage());
    }
    GuiExecutor.execute(
        () ->
            JOptionPane.showMessageDialog(
                WinUtil.getValidComponent(exportTree),
                state == null
                    ? Objects.requireNonNull(e).getMessage()
                    : StringUtil.getTruncatedString(state.getMessage(), 150, Suffix.THREE_PTS),
                getTitle(),
                JOptionPane.ERROR_MESSAGE));
  }
}
