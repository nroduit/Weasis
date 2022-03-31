/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.acquire.explorer;

import bibliothek.gui.dock.common.CLocation;
import bibliothek.gui.dock.common.mode.ExtendedMode;
import java.awt.BorderLayout;
import java.beans.PropertyChangeEvent;
import java.io.File;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.swing.Action;
import javax.swing.Icon;
import org.weasis.acquire.explorer.core.bean.SeriesGroup;
import org.weasis.acquire.explorer.gui.central.ImageGroupPane;
import org.weasis.acquire.explorer.gui.control.BrowsePanel;
import org.weasis.acquire.explorer.gui.control.ImportPanel;
import org.weasis.acquire.explorer.gui.list.AcquireThumbnailListPane;
import org.weasis.acquire.explorer.media.FileSystemDrive;
import org.weasis.acquire.explorer.media.MediaSource;
import org.weasis.base.explorer.JIThumbnailCache;
import org.weasis.core.api.explorer.DataExplorerView;
import org.weasis.core.api.explorer.ObservableEvent;
import org.weasis.core.api.explorer.model.DataExplorerModel;
import org.weasis.core.api.gui.Insertable;
import org.weasis.core.api.media.data.MediaElement;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.api.service.BundleTools;
import org.weasis.core.ui.docking.PluginTool;
import org.weasis.core.ui.docking.UIManager;

public class AcquireExplorer extends PluginTool implements DataExplorerView {

  public static final String BUTTON_NAME = "dicomizer"; // NON-NLS
  public static final String TOOL_NAME = Messages.getString("AcquireExplorer.acquisition");
  public static final String P_LAST_DIR = "acquire.explorer.last.dir";
  public static final String PREFERENCE_NODE = "importer"; // NON-NLS

  public static final int MEDIA_SOURCE_LIST_MAX = 5;

  private MediaSource systemDrive;

  private final ImageGroupPane centralPane;

  private final AcquireThumbnailListPane<MediaElement> acquireThumbnailListPane;
  private final BrowsePanel browsePanel;
  private final ImportPanel importPanel;

  public AcquireExplorer() {
    super(
        BUTTON_NAME,
        TOOL_NAME,
        POSITION.WEST,
        ExtendedMode.NORMALIZED,
        Insertable.Type.EXPLORER,
        20);
    setDockableWidth(400);

    JIThumbnailCache thumbCache = new JIThumbnailCache();
    centralPane = new ImageGroupPane(Messages.getString("AcquireExplorer.album"), thumbCache);

    browsePanel = new BrowsePanel(this);
    acquireThumbnailListPane = new AcquireThumbnailListPane<>(thumbCache);
    importPanel = new ImportPanel(acquireThumbnailListPane, centralPane);

    setLayout(new BorderLayout(0, 0));
    add(browsePanel, BorderLayout.NORTH);
    add(acquireThumbnailListPane, BorderLayout.CENTER);
    add(importPanel, BorderLayout.SOUTH);

    this.acquireThumbnailListPane.loadDirectory(Paths.get(systemDrive.getPath()));

    // Remove dropping capabilities in the central area (limit to import
    // from browse panel)
    UIManager.MAIN_AREA.getComponent().setTransferHandler(null);
  }

  public static String getLastPath() {
    String home = System.getProperty("user.home");
    File prefDir =
        new File(BundleTools.LOCAL_UI_PERSISTENCE.getProperty(AcquireExplorer.P_LAST_DIR, home));
    if (prefDir.canRead() && prefDir.isDirectory()) {
      return prefDir.getPath();
    }
    return home;
  }

  void saveLastPath() {
    if (systemDrive != null) {
      File dir = new File(systemDrive.getPath());
      if (dir.canRead()) {
        BundleTools.LOCAL_UI_PERSISTENCE.setProperty(P_LAST_DIR, dir.getPath());
      }
    }
  }

  public void initImageGroupPane() {
    centralPane.getDockable().setCloseable(false);
    centralPane.showDockable();
    centralPane.setSelectedAndGetFocus();
  }

