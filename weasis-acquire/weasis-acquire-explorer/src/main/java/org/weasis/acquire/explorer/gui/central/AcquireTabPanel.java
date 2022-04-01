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
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import javax.swing.ButtonGroup;
import javax.swing.JPanel;
import org.dcm4che3.data.Tag;
import org.weasis.acquire.explorer.AcquireImageInfo;
import org.weasis.acquire.explorer.AcquireManager;
import org.weasis.acquire.explorer.core.bean.SeriesGroup;
import org.weasis.acquire.explorer.gui.control.AcquirePublishPanel;
import org.weasis.base.explorer.JIThumbnailCache;
import org.weasis.dicom.codec.TagD;

public class AcquireTabPanel extends JPanel {

  private final Map<SeriesGroup, List<AcquireImageInfo>> btnMap = new HashMap<>();

  private final SerieButtonList seriesList;
  private final ButtonGroup btnGrp;
  private final AcquireCentralImagePanel imageList;

  private SeriesButton selected;

  public AcquireTabPanel(JIThumbnailCache thumbCache) {
    setLayout(new BorderLayout());
    btnGrp = new ButtonGroup();

    seriesList = new SerieButtonList();
    imageList = new AcquireCentralImagePanel(this, thumbCache);
    JPanel seriesPanel = new JPanel(new BorderLayout());
    seriesPanel.add(seriesList, BorderLayout.CENTER);
    seriesPanel.add(new AcquirePublishPanel(), BorderLayout.SOUTH);

    add(seriesPanel, BorderLayout.WEST);
    add(imageList, BorderLayout.CENTER);
  }

  public void setSelected(SeriesButton btn) {
    selected = btn;
    SeriesGroup seriesGroup = getSeriesGroup();
    imageList.setSeriesGroup(seriesGroup, seriesGroup == null ? null : btnMap.get(seriesGroup));
    imageList.refreshGUI();
  }

  public void updateSeries(SeriesGroup seriesGroup, List<AcquireImageInfo> imageInfos) {
    if (imageInfos == null) {
      return;
    }

    if (btnMap.containsKey(seriesGroup)) {
      // update series list
      btnMap.put(seriesGroup, imageInfos);
      if (seriesGroup.equals(getSeriesGroup())) {
        setSelected(selected);
      }
    } else {
      // Create series list
      btnMap.put(seriesGroup, imageInfos);

      SeriesButton btn = new SeriesButton(seriesGroup, this);
      btnGrp.add(btn);
      seriesList.addButton(btn);

      if (selected == null) {
        btnGrp.setSelected(btn.getModel(), true);
        setSelected(btn);
      }
    }
  }

  public void addSeriesElement(SeriesGroup seriesGroup, List<AcquireImageInfo> imageInfos) {
    if (imageInfos == null) {
      return;
    }

    if (btnMap.containsKey(seriesGroup)) {
      btnMap.get(seriesGroup).addAll(imageInfos);
      if (seriesGroup.equals(getSeriesGroup())) {
        setSelected(selected);
      }
    } else {
      // Create series list
      btnMap.put(seriesGroup, imageInfos);

      SeriesButton btn = new SeriesButton(seriesGroup, this);
      btnGrp.add(btn);
      seriesList.addButton(btn);

      if (selected == null) {
        btnGrp.setSelected(btn.getModel(), true);
        setSelected(btn);
      }
    }
  }

  public Set<SeriesGroup> getSeries() {
    return new TreeSet<>(btnMap.keySet());
  }

  private SeriesGroup getSeriesGroup() {
    return selected == null ? null : selected.getSeries();
  }

  private void removeSeries(SeriesGroup seriesGroup) {

    btnMap.remove(seriesGroup);

    seriesList.getButton(seriesGroup).ifPresent(btnGrp::remove);
    seriesList.removeBySerie(seriesGroup);
    Optional<SeriesButton> nextBtn = seriesList.getFirstSerieButton();

    if (nextBtn.isPresent()) {
      btnGrp.setSelected(nextBtn.get().getModel(), true);
      setSelected(nextBtn.get());
    } else if (btnMap.isEmpty()) {
      setSelected(null);
    }
  }

