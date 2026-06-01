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

import java.awt.Component;
import java.awt.GridBagConstraints;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JSlider;
import net.miginfocom.swing.MigLayout;
import org.weasis.core.api.image.util.Unit;
import org.weasis.core.api.util.ResourceUtil;
import org.weasis.core.api.util.ResourceUtil.OtherIcon;
import org.weasis.core.ui.editor.image.ImageViewerEventManager;
import org.weasis.core.ui.editor.image.ViewButton;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.codec.DicomSeries;
import org.weasis.dicom.viewer2d.View2d;

/**
 * 2D view displaying a curved-MPR panoramic image inside a regular {@code MprContainer}.
 *
 * <p>The view extends {@link View2d} so it inherits the standard WL/LUT/zoom/pan controls; only the
 * curve-specific settings popup (height + sampling step sliders) is added.
 */
public class CurvedMprView extends View2d {

  private CurvedMprAxis curvedMprAxis;

  public CurvedMprView(ImageViewerEventManager<DicomImageElement> eventManager) {
    super(eventManager);
    getViewButtons().clear();
    ViewButton settings =
        new ViewButton(
            this::showPanoramicSettingsPopup,
            ResourceUtil.getIcon(OtherIcon.VIEW_SETTING).derive(24, 24),
            "Panoramic settings"); // NON-NLS
    settings.setVisible(true);
    settings.setPosition(GridBagConstraints.NORTHEAST);
    getViewButtons().add(settings);
  }

  public CurvedMprAxis getCurvedMprAxis() {
    return curvedMprAxis;
  }

  /**
   * Bind a curved-MPR axis to this view and display its image. Creates a synthetic {@link
   * DicomSeries} wrapper because {@code InfoLayer} reads its data through the series. The previous
   * axis (if any) is disposed so its polyline listener and cached image are released.
   */
  public void setCurvedMprAxis(CurvedMprAxis axis) {
    if (this.curvedMprAxis != null && this.curvedMprAxis != axis) {
      this.curvedMprAxis.dispose();
    }
    this.curvedMprAxis = axis;
    if (axis == null) {
      return;
    }
    axis.setView(this);

    DicomImageElement img = axis.getImageElement();
    if (img != null) {
      DicomSeries series = new DicomSeries("curved-mpr-" + System.currentTimeMillis());
      series.addMedia(img);
      setSeries(series, img);
    }
  }

  @Override
  public void disposeView() {
    if (curvedMprAxis != null) {
      curvedMprAxis.dispose();
      curvedMprAxis = null;
    }
    super.disposeView();
  }

  /** Re-render the panoramic image after the axis parameters changed. */
  public void refreshCurvedImage() {
    if (curvedMprAxis == null) {
      return;
    }
    DicomImageElement img = curvedMprAxis.getImageElement();
    if (img == null) {
      return;
    }
    getImageLayer().setImage(null, null);
    img.removeImageFromCache();
    setImage(img);
    repaint();
  }

  /**
   * Show a popup with live sliders for the two panoramic generation parameters:
   *
   * <ul>
   *   <li><b>Height</b> — slab vertical extent ({@code widthMm} on the axis).
   *   <li><b>Step</b> — sampling distance along the curve ({@code stepMm} on the axis); changing
   *       this rescales the panoramic horizontally and the cross-section sample count.
   * </ul>
   *
   * Each slider regenerates the panoramic only when released (not on every tick) so dragging stays
   * responsive on large volumes. Ranges and scales are clamped so the current axis values always
   * sit inside the slider — works for tall volumes and for uncalibrated (pixel-only) data.
   */
  public void showPanoramicSettingsPopup(Component invoker, int x, int y) {
    if (curvedMprAxis == null) {
      return;
    }
    double pixelMm = sanitizePixelMm(curvedMprAxis.getVolume().getMinPixelRatio());
    String unitLabel = unitAbbreviation();

    JPanel panel =
        new JPanel(
            new MigLayout(
                "fillx, ins 6lp, wrap 1", "[grow, fill]", "[]2lp[]8lp[]2lp[]")); // NON-NLS

    addHeightSlider(panel, pixelMm, unitLabel);
    addStepSlider(panel, pixelMm, unitLabel);

    JPopupMenu popup = new JPopupMenu();
    popup.add(panel);
    popup.show(invoker, x, y);
  }

