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

import java.awt.Dimension;
import java.util.Optional;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import javax.swing.BoxLayout;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;
import org.weasis.acquire.explorer.core.bean.SeriesGroup;

public class SerieButtonList extends JScrollPane {

  private static final JPanel serieButtonPane = new JPanel();

  private final SortedSet<SeriesButton> seriesButtonSet = new TreeSet<>();

  public SerieButtonList() {
    super(
        serieButtonPane,
        ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
        ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
    serieButtonPane.setLayout(new BoxLayout(serieButtonPane, BoxLayout.Y_AXIS));
  }

  public void addButton(SeriesButton btn) {
    Dimension dim = btn.getPreferredSize();
    dim.width = 210;
    btn.setPreferredSize(dim);
    dim = btn.getMaximumSize();
    dim.width = 210;
    btn.setMaximumSize(dim);
    seriesButtonSet.add(btn);
    int index = seriesButtonSet.headSet(btn).size();
    serieButtonPane.add(btn, index);
  }

  public Optional<SeriesButton> getButton(final SeriesGroup seriesGroup) {
    return seriesButtonSet.stream().filter(sb -> sb.getSeries().equals(seriesGroup)).findAny();
  }

  public Set<SeriesButton> getButtons() {
    return seriesButtonSet;
  }

  private void remove(SeriesButton btn) {
    if (seriesButtonSet.remove(btn)) {
      serieButtonPane.remove(btn);
    }
  }

  public Optional<SeriesButton> getFirstSerieButton() {
    return seriesButtonSet.stream().sorted().findFirst();
  }

  public void removeBySerie(final SeriesGroup seriesGroup) {
    seriesButtonSet.stream()
        .filter(sb -> sb.getSeries().equals(seriesGroup))
        .findFirst()
        .ifPresent(this::remove);
  }

  protected void refreshGUI() {
    revalidate();
    repaint();
  }
}
