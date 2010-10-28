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

import java.awt.Graphics2D;
import java.awt.Rectangle;

import org.weasis.core.ui.Messages;
import org.weasis.core.ui.graphic.model.Layer;

public interface AnnotationsLayer extends Layer {

    public final static String ANNOTATIONS = Messages.getString("AnnotationsLayer.anno"); //$NON-NLS-1$
    public final static String SCALE = Messages.getString("AnnotationsLayer.scale"); //$NON-NLS-1$
    public final static String LUT = Messages.getString("AnnotationsLayer.lut"); //$NON-NLS-1$
    public final static String IMAGE_ORIENTATION = Messages.getString("AnnotationsLayer.or"); //$NON-NLS-1$
    public final static String WINDOW_LEVEL = Messages.getString("AnnotationsLayer.wl"); //$NON-NLS-1$
    public final static String ZOOM = Messages.getString("AnnotationsLayer.zoom"); //$NON-NLS-1$
    public final static String ROTATION = Messages.getString("AnnotationsLayer.rot"); //$NON-NLS-1$
    public final static String FRAME = Messages.getString("AnnotationsLayer.fr"); //$NON-NLS-1$
    public final static String PIXEL = Messages.getString("AnnotationsLayer.pix"); //$NON-NLS-1$

    public abstract boolean getDisplayPreferences(String item);

    public abstract boolean setDisplayPreferencesValue(String displayItem, boolean selected);

    public abstract Rectangle getPreloadingProgressBound();

    public abstract Rectangle getPixelInfoBound();

    public abstract void setPixelInfo(String pixelInfo);

    public abstract void paint(Graphics2D g2d);

}