  /**
   * Append a header + slider that drives {@link CurvedMprAxis#setWidthMm}. The slider uses
   * voxel-aligned ticks so granularity matches the underlying sampling resolution.
   */
  private void addHeightSlider(JPanel panel, double pixelMm, String unitLabel) {
    double current = Math.max(1.0, curvedMprAxis.getWidthMm());
    double min = Math.min(5.0, current);
    double max = Math.max(200.0, current * 2);

    // 1 slider unit ≈ 1 voxel. Cap to avoid integer overflow on tiny pixelMm values.
    final int scale = (int) Math.clamp(Math.round(1.0 / pixelMm), 1, 1000);
    int sliderMin = (int) Math.round(min * scale);
    int sliderMax = Math.max(sliderMin + 1, (int) Math.round(max * scale));
    int sliderInit = Math.clamp((int) Math.round(current * scale), sliderMin, sliderMax);

    JSlider slider = new JSlider(sliderMin, sliderMax, sliderInit);
    slider.setMajorTickSpacing(Math.max(1, (sliderMax - sliderMin) / 4));
    slider.setPaintTicks(true);
    JLabel label = new JLabel("Height: %.1f %s".formatted(current, unitLabel)); // NON-NLS
    slider.addChangeListener(
        e -> {
          double mm = slider.getValue() / (double) scale;
          label.setText("Height: %.1f %s".formatted(mm, unitLabel)); // NON-NLS
          if (!slider.getValueIsAdjusting()) {
            curvedMprAxis.setWidthMm(mm);
          }
        });
    panel.add(label);
    panel.add(slider, "growx, w 260lp"); // NON-NLS
  }

  /**
   * Append a header + slider that drives {@link CurvedMprAxis#setStepMm}. The label includes the
   * resulting sample count (curve arc-length ÷ step) so the user understands the cost.
   */
  private void addStepSlider(JPanel panel, double pixelMm, String unitLabel) {
    double current = Math.max(pixelMm / 2.0, curvedMprAxis.getStepMm());
    double min = Math.min(pixelMm / 2.0, current);
    double max = Math.max(10.0, current * 2);

    // 10 slider units per mm gives ~0.1 mm granularity, fine enough for sample-step tuning.
    final int scale = 10;
    int sliderMin = Math.max(1, (int) Math.round(min * scale));
    int sliderMax = Math.max(sliderMin + 1, (int) Math.round(max * scale));
    int sliderInit = Math.clamp((int) Math.round(current * scale), sliderMin, sliderMax);

    JSlider slider = new JSlider(sliderMin, sliderMax, sliderInit);
    slider.setMajorTickSpacing(Math.max(1, (sliderMax - sliderMin) / 4));
    slider.setPaintTicks(true);
    JLabel label = new JLabel();
    Runnable updateLabel =
        () -> {
          double mm = slider.getValue() / (double) scale;
          double arcLength = curvedMprAxis.getTotalArcLengthMm();
          int samples = mm > 0 ? (int) Math.ceil(arcLength / mm) : 0;
          label.setText("Step: %.2f %s (%d samples)".formatted(mm, unitLabel, samples)); // NON-NLS
        };
    updateLabel.run();
    slider.addChangeListener(
        e -> {
          updateLabel.run();
          if (!slider.getValueIsAdjusting()) {
            curvedMprAxis.setStepMm(slider.getValue() / (double) scale);
          }
        });
    panel.add(label);
    panel.add(slider, "growx, w 260lp"); // NON-NLS
  }

  private String unitAbbreviation() {
    Unit unit = Unit.MILLIMETER;
    if (curvedMprAxis != null) {
      DicomImageElement img = curvedMprAxis.getImageElement();
      if (img != null) {
        Unit spacingUnit = img.getPixelSpacingUnit();
        if (spacingUnit != null) {
          unit = spacingUnit;
        }
      }
    }
    return unit.getAbbreviation();
  }

  /** Fall back to 1.0 if the volume reports a degenerate (0 or negative) pixel spacing. */
  private static double sanitizePixelMm(double pixelMm) {
    return pixelMm > 0 ? pixelMm : 1.0;
  }
}
