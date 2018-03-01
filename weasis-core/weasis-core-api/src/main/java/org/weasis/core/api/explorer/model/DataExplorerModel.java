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
package org.weasis.core.api.explorer.model;

import java.beans.PropertyChangeListener;
import java.util.List;

import org.weasis.core.api.explorer.ObservableEvent;
import org.weasis.core.api.media.data.Codec;
import org.weasis.core.api.media.data.MediaElement;
import org.weasis.core.api.media.data.Series;

public interface DataExplorerModel {

    List<Codec> getCodecPlugins();

    void addPropertyChangeListener(PropertyChangeListener propertychangelistener);

    void removePropertyChangeListener(PropertyChangeListener propertychangelistener);

    void firePropertyChange(ObservableEvent event);

    TreeModelNode getTreeModelNodeForNewPlugin();

    boolean applySplittingRules(Series<?> original, MediaElement media);

}
