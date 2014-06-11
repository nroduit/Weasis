package org.weasis.core.ui.util;

import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JComponent;
import javax.swing.RootPaneContainer;
import javax.swing.Timer;

import org.jdesktop.jxlayer.JXLayer;
import org.jdesktop.jxlayer.plaf.AbstractLayerUI;

public class ColorLayerUI extends AbstractLayerUI<JComponent> {

    protected static final float MAX_ALPHA = 0.75f;

    private final RootPaneContainer parent;
    protected final JXLayer<JComponent> xlayer;
    protected volatile float alpha;

    public ColorLayerUI(final JXLayer<JComponent> comp, RootPaneContainer parent) {
        if (parent == null || comp == null) {
            throw new IllegalArgumentException();
        }
        this.parent = parent;
        this.xlayer = comp;
    }

    public static ColorLayerUI createTransparentLayerUI(RootPaneContainer parent) {
        if (parent != null) {
            JXLayer<JComponent> layer = new JXLayer(parent.getContentPane());
            final ColorLayerUI ui = new ColorLayerUI(layer, parent);
            layer.setUI(ui);
            parent.setContentPane(layer);
            ui.ShowUI();
            return ui;
        }
        return null;
    }

    @Override
    protected void paintLayer(final Graphics2D g, final JXLayer<? extends JComponent> comp) {
        super.paintLayer(g, this.xlayer);
        g.setColor(comp.getBackground());
        g.setComposite(AlphaComposite.SrcOver.derive(alpha * MAX_ALPHA));
        g.fillRect(0, 0, comp.getWidth(), comp.getHeight());
    }

    public synchronized void ShowUI() {
        this.alpha = 0.0f;
        final Timer timer = new Timer(3, null);
        timer.setRepeats(true);
        timer.addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent e) {
                alpha = Math.min(alpha + 0.1f, 1.0F);
                if (alpha >= 1.0) {
                    timer.stop();
                }
                xlayer.repaint();
            }
        });
        this.xlayer.repaint();
        timer.start();

    }

    public synchronized void hideUI() {
        this.alpha = 1.0f;
        final Timer timer = new Timer(3, null);
        timer.setRepeats(true);
        timer.addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent e) {
                alpha = Math.max(alpha - 0.1f, 0.0F);
                if (alpha <= 0.0) {
                    timer.stop();
                    parent.setContentPane(xlayer.getView());
                    return;
                }
                xlayer.repaint();
            }
        });
        this.xlayer.repaint();
        timer.start();

    }

    public JXLayer<JComponent> getXlayer() {
        return xlayer;
    }
}