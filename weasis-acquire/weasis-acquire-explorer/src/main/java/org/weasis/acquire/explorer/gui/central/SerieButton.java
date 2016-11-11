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

import org.weasis.acquire.explorer.core.bean.Serie;

public class SerieButton extends JToggleButton implements ActionListener, Comparable<SerieButton> {
    private static final long serialVersionUID = -2587964095510462601L;

    private final Serie serie;
    private final AcquireTabPanel panel;

    public SerieButton(Serie serie, AcquireTabPanel panel) {
        super(serie.getDisplayName());
        this.serie = serie;
        this.panel = panel;
        addActionListener(this);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if (e.getSource() == this) {
            panel.setSelected(this);
        }
    }

    public Serie getSerie() {
        return serie;
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
