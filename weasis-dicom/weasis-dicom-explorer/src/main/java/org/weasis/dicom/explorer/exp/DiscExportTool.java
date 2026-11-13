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

import org.weasis.core.api.gui.util.EmbeddedWizardPanel;
import org.weasis.core.api.util.ResourceUtil;
import org.weasis.core.api.util.ResourceUtil.ActionIcon;
import org.weasis.dicom.explorer.DicomModel;
import org.weasis.dicom.explorer.Messages;
import org.weasis.dicom.explorer.TransferKind;

/**
 * The export to a disc: the pages of the export plugins whose destination is a CD/DVD (the disc
 * image of the ISO writer), on the checked tree of the loaded studies. Offered only when a plugin
 * contributes such a page.
 */
public class DiscExportTool extends ExportHostTool {

  public static final String ID = "export-disc"; // NON-NLS

  public DiscExportTool(DicomModel model) {
    super(
        ID,
        Messages.getString("ExportTool.disc"),
        33,
        ResourceUtil.getIcon(ActionIcon.EXPORT_CD),
        0.6,
        Messages.common("export"),
        "dicom-export/#exporting", // NON-NLS
        model,
        TransferKind.DISC);
  }

  @Override
  public boolean isOffered() {
    return isAllowed() && !factories(TransferKind.DISC).isEmpty();
  }

  /** Opens the CD/DVD export tool, on the page of that title when given. */
  public static EmbeddedWizardPanel open(String pageTitle) {
    return open(ID, pageTitle);
  }
}
