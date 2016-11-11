/*******************************************************************************
 * Copyright (c) 2016 Weasis Team and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Nicolas Roduit - initial API and implementation
 *******************************************************************************/
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

import org.weasis.acquire.explorer.core.bean.Serie;

@SuppressWarnings("serial")
public class SerieButtonList extends JScrollPane {

    private static final JPanel serieButtonPane = new JPanel();

    private SortedSet<SerieButton> serieButtonSet = new TreeSet<>();

    public SerieButtonList() {
        super(serieButtonPane, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
            ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        serieButtonPane.setLayout(new BoxLayout(serieButtonPane, BoxLayout.Y_AXIS));
    }

    public void addButton(SerieButton btn) {
        Dimension dim = btn.getPreferredSize();
        dim.width = 210;
        btn.setPreferredSize(dim);
        dim = btn.getMaximumSize();
        dim.width = 210;
        btn.setMaximumSize(dim);
        serieButtonSet.add(btn);
        int index = serieButtonSet.headSet(btn).size();
        serieButtonPane.add(btn, index);
    }

    public Optional<SerieButton> getButton(final Serie serie) {
        return serieButtonSet.stream().filter(sb -> sb.getSerie().equals(serie)).findAny();
    }

    public Set<SerieButton> getButtons() {
        return serieButtonSet;
    }

    private void remove(SerieButton btn) {
        if (serieButtonSet.remove(btn)) {
            serieButtonPane.remove(btn);
        }
    }

    public Optional<SerieButton> getFirstSerieButton() {
        return serieButtonSet.stream().sorted().findFirst();
    }

    public void removeBySerie(final Serie serie) {
        serieButtonSet.stream().filter(sb -> sb.getSerie().equals(serie)).findFirst().ifPresent(this::remove);
    }

    protected void refreshGUI() {
        revalidate();
        repaint();
    }
}
