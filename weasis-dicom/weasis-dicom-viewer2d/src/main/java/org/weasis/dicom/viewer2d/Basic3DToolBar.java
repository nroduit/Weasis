/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.viewer2d;

import java.awt.Component;
import java.awt.event.ActionListener;
import javax.swing.JButton;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.util.ResourceUtil;
import org.weasis.core.api.util.ResourceUtil.OtherIcon;
import org.weasis.core.ui.editor.image.ViewCanvas;
import org.weasis.core.ui.util.WtoolBar;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.viewer2d.mip.MipMenu;
import org.weasis.dicom.viewer2d.mpr.MprFactory;
import org.weasis.dicom.viewer2d.mpr.MprView;

public class Basic3DToolBar extends WtoolBar {

  public Basic3DToolBar(int index) {
    super(Messages.getString("Basic3DToolBar.title"), index);

    final JButton mprButton = new JButton(ResourceUtil.getToolBarIcon(OtherIcon.VIEW_3D));
    mprButton.setToolTipText(Messages.getString("Basic3DToolBar.mpr.oblique"));
    mprButton.addActionListener(MprFactory.getMprAction(null));
    add(mprButton);

    final JButton mipButton = new JButton(ResourceUtil.getToolBarIcon(OtherIcon.VIEW_MIP));
    mipButton.setToolTipText(Messages.getString("Basic3DToolBar.mip"));
    mipButton.addActionListener(getMipAction());
    add(mipButton);

    // Attach 3D functions to the Volume actions
    EventManager.getInstance()
        .getAction(ActionW.VOLUME)
        .ifPresent(
            s -> {
              s.registerActionState(mprButton);
              s.registerActionState(mipButton);
            });
  }

  /**
   * Returns an {@link ActionListener} that shows the MIP dropdown popup instead of opening a modal
   * dialog. The popup lets the user choose mode (None / Min / Mean / Max), thickness, and rebuild
   * options all without leaving the image view.
   */
  public static ActionListener getMipAction() {
    return e -> {
      if (e.getSource() instanceof Component src) {
        ViewCanvas<DicomImageElement> view = EventManager.getInstance().getSelectedViewPane();
        if (view instanceof MprView mprView) {
          mprView.showMprPopup(src, 0, src.getHeight());
        } else {
          MipMenu.showPopup(src, 0, src.getHeight());
        }
      }
    };
  }
}
