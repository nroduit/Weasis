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

import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import net.samuelcampos.usbdrivedetector.USBDeviceDetectorManager;
import net.samuelcampos.usbdrivedetector.USBStorageDevice;
import net.samuelcampos.usbdrivedetector.events.DeviceEventType;
import net.samuelcampos.usbdrivedetector.events.IUSBDriveListener;
import net.samuelcampos.usbdrivedetector.events.USBStorageEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.acquire.explorer.AcquireExplorer;
import org.weasis.acquire.explorer.core.ItemList;
import org.weasis.acquire.explorer.gui.model.actions.ChangePathSelectionAction;
import org.weasis.acquire.explorer.gui.model.list.ItemListComboBoxModel;
import org.weasis.acquire.explorer.gui.model.renderer.MediaSourceListCellRenderer;
import org.weasis.acquire.explorer.media.FileSystemDrive;
import org.weasis.acquire.explorer.media.MediaSource;
import org.weasis.core.api.gui.util.GuiExecutor;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.util.FontItem;

public class BrowsePanel extends JPanel implements IUSBDriveListener {
  private static final Logger LOGGER = LoggerFactory.getLogger(BrowsePanel.class);

  private final ItemList<MediaSource> mediaSourceList = new ItemList<>();
  private final JComboBox<MediaSource> mediaSourceSelectionCombo = new JComboBox<>();

  public BrowsePanel(AcquireExplorer acquisitionView) {
    setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
    setBorder(GuiUtils.getEmptyBorder(5));
    try {
      acquisitionView.setSystemDrive(new FileSystemDrive(AcquireExplorer.getLastPath()));
      mediaSourceList.addItem(acquisitionView.getSystemDrive());
    } catch (Exception e) {
      LOGGER.warn(e.getMessage(), e);
    }

    USBDeviceDetectorManager driveDetector = new USBDeviceDetectorManager(3000);
    driveDetector.addDriveListener(this);

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

  @Override
  public void usbDriveEvent(USBStorageEvent event) {
    LOGGER.debug("USB event: {}", event);

    GuiExecutor.instance()
        .execute(
            () -> {
              DeviceEventType eventType = event.getEventType();
              if (eventType == DeviceEventType.CONNECTED) {
                addUsbDevice(event.getStorageDevice());
              } else if (eventType == DeviceEventType.REMOVED) {
                removeUsbDevice(event.getStorageDevice());
              }
            });
  }

  private void addUsbDevice(USBStorageDevice storageDevice) {
    FileSystemDrive item = new FileSystemDrive(storageDevice.getRootDirectory().getPath());
    mediaSourceList.addItem(item);
    mediaSourceSelectionCombo.setSelectedItem(item);
  }

  private void removeUsbDevice(USBStorageDevice storageDevice) {
    MediaSource selected = (MediaSource) mediaSourceSelectionCombo.getSelectedItem();
    String id = storageDevice.getRootDirectory().getPath();
    mediaSourceList.getList().removeIf(m -> m.getPath().startsWith(id));
    if (mediaSourceList.isEmpty()) {
      mediaSourceSelectionCombo.setSelectedItem(null);
    }

    if (selected == null || selected.getPath().startsWith(id)) {
      mediaSourceSelectionCombo.setSelectedIndex(0);
    }
  }
}
