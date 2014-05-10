package org.weasis.core.ui.pref;

import java.util.Locale;

public class JLocale {
    private final Locale locale;

    JLocale(Locale l) {
        if (l == null) {
            throw new IllegalArgumentException("locale cannot be null"); //$NON-NLS-1$
        }
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
