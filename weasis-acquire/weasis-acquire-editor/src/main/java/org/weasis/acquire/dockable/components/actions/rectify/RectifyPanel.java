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

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.util.Objects;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JPanel;
import org.weasis.acquire.dockable.EditionToolFactory;
import org.weasis.acquire.dockable.components.actions.AbstractAcquireActionPanel;
import org.weasis.acquire.dockable.components.actions.rectify.lib.AbstractRectifyButton;
import org.weasis.acquire.dockable.components.actions.rectify.lib.OrientationSliderComponent;
import org.weasis.acquire.dockable.components.actions.rectify.lib.btn.Rotate270Button;
import org.weasis.acquire.dockable.components.actions.rectify.lib.btn.Rotate90Button;
import org.weasis.acquire.explorer.AcquireImageInfo;
import org.weasis.acquire.explorer.AcquireImageValues;
import org.weasis.acquire.operations.impl.RectifyOrientationChangeListener;
import org.weasis.base.viewer2d.EventManager;
import org.weasis.core.api.gui.util.ComboItemListener;
import org.weasis.core.api.gui.util.WinUtil;
import org.weasis.core.api.image.CropOp;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.ui.editor.image.ImageViewerPlugin;
import org.weasis.core.ui.editor.image.MeasureToolBar;
import org.weasis.core.ui.editor.image.MouseActions;
import org.weasis.core.ui.editor.image.ViewCanvas;
import org.weasis.core.ui.editor.image.ViewerToolBar;

public class RectifyPanel extends AbstractAcquireActionPanel {
  private static final long serialVersionUID = 4041145212218086219L;

  private final OrientationSliderComponent orientationPanel;
  private final AbstractRectifyButton rotate90btn;
  private final AbstractRectifyButton rotate270btn;

  private final RectifyAction rectifyAction;

  public RectifyPanel(RectifyAction rectifyAction) {
    this.rectifyAction = Objects.requireNonNull(rectifyAction);
    setLayout(new BorderLayout());
    orientationPanel = new OrientationSliderComponent(this);
    rotate90btn = new Rotate90Button(rectifyAction);
    rotate270btn = new Rotate270Button(rectifyAction);
    add(createContent(), BorderLayout.NORTH);
  }

  private JPanel createContent() {
    JPanel panel = new JPanel();
    panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
    panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

    JPanel btnContent = new JPanel();
    btnContent.setLayout(new FlowLayout(FlowLayout.LEFT, 10, 0));
    btnContent.add(rotate90btn);
    btnContent.add(rotate270btn);
    btnContent.setBorder(BorderFactory.createEmptyBorder(15, 0, 5, 0));

    panel.add(orientationPanel);
    panel.add(btnContent);

    return panel;
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
    orientationPanel.setSliderValue(next.getOrientation());
    orientationPanel.updatePanelTitle();
    orientationPanel.addChangeListener(listener);
    rectifyAction.init(view.getGraphicManager(), info);
    repaint();

    // Remove the crop to get the entire image.
    info.getPostProcessOpManager().setParamValue(CropOp.OP_NAME, CropOp.P_AREA, null);

    view.getEventManager()
        .getAction(EditionToolFactory.DRAW_EDITON, ComboItemListener.class)
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
        String cmd = EditionToolFactory.EDITON.cmd();
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
