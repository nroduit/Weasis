/*******************************************************************************
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.weasis.core.ui.model.utils;

import java.io.Serializable;

public interface UUIDable extends Serializable {

    /**
     * Return a Universally Unique IDentifier (UUID)
     *
     * @return UUID
     */
    String getUuid();

    /**
     * Set a Universally Unique IDentifier (UUID)
     *
     * @param uuid
     */
    void setUuid(String uuid);
}
