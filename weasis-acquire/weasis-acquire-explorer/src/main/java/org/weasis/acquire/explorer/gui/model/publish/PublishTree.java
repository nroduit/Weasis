package org.weasis.acquire.explorer.gui.model.publish;

import javax.swing.JScrollPane;

import it.cnr.imaa.essi.lablib.gui.checkboxtree.CheckboxTree;
import it.cnr.imaa.essi.lablib.gui.checkboxtree.TreeCheckingListener;
import it.cnr.imaa.essi.lablib.gui.checkboxtree.TreeCheckingModel;

public class PublishTree extends JScrollPane {
    private static final long serialVersionUID = -353604550595956677L;

    private final CheckboxTree checkboxTree;
    private final PublishTreeModel publishTreeModel;

    public PublishTree() {
        this.publishTreeModel = new PublishTreeModel();
        this.checkboxTree = new PublishCheckboxTree(publishTreeModel);

        // Register tooltips
        checkboxTree.setToolTipText(""); //$NON-NLS-1$

        /**
         * At this point checking Paths are supposed to be binded at Series Level but depending on the CheckingMode it
         * may also contains parents treeNode paths.<br>
         * For medical use recommendation is to default select the whole series related to studies to be analyzed
         */

        TreeCheckingModel checkingModel = publishTreeModel.getCheckingModel();
        checkboxTree.setCheckingModel(checkingModel); // be aware that checkingPaths is cleared at this point

        setViewportView(checkboxTree);
    }

    public CheckboxTree getTree() {
        return checkboxTree;
    }

    public PublishTreeModel getModel() {
        return publishTreeModel;
    }

    public void addTreeCheckingListener(TreeCheckingListener tsl) {
        checkboxTree.addTreeCheckingListener(tsl);
    }
}