  @SuppressWarnings("unchecked")
  @Override
  public void propertyChange(PropertyChangeEvent evt) {
    if (evt instanceof ObservableEvent observableEvent) {
      if (evt.getSource() instanceof AcquireManager) {
        if (ObservableEvent.BasicAction.REPLACE.equals(observableEvent.getActionCommand())) {

          String newPatientName =
              Optional.ofNullable(evt.getNewValue())
                  .filter(String.class::isInstance)
                  .map(String.class::cast)
                  .orElse(Messages.getString("AcquireExplorer.album"));

          if (TagW.NO_VALUE.equals(newPatientName)) {
            newPatientName = Messages.getString("AcquireExplorer.album");
          }

          centralPane.setPluginName(newPatientName);
          centralPane.tabbedPane.clearAll();
          centralPane.tabbedPane.repaint();

        } else if (ObservableEvent.BasicAction.REMOVE.equals(observableEvent.getActionCommand())) {

          if (evt.getNewValue() instanceof Collection<?>) {
            centralPane.tabbedPane.removeImages((Collection<AcquireImageInfo>) evt.getNewValue());
            centralPane.tabbedPane.repaint();

          } else if (evt.getNewValue() instanceof AcquireImageInfo info) {
            centralPane.tabbedPane.removeImage(info);
            centralPane.tabbedPane.repaint();
          }

        } else if (ObservableEvent.BasicAction.UPDATE.equals(observableEvent.getActionCommand())) {
          centralPane.tabbedPane.refreshGUI();
          centralPane.tabbedPane.repaint();

        } else if (ObservableEvent.BasicAction.ADD.equals(observableEvent.getActionCommand())) {

          if (evt.getNewValue() instanceof Collection<?>) {
            ((Collection<AcquireImageInfo>) evt.getNewValue())
                .stream()
                    .collect(Collectors.groupingBy(AcquireImageInfo::getSeries))
                    .forEach(centralPane.tabbedPane::addSeriesElement);

          } else if (evt.getNewValue() instanceof AcquireImageInfo info) {
            SeriesGroup series = info.getSeries();
            ArrayList<AcquireImageInfo> infos = new ArrayList<>();
            infos.add(info);
            centralPane.tabbedPane.addSeriesElement(series, infos);
          }

          centralPane.tabbedPane.refreshGUI();
          centralPane.tabbedPane.repaint();
        }
      }
    }
  }

  @Override
  public Icon getIcon() {
    return null;
  }

  @Override
  public String getUIName() {
    return TOOL_NAME;
  }

  @Override
  public String getDescription() {
    return null;
  }

  @Override
  public void dispose() {
    super.closeDockable();
  }

  @Override
  public DataExplorerModel getDataExplorerModel() {
    return null;
  }

  @Override
  public List<Action> getOpenImportDialogAction() {
    return Collections.emptyList();
  }

  @Override
  public List<Action> getOpenExportDialogAction() {
    return Collections.emptyList();
  }

  @Override
  public void importFiles(File[] files, boolean recursive) {
    // Do nothing
  }

  @Override
  public boolean canImportFiles() {
    return false;
  }

  @Override
  protected void changeToolWindowAnchor(CLocation clocation) {
    // Do nothing
  }

  public MediaSource getSystemDrive() {
    return systemDrive;
  }

  public void setSystemDrive(MediaSource systemDrive) {
    this.systemDrive = systemDrive;
  }

  public ImageGroupPane getCentralPane() {
    return centralPane;
  }

  public ImportPanel getImportPanel() {
    return importPanel;
  }

  public void applyNewPath(String newRootPath) {
    setSystemDrive(new FileSystemDrive(newRootPath));

    browsePanel.getMediaSourceList().insertItem(0, systemDrive);
    if (browsePanel.getMediaSourceList().getSize() >= MEDIA_SOURCE_LIST_MAX) {
      browsePanel.getMediaSourceList().removeItem(MEDIA_SOURCE_LIST_MAX - 1);
    }
    browsePanel.getMediaSourceSelectionCombo().setSelectedItem(systemDrive);
    loadSystemDrive();
  }

  public void loadSystemDrive() {
    acquireThumbnailListPane.loadDirectory(Paths.get(systemDrive.getPath()));
  }
}
