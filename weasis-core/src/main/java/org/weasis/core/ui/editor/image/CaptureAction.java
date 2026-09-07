/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.editor.image;

import java.awt.event.ActionEvent;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import javax.swing.Action;
import org.weasis.core.api.gui.util.ShortcutManager;
import org.weasis.core.api.util.ResourceUtil;
import org.weasis.core.api.util.ResourceUtil.ActionIcon;
import org.weasis.core.ui.util.DefaultAction;

/**
 * The three ways to capture a viewer, each with its icon and its configurable shortcut. A viewer
 * builds its export actions through {@link #action}, so the capture button, the menus and the
 * shortcut handler all find the same action by its kind.
 */
public enum CaptureAction {
  /** One image of the selected view. */
  SCREENSHOT(ShortcutManager.ID_VIEWER_SCREENSHOT, ActionIcon.SCREENSHOT),
  /** An animated image or a DICOM cine from what the view already holds: a cine loop, a turn. */
  ANIMATION(ShortcutManager.ID_VIEWER_EXPORT_ANIMATION, ActionIcon.EXPORT_ANIMATION),
  /** What happens in the viewer while the user works. */
  RECORD(ShortcutManager.ID_VIEWER_RECORD, ActionIcon.RECORD);

  private final String shortcutId;
  private final ActionIcon icon;

  CaptureAction(String shortcutId, ActionIcon icon) {
    this.shortcutId = shortcutId;
    this.icon = icon;
  }

  public String getShortcutId() {
    return shortcutId;
  }

  /** An action of this kind, carrying the icon and the accelerator the user chose. */
  public DefaultAction action(String title, Consumer<ActionEvent> listener) {
    DefaultAction action = new DefaultAction(title, ResourceUtil.getIcon(icon), listener);
    action.putValue(Action.ACTION_COMMAND_KEY, name());
    action.putValue(Action.ACCELERATOR_KEY, ShortcutManager.getInstance().getKeyStroke(shortcutId));
    return action;
  }

  /** The action of this kind among {@code actions}, if the viewer offers one. */
  public Optional<Action> find(List<Action> actions) {
    if (actions == null) {
      return Optional.empty();
    }
    return actions.stream()
        .filter(a -> name().equals(a.getValue(Action.ACTION_COMMAND_KEY)))
        .findFirst();
  }
}
