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

import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import javax.swing.Icon;

/**
 * Deterministic mapping from a DICOM {@code FrameOfReferenceUID} to a color drawn from a small
 * qualitative palette. The same UID always resolves to the same color across sessions and across
 * containers, so a user can recognize at a glance which views belong to the same spatial reference
 * frame.
 *
 * <p>Palette: Kelly's 22 colors of maximum contrast (Kenneth Kelly, 1965), with white and the
 * "vivid red" (#BE0032) removed — white is invisible on light themes, and red collides with the red
 * on/off tint of {@link SynchViewButton}. The 20 remaining entries are ordered as Kelly recommended
 * (most-distinct first), so the chips picked when only a few FoRs are present remain the most
 * legible.
 */
public final class FrameOfReferenceColor {

  private static final Color[] PALETTE = {
    new Color(0xF3C300), // vivid yellow
    new Color(0x0067A5), // strong blue
    new Color(0x875692), // strong purple
    new Color(0xA1CAF1), // very light blue
    new Color(0xF6A600), // vivid orange yellow
    new Color(0xE68FAC), // strong purplish pink
    new Color(0x008856), // vivid green
    new Color(0x882D17), // strong reddish brown
    new Color(0x8DB600), // vivid yellowish green
    new Color(0x848482), // medium gray
    new Color(0xF99379), // strong yellowish pink
    new Color(0xB3446C), // strong purplish red
    new Color(0x654522), // deep yellowish brown
    new Color(0x222222), // black
    new Color(0x2B3D26), // dark olive green
    new Color(0xF38400), // vivid orange
    new Color(0x604E97), // strong violet
    new Color(0xDCD300), // vivid greenish yellow
    new Color(0xE25822), // vivid reddish orange
    new Color(0xC2B280), // grayish yellow (buff)
  };

  private FrameOfReferenceColor() {}

  /**
   * Return the chip color for the given UID, or {@code null} when no UID is available — callers use
   * the {@code null} return as the signal to skip drawing the chip entirely.
   */
  public static Color colorFor(String frameOfReferenceUID, Iterable<String> distinctUidsInOrder) {
    if (frameOfReferenceUID == null || frameOfReferenceUID.isBlank()) {
      return null;
    }
    int slot = 0;
    String last = null;
    for (String uid : distinctUidsInOrder) {
      if (uid == null || uid.isBlank() || uid.equals(last)) {
        continue;
      }
      if (frameOfReferenceUID.equals(uid)) {
        return PALETTE[slot % PALETTE.length];
      }
      last = uid;
      slot++;
    }
    return null;
  }

  /**
   * Build a square swatch {@link Icon} of the given color, suitable for a {@link
   * javax.swing.JMenuItem#setIcon(Icon)}. Returns {@code null} when {@code color} is {@code null}
   * so callers can pass the result of {@link #colorFor(String, Iterable)} straight through.
   */
  public static Icon swatch(Color color, int size) {
    if (color == null) {
      return null;
    }
    return new SwatchIcon(color, size);
  }

  private record SwatchIcon(Color color, int size) implements Icon {
    @Override
    public void paintIcon(Component c, Graphics g, int x, int y) {
      Graphics2D g2 = (Graphics2D) g.create();
      try {
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(color);
        g2.fillRect(x, y, size, size);
        g2.setColor(color.darker());
        g2.drawRect(x, y, size - 1, size - 1);
      } finally {
        g2.dispose();
      }
    }

    @Override
    public int getIconWidth() {
      return size;
    }

    @Override
    public int getIconHeight() {
      return size;
    }
  }
}
