package org.weasis.acquire.explorer.gui.central.component;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JToggleButton;

import org.weasis.acquire.explorer.core.bean.Serie;
import org.weasis.acquire.explorer.gui.central.AcquireTabPanel;

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
    public int compareTo(SerieButton o) {
        return getSerie().compareTo(o.getSerie());
    }

}
