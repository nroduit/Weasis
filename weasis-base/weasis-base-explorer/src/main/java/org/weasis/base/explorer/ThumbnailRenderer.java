/*******************************************************************************
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.weasis.base.explorer;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Rectangle;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.ListCellRenderer;
import javax.swing.OverlayLayout;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;
import javax.swing.border.EtchedBorder;

import org.weasis.base.explorer.list.AbstractThumbnailList;
import org.weasis.base.explorer.list.ThumbnailList;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.api.media.data.MediaElement;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.api.util.FontTools;
import org.weasis.core.api.util.LangUtil;

@SuppressWarnings("serial")
public class ThumbnailRenderer<E extends MediaElement> extends JPanel implements ListCellRenderer<E> {

    public static final Dimension ICON_DIM = new Dimension(150, 150);
    public static final Icon ICON_CHECKED = new ImageIcon(ThumbnailRenderer.class.getResource("/icon/24x24/tick.png")); //$NON-NLS-1$

    private final JLabel iconLabel = new JLabel("", SwingConstants.CENTER); //$NON-NLS-1$
    private final JLabel iconCheckedLabel = new JLabel((Icon) null);
    private final JLabel descriptionLabel = new JLabel("", SwingConstants.CENTER); //$NON-NLS-1$
    private static final Color back = new Color(242, 242, 242);

    public ThumbnailRenderer() {
        // Cannot pass a boxLayout directly to super because it has a this reference
        super(null, true);
        this.setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        JPanel panel = new JPanel();
        panel.setLayout(new OverlayLayout(panel));
        panel.setPreferredSize(ICON_DIM);
        panel.setMaximumSize(ICON_DIM);

        iconCheckedLabel.setPreferredSize(ICON_DIM);
        iconCheckedLabel.setMaximumSize(ICON_DIM);
        panel.add(iconCheckedLabel);

        iconLabel.setPreferredSize(ICON_DIM);
        iconLabel.setMaximumSize(ICON_DIM);
        iconLabel.setBorder(BorderFactory.createEtchedBorder(EtchedBorder.RAISED));
        panel.add(iconLabel);
        this.add(panel);

        descriptionLabel.setFont(FontTools.getFont10());
        Dimension dim = new Dimension(ICON_DIM.width, descriptionLabel.getFont().getSize() + 4);
        descriptionLabel.setPreferredSize(dim);
        descriptionLabel.setMaximumSize(dim);

        this.add(descriptionLabel);

        setBorder(new EmptyBorder(5, 5, 5, 5));
    }

    @Override
    public Component getListCellRendererComponent(JList<? extends E> list, E value, int index, boolean isSelected,
        boolean cellHasFocus) {
        ThumbnailIcon icon = null;
        if (value instanceof ImageElement) {
            if (list instanceof AbstractThumbnailList) {
                icon = ((AbstractThumbnailList) list).getThumbCache().getThumbnailFor((ImageElement) value,
                    (ThumbnailList<E>) list, index);
            }
            if (LangUtil.getNULLtoFalse((Boolean) value.getTagValue(TagW.Checked))) {
                iconCheckedLabel.setIcon(ICON_CHECKED);
            } else {
                iconCheckedLabel.setIcon(null);
            }
        }
        this.iconLabel.setIcon(icon == null ? JIUtility.getSystemIcon(value) : icon);
        this.descriptionLabel.setText(value.getName());
        setBackground(isSelected ? list.getSelectionBackground() : back);
        return this;
    }

    @Override
    public void repaint(final long tm, final int x, final int y, final int width, final int height) {
        // Overridden for performance reasons
    }

    @Override
    public void repaint(final Rectangle r) {
        // Overridden for performance reasons
    }

    @Override
    protected void firePropertyChange(final String propertyName, final Object oldValue, final Object newValue) {
        // Overridden for performance reasons
    }

    @Override
    public void firePropertyChange(final String propertyName, final byte oldValue, final byte newValue) {
        // Overridden for performance reasons
    }

    @Override
    public void firePropertyChange(final String propertyName, final char oldValue, final char newValue) {
        // Overridden for performance reasons
    }

    @Override
    public void firePropertyChange(final String propertyName, final short oldValue, final short newValue) {
        // Overridden for performance reasons
    }

    @Override
    public void firePropertyChange(final String propertyName, final int oldValue, final int newValue) {
        // Overridden for performance reasons
    }

    @Override
    public void firePropertyChange(final String propertyName, final long oldValue, final long newValue) {
        // Overridden for performance reasons
    }

    @Override
    public void firePropertyChange(final String propertyName, final float oldValue, final float newValue) {
        // Overridden for performance reasons
    }

    @Override
    public void firePropertyChange(final String propertyName, final double oldValue, final double newValue) {
        // Overridden for performance reasons
    }

    @Override
    public void firePropertyChange(final String propertyName, final boolean oldValue, final boolean newValue) {
        // Overridden for performance reasons
    }

}
