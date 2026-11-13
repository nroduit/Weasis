/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.model.layer;

import java.awt.Rectangle;
import java.util.Objects;
import org.weasis.core.ui.editor.image.ShowPopup;

/**
 * A value of the corner text that can be clicked to edit it (the plane, the window and level, the
 * zoom...). The info layer records where it drew the value at each paint; the view shows a hover
 * background and opens the editor on a click.
 */
public final class InfoValue {

  private final String id;
  private final Rectangle bounds = new Rectangle();
  private ShowPopup editor;
  private String tooltip;
  private boolean painted;
  private boolean hover;

  public InfoValue(String id) {
    this.id = Objects.requireNonNull(id);
  }

  public String getId() {
    return id;
  }

  /** The area of the value text in view coordinates, valid while {@link #isPainted()}. */
  public Rectangle getBounds() {
    return bounds;
  }

  public ShowPopup getEditor() {
    return editor;
  }

  public String getTooltip() {
    return tooltip;
  }

  /** Whether the last paint of the layer drew this value. */
  public boolean isPainted() {
    return painted;
  }

  public boolean isHover() {
    return hover;
  }

  public void setHover(boolean hover) {
    this.hover = hover;
  }

  public boolean contains(int x, int y) {
    return painted && editor != null && bounds.contains(x, y);
  }

  void update(Rectangle area, ShowPopup editor, String tooltip) {
    this.bounds.setBounds(area);
    this.editor = editor;
    this.tooltip = tooltip;
    this.painted = true;
  }

  void clear() {
    this.painted = false;
  }
}
