/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.acquire.explorer.util;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import javax.swing.event.SwingPropertyChangeSupport;

/**
 * An encapsulation of the PropertyChangeSupport methods based on java.beans.PropertyChangeSupport.
 * <br>
 * PropertyChangeListeners are fired on the event dispatching thread.
 */
public abstract class AbstractBean<E extends Enum<E>> {

  private final PropertyChangeSupport pcs;

  protected AbstractBean() {
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
