package org.weasis.base.explorer.list;

public interface JIObservable {

    void notifyObservers(Object arg);

    boolean hasChanged();

}
