/*
 * Copyright (c) 2012 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.viewer3d;

import javax.swing.JButton;
import org.weasis.core.api.explorer.model.DataExplorerModel;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.api.util.ResourceUtil;
import org.weasis.core.api.util.ResourceUtil.ActionIcon;
import org.weasis.core.ui.editor.SeriesViewerFactory;
import org.weasis.core.ui.editor.ViewerPluginBuilder;
import org.weasis.core.ui.util.WtoolBar;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.viewer2d.EventManager;

public class ExternalView3DToolbar extends WtoolBar {

  public ExternalView3DToolbar(int position) {
    super(View3DFactory.NAME, position);

    JButton open = new JButton(ResourceUtil.getToolBarIcon(ActionIcon.VOLUME));
    open.setToolTipText(Messages.getString("open.3d.viewer"));
    open.addActionListener(
        e -> {
          MediaSeries<DicomImageElement> s = EventManager.getInstance().getSelectedSeries();
          SeriesViewerFactory factory = GuiUtils.getUICore().getViewerFactory(View3DFactory.class);
          if (factory != null && factory.canReadSeries(s)) {
            ViewerPluginBuilder.openSequenceInPlugin(
                factory, s, (DataExplorerModel) s.getTagValue(TagW.ExplorerModel), false, false);
          }
        });

    add(open);

    // Attach 3D functions to the Volume actions
    EventManager.getInstance()
        .getAction(ActionW.VOLUME)
        .ifPresent(s -> s.registerActionState(open));
  }
}
