/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.viewer2d;

import java.awt.Component;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.swing.JComponent;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.gui.util.ShortcutManager;
import org.weasis.core.api.gui.util.SliderChangeListener;
import org.weasis.core.api.gui.util.SliderCineListener;
import org.weasis.core.api.image.OpManager;
import org.weasis.core.api.image.WindowOp;
import org.weasis.core.ui.editor.image.ImageViewerEventManager;
import org.weasis.core.ui.editor.image.InfoValueEditor;
import org.weasis.core.ui.editor.image.InfoValueEditor.Field;
import org.weasis.core.ui.editor.image.ShowPopup;
import org.weasis.core.ui.editor.image.SynchEvent;
import org.weasis.core.ui.editor.image.ZoomToolBar;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.viewer2d.mpr.ReformatMenu;

/**
 * The editors of the values of the corner text of a DICOM view: the plane, the window and level and
 * the zoom. They act on the selected view through the actions of the event manager, so that the
 * synchronized views follow as with the mouse; a click on a value selects its view first.
 */
final class InfoValueEditors {

  static final String PLANE = "plane";
  static final String WINDOW_LEVEL = "wl";
  static final String ZOOM = "zoom";
  static final String ROTATION = "rotation";
  static final String FRAME = "frame";

  private InfoValueEditors() {}

  /** The plane: the acquired plane and the two reformats, null when the series has no volume. */
  static ShowPopup plane(View2d view) {
    if (!ReformatMenu.applies(view)) {
      return null;
    }
    return (invoker, x, y) ->
        InfoValueEditor.showAbove(
            ReformatMenu.popup(view.getEventManager().getSelectedView2dContainer(), view),
            invoker,
            x,
            y);
  }

  static ShowPopup windowLevel(View2d view) {
    return (invoker, x, y) -> {
      if (view.getEventManager() instanceof EventManager manager) {
        showWindowLevel(view, manager, invoker, x, y);
      }
    };
  }

  private static void showWindowLevel(
      View2d view, EventManager manager, Component invoker, int x, int y) {
    Optional<SliderChangeListener> window = manager.getAction(ActionW.WINDOW);
    Optional<SliderChangeListener> level = manager.getAction(ActionW.LEVEL);
    if (window.isEmpty() || level.isEmpty() || !window.get().isActionEnabled()) {
      return;
    }
    // The values of the view, as the corner text shows them (the sliders round them)
    OpManager disOp = view.getDisplayOpManager();
    double w =
        disOp
            .getParamValue(WindowOp.OP_NAME, ActionW.WINDOW.cmd(), Number.class)
            .map(Number::doubleValue)
            .orElse(window.get().getRealValue());
    double l =
        disOp
            .getParamValue(WindowOp.OP_NAME, ActionW.LEVEL.cmd(), Number.class)
            .map(Number::doubleValue)
            .orElse(level.get().getRealValue());
    InfoValueEditor editor =
        new InfoValueEditor(
            List.of(
                field(ActionW.WINDOW.getTitle(), w, window.get(), 1.0),
                field(ActionW.LEVEL.getTitle(), l, level.get(), 1.0)),
            values -> {
              if (isSelected(view)) {
                manager.setWindowLevel(values[0], values[1]);
              }
            });
    editor.addItems(menuItems(manager.getPresetMenu("weasis.contextmenu.presets")));
    editor.show(invoker, x, y);
  }

  static ShowPopup zoom(View2d view) {
    return (invoker, x, y) -> {
      Optional<SliderChangeListener> zoom = view.getEventManager().getAction(ActionW.ZOOM);
      if (zoom.isEmpty() || !zoom.get().isActionEnabled()) {
        return;
      }
      SliderChangeListener z = zoom.get();
      ImageViewerEventManager<DicomImageElement> manager = view.getEventManager();
      Field field =
          field(
              Messages.common("zoom") + " (%)",
              view.getViewModel().getViewScale() * 100.0,
              z,
              10.0);
      Field percent =
          new Field(
              field.label(), field.value(), field.min() * 100.0, field.max() * 100.0, field.step());
      InfoValueEditor editor =
          new InfoValueEditor(
              List.of(percent),
              values -> {
                if (!isSelected(view)) {
                  return;
                }
                // Sent as typed: the steps of the slider would round the value
                double scale = values[0] / 100.0;
                z.setRealValue(scale, false);
                manager.firePropertyChange(
                    ActionW.SYNCH.cmd(),
                    null,
                    new SynchEvent(manager.getSelectedViewPane(), ActionW.ZOOM.cmd(), scale));
              });
      editor.addItems(ZoomToolBar.getZoomListMenuItems(view.getEventManager()));
      editor.show(invoker, x, y);
    };
  }

