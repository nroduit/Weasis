/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.wave;

import de.erichseifert.vectorgraphics2d.VectorGraphics2D;
import de.erichseifert.vectorgraphics2d.intermediate.CommandSequence;
import de.erichseifert.vectorgraphics2d.pdf.PDFProcessor;
import de.erichseifert.vectorgraphics2d.util.PageSize;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Locale;
import javax.imageio.ImageIO;

/**
 * Writes the ECG page (patient header and leads as displayed) to a PNG or JPEG image at the screen
 * size, or to a vector PDF at the paper scale: one screen millimetre is one millimetre of the PDF
 * page, so the grid, 25 mm/s and 10 mm/mV print true.
 */
public final class WaveExport {

  public static final String PNG = "png"; // NON-NLS
  public static final String JPEG = "jpg"; // NON-NLS
  public static final String PDF = "pdf"; // NON-NLS

  private WaveExport() {}

  /** Writes the page in the format given by the extension of the file (png, jpg or pdf). */
  public static void write(WaveView view, File file) throws IOException {
    String name = file.getName().toLowerCase(Locale.ROOT);
    if (name.endsWith("." + PDF)) {
      writePdf(view, file);
    } else {
      writeImage(view, file, name.endsWith("." + JPEG) || name.endsWith(".jpeg") ? JPEG : PNG);
    }
  }

  static void writeImage(WaveView view, File file, String format) throws IOException {
    int width = Math.max(1, view.getPageWidth());
    int height = Math.max(1, view.getPageHeight());
    BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
    Graphics2D g2 = image.createGraphics();
    try {
      g2.setColor(Color.WHITE);
      g2.fillRect(0, 0, width, height);
      view.paintPage(g2);
    } finally {
      g2.dispose();
    }
    if (!ImageIO.write(image, format, file)) {
      throw new IOException("No writer for " + format);
    }
  }

  static void writePdf(WaveView view, File file) throws IOException {
    // Drawing units are points; the page size of VectorGraphics2D is in millimetres
    double pointsPerPixel = 72.0 / 25.4 / view.getPixelPerMm();
    double width = view.getPageWidth() * pointsPerPixel;
    double height = view.getPageHeight() * pointsPerPixel;
    double widthMm = view.getPageWidth() / view.getPixelPerMm();
    double heightMm = view.getPageHeight() / view.getPixelPerMm();
    VectorGraphics2D g2 = new VectorGraphics2D();
    g2.setColor(Color.WHITE);
    g2.fillRect(0, 0, (int) Math.ceil(width) + 1, (int) Math.ceil(height) + 1);
    g2.scale(pointsPerPixel, pointsPerPixel);
    view.paintPage(g2);
    CommandSequence commands = g2.getCommands();
    PDFProcessor processor = new PDFProcessor(true);
    try (OutputStream out = new FileOutputStream(file)) {
      processor.getDocument(commands, new PageSize(0, 0, widthMm, heightMm)).writeTo(out);
    }
  }
}
