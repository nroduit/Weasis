/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.qr;

import eu.essilab.lablib.checkboxtree.CheckboxTree;
import java.awt.BorderLayout;
import java.awt.Component;
import java.util.Objects;
import javax.swing.JPanel;
import org.weasis.dicom.explorer.DicomModel;
import org.weasis.dicom.explorer.ExportTree;

public class RetrieveTree extends JPanel {

  private RetrieveTreeModel retrieveTreeModel;

  public RetrieveTree() {
    this(new RetrieveTreeModel());
  }

  public RetrieveTree(DicomModel dicomModel) {
    this(new RetrieveTreeModel(dicomModel));
  }

  public RetrieveTree(RetrieveTreeModel retrieveTreeModel) {
    this.setLayout(new BorderLayout());
    setRetrieveTreeModel(retrieveTreeModel);
  }

  public RetrieveTreeModel getRetrieveTreeModel() {
    return retrieveTreeModel;
  }

  public void setRetrieveTreeModel(RetrieveTreeModel retrieveTreeModel) {
    this.retrieveTreeModel = Objects.requireNonNull(retrieveTreeModel);
    CheckboxTree checkboxTree = ExportTree.buildCheckboxTree(retrieveTreeModel);
    ExportTree.initTree(retrieveTreeModel, checkboxTree);
    removeAll();
    add(checkboxTree, BorderLayout.CENTER);
  }

  public CheckboxTree getCheckboxTree() {
    for (int i = 0; i < getComponentCount(); i++) {
      Component c = getComponent(i);
      if (c instanceof CheckboxTree tree) {
        return tree;
      }
    }
    throw new IllegalStateException("CheckboxTree cannot be null");
  }
}
