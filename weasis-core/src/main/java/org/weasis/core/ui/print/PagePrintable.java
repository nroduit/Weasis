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

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.geom.Rectangle2D;
import java.awt.print.PageFormat;
import java.awt.print.Printable;
import java.awt.print.PrinterException;
import java.awt.print.PrinterJob;
import javax.print.PrintService;
import javax.print.attribute.HashPrintRequestAttributeSet;
import javax.print.attribute.PrintRequestAttributeSet;
import javax.swing.JOptionPane;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.Messages;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.ui.util.ForcedAcceptPrintService;
import org.weasis.core.ui.util.PrintOptions;

/**
 * The system printer sink: the pages of a source, each cell rendered at the resolution of the
 * printer and handed to it pixel for pixel, so that the platform never resamples the image.
 */
public class PagePrintable implements Printable {
  private static final Logger LOGGER = LoggerFactory.getLogger(PagePrintable.class);

  private final PageSource pages;
  private final PrintOptions options;
  private PrintService service;
  private PrintRequestAttributeSet attributes = new HashPrintRequestAttributeSet();

  public PagePrintable(PageSource pages, PrintOptions options) {
    this.pages = pages;
    this.options = options == null ? new PrintOptions() : options;
  }

  /**
   * Prints the pages on {@code service} with these job attributes (media, orientation, copies and
   * whatever the native dialog returned).
   */
  public void print(PrintService service, PrintRequestAttributeSet attributes)
      throws PrinterException {
    this.service = service;
    this.attributes = attributes == null ? new HashPrintRequestAttributeSet() : attributes;
    PrinterJob job = PrinterJob.getPrinterJob();
    if (service != null) {
      job.setPrintService(service);
    }
    job.setPrintable(this);
    print(job, this.attributes);
  }

  /**
   * Runs {@code job} with these attributes. A printer that refuses jobs is retried once with the
   * user's consent.
   */
  public static void print(PrinterJob job, PrintRequestAttributeSet attributes)
      throws PrinterException {
    try {
      job.print(attributes);
    } catch (PrinterException e) {
      if (e.getMessage() == null || !e.getMessage().contains("accepting job")) { // NON-NLS
        throw e;
      }
      int response =
          JOptionPane.showConfirmDialog(
              GuiUtils.getUICore().getApplicationWindow(),
              Messages.getString("ImagePrint.issue_desc"),
              Messages.getString("ImagePrint.status"),
              JOptionPane.YES_NO_OPTION,
              JOptionPane.WARNING_MESSAGE);
      if (response == JOptionPane.YES_OPTION) {
        // Print again, ignoring the not-accepting-jobs attribute of the service
        ForcedAcceptPrintService.setupPrintJob(job);
        job.print(attributes);
        LOGGER.info("Bypass Printer is not accepting job");
      }
    }
  }

  @Override
  public int print(Graphics g, PageFormat f, int pageIndex) {
    if (pages == null || pageIndex >= pages.pageCount()) {
      return Printable.NO_SUCH_PAGE;
    }
    printPage((Graphics2D) g, f, pageIndex);
    return Printable.PAGE_EXISTS;
  }

  /**
   * Draws one page into a printer graphics whose unit is the point: the graphics is scaled to the
   * device pixel, so that every cell raster lands pixel for pixel.
   */
  public void printPage(Graphics2D g2d, PageFormat format, int pageIndex) {
    int dpi = PrintResolution.resolve(attributes, service, g2d, PrintResolution.maxDpi());
    double pixelsPerMm = PrintResolution.pixelsPerMm(dpi);
    PageSpec spec =
        PageSpec.of(format, options.getBackground(), options.getBorder())
            .withHeader(options.isHeader());
    LOGGER.debug(
        "Print page {}/{} {} {} at {} dpi",
        pageIndex + 1,
        pages.pageCount(),
        spec.paper(),
        spec.orientation(),
        dpi);

    Graphics2D g = (Graphics2D) g2d.create();
    try {
      g.scale(Paper.POINTS_PER_INCH / dpi, Paper.POINTS_PER_INCH / dpi);
      Color background = spec.background();
      if (background != null && !Color.WHITE.equals(background)) {
        Rectangle2D area = spec.printableArea().createUnion(spec.headerArea());
        g.setColor(background);
        g.fill(
            new Rectangle2D.Double(
                area.getX() * pixelsPerMm,
                area.getY() * pixelsPerMm,
                area.getWidth() * pixelsPerMm,
                area.getHeight() * pixelsPerMm));
      }
      PageRenderer.drawPage(
          g, pages.page(pageIndex), spec, pixelsPerMm, options, pageIndex, pages.pageCount());
    } finally {
      g.dispose();
    }
  }
}
