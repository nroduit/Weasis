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

import java.awt.Graphics2D;
import java.awt.print.PrinterGraphics;
import java.util.OptionalInt;
import javax.print.PrintService;
import javax.print.attribute.PrintRequestAttributeSet;
import javax.print.attribute.ResolutionSyntax;
import javax.print.attribute.standard.PrinterResolution;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.service.UICore;

/**
 * The resolution a page is rendered at: the one of the destination, derived and bounded, never
 * asked. The system printer gives its device resolution, capped by {@code weasis.print.max.dpi}; a
 * cell whose raster would exceed the pixel budget is rendered smaller and scaled up by the
 * platform, which only happens on very large sheets at high resolutions.
 */
public final class PrintResolution {

  public static final String MAX_DPI_PROPERTY = "weasis.print.max.dpi"; // NON-NLS
  public static final int DEFAULT_DPI = 300;
  public static final int DEFAULT_MAX_DPI = 600;
  public static final int MIN_DPI = 72;

  /** Pixels of one cell raster: 32 Mpx is about 96 MB in color, twice that while converting. */
  public static final long MAX_CELL_PIXELS = 32_000_000L;

  private PrintResolution() {}

  /** The resolution of the print job, from the service, else the graphics, else the default. */
  public static int resolve(PrintService service, Graphics2D graphics, int maxDpi) {
    return resolve(null, service, graphics, maxDpi);
  }

  /**
   * The resolution of the print job: the one its attributes request, else the one the service
   * reports, else the one behind the graphics, else the default; capped at {@code maxDpi}.
   */
  public static int resolve(
      PrintRequestAttributeSet attributes, PrintService service, Graphics2D graphics, int maxDpi) {
    OptionalInt requested = fromAttributes(attributes);
    int dpi =
        requested.isPresent()
            ? requested.getAsInt()
            : fromService(service).orElseGet(() -> fromGraphics(graphics).orElse(DEFAULT_DPI));
    return Math.clamp(dpi, MIN_DPI, Math.max(MIN_DPI, maxDpi));
  }

  /** The resolution a job's attributes request, typically set in the native print dialog. */
  public static OptionalInt fromAttributes(PrintRequestAttributeSet attributes) {
    if (attributes != null
        && attributes.get(PrinterResolution.class) instanceof PrinterResolution resolution) {
      int dpi = resolution.getCrossFeedResolution(ResolutionSyntax.DPI);
      return dpi >= MIN_DPI ? OptionalInt.of(dpi) : OptionalInt.empty();
    }
    return OptionalInt.empty();
  }

  /** The default resolution the print service reports, when it reports one. */
  public static OptionalInt fromService(PrintService service) {
    if (service == null) {
      return OptionalInt.empty();
    }
    try {
      if (service.getDefaultAttributeValue(PrinterResolution.class)
          instanceof PrinterResolution resolution) {
        int dpi = resolution.getCrossFeedResolution(ResolutionSyntax.DPI);
        return dpi >= MIN_DPI ? OptionalInt.of(dpi) : OptionalInt.empty();
      }
    } catch (RuntimeException e) {
      // Some services throw on attribute queries: fall back to the graphics
    }
    return OptionalInt.empty();
  }

  /**
   * The device resolution behind a printer graphics, whose user space is in points: the default
   * transform scales points to device pixels. Not meaningful for a screen or an image.
   */
  public static OptionalInt fromGraphics(Graphics2D graphics) {
    if (!(graphics instanceof PrinterGraphics) || graphics.getDeviceConfiguration() == null) {
      return OptionalInt.empty();
    }
    double scale = graphics.getDeviceConfiguration().getDefaultTransform().getScaleX();
    int dpi = (int) Math.round(scale * Paper.POINTS_PER_INCH);
    return dpi >= MIN_DPI ? OptionalInt.of(dpi) : OptionalInt.empty();
  }

  /** The cap on the printer resolution, from the system preferences. */
  public static int maxDpi() {
    UICore core = GuiUtils.getUICore();
    if (core == null || core.getSystemPreferences() == null) {
      return DEFAULT_MAX_DPI;
    }
    return Math.max(
        MIN_DPI, core.getSystemPreferences().getIntProperty(MAX_DPI_PROPERTY, DEFAULT_MAX_DPI));
  }

  /** Pixels per millimeter at this resolution. */
  public static double pixelsPerMm(int dpi) {
    return dpi / Paper.MM_PER_INCH;
  }

  /**
   * The factor, at most 1, that keeps a raster of {@code width} by {@code height} pixels within
   * {@code maxPixels}.
   */
  public static double budgetScale(double width, double height, long maxPixels) {
    double pixels = width * height;
    if (pixels <= maxPixels || pixels <= 0) {
      return 1.0;
    }
    return Math.sqrt(maxPixels / pixels);
  }
}
