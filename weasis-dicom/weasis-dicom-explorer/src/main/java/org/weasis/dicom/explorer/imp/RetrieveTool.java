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

import java.util.List;
import org.weasis.core.api.gui.InsertableUtil;
import org.weasis.core.api.gui.util.AbstractItemDialogPage;
import org.weasis.core.api.gui.util.EmbeddedWizardPanel;
import org.weasis.core.api.util.ResourceUtil;
import org.weasis.core.api.util.ResourceUtil.ActionIcon;
import org.weasis.dicom.explorer.DicomModel;
import org.weasis.dicom.explorer.Messages;
import org.weasis.dicom.explorer.TransferKind;
import org.weasis.dicom.explorer.TransferTool;

/**
 * The import from the network: the pages of the import plugins whose source is a network node, the
 * query/retrieve page among them. Offered only when a plugin contributes such a page.
 */
public class RetrieveTool extends TransferTool {

  public static final String ID = "retrieve"; // NON-NLS

  public RetrieveTool(DicomModel model) {
    super(
        ID,
        Messages.getString("RetrieveTool.title"),
        31,
        ResourceUtil.getIcon(ActionIcon.RETRIEVE_DICOM),
        0.65,
        Messages.common("retrieve"),
        "dicom-import", // NON-NLS
        model);
  }

  @Override
  public boolean isOffered() {
    return ImportTool.isAllowed() && !ImportTool.factories(TransferKind.NETWORK).isEmpty();
  }

  /** Opens the retrieve tool, on the page of that title when given. */
  public static EmbeddedWizardPanel open(String pageTitle) {
    return open(ID, pageTitle);
  }

  @Override
  protected List<AbstractItemDialogPage> createPages() {
    List<AbstractItemDialogPage> list = ImportTool.createPluginPages(TransferKind.NETWORK);
    InsertableUtil.sortInsertable(list);
    return list;
  }

  @Override
  protected void run(AbstractItemDialogPage page) {
    if (page instanceof ImportDicom importPage) {
      importPage.importDICOM(getModel(), null);
    }
  }
}
