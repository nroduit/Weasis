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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.api.media.data.MediaElement;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.media.data.MediaSeries.MEDIA_POSITION;
import org.weasis.core.api.media.data.MediaSeriesGroup;
import org.weasis.core.api.media.data.Series;
import org.weasis.core.api.media.data.SeriesThumbnail;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.api.util.ResourceUtil;
import org.weasis.core.api.util.ResourceUtil.ActionIcon;
import org.weasis.core.ui.docking.UIManager;
import org.weasis.core.ui.editor.DefaultMimeAppFactory;
import org.weasis.core.ui.editor.SeriesViewer;
import org.weasis.core.ui.editor.SeriesViewerFactory;
import org.weasis.core.ui.editor.ViewerPluginBuilder;
import org.weasis.dicom.codec.DicomSeries;
import org.weasis.dicom.codec.TagD;
import org.weasis.dicom.codec.geometry.ImageOrientation;
import org.weasis.dicom.explorer.wado.LoadSeries;

public class ThumbnailMouseAndKeyAdapter extends MouseAdapter implements KeyListener {
  private final Series series;
  private final DicomModel dicomModel;
  private final LoadSeries loadSeries;

  public ThumbnailMouseAndKeyAdapter(Series series, DicomModel dicomModel, LoadSeries loadSeries) {
    this.series = Objects.requireNonNull(series);
    this.dicomModel = Objects.requireNonNull(dicomModel);
    this.loadSeries = loadSeries;
  }

  @Override
  public void mouseClicked(MouseEvent e) {
    if (e.getClickCount() == 2) {
      final SeriesSelectionModel selList = getSeriesSelectionModel();
      selList.setOpeningSeries(true);
      Map<String, Object> props = Collections.synchronizedMap(new HashMap<>());
      props.put(ViewerPluginBuilder.CMP_ENTRY_BUILD_NEW_VIEWER, true);
      props.put(ViewerPluginBuilder.BEST_DEF_LAYOUT, false);
      props.put(ViewerPluginBuilder.OPEN_IN_SELECTION, true);

      String mime = series.getMimeType();
      SeriesViewerFactory plugin = UIManager.getViewerFactory(mime);
      if (plugin == null) {
        plugin = DefaultMimeAppFactory.getInstance();
      }

      ArrayList<MediaSeries<MediaElement>> list = new ArrayList<>(1);
      list.add(series);
      ViewerPluginBuilder builder = new ViewerPluginBuilder(plugin, list, dicomModel, props);
      ViewerPluginBuilder.openSequenceInPlugin(builder);

      selList.setOpeningSeries(false);
    }
  }

