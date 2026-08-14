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
import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeExpansionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import org.weasis.core.api.media.data.MediaSeriesGroup;
import org.weasis.dicom.explorer.DicomModel;
import org.weasis.dicom.explorer.exp.ExportTree;

public class RetrieveTree extends JPanel {

  /** Queries the series of a study, on expansion of its node in the tree. */
  @FunctionalInterface
  public interface SeriesLoader {
    void loadSeries(RetrieveTreeModel treeModel, DefaultMutableTreeNode studyNode);
  }

  private RetrieveTreeModel retrieveTreeModel;
  private SeriesLoader seriesLoader;

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

  /** Registers the query run when a study is expanded for the first time. */
  public void setSeriesLoader(SeriesLoader seriesLoader) {
    this.seriesLoader = seriesLoader;
  }

  public void setRetrieveTreeModel(RetrieveTreeModel retrieveTreeModel) {
    this.retrieveTreeModel = Objects.requireNonNull(retrieveTreeModel);
    CheckboxTree checkboxTree = ExportTree.buildCheckboxTree(retrieveTreeModel);
    ExportTree.initTree(retrieveTreeModel, checkboxTree);
    checkboxTree.addTreeExpansionListener(new SeriesExpansionListener());
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

  private class SeriesExpansionListener implements TreeExpansionListener {

    @Override
    public void treeExpanded(TreeExpansionEvent event) {
      // A study sits at the third level: root, patient, study
      if (seriesLoader == null || event.getPath().getPathCount() != 3) {
        return;
      }
      if (event.getPath().getLastPathComponent() instanceof DefaultMutableTreeNode studyNode
          && studyNode.getUserObject() instanceof MediaSeriesGroup
          && RetrieveTreeModel.hasPlaceholder(studyNode)) {
        seriesLoader.loadSeries(retrieveTreeModel, studyNode);
      }
    }

    @Override
    public void treeCollapsed(TreeExpansionEvent event) {
      // Series already queried are kept, so that the checked ones survive a collapse
    }
  }
}
