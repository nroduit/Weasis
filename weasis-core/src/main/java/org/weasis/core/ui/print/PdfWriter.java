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

import java.awt.image.BufferedImage;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.zip.DeflaterOutputStream;

/**
 * Writes a PDF whose pages each hold one raster, Flate-compressed and lossless, placed to fill the
 * page: enough for a print preview or a report without a dependency. Pages are written as they
 * come; the page tree and the cross-reference table follow at {@link #close()}.
 */
public final class PdfWriter implements Closeable {

  private static final int CATALOG = 1;
  private static final int PAGES = 2;

  private final OutputStream out;
  private final List<Long> offsets = new ArrayList<>();
  private final List<Integer> pages = new ArrayList<>();
  private long written;
  private int nextObject = PAGES + 1;
  private boolean closed;

  public PdfWriter(Path file) throws IOException {
    this(new BufferedOutputStream(Files.newOutputStream(file)));
  }

  public PdfWriter(OutputStream stream) throws IOException {
    this.out = stream;
    offsets.add(0L);
    offsets.add(0L);
    offsets.add(0L);
    write("%PDF-1.4\n%âãÏÓ\n");
  }

  /**
   * Adds a page of {@code widthPt} by {@code heightPt} points filled by the raster. A color raster
   * whose pixels are all neutral is stored as gray, a third of the size.
   */
  public void addPage(BufferedImage image, double widthPt, double heightPt) throws IOException {
    boolean gray = image.getRaster().getNumBands() == 1;
    byte[] samples = pixels(image, gray);
    if (!gray && isNeutral(samples)) {
      samples = toGray(samples);
      gray = true;
    }
    byte[] data = deflate(samples);
    int imageObject = nextObject++;
    int contentObject = nextObject++;
    int pageObject = nextObject++;
    writeObject(
        imageObject,
        String.format(
            Locale.ROOT,
            "<< /Type /XObject /Subtype /Image /Width %d /Height %d /ColorSpace %s"
                + " /BitsPerComponent 8 /Filter /FlateDecode /Length %d >>",
            image.getWidth(),
            image.getHeight(),
            gray ? "/DeviceGray" : "/DeviceRGB",
            data.length),
        data);
    byte[] content =
        String.format(
                Locale.ROOT, "q %.4f 0 0 %.4f 0 0 cm /Im%d Do Q", widthPt, heightPt, imageObject)
            .getBytes(StandardCharsets.US_ASCII);
    writeObject(
        contentObject, String.format(Locale.ROOT, "<< /Length %d >>", content.length), content);
    writeObject(
        pageObject,
        String.format(
            Locale.ROOT,
            "<< /Type /Page /Parent %d 0 R /MediaBox [0 0 %.4f %.4f]"
                + " /Resources << /XObject << /Im%d %d 0 R >> >> /Contents %d 0 R >>",
            PAGES,
            widthPt,
            heightPt,
            imageObject,
            imageObject,
            contentObject),
        null);
    pages.add(pageObject);
  }

  public int pageCount() {
    return pages.size();
  }

  @Override
  public void close() throws IOException {
    if (closed) {
      return;
    }
    closed = true;
    try (out) {
      StringBuilder kids = new StringBuilder();
      for (int page : pages) {
        kids.append(page).append(" 0 R ");
      }
      writeObject(
          PAGES,
          "<< /Type /Pages /Kids [" + kids.toString().trim() + "] /Count " + pages.size() + " >>",
          null);
      writeObject(CATALOG, "<< /Type /Catalog /Pages " + PAGES + " 0 R >>", null);
      long xref = written;
      write("xref\n0 " + nextObject + "\n0000000000 65535 f \n");
      for (int i = 1; i < nextObject; i++) {
        write(String.format(Locale.ROOT, "%010d 00000 n \n", offsets.get(i)));
      }
      write(
          "trailer\n<< /Size "
              + nextObject
              + " /Root "
              + CATALOG
              + " 0 R >>\nstartxref\n"
              + xref
              + "\n%%EOF\n");
    }
  }

  private void writeObject(int number, String dictionary, byte[] stream) throws IOException {
    while (offsets.size() <= number) {
      offsets.add(0L);
    }
    offsets.set(number, written);
    write(number + " 0 obj\n" + dictionary + "\n");
    if (stream != null) {
      write("stream\n");
      out.write(stream);
      written += stream.length;
      write("\nendstream\n");
    }
    write("endobj\n");
  }

  private void write(String text) throws IOException {
    byte[] bytes = text.getBytes(StandardCharsets.ISO_8859_1);
    out.write(bytes);
    written += bytes.length;
  }

  /** The samples of the raster, row by row, one byte per sample. */
  static byte[] pixels(BufferedImage image, boolean gray) {
    int w = image.getWidth();
    int h = image.getHeight();
    if (gray) {
      byte[] bytes = new byte[w * h];
      int[] row = new int[w];
      for (int y = 0; y < h; y++) {
        image.getRaster().getSamples(0, y, w, 1, 0, row);
        for (int x = 0; x < w; x++) {
          bytes[y * w + x] = (byte) row[x];
        }
      }
      return bytes;
    }
    byte[] bytes = new byte[w * h * 3];
    int[] row = new int[w];
    for (int y = 0; y < h; y++) {
      image.getRGB(0, y, w, 1, row, 0, w);
      int i = y * w * 3;
      for (int x = 0; x < w; x++) {
        int rgb = row[x];
        bytes[i++] = (byte) (rgb >> 16);
        bytes[i++] = (byte) (rgb >> 8);
        bytes[i++] = (byte) rgb;
      }
    }
    return bytes;
  }

  /** Whether every RGB triplet has equal components. */
  static boolean isNeutral(byte[] rgb) {
    for (int i = 0; i + 2 < rgb.length; i += 3) {
      if (rgb[i] != rgb[i + 1] || rgb[i] != rgb[i + 2]) {
        return false;
      }
    }
    return true;
  }

  private static byte[] toGray(byte[] rgb) {
    byte[] gray = new byte[rgb.length / 3];
    for (int i = 0; i < gray.length; i++) {
      gray[i] = rgb[i * 3];
    }
    return gray;
  }

  private static byte[] deflate(byte[] data) throws IOException {
    ByteArrayOutputStream buffer = new ByteArrayOutputStream(data.length / 4 + 64);
    try (DeflaterOutputStream deflater = new DeflaterOutputStream(buffer)) {
      deflater.write(data);
    }
    return buffer.toByteArray();
  }
}