  @Override
  public void mousePressed(MouseEvent mouseevent) {
    final Component c = mouseevent.getComponent();
    if (!c.isFocusOwner()) {
      c.requestFocusInWindow();
    }

    final SeriesSelectionModel selList = getSeriesSelectionModel();

    if (SwingUtilities.isRightMouseButton(mouseevent)) {
      JPopupMenu popupMenu = new JPopupMenu();

      List<SeriesViewerFactory> plugins =
          UIManager.getViewerFactoryList(new String[] {series.getMimeType()});
      if (!selList.contains(series)) {
        selList.setSelectionInterval(series, series);
      }
      // Is the selection has multiple mime types
      boolean multipleMimes = false;
      String mime = series.getMimeType();
      for (Series s : selList) {
        if (!mime.equals(s.getMimeType())) {
          multipleMimes = true;
          break;
        }
      }
      final List<MediaSeries<? extends MediaElement>> seriesList;
      if (multipleMimes) {
        // Filter the list to have only one mime type
        seriesList = new ArrayList<>();
        for (Series s : selList) {
          if (mime.equals(s.getMimeType())) {
            seriesList.add(s);
          }
        }
      } else {
        seriesList = new ArrayList<>(selList);
      }
      for (final SeriesViewerFactory viewerFactory : plugins) {
        JMenu menuFactory = new JMenu(viewerFactory.getUIName());
        menuFactory.setIcon(viewerFactory.getIcon());
        GuiUtils.applySelectedIconEffect(menuFactory);

        JMenuItem item4 = new JMenuItem(Messages.getString("DicomExplorer.open"));
        item4.addActionListener(
            e -> {
              selList.setOpeningSeries(true);
              ViewerPluginBuilder.openSequenceInPlugin(
                  viewerFactory, seriesList, dicomModel, true, true);
              selList.setOpeningSeries(false);
            });
        menuFactory.add(item4);

        // Exclude system factory
        if (viewerFactory.canExternalizeSeries()) {
          item4 =
              new JMenuItem(
                  Messages.getString("DicomExplorer.open_win"),
                  ResourceUtil.getIcon(ActionIcon.OPEN_NEW_TAB));
          GuiUtils.applySelectedIconEffect(item4);
          item4.addActionListener(
              e -> {
                selList.setOpeningSeries(true);
                ViewerPluginBuilder.openSequenceInPlugin(
                    viewerFactory, seriesList, dicomModel, false, true);
                selList.setOpeningSeries(false);
              });
          menuFactory.add(item4);

          GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
          final GraphicsDevice[] gd = ge.getScreenDevices();
          if (gd.length > 0) {
            JMenu subMenu = new JMenu(Messages.getString("DicomExplorer.open_screen"));
            for (GraphicsDevice graphicsDevice : gd) {
              GraphicsConfiguration config = graphicsDevice.getDefaultConfiguration();
              final Rectangle b = config.getBounds();
              item4 = new JMenuItem(config.getDevice().toString());
              item4.addActionListener(
                  e -> {
                    selList.setOpeningSeries(true);
                    ViewerPluginBuilder.openSequenceInPlugin(
                        viewerFactory, seriesList, dicomModel, false, true, b);
                    selList.setOpeningSeries(false);
                  });
              subMenu.add(item4);
            }
            menuFactory.add(subMenu);
          }
        }

        if (viewerFactory.canAddSeries()) {
          item4 = new JMenuItem(Messages.getString("DicomExplorer.add"));
          item4.addActionListener(
              e -> {
                selList.setOpeningSeries(true);
                ViewerPluginBuilder.openSequenceInPlugin(
                    viewerFactory, seriesList, dicomModel, true, false);
                selList.setOpeningSeries(false);
              });
          menuFactory.add(item4);
        }

        popupMenu.add(menuFactory);

        if (viewerFactory instanceof MimeSystemAppFactory) {
          final JMenuItem item5 =
              new JMenuItem(
                  Messages.getString("DicomExplorer.open_info"),
                  ResourceUtil.getIcon(ActionIcon.METADATA));
          GuiUtils.applySelectedIconEffect(item5);
          item5.addActionListener(
              e -> {
                SeriesViewer<?> viewer = viewerFactory.createSeriesViewer(null);
                MediaElement dcm = series.getMedia(MEDIA_POSITION.FIRST, null, null);
                DicomFieldsView.showHeaderDialog(viewer, series, dcm);
              });
          popupMenu.add(item5);
        }
      }
      if (series instanceof DicomSeries) {
        if (selList.size() == 1) {
          popupMenu.add(new JSeparator());
          JMenuItem item2 = new JMenuItem(Messages.getString("DicomExplorer.sel_rel_series"));
          item2.addActionListener(
              e -> {
                String fruid = TagD.getTagValue(series, Tag.FrameOfReferenceUID, String.class);
                if (fruid != null) {
                  selList.clear();
                  MediaSeriesGroup studyGroup = dicomModel.getParent(series, DicomModel.study);
                  synchronized (dicomModel) {
                    for (MediaSeriesGroup seq : dicomModel.getChildren(studyGroup)) {
                      if (seq instanceof Series<?> s
                          && fruid.equals(TagD.getTagValue(s, Tag.FrameOfReferenceUID))) {
                        selList.add(s);
                      }
                    }
                  }
                  selList.addSelectionInterval(series, series);
                }
              });
          popupMenu.add(item2);
          item2 = new JMenuItem(Messages.getString("DicomExplorer.sel_rel_series_axis"));
          item2.addActionListener(
              e -> {
                String fruid = TagD.getTagValue(series, Tag.FrameOfReferenceUID, String.class);
                if (fruid != null) {
                  selList.clear();
                  MediaSeriesGroup studyGroup = dicomModel.getParent(series, DicomModel.study);
                  synchronized (dicomModel) {
                    for (MediaSeriesGroup seq : dicomModel.getChildren(studyGroup)) {
                      if (seq instanceof Series s
                          && fruid.equals(TagD.getTagValue(s, Tag.FrameOfReferenceUID))
                          && ImageOrientation.hasSameOrientation(series, s)) {
                        selList.add(s);
                      }
                    }
                  }
                  selList.addSelectionInterval(series, series);
                }
              });
          popupMenu.add(item2);
        }
        if (selList.size() == 1 && loadSeries != null) {
          if (loadSeries.isStopped()) {
            popupMenu.add(new JSeparator());
            JMenuItem item3 =
                new JMenuItem(
                    Messages.getString("LoadSeries.resume"),
                    ResourceUtil.getIcon(ActionIcon.EXECUTE));
            item3.addActionListener(e -> loadSeries.resume());
            popupMenu.add(item3);
          } else if (!loadSeries.isDone()) {
            popupMenu.add(new JSeparator());
            JMenuItem item3 =
                new JMenuItem(
                    Messages.getString("LoadSeries.stop"),
                    ResourceUtil.getIcon(ActionIcon.SUSPEND));
            item3.addActionListener(e -> loadSeries.stop());
            popupMenu.add(item3);
          }
        }

        Object splitNb = series.getTagValue(TagW.SplitSeriesNumber);
        if (splitNb != null && seriesList.size() > 1) {
          String uid = TagD.getTagValue(series, Tag.SeriesInstanceUID, String.class);
          boolean sameOrigin = true;
          if (uid != null) {
            for (MediaSeries s : seriesList) {
              if (!uid.equals(TagD.getTagValue(s, Tag.SeriesInstanceUID))) {
                sameOrigin = false;
                break;
              }
            }
          }
          if (sameOrigin) {
            popupMenu.add(new JSeparator());
            JMenuItem item2 = new JMenuItem(Messages.getString("DicomExplorer.merge"));
            item2.addActionListener(
                e -> {
                  selList.clear();
                  dicomModel.mergeSeries(seriesList);
                });
            popupMenu.add(item2);
          }
        }
      }
      popupMenu.add(new JSeparator());
      JMenuItem item2 = new JMenuItem(Messages.getString("DicomExplorer.rem_series"));
      item2.addActionListener(
          e -> {
            for (int i = selList.size() - 1; i >= 0; i--) {
              dicomModel.removeSeries(selList.get(i));
            }
            selList.clear();
          });
      popupMenu.add(item2);
      if (selList.size() == 1) {
        item2 = new JMenuItem(Messages.getString("DicomExplorer.rem_study"));
        item2.addActionListener(
            e -> {
              MediaSeriesGroup studyGroup = dicomModel.getParent(series, DicomModel.study);
              dicomModel.removeStudy(studyGroup);
              selList.clear();
            });
        popupMenu.add(item2);
        item2 = new JMenuItem(Messages.getString("DicomExplorer.rem_pat"));
        item2.addActionListener(
            e -> {
              MediaSeriesGroup patientGroup =
                  dicomModel.getParent(
                      dicomModel.getParent(series, DicomModel.study), DicomModel.patient);
              dicomModel.removePatient(patientGroup);
              selList.clear();
            });
        popupMenu.add(item2);
        if (series.size(null) > 1) {
          if (series.getMedia(0, null, null) instanceof ImageElement) {
            popupMenu.add(new JSeparator());
            JMenu menu = new JMenu(Messages.getString("DicomExplorer.build_thumb"));
            item2 = new JMenuItem(Messages.getString("DicomExplorer.from_1"));
            item2.addActionListener(
                e -> {
                  SeriesThumbnail t = (SeriesThumbnail) series.getTagValue(TagW.Thumbnail);
                  if (t != null) {
                    t.reBuildThumbnail(MEDIA_POSITION.FIRST);
                  }
                });
            menu.add(item2);
            item2 = new JMenuItem(Messages.getString("DicomExplorer.from_mid"));
            item2.addActionListener(
                e -> {
                  SeriesThumbnail t = (SeriesThumbnail) series.getTagValue(TagW.Thumbnail);
                  if (t != null) {
                    t.reBuildThumbnail(MEDIA_POSITION.MIDDLE);
                  }
                });
            menu.add(item2);
            item2 = new JMenuItem(Messages.getString("DicomExplorer.from_last"));
            item2.addActionListener(
                e -> {
                  SeriesThumbnail t = (SeriesThumbnail) series.getTagValue(TagW.Thumbnail);
                  if (t != null) {
                    t.reBuildThumbnail(MEDIA_POSITION.LAST);
                  }
                });
            menu.add(item2);
            popupMenu.add(menu);
          }
        }
      }
      popupMenu.show(mouseevent.getComponent(), mouseevent.getX() - 5, mouseevent.getY() - 5);
    } else {
      selList.adjustSelection(mouseevent, series);
    }
  }

