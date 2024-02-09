/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.acquire.explorer.gui.model.publish;

import eu.essilab.lablib.checkboxtree.CheckboxTree;
import eu.essilab.lablib.checkboxtree.TreeCheckingListener;
import eu.essilab.lablib.checkboxtree.TreeCheckingModel;
import javax.swing.JScrollPane;
import org.weasis.core.ui.util.TreeBuilder;

public class PublishTree extends JScrollPane {

  private final CheckboxTree checkboxTree;
  private final PublishTreeModel publishTreeModel;

  public PublishTree() {
    this.publishTreeModel = new PublishTreeModel();
    checkboxTree = new CheckboxTree(publishTreeModel.getModel());
    TreeCheckingModel checkingModel = publishTreeModel.getCheckingModel();
    checkboxTree.setCheckingModel(
        checkingModel); // be aware that checkingPaths is cleared at this point
    checkboxTree.setCellRenderer(TreeBuilder.buildNoIconCheckboxTreeCellRenderer());
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
