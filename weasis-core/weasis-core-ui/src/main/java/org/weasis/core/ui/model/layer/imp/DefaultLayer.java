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
package org.weasis.core.ui.model.layer.imp;

import javax.xml.bind.annotation.XmlRootElement;

import org.weasis.core.ui.model.layer.AbstractGraphicLayer;
import org.weasis.core.ui.model.layer.LayerType;

/**
 * The Class DefaultLayer.
 *
 * @author Nicolas Roduit, Benoit Jacquemoud
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
