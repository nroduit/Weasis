/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.editor.image;

import java.awt.GraphicsConfiguration;
import java.awt.Window;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JToggleButton;
import javax.swing.SwingUtilities;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.gui.util.DropButtonIcon;
import org.weasis.core.api.gui.util.DropDownButton;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.service.AuditLog;
import org.weasis.core.api.util.ResourceUtil;
import org.weasis.core.api.util.ResourceUtil.ActionIcon;
import org.weasis.core.ui.Messages;
import org.weasis.core.ui.editor.image.dockable.MeasureTool;
import org.weasis.core.ui.pref.Monitor;
import org.weasis.core.ui.util.WtoolBar;

public class ZoomToolBar extends WtoolBar {

  public ZoomToolBar(final ImageViewerEventManager<?> eventManager, int index, boolean showLens) {
    super(Messages.getString("ZoomToolBar.zoomBar"), index);
    if (eventManager == null) {
      throw new IllegalArgumentException("EventManager cannot be null");
    }

    final DropDownButton zoom =
        new DropDownButton(
            "zoom", // NON-NLS
            new DropButtonIcon(ResourceUtil.getToolBarIcon(ActionIcon.ZOOM))) {

          @Override
          protected JPopupMenu getPopupMenu() {
            return getZoomPopupMenuButton(this, eventManager);
          }
        };
    zoom.setToolTipText(Messages.getString("ZoomToolBar.zoom_type"));
    add(zoom);

    eventManager.getAction(ActionW.ZOOM).ifPresent(s -> s.registerActionState(zoom));

    if (showLens) {
      final JToggleButton jButtonLens =
          new JToggleButton(ResourceUtil.getToolBarIcon(ActionIcon.ZOOM_PAN));
      jButtonLens.setToolTipText(Messages.getString("ViewerToolBar.show_lens"));
      eventManager.getAction(ActionW.LENS).ifPresent(s -> s.registerActionState(jButtonLens));
      add(jButtonLens);
    }
  }

  private JPopupMenu getZoomPopupMenuButton(
      DropDownButton dropDownButton, final ImageViewerEventManager<?> eventManager) {
    JPopupMenu popupMouseButtons = new JPopupMenu();
    for (JMenuItem jMenuItem : getZoomListMenuItems(eventManager)) {
      popupMouseButtons.add(jMenuItem);
    }
    return popupMouseButtons;
  }

  public static List<JMenuItem> getZoomListMenuItems(
      final ImageViewerEventManager<?> eventManager) {

    List<JMenuItem> list = new ArrayList<>();
    final JMenuItem actualZoomMenu =
        new JMenuItem(
            Messages.getString("ViewerToolBar.zoom_1"),
            ResourceUtil.getIcon(ActionIcon.ZOOM_ORIGINAL));
    GuiUtils.applySelectedIconEffect(actualZoomMenu);
    actualZoomMenu.addActionListener(
        e -> {
          eventManager.getAction(ActionW.ZOOM).ifPresent(s -> s.setRealValue(1.0));
        });
    list.add(actualZoomMenu);

    ImageViewerPlugin<?> selCt = eventManager.getSelectedView2dContainer();
    Window win = selCt == null ? null : SwingUtilities.getWindowAncestor(selCt);
    if (win != null) {
      GraphicsConfiguration config = win.getGraphicsConfiguration();
      Monitor monitor = MeasureTool.viewSetting.getMonitor(config.getDevice());
      if (monitor != null) {
        double realFactor = monitor.getRealScaleFactor();
        if (realFactor > 0.0) {
          final JMenuItem realSizeMenu =
              new JMenuItem(
                  Messages.getString("ZoomToolBar.real_zoom"),
                  ResourceUtil.getIcon(ActionIcon.ZOOM_REAL_WORLD));
          GuiUtils.applySelectedIconEffect(realSizeMenu);
          realSizeMenu.addActionListener(
              e -> {
                /*
                 * Pass the value -100.0 (convention: -100.0 => real world size) directly to the property
                 * change, otherwise the value is adjusted by the BoundedRangeModel
                 */
                eventManager.firePropertyChange(
                    ActionW.SYNCH.cmd(), null, new SynchEvent(null, ActionW.ZOOM.cmd(), -100.0));
                AuditLog.LOGGER.info("action:{} val:-100.0", ActionW.ZOOM.cmd());
              });
          list.add(realSizeMenu);
        }
      }
    }

    final JMenuItem bestFitMenu =
        new JMenuItem(
            Messages.getString("ViewerToolBar.zoom_b"),
            ResourceUtil.getIcon(ActionIcon.ZOOM_BEST_FIT));
    GuiUtils.applySelectedIconEffect(bestFitMenu);
    bestFitMenu.addActionListener(
        e -> {
          // Pass the value -200.0 (convention: -200.0 = > best fit zoom value) directly to the
          // property change,
          // otherwise the value is adjusted by the BoundedRangeModel
          eventManager.firePropertyChange(
              ActionW.SYNCH.cmd(), null, new SynchEvent(null, ActionW.ZOOM.cmd(), -200.0));
          AuditLog.LOGGER.info("action:{} val:-200.0", ActionW.ZOOM.cmd());
        });
    list.add(bestFitMenu);

    // final JMenuItem areaZoomMenu =
    // new JMenuItem("Area selection", new ImageIcon(
    // MResourceUtil.getIcon(ActionIcon.ZOOM_AREA));
    // areaZoomMenu.addActionListener(new ActionListener() {
    //
    // @Override
    // public void actionPerformed(ActionEvent e) {
    //
    // }
    // });
    // list.add(areaZoomMenu);

    return list;
  }
}
