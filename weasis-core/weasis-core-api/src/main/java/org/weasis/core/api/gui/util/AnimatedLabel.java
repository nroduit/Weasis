/*******************************************************************************
 * Copyright (c) 2010 Nicolas Roduit.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     Nicolas Roduit - initial API and implementation
 ******************************************************************************/
package org.weasis.core.api.gui.util;

import java.awt.Graphics;

import javax.swing.JLabel;

public class AnimatedLabel extends JLabel {

    private static final long serialVersionUID = 1L;

    private volatile AnimatedIconStatic ani;
    private volatile Animate an;

    public AnimatedLabel(AnimatedIconStatic icon, long refresh) {
        init(icon, refresh);
    }

    protected void init(AnimatedIconStatic icon, long refresh) {
        this.ani = icon;
        super.setIcon(icon);
        super.setSize(icon.getIconWidth(), icon.getIconWidth());
        this.an = new Animate(refresh);

    }

    public void start() {
        this.ani.reset();
        this.an.start();
    }

    public void stop() {
        this.an.interrupt();
    }

    public void reset() {
        this.ani.reset();
    }

    @Override
    protected void paintComponent(Graphics g) {
        ani.paintIcon(this, g, 0, 0);
        super.paintComponent(g);
    }

    @Override
    protected void finalize() throws Throwable {
        this.an.interrupt();
    }

    protected class Animate extends Thread {

        protected final long refresh;

        public Animate(long refresh) {
            super.setDaemon(true);
            super.setName(AnimatedLabel.this.ani.getName()); //$NON-NLS-1$ //$NON-NLS-2$
            this.refresh = refresh;
        }

        @Override
        public void run() {
            while (!this.isInterrupted()) {
                AnimatedLabel.this.ani.animate();
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
