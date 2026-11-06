/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.util;

import java.awt.Color;
import org.weasis.core.ui.print.TextSize;

/**
 * @author Marcelo Porto (marcelo@animati.com.br)
 * @author Nicolas Roduit
 * @since 18/11/2011
 */
public class PrintOptions {
  private boolean showingAnnotations;
  private boolean center;
  private boolean colorPrint;
  private TextSize textSize;
  private Color background;
  private Color border;
  private boolean header;

  public PrintOptions() {
    this.showingAnnotations = true;
    this.center = true;
    this.colorPrint = true;
    this.background = Color.WHITE;
  }

  public boolean isShowingAnnotations() {
    return showingAnnotations;
  }

  public void setShowingAnnotations(boolean showingAnnotations) {
    this.showingAnnotations = showingAnnotations;
  }

  public boolean isCenter() {
    return center;
  }

  public void setCenter(boolean center) {
    this.center = center;
  }

  public boolean isColorPrint() {
    return colorPrint;
  }

  public void setColorPrint(boolean colorPrint) {
    this.colorPrint = colorPrint;
  }

  /** The text size on paper, or {@code null} for the default of each cell width. */
  public TextSize getTextSize() {
    return textSize;
  }

  public void setTextSize(TextSize textSize) {
    this.textSize = textSize;
  }

  /** The page background; white by default for paper, black for film. */
  public Color getBackground() {
    return background;
  }

  public void setBackground(Color background) {
    this.background = background == null ? Color.WHITE : background;
  }

  /** The border drawn around each cell, or {@code null} for none. */
  public Color getBorder() {
    return border;
  }

  public void setBorder(Color border) {
    this.border = border;
  }

  /** Whether a header line (patient, study, page number) is printed above the cells. */
  public boolean isHeader() {
    return header;
  }

  public void setHeader(boolean header) {
    this.header = header;
  }
}
