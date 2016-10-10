/*******************************************************************************
 * Copyright (c) 2016 Weasis Team and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Nicolas Roduit - initial API and implementation
 *******************************************************************************/
package org.weasis.core.ui.model.layer;

public enum LayerType {
    IMAGE(10, "Image", Boolean.TRUE, Boolean.FALSE, Boolean.FALSE, Boolean.FALSE),

    CROSSLINES(20, "Crosslines", Boolean.TRUE, Boolean.TRUE, Boolean.FALSE, Boolean.FALSE),

    IMAGE_ANNOTATION(10_000, "Image Annotations", Boolean.TRUE, Boolean.FALSE, Boolean.FALSE, Boolean.FALSE),

    ANNOTATION(31, "Annotations", Boolean.TRUE, Boolean.FALSE, Boolean.TRUE, Boolean.TRUE),

    DRAW(40, "Drawings", Boolean.TRUE, Boolean.FALSE, Boolean.TRUE, Boolean.TRUE),

    MEASURE(50, "Measurements", Boolean.TRUE, Boolean.FALSE, Boolean.TRUE, Boolean.TRUE),

    TEMP_DRAW(60, "Drawings [Temp]", Boolean.TRUE, Boolean.FALSE, Boolean.FALSE, Boolean.TRUE),

    ACQUIRE(70, "Dicomizer [Temp]", Boolean.TRUE, Boolean.FALSE, Boolean.FALSE, Boolean.TRUE),

    BLOB(80, "Objects", Boolean.TRUE, Boolean.TRUE, Boolean.TRUE, Boolean.TRUE),

    POINTS(90, "Points", Boolean.TRUE, Boolean.TRUE, Boolean.TRUE, Boolean.TRUE),

    DICOM_PR(100, "DICOM PR", Boolean.TRUE, Boolean.TRUE, Boolean.FALSE, Boolean.TRUE);

    private final Integer level;
    private final Boolean visible;
    private final Boolean locked;
    private final Boolean serializable;
    private final Boolean selectable;
    private final String defaultName;

    private LayerType(Integer level, String defaultName, Boolean visible, Boolean locked, Boolean serializable,
        Boolean selectable) {
        this.level = level;
        this.visible = visible;
        this.locked = locked;
        this.serializable = serializable;
        this.defaultName = defaultName;
        this.selectable = selectable;
    }

    public Integer getLevel() {
        return level;
    }

    @Override
    public String toString() {
        return defaultName;
    }

    public String getDefaultName() {
        return defaultName;
    }

    public Boolean getVisible() {
        return visible;
    }

    public Boolean getLocked() {
        return locked;
    }

    public Boolean getSerializable() {
        return serializable;
    }

    public Boolean getSelectable() {
        return selectable;
    }

}
