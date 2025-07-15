/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.acquire.explorer.gui.central;

import java.awt.BorderLayout;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import javax.swing.JPanel;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import org.dcm4che3.data.Tag;
import org.weasis.acquire.explorer.AcquireMediaInfo;
import org.weasis.acquire.explorer.core.bean.SeriesGroup;
import org.weasis.acquire.explorer.gui.central.tumbnail.AcquireCentralThumbnailPane;
import org.weasis.base.explorer.JIThumbnailCache;
import org.weasis.base.explorer.list.IThumbnailModel;
import org.weasis.core.api.media.data.MediaElement;
import org.weasis.dicom.codec.TagD;

public class AcquireCentralImagePanel extends JPanel implements ListSelectionListener {

  private final AcquireCentralThumbnailPane<MediaElement> imageListPane;
  private final AcquireCentralInfoPanel imageInfo;

  public AcquireCentralImagePanel(AcquireTabPanel acquireTabPanel, JIThumbnailCache thumbCache) {
    setLayout(new BorderLayout());
    this.imageInfo = new AcquireCentralInfoPanel(null);
    this.imageListPane = new AcquireCentralThumbnailPane<>(new ArrayList<>(), thumbCache);

    imageListPane.setAcquireTabPanel(Objects.requireNonNull(acquireTabPanel));
    imageListPane.addListSelectionListener(this);

    add(imageListPane, BorderLayout.CENTER);
    add(imageInfo, BorderLayout.SOUTH);
  }

  public void getCurrentSeries(SeriesGroup newSeries) {
    imageInfo.setSeries(newSeries);
  }

  public void setSeriesGroup(SeriesGroup seriesGroup, List<AcquireMediaInfo> mediaInfos) {
    imageInfo.setSeries(seriesGroup);
    List<MediaElement> list = mediaInfos == null ? null : toImageElement(mediaInfos);
    imageListPane.setList(list);
  }

  private static List<MediaElement> toImageElement(List<AcquireMediaInfo> list) {
    return list.stream()
        .map(AcquireMediaInfo::getMedia)
        .sorted(
            Comparator.comparing(
                i -> TagD.dateTime(Tag.ContentDate, Tag.ContentTime, i),
                Comparator.nullsLast(Comparator.naturalOrder())))
        .collect(Collectors.toList());
  }

  public void addImagesInfo(List<AcquireMediaInfo> mediaInfos) {
    List<MediaElement> list = mediaInfos == null ? null : toImageElement(mediaInfos);
    imageListPane.addElements(list);
  }

  public void updateList(List<AcquireMediaInfo> mediaInfos) {
    List<MediaElement> list = mediaInfos == null ? null : toImageElement(mediaInfos);
    imageListPane.setList(list);
  }

  public void updateSeries(SeriesGroup newSeriesGroup) {
    imageInfo.setSeries(newSeriesGroup);
  }

  public IThumbnailModel<MediaElement> getFileListModel() {
    return imageListPane.getFileListModel();
  }

  public boolean containsImageElement(MediaElement image) {
    return getFileListModel().contains(image);
  }

  public void removeElement(MediaElement image) {
    getFileListModel().removeElement(image);
  }

  public void removeElements(List<MediaElement> medias) {
    IThumbnailModel<MediaElement> model = getFileListModel();
    medias.forEach(model::removeElement);
  }

  public void clearAll() {
    getFileListModel().clear();
  }

  public boolean isEmpty() {
    return getFileListModel().isEmpty();
  }

  protected void refreshGUI() {
    imageListPane.revalidate();
    imageListPane.repaint();
    refreshInfoGUI();
  }

  protected void refreshInfoGUI() {
    imageInfo.refreshGUI();
  }

  @Override
  public void valueChanged(ListSelectionEvent e) {
    List<MediaElement> images = imageListPane.getSelectedValuesList();
    if (images.size() == 1) {
      imageInfo.setImage(images.getFirst());
    } else {
      imageInfo.setImage(null);
    }
  }
}
