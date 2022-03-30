/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.acquire.dockable.components.util;

import java.util.StringJoiner;
import javax.swing.JSlider;
import javax.swing.border.TitledBorder;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.util.StringUtil;

public abstract class AbstractSliderComponent extends JSlider {

  protected final String title;
  protected TitledBorder borderTitle;

  protected AbstractSliderComponent(String title, int min, int max, int value) {
    super(min, max, value);
    this.title = title;
    this.borderTitle = GuiUtils.getTitledBorder(getDisplayTitle());
    setMajorTickSpacing(max);
    setPaintTicks(true);
    setPaintLabels(true);
    setBorder(borderTitle);
  }

  public String getDisplayTitle() {
    return new StringJoiner(StringUtil.COLON_AND_SPACE)
        .add(title)
        .add(Integer.toString(getSliderValue()))
        .toString();
  }

  public int getSliderValue() {
    return getModel().getValue();
  }

  public void updatePanelTitle() {
    borderTitle.setTitle(getDisplayTitle());
    repaint();
  }
}