  @Override
  public void keyTyped(KeyEvent e) {
    // Do nothing
  }

  @Override
  public void keyPressed(KeyEvent e) {
    int code = e.getKeyCode();
    SeriesSelectionModel selList = getSeriesSelectionModel();
    if (code == KeyEvent.VK_ENTER) {
      if (selList.isEmpty()) {
        selList.add(series);
      }
      selList.setOpeningSeries(true);
      ViewerPluginBuilder.openSequenceInDefaultPlugin(
          new ArrayList<>(selList), dicomModel, true, true);
      selList.setOpeningSeries(false);
      if (e.getSource() instanceof JComponent jComponent) {
        jComponent.requestFocusInWindow();
      }
      e.consume();
    } else if (code == KeyEvent.VK_DOWN) {
      selList.adjustSelection(e, selList.getNextElement(series));
    } else if (code == KeyEvent.VK_UP) {
      selList.adjustSelection(e, selList.getPreviousElement(series));
    } else if (e.isControlDown() && code == KeyEvent.VK_A) {
      selList.setSelectionInterval(selList.getFirstElement(), selList.getLastElement());
    } else if (code == KeyEvent.VK_PAGE_DOWN || code == KeyEvent.VK_END) {
      Series val = selList.getLastElement();
      selList.setSelectionInterval(val, val);
    } else if (code == KeyEvent.VK_PAGE_UP || code == KeyEvent.VK_HOME) {
      Series val = selList.getFirstElement();
      selList.setSelectionInterval(val, val);
    }
  }

  @Override
  public void keyReleased(KeyEvent e) {
    // Do nothing
  }

  public static SeriesSelectionModel getSeriesSelectionModel() {
    DataExplorerView explorer = UIManager.getExplorerplugin(DicomExplorer.NAME);
    return explorer instanceof DicomExplorer model
        ? model.getSelectionList()
        : new SeriesSelectionModel(null);
  }
}
