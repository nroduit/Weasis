/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.model.utils.imp;

import java.awt.event.MouseEvent;
import org.weasis.core.ui.model.graphic.DragGraphic;
import org.weasis.core.ui.util.MouseEventDouble;

public class SelectedDragSequence extends DefaultDragSequence {
  public SelectedDragSequence(DragGraphic graphic) {
    super(graphic);
  }

  @Override
  public Boolean completeDrag(MouseEventDouble mouseEvent) {
    if (mouseEvent != null && mouseEvent.getID() == MouseEvent.MOUSE_RELEASED) {
      graphic.fireRemoveAndRepaintAction();
    }
    return Boolean.TRUE;
  }
}
