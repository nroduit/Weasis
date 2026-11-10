/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.viewer2d.mpr;

import javax.swing.ButtonGroup;
import javax.swing.JMenu;
import javax.swing.JPopupMenu;
import javax.swing.JRadioButtonMenuItem;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.ui.editor.image.ImageViewerPlugin;
import org.weasis.core.ui.editor.image.ViewCanvas;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.viewer2d.Messages;
import org.weasis.dicom.viewer2d.mpr.MprView.Plane;

/**
 * The plane of a pane as a menu: the acquired plane of its series and the two reformats, the shown
 * one checked. The same items make the popup of the view button and the submenu of the context
 * menu.
 */
public final class ReformatMenu {

  private ReformatMenu() {}

  /** Whether the pane shows a series that can be reformatted, or a reformat. */
  public static boolean applies(ViewCanvas<DicomImageElement> view) {
    return view != null
        && view.getSeries() != null
        && ReformatService.canReformat(ReformatService.sourceOf(view.getSeries()));
  }

  public static JPopupMenu popup(
      ImageViewerPlugin<DicomImageElement> container, ViewCanvas<DicomImageElement> view) {
    JPopupMenu popup = new JPopupMenu(Messages.getString("ReformatMenu.title"));
    fill(popup, container, view);
    return popup;
  }

  public static JMenu menu(
      ImageViewerPlugin<DicomImageElement> container, ViewCanvas<DicomImageElement> view) {
    JMenu menu = new JMenu(Messages.getString("ReformatMenu.title"));
    fill(menu, container, view);
    return menu;
  }

  private static void fill(
      javax.swing.JComponent menu,
      ImageViewerPlugin<DicomImageElement> container,
      ViewCanvas<DicomImageElement> view) {
    MediaSeries<DicomImageElement> shown = view.getSeries();
    Plane current = ReformatService.planeOf(shown);
    Plane acquired = ReformatService.planeOf(ReformatService.sourceOf(shown));
    ButtonGroup group = new ButtonGroup();
    for (Plane plane : Plane.values()) {
      String label = label(plane);
      if (plane == acquired) {
        label = Messages.format("ReformatMenu.acquired", label);
      }
      JRadioButtonMenuItem item = new JRadioButtonMenuItem(label, plane == current);
      item.addActionListener(e -> ReformatService.switchPlane(container, view, plane));
      group.add(item);
      menu.add(item);
    }
  }

  public static String label(Plane plane) {
    return switch (plane) {
      case AXIAL -> Messages.getString("axial");
      case CORONAL -> Messages.getString("coronal");
      case SAGITTAL -> Messages.getString("sagittal");
    };
  }
}
