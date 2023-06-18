/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.acquire.explorer.gui.central;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Objects;
import javax.swing.JToggleButton;
import org.weasis.acquire.explorer.core.bean.SeriesGroup;

public class SeriesButton extends JToggleButton
    implements ActionListener, SeriesDataListener, Comparable<SeriesButton> {

  private final SeriesGroup seriesGroup;
  private final AcquireTabPanel panel;

  public SeriesButton(SeriesGroup seriesGroup, AcquireTabPanel panel) {
    super(seriesGroup.getDisplayName());
    this.seriesGroup = Objects.requireNonNull(seriesGroup);
    seriesGroup.addLayerChangeListener(this);
    this.panel = panel;
    addActionListener(this);
  }

  @Override
  public void actionPerformed(ActionEvent e) {
    if (e.getSource() == this) {
      panel.setSelected(this);
    }
  }

  public SeriesGroup getSeries() {
    return seriesGroup;
  }

  @Override
  public void setText(String text) {
    super.setText(text);
    setToolTipText(text);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    SeriesButton that = (SeriesButton) o;
    return seriesGroup.equals(that.seriesGroup);
  }

  @Override
  public int hashCode() {
    return Objects.hash(seriesGroup);
  }

  @Override
  public int compareTo(SeriesButton o) {
    return getSeries().compareTo(o.getSeries());
  }

  @Override
  public void handleSeriesChanged() {
    setText(seriesGroup.getDisplayName());
  }
}
