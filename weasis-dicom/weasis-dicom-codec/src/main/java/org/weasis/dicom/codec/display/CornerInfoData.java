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
package org.weasis.dicom.codec.display;

import java.io.Serializable;

import org.weasis.core.api.media.data.TagElement;

public class CornerInfoData implements Serializable {

    private static final long serialVersionUID = 5017001560431431636L;
    public final static int ELEMENT_NUMBER = 7;
    private final CornerDisplay corner;
    private TagElement[] infos;

    public CornerInfoData(CornerDisplay corner) {
        this.corner = corner;
        this.infos = new TagElement[ELEMENT_NUMBER];
    }

    public TagElement[] getInfos() {
        return infos;
    }

    public void setInfos(TagElement[] infos) {
        this.infos = infos;
    }

    public CornerDisplay getCorner() {
        return corner;
    }

    @Override
    public String toString() {
        return corner.toString();
    }
}
