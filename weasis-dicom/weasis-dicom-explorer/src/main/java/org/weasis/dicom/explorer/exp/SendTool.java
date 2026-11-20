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
 * The export to the network: the pages of the export plugins whose destination is a network node,
 * the DICOM send page among them. Offered only when a plugin contributes such a page.
 */
public class SendTool extends ExportHostTool {

  public static final String ID = "send"; // NON-NLS

  public SendTool(DicomModel model) {
    super(
        ID,
        Messages.common("send"),
        34,
        ResourceUtil.getIcon(ActionIcon.SEND),
        0.6,
        Messages.common("send"),
        "share-export/dicom-export/#send", // NON-NLS
        model,
        TransferKind.NETWORK);
  }

  @Override
  public boolean isOffered() {
    return isAllowed() && !factories(TransferKind.NETWORK).isEmpty();
  }

  /** Opens the send tool, on the page of that title when given. */
  public static EmbeddedWizardPanel open(String pageTitle) {
    return open(ID, pageTitle);
  }
}
