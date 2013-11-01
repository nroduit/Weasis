package org.weasis.core.api.gui;

import java.util.Hashtable;

import org.weasis.core.api.gui.Insertable.Type;

public interface InsertableFactory {

    Insertable createInstance(Hashtable<String, Object> properties);

    void dispose(Insertable component);

    boolean isComponentCreatedByThisFactory(Insertable component);

    Type getType();
}
