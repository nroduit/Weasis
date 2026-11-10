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
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;
import org.weasis.core.Messages;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.media.data.TagView;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.api.util.FontItem;
import org.weasis.core.ui.editor.image.ExportImage;
import org.weasis.core.ui.util.ExportLayout;
import org.weasis.core.util.StringUtil;

/**
 * The header line of a page: patient name and id, study date and description of the first image,
 * masked by the active profile, and the page number, drawn in the header band outside the cells.
 */
public final class PageHeader {

  private static final String[] KEYWORDS = {
    "PatientName", "PatientID", "StudyDate", "StudyDescription" // NON-NLS
  };
  private static final String SEPARATOR = "   ·   ";
  private static final String ELLIPSIS = "…";
  private static final double SPACING_MM = 0.5;

  private PageHeader() {}

  /** The text of the left part, empty when the layout holds no image or nothing is known. */
  public static String describe(ExportLayout<? extends ImageElement> layout) {
    for (ExportImage<?> view : PageSource.views(layout)) {
      ImageElement image = view.getImage();
      if (image == null) {
        continue;
      }
      MediaSeries<?> series = view.getSeries();
      List<String> parts = new ArrayList<>();
      for (String keyword : KEYWORDS) {
        TagW tag = TagW.get(keyword);
        if (tag == null) {
          continue;
        }
        TagView tagView = new TagView(tag);
        String text =
            series == null
                ? tagView.getFormattedText(image)
                : tagView.getFormattedText(image, series);
        if (StringUtil.hasText(text)) {
          parts.add(text);
        }
      }
      return String.join(SEPARATOR, parts);
    }
    return StringUtil.EMPTY_STRING;
  }

  /** Draws the header in the band of {@code spec}; {@code g2d} is in device pixels. */
  public static void draw(
      Graphics2D g2d,
      ExportLayout<? extends ImageElement> layout,
      PageSpec spec,
      double pixelsPerMm,
      int pageIndex,
      int pageCount) {
    Rectangle2D band = spec.headerArea();
    if (band.isEmpty()) {
      return;
    }
    Graphics2D g = (Graphics2D) g2d.create();
    try {
      float fontPx = (float) (TextSize.NORMAL.heightMm() * pixelsPerMm);
      g.setFont(FontItem.DEFAULT_SEMIBOLD.getFont().deriveFont(fontPx));
      g.setRenderingHint(
          RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
      g.setColor(isDark(spec.background()) ? Color.WHITE : Color.BLACK);
      FontMetrics fm = g.getFontMetrics();
      double left = band.getX() * pixelsPerMm;
      double right = (band.getX() + band.getWidth()) * pixelsPerMm;
      double baseline =
          (band.getY() + band.getHeight() - SPACING_MM) * pixelsPerMm - fm.getDescent();
      String pages = Messages.format("PrintDialog.page_of", pageIndex + 1, pageCount);
      double pagesX = right - fm.stringWidth(pages);
      g.drawString(pages, (float) pagesX, (float) baseline);
      String text = fit(describe(layout), fm, pagesX - left - fm.stringWidth(SEPARATOR));
      if (!text.isEmpty()) {
        g.drawString(text, (float) left, (float) baseline);
      }
    } finally {
      g.dispose();
    }
  }

  private static String fit(String text, FontMetrics fm, double width) {
    if (width <= 0) {
      return StringUtil.EMPTY_STRING;
    }
    String s = text;
    while (!s.isEmpty() && fm.stringWidth(s) > width) {
      s = s.substring(0, s.length() - 1).stripTrailing();
      if (fm.stringWidth(s + ELLIPSIS) <= width) {
        return s + ELLIPSIS;
      }
    }
    return s;
  }

  private static boolean isDark(Color color) {
    return color != null
        && (0.299 * color.getRed() + 0.587 * color.getGreen() + 0.114 * color.getBlue()) < 128;
  }
}
