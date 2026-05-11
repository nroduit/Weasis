/*
 * Copyright (c) 2025 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.editor.image;

import com.formdev.flatlaf.extras.FlatSVGIcon;
import com.formdev.flatlaf.extras.FlatSVGIcon.ColorFilter;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.EnumMap;
import java.util.Map;
import javax.swing.Icon;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.gui.util.GuiUtils.IconColor;
import org.weasis.core.api.util.ResourceUtil;
import org.weasis.core.ui.editor.image.SynchData.SyncState;

/**
 * Per-view overlay button toggling automatic synchronization for a single view.
 *
 * <p>The button only renders the current {@link SyncState}; all state transitions are owned by
 * {@link DefaultView2d#updateSynchState()} which is the single source of truth.
 */
public class SynchViewButton extends ViewButton {
  private static final FlatSVGIcon BASE_ICON =
      ResourceUtil.getIcon(ResourceUtil.ActionIcon.SYNCH, 20, 20);
  private static final Color BASE_COLOR = new Color(0x6E6E6E);

  /** Colored icons keyed by the domain {@link SyncState}, computed once at class init. */
  private static final Map<SyncState, FlatSVGIcon> ICONS = new EnumMap<>(SyncState.class);

  static {
    ICONS.put(SyncState.OFF, derive(IconColor.ACTIONS_RED.getColor()));
    ICONS.put(SyncState.ON, derive(IconColor.ACTIONS_GREEN.getColor()));
  }

  private static FlatSVGIcon derive(Color color) {
    return GuiUtils.getDerivedIcon(BASE_ICON, new ColorFilter().add(BASE_COLOR, color));
  }

  protected SyncState state = SyncState.OFF;
  private Color chipColor;

  public SynchViewButton(ShowPopup popup) {
    super(popup, ICONS.get(SyncState.OFF), "sync"); // NON-NLS
  }

  public SyncState getState() {
    return state;
  }

  public void setState(SyncState state) {
    this.state = state;
  }

  /**
   * Set the FoR chip color. {@code null} suppresses the chip — used for views without a known frame
   * of reference, or when the container has no other view to group with.
   */
  public void setChipColor(Color chipColor) {
    this.chipColor = chipColor;
  }

  @Override
  public Icon getIcon() {
    return ICONS.get(state);
  }

  /**
   * Paint a small filled square at the icon center using the per-view chip color (set externally
   * from container-aware logic). Skipped when no color is set.
   */
  @Override
  protected void paintOverlay(Graphics2D g2d, int x, int y, int width, int height) {
    Color chip = chipColor;
    if (chip == null) {
      return;
    }
    int side = Math.max(4, Math.round(width * 0.35f));
    int cx = x + (width - side) / 2;
    int cy = y + (height - side) / 2;

    Object oldAA = g2d.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
    Color oldColor = g2d.getColor();
    g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    g2d.setColor(chip);
    g2d.fillRect(cx, cy, side, side);
    g2d.setColor(chip.darker());
    g2d.drawRect(cx, cy, side - 1, side - 1);
    g2d.setColor(oldColor);
    if (oldAA != null) {
      g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, oldAA);
    }
  }
}