  public SeriesButton getSelected() {
    return selected;
  }

  public void removeImage(AcquireImageInfo imageInfo) {
    btnMap.entrySet().stream()
        .filter(e -> e.getValue().contains(imageInfo))
        .findFirst()
        .ifPresent(e -> removeImage(e.getKey(), imageInfo));
  }

  public void removeImages(Collection<AcquireImageInfo> images) {
    Map<SeriesGroup, List<AcquireImageInfo>> imagesToRemove = new HashMap<>();

    for (Entry<SeriesGroup, List<AcquireImageInfo>> e : btnMap.entrySet()) {
      List<AcquireImageInfo> newList = null;
      Iterator<AcquireImageInfo> it = images.iterator();
      while (it.hasNext()) {
        AcquireImageInfo image = it.next();
        if (e.getValue().contains(image)) {
          it.remove();
          if (newList == null) {
            newList = new ArrayList<>();
          }
          newList.add(image);
        }
      }
      if (newList != null) {
        imagesToRemove.put(e.getKey(), newList);
      }
    }

    imagesToRemove.forEach(this::removeImages);
  }

  private void removeImage(SeriesGroup seriesGroup, AcquireImageInfo imageInfo) {
    List<AcquireImageInfo> imageInfos = btnMap.get(seriesGroup);
    if (Objects.nonNull(imageInfos)) {
      imageInfos.remove(imageInfo);

      if (imageInfos.isEmpty()) {
        removeSeries(seriesGroup);
        seriesList.refreshGUI();
      } else if (seriesGroup.equals(getSeriesGroup())) {
        setSelected(selected);
      }
    }
  }

  private void removeImages(SeriesGroup seriesGroup, List<AcquireImageInfo> images) {
    List<AcquireImageInfo> imagePane = btnMap.get(seriesGroup);
    if (Objects.nonNull(imagePane)) {
      imagePane.removeAll(images);
      if (seriesGroup.equals(getSeriesGroup())) {
        setSelected(selected);
      }

      if (imagePane.isEmpty()) {
        removeSeries(seriesGroup);
        seriesList.refreshGUI();
      } else if (seriesGroup.equals(getSeriesGroup())) {
        setSelected(selected);
      }
    }
  }

  public void clearUnusedSeries(List<SeriesGroup> usedSeries) {
    List<SeriesGroup> seriesToRemove =
        btnMap.keySet().stream().filter(s -> !usedSeries.contains(s)).toList();
    seriesToRemove.forEach(this::removeSeries);
    seriesList.refreshGUI();
  }

  public void clearAll() {
    for (SeriesGroup seriesGroup : btnMap.keySet()) {
      seriesList.getButton(seriesGroup).ifPresent(btnGrp::remove);
      seriesList.removeBySerie(seriesGroup);
    }
    btnMap.clear();

    setSelected(null);
    refreshGUI();
  }

  public void refreshGUI() {
    imageList.refreshGUI();
    seriesList.refreshGUI();
  }

  public void refreshInfoGUI() {
    imageList.refreshInfoGUI();
  }

  public void moveElements(SeriesGroup seriesGroup, List<AcquireImageInfo> medias) {
    removeImages(selected.getSeries(), medias);

    medias.forEach(m -> m.setSeries(seriesGroup));
    updateSeries(seriesGroup, AcquireManager.findBySeries(seriesGroup));
  }

  public void updateSeriesFromGlobalTags() {
    btnMap.keySet().forEach(SeriesGroup::updateDicomTags);
  }

  public void moveElementsByDate(List<AcquireImageInfo> medias) {
    removeImages(selected.getSeries(), medias);

    Set<SeriesGroup> seriesGroups = new HashSet<>();
    medias.forEach(
        m -> {
          LocalDateTime date = TagD.dateTime(Tag.ContentDate, Tag.ContentTime, m.getImage());
          SeriesGroup seriesGroup = AcquireManager.getSeries(new SeriesGroup(date));
          seriesGroups.add(seriesGroup);
          m.setSeries(seriesGroup);
        });

    AcquireManager.groupBySeries()
        .forEach(
            (k, v) -> {
              if (seriesGroups.contains(k)) {
                updateSeries(k, v);
              }
            });
  }
}
