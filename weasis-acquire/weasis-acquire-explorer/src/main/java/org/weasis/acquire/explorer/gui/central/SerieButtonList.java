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

import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.util.Optional;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.acquire.explorer.core.bean.Serie;
import org.weasis.acquire.explorer.gui.control.AcquirePublishPanel;
import org.weasis.core.api.gui.util.JMVUtils;

public class SerieButtonList extends JScrollPane {
    private static final long serialVersionUID = 3875335843304715915L;
    protected static final Logger LOGGER = LoggerFactory.getLogger(SerieButtonList.class);

    private static final JPanel acquireSerieButtonPanel = new JPanel(new BorderLayout());
    private static final JPanel serieButtonPane = new JPanel(new GridLayout(0, 1));

    private SortedSet<SerieButton> serieButtonSet = new TreeSet<>();
    private JPanel publishActionPane = new AcquirePublishPanel();

    public SerieButtonList() {
        super(acquireSerieButtonPanel, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
            ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        JMVUtils.setPreferredWidth(acquireSerieButtonPanel, 200);
        JMVUtils.setPreferredHeight(acquireSerieButtonPanel, 300);

        acquireSerieButtonPanel.add(serieButtonPane, BorderLayout.NORTH);
        acquireSerieButtonPanel.add(publishActionPane, BorderLayout.SOUTH);
    }

    public void addButton(SerieButton btn) {
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
        serieButtonSet.stream().filter(sb -> sb.getSerie().equals(serie)).findFirst().ifPresent(sb -> remove(sb));
    }

    protected void refreshGUI() {
        revalidate();
        repaint();
    }
}
