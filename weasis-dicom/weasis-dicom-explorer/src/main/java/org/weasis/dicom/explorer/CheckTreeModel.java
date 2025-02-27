/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.explorer;

import eu.essilab.lablib.checkboxtree.DefaultTreeCheckingModel;
import eu.essilab.lablib.checkboxtree.TreeCheckingModel;
import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import org.dcm4che3.data.Tag;
import org.dcm4che3.util.UIDUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.gui.util.GuiUtils.IconColor;
import org.weasis.core.api.media.data.MediaElement;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.media.data.MediaSeriesGroup;
import org.weasis.core.api.media.data.MediaSeriesGroupNode;
import org.weasis.core.api.media.data.Series;
import org.weasis.core.api.media.data.TagReadable;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.api.media.data.TagW.TagType;
import org.weasis.core.api.media.data.Thumbnailable;
import org.weasis.core.ui.model.GraphicModel;
import org.weasis.core.util.LangUtil;
import org.weasis.core.util.StringUtil;
import org.weasis.dicom.codec.DcmMediaReader;
import org.weasis.dicom.codec.DicomSeries;
import org.weasis.dicom.codec.DicomSpecialElement;
import org.weasis.dicom.codec.TagD;

public class CheckTreeModel {
  private static final Logger LOGGER = LoggerFactory.getLogger(CheckTreeModel.class);

  public static final TagW SourceSeriesForPR = new TagW("SourceSeriesForPR", TagType.OBJECT);

  protected final DefaultMutableTreeNode rootNode;
  protected final DefaultTreeModel model;
  protected final TreeCheckingModel checkingModel;
  protected final List<TreePath> defaultSelectedPaths;
  protected final DicomModel dicomModel;

