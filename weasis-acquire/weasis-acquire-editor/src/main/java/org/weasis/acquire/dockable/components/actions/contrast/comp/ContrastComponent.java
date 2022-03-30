/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.acquire.dockable.components.actions.contrast.comp;

import java.util.Hashtable;
import java.util.StringJoiner;
import javax.swing.JLabel;
import org.weasis.acquire.Messages;
import org.weasis.acquire.dockable.components.actions.contrast.ContrastPanel;
import org.weasis.acquire.dockable.components.util.AbstractSliderComponent;
import org.weasis.core.api.gui.util.SliderChangeListener;
import org.weasis.core.api.util.FontItem;
import org.weasis.core.util.StringUtil;

public class ContrastComponent extends AbstractSliderComponent {

  public static final int CONTRAST_VALUE = 100;
  public static final int CONTRAST_MIN = 1;
  public static final int CONTRAST_MAX = 200;

  private static final Hashtable<Integer, JLabel> labels = new Hashtable<>();

  static {
    labels.put(CONTRAST_MIN, new JLabel("0.01"));
    labels.put(CONTRAST_VALUE, new JLabel("1"));
    labels.put(CONTRAST_MAX, new JLabel("2"));
  }

  public ContrastComponent(ContrastPanel panel) {
    super(
        Messages.getString("ContrastComponent.contrast"),
        CONTRAST_MIN,
        CONTRAST_MAX,
        CONTRAST_VALUE);
    setLabelTable(labels);
    SliderChangeListener.setFont(this, FontItem.MINI.getFont());
    addChangeListener(panel);
  }

  @Override
  public String getDisplayTitle() {
    return new StringJoiner(StringUtil.COLON)
        .add(title)
        .add(Float.toString(getSliderValue() / 100f))
        .toString();
  }
}
