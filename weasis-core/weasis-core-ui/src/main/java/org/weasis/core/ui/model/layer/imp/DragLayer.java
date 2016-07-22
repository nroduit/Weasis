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
package org.weasis.core.ui.model.layer.imp;

import javax.xml.bind.annotation.XmlRootElement;

import org.weasis.core.ui.model.layer.AbstractGraphicLayer;
import org.weasis.core.ui.model.layer.LayerType;

/**
 * The Class DragLayer.
 *
 * @author Nicolas Roduit, Benoit Jacquemoud
 */
@XmlRootElement(name = "dragLayer")
public class DragLayer extends AbstractGraphicLayer {

    private static final long serialVersionUID = 8576601524359423997L;

    public DragLayer() {
        super(LayerType.TEMP_DRAW);
    }

    public DragLayer(LayerType type) {
        super(type);
    }

}