  /** The angle, with the rotations by a quarter turn and the flip of the Orientation menu. */
  static ShowPopup rotation(View2d view) {
    return (invoker, x, y) -> {
      if (!(view.getEventManager() instanceof EventManager manager)) {
        return;
      }
      Optional<SliderChangeListener> rotate = manager.getAction(ActionW.ROTATION);
      if (rotate.isEmpty() || !rotate.get().isActionEnabled()) {
        return;
      }
      SliderChangeListener r = rotate.get();
      Field angle =
          new Field(
              Messages.getString("InfoLayer.angle") + " (°)",
              r.getSliderValue(),
              r.getSliderMin(),
              r.getSliderMax(),
              1.0);
      InfoValueEditor editor =
          new InfoValueEditor(
              List.of(angle),
              values -> {
                if (isSelected(view)) {
                  r.setSliderValue((int) Math.round(values[0]) % 360);
                }
              });
      editor.addItems(menuItems(manager.getOrientationMenu("weasis.contextmenu.orientation")));
      editor.show(invoker, x, y);
    };
  }

  /**
   * The position in the stack: type a number to go to that image, or go to the first, the middle or
   * the last.
   */
  static ShowPopup frame(View2d view) {
    return (invoker, x, y) -> {
      Optional<SliderCineListener> scroll = view.getEventManager().getAction(ActionW.SCROLL_SERIES);
      if (scroll.isEmpty() || !scroll.get().isActionEnabled()) {
        return;
      }
      SliderChangeListener s = scroll.get();
      if (s.getSliderMax() <= s.getSliderMin()) {
        return; // a single image: nowhere to go
      }
      Field image =
          new Field(
              Messages.getString("InfoValue.image"),
              s.getSliderValue(),
              s.getSliderMin(),
              s.getSliderMax(),
              1.0);
      InfoValueEditor editor =
          new InfoValueEditor(
              List.of(image),
              values -> {
                if (isSelected(view)) {
                  s.setSliderValue((int) Math.round(values[0]));
                }
              });
      ShortcutManager sm = ShortcutManager.getInstance();
      JMenuItem first =
          new JMenuItem(org.weasis.core.Messages.getString("ShortcutManager.first_image"));
      first.setAccelerator(sm.getKeyStroke(ShortcutManager.ID_VIEWER_SCROLL_FIRST));
      first.addActionListener(_ -> s.setSliderValue(s.getSliderMin()));
      JMenuItem last =
          new JMenuItem(org.weasis.core.Messages.getString("ShortcutManager.last_image"));
      last.setAccelerator(sm.getKeyStroke(ShortcutManager.ID_VIEWER_SCROLL_LAST));
      last.addActionListener(_ -> s.setSliderValue(s.getSliderMax()));
      JMenuItem middle = new JMenuItem(Messages.getString("InfoValue.middle_image"));
      middle.addActionListener(
          _ -> s.setSliderValue((int) Math.round((s.getSliderMin() + s.getSliderMax()) / 2.0)));
      editor.addItems(List.of(first, middle, last));
      editor.show(invoker, x, y);
    };
  }

  /**
   * Whether the view of an editor is still the selected one: the editors act through the actions of
   * the event manager, which follow the selected view, so a value must never reach another view.
   */
  private static boolean isSelected(View2d view) {
    return view.getEventManager().getSelectedViewPane() == view;
  }

  /** A field bounded by the range of a slider. */
  private static Field field(String label, double value, SliderChangeListener slider, double step) {
    return new Field(
        label,
        value,
        slider.toModelValue(slider.getSliderMin()),
        slider.toModelValue(slider.getSliderMax()),
        step);
  }

  /** The items of a menu of the event manager, to show them in another popup. */
  private static List<JComponent> menuItems(JMenu menu) {
    List<JComponent> items = new ArrayList<>();
    if (menu != null) {
      for (Component c : menu.getMenuComponents()) {
        if (c instanceof JComponent jc) {
          items.add(jc);
        }
      }
    }
    return items;
  }
}
