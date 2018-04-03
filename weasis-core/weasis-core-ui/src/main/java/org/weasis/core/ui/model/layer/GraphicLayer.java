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

@XmlJavaTypeAdapter(AbstractGraphicLayer.Adapter.class)
public interface GraphicLayer extends Layer {

    Boolean getLocked();

    void setLocked(Boolean locked);

    Boolean getSelectable();

    void setSelectable(Boolean selectable);

    Boolean getSerializable();

    void setSerializable(Boolean serializable);

}
