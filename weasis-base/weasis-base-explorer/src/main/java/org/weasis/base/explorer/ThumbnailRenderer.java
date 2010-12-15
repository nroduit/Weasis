package org.weasis.base.explorer;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Rectangle;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.Icon;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.ListCellRenderer;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;
import javax.swing.border.EtchedBorder;

import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.api.media.data.MediaElement;
import org.weasis.core.api.util.FontTools;

public class ThumbnailRenderer extends JPanel implements ListCellRenderer {

    public static final Dimension ICON_DIM = new Dimension(150, 150);
    private final JLabel iconLabel = new JLabel("", SwingConstants.CENTER);
    private final JLabel descriptionLabel = new JLabel("", SwingConstants.CENTER);
    private final static Color back = new Color(242, 242, 242);

    // TODO renderer not supported by Substance laf
    public ThumbnailRenderer() {
        // Cannot pass a boxLayout directly to super because it has a this reference
        super(null, true);
        this.setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        this.iconLabel.setVerticalAlignment(SwingConstants.CENTER);
        iconLabel.setPreferredSize(ICON_DIM);
        iconLabel.setMaximumSize(ICON_DIM);
        iconLabel.setBorder(BorderFactory.createEtchedBorder(EtchedBorder.RAISED));
        this.add(iconLabel);

        descriptionLabel.setFont(FontTools.getFont10());
        Dimension dim = new Dimension(ICON_DIM.width, descriptionLabel.getFont().getSize() + 4);
        descriptionLabel.setPreferredSize(dim);
        descriptionLabel.setMaximumSize(dim);
        this.add(descriptionLabel);
        setBorder(new EmptyBorder(5, 5, 5, 5));
    }

    public Component getListCellRendererComponent(final JList list, final Object value, final int index,
        final boolean isSelected, final boolean cellHasFocus) {

        if (value instanceof MediaElement) {
            final MediaElement diskObject = (MediaElement) value;
            Icon icon = null;
            if (value instanceof ImageElement) {
                icon =
                    JIThumbnailCache.getInstance().getThumbnailFor((ImageElement) diskObject, (JIThumbnailList) list,
                        index);
            }
            this.iconLabel.setIcon(icon == null ? JIUtility.getSystemIcon(diskObject) : icon);
            this.descriptionLabel.setText(diskObject.getName());
            setBackground(isSelected ? list.getSelectionBackground() : back);
            return this;
        }

        return null;
    }

    @Override
    public void repaint(final long tm, final int x, final int y, final int width, final int height) {
    }

    /**
     * Overridden for performance reasons. See the <a href="#override">Implementation Note</a> for more information.
     */
    @Override
    public void repaint(final Rectangle r) {
    }

    /**
     * Overridden for performance reasons. See the <a href="#override">Implementation Note</a> for more information.
     */
    @Override
    protected void firePropertyChange(final String propertyName, final Object oldValue, final Object newValue) {
    }

    /**
     * Overridden for performance reasons. See the <a href="#override">Implementation Note</a> for more information.
     */
    @Override
    public void firePropertyChange(final String propertyName, final byte oldValue, final byte newValue) {
    }

    /**
     * Overridden for performance reasons. See the <a href="#override">Implementation Note</a> for more information.
     */
    @Override
    public void firePropertyChange(final String propertyName, final char oldValue, final char newValue) {
    }

    /**
     * Overridden for performance reasons. See the <a href="#override">Implementation Note</a> for more information.
     */
    @Override
    public void firePropertyChange(final String propertyName, final short oldValue, final short newValue) {
    }

    /**
     * Overridden for performance reasons. See the <a href="#override">Implementation Note</a> for more information.
     */
    @Override
    public void firePropertyChange(final String propertyName, final int oldValue, final int newValue) {
    }

    /**
     * Overridden for performance reasons. See the <a href="#override">Implementation Note</a> for more information.
     */
    @Override
    public void firePropertyChange(final String propertyName, final long oldValue, final long newValue) {
    }

    /**
     * Overridden for performance reasons. See the <a href="#override">Implementation Note</a> for more information.
     */
    @Override
    public void firePropertyChange(final String propertyName, final float oldValue, final float newValue) {
    }

    /**
     * Overridden for performance reasons. See the <a href="#override">Implementation Note</a> for more information.
     */
    @Override
    public void firePropertyChange(final String propertyName, final double oldValue, final double newValue) {
    }

    /**
     * Overridden for performance reasons. See the <a href="#override">Implementation Note</a> for more information.
     */
    @Override
    public void firePropertyChange(final String propertyName, final boolean oldValue, final boolean newValue) {
    }

}
