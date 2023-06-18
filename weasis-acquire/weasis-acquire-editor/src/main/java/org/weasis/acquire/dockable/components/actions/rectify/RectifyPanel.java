/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.acquire.dockable.components.actions.rectify;

import java.util.Objects;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import org.weasis.acquire.dockable.EditionToolFactory;
import org.weasis.acquire.dockable.components.actions.AbstractAcquireActionPanel;
import org.weasis.acquire.explorer.AcquireImageInfo;
import org.weasis.acquire.explorer.AcquireImageValues;
import org.weasis.acquire.operations.impl.RectifyOrientationChangeListener;
import org.weasis.base.viewer2d.EventManager;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.gui.util.WinUtil;
import org.weasis.core.api.image.CropOp;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.ui.editor.image.ImageViewerPlugin;
import org.weasis.core.ui.editor.image.MeasureToolBar;
import org.weasis.core.ui.editor.image.MouseActions;
import org.weasis.core.ui.editor.image.ViewCanvas;
import org.weasis.core.ui.editor.image.ViewerToolBar;

public class RectifyPanel extends AbstractAcquireActionPanel {

  private final OrientationSliderComponent orientationPanel;

  private final RectifyAction rectifyAction;

  public RectifyPanel(RectifyAction rectifyAction) {
    setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
    setBorder(GuiUtils.getEmptyBorder(10, 5, 2, 5));
    this.rectifyAction = Objects.requireNonNull(rectifyAction);
    orientationPanel = new OrientationSliderComponent(this);
    JButton rotate90btn = new Rotate90Button(rectifyAction);
    rotate90btn.setPreferredSize(GuiUtils.getBigIconButtonSize(rotate90btn));
    JButton rotate270btn = new Rotate270Button(rectifyAction);
    rotate270btn.setPreferredSize(GuiUtils.getBigIconButtonSize(rotate270btn));

    add(orientationPanel);
    add(GuiUtils.boxVerticalStrut(15));
    add(GuiUtils.getFlowLayoutPanel(10, 5, rotate90btn, rotate270btn));
    add(GuiUtils.boxYLastElement(5));
  }

  public RectifyAction getRectifyAction() {
    return rectifyAction;
  }

  @Override
  public boolean needValidationPanel() {
    return true;
  }

  @Override
  public void initValues(AcquireImageInfo info, AcquireImageValues values) {
    ViewCanvas<ImageElement> view = EventManager.getInstance().getSelectedViewPane();

    AcquireImageValues next = info.getNextValues();
    next.setOrientation(values.getOrientation());
    next.setRotation(values.getRotation());
    next.setCropZone(values.getCropZone());

    RectifyOrientationChangeListener listener = orientationPanel.getListener();
    orientationPanel.removeChangeListener(listener);
    orientationPanel.setValue(next.getOrientation());
    orientationPanel.updatePanelTitle();
    orientationPanel.addChangeListener(listener);
    rectifyAction.init(view.getGraphicManager(), info);
    repaint();

    // Remove the crop to get the entire image.
    info.getPostProcessOpManager().setParamValue(CropOp.OP_NAME, CropOp.P_AREA, null);

    view.getEventManager()
        .getAction(EditionToolFactory.DRAW_EDITION)
        .ifPresent(a -> a.setSelectedItem(MeasureToolBar.selectionGraphic));
    ImageViewerPlugin<?> container =
        WinUtil.getParentOfClass(view.getJComponent(), ImageViewerPlugin.class);
    applyEditAction(this, container);

    listener.applyNextValues();
    info.applyCurrentProcessing(view);
    rectifyAction.updateCropGraphic();
  }

  public static void applyEditAction(
      AbstractAcquireActionPanel panel, ImageViewerPlugin<?> container) {
    if (container != null) {
      final ViewerToolBar<?> toolBar = container.getViewerToolBar();
      if (toolBar != null) {
        String cmd = EditionToolFactory.EDITION.cmd();
        MouseActions mouseActions = EventManager.getInstance().getMouseActions();
        if (!cmd.equals(mouseActions.getLeft())) {
          panel.setLastActionCommand(mouseActions.getLeft());
          mouseActions.setAction(MouseActions.T_LEFT, cmd);
          container.setMouseActions(mouseActions);
          toolBar.changeButtonState(MouseActions.T_LEFT, cmd);
        }
      }
    }
  }
}
