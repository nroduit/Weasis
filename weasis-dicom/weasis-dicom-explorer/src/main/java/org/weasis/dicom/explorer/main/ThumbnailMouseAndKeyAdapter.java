/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.explorer.main;

import java.awt.Component;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import javax.swing.JComponent;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JSeparator;
import javax.swing.SwingUtilities;
import org.dcm4che3.data.Tag;
import org.weasis.core.api.explorer.DataExplorerView;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.media.data.MediaElement;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.media.data.MediaSeries.MEDIA_POSITION;
import org.weasis.core.api.media.data.MediaSeriesGroup;
import org.weasis.core.api.media.data.SeriesThumbnail;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.api.util.ResourceUtil;
import org.weasis.core.api.util.ResourceUtil.ActionIcon;
import org.weasis.core.ui.editor.SeriesViewer;
import org.weasis.core.ui.editor.SeriesViewerFactory;
import org.weasis.core.ui.editor.ViewerPluginBuilder;
import org.weasis.dicom.codec.DicomSeries;
import org.weasis.dicom.codec.TagD;
import org.weasis.dicom.codec.geometry.ImageOrientation;
import org.weasis.dicom.explorer.DicomModel;
import org.weasis.dicom.explorer.DicomSeriesHandler;
import org.weasis.dicom.explorer.LoadLocalDicom;
import org.weasis.dicom.explorer.Messages;
import org.weasis.dicom.explorer.MimeSystemAppFactory;
import org.weasis.dicom.explorer.tag.DicomFieldsView;
import org.weasis.dicom.explorer.wado.LoadSeries;

public class ThumbnailMouseAndKeyAdapter extends MouseAdapter implements KeyListener {
  private final DicomSeries series;
  private final DicomModel dicomModel;
  private final LoadSeries loadSeries;

  public ThumbnailMouseAndKeyAdapter(
      DicomSeries series, DicomModel dicomModel, LoadSeries loadSeries) {
    this.series = Objects.requireNonNull(series);
    this.dicomModel = Objects.requireNonNull(dicomModel);
    this.loadSeries = loadSeries;
  }

  @Override
  public void mouseClicked(MouseEvent e) {
    if (e.getClickCount() == 2) {
      openSeriesInDefaultPlugin(series, dicomModel);
    }
  }

  @Override
  public void mousePressed(MouseEvent e) {
    requestFocusIfNeeded(e.getComponent());

    final SeriesSelectionModel selList = getSeriesSelectionModel();

    if (SwingUtilities.isRightMouseButton(e)) {
      showContextMenu(e, selList);
    } else {
      selList.adjustSelection(e, series);
    }
  }

  @Override
  public void keyPressed(KeyEvent e) {
    int code = e.getKeyCode();
    SeriesSelectionModel selList = getSeriesSelectionModel();

    switch (code) {
      case KeyEvent.VK_ENTER -> handleEnterKey(e, selList);
      case KeyEvent.VK_DOWN -> selList.selectNext();
      case KeyEvent.VK_UP -> selList.selectPrevious();
      case KeyEvent.VK_PAGE_DOWN, KeyEvent.VK_END -> selList.selectLast();
      case KeyEvent.VK_PAGE_UP, KeyEvent.VK_HOME -> selList.selectFirst();
      case KeyEvent.VK_A -> {
        if (e.isControlDown()) {
          selList.selectAll();
        }
      }
    }
  }

  @Override
  public void keyTyped(KeyEvent e) {
    // Do nothing
  }

  @Override
  public void keyReleased(KeyEvent e) {
    // Do nothing
  }

  private void requestFocusIfNeeded(Component component) {
    if (!component.isFocusOwner()) {
      component.requestFocusInWindow();
    }
  }

  private void handleEnterKey(KeyEvent e, SeriesSelectionModel selList) {
    selList.ensureSelection(series);
    if (selList.openSelectedSeries(dicomModel)) {
      // Request focus back to the component after opening
      if (e.getSource() instanceof JComponent jComponent) {
        jComponent.requestFocusInWindow();
      }
    }
    e.consume();
  }

  private void showContextMenu(MouseEvent e, SeriesSelectionModel selList) {
    JPopupMenu popupMenu = new JPopupMenu();

    ensureSeriesSelected(selList);
    List<MediaSeries<? extends MediaElement>> seriesList = getFilteredSeriesList(selList);

    addViewerMenuItems(popupMenu, seriesList);
    addSelectionMenuItems(popupMenu, selList);
    addLoadSeriesMenuItems(popupMenu, selList);
    addMergeMenuItem(popupMenu, selList, seriesList);
    addRemovalMenuItems(popupMenu, selList);
    addSplitPhases(popupMenu, selList);
    addThumbnailMenuItems(popupMenu, selList);

    popupMenu.show(e.getComponent(), e.getX() - 5, e.getY() - 5);
  }

