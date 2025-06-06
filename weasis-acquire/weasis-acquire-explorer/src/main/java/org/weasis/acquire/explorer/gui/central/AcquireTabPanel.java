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
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import javax.swing.ButtonGroup;
import javax.swing.JPanel;
import org.weasis.acquire.explorer.AcquireManager;
import org.weasis.acquire.explorer.AcquireMediaInfo;
import org.weasis.acquire.explorer.core.bean.SeriesGroup;
import org.weasis.acquire.explorer.gui.control.AcquirePublishPanel;
import org.weasis.base.explorer.JIThumbnailCache;

public class AcquireTabPanel extends JPanel {

  private final Map<SeriesGroup, List<AcquireMediaInfo>> btnMap = new HashMap<>();

  private final SeriesButtonList seriesList;
  private final ButtonGroup btnGrp;
  private final AcquireCentralImagePanel imageList;

  private SeriesButton selected;

  public AcquireTabPanel(JIThumbnailCache thumbCache) {
    setLayout(new BorderLayout());
    btnGrp = new ButtonGroup();

    seriesList = new SeriesButtonList();
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

  public void updateSeries(SeriesGroup seriesGroup, List<AcquireMediaInfo> imageInfos) {
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
      createSeriesList(seriesGroup, imageInfos);
    }
  }

  public void addSeriesElement(SeriesGroup seriesGroup, List<AcquireMediaInfo> mediaInfos) {
    if (mediaInfos == null) {
      return;
    }

    if (btnMap.containsKey(seriesGroup)) {
      btnMap.get(seriesGroup).addAll(mediaInfos);
      if (seriesGroup.equals(getSeriesGroup())) {
        setSelected(selected);
      }
    } else {
      createSeriesList(seriesGroup, mediaInfos);
    }
  }

  private void createSeriesList(SeriesGroup seriesGroup, List<AcquireMediaInfo> mediaInfos) {
    btnMap.put(seriesGroup, mediaInfos);

    SeriesButton btn = new SeriesButton(seriesGroup, this);
    btnGrp.add(btn);
    seriesList.addButton(btn);
    if (selected == null) {
      updateSeriesButton(btn);
    }
  }

  public void selectSeries(SeriesGroup seriesGroup) {
    if (seriesGroup == null) {
      setSelected(null);
      return;
    }

    Optional<SeriesButton> btn = seriesList.getButton(seriesGroup);
    btn.ifPresent(this::updateSeriesButton);
  }

  private void updateSeriesButton(SeriesButton btn) {
    btnGrp.setSelected(btn.getModel(), true);
    setSelected(btn);
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
    seriesList.removeBySeries(seriesGroup);
    Optional<SeriesButton> nextBtn = seriesList.getFirstSeriesButton();

    if (nextBtn.isPresent()) {
      updateSeriesButton(nextBtn.get());
    } else if (btnMap.isEmpty()) {
      setSelected(null);
    }
  }

  public SeriesButton getSelected() {
    return selected;
  }

  public void removeImage(AcquireMediaInfo mediaInfo) {
    btnMap.entrySet().stream()
        .filter(e -> e.getValue().contains(mediaInfo))
        .findFirst()
        .ifPresent(e -> removeImage(e.getKey(), mediaInfo));
  }

  public void removeImages(Collection<AcquireMediaInfo> mediaInfos) {
    if (mediaInfos == null || mediaInfos.isEmpty()) {
      return;
    }
    Map<SeriesGroup, List<AcquireMediaInfo>> mediaRemovalMap = new HashMap<>();

    btnMap.forEach(
        (seriesGroup, mediaInfoList) -> {
          List<AcquireMediaInfo> pendingRemovals = new ArrayList<>();
          for (AcquireMediaInfo media : mediaInfos) {
            if (mediaInfoList.contains(media)) {
              pendingRemovals.add(media);
            }
          }
          if (!pendingRemovals.isEmpty()) {
            mediaRemovalMap.put(seriesGroup, pendingRemovals);
          }
        });

    mediaRemovalMap.forEach(this::removeImages);
  }

  private void removeImage(SeriesGroup seriesGroup, AcquireMediaInfo mediaInfo) {
    List<AcquireMediaInfo> imageInfos = btnMap.get(seriesGroup);
    if (Objects.nonNull(imageInfos)) {
      imageInfos.remove(mediaInfo);

      if (imageInfos.isEmpty()) {
        removeSeries(seriesGroup);
        seriesList.refreshGUI();
      } else if (seriesGroup.equals(getSeriesGroup())) {
        setSelected(selected);
      }
    }
  }

  private void removeImages(SeriesGroup seriesGroup, List<AcquireMediaInfo> mediaInfos) {
    List<AcquireMediaInfo> imagePane = btnMap.get(seriesGroup);
    if (Objects.nonNull(imagePane)) {
      imagePane.removeAll(mediaInfos);
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
      seriesList.removeBySeries(seriesGroup);
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

  public void moveElements(SeriesGroup seriesGroup, List<AcquireMediaInfo> mediaInfos) {
    removeImages(selected.getSeries(), mediaInfos);

    mediaInfos.forEach(m -> m.setSeries(seriesGroup));
    updateSeries(seriesGroup, AcquireManager.findBySeries(seriesGroup));
  }

  public void updateSeriesFromGlobalTags() {
    btnMap.keySet().forEach(SeriesGroup::updateDicomTags);
  }
}