  public CheckTreeModel(DicomModel dicomModel) {
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

  protected void buildSeries(DefaultMutableTreeNode studyNode, Series<?> series) {
    DefaultMutableTreeNode seriesNode = new ToolTipSeriesNode(series, true);
    if (series instanceof DicomSeries dicomSeries) {
      List<DicomSpecialElement> specialElements = dicomSeries.getAllDicomSpecialElement();
      if (specialElements != null) {
        for (DicomSpecialElement specialElement : specialElements) {
          seriesNode.add(
              new DefaultMutableTreeNode(specialElement, false) {
                @Override
                public String toString() {
                  DicomSpecialElement d = (DicomSpecialElement) getUserObject();
                  StringBuilder buf = new StringBuilder();
                  boolean newElement = initToolTipText(d, buf);
                  buf.append(d.getShortLabel());
                  if (newElement) {
                    buf.append(GuiUtils.HTML_END);
                  }
                  return buf.toString();
                }
              });
        }
      }
    }

    boolean hasGraphics = false;
    synchronized (series) { // NOSONAR lock object is the list for iterating its elements safely
      for (MediaElement dicom : series.getMedias(null, null)) {
        seriesNode.add(
            new DefaultMutableTreeNode(dicom, false) {
              @Override
              public String toString() {
                MediaElement m = (MediaElement) getUserObject();
                Integer val = TagD.getTagValue(m, Tag.InstanceNumber, Integer.class);
                StringBuilder buf = new StringBuilder();
                if (val != null) {
                  buf.append("[");
                  buf.append(val);
                  buf.append("] ");
                }
                String sopUID = TagD.getTagValue(m, Tag.SOPInstanceUID, String.class);
                if (sopUID != null) {
                  buf.append(sopUID);
                }
                return buf.toString();
              }
            });

        if (!hasGraphics) {
          GraphicModel grModel = (GraphicModel) dicom.getTagValue(TagW.PresentationModel);
          hasGraphics = grModel != null && grModel.hasSerializableGraphics();
        }
      }
    }

    List<?> children = Collections.list(studyNode.children());
    int index = Collections.binarySearch(children, seriesNode, DicomSorter.SERIES_COMPARATOR);
    index = index < 0 ? -(index + 1) : index;
    studyNode.insert(seriesNode, index);

    if (hasGraphics) {
      String seriesInstanceUID = UIDUtils.createUID();
      Series<?> prSeries = new DicomSeries(seriesInstanceUID);
      prSeries.setTag(
          TagD.get(Tag.SeriesNumber), TagD.getTagValue(series, Tag.SeriesNumber, Integer.class));
      prSeries.setTag(TagD.get(Tag.Modality), "PR");
      prSeries.setTag(TagD.get(Tag.SeriesInstanceUID), seriesInstanceUID);
      prSeries.setTag(TagW.ObjectToSave, Boolean.TRUE);
      prSeries.setTag(SourceSeriesForPR, series);
      prSeries.setTag(
          TagD.get(Tag.SeriesDescription),
          Optional.ofNullable(TagD.getTagValue(series, Tag.SeriesDescription, String.class))
                  .orElse("")
              + " [GRAPHICS]"); // NON-NLS
      DefaultMutableTreeNode prVirtualNode = new ToolTipSeriesNode(prSeries, false);
      studyNode.insert(prVirtualNode, index + 1);
    }
  }

  protected synchronized DefaultTreeModel buildModel(DicomModel dicomModel) {
    Collection<MediaSeriesGroup> patients = dicomModel.getChildren(MediaSeriesGroupNode.rootNode);
    DefaultMutableTreeNode rootNode =
        new DefaultMutableTreeNode(
            patients.isEmpty()
                ? Messages.getString("no.patient.found")
                : DicomExplorer.ALL_PATIENTS);
    for (MediaSeriesGroup pt : patients) {
      DefaultMutableTreeNode patientNode = new ToolTipPatientNode(pt, true);
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

  private static boolean initToolTipText(TagReadable tagReadable, StringBuilder buf) {
    boolean newElement =
        LangUtil.getNULLtoFalse((Boolean) tagReadable.getTagValue(TagW.ObjectToSave));
    if (newElement) {
      buf.append(GuiUtils.HTML_COLOR_START);
      buf.append(IconColor.ACTIONS_YELLOW.getHtmlCode());
      buf.append("'><b>NEW </b></font>"); // NON-NLS
    }
    return newElement;
  }

  private static String getTags(TagReadable tagReadable) {
    StringBuilder toolTips = new StringBuilder();
    toolTips.append(GuiUtils.HTML_START);
    tagReadable
        .getTagEntrySetIterator()
        .forEachRemaining(
            i -> {
              TagW tag = i.getKey();
              if (tag == null || tag == TagW.Timezone) {
                return;
              }
              toolTips.append("<b>");
              toolTips.append(tag.getDisplayedName());
              toolTips.append("</b>");
              toolTips.append(StringUtil.COLON_AND_SPACE);
              String f = tag.addGMTOffset(null, tagReadable);
              toolTips.append(tag.getFormattedTagValue(i.getValue(), f));
              toolTips.append(GuiUtils.HTML_BR);
            });
    toolTips.append(GuiUtils.HTML_END);
    return toolTips.toString();
  }

  public static class ToolTipPatientNode extends DefaultMutableTreeNode {

    public ToolTipPatientNode(TagReadable userObject, boolean allowsChildren) {
      super(Objects.requireNonNull(userObject), allowsChildren);
    }

    public String getToolTipText() {
      return getTags((TagReadable) getUserObject());
    }
  }

  public static class ToolTipStudyNode extends DefaultMutableTreeNode {

    public ToolTipStudyNode(TagReadable userObject, boolean allowsChildren) {
      super(Objects.requireNonNull(userObject), allowsChildren);
    }

    public String getToolTipText() {
      return getTags((TagReadable) getUserObject());
    }
  }

  public static class ToolTipSeriesNode extends DefaultMutableTreeNode {

    public ToolTipSeriesNode(TagReadable userObject, boolean allowsChildren) {
      super(Objects.requireNonNull(userObject), allowsChildren);
    }

    public String getToolTipText() {
      return buildToolTipText((TagReadable) getUserObject());
    }

    @Override
    public String toString() {
      MediaSeries<?> s = (MediaSeries<?>) getUserObject();
      StringBuilder buf = new StringBuilder();
      boolean newElement = initToolTipText(s, buf);
      buildSeriesEntry(s, buf);
      int child = getChildCount();
      if (newElement) {
        child = Math.max(1, child); // Fix instance build on the fly
      }
      if (child > 0) {
        buf.append(" -- ").append(child).append(" instance(s)"); // NON-NLS
      }
      if (newElement) {
        buf.append(GuiUtils.HTML_END);
      }
      return buf.toString();
    }
  }

  public static String buildToolTipText(TagReadable s) {
    Thumbnailable thumb = (Thumbnailable) s.getTagValue(TagW.Thumbnail);
    if (thumb != null) {
      try {
        File path = thumb.getThumbnailPath();
        if (path != null) {
          URL url = path.toURI().toURL();
          StringBuilder buf = new StringBuilder();
          buf.append(GuiUtils.HTML_START);
          buf.append("<img src=\""); // NON-NLS
          buf.append(url);
          buf.append("\"><br>"); // NON-NLS
          buf.append(DcmMediaReader.buildDateTimeWithTimeZone(s, Tag.SeriesDate, Tag.SeriesTime));
          buf.append(GuiUtils.HTML_END);
          return buf.toString();
        }
      } catch (Exception e) {
        LOGGER.error("Display tooltip", e);
      }
    }
    return null;
  }

  public static void buildSeriesEntry(MediaSeries<?> s, StringBuilder buf) {
    Integer val = TagD.getTagValue(s, Tag.SeriesNumber, Integer.class);
    if (val != null) {
      buf.append("[");
      buf.append(val);
      buf.append("] ");
    }
    String modality = TagD.getTagValue(s, Tag.Modality, String.class);
    if (modality != null) {
      buf.append(modality);
      buf.append(" ");
    }
    String desc = TagD.getTagValue(s, Tag.SeriesDescription, String.class);
    if (desc != null) {
      buf.append(desc);
    }
  }
}
