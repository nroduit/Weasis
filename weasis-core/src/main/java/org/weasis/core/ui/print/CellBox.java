/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.print;

import java.awt.geom.Rectangle2D;
import org.weasis.core.api.gui.layout.MigCell;

/** A cell of the layout placed on the page, in page millimeters from the top-left corner. */
public record CellBox(MigCell cell, Rectangle2D boundsMm) {

  public double widthMm() {
    return boundsMm.getWidth();
  }

  public double heightMm() {
    return boundsMm.getHeight();
  }
}
