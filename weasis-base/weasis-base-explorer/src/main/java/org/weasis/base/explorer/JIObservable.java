package org.weasis.base.explorer;

public interface JIObservable {

    public static final String SECTION_CHANGED = "SECTION_CHANGED";
    public static final String DIRECTORY_SIZE = "DIRECTORY_SIZE";

    public void notifyObservers(Object arg);

    public boolean hasChanged();

}
