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
package org.weasis.acquire.explorer.util;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;

import javax.swing.event.SwingPropertyChangeSupport;

/**
 * An encapsulation of the PropertyChangeSupport methods based on java.beans.PropertyChangeSupport.<br>
 * PropertyChangeListeners are fired on the event dispatching thread.
 *
 */

public abstract class AbstractBean<E extends Enum<E>> {

    private final PropertyChangeSupport pcs;

    public AbstractBean() {
        pcs = new SwingPropertyChangeSupport(this, true);
    }

    public void addPropertyChangeListener(PropertyChangeListener listener) {
        pcs.addPropertyChangeListener(listener);
    }

    public void addPropertyChangeListener(E property, PropertyChangeListener listener) {
        pcs.addPropertyChangeListener(property.name(), listener);
    }

    public void removePropertyChangeListener(PropertyChangeListener listener) {
        pcs.removePropertyChangeListener(listener);
    }

    public void removePropertyChangeListener(E property, PropertyChangeListener listener) {
        pcs.removePropertyChangeListener(property.name(), listener);
    }

    public PropertyChangeListener[] getPropertyChangeListeners() {
        return pcs.getPropertyChangeListeners();
    }

    public PropertyChangeListener[] getPropertyChangeListeners(E property) {
        return pcs.getPropertyChangeListeners(property.name());
    }

    public Boolean hasListener(E property) {
        return pcs.hasListeners(property.name());
    }

    protected void firePropertyChange(E property, Object oldValue, Object newValue) {
        pcs.firePropertyChange(property.name(), oldValue, newValue);
    }

    protected void firePropertyChange(E property) {
        pcs.firePropertyChange(property.name(), null, null);
    }
}
