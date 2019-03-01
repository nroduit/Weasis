/*******************************************************************************
 * Copyright (c) 2009-2018 Weasis Team and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 *     Nicolas Roduit - initial API and implementation
 *******************************************************************************/
package org.weasis.core.api.gui.task;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

import javax.swing.ImageIcon;
import javax.swing.JProgressBar;

import org.weasis.core.api.gui.util.AnimatedIconStatic;
import org.weasis.core.api.gui.util.ImageSectionIcon;
import org.weasis.core.api.util.FontTools;
import org.weasis.core.api.util.LocalUtil;

@SuppressWarnings("serial")
public class CircularProgressBar extends JProgressBar {
    private static final Color BACK_COLOR = new Color(82, 152, 219);
    public static final ImageIcon ICON =
        new ImageIcon(CircularProgressBar.class.getResource("/icon/22x22/process-working.png")); //$NON-NLS-1$

    private Animate animateThread;

    public CircularProgressBar() {
        init();
    }

    public CircularProgressBar(int min, int max) {
        super(min, max);
        init();
    }

    private void init() {
        this.setOpaque(false);
        this.setSize(30, 30);
        Dimension dim = new Dimension(30, 30);
        this.setPreferredSize(dim);
        this.setMaximumSize(dim);
    }

    @Override
    public void paint(Graphics g) {
        if (g instanceof Graphics2D) {
            if (isIndeterminate()) {
                drawInderminate((Graphics2D) g);
            } else {
                draw((Graphics2D) g);
            }
        }
    }

    private void drawInderminate(Graphics2D g) {
        if (animateThread != null) {
            animateThread.paintIcon(this, g);
        }
    }

    private void draw(Graphics2D g2) {
        int h = this.getHeight();
        int w = this.getWidth();
        int range = this.getMaximum() - this.getMinimum();
        if (range < 1) {
            range = 1;
        }
        int a = 360 - this.getValue() * 360 / range;
        String str = LocalUtil.getPercentInstance().format((double) this.getValue() / range);

        float x = w / 2.0f - g2.getFontMetrics().stringWidth(str) / 2.0f;

        final float fontHeight = FontTools.getAccurateFontHeight(g2);
        final float midfontHeight = fontHeight * FontTools.getMidFontHeightFactor();

        float y = h / 2.0f + midfontHeight;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setPaint(Color.WHITE);
        g2.fillArc(0, 0, w, h, 0, 360);

        g2.setPaint(BACK_COLOR);
        g2.fillArc(0, 0, w, h, a, 360 - a);

        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_DEFAULT);

        g2.setPaint(Color.BLACK);
        g2.drawString(str, x, y);
    }

    @Override
    public synchronized void setIndeterminate(boolean newValue) {
        if (animateThread != null) {
            stopIndeterminate();
        }
        if (newValue != this.isIndeterminate()) {
            if (newValue && animateThread == null) {
                animateThread = new Animate(50);
                animateThread.start();
            }
            super.setIndeterminate(newValue);
        }
    }

    public synchronized void stopIndeterminate() {
        Thread moribund = animateThread;
        animateThread = null;
        if (moribund != null) {
            moribund.interrupt();
        }
    }

    protected class Animate extends Thread {
        private final AnimatedIconStatic indeterminateIcon;
        private final long refresh;

        public Animate(long refresh) {
            super.setDaemon(true);
            this.refresh = refresh;
            indeterminateIcon = new ImageSectionIcon(ICON, 22, 22, 0, 32);
        }

        public void paintIcon(CircularProgressBar circularProgressBar, Graphics2D g) {
            int h = circularProgressBar.getHeight();
            int w = circularProgressBar.getWidth();
            int x = (w - indeterminateIcon.getIconWidth()) / 2;
            int y = (h - indeterminateIcon.getIconHeight()) / 2;
            g.setPaint(Color.WHITE);
            g.fillRect(x, y, indeterminateIcon.getIconWidth(), indeterminateIcon.getIconHeight());
            indeterminateIcon.paintIcon(circularProgressBar, g, x, y);
        }

        @Override
        public void run() {
            while (!this.isInterrupted()) {
                indeterminateIcon.animate();
                CircularProgressBar.this.repaint();
                try {
                    Thread.sleep(this.refresh);
                } catch (InterruptedException e) {
                    this.interrupt();
                }
            }
        }
    }

}
