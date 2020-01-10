/*******************************************************************************
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.weasis.acquire.explorer.gui.central;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JToggleButton;

import org.weasis.acquire.explorer.core.bean.SeriesGroup;

public class SerieButton extends JToggleButton implements ActionListener, SeriesDataListener, Comparable<SerieButton> {
    private static final long serialVersionUID = -2587964095510462601L;

    private final SeriesGroup seriesGroup;
    private final AcquireTabPanel panel;

    public SerieButton(SeriesGroup seriesGroup, AcquireTabPanel panel) {
        super(seriesGroup.getDisplayName());
        this.seriesGroup = seriesGroup;
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

    public SeriesGroup getSerie() {
        return seriesGroup;
    }

    @Override
    public void setText(String text) {
        super.setText(text);
        setToolTipText(text);
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + seriesGroup.hashCode();
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        SerieButton other = (SerieButton) obj;
        return seriesGroup.equals(other.seriesGroup);
    }

    @Override
    public int compareTo(SerieButton o) {
        return getSerie().compareTo(o.getSerie());
    }

    @Override
    public void handleSeriesChanged() {
        setText(seriesGroup.getDisplayName());
    }

}
