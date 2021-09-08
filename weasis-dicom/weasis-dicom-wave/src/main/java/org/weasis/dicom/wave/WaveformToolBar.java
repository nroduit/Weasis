/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.wave;

import javax.swing.ImageIcon;
import javax.swing.JButton;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.ui.editor.image.ImageViewerPlugin;
import org.weasis.core.ui.editor.image.MouseActions;
import org.weasis.core.ui.util.WtoolBar;

public class WaveformToolBar extends WtoolBar {
  protected final JButton jButtondelete = new JButton();

  public WaveformToolBar(int index) {
    super("Main Bar", index); // NON-NLS

    final JButton printButton =
        new JButton(new ImageIcon(ImageViewerPlugin.class.getResource("/icon/32x32/printer.png")));
    printButton.addActionListener(
        e -> {
          ImageViewerPlugin<?> container =
              WaveContainer.ECG_EVENT_MANAGER.getSelectedView2dContainer();
          if (container instanceof WaveContainer) {
            ((WaveContainer) container).printCurrentView();
          }
        });
    add(printButton);

    final JButton metaButton =
        new JButton(
            new ImageIcon(ImageViewerPlugin.class.getResource("/icon/32x32/dcm-header.png")));
    metaButton.setToolTipText(ActionW.SHOW_HEADER.getTitle());
    metaButton.addActionListener(
        e -> {
          ImageViewerPlugin<?> container =
              WaveContainer.ECG_EVENT_MANAGER.getSelectedView2dContainer();
          if (container instanceof WaveContainer) {
            ((WaveContainer) container).displayHeader();
          }
        });
    add(metaButton);

    jButtondelete.setToolTipText(Messages.getString("WaveformToolBar.delete"));
    jButtondelete.setIcon(
        new ImageIcon(MouseActions.class.getResource("/icon/32x32/draw-delete.png")));
    jButtondelete.addActionListener(
        e -> {
          ImageViewerPlugin<?> container =
              WaveContainer.ECG_EVENT_MANAGER.getSelectedView2dContainer();
          if (container instanceof WaveContainer) {
            ((WaveContainer) container).clearMeasurements();
          }
        });
    add(jButtondelete);
  }
}
