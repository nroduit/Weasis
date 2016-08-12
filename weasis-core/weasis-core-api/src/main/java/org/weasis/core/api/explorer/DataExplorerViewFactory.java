/*******************************************************************************
 * Copyright (c) 2016 Weasis Team and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Nicolas Roduit - initial API and implementation
 *******************************************************************************/

package org.weasis.core.api.explorer;

import java.util.Hashtable;

@FunctionalInterface
public interface DataExplorerViewFactory {

    /**
     * Creates a new DataExplorerView object. It should be a unique instance.
     *
     * @param properties the properties. Can be null.
     * @return the explorer view
     */
    DataExplorerView createDataExplorerView(Hashtable<String, Object> properties);

}
