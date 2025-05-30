/*
 * Copyright (c) 2024 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.tp.raven.spinner;

import javax.swing.Icon;
import javax.swing.JProgressBar;
import org.weasis.core.api.util.FontItem;

public class SpinnerProgress extends JProgressBar {

  private Icon icon;

  private int verticalAlignment = CENTER;
  private int horizontalAlignment = CENTER;

  private int verticalTextPosition = CENTER;
  private int horizontalTextPosition = TRAILING;

  private int iconTextGap = 4;
  private int space = 10;

  public SpinnerProgress() {
    init();
  }

  public SpinnerProgress(Icon icon) {
    this();
    this.icon = icon;
  }

  public SpinnerProgress(int min, int max) {
    super(min, max);
    init();
  }

  @Override
  public void updateUI() {
    setUI(new SpinnerProgressUI());
    setFont(FontItem.MICRO_SEMIBOLD.getFont());
  }

  private void init() {
    updateUI();
  }

  public Icon getIcon() {
    return icon;
  }

  public void setIcon(Icon icon) {
    this.icon = icon;
    repaint();
    revalidate();
  }

  public int getVerticalAlignment() {
    return verticalAlignment;
  }

  public void setVerticalAlignment(int alignment) {
    if (this.verticalAlignment != alignment) {
      this.verticalAlignment = alignment;
      revalidate();
    }
  }

  public int getHorizontalAlignment() {
    return horizontalAlignment;
  }

  public void setHorizontalAlignment(int alignment) {
    if (this.horizontalAlignment != alignment) {
      this.horizontalAlignment = alignment;
      revalidate();
    }
  }

  public int getVerticalTextPosition() {
    return verticalTextPosition;
  }

  public void setVerticalTextPosition(int textPosition) {
    if (this.verticalTextPosition != textPosition) {
      this.verticalTextPosition = textPosition;
      revalidate();
    }
  }

  public int getHorizontalTextPosition() {
    return horizontalTextPosition;
  }

  public void setHorizontalTextPosition(int textPosition) {
    if (this.horizontalTextPosition != textPosition) {
      this.horizontalTextPosition = textPosition;
      revalidate();
    }
  }

  public int getIconTextGap() {
    return iconTextGap;
  }

  public void setIconTextGap(int iconTextGap) {
    if (this.iconTextGap != iconTextGap) {
      this.iconTextGap = iconTextGap;
      revalidate();
    }
  }

  public int getSpace() {
    return space;
  }

  public void setSpace(int space) {
    if (this.space != space) {
      this.space = space;
      revalidate();
    }
  }
}
