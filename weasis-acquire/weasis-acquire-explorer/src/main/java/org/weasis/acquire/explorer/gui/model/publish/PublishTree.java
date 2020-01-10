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
        checkboxTree = new CheckboxTree(publishTreeModel.getModel());
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