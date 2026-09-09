/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.acquire.explorer.gui.control;

import java.util.Collection;
import java.util.List;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.acquire.explorer.AcquireExplorer;
import org.weasis.acquire.explorer.core.ItemList;
import org.weasis.acquire.explorer.gui.model.actions.ChangePathSelectionAction;
import org.weasis.acquire.explorer.gui.model.list.ItemListComboBoxModel;
import org.weasis.acquire.explorer.gui.model.renderer.MediaSourceListCellRenderer;
import org.weasis.acquire.explorer.media.FileSystemDrive;
import org.weasis.acquire.explorer.media.MediaSource;
import org.weasis.acquire.explorer.media.RemovableDriveWatcher;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.util.FontItem;
import org.weasis.core.api.util.HardwareInfo.Drive;

public class BrowsePanel extends JPanel implements RemovableDriveWatcher.Listener {
  private static final Logger LOGGER = LoggerFactory.getLogger(BrowsePanel.class);

  private final ItemList<MediaSource> mediaSourceList = new ItemList<>();
  private final JComboBox<MediaSource> mediaSourceSelectionCombo = new JComboBox<>();
  private final transient RemovableDriveWatcher driveWatcher;

  public BrowsePanel(AcquireExplorer acquisitionView) {
    setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
    setBorder(GuiUtils.getEmptyBorder(5));

    acquisitionView.setSystemDrive(new FileSystemDrive(AcquireExplorer.getLastPath()));
    mediaSourceList.addItem(acquisitionView.getSystemDrive());

    driveWatcher = new RemovableDriveWatcher(this);

    ItemListComboBoxModel<MediaSource> mediaSourceListComboModel =
        new ItemListComboBoxModel<>(mediaSourceList);

    mediaSourceSelectionCombo.setModel(mediaSourceListComboModel);
    mediaSourceSelectionCombo.setRenderer(new MediaSourceListCellRenderer());
    mediaSourceSelectionCombo.setMaximumRowCount(15);
    mediaSourceSelectionCombo.setFont(FontItem.SMALL.getFont());
    mediaSourceSelectionCombo.addActionListener(
        e -> {
          acquisitionView.setSystemDrive(
              (FileSystemDrive) mediaSourceSelectionCombo.getSelectedItem());
          acquisitionView.loadSystemDrive();
        });
    add(mediaSourceSelectionCombo);

    final JButton pathSelectionBtn = new JButton(new ChangePathSelectionAction(acquisitionView));
    pathSelectionBtn.setFont(FontItem.SMALL.getFont());
    add(pathSelectionBtn);

    // Allow combo to limit the size with long path
    GuiUtils.setPreferredWidth(
        mediaSourceSelectionCombo,
        mediaSourceSelectionCombo.getPreferredSize().width
            - pathSelectionBtn.getPreferredSize().width
            - GuiUtils.getScaleLength(5));
  }

  public JComboBox<MediaSource> getMediaSourceSelectionCombo() {
    return mediaSourceSelectionCombo;
  }

  public ItemList<MediaSource> getMediaSourceList() {
    return mediaSourceList;
  }

  /** Stops watching the removable drives; called when the acquisition explorer is disposed. */
  public void close() {
    driveWatcher.close();
  }

  /**
   * Drives already mounted are offered, but the last path the user chose stays selected; only a
   * drive plugged in afterward is an explicit request to browse it.
   */
  @Override
  public void drivesDetected(Collection<Drive> drives) {
    drives.forEach(drive -> addDrive(drive, false));
  }

  @Override
  public void driveConnected(Drive drive) {
    addDrive(drive, true);
  }

  @Override
  public void driveDisconnected(Drive drive) {
    String mount = drive.mount();
    List<MediaSource> gone =
        mediaSourceList.getList().stream().filter(m -> m.getPath().startsWith(mount)).toList();
    if (gone.isEmpty()) {
      return;
    }
    MediaSource selected = (MediaSource) mediaSourceSelectionCombo.getSelectedItem();
    mediaSourceList.removeItems(gone);
    if (selected == null || selected.getPath().startsWith(mount)) {
      mediaSourceSelectionCombo.setSelectedIndex(mediaSourceList.isEmpty() ? -1 : 0);
    }
  }

  private void addDrive(Drive drive, boolean select) {
    try {
      FileSystemDrive item = new FileSystemDrive(drive.mount());
      mediaSourceList.addItem(item);
      if (select) {
        mediaSourceSelectionCombo.setSelectedItem(item);
      }
    } catch (IllegalArgumentException e) {
      LOGGER.warn("Cannot browse the removable drive {}", drive.mount(), e);
    }
  }
}
