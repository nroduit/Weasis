/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.base.viewer2d;

import java.awt.event.ActionEvent;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JButton;
import org.weasis.core.api.service.BundleTools;
import org.weasis.core.api.util.ResourceUtil;
import org.weasis.core.api.util.ResourceUtil.ActionIcon;
import org.weasis.core.ui.util.WtoolBar;

public class ImportToolBar extends WtoolBar {

  public ImportToolBar(int index) {
    super(Messages.getString("ImportToolBar.import_img_bar"), index);

    if (BundleTools.SYSTEM_PREFERENCES.getBooleanProperty("weasis.import.images", true)) {
      AbstractAction action =
          new AbstractAction() {

            @Override
            public void actionPerformed(ActionEvent e) {
              ViewerFactory.getOpenImageAction(e);
            }
          };
      action.putValue(Action.LARGE_ICON_KEY, ResourceUtil.getToolBarIcon(ActionIcon.IMPORT_IMAGE));
      final JButton btnImport = new JButton(action);
      btnImport.setToolTipText(Messages.getString("ImportToolBar.open_img"));
      add(btnImport);
    }
  }
}
