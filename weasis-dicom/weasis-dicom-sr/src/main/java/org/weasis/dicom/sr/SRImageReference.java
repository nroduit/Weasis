/*******************************************************************************
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.weasis.dicom.sr;

import java.util.ArrayList;
import java.util.List;

import org.weasis.core.ui.model.graphic.Graphic;
import org.weasis.dicom.codec.macro.SOPInstanceReference;

public class SRImageReference {
    private SOPInstanceReference sopInstanceReference;
    private List<Graphic> graphics;
    private final String nodeLevel;

    public SRImageReference(String nodeLevel) {
        super();
        this.nodeLevel = nodeLevel;
    }

    public void addGraphic(Graphic g) {
        if (g != null) {
            if (graphics == null) {
                graphics = new ArrayList<>();
            }
            graphics.add(g);
        }
    }

    public SOPInstanceReference getSopInstanceReference() {
        return sopInstanceReference;
    }

    public void setSopInstanceReference(SOPInstanceReference sopInstanceReference) {
        this.sopInstanceReference = sopInstanceReference;
    }

    public List<Graphic> getGraphics() {
        return graphics;
    }

    public String getNodeLevel() {
        return nodeLevel;
    }

}
