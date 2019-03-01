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
import java.beans.PropertyChangeSupport;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import javax.swing.SwingUtilities;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.command.Option;
import org.weasis.core.api.command.Options;
import org.weasis.core.api.explorer.ObservableEvent;
import org.weasis.core.api.gui.util.GuiExecutor;
import org.weasis.core.api.media.data.Codec;
import org.weasis.core.api.media.data.MediaElement;
import org.weasis.core.api.media.data.MediaSeriesGroup;
import org.weasis.core.api.media.data.MediaSeriesGroupNode;
import org.weasis.core.api.media.data.Series;
import org.weasis.core.api.media.data.TagView;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.api.service.BundleTools;

public abstract class AbstractFileModel implements TreeModel, DataExplorerModel {
    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractFileModel.class);

    public static final List<String> functions = Collections.unmodifiableList(Arrays.asList("get", "close")); //$NON-NLS-1$ //$NON-NLS-2$

    public static final String NAME = "All Files"; //$NON-NLS-1$
    public static final TreeModelNode group =
        new TreeModelNode(1, 0, TagW.Group, new TagView(TagW.Group, TagW.FileName));
    public static final TreeModelNode series =
        new TreeModelNode(2, 0, TagW.SubseriesInstanceUID, new TagView(TagW.FileName));

    private static final List<TreeModelNode> modelStrucure = Arrays.asList(TreeModelNode.ROOT, group, series);

    private final Tree<MediaSeriesGroup> model;
    private PropertyChangeSupport propertyChange = null;

    public AbstractFileModel() {
        model = new Tree<>(MediaSeriesGroupNode.rootNode);
    }

    @Override
    public List<Codec> getCodecPlugins() {
        return BundleTools.CODEC_PLUGINS;
    }

    @Override
    public Collection<MediaSeriesGroup> getChildren(MediaSeriesGroup node) {
        return model.getSuccessors(node);
    }

    @Override
    public MediaSeriesGroup getHierarchyNode(MediaSeriesGroup parent, Object value) {
        if (parent != null || value != null) {
            for (MediaSeriesGroup node : model.getSuccessors(parent)) {
                if (node.matchIdValue(value)) {
                    return node;
                }
            }
        }
        return null;
    }

    @Override
    public void addHierarchyNode(MediaSeriesGroup root, MediaSeriesGroup leaf) {
        model.addLeaf(root, leaf);
    }

    @Override
    public void removeHierarchyNode(MediaSeriesGroup root, MediaSeriesGroup leaf) {
        Tree<MediaSeriesGroup> tree = model.getTree(root);
        if (tree != null) {
            tree.removeLeaf(leaf);
        }
    }

    @Override
    public MediaSeriesGroup getParent(MediaSeriesGroup node, TreeModelNode modelNode) {
        if (null != node && modelNode != null) {
            if (node.getTagID().equals(modelNode.getTagElement())) {
                return node;
            }
            Tree<MediaSeriesGroup> tree = model.getTree(node);
            if (tree != null) {
                Tree<MediaSeriesGroup> parent;
                while ((parent = tree.getParent()) != null) {
                    if (parent.getHead().getTagID().equals(modelNode.getTagElement())) {
                        return parent.getHead();
                    }
                    tree = parent;
                }
            }
        }
        return null;
    }

    public void dispose() {
        for (Iterator<MediaSeriesGroup> iterator = this.getChildren(MediaSeriesGroupNode.rootNode).iterator(); iterator
            .hasNext();) {
            iterator.next().dispose();
        }
        model.clear();
    }

    @Override
    public String toString() {
        return NAME;
    }

    @Override
    public List<TreeModelNode> getModelStructure() {
        return modelStrucure;
    }

    @Override
    public void addPropertyChangeListener(PropertyChangeListener propertychangelistener) {
        if (propertyChange == null) {
            propertyChange = new PropertyChangeSupport(this);
        }
        propertyChange.addPropertyChangeListener(propertychangelistener);
    }

    @Override
    public void removePropertyChangeListener(PropertyChangeListener propertychangelistener) {
        if (propertyChange != null) {
            propertyChange.removePropertyChangeListener(propertychangelistener);
        }

    }

    @Override
    public void firePropertyChange(final ObservableEvent event) {
        if (propertyChange != null) {
            if (event == null) {
                throw new NullPointerException();
            }
            if (SwingUtilities.isEventDispatchThread()) {
                propertyChange.firePropertyChange(event);
            } else {
                SwingUtilities.invokeLater(() -> propertyChange.firePropertyChange(event));
            }
        }
    }

    public void removeTopGroup(MediaSeriesGroup topGroup) {
        if (topGroup != null) {
            firePropertyChange(
                new ObservableEvent(ObservableEvent.BasicAction.REMOVE, AbstractFileModel.this, null, topGroup));
            Collection<MediaSeriesGroup> seriesList = getChildren(topGroup);
            for (Iterator<MediaSeriesGroup> it = seriesList.iterator(); it.hasNext();) {
                it.next().dispose();
            }
            removeHierarchyNode(MediaSeriesGroupNode.rootNode, topGroup);
            LOGGER.info("Remove Group: {}", topGroup); //$NON-NLS-1$
        }
    }

    public void removeSeries(MediaSeriesGroup seriesGroup) {
        if (seriesGroup != null) {
            // remove first series in UI (Viewer using this series)
            firePropertyChange(
                new ObservableEvent(ObservableEvent.BasicAction.REMOVE, AbstractFileModel.this, null, seriesGroup));
            // remove in the data model
            MediaSeriesGroup topGroup = getParent(seriesGroup, AbstractFileModel.group);
            removeHierarchyNode(topGroup, seriesGroup);
            seriesGroup.dispose();
            LOGGER.info("Remove Series/Image: {}", seriesGroup); //$NON-NLS-1$
        }
    }

    @Override
    public boolean applySplittingRules(Series original, MediaElement media) {
        return false;
    }

    public abstract void get(String[] argv) throws IOException;

    public void close(String[] argv) throws IOException {
        final String[] usage = { "Close images", //$NON-NLS-1$
            "Usage: dicom:close (-a | ([-g UID]... [-s UID]...)) ", //$NON-NLS-1$
            "  -a --all         close all series", //$NON-NLS-1$
            "  -g --group=UID   close a group from its UID", "  -s --series=UID   close an series/image from its UID", //$NON-NLS-1$ //$NON-NLS-2$
            "  -? --help        show help" }; //$NON-NLS-1$
        final Option opt = Options.compile(usage).parse(argv);
        final List<String> gargs = opt.getList("group"); //$NON-NLS-1$
        final List<String> iargs = opt.getList("series"); //$NON-NLS-1$

        if (opt.isSet("help") || (gargs.isEmpty() && iargs.isEmpty() && !opt.isSet("all"))) { //$NON-NLS-1$ //$NON-NLS-2$
            opt.usage();
            return;
        }

        GuiExecutor.instance().execute(() -> {
            AbstractFileModel dataModel = AbstractFileModel.this;
            firePropertyChange(new ObservableEvent(ObservableEvent.BasicAction.SELECT, dataModel, null, dataModel));
            if (opt.isSet("all")) { //$NON-NLS-1$
                for (MediaSeriesGroup g : model.getSuccessors(MediaSeriesGroupNode.rootNode)) {
                    dataModel.removeTopGroup(g);
                }
            } else {
                if (opt.isSet("group")) { //$NON-NLS-1$

                    for (String gUID : gargs) {
                        dataModel.removeTopGroup(getHierarchyNode(MediaSeriesGroupNode.rootNode, gUID));
                    }
                }

                if (opt.isSet("series")) { //$NON-NLS-1$
                    for (String uid : iargs) {
                        for (MediaSeriesGroup topGroup : model.getSuccessors(MediaSeriesGroupNode.rootNode)) {
                            MediaSeriesGroup s = getHierarchyNode(topGroup, uid);
                            if (s != null) {
                                removeSeries(s);
                                break;
                            }
                        }
                    }
                }
            }
        });
    }

    @Override
    public TreeModelNode getTreeModelNodeForNewPlugin() {
        return group;
    }

}
