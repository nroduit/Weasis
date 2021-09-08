/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.acquire.explorer.gui;

import javax.swing.ImageIcon;
import javax.swing.JButton;
import org.weasis.acquire.explorer.Messages;
import org.weasis.core.ui.editor.image.ImageViewerPlugin;
import org.weasis.core.ui.util.WtoolBar;

public class AcquireToolBar<DicomImageElement> extends WtoolBar {
  private static final long serialVersionUID = 3195220259820490950L;

  public AcquireToolBar(int index) {
    super(Messages.getString("AcquireToolBar.title"), index);

    // TODO add button for publishing, help...
    final JButton printButton =
        new JButton(new ImageIcon(ImageViewerPlugin.class.getResource("/icon/32x32/printer.png")));
    printButton.setToolTipText("");
    printButton.addActionListener(
        e -> {
          // Do nothing
        });
    add(printButton);
  }
}
