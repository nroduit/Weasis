/*
 * Copyright (c) 2024 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.viewer2d.mpr.cmpr;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import net.miginfocom.swing.MigLayout;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.image.util.Unit;
import org.weasis.core.util.StringUtil;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.viewer2d.mpr.MprView;
import org.weasis.dicom.viewer2d.mpr.Volume;

/**
 * Modal parameter dialog shown before {@link CurvedMprBuilder#openCrossSectionSeries}. Adjusts slab
 * width, slab height, and inter-cut spacing; the unit label tracks the source image's pixel-spacing
 * unit (mm when calibrated, pix otherwise). Spinner ranges and steps are clamped so the supplied
 * defaults always sit inside the spinner range — important for tall volumes whose full Z extent
 * might exceed a fixed cap or uncalibrated data where {@code pixelMm} is 1.0.
 */
public final class CrossSectionDialog {

  private CrossSectionDialog() {}

  /**
   * Show the dialog seeded with sensible defaults for the source view's volume. Returns the user's
   * choice, or {@code null} when they cancel.
   */
  public static CrossSectionParams prompt(MprView sourceView) {
    Volume<?, ?> volume = sourceView.getMprController().getVolume();
    return prompt(sourceView.getImage(), volume, CrossSectionParams.defaults(volume));
  }

  /** Variant that lets the caller supply explicit defaults (useful for unit tests). */
  public static CrossSectionParams prompt(
      DicomImageElement refImg, Volume<?, ?> volume, CrossSectionParams defaults) {
    double pixelMm = sanitizePixelMm(volume.getMinPixelRatio());
    Unit unit = refImg == null ? Unit.MILLIMETER : refImg.getPixelSpacingUnit();
    String unitLabel = unit == null ? Unit.MILLIMETER.getAbbreviation() : unit.getAbbreviation();

    JSpinner widthSpinner = makeSpinner(defaults.widthMm(), 1.0, 200.0, 1.0);
    JSpinner heightSpinner = makeSpinner(defaults.heightMm(), 1.0, 1000.0, 1.0);
    JSpinner spacingSpinner = makeSpinner(defaults.spacingMm(), pixelMm, 50.0, pixelMm);

    JPanel panel =
        new JPanel(
            new MigLayout(
                "fillx, ins 8lp, wrap 2", // NON-NLS
                "[align right][grow, fill, 80lp::]", // NON-NLS
                "[]6lp[]6lp[]")); // NON-NLS
    panel.add(new JLabel("Slab width (%s)".formatted(unitLabel) + StringUtil.COLON)); // NON-NLS
    panel.add(widthSpinner);
    panel.add(new JLabel("Slab height (%s)".formatted(unitLabel) + StringUtil.COLON)); // NON-NLS
    panel.add(heightSpinner);
    panel.add(
        new JLabel("Spacing between cuts (%s)".formatted(unitLabel) + StringUtil.COLON)); // NON-NLS
    panel.add(spacingSpinner);

    JButton reset = new JButton("Reset to defaults"); // NON-NLS
    reset.addActionListener(
        e -> {
          widthSpinner.setValue(defaults.widthMm());
          heightSpinner.setValue(defaults.heightMm());
          spacingSpinner.setValue(defaults.spacingMm());
        });
    panel.add(reset, "span 2, align right"); // NON-NLS

    int result =
        JOptionPane.showConfirmDialog(
            GuiUtils.getUICore().getApplicationWindow(),
            panel,
            "Build Cross-Sectional Slices", // NON-NLS
            JOptionPane.OK_CANCEL_OPTION,
            JOptionPane.PLAIN_MESSAGE);
    if (result != JOptionPane.OK_OPTION) {
      return null;
    }
    return new CrossSectionParams(
        ((Number) widthSpinner.getValue()).doubleValue(),
        ((Number) heightSpinner.getValue()).doubleValue(),
        ((Number) spacingSpinner.getValue()).doubleValue());
  }

  /**
   * Build a {@link JSpinner} whose value is guaranteed to sit in {@code [min, max]}: the range is
   * widened to admit the requested value (so legitimate defaults pulled from the volume — e.g. a
   * 600 mm Z extent — never throw), and the step is forced positive.
   */
  private static JSpinner makeSpinner(double value, double min, double max, double step) {
    double safeMin = Math.max(0.0, min);
    double safeMax = Math.max(safeMin + 1.0, max);
    double safeStep = step > 0 ? step : 1.0;
    double safeValue = Math.clamp(safeMax, safeMin, value);
    if (value > safeMax) {
      safeMax = value;
      safeValue = value;
    }
    return new JSpinner(new SpinnerNumberModel(safeValue, safeMin, safeMax, safeStep));
  }

  /** Fall back to 1.0 if the volume reports a degenerate (0 or negative) pixel spacing. */
  private static double sanitizePixelMm(double pixelMm) {
    return pixelMm > 0 ? pixelMm : 1.0;
  }
}
