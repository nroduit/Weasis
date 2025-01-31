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
import java.util.List;
import javax.swing.tree.DefaultMutableTreeNode;
import org.weasis.core.api.media.data.Series;
import org.weasis.dicom.explorer.CheckTreeModel;
import org.weasis.dicom.explorer.DicomModel;
import org.weasis.dicom.explorer.DicomSorter;

public class RetrieveTreeModel extends CheckTreeModel {

  public RetrieveTreeModel() {
    this(null);
  }

  public RetrieveTreeModel(DicomModel dicomModel) {
    super(dicomModel);
  }

  protected void buildSeries(DefaultMutableTreeNode studyNode, Series<?> series) {
    DefaultMutableTreeNode seriesNode = new ToolTipSeriesNode(series, true);

    List<?> children = Collections.list(studyNode.children());
    int index = Collections.binarySearch(children, seriesNode, DicomSorter.SERIES_COMPARATOR);
    index = index < 0 ? -(index + 1) : index;
    studyNode.insert(seriesNode, index);
  }
}
