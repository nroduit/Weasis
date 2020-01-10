/*******************************************************************************
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.weasis.base.explorer;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.List;

import javax.swing.SwingUtilities;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;

import org.weasis.core.api.explorer.ObservableEvent;
import org.weasis.core.api.explorer.model.DataExplorerModel;
import org.weasis.core.api.explorer.model.TreeModelNode;
import org.weasis.core.api.media.data.Codec;
import org.weasis.core.api.media.data.MediaElement;
import org.weasis.core.api.media.data.Series;
import org.weasis.core.api.service.BundleTools;

public class FileTreeModel extends DefaultTreeModel implements DataExplorerModel {
    private static final long serialVersionUID = 5866861109081440236L;

    private final PropertyChangeSupport propertyChange;

    public FileTreeModel(TreeNode root) {
        this(root, false);

    }

    public FileTreeModel(TreeNode root, boolean asksAllowsChildren) {
        super(root, asksAllowsChildren);
        propertyChange = new PropertyChangeSupport(this);
    }

    @Override
    public void addPropertyChangeListener(PropertyChangeListener propertychangelistener) {
        propertyChange.addPropertyChangeListener(propertychangelistener);
    }

    @Override
    public void removePropertyChangeListener(PropertyChangeListener propertychangelistener) {
        propertyChange.removePropertyChangeListener(propertychangelistener);

    }

    @Override
    public void firePropertyChange(final ObservableEvent event) {
        if (event == null) {
            throw new NullPointerException();
        }
        if (SwingUtilities.isEventDispatchThread()) {
            propertyChange.firePropertyChange(event);
        } else {
            SwingUtilities.invokeLater(() -> propertyChange.firePropertyChange(event));
        }
    }

    @Override
    public List<Codec> getCodecPlugins() {
        return BundleTools.CODEC_PLUGINS;
    }

    @Override
    public TreeModelNode getTreeModelNodeForNewPlugin() {
        return null;
    }

    @Override
    public boolean applySplittingRules(Series original, MediaElement media) {
        return false;
    }

}