  private void ensureSeriesSelected(SeriesSelectionModel selList) {
    if (!selList.contains(series)) {
      selList.add(series);
    }
  }

  private List<MediaSeries<? extends MediaElement>> getFilteredSeriesList(
      SeriesSelectionModel selList) {
    String mime = series.getMimeType();
    boolean multipleMimes = hasMultipleMimeTypes(selList, mime);
    if (multipleMimes) {
      return selList.stream()
          .filter(s -> mime.equals(s.getMimeType()))
          .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
    } else {
      return new ArrayList<>(selList);
    }
  }

  private boolean hasMultipleMimeTypes(SeriesSelectionModel selList, String mime) {
    return selList.stream().anyMatch(s -> !mime.equals(s.getMimeType()));
  }

  private void addViewerMenuItems(
      JPopupMenu popupMenu, List<MediaSeries<? extends MediaElement>> seriesList) {
    List<SeriesViewerFactory> plugins =
        GuiUtils.getUICore().getViewerFactoryList(new String[] {series.getMimeType()});

    for (SeriesViewerFactory viewerFactory : plugins) {
      if (viewerFactory.canReadSeries(series)) {
        JMenu menuFactory = createViewerFactoryMenu(viewerFactory, seriesList);
        popupMenu.add(menuFactory);

        if (viewerFactory instanceof MimeSystemAppFactory) {
          addMetadataMenuItem(popupMenu, viewerFactory);
        }
      }
    }
  }

  private JMenu createViewerFactoryMenu(
      SeriesViewerFactory viewerFactory, List<MediaSeries<? extends MediaElement>> seriesList) {
    JMenu menuFactory = new JMenu(viewerFactory.getUIName());
    menuFactory.setIcon(viewerFactory.getIcon());
    GuiUtils.applySelectedIconEffect(menuFactory);

    addOpenMenuItem(menuFactory, viewerFactory, seriesList);

    if (viewerFactory.canExternalizeSeries()) {
      addExternalizeMenuItems(menuFactory, viewerFactory, seriesList);
    }

    if (viewerFactory.canAddSeries()) {
      addAddMenuItem(menuFactory, viewerFactory, seriesList);
    }

    return menuFactory;
  }

  private void addOpenMenuItem(
      JMenu menuFactory,
      SeriesViewerFactory viewerFactory,
      List<MediaSeries<? extends MediaElement>> seriesList) {
    JMenuItem openItem = new JMenuItem(Messages.getString("DicomExplorer.open"));
    openItem.addActionListener(
        e ->
            executeWithOpeningSeries(
                () ->
                    ViewerPluginBuilder.openSequenceInPlugin(
                        viewerFactory, seriesList, dicomModel, true, true)));
    menuFactory.add(openItem);
  }

  private void addExternalizeMenuItems(
      JMenu menuFactory,
      SeriesViewerFactory viewerFactory,
      List<MediaSeries<? extends MediaElement>> seriesList) {
    JMenuItem newWindowItem =
        new JMenuItem(
            Messages.getString("DicomExplorer.open_win"),
            ResourceUtil.getIcon(ActionIcon.OPEN_NEW_TAB));
    GuiUtils.applySelectedIconEffect(newWindowItem);
    newWindowItem.addActionListener(
        e ->
            executeWithOpeningSeries(
                () ->
                    ViewerPluginBuilder.openSequenceInPlugin(
                        viewerFactory, seriesList, dicomModel, false, true)));
    menuFactory.add(newWindowItem);

    addScreenMenuItems(menuFactory, viewerFactory, seriesList);
  }

  private void addScreenMenuItems(
      JMenu menuFactory,
      SeriesViewerFactory viewerFactory,
      List<MediaSeries<? extends MediaElement>> seriesList) {
    GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
    GraphicsDevice[] gd = ge.getScreenDevices();

    if (gd.length > 1) { // Only add screen menu if multiple screens
      JMenu subMenu = new JMenu(Messages.getString("DicomExplorer.open_screen"));
      for (GraphicsDevice graphicsDevice : gd) {
        GraphicsConfiguration config = graphicsDevice.getDefaultConfiguration();
        Rectangle bounds = config.getBounds();
        JMenuItem screenItem = new JMenuItem(config.getDevice().toString());
        screenItem.addActionListener(
            e ->
                executeWithOpeningSeries(
                    () ->
                        ViewerPluginBuilder.openSequenceInPlugin(
                            viewerFactory, seriesList, dicomModel, false, true, bounds)));
        subMenu.add(screenItem);
      }
      menuFactory.add(subMenu);
    }
  }

