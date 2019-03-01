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
package org.weasis.core.api.gui.util;

import java.awt.Graphics;

import javax.swing.JLabel;

public class AnimatedLabel extends JLabel {

    private static final long serialVersionUID = 1L;

    private final AnimatedIconStatic icon;
    private final Animate animate;

    public AnimatedLabel(AnimatedIconStatic icon, long refresh) {
        this.icon = icon;
        super.setIcon(icon);
        super.setSize(icon.getIconWidth(), icon.getIconWidth());
        this.animate = new Animate(refresh);
    }

    public void start() {
        this.icon.reset();
        this.animate.start();
    }

    public void stop() {
        this.animate.interrupt();
    }

    public void reset() {
        this.icon.reset();
    }

    @Override
    protected void paintComponent(Graphics g) {
        icon.paintIcon(this, g, 0, 0);
        super.paintComponent(g);
    }

    protected class Animate extends Thread {

        protected final long refresh;

        public Animate(long refresh) {
            super.setDaemon(true);
            super.setName(AnimatedLabel.this.icon.getName());
            this.refresh = refresh;
        }

        @Override
        public void run() {
            while (!this.isInterrupted()) {
                AnimatedLabel.this.icon.animate();
                AnimatedLabel.this.repaint();
                try {
                    Thread.sleep(this.refresh);
                } catch (InterruptedException e) {
                    this.interrupt();
                }
            }
        }
    }

}
