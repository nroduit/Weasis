/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.acquire.explorer.gui.central.meta.panel.imp;

import org.weasis.acquire.explorer.Messages;
import org.weasis.acquire.explorer.gui.central.meta.model.AcquireMetadataTableModel;
import org.weasis.acquire.explorer.gui.central.meta.model.imp.AcquireImageMeta;
import org.weasis.acquire.explorer.gui.central.meta.panel.AcquireMetadataPanel;
import org.weasis.core.util.StringUtil;

public class AcquireImageMetaPanel extends AcquireMetadataPanel {

  private static final String NO_IMAGE = Messages.getString("AcquireImageMetaPanel.no_img");
  private static final String IMAGE_PREFIX =
      Messages.getString("AcquireImageMetaPanel.img") + StringUtil.COLON_AND_SPACE;

  public AcquireImageMetaPanel(String title) {
    super(title);
  }

  @Override
  public AcquireMetadataTableModel newTableModel() {
    return new AcquireImageMeta(imageInfo);
  }

  @Override
  public String getDisplayText() {
    if (imageInfo != null) {
      return IMAGE_PREFIX + imageInfo.getImage().getName();
    } else {
      return NO_IMAGE;
    }
  }
}
