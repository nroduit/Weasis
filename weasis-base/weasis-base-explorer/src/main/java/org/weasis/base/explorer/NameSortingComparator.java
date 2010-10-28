package org.weasis.base.explorer;

import java.io.File;
import java.util.Comparator;

public final class NameSortingComparator<T> implements Comparator<File> {

    public int compare(final File a, final File b) {
        final String stra = a.getName();
        final String strb = b.getName();
        return stra.compareToIgnoreCase(strb);
    }
}
