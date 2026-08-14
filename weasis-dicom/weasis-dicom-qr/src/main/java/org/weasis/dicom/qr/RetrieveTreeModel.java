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

import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import org.dcm4che3.data.Tag;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.media.data.Series;
import org.weasis.dicom.codec.TagD;
import org.weasis.dicom.explorer.DicomModel;
import org.weasis.dicom.explorer.DicomSorter;
import org.weasis.dicom.explorer.exp.CheckTreeModel;

/**
 * Query result tree. A study level query returns no series, so each study without children gets a
 * placeholder child: it makes the study expandable and triggers the series query on expansion (see
 * {@link RetrieveTree}).
 */
public class RetrieveTreeModel extends CheckTreeModel {

  /** Placeholder standing for the series of a study that have not been queried yet. */
  public static class SeriesPlaceholderNode extends DefaultMutableTreeNode {
    private boolean loading;

    public SeriesPlaceholderNode() {
      super(Messages.getString("RetrieveTreeModel.expand_series"), false);
    }

    public boolean isLoading() {
      return loading;
    }

    void setLoading(boolean loading) {
      this.loading = loading;
      setUserObject(
          Messages.getString(
              loading ? "RetrieveTreeModel.loading_series" : "RetrieveTreeModel.expand_series"));
    }
  }

  /**
   * Series of a query result. It holds no instance, so the number of images is taken from the query
   * instead of the child count.
   */
  public static class QuerySeriesNode extends ToolTipSeriesNode {
    public QuerySeriesNode(Series<?> series) {
      super(series, true);
    }

    @Override
    public String toString() {
      MediaSeries<?> series = (MediaSeries<?>) getUserObject();
      StringBuilder buf = new StringBuilder();
      buildSeriesEntry(series, buf);
      Integer instances =
          TagD.getTagValue(series, Tag.NumberOfSeriesRelatedInstances, Integer.class);
      if (instances != null) {
        buf.append(" -- ").append(instances).append(" instance(s)"); // NON-NLS
      }
      return buf.toString();
    }
  }

  public RetrieveTreeModel() {
    this(null);
  }

  public RetrieveTreeModel(DicomModel dicomModel) {
    super(dicomModel);
  }

  @Override
  protected void buildSeries(DefaultMutableTreeNode studyNode, Series<?> series) {
    insertSeriesNode(studyNode, new QuerySeriesNode(series));
  }

  @Override
  protected synchronized DefaultTreeModel buildModel(DicomModel dicomModel) {
    DefaultTreeModel treeModel = super.buildModel(dicomModel);
    DefaultMutableTreeNode root = (DefaultMutableTreeNode) treeModel.getRoot();
    forEachStudyNode(
        root,
        studyNode -> {
          if (studyNode.getChildCount() == 0) {
            studyNode.add(new SeriesPlaceholderNode());
          }
        });
    return treeModel;
  }

  /** Tells whether the series of this study still have to be queried. */
  public static boolean hasPlaceholder(DefaultMutableTreeNode studyNode) {
    return studyNode.getChildCount() == 1
        && studyNode.getChildAt(0) instanceof SeriesPlaceholderNode placeholder
        && !placeholder.isLoading();
  }

  /** Flags the placeholder as queried, so collapsing and expanding again does not query twice. */
  public void setPlaceholderLoading(DefaultMutableTreeNode studyNode, boolean loading) {
    for (int i = 0; i < studyNode.getChildCount(); i++) {
      if (studyNode.getChildAt(i) instanceof SeriesPlaceholderNode placeholder) {
        placeholder.setLoading(loading);
        model.nodeChanged(placeholder);
      }
    }
  }

  /**
   * Replaces the placeholder of a study by the queried series. The insertion goes through the tree
   * model so the checking model propagates the state of the study to the new nodes.
   */
  public void setSeriesNodes(DefaultMutableTreeNode studyNode, List<Series<?>> seriesList) {
    // Inserted before the placeholder is dropped, otherwise the study would briefly have no child
    // and the tree would collapse it
    for (Series<?> series : seriesList) {
      DefaultMutableTreeNode seriesNode = new QuerySeriesNode(series);
      model.insertNodeInto(seriesNode, studyNode, insertionIndex(studyNode, seriesNode));
    }
    removePlaceholder(studyNode);
  }

  /** Drops the placeholder of a study whose series query returned nothing or failed. */
  public void removePlaceholder(DefaultMutableTreeNode studyNode) {
    for (int i = studyNode.getChildCount() - 1; i >= 0; i--) {
      if (studyNode.getChildAt(i) instanceof SeriesPlaceholderNode placeholder) {
        model.removeNodeFromParent(placeholder);
      }
    }
  }

  private static void insertSeriesNode(
      DefaultMutableTreeNode studyNode, DefaultMutableTreeNode seriesNode) {
    studyNode.insert(seriesNode, insertionIndex(studyNode, seriesNode));
  }

  /**
   * Sorted position among the series of the study. The placeholder is always the last child, so
   * this index also holds in the child list it is still part of.
   */
  private static int insertionIndex(
      DefaultMutableTreeNode studyNode, DefaultMutableTreeNode seriesNode) {
    List<?> series =
        Collections.list(studyNode.children()).stream()
            .filter(QuerySeriesNode.class::isInstance)
            .toList();
    int index = Collections.binarySearch(series, seriesNode, DicomSorter.SERIES_COMPARATOR);
    return index < 0 ? -(index + 1) : index;
  }

  /** Visits the study nodes, which sit at the third level of the tree. */
  private static void forEachStudyNode(
      DefaultMutableTreeNode root, Consumer<DefaultMutableTreeNode> action) {
    Enumeration<?> patients = root.children();
    while (patients.hasMoreElements()) {
      DefaultMutableTreeNode patient = (DefaultMutableTreeNode) patients.nextElement();
      Enumeration<?> studies = patient.children();
      while (studies.hasMoreElements()) {
        action.accept((DefaultMutableTreeNode) studies.nextElement());
      }
    }
  }
}
