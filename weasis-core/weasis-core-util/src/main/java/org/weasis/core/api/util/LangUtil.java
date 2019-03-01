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
package org.weasis.core.api.util;

import java.util.Collection;
import java.util.Collections;

public class LangUtil {
    
    private LangUtil() {
    }

    public static <T> Iterable<T> emptyIfNull(Iterable<T> iterable) {
        return iterable == null ? Collections.<T> emptyList() : iterable;
    }

    public static boolean getNULLtoFalse(Boolean val) {
        if (val != null) {
            return val.booleanValue();
        }
        return false;
    }

    public static boolean getNULLtoTrue(Boolean val) {
        if (val != null) {
            return val.booleanValue();
        }
        return true;
    }

    public static boolean getEmptytoFalse(String val) {
        if (StringUtil.hasText(val)) {
            return getBoolean(val);
        }
        return false;
    }

    public static boolean geEmptytoTrue(String val) {
        if (StringUtil.hasText(val)) {
            return getBoolean(val);
        }
        return true;
    }

    private static boolean getBoolean(String val) {
        return "true".equalsIgnoreCase(val); //$NON-NLS-1$
    }

    public static <T, C extends Collection<T>> C convertCollectionType(Iterable<?> from, C newCollection,
        Class<T> listClass) {
        for (Object item : from) {
            newCollection.add(listClass.cast(item));
        }
        return newCollection;
    }
}
