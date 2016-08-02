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
