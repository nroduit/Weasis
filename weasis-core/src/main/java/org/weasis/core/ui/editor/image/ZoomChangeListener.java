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

import java.awt.Component;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.geom.Point2D;
import org.weasis.core.api.gui.util.ActionState;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.gui.util.Feature;
import org.weasis.core.api.gui.util.SliderChangeListener;
import org.weasis.core.api.setting.CoreSettings;
import org.weasis.core.api.setting.Settings;

/**
 * Zoom slider that remembers where the mouse zooms. While a wheel step or a drag changes the value,
 * the pointer is known as an offset from the center of the view (for a drag, the point where the
 * button was pressed); {@link #withAnchor(SynchEvent)} adds it to the zoom event when cursor zoom
 * is enabled ({@link CoreSettings#ZOOM_AT_CURSOR}). Changes from the slider, the keyboard or the
 * commands carry no anchor and zoom around the center.
 */
public abstract class ZoomChangeListener extends SliderChangeListener {

  /** Key of the anchor in a {@link SynchEvent}: a {@link Point2D} offset from the view center. */
  public static final String ANCHOR_CMD = "zoom.anchor"; // NON-NLS

  private Point2D pressAnchor;
  private Point2D anchor;

  protected ZoomChangeListener(
      Feature<? extends ActionState> action,
      double min,
      double max,
      double value,
      boolean triggerWhenAdjusting,
      double mouseSensitivity,
      int sliderRange) {
    super(action, min, max, value, triggerWhenAdjusting, mouseSensitivity, sliderRange);
  }

  @Override
  public void mousePressed(MouseEvent e) {
    pressAnchor = getAnchor(e);
    super.mousePressed(e);
  }

  @Override
  public void mouseDragged(MouseEvent e) {
    anchor = pressAnchor;
    try {
      super.mouseDragged(e);
    } finally {
      anchor = null;
    }
  }

  @Override
  public void mouseReleased(MouseEvent e) {
    super.mouseReleased(e);
    if (isBoundButton(e)) {
      pressAnchor = null;
    }
  }

  /** Records the pointer, then lets {@link #wheel(MouseWheelEvent)} change the value. */
  @Override
  public final void mouseWheelMoved(MouseWheelEvent e) {
    anchor = getAnchor(e);
    try {
      wheel(e);
    } finally {
      anchor = null;
    }
  }

  /** Changes the value for a wheel event: one slider step per notch by default. */
  protected void wheel(MouseWheelEvent e) {
    super.mouseWheelMoved(e);
  }

  /**
   * Adds the anchor of the mouse event being handled, if any and if cursor zoom is enabled.
   *
   * @return the event
   */
  protected SynchEvent withAnchor(SynchEvent event) {
    Point2D point = anchor;
    if (point != null && Settings.get(CoreSettings.ZOOM_AT_CURSOR)) {
      event.put(ANCHOR_CMD, new Point2D.Double(point.getX(), point.getY()));
    }
    return event;
  }

  /**
   * The anchor of a zoom event for a view: the issuing view zooms around the pointer; another view
   * does it only when it shares the pan with the issuer, so that both keep showing the same region.
   * Otherwise the view zooms around its center.
   *
   * @param synch the zoom event
   * @param view the view receiving the event
   * @param issuerSyncData the synchronization of the issuing view, may be null
   * @param synchData the synchronization of the receiving view, may be null
   * @return the anchor as an offset from the view center, or null to zoom around the center
   */
  public static Point2D getAnchor(
      SynchEvent synch, ViewCanvas<?> view, SynchData issuerSyncData, SynchData synchData) {
    if (!(synch.getEvents().get(ANCHOR_CMD) instanceof Point2D point)) {
      return null;
    }
    if (synch.getView() == view) {
      return point;
    }
    String pan = ActionW.PAN.cmd();
    boolean panShared =
        (synchData == null || synchData.isActionEnable(pan))
            && (issuerSyncData == null || issuerSyncData.isActionEnable(pan));
    return panShared ? point : null;
  }

  /** The pointer as an offset from the center of the component that received the event. */
  static Point2D getAnchor(MouseEvent e) {
    Component c = e.getComponent();
    if (c == null || c.getWidth() <= 0 || c.getHeight() <= 0) {
      return null;
    }
    return new Point2D.Double(e.getX() - c.getWidth() * 0.5, e.getY() - c.getHeight() * 0.5);
  }
}
