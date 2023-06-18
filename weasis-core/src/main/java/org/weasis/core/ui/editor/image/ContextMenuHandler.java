/*
 * Copyright (c) 2023 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.editor.image;

import java.awt.event.MouseEvent;
import java.util.List;
import javax.swing.JPopupMenu;
import org.weasis.core.api.gui.util.MouseActionAdapter;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.ui.model.graphic.Graphic;

public class ContextMenuHandler<E extends ImageElement> extends MouseActionAdapter {
  private final ViewCanvas<E> viewCanvas;

  public ContextMenuHandler(ViewCanvas<E> viewCanvas) {
    this.viewCanvas = viewCanvas;
  }

  @Override
  public void mousePressed(final MouseEvent evt) {
    showPopup(evt);
  }

  @Override
  public void mouseReleased(final MouseEvent evt) {
    showPopup(evt);
  }

  private void showPopup(final MouseEvent evt) {
    // Context menu
    if ((evt.getModifiersEx() & getButtonMaskEx()) != 0) {
      JPopupMenu popupMenu = null;
      final List<Graphic> selected = viewCanvas.getGraphicManager().getSelectedGraphics();
      if (!selected.isEmpty() && viewCanvas.isDrawActionActive()) {
        popupMenu = viewCanvas.buildGraphicContextMenu(evt, selected);
      } else if (viewCanvas.hasValidContent()) {
        popupMenu = viewCanvas.buildContextMenu(evt);
      }
      if (popupMenu != null) {
        popupMenu.show(evt.getComponent(), evt.getX(), evt.getY());
      }
    }
  }
}
