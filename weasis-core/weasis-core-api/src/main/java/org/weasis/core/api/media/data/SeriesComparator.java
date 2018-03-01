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
package org.weasis.core.api.media.data;

import java.util.Collections;
import java.util.Comparator;

public abstract class SeriesComparator<T> implements Comparator<T> {
    private Comparator<T> inverse;

    public final Comparator<T> getReversOrderComparator() {
        if (inverse == null) {
            inverse = Collections.reverseOrder(this);
        }
        return inverse;
    }
}
