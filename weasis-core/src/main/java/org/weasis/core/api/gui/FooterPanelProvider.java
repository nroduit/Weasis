/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.api.gui;

import javax.swing.Icon;
import javax.swing.JComponent;
import org.pf4j.ExtensionPoint;

/**
 * Provides a panel for the bottom-left footer of the main window (e.g. embedded DICOM import or
 * export). Each provider contributes one toggle button; expanding it shows the panel in a vertical
 * split under the explorer.
 */
public interface FooterPanelProvider extends ExtensionPoint {

  String getTitle();

  Icon getIcon();

  /** Order of the footer button, lower first. */
  int getPosition();

  /**
   * Creates the embedded panel; {@code closeCallback} collapses the footer when the panel's own
   * Close action is used.
   */
  JComponent createPanel(Runnable closeCallback);

  /** Invoked when the panel is collapsed so it can release its resources. */
  void onCollapse(JComponent panel);
}