  private void addAddMenuItem(
      JMenu menuFactory,
      SeriesViewerFactory viewerFactory,
      List<MediaSeries<? extends MediaElement>> seriesList) {
    JMenuItem addItem = new JMenuItem(Messages.getString("DicomExplorer.add"));
    addItem.addActionListener(
        e ->
            executeWithOpeningSeries(
                () ->
                    ViewerPluginBuilder.openSequenceInPlugin(
                        viewerFactory, seriesList, dicomModel, true, false)));
    menuFactory.add(addItem);
  }

  private void addMetadataMenuItem(JPopupMenu popupMenu, SeriesViewerFactory viewerFactory) {
    JMenuItem metadataItem =
        new JMenuItem(
            Messages.getString("DicomExplorer.open_info"),
            ResourceUtil.getIcon(ActionIcon.METADATA));
    GuiUtils.applySelectedIconEffect(metadataItem);
    metadataItem.addActionListener(
        e -> {
          SeriesViewer<?> viewer = viewerFactory.createSeriesViewer(null);
          MediaElement dcm = series.getMedia(MEDIA_POSITION.FIRST, null, null);
          DicomFieldsView.showHeaderDialog(viewer, series, dcm);
        });
    popupMenu.add(metadataItem);
  }

  private void addSelectionMenuItems(JPopupMenu popupMenu, SeriesSelectionModel selList) {
    if (selList.size() == 1) {
      popupMenu.add(new JSeparator());
      addRelatedSeriesMenuItem(popupMenu, selList);
      addRelatedSeriesAxisMenuItem(popupMenu, selList);
    }
  }

  private void addRelatedSeriesMenuItem(JPopupMenu popupMenu, SeriesSelectionModel selList) {
    JMenuItem relatedItem = new JMenuItem(Messages.getString("DicomExplorer.sel_rel_series"));
    relatedItem.addActionListener(e -> selectRelatedSeries(selList, false));
    popupMenu.add(relatedItem);
  }

  private void addRelatedSeriesAxisMenuItem(JPopupMenu popupMenu, SeriesSelectionModel selList) {
    JMenuItem relatedAxisItem =
        new JMenuItem(Messages.getString("DicomExplorer.sel_rel_series_axis"));
    relatedAxisItem.addActionListener(e -> selectRelatedSeries(selList, true));
    popupMenu.add(relatedAxisItem);
  }

  private void selectRelatedSeries(SeriesSelectionModel selList, boolean checkOrientation) {
    String fruid = TagD.getTagValue(series, Tag.FrameOfReferenceUID, String.class);
    if (fruid == null) return;
    selList.clear();
    MediaSeriesGroup studyGroup = dicomModel.getParent(series, DicomModel.study);
    synchronized (dicomModel) {
      dicomModel.getChildren(studyGroup).stream()
          .filter(seq -> seq instanceof DicomSeries)
          .map(seq -> (DicomSeries) seq)
          .filter(s -> fruid.equals(TagD.getTagValue(s, Tag.FrameOfReferenceUID)))
          .filter(s -> !checkOrientation || ImageOrientation.hasSameOrientation(series, s))
          .forEach(selList::add);
    }
  }

  private void addLoadSeriesMenuItems(JPopupMenu popupMenu, SeriesSelectionModel selList) {
    if (selList.size() == 1 && loadSeries != null) {
      if (loadSeries.isStopped()) {
        popupMenu.add(new JSeparator());
        JMenuItem resumeItem =
            new JMenuItem(
                Messages.getString("LoadSeries.resume"), ResourceUtil.getIcon(ActionIcon.EXECUTE));
        resumeItem.addActionListener(e -> loadSeries.resume());
        popupMenu.add(resumeItem);
      } else if (!loadSeries.isDone()) {
        popupMenu.add(new JSeparator());
        JMenuItem stopItem =
            new JMenuItem(
                Messages.getString("LoadSeries.stop"), ResourceUtil.getIcon(ActionIcon.SUSPEND));
        stopItem.addActionListener(e -> loadSeries.stop());
        popupMenu.add(stopItem);
      }
    }
  }

  private void addMergeMenuItem(
      JPopupMenu popupMenu,
      SeriesSelectionModel selList,
      List<MediaSeries<? extends MediaElement>> seriesList) {
    Object splitNb = series.getTagValue(TagW.SplitSeriesNumber);
    if (splitNb != null && seriesList.size() > 1 && canMergeSeries(seriesList)) {
      popupMenu.add(new JSeparator());
      JMenuItem mergeItem = new JMenuItem(Messages.getString("DicomExplorer.merge"));
      mergeItem.addActionListener(
          e -> {
            selList.clear();
            dicomModel.mergeSeries(seriesList);
          });
      popupMenu.add(mergeItem);
    }
  }

  private boolean canMergeSeries(List<MediaSeries<? extends MediaElement>> seriesList) {
    String uid = TagD.getTagValue(series, Tag.SeriesInstanceUID, String.class);
    return uid != null
        && seriesList.stream()
            .allMatch(s -> uid.equals(TagD.getTagValue(s, Tag.SeriesInstanceUID)));
  }

