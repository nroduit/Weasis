/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
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
