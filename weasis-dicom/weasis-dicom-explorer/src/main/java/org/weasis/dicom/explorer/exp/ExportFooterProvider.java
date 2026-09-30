/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.explorer.exp;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.io.IOException;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import org.pf4j.Extension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.gui.FooterPanelProvider;
import org.weasis.core.api.gui.util.AbstractWizardDialog;
import org.weasis.core.api.gui.util.EmbeddedWizardPanel;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.util.ResourceUtil;
import org.weasis.core.api.util.ResourceUtil.ActionIcon;
import org.weasis.dicom.explorer.DicomModel;
import org.weasis.dicom.explorer.LocalPersistence;
import org.weasis.dicom.explorer.Messages;

/** Embedded DICOM export panel for the bottom-left footer, reusing the wizard pages. */
@Extension
public class ExportFooterProvider implements FooterPanelProvider {
  private static final Logger LOGGER = LoggerFactory.getLogger(ExportFooterProvider.class);

  @Override
  public String getTitle() {
    return Messages.getString("DicomExport.exp_dicom");
  }

  @Override
  public Icon getIcon() {
    return ResourceUtil.getIcon(ActionIcon.EXPORT_DICOM);
  }

  @Override
  public int getPosition() {
    return 20;
  }

  @Override
  public JComponent createPanel(Runnable closeCallback) {
    if (!GuiUtils.getUICore()
        .getSystemPreferences()
        .getBooleanProperty("weasis.export.dicom", true)) {
      JPanel panel = new JPanel(new BorderLayout());
      panel.add(new JLabel(Messages.getString("DicomExplorer.export_perm")), BorderLayout.CENTER);
      return panel;
    }
    DicomModel model = DicomModel.getApplicationModel();
    CheckTreeModel treeModel = new CheckTreeModel(model);
    DicomExport.initTreeCheckingModel(treeModel);

    JButton exportButton = new JButton(Messages.getString("DicomExport.exp"));
    JButton closeButton =
        new JButton(org.weasis.core.Messages.getString("AbstractWizardDialog.close"));
    EmbeddedWizardPanel panel =
        new EmbeddedWizardPanel(
            DicomExport.createPages(model, treeModel),
            GuiUtils.getFlowLayoutPanel(
                FlowLayout.TRAILING,
                AbstractWizardDialog.HORIZONTAL_GAP,
                AbstractWizardDialog.VERTICAL_GAP,
                exportButton,
                closeButton));
    exportButton.addActionListener(
        e -> {
          if (panel.getSelectedPage() instanceof ExportDicom page) {
            try {
              page.exportDICOM(treeModel, null);
            } catch (IOException ex) {
              LOGGER.error("DICOM export failed", ex);
            }
          }
        });
    closeButton.addActionListener(e -> closeCallback.run());
    panel.selectPage(LocalPersistence.getProperties().getProperty(DicomExport.LAST_PAGE));
    return panel;
  }

  @Override
  public void onCollapse(JComponent panel) {
    if (panel instanceof EmbeddedWizardPanel wizard) {
      if (wizard.getSelectedPage() != null) {
        LocalPersistence.getProperties()
            .setProperty(DicomExport.LAST_PAGE, wizard.getSelectedPage().getTitle());
      }
      wizard.closeAllPages();
    }
  }
}
