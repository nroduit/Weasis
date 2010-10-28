/*******************************************************************************
 * Copyright (c) 2010 Nicolas Roduit.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     Nicolas Roduit - initial API and implementation
 ******************************************************************************/
package org.weasis.core.api.media.data;

import java.util.Comparator;

public interface MediaSeriesGroup {

    public TagElement getTagID();

    public boolean equals(Object obj);

    public void setTag(TagElement tag, Object value);

    public boolean containTagKey(TagElement tag);

    public Object getTagValue(TagElement tag);

    public TagElement getTagElement(int id);

    public void dispose();

    public void setComparator(Comparator<TagElement> comparator);

    public Comparator<TagElement> getComparator();
}
