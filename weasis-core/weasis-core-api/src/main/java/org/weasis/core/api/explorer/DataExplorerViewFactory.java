/*******************************************************************************
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/

package org.weasis.core.api.explorer;

import java.util.Hashtable;

@FunctionalInterface
public interface DataExplorerViewFactory {

    /**
     * Creates a new DataExplorerView object. It should be a unique instance.
     *
     * @param properties
     *            the properties. Can be null.
     * @return the explorer view
     */
    DataExplorerView createDataExplorerView(Hashtable<String, Object> properties);

}
