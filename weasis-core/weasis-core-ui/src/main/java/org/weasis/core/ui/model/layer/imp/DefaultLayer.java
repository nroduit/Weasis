/*******************************************************************************
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.weasis.core.ui.model.layer.imp;

import javax.xml.bind.annotation.XmlRootElement;

import org.weasis.core.ui.model.layer.AbstractGraphicLayer;
import org.weasis.core.ui.model.layer.LayerType;

/**
 * The Class DefaultLayer.
 *
 * @author Nicolas Roduit
 * @author Benoit Jacquemoud
 */
@XmlRootElement(name = "layer")
public class DefaultLayer extends AbstractGraphicLayer {

    private static final long serialVersionUID = 8576601524359423997L;

    public DefaultLayer() {
        super(LayerType.TEMP_DRAW);
    }

    public DefaultLayer(LayerType type) {
        super(type);
    }

}
