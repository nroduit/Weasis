/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.acquire.explorer.gui.model.renderer;

import java.awt.Component;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.ListCellRenderer;
import org.weasis.acquire.explorer.media.MediaSource;

public class MediaSourceListCellRenderer extends JLabel implements ListCellRenderer<MediaSource> {

  public MediaSourceListCellRenderer() {
    setOpaque(true);
    setHorizontalAlignment(LEFT);
    setVerticalAlignment(CENTER);
  }

  @Override
  public Component getListCellRendererComponent(
      JList<? extends MediaSource> list,
      MediaSource value,
      int index,
      boolean isSelected,
      boolean cellHasFocus) {

    if (value != null) {
      setIcon(value.getIcon());
      String str = value.getDisplayName();
      setText(str);
    } else {
      setIcon(null);
      setText("");
    }

    setForeground(isSelected ? list.getSelectionForeground() : list.getForeground());
    setBackground(isSelected ? list.getSelectionBackground() : list.getBackground());

    return this;
  }
}
