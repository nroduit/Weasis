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

import java.awt.Component;
import java.util.List;
import javax.swing.JOptionPane;
import org.weasis.core.api.gui.util.AbstractItemDialogPage;
import org.weasis.core.api.gui.util.EmbeddedWizardPanel;
import org.weasis.core.api.util.ResourceUtil;
import org.weasis.core.api.util.ResourceUtil.ActionIcon;
import org.weasis.core.ui.util.DefaultAction;
import org.weasis.dicom.explorer.DicomModel;
import org.weasis.dicom.explorer.Messages;
import org.weasis.dicom.explorer.TransferKind;

/**
 * The export to the local device: the local page (DICOM, images, media layout) and the local pages
 * of the export plugins (a disc image), on the checked tree of the loaded studies.
 */
public class ExportTool extends ExportHostTool {

  public static final String ID = "export"; // NON-NLS

  public ExportTool(DicomModel model) {
    super(
        ID,
        Messages.getString("DicomExport.exp_dicom"),
        32,
        ResourceUtil.getIcon(ActionIcon.EXPORT_DICOM),
        0.6,
        Messages.common("export"),
        "dicom-export/#exporting", // NON-NLS
        model,
        TransferKind.LOCAL);
  }

  @Override
  public boolean isOffered() {
    return isAllowed();
  }

  /** Opens the export tool, on the page of that title when given. */
  public static EmbeddedWizardPanel open(String pageTitle) {
    return open(ID, pageTitle);
  }

  /** The export of the File menu: opens the export tool, or says the site does not allow it. */
  public static DefaultAction buildExportAction(String actionName) {
    return new DefaultAction(
        actionName,
        ResourceUtil.getIcon(ActionIcon.EXPORT_DICOM),
        event -> {
          if (isAllowed()) {
            open(Messages.getString("LocalExport.local_dev"));
          } else {
            JOptionPane.showMessageDialog(
                (Component) event.getSource(), Messages.getString("DicomExplorer.export_perm"));
          }
        });
  }

  @Override
  protected List<AbstractItemDialogPage> createOwnPages(CheckTreeModel tree) {
    return List.of(new LocalExport(getModel(), tree));
  }
}
