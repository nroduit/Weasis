/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.util.tree;

import com.formdev.flatlaf.icons.FlatCheckBoxIcon;
import java.awt.BasicStroke;
import java.awt.Component;
import java.awt.Graphics2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;

/**
 * Adds a fourth mark to the FlatLaf check box icon for {@link
 * TreeCheckingModel.CheckState#CHECKED_PARTIAL}: a check mark above the indeterminate bar, so that
 * a checked node with partially checked descendants is not drawn like an unchecked one.
 */
class TreeCheckBoxIcon extends FlatCheckBoxIcon {

  private boolean partial;

  void setPartial(boolean partial) {
    this.partial = partial;
  }

  @Override
  protected void paintCheckmark(Component c, Graphics2D g) {
    if (!partial) {
      super.paintCheckmark(c, g);
      return;
    }
    // Same shape as the default check mark, shrunk to leave room for the bar underneath
    Path2D.Float path = new Path2D.Float(Path2D.WIND_NON_ZERO, 3);
    path.moveTo(4.3f, 5.8f);
    path.lineTo(6.3f, 8f);
    path.lineTo(11.2f, 2.2f);
    g.setStroke(new BasicStroke(1.7f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
    g.draw(path);
    g.fill(new RoundRectangle2D.Float(3.75f, 10.2f, 8.5f, 2.1f, 2f, 2f));
  }
}
