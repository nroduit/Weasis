/*******************************************************************************
 * Copyright (c) 2010 Nicolas Roduit.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse  License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     Nicolas Roduit - initial API and implementation
 ******************************************************************************/
package org.weasis.core.api.media.data;

import java.util.Comparator;

public interface MediaSeriesGroup {

    TagW getTagID();

    @Override
    boolean equals(Object obj);

    void setTag(TagW tag, Object value);

    boolean containTagKey(TagW tag);

    Object getTagValue(TagW tag);

    TagW getTagElement(int id);

    void dispose();

    void setComparator(Comparator<TagW> comparator);

    Comparator<TagW> getComparator();

    void setTagNoNull(TagW tag, Object value);
}
