/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.explorer.imp;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import org.pf4j.Extension;
import org.weasis.core.api.gui.FooterPanelProvider;
import org.weasis.core.api.gui.util.AbstractWizardDialog;
import org.weasis.core.api.gui.util.EmbeddedWizardPanel;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.util.ResourceUtil;
import org.weasis.core.api.util.ResourceUtil.ActionIcon;
import org.weasis.dicom.explorer.DicomModel;
import org.weasis.dicom.explorer.LocalPersistence;
import org.weasis.dicom.explorer.Messages;

/** Embedded DICOM import panel for the bottom-left footer, reusing the wizard pages. */
@Extension
public class ImportFooterProvider implements FooterPanelProvider {

  @Override
  public String getTitle() {
    return Messages.getString("DicomImport.imp_dicom");
  }

  @Override
  public Icon getIcon() {
    return ResourceUtil.getIcon(ActionIcon.IMPORT_DICOM);
  }

  @Override
  public int getPosition() {
    return 10;
  }

  @Override
  public JComponent createPanel(Runnable closeCallback) {
    if (!GuiUtils.getUICore()
        .getSystemPreferences()
        .getBooleanProperty("weasis.import.dicom", true)) {
      JPanel panel = new JPanel(new BorderLayout());
      panel.add(new JLabel(Messages.getString("DicomExplorer.export_perm")), BorderLayout.CENTER);
      return panel;
    }
    DicomModel model = DicomModel.getApplicationModel();
    JButton importButton = new JButton(Messages.getString("DicomImport.imp"));
    JButton closeButton =
        new JButton(org.weasis.core.Messages.getString("AbstractWizardDialog.close"));
    EmbeddedWizardPanel panel =
        new EmbeddedWizardPanel(
            DicomImport.createPages(),
            GuiUtils.getFlowLayoutPanel(
                FlowLayout.TRAILING,
                AbstractWizardDialog.HORIZONTAL_GAP,
                AbstractWizardDialog.VERTICAL_GAP,
                importButton,
                closeButton));
    importButton.addActionListener(
        e -> {
          if (panel.getSelectedPage() instanceof ImportDicom page) {
            page.importDICOM(model, null);
          }
        });
    closeButton.addActionListener(e -> closeCallback.run());
    panel.selectPage(LocalPersistence.getProperties().getProperty(DicomImport.LAST_PAGE));
    return panel;
  }

  @Override
  public void onCollapse(JComponent panel) {
    if (panel instanceof EmbeddedWizardPanel wizard) {
      if (wizard.getSelectedPage() != null) {
        LocalPersistence.getProperties()
            .setProperty(DicomImport.LAST_PAGE, wizard.getSelectedPage().getTitle());
      }
      wizard.closeAllPages();
    }
  }
}
