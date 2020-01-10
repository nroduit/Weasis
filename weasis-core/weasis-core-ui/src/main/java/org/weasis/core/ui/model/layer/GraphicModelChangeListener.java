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

import org.weasis.core.ui.model.GraphicModel;

/**
 * The listener interface for receiving layerModelChange events.
 */
public interface GraphicModelChangeListener {

    default void handleModelChanged(GraphicModel modelList) {
    }

    default void handleLayerAdded(GraphicModel modelList, Layer layer) {
    }

    default void handleLayerRemoved(GraphicModel modelList, Layer layer) {
    }

    default void handleLayerChanged(GraphicModel modelList, Layer layer) {
    }
}
