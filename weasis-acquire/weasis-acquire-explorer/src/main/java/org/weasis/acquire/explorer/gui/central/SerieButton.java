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

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JToggleButton;

import org.weasis.acquire.explorer.core.bean.SeriesGroup;

public class SerieButton extends JToggleButton implements ActionListener, Comparable<SerieButton> {
    private static final long serialVersionUID = -2587964095510462601L;

    private final SeriesGroup seriesGroup;
    private final AcquireTabPanel panel;

    public SerieButton(SeriesGroup seriesGroup, AcquireTabPanel panel) {
        super(seriesGroup.getDisplayName());
        this.seriesGroup = seriesGroup;
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
    public int compareTo(SerieButton o) {
        return getSerie().compareTo(o.getSerie());
    }

}
