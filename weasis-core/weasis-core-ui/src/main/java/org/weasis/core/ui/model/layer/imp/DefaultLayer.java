/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.model.layer.imp;

import jakarta.xml.bind.annotation.XmlRootElement;
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

  public DefaultLayer() {
    super(LayerType.TEMP_DRAW);
  }

  public DefaultLayer(LayerType type) {
    super(type);
  }
}
