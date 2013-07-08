package org.weasis.core.ui.docking;

import java.util.Hashtable;

import org.weasis.core.ui.docking.Insertable.Type;

public interface InsertableFactory {

    Insertable createInstance(Hashtable<String, Object> properties);

    void dispose(Insertable component);

    boolean isComponentCreatedByThisFactory(Insertable component);

    Type getType();
}
