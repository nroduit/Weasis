/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.viewer2d.fusion;

import java.awt.Cursor;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.SwingUtilities;
import org.weasis.core.ui.editor.image.DefaultView2d;
import org.weasis.core.ui.editor.image.ViewCanvas;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.viewer2d.EventManager;

/**
 * Moves the top of the overlay display window by dragging the {@link FusionColorBar} with the left
 * button, whatever action the button carries elsewhere on the view: up widens the scale, down
 * narrows it, the bottom stays put. The press is consumed so the action bound to the button (W/L,
 * pan…) does not run as well.
 *
 * <p>It drives the scale maximum slider rather than the window itself, so both stay in step.
 */
public final class FusionScaleDragHandler extends MouseAdapter {

  private static final Cursor RESIZE_CURSOR = Cursor.getPredefinedCursor(Cursor.N_RESIZE_CURSOR);

  /**
   * Drag, in pixels, that doubles or halves the span of the scale: relative steps feel the same on
   * a 0 to 5 SUV scale as on a 0 to 20 one, which a fixed value per pixel would not.
   */
  private static final double DOUBLING_PIXELS = 128.0;

  private final ViewCanvas<DicomImageElement> view;
  private FusionWindow dragStart;
  private int startY;
  private boolean hover;

  public FusionScaleDragHandler(ViewCanvas<DicomImageElement> view) {
    this.view = view;
  }

  @Override
  public void mousePressed(MouseEvent e) {
    if (e.isConsumed()
        || !SwingUtilities.isLeftMouseButton(e)
        || !FusionColorBar.isOnBar(view, e.getPoint())) {
      return;
    }
    // FocusHandler runs first and has selected this view, so the window is the one shown here.
    FusionWindow window = FusionController.windowOf(view);
    if (window == null) {
      return;
    }
    e.consume();
    dragStart = window;
    startY = e.getY();
    view.getJComponent().setCursor(RESIZE_CURSOR);
  }

  @Override
  public void mouseDragged(MouseEvent e) {
    if (dragStart == null) {
      return;
    }
    e.consume();
    double span = dragStart.displayMax() - dragStart.displayMin();
    double top =
        dragStart.displayMin() + span * Math.pow(2.0, (startY - e.getY()) / DOUBLING_PIXELS);
    EventManager.getInstance()
        .getAction(FusionAction.WINDOW_TOP)
        .ifPresent(a -> a.setRealValue(top));
  }

  @Override
  public void mouseReleased(MouseEvent e) {
    if (dragStart != null) {
      e.consume();
      dragStart = null;
      hover = false;
    }
  }

  @Override
  public void mouseMoved(MouseEvent e) {
    boolean onBar = FusionColorBar.isOnBar(view, e.getPoint());
    if (onBar != hover) {
      hover = onBar;
      view.getJComponent().setCursor(onBar ? RESIZE_CURSOR : DefaultView2d.DEFAULT_CURSOR);
    }
  }
}
