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

import java.awt.event.ActionListener;
import javax.swing.JButton;
import org.weasis.core.api.explorer.model.DataExplorerModel;
import org.weasis.core.api.gui.util.ActionState;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.api.util.ResourceUtil;
import org.weasis.core.api.util.ResourceUtil.OtherIcon;
import org.weasis.core.ui.editor.ViewerPluginBuilder;
import org.weasis.core.ui.editor.image.ImageViewerPlugin;
import org.weasis.core.ui.editor.image.ViewCanvas;
import org.weasis.core.ui.util.WtoolBar;
import org.weasis.dicom.explorer.DicomModel;
import org.weasis.dicom.viewer2d.mip.MipPopup;
import org.weasis.dicom.viewer2d.mip.MipPopup.MipDialog;
import org.weasis.dicom.viewer2d.mip.MipView;
import org.weasis.dicom.viewer2d.mpr.MPRFactory;

public class Basic3DToolBar<DicomImageElement> extends WtoolBar {

  public Basic3DToolBar(int index) {
    super(Messages.getString("Basic3DToolBar.title"), index);

    final JButton mprButton = new JButton(ResourceUtil.getToolBarIcon(OtherIcon.VIEW_3D));
    mprButton.setToolTipText(Messages.getString("Basic3DToolBar.mpr"));
    mprButton.addActionListener(getMprAction());
    add(mprButton);

    final JButton mipButton = new JButton(ResourceUtil.getToolBarIcon(OtherIcon.VIEW_MIP));
    mipButton.setToolTipText(Messages.getString("Basic3DToolBar.mip"));
    mipButton.addActionListener(getMipAction());
    add(mipButton);

    // Attach 3D functions to the SCROLL_SERIES actions
    ActionState scrollAction = EventManager.getInstance().getAction(ActionW.SCROLL_SERIES);
    if (scrollAction != null) {
      scrollAction.registerActionState(mprButton);
      scrollAction.registerActionState(mipButton);
    }
  }

  public static ActionListener getMprAction() {
    return e -> {
      EventManager eventManager = EventManager.getInstance();
      MediaSeries<org.weasis.dicom.codec.DicomImageElement> s = eventManager.getSelectedSeries();
      // Requires at least 5 images to build the MPR views
      if (s != null && s.size(null) >= 5) {
        DataExplorerModel model = (DataExplorerModel) s.getTagValue(TagW.ExplorerModel);
        if (model instanceof DicomModel) {
          ViewerPluginBuilder.openSequenceInPlugin(new MPRFactory(), s, model, false, false);
        }
      }
    };
  }

  public static ActionListener getMipAction() {
    return e -> {
      EventManager eventManager = EventManager.getInstance();
      ImageViewerPlugin<org.weasis.dicom.codec.DicomImageElement> container =
          eventManager.getSelectedView2dContainer();
      if (container instanceof View2dContainer) {
        ViewCanvas<org.weasis.dicom.codec.DicomImageElement> selView =
            container.getSelectedImagePane();
        if (selView != null) {
          MediaSeries<org.weasis.dicom.codec.DicomImageElement> s = selView.getSeries();
          if (s != null && s.size(null) > 2) {
            container.setSelectedAndGetFocus();
            MipView newView2d = new MipView(eventManager);
            newView2d.registerDefaultListeners();
            newView2d.initMIPSeries(selView);
            container.replaceView(selView, newView2d);
            MipDialog dialog = MipPopup.buildDialog(newView2d);
            dialog.pack();
            MipView.buildMip(newView2d, false);
            dialog.updateThickness();
            GuiUtils.showCenterScreen(dialog, container);
          }
        }
      }
    };
  }
}
