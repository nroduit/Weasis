/*******************************************************************************
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.weasis.core.api.media.data;

import java.util.Iterator;
import java.util.Map.Entry;

public interface TagReadable {

    boolean containTagKey(TagW tag);

    Object getTagValue(TagW tag);

    Iterator<Entry<TagW, Object>> getTagEntrySetIterator();
}