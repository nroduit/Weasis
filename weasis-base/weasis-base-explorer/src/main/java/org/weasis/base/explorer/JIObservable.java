package org.weasis.base.explorer;

interface JIObservable {

    String SECTION_CHANGED = "SECTION_CHANGED";
    String DIRECTORY_SIZE = "DIRECTORY_SIZE";

    void notifyObservers(Object arg);

    boolean hasChanged();

}
