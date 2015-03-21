/*
 * @copyright Copyright (c) 2012 Animati Sistemas de Informática Ltda.
 * (http://www.animati.com.br)
 */

package br.com.animati.texture.mpr3dview;

import java.awt.Point;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import org.weasis.core.api.gui.util.ActionState;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.gui.util.MouseActionAdapter;

/**
 *
 * @author Gabriela Bauermann (gabriela@animati.com.br)
 * @version 2013, 7 ago
 */
public abstract class TexturePannerListener extends MouseActionAdapter
        implements ActionState, KeyListener {
    protected final ActionW action;
    private Point oldPoint;


    public TexturePannerListener(ActionW actionW) {
        action = actionW;
    }
    
    @Override
    public void enableAction(boolean enabled) { /*Empty*/ }
    @Override
    public boolean isActionEnabled() { return true; }

    @Override
    public ActionW getActionW() {
        return action;
    }
    
    public abstract void pointChanged(int x, int y);
    
    @Override
    public String toString() {
        return action.getTitle();
    }
    
    @Override
    public void mousePressed(MouseEvent evt) {
        int buttonMask = getButtonMaskEx();
        if ((evt.getModifiersEx() & buttonMask) != 0) {
            oldPoint = evt.getPoint();
        }
    }
    
    @Override
    public void mouseDragged(MouseEvent evt) {
        int buttonMask = getButtonMaskEx();
        if (!evt.isConsumed() && (evt.getModifiersEx() & buttonMask) != 0) {
            if (oldPoint != null) {
                Point point1 = evt.getPoint();
                pointChanged(point1.x - oldPoint.x, point1.y - oldPoint.y);
                oldPoint = point1;
            }
        }
    }
    
    @Override
    public void mouseReleased(MouseEvent e) {
        int buttonMask = getButtonMask();
        if (!e.isConsumed() && (e.getModifiers() & buttonMask) != 0) {
            oldPoint = null;
        }
    }
    
    @Override
    public void keyPressed(KeyEvent ke) {
        if (ke.getKeyCode() == KeyEvent.VK_LEFT) {
            setPoint(new Point(-5, 0));
        } else if (ke.getKeyCode() == KeyEvent.VK_UP) {
            setPoint(new Point(0, -5));
        } else if (ke.getKeyCode() == KeyEvent.VK_RIGHT) {
            setPoint(new Point(5, 0));
        } else if (ke.getKeyCode() == KeyEvent.VK_DOWN) {
            setPoint(new Point(0, 5));
        }
    }

    public void setPoint(Point point) {
        if (point != null) {
            pointChanged(point.x, point.y);
        }
    }

    @Override public void keyTyped(KeyEvent ke) { /*Empty*/ }
    @Override public void keyReleased(KeyEvent ke) { /*Empty*/ }
    @Override public boolean registerActionState(Object c) { return false; }
    @Override public void unregisterActionState(Object c) { /*Empty*/ }

}


