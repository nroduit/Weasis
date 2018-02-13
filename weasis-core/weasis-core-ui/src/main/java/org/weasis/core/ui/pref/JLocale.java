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
package org.weasis.core.ui.pref;

import java.util.Locale;
import java.util.Objects;

public class JLocale {
    private final Locale locale;

    JLocale(Locale l) {
        Objects.nonNull(l);
        locale = l;
    }

    @Override
    public String toString() {
        return locale.getDisplayName();
    }

    public Locale getLocale() {
        return locale;
    }
}
