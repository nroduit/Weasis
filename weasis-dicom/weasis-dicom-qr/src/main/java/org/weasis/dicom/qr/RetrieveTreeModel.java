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

import it.cnr.imaa.essi.lablib.gui.checkboxtree.DefaultTreeCheckingModel;
import it.cnr.imaa.essi.lablib.gui.checkboxtree.TreeCheckingModel;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.media.data.MediaSeriesGroup;
import org.weasis.core.api.media.data.MediaSeriesGroupNode;
import org.weasis.core.api.media.data.Series;
import org.weasis.core.api.media.data.TagReadable;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.util.StringUtil;
import org.weasis.dicom.explorer.CheckTreeModel;
import org.weasis.dicom.explorer.DicomExplorer;
import org.weasis.dicom.explorer.DicomModel;
import org.weasis.dicom.explorer.DicomSorter;

public class RetrieveTreeModel {

  private final DefaultMutableTreeNode rootNode;
  private final DefaultTreeModel model;
  private final TreeCheckingModel checkingModel;
  private final List<TreePath> defaultSelectedPaths;
  private final DicomModel dicomModel;

  public RetrieveTreeModel() {
    this(null);
  }

  public RetrieveTreeModel(DicomModel dicomModel) {
    this.dicomModel = dicomModel == null ? new DicomModel() : dicomModel;
    this.model = buildModel(this.dicomModel);
    this.rootNode = (DefaultMutableTreeNode) model.getRoot();
    this.checkingModel = new DefaultTreeCheckingModel(model);
    this.defaultSelectedPaths = Collections.synchronizedList(new ArrayList<>());
  }

  public DicomModel getDicomModel() {
    return dicomModel;
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

  private static void buildSeries(DefaultMutableTreeNode studyNode, Series<?> series) {
    DefaultMutableTreeNode seriesNode = new ToolTipSeriesNode(series, true);

    List<?> children = Collections.list(studyNode.children());
    int index = Collections.binarySearch(children, seriesNode, DicomSorter.SERIES_COMPARATOR);
    index = index < 0 ? -(index + 1) : index;
    studyNode.insert(seriesNode, index);
  }

  public static synchronized DefaultTreeModel buildModel(DicomModel dicomModel) {
    Collection<MediaSeriesGroup> patients = dicomModel.getChildren(MediaSeriesGroupNode.rootNode);
    DefaultMutableTreeNode rootNode =
        new DefaultMutableTreeNode(
            patients.isEmpty()
                ? Messages.getString("RetrieveTreeModel.no_pat")
                : DicomExplorer.ALL_PATIENTS);
    for (MediaSeriesGroup pt : patients) {
      DefaultMutableTreeNode patientNode = new DefaultMutableTreeNode(pt, true);
      for (MediaSeriesGroup study : dicomModel.getChildren(pt)) {
        DefaultMutableTreeNode studyNode = new ToolTipStudyNode(study, true);
        for (MediaSeriesGroup item : dicomModel.getChildren(study)) {
          if (item instanceof Series) {
            buildSeries(studyNode, (Series<?>) item);
          }
        }
        List<?> children = Collections.list(patientNode.children());
        int index = Collections.binarySearch(children, studyNode, DicomSorter.STUDY_COMPARATOR);
        if (index < 0) {
          patientNode.insert(studyNode, -(index + 1));
        } else {
          patientNode.insert(studyNode, index);
        }
      }
      List<?> children = Collections.list(rootNode.children());
      int index = Collections.binarySearch(children, patientNode, DicomSorter.PATIENT_COMPARATOR);
      if (index < 0) {
        rootNode.insert(patientNode, -(index + 1));
      } else {
        rootNode.insert(patientNode, index);
      }
    }
    return new DefaultTreeModel(rootNode, false);
  }

  static class ToolTipStudyNode extends DefaultMutableTreeNode {

    public ToolTipStudyNode(TagReadable userObject, boolean allowsChildren) {
      super(Objects.requireNonNull(userObject), allowsChildren);
    }

    public String getToolTipText() {
      TagReadable s = (TagReadable) getUserObject();
      StringBuilder toolTips = new StringBuilder();
      toolTips.append(GuiUtils.HTML_START);
      s.getTagEntrySetIterator()
          .forEachRemaining(
              i -> {
                TagW tag = i.getKey();
                toolTips.append("<b>");
                toolTips.append(tag.getDisplayedName());
                toolTips.append("</b>");
                toolTips.append(StringUtil.COLON_AND_SPACE);
                toolTips.append(tag.getFormattedTagValue(i.getValue(), null));
                toolTips.append(GuiUtils.HTML_BR);
              });
      toolTips.append(GuiUtils.HTML_END);
      return toolTips.toString();
    }
  }

  static class ToolTipSeriesNode extends DefaultMutableTreeNode {

    public ToolTipSeriesNode(TagReadable userObject, boolean allowsChildren) {
      super(Objects.requireNonNull(userObject), allowsChildren);
    }

    public String getToolTipText() {
      return CheckTreeModel.buildToolTipText((TagReadable) getUserObject());
    }

    @Override
    public String toString() {
      MediaSeries<?> s = (MediaSeries<?>) getUserObject();
      StringBuilder buf = new StringBuilder();
      CheckTreeModel.buildSeriesEntry(s, buf);
      return buf.toString();
    }
  }
}
