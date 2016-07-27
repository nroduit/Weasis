package org.weasis.acquire.explorer.gui.model.publish;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;

import org.weasis.acquire.explorer.AcquireImageInfo;
import org.weasis.acquire.explorer.AcquireManager;
import org.weasis.acquire.explorer.core.bean.Serie;

import it.cnr.imaa.essi.lablib.gui.checkboxtree.DefaultTreeCheckingModel;
import it.cnr.imaa.essi.lablib.gui.checkboxtree.TreeCheckingModel;

public class PublishTreeModel {
    private final DefaultTreeModel model;
    private final TreeCheckingModel checkingModel;
    private final List<TreePath> defaultSelectedPaths;

    private final List<Serie> series;
    private final Map<Serie, List<AcquireImageInfo>> dictionary;

    DefaultMutableTreeNode rootNode, serieNode;

    public PublishTreeModel() {
        this.series = AcquireManager.getBySeries();
        this.dictionary = AcquireManager.groupBySeries();
        this.model = buildModel();
        this.rootNode = (DefaultMutableTreeNode) model.getRoot();
        this.checkingModel = new DefaultTreeCheckingModel(model);
        this.defaultSelectedPaths = Collections.synchronizedList(new ArrayList<TreePath>());

    }

    private DefaultTreeModel buildModel() {
        rootNode = new DefaultMutableTreeNode(AcquireManager.GLOBAL);
        series.stream().forEach(serie -> {
            serieNode = new DefaultMutableTreeNode(serie);
            rootNode.add(serieNode);
            dictionary.get(serie).stream().forEach(createImageNode);
        });
        return new DefaultTreeModel(rootNode, false);
    }

    private Consumer<AcquireImageInfo> createImageNode = image -> {
        DefaultMutableTreeNode node = new DefaultMutableTreeNode(image);
        serieNode.add(node);
    };

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
