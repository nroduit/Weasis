package org.weasis.core.ui.editor.image;

import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.geom.Rectangle2D;

import javax.swing.Icon;

public class ViewButton extends Rectangle2D.Double implements ShowPopup {

    private final ShowPopup popup;
    private final Icon icon;
    private boolean visible;
    private boolean enable;
    private int position;

    public ViewButton(ShowPopup popup, Icon icon) {
        if (icon == null || popup == null) {
            throw new IllegalArgumentException("Null parameter");
        }
        this.popup = popup;
        this.icon = icon;
        this.position = GridBagConstraints.EAST;
        this.setFrame(0, 0, icon.getIconWidth(), icon.getIconHeight());
    }

    public boolean isVisible() {
        return visible;
    }

    public void setVisible(boolean visible) {
        this.visible = visible;
    }

    public boolean isEnable() {
        return enable;
    }

    public void setEnable(boolean enable) {
        this.enable = enable;
    }

    public Icon getIcon() {
        return icon;
    }

    public int getPosition() {
        return position;
    }

    public void setPosition(int position) {
        this.position = position;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof ViewButton) {
            return ((ViewButton) obj).popup == popup;
        }
        return false;
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.weasis.core.ui.editor.image.ShowPopup#showPopup(java.awt.Component, int, int)
     */
    @Override
    public void showPopup(Component invoker, int x, int y) {
        popup.showPopup(invoker, x, y);
    }

}
