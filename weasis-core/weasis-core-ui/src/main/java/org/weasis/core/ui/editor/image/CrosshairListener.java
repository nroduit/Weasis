/*******************************************************************************
 * Copyright (c) 2009-2018 Weasis Team and others. All rights reserved. This program and the accompanying materials are made available under the terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors: Nicolas Roduit - initial API and implementation
 *******************************************************************************/
package org.weasis.core.ui.editor.image;

import java.awt.Point;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.geom.Point2D;
import java.util.Objects;
import java.util.Optional;

import org.weasis.core.api.gui.util.ActionState;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.gui.util.BasicActionState;
import org.weasis.core.api.gui.util.MouseActionAdapter;
import org.weasis.core.api.service.AuditLog;

public abstract class CrosshairListener extends MouseActionAdapter implements ActionState, KeyListener {

    private volatile MouseActionAdapter win = null;
    private volatile MouseActionAdapter lev = null;
    private final BasicActionState basicState;
    private final boolean triggerAction = true;
    private Point pickPoint;

    private Point2D point;

    public CrosshairListener(ActionW action, Point2D point) {
        this.basicState = new BasicActionState(action);
        this.point = point == null ? new Point2D.Double() : point;
    }

    @Override
    public void enableAction(boolean enabled) {
        basicState.enableAction(enabled);
    }

    @Override
    public boolean isActionEnabled() {
        return basicState.isActionEnabled();
    }

    @Override
    public boolean registerActionState(Object c) {
        return basicState.registerActionState(c);
    }

    @Override
    public void unregisterActionState(Object c) {
        basicState.unregisterActionState(c);
    }

    public Point2D getPoint() {
        return (Point2D) point.clone();
    }

    public void setPoint(Point2D point) {
        if (point != null) {
            this.point = point;
            pointChanged(point);
            AuditLog.LOGGER.info("action:{} val:{},{}", //$NON-NLS-1$
                    new Object[] { basicState.getActionW().cmd(), point.getX(), point.getY() });
        }
    }

    public boolean isTriggerAction() {
        return triggerAction;
    }

    @Override
    public ActionW getActionW() {
        return basicState.getActionW();
    }

    public String getValueToDisplay() {
        return "x:" + point.getX() + ", y:" + point.getY(); //$NON-NLS-1$ //$NON-NLS-2$
    }

    public abstract void pointChanged(Point2D point);

    @Override
    public void setButtonMaskEx(int buttonMask) {
        // Zero is used to disable the mouse adapter
        if (buttonMask == 0 && this.buttonMaskEx != 0) {
            // Convention to delete cross-lines on the views when selecting another action
            this.setPoint(new Point2D.Double(Double.NaN, Double.NaN));
        }
        super.setButtonMaskEx(buttonMask);
    }

    @Override
    public String toString() {
        return basicState.getActionW().getTitle();
    }

    private static ViewCanvas<?> getViewCanvas(InputEvent e) {
        Object source = e.getSource();
        if (source instanceof ViewCanvas) {
            return (ViewCanvas<?>) source;
        }
        return null;
    }

    @Override
    public void mousePressed(MouseEvent e) {
        if (basicState.isActionEnabled()) {
            int buttonMask = getButtonMaskEx();
            int modifier = e.getModifiersEx();
            if (!e.isConsumed() && (modifier & buttonMask) != 0) {
                ViewCanvas<?> panner = getViewCanvas(e);
                int mask = InputEvent.CTRL_DOWN_MASK;
                if ((modifier & mask) == mask && Objects.nonNull(panner)) {
                    panner.getJComponent().setCursor(ActionW.WINLEVEL.getCursor());
                    win = (MouseActionAdapter) panner.getEventManager().getAction(ActionW.WINDOW);
                    if (win != null) {
                        win.setButtonMaskEx(win.getButtonMaskEx() | buttonMask);
                        win.setMoveOnX(true);
                        win.mousePressed(e);
                    }
                    lev = (MouseActionAdapter) panner.getEventManager().getAction(ActionW.LEVEL);
                    if (lev != null) {
                        lev.setButtonMaskEx(lev.getButtonMaskEx() | buttonMask);
                        lev.setInverse(true);
                        lev.mousePressed(e);
                    }
                } else {
                    releaseWinLevelAdapter();

                    if (Objects.nonNull(panner)) {
                        pickPoint = e.getPoint();
                        setPoint(panner.getImageCoordinatesFromMouse(e.getX(), e.getY()));
                    }
                }
            }
        }
    }

    @Override
    public void mouseDragged(MouseEvent e) {
        if (basicState.isActionEnabled()) {
            int buttonMask = getButtonMaskEx();
            if (!e.isConsumed() && (e.getModifiersEx() & buttonMask) != 0) {
                ViewCanvas<?> panner = getViewCanvas(e);
                if (win != null && Objects.nonNull(panner)) {
                    panner.getJComponent().setCursor(ActionW.WINLEVEL.getCursor());
                    win.mouseDragged(e);
                    if (lev != null) {
                        lev.mouseDragged(e);
                    }
                } else if (Objects.nonNull(panner) && Objects.nonNull(pickPoint)) {
                    setPoint(panner.getImageCoordinatesFromMouse(e.getX(), e.getY()));
                }
            }
        }
    }

    @Override
    public void mouseReleased(MouseEvent e) {
        releaseWinLevelAdapter();
        if (basicState.isActionEnabled() && !e.isConsumed() && (e.getModifiers() & getButtonMask()) != 0) {
            ViewCanvas<?> panner = getViewCanvas(e);
            Optional.ofNullable(panner).ifPresent(p -> p.getJComponent().repaint());
        }
    }

    private void releaseWinLevelAdapter() {
        if (win != null) {
            win.setButtonMaskEx(0);
            win = null;
        }
        if (lev != null) {
            lev.setButtonMaskEx(0);
            lev = null;
        }
    }

    public void reset() {
        pickPoint = null;
    }

    @Override
    public void keyPressed(KeyEvent e) {
    }

    @Override
    public void keyReleased(KeyEvent e) {
    }

    @Override
    public void keyTyped(KeyEvent e) {
    }

}
