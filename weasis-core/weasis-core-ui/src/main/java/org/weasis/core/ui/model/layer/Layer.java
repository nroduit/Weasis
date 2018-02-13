/*******************************************************************************
 * Copyright (c) 2009-2018 Weasis Team and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 *     Nicolas Roduit - initial API and implementation
 *******************************************************************************/
package org.weasis.core.ui.model.layer;

import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

import org.weasis.core.ui.model.utils.UUIDable;

@XmlJavaTypeAdapter(AbstractGraphicLayer.Adapter.class)
public interface Layer extends Comparable<Layer>, UUIDable {

    void setVisible(Boolean visible);

    Boolean getVisible();

    void setLevel(Integer level);

    Integer getLevel();

    LayerType getType();

    void setType(LayerType type);

    /**
     * Set a name to the layer. The default value is null and toString() gets the layer type name.
     *
     * @param layerName
     */
    void setName(String layerName);

    String getName();

    @Override
    default int compareTo(Layer obj) {
        if (obj == null) {
            return 1;
        }
        int thisVal = this.getLevel();
        int anotherVal = obj.getLevel();
        return thisVal < anotherVal ? -1 : (thisVal == anotherVal ? 0 : 1);
    }
}