  private void addRemovalMenuItems(JPopupMenu popupMenu, SeriesSelectionModel selList) {
    popupMenu.add(new JSeparator());
    JMenuItem removeSeriesItem = new JMenuItem(Messages.getString("DicomExplorer.rem_series"));
    removeSeriesItem.addActionListener(
        e -> {
          for (int i = selList.size() - 1; i >= 0; i--) {
            dicomModel.removeSeries(selList.get(i));
          }
          selList.clear();
        });
    popupMenu.add(removeSeriesItem);
    if (selList.size() == 1) {
      addRemoveStudyMenuItem(popupMenu, selList);
      addRemovePatientMenuItem(popupMenu, selList);
    }
  }

  private void addRemoveStudyMenuItem(JPopupMenu popupMenu, SeriesSelectionModel selList) {
    JMenuItem removeStudyItem = new JMenuItem(Messages.getString("DicomExplorer.rem_study"));
    removeStudyItem.addActionListener(
        e -> {
          MediaSeriesGroup studyGroup = dicomModel.getParent(series, DicomModel.study);
          dicomModel.removeStudy(studyGroup);
          selList.clear();
        });
    popupMenu.add(removeStudyItem);
  }

  private void addRemovePatientMenuItem(JPopupMenu popupMenu, SeriesSelectionModel selList) {
    JMenuItem removePatientItem = new JMenuItem(Messages.getString("DicomExplorer.rem_pat"));
    removePatientItem.addActionListener(
        e -> {
          MediaSeriesGroup patientGroup =
              dicomModel.getParent(
                  dicomModel.getParent(series, DicomModel.study), DicomModel.patient);
          dicomModel.removePatient(patientGroup);
          selList.clear();
        });
    popupMenu.add(removePatientItem);
  }

  private void addSplitPhases(JPopupMenu popupMenu, SeriesSelectionModel selList) {
    if (selList.size() == 1 && series.size(null) > 1) {
      if (LoadLocalDicom.isMultiPhaseSeries(series)) {
        popupMenu.add(new JSeparator());
        JMenuItem item2 = new JMenuItem(Messages.getString("separate.phases"));
        item2.addActionListener(_ -> LoadLocalDicom.seriesPostProcessing(series, dicomModel, true));
        popupMenu.add(item2);
      }
    }
  }

  private void addThumbnailMenuItems(JPopupMenu popupMenu, SeriesSelectionModel selList) {
    if (selList.size() == 1 && series.size(null) > 1) {
      popupMenu.add(new JSeparator());
      JMenu thumbnailMenu = new JMenu(Messages.getString("DicomExplorer.build_thumb"));
      addThumbnailMenuItem(thumbnailMenu, "DicomExplorer.from_1", MEDIA_POSITION.FIRST);
      addThumbnailMenuItem(thumbnailMenu, "DicomExplorer.from_mid", MEDIA_POSITION.MIDDLE);
      addThumbnailMenuItem(thumbnailMenu, "DicomExplorer.from_last", MEDIA_POSITION.LAST);
      popupMenu.add(thumbnailMenu);
    }
  }

  private void addThumbnailMenuItem(JMenu menu, String messageKey, MEDIA_POSITION position) {
    JMenuItem item = new JMenuItem(Messages.getString(messageKey));
    item.addActionListener(
        _ -> {
          SeriesThumbnail thumbnail = (SeriesThumbnail) series.getTagValue(TagW.Thumbnail);
          if (thumbnail != null) {
            thumbnail.reBuildThumbnail(position);
          }
        });
    menu.add(item);
  }

  private void executeWithOpeningSeries(Runnable action) {
    SeriesSelectionModel selList = getSeriesSelectionModel();
    selList.setOpeningSeries(true);
    try {
      action.run();
    } finally {
      selList.setOpeningSeries(false);
    }
  }

  public static SeriesSelectionModel getSeriesSelectionModel() {
    DataExplorerView explorer = GuiUtils.getUICore().getExplorerPlugin(DicomExplorer.NAME);
    if (explorer instanceof DicomExplorer dicomExplorer) {
      return dicomExplorer.getSelectionList();
    }
    throw new IllegalStateException(
        "DicomExplorer plugin is not available, cannot get SeriesSelectionModel");
  }

  public static void openSeriesInDefaultPlugin(DicomSeries series, DicomModel dicomModel) {
    final SeriesSelectionModel selList = getSeriesSelectionModel();
    selList.setOpeningSeries(true);
    try {
      DicomSeriesHandler.openDicomSeriesInViewer(series, dicomModel);
    } finally {
      selList.setOpeningSeries(false);
    }
  }
}
