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

import java.awt.Color;
import java.awt.Point;
import java.awt.Rectangle;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.Popup;
import javax.swing.PopupFactory;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

/**
 * A short message shown over a view without stealing the focus and without asking anything. It is
 * what replaces a modal dialog for something the user does not have to answer, such as a graphic
 * pasted outside the image.
 */
public final class ViewNotification {

  /** Milliseconds the message stays on screen. */
  public static final int DURATION = 3000;

  private ViewNotification() {}

  public static void show(JComponent view, String message) {
    if (view == null || message == null || message.isBlank()) {
      return;
    }
    SwingUtilities.invokeLater(() -> showNow(view, message));
  }

  private static void showNow(JComponent view, String message) {
    if (!view.isShowing()) {
      return;
    }
    JLabel label = new JLabel(message);
    label.setOpaque(true);
    label.setBackground(new Color(0, 0, 0, 200));
    label.setForeground(Color.WHITE);
    label.setBorder(
        BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(Color.GRAY),
            BorderFactory.createEmptyBorder(6, 12, 6, 12)));

    Rectangle bounds = view.getVisibleRect();
    Point origin = view.getLocationOnScreen();
    int width = label.getPreferredSize().width;
    int x = origin.x + bounds.x + Math.max(0, (bounds.width - width) / 2);
    int y = origin.y + bounds.y + bounds.height - label.getPreferredSize().height - 24;

    Popup popup = PopupFactory.getSharedInstance().getPopup(view, label, x, y);
    popup.show();

    Timer timer = new Timer(DURATION, _ -> popup.hide());
    timer.setRepeats(false);
    timer.start();
  }
}
