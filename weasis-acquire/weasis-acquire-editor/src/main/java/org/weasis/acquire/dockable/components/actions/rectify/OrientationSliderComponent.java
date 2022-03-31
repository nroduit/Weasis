/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.acquire.dockable.components.actions.rectify;

import java.util.Hashtable;
import javax.swing.JLabel;
import org.weasis.acquire.Messages;
import org.weasis.acquire.dockable.components.util.AbstractSliderComponent;
import org.weasis.acquire.operations.impl.RectifyOrientationChangeListener;
import org.weasis.core.api.gui.util.SliderChangeListener;
import org.weasis.core.api.util.FontItem;

public class OrientationSliderComponent extends AbstractSliderComponent {

  private static final int RECTIFY_ORIENTATION_MIN = -45;
  private static final int RECTIFY_ORIENTATION_MAX = 45;
  private static final int RECTIFY_ORIENTATION_DEFAULT = 0;

  private static final Hashtable<Integer, JLabel> labels = new Hashtable<>();

  static {
    int div = 7;
    int space = (RECTIFY_ORIENTATION_MAX - RECTIFY_ORIENTATION_MIN) / (div - 1);

    for (int i = 0; i < div; i++) {
      Integer index = i * space + RECTIFY_ORIENTATION_MIN;
      labels.put(index, new JLabel(index.toString()));
    }
  }

  private final RectifyOrientationChangeListener listener;

  public OrientationSliderComponent(RectifyPanel panel) {
    super(
        Messages.getString("OrientationSliderComponent.orientation"),
        RECTIFY_ORIENTATION_MIN,
        RECTIFY_ORIENTATION_MAX,
        RECTIFY_ORIENTATION_DEFAULT);
    setLabelTable(labels);
    SliderChangeListener.setFont(this, FontItem.MINI.getFont());
    listener = new RectifyOrientationChangeListener(panel.getRectifyAction());
    addChangeListener(listener);
  }

  public RectifyOrientationChangeListener getListener() {
    return listener;
  }

  @Override
  public String getDisplayTitle() {
    return super.getDisplayTitle() + " °";
  }
}
