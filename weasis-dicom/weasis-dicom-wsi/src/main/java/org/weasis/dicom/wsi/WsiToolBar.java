/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.wsi;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import javax.swing.JButton;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComboBox;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import org.weasis.core.api.setting.Settings;
import org.weasis.core.api.util.ResourceUtil;
import org.weasis.core.api.util.ResourceUtil.ActionIcon;
import org.weasis.core.ui.editor.SeriesViewerEvent;
import org.weasis.core.ui.editor.SeriesViewerEvent.EVENT;
import org.weasis.core.ui.editor.SeriesViewerListener;
import org.weasis.core.ui.util.WtoolBar;
import org.weasis.dicom.viewer2d.EventManager;
import org.weasis.dicom.wsi.model.Slide;

/**
 * Navigation of the slide viewer: fit, 1:1, rotation, the magnification presets of {@link
 * WsiSettings#ZOOM_PRESETS}, the focal plane and optical path selectors of a slide that has
 * several, and the label image.
 */
public class WsiToolBar extends WtoolBar implements SeriesViewerListener {

  private final JComboBox<String> presets = new JComboBox<>();
  private final JComboBox<String> focalPlanes = new JComboBox<>();
  private final JComboBox<String> opticalPaths = new JComboBox<>();
  private final JButton annotations =
      new JButton(ResourceUtil.getToolBarIcon(ActionIcon.DRAW_POLYGON));
  private double[] magnifications = WsiSettings.presets();
  private boolean filling;

  public WsiToolBar(int index) {
    super(Messages.getString("WsiToolBar.name"), index);
    add(button(ActionIcon.ZOOM_BEST_FIT, "WsiToolBar.fit", WsiView2d::fitToView));
    add(button(ActionIcon.ZOOM_ORIGINAL, "WsiToolBar.one_to_one", WsiView2d::oneToOne));
    add(button(ActionIcon.ROTATE_COUNTERCLOCKWISE, "WsiToolBar.rotate_left", v -> v.rotate(-1)));
    add(button(ActionIcon.ROTATE_CLOCKWISE, "WsiToolBar.rotate_right", v -> v.rotate(1)));

    presets.setToolTipText(Messages.getString("WsiToolBar.magnification"));
    presets.addActionListener(
        _ -> {
          int selected = presets.getSelectedIndex();
          if (!filling && selected > 0 && selected <= magnifications.length) {
            selectedView().ifPresent(v -> v.zoomToMagnification(magnifications[selected - 1]));
            presets.setSelectedIndex(0);
          }
        });
    fillPresets();
    // The toolbar lives as long as the application: the listener is never removed
    Settings.addListener(WsiSettings.ZOOM_PRESETS, _ -> fillPresets());
    add(presets);

    focalPlanes.setToolTipText(Messages.getString("WsiToolBar.focal_plane"));
    focalPlanes.addActionListener(
        _ -> {
          if (!filling && focalPlanes.getSelectedIndex() >= 0) {
            selectedView()
                .ifPresent(v -> v.setPlane(focalPlanes.getSelectedIndex(), v.opticalPath()));
          }
        });
    opticalPaths.setToolTipText(Messages.getString("WsiToolBar.optical_path"));
    opticalPaths.addActionListener(
        _ -> {
          if (!filling && opticalPaths.getSelectedIndex() >= 0) {
            selectedView()
                .ifPresent(v -> v.setPlane(v.focalPlane(), opticalPaths.getSelectedIndex()));
          }
        });
    add(focalPlanes);
    add(opticalPaths);
    annotations.setToolTipText(Messages.getString("WsiToolBar.annotations"));
    annotations.addActionListener(_ -> showAnnotationGroups());
    add(annotations);
    refreshSelectors();
    EventManager.getInstance().addSeriesViewerListener(this);

    add(button(ActionIcon.METADATA, "WsiToolBar.label", WsiView2d::showLabel));
  }

