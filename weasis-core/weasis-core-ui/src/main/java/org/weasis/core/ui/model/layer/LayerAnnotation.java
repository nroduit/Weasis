/*******************************************************************************
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.weasis.core.ui.model.layer;

import java.awt.Graphics2D;
import java.awt.Rectangle;

import org.weasis.core.ui.Messages;
import org.weasis.core.ui.editor.image.PixelInfo;
import org.weasis.core.ui.editor.image.ViewCanvas;

public interface LayerAnnotation extends Layer {

    String ANNOTATIONS = Messages.getString("AnnotationsLayer.anno"); //$NON-NLS-1$
    String MIN_ANNOTATIONS = Messages.getString("LayerAnnotation.min_anot"); //$NON-NLS-1$
    String ANONYM_ANNOTATIONS = Messages.getString("AnnotationsLayer.anonym"); //$NON-NLS-1$
    String SCALE = Messages.getString("AnnotationsLayer.scale"); //$NON-NLS-1$
    String LUT = Messages.getString("AnnotationsLayer.lut"); //$NON-NLS-1$
    String IMAGE_ORIENTATION = Messages.getString("AnnotationsLayer.or"); //$NON-NLS-1$
    String WINDOW_LEVEL = Messages.getString("AnnotationsLayer.wl"); //$NON-NLS-1$
    String ZOOM = Messages.getString("AnnotationsLayer.zoom"); //$NON-NLS-1$
    String ROTATION = Messages.getString("AnnotationsLayer.rot"); //$NON-NLS-1$
    String FRAME = Messages.getString("AnnotationsLayer.fr"); //$NON-NLS-1$
    String PIXEL = Messages.getString("AnnotationsLayer.pix"); //$NON-NLS-1$
    String PRELOADING_BAR = Messages.getString("AnnotationsLayer.preload_bar"); //$NON-NLS-1$
    String KEY_OBJECT = Messages.getString("AnnotationsLayer.ko"); //$NON-NLS-1$

    Boolean getDisplayPreferences(String item);

    Boolean setDisplayPreferencesValue(String displayItem, Boolean selected);

    Rectangle getPreloadingProgressBound();

    Rectangle getPixelInfoBound();

    void setPixelInfo(PixelInfo pixelInfo);

    PixelInfo getPixelInfo();

    Integer getBorder();

    void setBorder(Integer border);

    void paint(Graphics2D g2d);

    LayerAnnotation getLayerCopy(ViewCanvas view2DPane);

    Boolean isShowBottomScale();

    void setShowBottomScale(Boolean showBottomScale);

    void resetToDefault();

}
