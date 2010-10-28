/*******************************************************************************
 * Copyright (c) 2010 Nicolas Roduit.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     Nicolas Roduit - initial API and implementation
 ******************************************************************************/
package org.weasis.core.api.explorer.model;

import java.beans.PropertyChangeListener;
import java.util.List;

import org.weasis.core.api.explorer.ObservableEvent;
import org.weasis.core.api.media.data.Codec;

public interface DataExplorerModel {

    public List<Codec> getCodecPlugins();

    public void addPropertyChangeListener(PropertyChangeListener propertychangelistener);

    public void removePropertyChangeListener(PropertyChangeListener propertychangelistener);

    public void firePropertyChange(ObservableEvent event);

}