  /** Another view or slide may have other planes and paths: the selectors follow it. */
  @Override
  public void changingViewContentEvent(SeriesViewerEvent event) {
    EVENT type = event.getEventType();
    if (type == EVENT.SELECT || type == EVENT.SELECT_VIEW || type == EVENT.LAYOUT) {
      refreshSelectors();
    }
  }

  // The selectors show only when the slide of the selected view has several planes or paths
  private void refreshSelectors() {
    filling = true;
    try {
      Optional<WsiView2d> view = selectedView();
      Optional<Slide> slide = view.flatMap(WsiView2d::slide);
      int planes = slide.map(sl -> sl.base().focalPlanes()).orElse(1);
      List<String> paths = slide.map(sl -> sl.base().opticalPaths()).orElse(List.of());
      fill(focalPlanes, planeLabels(planes), view.map(WsiView2d::focalPlane).orElse(0), planes > 1);
      fill(opticalPaths, paths, view.map(WsiView2d::opticalPath).orElse(0), paths.size() > 1);
      annotations.setVisible(view.map(v -> !v.annotationElements().isEmpty()).orElse(false));
    } finally {
      filling = false;
    }
  }

  // A menu with one check box per annotation group of the slide of the selected view
  private void showAnnotationGroups() {
    Optional<WsiView2d> view = selectedView();
    if (view.isEmpty()) {
      return;
    }
    JPopupMenu menu = new JPopupMenu();
    List<WsiView2d.AnnotationGroup> groups = view.get().annotationGroups();
    if (groups.isEmpty()) {
      JMenuItem none = new JMenuItem(Messages.getString("WsiToolBar.no_annotations"));
      none.setEnabled(false);
      menu.add(none);
    }
    for (WsiView2d.AnnotationGroup group : groups) {
      JCheckBoxMenuItem item =
          new JCheckBoxMenuItem(
              Messages.getString("WsiToolBar.annotation_group")
                  .formatted(group.group().label(), group.group().count()),
              group.shown());
      item.setForeground(group.group().color());
      item.addActionListener(
          _ -> {
            group.setShown(item.isSelected());
            view.get().repaint();
          });
      menu.add(item);
    }
    menu.show(annotations, 0, annotations.getHeight());
  }

  private static List<String> planeLabels(int planes) {
    List<String> labels = new ArrayList<>();
    for (int i = 1; i <= planes; i++) {
      labels.add(Messages.getString("WsiToolBar.plane").formatted(i, planes));
    }
    return labels;
  }

  private static void fill(
      JComboBox<String> combo, List<String> items, int selected, boolean shown) {
    combo.removeAllItems();
    items.forEach(combo::addItem);
    if (selected >= 0 && selected < items.size()) {
      combo.setSelectedIndex(selected);
    }
    combo.setMaximumSize(null);
    combo.setMaximumSize(combo.getPreferredSize());
    combo.setVisible(shown);
    combo.revalidate();
  }

  private void fillPresets() {
    filling = true;
    try {
      magnifications = WsiSettings.presets();
      presets.removeAllItems();
      presets.addItem(Messages.getString("WsiToolBar.magnification"));
      for (double m : magnifications) {
        presets.addItem(Messages.getString("WsiView.magnification").formatted(m));
      }
      presets.setMaximumSize(null);
      presets.setMaximumSize(presets.getPreferredSize());
      presets.revalidate();
    } finally {
      filling = false;
    }
  }

  private static JButton button(ActionIcon icon, String tooltipKey, Consumer<WsiView2d> action) {
    JButton button = new JButton(ResourceUtil.getToolBarIcon(icon));
    button.setToolTipText(Messages.getString(tooltipKey));
    button.addActionListener(_ -> selectedView().ifPresent(action));
    return button;
  }

  private static Optional<WsiView2d> selectedView() {
    return EventManager.getInstance().getSelectedViewPane() instanceof WsiView2d view
        ? Optional.of(view)
        : Optional.empty();
  }
}
