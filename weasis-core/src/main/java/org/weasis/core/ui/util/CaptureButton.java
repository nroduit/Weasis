/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.util;

import com.formdev.flatlaf.FlatClientProperties;
import java.awt.Color;
import java.util.List;
import java.util.function.Supplier;
import javax.swing.Action;
import javax.swing.JButton;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.Timer;
import org.weasis.core.Messages;
import org.weasis.core.api.gui.util.DropButtonIcon;
import org.weasis.core.api.gui.util.GuiExecutor;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.util.ResourceUtil;
import org.weasis.core.api.util.ResourceUtil.ActionIcon;
import org.weasis.core.ui.editor.image.export.AnimationRecorder;
import org.weasis.core.ui.editor.image.export.RecordingState;

/**
 * The one place to capture the screen, in the menu bar next to the masking indicator. Its menu
 * lists what the selected viewer can export — a screenshot, a cine loop, a rotation, a recording —
 * so it fits every viewer type without knowing any of them. While a recording runs, the button
 * turns into a red badge with the elapsed time and the frame budget, and its click offers to stop;
 * being part of the application window, it cannot be hidden by the window manager the way a
 * floating indicator can.
 */
public class CaptureButton extends JButton {

  private static final Color RECORDING_BACKGROUND = new Color(0xD64545);

  /**
   * Larger than a menu glyph: the button sits in the title-bar row, next to the window controls.
   */
  private static final int ICON_SIZE = 20;

  private final Supplier<List<Action>> exportActions;
  private final Runnable stateListener = () -> GuiExecutor.execute(this::refresh);
  private final Timer clock = new Timer(1000, _ -> refresh());
  private final Color idleBackground = getBackground();
  private final Color idleForeground = getForeground();

  /**
   * @param exportActions what the selected viewer offers, asked each time the menu opens
   */
  public CaptureButton(Supplier<List<Action>> exportActions) {
    this.exportActions = exportActions;
    putClientProperty(
        FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_ROUND_RECT);
    setFocusable(false);
    setIconTextGap(6);
    addActionListener(_ -> showMenu());
    refresh();
  }

  @Override
  public void addNotify() {
    super.addNotify();
    RecordingState.addChangeListener(stateListener);
    refresh();
  }

  @Override
  public void removeNotify() {
    RecordingState.removeChangeListener(stateListener);
    clock.stop();
    super.removeNotify();
  }

  private void refresh() {
    AnimationRecorder recorder = RecordingState.active().orElse(null);
    if (recorder != null) {
      setIcon(ResourceUtil.getIcon(ActionIcon.RECORD, ICON_SIZE, ICON_SIZE));
      setText(recorder.status());
      setToolTipText(Messages.getString("animation.indicator.tip"));
      setBackground(RECORDING_BACKGROUND);
      setForeground(Color.WHITE);
      if (!clock.isRunning()) {
        clock.start();
      }
    } else {
      clock.stop();
      setIcon(
          DropButtonIcon.createDropButtonIcon(
              ResourceUtil.getIcon(ActionIcon.CAPTURE, ICON_SIZE, ICON_SIZE)));
      setText(Messages.getString("capture.button"));
      setToolTipText(Messages.getString("capture.button.tip"));
      setBackground(idleBackground);
      setForeground(idleForeground);
    }
    if (getParent() != null) {
      getParent().revalidate();
      getParent().repaint();
    }
  }

  private void showMenu() {
    JPopupMenu menu = new JPopupMenu();
    if (RecordingState.isRecording()) {
      JMenuItem stop = new JMenuItem(Messages.getString("animation.stop"));
      stop.addActionListener(_ -> RecordingState.active().ifPresent(AnimationRecorder::stop));
      menu.add(stop);
    } else {
      List<Action> actions = exportActions.get();
      if (actions == null || actions.isEmpty()) {
        JMenuItem none = new JMenuItem(Messages.getString("capture.button.none"));
        none.setEnabled(false);
        menu.add(none);
      } else {
        for (Action action : actions) {
          JMenuItem item = new JMenuItem(action);
          GuiUtils.applySelectedIconEffect(item);
          menu.add(item);
        }
      }
    }
    menu.show(this, 0, getHeight());
  }
}
