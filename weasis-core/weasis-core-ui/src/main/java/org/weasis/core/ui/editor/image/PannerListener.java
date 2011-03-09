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
package org.weasis.core.ui.editor.image;

import java.awt.Point;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.geom.Point2D;

import org.weasis.core.api.gui.util.ActionState;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.gui.util.MouseActionAdapter;
import org.weasis.core.ui.graphic.DragPoint;

public abstract class PannerListener extends MouseActionAdapter implements ActionState, KeyListener {

    protected final ActionW action;
    private boolean enable;
    private final boolean triggerAction = true;
    private Point pickPoint;

    private Point2D point;

    public PannerListener(ActionW action, Point2D point) {
        this.action = action;
        this.point = point == null ? new Point2D.Double() : point;
        enable = true;
    }

    public void enableAction(boolean enabled) {
        this.enable = enabled;
    }

    public Point2D getPoint() {
        return (Point2D) point.clone();
    }

    public void setPoint(Point2D point) {
        if (point != null) {
            this.point = point;
            pointChanged(point);
        }
    }

    public boolean isTriggerAction() {
        return triggerAction;
    }

    public ActionW getActionW() {
        return action;
    }

    public String getValueToDisplay() {
        return "x:" + point.getX() + ", y:" + point.getY(); //$NON-NLS-1$ //$NON-NLS-2$
    }

    public abstract void pointChanged(Point2D point);

    @Override
    public String toString() {
        return action.getTitle();
    }

    private DefaultView2d getDefaultView2d(InputEvent e) {
        Object source = e.getSource();
        if (source instanceof DefaultView2d) {
            return (DefaultView2d) source;
        }
        return null;
    }

    @Override
    public void mousePressed(MouseEvent e) {
        int buttonMask = getButtonMaskEx();
        if ((e.getModifiersEx() & buttonMask) != 0) {
            DefaultView2d panner = getDefaultView2d(e);
            if (panner != null) {
                pickPoint = e.getPoint();
                double scale = panner.getViewModel().getViewScale();
                setPoint(new DragPoint(DragPoint.STATE.Started, -(pickPoint.getX() / scale),
                    -(pickPoint.getY() / scale)));
            }
        }
    }

    @Override
    public void mouseDragged(MouseEvent e) {
        int buttonMask = getButtonMaskEx();
        if ((e.getModifiersEx() & buttonMask) != 0) {
            DefaultView2d panner = getDefaultView2d(e);
            if (panner != null) {
                if (pickPoint != null) {
                    double scale = panner.getViewModel().getViewScale();
                    setPoint(new DragPoint(DragPoint.STATE.Dragged, -((e.getX() - pickPoint.getX()) / scale),
                        -((e.getY() - pickPoint.getY()) / scale)));
                    panner.setPointerType(1);
                }
            }
        }
    }

    @Override
    public void mouseReleased(MouseEvent e) {
        int buttonMask = getButtonMask();
        if ((e.getModifiers() & buttonMask) != 0) {
            DefaultView2d panner = getDefaultView2d(e);
            if (panner != null) {
                panner.setPointerType(0);
                panner.repaint();
            }
        }
    }

    public void reset() {
        pickPoint = null;
    }

    public void keyPressed(KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.VK_LEFT) {
            setPoint(new Point(5, 0));
        } else if (e.getKeyCode() == KeyEvent.VK_UP) {
            setPoint(new Point(0, 5));
        } else if (e.getKeyCode() == KeyEvent.VK_RIGHT) {
            setPoint(new Point(-5, 0));
        } else if (e.getKeyCode() == KeyEvent.VK_DOWN) {
            setPoint(new Point(0, -5));
        }
    }

    public void keyReleased(KeyEvent e) {
    }

    public void keyTyped(KeyEvent e) {
    }

}
