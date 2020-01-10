/*******************************************************************************
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.weasis.acquire.explorer.gui.model.publish;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;

import org.weasis.acquire.explorer.AcquireImageInfo;
import org.weasis.acquire.explorer.AcquireManager;
import org.weasis.acquire.explorer.core.bean.SeriesGroup;

import it.cnr.imaa.essi.lablib.gui.checkboxtree.DefaultTreeCheckingModel;
import it.cnr.imaa.essi.lablib.gui.checkboxtree.TreeCheckingModel;

public class PublishTreeModel {
    private final DefaultTreeModel model;
    private final TreeCheckingModel checkingModel;
    private final List<TreePath> defaultSelectedPaths;

    private final List<SeriesGroup> seriesGroups;
    private final Map<SeriesGroup, List<AcquireImageInfo>> dictionary;

    DefaultMutableTreeNode rootNode, serieNode;

    public PublishTreeModel() {
        this.seriesGroups = AcquireManager.getBySeries();
        this.dictionary = AcquireManager.groupBySeries();
        this.model = buildModel();
        this.rootNode = (DefaultMutableTreeNode) model.getRoot();
        this.checkingModel = new DefaultTreeCheckingModel(model);
        this.defaultSelectedPaths = Collections.synchronizedList(new ArrayList<TreePath>());

    }

    private DefaultTreeModel buildModel() {
        rootNode = new DefaultMutableTreeNode(AcquireManager.GLOBAL);
        seriesGroups.stream().forEach(s -> {
            serieNode = new DefaultMutableTreeNode(s);
            rootNode.add(serieNode);
            dictionary.get(s).forEach(image -> serieNode.add(new DefaultMutableTreeNode(image)));
        });
        return new DefaultTreeModel(rootNode, false);
    }

    public DefaultMutableTreeNode getRootNode() {
        return rootNode;
    }

    public DefaultTreeModel getModel() {
        return model;
    }

    public TreeCheckingModel getCheckingModel() {
        return checkingModel;
    }

    public TreePath[] getCheckingPaths() {
        return checkingModel.getCheckingPaths();
    }

    public void setDefaultSelectionPaths(List<TreePath> selectedPaths) {
        defaultSelectedPaths.clear();
        defaultSelectedPaths.addAll(selectedPaths);
    }

    public List<TreePath> getDefaultSelectedPaths() {
        return defaultSelectedPaths;
    }
}
