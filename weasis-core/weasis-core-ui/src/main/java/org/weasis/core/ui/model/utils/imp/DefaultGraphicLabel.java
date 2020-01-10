/*******************************************************************************
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.weasis.core.ui.model.utils.imp;

import org.weasis.core.ui.model.graphic.AbstractGraphicLabel;
import org.weasis.core.ui.model.graphic.GraphicLabel;

public class DefaultGraphicLabel extends AbstractGraphicLabel {
    public DefaultGraphicLabel() {
        super();
    }

    public DefaultGraphicLabel(DefaultGraphicLabel object) {
        super(object);
    }

    @Override
    public GraphicLabel copy() {
        return new DefaultGraphicLabel(this);
    }

}
