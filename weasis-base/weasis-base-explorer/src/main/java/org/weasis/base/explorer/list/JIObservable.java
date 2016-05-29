package org.weasis.base.explorer.list;

public interface JIObservable {

    String SECTION_CHANGED = "SECTION_CHANGED"; //$NON-NLS-1$
    String DIRECTORY_SIZE = "DIRECTORY_SIZE"; //$NON-NLS-1$

    void notifyObservers(Object arg);

    boolean hasChanged();

}
