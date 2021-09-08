/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.model.layer;

import jakarta.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

@XmlJavaTypeAdapter(AbstractGraphicLayer.Adapter.class)
public interface GraphicLayer extends Layer {

  Boolean getLocked();

  void setLocked(Boolean locked);

  Boolean getSelectable();

  void setSelectable(Boolean selectable);

  Boolean getSerializable();

  void setSerializable(Boolean serializable);
}
