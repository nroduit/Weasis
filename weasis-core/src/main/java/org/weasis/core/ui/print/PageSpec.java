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
import java.awt.geom.Rectangle2D;
import java.awt.print.PageFormat;

/**
 * The geometry and look of a printed page in millimeters: the paper, its orientation, the margins
 * that bound the printable area, a header band at the top of that area, the gutter between cells,
 * the background and the border of the cells ({@code null} for none).
 */
public record PageSpec(
    Paper paper,
    Orientation orientation,
    Margins margins,
    double headerMm,
    double gutterMm,
    Color background,
    Color border) {

  public enum Orientation {
    PORTRAIT,
    LANDSCAPE
  }

  /** Margins in millimeters. */
  public record Margins(double top, double left, double bottom, double right) {
    public static Margins uniform(double mm) {
      return new Margins(mm, mm, mm, mm);
    }
  }

  public static final double DEFAULT_MARGIN_MM = 6.0;
  public static final double DEFAULT_GUTTER_MM = 2.0;

  /** Height of the header band: one line of normal text and its spacing. */
  public static final double HEADER_MM = 6.0;

  public PageSpec {
    if (gutterMm < 0 || headerMm < 0) {
      throw new IllegalArgumentException("Negative gutter or header");
    }
  }

  public PageSpec(
      Paper paper,
      Orientation orientation,
      Margins margins,
      double gutterMm,
      Color background,
      Color border) {
    this(paper, orientation, margins, 0, gutterMm, background, border);
  }

  public static PageSpec of(Paper paper, Orientation orientation, Color background) {
    return new PageSpec(
        paper,
        orientation,
        Margins.uniform(DEFAULT_MARGIN_MM),
        DEFAULT_GUTTER_MM,
        background,
        null);
  }

  /**
   * The page a Java print job describes: the paper from its size, the orientation it reports and
   * its imageable area as margins.
   */
  public static PageSpec of(PageFormat format, Color background, Color border) {
    double width = Paper.pointsToMm(format.getWidth());
    double height = Paper.pointsToMm(format.getHeight());
    Orientation orientation =
        format.getOrientation() == PageFormat.PORTRAIT
            ? Orientation.PORTRAIT
            : Orientation.LANDSCAPE;
    double left = Paper.pointsToMm(format.getImageableX());
    double top = Paper.pointsToMm(format.getImageableY());
    double right = width - left - Paper.pointsToMm(format.getImageableWidth());
    double bottom = height - top - Paper.pointsToMm(format.getImageableHeight());
    return new PageSpec(
        Paper.fromMm(width, height),
        orientation,
        new Margins(Math.max(0, top), Math.max(0, left), Math.max(0, bottom), Math.max(0, right)),
        DEFAULT_GUTTER_MM,
        background,
        border);
  }

  /** Width of the oriented page in millimeters. */
  public double widthMm() {
    return orientation == Orientation.PORTRAIT ? paper.widthMm() : paper.heightMm();
  }

  /** Height of the oriented page in millimeters. */
  public double heightMm() {
    return orientation == Orientation.PORTRAIT ? paper.heightMm() : paper.widthMm();
  }

  /**
   * The area the cells fill: inside the margins and under the header band, in page millimeters with
   * the origin at the top-left corner.
   */
  public Rectangle2D printableArea() {
    double w = Math.max(0, widthMm() - margins.left() - margins.right());
    double h = Math.max(0, heightMm() - margins.top() - margins.bottom() - headerMm);
    return new Rectangle2D.Double(margins.left(), margins.top() + headerMm, w, h);
  }

  /** The header band above the cells; empty when the page has none. */
  public Rectangle2D headerArea() {
    double w = Math.max(0, widthMm() - margins.left() - margins.right());
    return new Rectangle2D.Double(margins.left(), margins.top(), w, headerMm);
  }

  public boolean hasHeader() {
    return headerMm > 0;
  }

  public PageSpec withBackground(Color color) {
    return new PageSpec(paper, orientation, margins, headerMm, gutterMm, color, border);
  }

  /** The same page with, or without, the header band. */
  public PageSpec withHeader(boolean header) {
    return new PageSpec(
        paper, orientation, margins, header ? HEADER_MM : 0, gutterMm, background, border);
  }
}
