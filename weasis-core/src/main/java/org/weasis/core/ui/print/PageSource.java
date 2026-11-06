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

import java.awt.Component;
import java.util.List;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.api.media.data.PixelReviewAdvisor;
import org.weasis.core.ui.editor.image.ExportImage;
import org.weasis.core.ui.util.ExportLayout;

/**
 * The pages of a print job: one layout, or the frames of a series spread over several pages. A page
 * is an {@link ExportLayout} the renderer draws; a source may build it on demand and keep only the
 * page last asked for.
 */
public interface PageSource {

  int pageCount();

  /** The layout of page {@code index}, counting from 0. */
  ExportLayout<? extends ImageElement> page(int index);

  /** Whether an image of the pages may carry identity burned into its pixels that nothing hides. */
  default boolean requiresPixelReview() {
    return requiresPixelReview(page(0));
  }

  void dispose();

  /** A source of one fixed page. */
  static PageSource of(ExportLayout<? extends ImageElement> layout) {
    return new PageSource() {
      @Override
      public int pageCount() {
        return 1;
      }

      @Override
      public ExportLayout<? extends ImageElement> page(int index) {
        if (index != 0) {
          throw new IndexOutOfBoundsException("Page " + index + " of 1");
        }
        return layout;
      }

      @Override
      public void dispose() {
        layout.dispose();
      }
    };
  }

  /** The export views of a layout, tiles included. */
  static List<ExportImage<?>> views(ExportLayout<? extends ImageElement> layout) {
    List<ExportImage<?>> views = new java.util.ArrayList<>();
    for (var cell : layout.getLayoutModel().getCells()) {
      Component component = layout.findComponentForCell(cell);
      if (component instanceof ExportImage<?> view) {
        views.add(view);
      } else if (component instanceof ExportLayout.ExportTiles<?> tiles) {
        views.addAll(tiles.getTiles());
      }
    }
    return views;
  }

  static boolean requiresPixelReview(ExportLayout<? extends ImageElement> layout) {
    return views(layout).stream()
        .anyMatch(v -> PixelReviewAdvisor.review(v.getImage(), v.getSeries()));
  }
}
